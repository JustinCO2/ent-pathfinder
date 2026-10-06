package com.entpathfinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Collapses the raw scouting feed into distinct in-game events.
 * <p>
 * The feed is a firehose of individual sightings: one ent typically arrives as a dozen or more
 * rows scattered over a handful of tiles and a couple of minutes, one per scout who saw it.
 * Pathing off raw rows would alert repeatedly for a single event and thrash the pathfinder.
 * <p>
 * This class is pure logic -- no RuneLite types, no network, no clock of its own. The caller
 * supplies the current time, which is what makes it testable and headless-runnable.
 */
public class EventClusterer
{
	/** Mirrors the upstream plugin's own dedupe distance. */
	static final int MATCH_RADIUS_TILES = 20;

	private final List<EventCluster> active = new ArrayList<>();

	/**
	 * Clusters that have run out of time but may still receive reports.
	 * <p>
	 * Kept deliberately: reports arrive after the event they describe has ended -- a scout who saw
	 * it in its final seconds, uploaded on the next three second tick, reaching us later still.
	 * Without somewhere for those to land they would match nothing and be mistaken for a brand new
	 * event at the same tree, alerting the player to something already over.
	 */
	private final List<EventCluster> recentlyExpired = new ArrayList<>();

	private int nextId = 1;

	/**
	 * Feed in freshly fetched rows.
	 *
	 * @return only the clusters that are genuinely new and still running -- i.e. exactly the
	 *         events worth alerting on. Rows folded into known events return nothing.
	 */
	public List<EventCluster> accept(Collection<EventCall> rows, long nowEpoch)
	{
		prune(nowEpoch);

		List<EventCall> ordered = new ArrayList<>(rows);
		ordered.sort(Comparator.comparingLong(EventCall::getDiscoveredTime));

		List<EventCluster> started = new ArrayList<>();
		for (EventCall call : ordered)
		{
			if (absorb(call))
			{
				continue;
			}

			EventCluster cluster = new EventCluster(nextId++, call);
			if (cluster.isExpired(nowEpoch))
			{
				// Already over by the time we heard about it -- track it so its stragglers do
				// not later look like a new event, but do not alert.
				recentlyExpired.add(cluster);
				continue;
			}
			active.add(cluster);
			started.add(cluster);
		}
		return started;
	}

	private boolean absorb(EventCall call)
	{
		for (EventCluster cluster : active)
		{
			if (cluster.matches(call, MATCH_RADIUS_TILES))
			{
				cluster.add(call);
				return true;
			}
		}
		for (EventCluster cluster : recentlyExpired)
		{
			if (cluster.matches(call, MATCH_RADIUS_TILES))
			{
				cluster.add(call);
				return true;
			}
		}
		return false;
	}

	/**
	 * Age out finished events. Safe and necessary to call on every poll, including polls that
	 * returned nothing -- an event ending is a state change even when no rows arrive.
	 *
	 * @return clusters that expired on this call.
	 */
	public List<EventCluster> prune(long nowEpoch)
	{
		List<EventCluster> justExpired = new ArrayList<>();
		for (Iterator<EventCluster> it = active.iterator(); it.hasNext(); )
		{
			EventCluster cluster = it.next();
			if (cluster.isExpired(nowEpoch))
			{
				it.remove();
				recentlyExpired.add(cluster);
				justExpired.add(cluster);
			}
		}

		// Hold a finished cluster for a second lifetime beyond its own, long enough to catch
		// stragglers. Scaling by the event rather than using a flat figure means a long event is
		// not dropped the instant it expires, which would let its trailing reports raise a
		// phantom second event.
		recentlyExpired.removeIf(c ->
			nowEpoch - c.getFirstSeen() > (long) c.getLifetimeSeconds() + c.matchWindowSeconds());

		return justExpired;
	}

	/** Currently running events, oldest first. */
	public List<EventCluster> getActive(long nowEpoch)
	{
		List<EventCluster> live = new ArrayList<>();
		for (EventCluster cluster : active)
		{
			if (!cluster.isExpired(nowEpoch))
			{
				live.add(cluster);
			}
		}
		// Oldest first: the event that appeared first is the one closest to ending, so it is both
		// the one to head for and the one to list at the top.
		live.sort(Comparator.comparingLong(EventCluster::getFirstSeen));
		return Collections.unmodifiableList(live);
	}

	public void reset()
	{
		active.clear();
		recentlyExpired.clear();
	}
}
