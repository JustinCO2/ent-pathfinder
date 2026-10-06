package com.entpathfinder;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Hands a target to the Shortest Path plugin over its supported plugin-message API -- the same
 * route Quest Helper uses. No reflection and no hard dependency: if Shortest Path is not
 * installed the messages are simply never consumed.
 */
@Singleton
public class ShortestPathBridge
{
	private static final String NAMESPACE = "shortestpath";
	private static final String MESSAGE_PATH = "path";
	private static final String MESSAGE_CLEAR = "clear";
	private static final String KEY_TARGET = "target";

	private final EventBus eventBus;
	private final ClientThread clientThread;

	/**
	 * Whether the current path was drawn by this plugin.
	 * <p>
	 * Shortest Path does not broadcast its target, so a path the player set by hand is
	 * indistinguishable from no path at all. Tracking our own writes at least means this plugin
	 * never clears a path it did not draw.
	 * <p>
	 * Volatile because it is written from the poller thread and read when clearing.
	 */
	private volatile boolean pathIsOurs;

	@Inject
	ShortestPathBridge(EventBus eventBus, ClientThread clientThread)
	{
		this.eventBus = eventBus;
		this.clientThread = clientThread;
	}

	/**
	 * Draw a path to one event.
	 * <p>
	 * Deliberately a single target rather than the set Shortest Path also accepts. It treats the
	 * set as "any of these will do" and wipes the whole path the moment the player comes within
	 * reachedDistance of any one of them, which with several live events means arriving at the
	 * first silently cancels the route to the rest. Advancing one event at a time keeps the path
	 * and the on-screen list saying the same thing.
	 */
	public void pathTo(EventCluster cluster)
	{
		WorldPoint target = new WorldPoint(cluster.getCentreX(), cluster.getCentreY(), cluster.getPlane());

		pathIsOurs = true;
		post(new PluginMessage(NAMESPACE, MESSAGE_PATH, Collections.singletonMap(KEY_TARGET, target)));
	}

	public void clearIfOurs()
	{
		if (!pathIsOurs)
		{
			return;
		}
		pathIsOurs = false;
		post(new PluginMessage(NAMESPACE, MESSAGE_CLEAR, Collections.emptyMap()));
	}

	/**
	 * Posts on the client thread, which is not optional.
	 * <p>
	 * EventBus delivers to subscribers synchronously on the calling thread, and the caller here is
	 * the poller running on RuneLite's scheduled executor. The Shortest Path handler reads
	 * the local player's world location to default the start point, which asserts it is on the
	 * client thread -- so posting directly threw "must be called on client thread" on every single
	 * attempt and no path was ever drawn. The HTTP work stays off the client thread; only the post
	 * hops onto it.
	 */
	private void post(PluginMessage message)
	{
		clientThread.invokeLater(() -> eventBus.post(message));
	}

	public boolean isPathOurs()
	{
		return pathIsOurs;
	}
}
