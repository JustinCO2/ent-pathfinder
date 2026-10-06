package com.entpathfinder;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Ent Pathfinder",
	description = "Draws a Shortest Path to called Forestry ent events on the forestry world",
	tags = {"forestry", "ent", "entling", "woodcutting", "shortestpath", "scouting"}
)
public class EntPathfinderPlugin extends Plugin
{
	/** No event is currently targeted. */
	static final int NO_TARGET = 0;

	/**
	 * The community forestry world, and the only world the plugin connects on. Fixing it to one
	 * world also bounds the relay's load: a single world holds at most 2,000 players.
	 */
	static final int FORESTRY_WORLD = 444;

	/**
	 * How long to keep the relay connection after it stops being needed. Hopping, relogging after
	 * the six-hour logout, or a lag spike would otherwise each cost a fresh connection against the
	 * relay's daily allowance. A grace period rides them out.
	 */
	private static final long DISCONNECT_GRACE_MILLIS = 60_000;

	@Inject
	private Client client;

	@Inject
	private EntPathfinderConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private Notifier notifier;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EntPathfinderOverlay overlay;

	@Inject
	private ShortestPathBridge shortestPath;

	/**
	 * RuneLite's shared executor, used instead of {@code @Schedule}. {@code @Schedule} is driven from
	 * the client's render loop and stalls whenever the client is minimised or frame-limited --
	 * exactly when someone is sitting waiting for a call.
	 */
	@Inject
	private ScheduledExecutorService executor;

	/** Replaced on every start-up; the previous one is closed for good on shutdown. */
	private volatile RelayClient relay;
	private ScheduledFuture<?> tickTask;

	/** False once shutdown begins, so in-flight background work stands down. */
	private volatile boolean running;

	private final EventClusterer clusterer = new EventClusterer();

	/**
	 * Guards the clusterer and the per-event state below. Calls arrive on the relay's network
	 * thread, the tick runs on the executor, and arrival is checked on the client thread.
	 */
	private final Object eventsLock = new Object();

	/** Clusters already alerted on, so a long-running event alerts exactly once. */
	private final Set<Integer> announced = new HashSet<>();

	/** Events the player has reached but not finished. Still listed, no longer a destination. */
	private final Set<Integer> arrived = ConcurrentHashMap.newKeySet();

	/** Events finished and done with. Dropped from the list entirely. */
	private final Set<Integer> completed = ConcurrentHashMap.newKeySet();

	/**
	 * The event the path currently points at. Exactly one, never a set: Shortest Path clears its
	 * entire target set as soon as the player reaches <em>any</em> one of them.
	 */
	private volatile int currentTargetId = NO_TARGET;

	/** Live events, oldest first. Completed ones are filtered out on read. */
	private volatile List<EventCluster> liveSnapshot = Collections.emptyList();

	// Game state, sampled on the client thread for the executor to read.
	private volatile GameState gameState = GameState.UNKNOWN;
	private volatile int currentWorld;
	private volatile WorldPoint playerLocation;

	private volatile long lastNeededRelayMillis;
	private volatile RelayClient.Status relayStatus = RelayClient.Status.DISCONNECTED;

	/** The relay's clock minus ours, so event timers agree with the relay rather than this PC. */
	private volatile long clockSkewSeconds;

