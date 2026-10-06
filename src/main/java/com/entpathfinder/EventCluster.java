package com.entpathfinder;

import lombok.Getter;

/**
 * One real in-game event, assembled from every {@link EventCall} that describes it.
 * <p>
 * Deliberately free of RuneLite types so the clustering logic can be unit tested and run
 * headlessly. {@link ShortestPathBridge} converts the centre to a WorldPoint at the edge.
 */
@Getter
public class EventCluster
{
	private final int id;
	private final String eventType;
	private final int world;
	private final int plane;

	/** How long this particular type of event runs -- not a single figure across all events. */
	private final int lifetimeSeconds;

	/** False when the lifetime is a holding window rather than a real duration. */
	private final boolean timed;

	/** Epoch seconds of the earliest sighting -- the best estimate of when the event began. */
	private final long firstSeen;
	private long lastSeen;

	/** Fixed at construction so the displayed place name never moves. See {@link #getLocation()}. */
	private final CallLocation location;

	private long sumX;
	private long sumY;
	private int reportCount;

	EventCluster(int id, EventCall seed)
	{
		this.id = id;
		this.eventType = seed.getEventType();
		this.world = seed.getWorld();
		this.plane = seed.getPlane();
		this.lifetimeSeconds = EventType.lifetimeOf(seed.getEventType());
		this.timed = EventType.isTimed(seed.getEventType());
		this.firstSeen = seed.getDiscoveredTime();
		this.lastSeen = seed.getDiscoveredTime();
		this.location = CallLocation.nearest(seed.getXCoord(), seed.getYCoord());
		add(seed);
	}

	void add(EventCall call)
	{
		sumX += call.getXCoord();
		sumY += call.getYCoord();
		reportCount++;
		if (call.getDiscoveredTime() > lastSeen)
		{
			lastSeen = call.getDiscoveredTime();
		}
	}

	/**
	 * How far apart two sightings may be in time and still be the same event.
	 * <p>
	 * Exactly the event's own lifetime, because a report timestamped after the event ended cannot
	 * have been about it. A flat window wider than the lifetime -- such as the upstream plugin's
	 * 180s dedupe, which outlives the ent's 120s by a minute -- silently swallows the next event
	 * at the same spot. The forestry world concentrates on a handful of popular trees, so a
	 * repeat ent 150s after the last one at that tree was being folded into the dead cluster and
	 * never announced at all.
	 */
	int matchWindowSeconds()
	{
		return lifetimeSeconds;
	}

	/**
	 * Whether {@code call} describes this same event.
	 * <p>
	 * Mirrors the upstream plugin's own dedupe rule (same type, world and plane, within
	 * {@code radius} tiles). The time comparison is against {@link #firstSeen} rather than
	 * {@link #lastSeen} on purpose -- comparing against the latest sighting would let a trickle of
	 * late reports extend one cluster indefinitely.
	 */
	boolean matches(EventCall call, int radius)
	{
		if (!eventType.equals(call.getEventType()) || world != call.getWorld() || plane != call.getPlane())
		{
			return false;
		}
		if (Math.abs(call.getDiscoveredTime() - firstSeen) > matchWindowSeconds())
		{
			return false;
		}
		return Math.abs(call.getXCoord() - getCentreX()) <= radius
			&& Math.abs(call.getYCoord() - getCentreY()) <= radius;
	}

	/**
	 * Centre of mass of every sighting. Individual scouts report from wherever they happened
	 * to be standing, so averaging lands closer to the tree than any single report does.
	 */
	public int getCentreX()
	{
		return (int) Math.round((double) sumX / reportCount);
	}

	public int getCentreY()
	{
		return (int) Math.round((double) sumY / reportCount);
	}

	public String getDisplayName()
	{
		return EventType.displayNameOf(eventType);
	}

	/**
	 * Named place this call is at, or null if the first sighting matched nothing known.
	 * <p>
	 * Resolved once, from the seed report, and then fixed for the life of the cluster. Resolving it
	 * from the live centroid instead made the label move about: the centroid shifts every time
	 * another scout reports, so a call could slide between two neighbouring places, or drift out of
	 * every radius and fall back to showing the event's name. A call does not change where it is,
	 * so neither should its label.
	 */
	public CallLocation getLocation()
	{
		return location;
	}

	/** Seconds since the first sighting. Always meaningful, unlike a countdown. */
	public long age(long nowEpoch)
	{
		return Math.max(0, nowEpoch - firstSeen);
	}

	/**
	 * Seconds of event left, never negative. Only trustworthy when {@link #isTimed()};
	 * otherwise this is just the holding window counting down and {@link #age} is the honest number.
	 */
	public long secondsRemaining(long nowEpoch)
	{
		return Math.max(0, lifetimeSeconds - (nowEpoch - firstSeen));
	}

	public boolean isExpired(long nowEpoch)
	{
		return secondsRemaining(nowEpoch) <= 0;
	}

	@Override
	public String toString()
	{
		return getDisplayName() + " w" + world + " (" + getCentreX() + "," + getCentreY() + ")"
			+ " reports=" + reportCount;
	}
}
