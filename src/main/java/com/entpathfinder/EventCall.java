package com.entpathfinder;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

/**
 * A single sighting of an event, as pushed by the call relay.
 * <p>
 * One real in-game event produces many sightings -- every scout running the Event Scouting plugin
 * who sees it reports it, a few tiles and a few seconds apart. A burst of seventeen sightings is
 * routinely one or two actual ents. {@link EventClusterer} collapses them.
 * <p>
 * Field names mirror the scouting API's schema. The relay passes on only these fields -- what,
 * where and when -- and nothing about who reported the event.
 */
@Getter
public class EventCall
{
	@SerializedName("event_type")
	private String eventType;

	@SerializedName("world")
	private int world;

	@SerializedName("x_coord")
	private int xCoord;

	@SerializedName("y_coord")
	private int yCoord;

	/** Forestry is all ground level, so a missing value is read as plane 0. */
	@SerializedName("plane")
	private Integer plane;

	/** Epoch seconds, set by the scout's own client when it spotted the event. */
	@SerializedName("discovered_time")
	private long discoveredTime;

	/** Gson. */
	public EventCall()
	{
	}

	public EventCall(String eventType, int world, int xCoord, int yCoord, long discoveredTime)
	{
		this.eventType = eventType;
		this.world = world;
		this.xCoord = xCoord;
		this.yCoord = yCoord;
		this.plane = 0;
		this.discoveredTime = discoveredTime;
	}

	public int getPlane()
	{
		return plane == null ? 0 : plane;
	}

	@Override
	public String toString()
	{
		return eventType + " w" + world + " (" + xCoord + "," + yCoord + ") t=" + discoveredTime;
	}
}
