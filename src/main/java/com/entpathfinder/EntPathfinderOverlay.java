package com.entpathfinder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Lists live events oldest first -- the order they were called, and the order the path visits them
 * in. One line each: where the call is, and how long is left.
 * <p>
 * The place name carries the identity of a call, so the index, event name, distance and leading
 * marker that used to sit on each line are all gone; with two ents running, "Nemus Retreat" and
 * "Seers' Village" tell them apart far better than "1." and "2." ever did. There is no title
 * either -- the lines say what they are. State is left to colour.
 * <p>
 * Only the ent shows a countdown. Untimed events show their age instead: the ent is the only one
 * whose duration is actually known, and inventing a countdown for the rest would be worse than
 * showing nothing.
 */
public class EntPathfinderOverlay extends OverlayPanel
{
	private static final int WIDTH = 210;

	/** The event the path is pointing at. */
	private static final Color TARGET = Color.WHITE;

	/** The event the player has reached and is presumably working. */
	private static final Color HERE = Color.CYAN;

	private static final Color PENDING = Color.LIGHT_GRAY;

	private final EntPathfinderPlugin plugin;
	private final EntPathfinderConfig config;

	@Inject
	EntPathfinderOverlay(EntPathfinderPlugin plugin, EntPathfinderConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		List<EventCluster> live = plugin.getLiveEvents();
		boolean offline = plugin.isRelayOffline();
		if (live.isEmpty() && !offline)
		{
			return null;
		}

		if (offline)
		{
			addOfflineLine();
		}
		for (EventCluster cluster : live)
		{
			addEventLine(cluster);
		}

		return super.render(graphics);
	}

	/**
	 * Said plainly rather than hidden. With the relay unreachable no calls can arrive, and an empty
	 * overlay would look exactly like a quiet spell.
	 */
	private void addOfflineLine()
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Call relay offline")
			.leftColor(Color.RED)
			.right("(retrying)")
			.rightColor(Color.GRAY)
			.build());
	}

	private void addEventLine(EventCluster cluster)
	{
		long now = plugin.serverNow();

		panelComponent.getChildren().add(LineComponent.builder()
			.left(placeName(cluster))
			.leftColor(stateColour(cluster))
			.right("(" + timer(cluster, now) + ")")
			.rightColor(cluster.isTimed() ? remainingColour(cluster.secondsRemaining(now)) : stateColour(cluster))
			.build());
	}

	/** White for the event the path is on, cyan once reached, grey for the rest. */
	private Color stateColour(EventCluster cluster)
	{
		if (plugin.isInProgress(cluster))
		{
			return HERE;
		}
		return cluster.getId() == plugin.getCurrentTargetId() ? TARGET : PENDING;
	}

	/**
	 * Where the call is. Falls back to the event's own name when the coordinate matches no known
	 * place, so a line is never left blank.
	 */
	private static String placeName(EventCluster cluster)
	{
		CallLocation where = cluster.getLocation();
		return where == null ? cluster.getDisplayName() : where.getDisplayName();
	}

	private static String timer(EventCluster cluster, long now)
	{
		return cluster.isTimed()
			? cluster.secondsRemaining(now) + "s"
			: cluster.age(now) + "s ago";
	}

	private static Color remainingColour(long remaining)
	{
		if (remaining <= 20)
		{
			// Roughly the "Almost time to go!" warning window.
			return Color.RED;
		}
		return remaining <= 45 ? Color.ORANGE : Color.GREEN;
	}
}