	@Provides
	EntPathfinderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EntPathfinderConfig.class);
	}

	@Override
	protected void startUp()
	{
		relay = new RelayClient(okHttpClient, gson, executor, new RelayListener());
		overlayManager.add(overlay);
		running = true;
		tickTask = executor.scheduleWithFixedDelay(this::tickSafely, 1, 1, TimeUnit.SECONDS);
	}

	/**
	 * Background work can still be mid-flight while this runs: a tick, or a push arriving. So
	 * {@code running} goes false first, and the relay client is closed for good rather than just
	 * disconnected, so a late tick cannot reopen it.
	 */
	@Override
	protected void shutDown()
	{
		running = false;
		tickTask.cancel(false);
		relay.close();

		overlayManager.remove(overlay);
		shortestPath.clearIfOurs();
		forget();
	}

	// ------------------------------------------------------------------ game state

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		gameState = event.getGameState();
		currentWorld = client.getWorld();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		gameState = client.getGameState();
		currentWorld = client.getWorld();

		Player local = client.getLocalPlayer();
		playerLocation = local == null ? null : local.getWorldLocation();

		checkArrival();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!EntPathfinderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (changesWhichCallsCount(event.getKey()))
		{
			shortestPath.clearIfOurs();
			forget();
		}
	}

	/**
	 * Settings that change which calls are relevant. Anything already on screen has to go, or an
	 * island the player just chose to ignore would linger until its clusters aged out.
	 */
	private static boolean changesWhichCallsCount(String key)
	{
		return "ignoreSunbleak".equals(key) || "ignoreDrumstickIsle".equals(key);
	}

	// ------------------------------------------------------------------ the tick

	/**
	 * Runs every second. Wrapped so nothing can escape: a repeating task is cancelled for good by
	 * its first uncaught exception, which would silently stop the plugin for the rest of the session.
	 */
	private void tickSafely()
	{
		if (!running)
		{
			return;
		}

		try
		{
			updateRelayConnection();
			refreshEvents();
		}
		catch (Throwable t)
		{
			log.warn("Tick failed", t);
		}
	}

	/**
	 * Hold a relay connection only while it can be useful. Logged out or on any other world, the
	 * plugin holds no connection at all and costs the relay nothing.
	 */
	private void updateRelayConnection()
	{
		long now = System.currentTimeMillis();

		if (relayNeeded())
		{
			lastNeededRelayMillis = now;
			relay.connect(relayAddress());
		}
		else if (now - lastNeededRelayMillis > DISCONNECT_GRACE_MILLIS)
		{
			relay.disconnect();
		}
	}

	private boolean relayNeeded()
	{
		return isInGame(gameState) && currentWorld == FORESTRY_WORLD;
	}

	/**
	 * Loading screens and momentary connection loss still count as in game. Treating them as logged
	 * out would drop and reopen the relay connection on every region load.
	 */
	private static boolean isInGame(GameState state)
	{
		return state == GameState.LOGGED_IN
			|| state == GameState.LOADING
			|| state == GameState.CONNECTION_LOST;
	}

	/** Subscribe to ents on the forestry world only, so the relay sends nothing else. */
	private String relayAddress()
	{
		return config.relayUrl().trim()
			+ "?world=" + FORESTRY_WORLD
			+ "&type=" + EventType.ENT.getWireName();
	}

	/** Receives the relay's pushes on its network thread. */
	private class RelayListener implements RelayClient.Listener
	{
		@Override
		public void onCalls(List<EventCall> calls, long serverTimeSeconds)
		{
			if (!running)
			{
				return;
			}
			if (isPlausibleServerTime(serverTimeSeconds, localNowSeconds()))
			{
				clockSkewSeconds = serverTimeSeconds - localNowSeconds();
			}

			synchronized (eventsLock)
			{
				clusterer.accept(relevantCalls(calls), serverNow());
			}
			refreshEvents();
		}

		@Override
		public void onStatusChanged(RelayClient.Status status)
		{
			relayStatus = status;
		}
	}

	// ------------------------------------------------------------------ events

	/**
	 * Age out finished events, rebuild the list, and repoint the path. Called on every push and
	 * every tick: an event ending is a change even when nothing new arrives.
	 */
	private void refreshEvents()
	{
		long now = serverNow();

		synchronized (eventsLock)
		{
			forgetFinished(clusterer.prune(now));
			liveSnapshot = liveEventsAnnouncingNew(now);
			retarget();
		}
	}

	private void forgetFinished(List<EventCluster> finished)
	{
		for (EventCluster cluster : finished)
		{
			announced.remove(cluster.getId());
			arrived.remove(cluster.getId());
			completed.remove(cluster.getId());
		}
	}

	/** Live events, oldest first, announcing any seen for the first time. */
	private List<EventCluster> liveEventsAnnouncingNew(long now)
	{
		List<EventCluster> live = clusterer.getActive(now);
		for (EventCluster cluster : live)
		{
			if (announced.add(cluster.getId()))
			{
				announce(cluster, now);
			}
		}
		return live;
	}

	/**
	 * Ents, minus any at places the player has chosen to ignore. Done before clustering, so an ignored
	 * island never becomes an event: it cannot alert, be pathed to, or sit at the top of the list.
	 * <p>
	 * The relay is only ever asked for ents, so the type check never normally removes anything. It
	 * is there so a misconfigured or older relay cannot put other events on screen.
	 */
	private List<EventCall> relevantCalls(List<EventCall> calls)
	{
		List<EventCall> kept = new ArrayList<>(calls.size());
		for (EventCall call : calls)
		{
			boolean isEnt = EventType.ENT.getWireName().equals(call.getEventType());
			if (isEnt && !isIgnored(CallLocation.nearest(call.getXCoord(), call.getYCoord())))
			{
				kept.add(call);
			}
		}
		return kept;
	}

	private boolean isIgnored(CallLocation location)
	{
		if (location == CallLocation.SUNBLEAK_ISLAND)
		{
			return config.ignoreSunbleak();
		}
		if (location == CallLocation.DRUMSTICK_ISLE)
		{
			return config.ignoreDrumstickIsle();
		}
		return false;
	}

	private void announce(EventCluster cluster, long now)
	{
		String when = cluster.isTimed()
			? cluster.secondsRemaining(now) + "s left"
			: "called " + cluster.age(now) + "s ago";

		log.debug("New event {} ({})", cluster, when);

		if (config.notifyOnCall())
		{
			notifier.notify(placeOf(cluster) + " -- " + when);
		}
	}

	private static String placeOf(EventCluster cluster)
	{
		CallLocation where = cluster.getLocation();
		return where == null ? cluster.getDisplayName() : where.getDisplayName();
	}

	private void forget()
	{
		synchronized (eventsLock)
		{
			clusterer.reset();
			announced.clear();
			arrived.clear();
			completed.clear();
			currentTargetId = NO_TARGET;
			liveSnapshot = Collections.emptyList();
		}
	}

	// ------------------------------------------------------------------ the path

	/**
	 * Point the path at the oldest event not yet reached or finished -- the one closest to ending,
	 * and the one at the top of the list -- or clear it once there is nothing left to go to.
	 * Callers hold eventsLock.
	 */
	private void retarget()
	{
		EventCluster next = firstUnvisitedEvent();
		int nextId = next == null ? NO_TARGET : next.getId();
		if (nextId == currentTargetId)
		{
			return;
		}
		currentTargetId = nextId;

		if (next == null)
		{
			shortestPath.clearIfOurs();
		}
		else
		{
			shortestPath.pathTo(next);
		}
	}

	private EventCluster firstUnvisitedEvent()
	{
		for (EventCluster cluster : liveSnapshot)
		{
			if (!arrived.contains(cluster.getId()) && !completed.contains(cluster.getId()))
			{
				return cluster;
			}
		}
		return null;
	}

	/**
	 * Mark the current event reached once the player gets close enough, and move the path on.
	 * Measured here because Shortest Path exposes nothing about progress.
	 */
	private void checkArrival()
	{
		synchronized (eventsLock)
		{
			EventCluster target = currentTarget();
			if (target == null || !isWithinArrivalDistance(target))
			{
				return;
			}

			// Normally a reached event stays listed while the player works it; the setting treats
			// arrival as done with.
			if (config.clearOnArrival())
			{
				completed.add(target.getId());
			}
			else
			{
				arrived.add(target.getId());
			}
			log.debug("Arrived at {} -- advancing", target);
			retarget();
		}
	}

	private EventCluster currentTarget()
	{
		for (EventCluster cluster : liveSnapshot)
		{
			if (cluster.getId() == currentTargetId)
			{
				return cluster;
			}
		}
		return null;
	}

	private boolean isWithinArrivalDistance(EventCluster cluster)
	{
		int tiles = tilesTo(cluster);
		return tiles >= 0 && tiles <= config.arrivalTiles();
	}

	/**
	 * The event finishing is the one thing arrival cannot tell us. Proximity says the player is
	 * there; only the game says they are done.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		if (!EntChatSignals.isEntEventComplete(Text.removeTags(event.getMessage())))
		{
			return;
		}

		synchronized (eventsLock)
		{
			EventCluster here = eventPlayerIsAt();
			if (here == null)
			{
				return;
			}
			completed.add(here.getId());
			arrived.remove(here.getId());
			log.debug("Finished {} -- dropping from the list", here);
			retarget();
		}
	}

	/** The nearest listed event within arrival distance, so finishing one of two clears the right one. */
	private EventCluster eventPlayerIsAt()
	{
		EventCluster nearest = null;
		int nearestTiles = Integer.MAX_VALUE;

		for (EventCluster cluster : getLiveEvents())
		{
			int tiles = tilesTo(cluster);
			if (isWithinArrivalDistance(cluster) && tiles < nearestTiles)
			{
				nearest = cluster;
				nearestTiles = tiles;
			}
		}
		return nearest;
	}

	// ------------------------------------------------------------------ for the overlay

	/** Chebyshev tiles from the player, as the game measures distance. -1 when unknown. */
	int tilesTo(EventCluster cluster)
	{
		WorldPoint location = playerLocation;
		if (location == null)
		{
			return -1;
		}
		return Math.max(
			Math.abs(location.getX() - cluster.getCentreX()),
			Math.abs(location.getY() - cluster.getCentreY()));
	}

	/** Live events, oldest first, finished ones removed. Safe to call from the client thread. */
	List<EventCluster> getLiveEvents()
	{
		List<EventCluster> visible = new ArrayList<>();
		for (EventCluster cluster : liveSnapshot)
		{
			if (!completed.contains(cluster.getId()))
			{
				visible.add(cluster);
			}
		}
		return visible;
	}

	int getCurrentTargetId()
	{
		return currentTargetId;
	}

	/** True once the player has reached this event and is presumably working it. */
	boolean isInProgress(EventCluster cluster)
	{
		return arrived.contains(cluster.getId());
	}

	/** True while the relay is unreachable and the plugin is waiting to try again. */
	boolean isRelayOffline()
	{
		return relayStatus == RelayClient.Status.WAITING_TO_RETRY;
	}

	long serverNow()
	{
		return localNowSeconds() + clockSkewSeconds;
	}

	private static long localNowSeconds()
	{
		return System.currentTimeMillis() / 1000L;
	}

	/** How far the relay's clock may disagree with this PC's before it is distrusted. */
	static final long MAX_PLAUSIBLE_SKEW_SECONDS = 24 * 60 * 60;

	/**
	 * Whether a relay timestamp is believable. A missing or garbled one would otherwise shift every
	 * event timer by years -- events that never expire, or that expire the moment they arrive.
	 * A PC clock that is genuinely a few minutes or hours out is still corrected for.
	 */
	static boolean isPlausibleServerTime(long serverTimeSeconds, long localNowSeconds)
	{
		return serverTimeSeconds > 0
			&& Math.abs(serverTimeSeconds - localNowSeconds) <= MAX_PLAUSIBLE_SKEW_SECONDS;
	}
}
