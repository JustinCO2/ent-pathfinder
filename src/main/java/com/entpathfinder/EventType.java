package com.entpathfinder;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Every event type the scouting feed carries.
 * <p>
 * Only the ent has a real duration here, because only the ent matters: it is the one Forestry
 * event whose egg nest does not require having been chopping nearby when it spawned. Everything
 * else exists so "Any event" can exercise the pipeline against live traffic.
 * <p>
 * The others are deliberately given no timer rather than a guessed one. The wiki does not state
 * durations for most of them, and a single shared 120s guess is what silently swallowed live
 * Rising Roots and Flowering Bush events. They get a generous holding window instead -- long
 * enough that nothing live is thrown away, and shown as an age rather than a countdown so no
 * number on screen is one this plugin invented.
 */
@Getter
public enum EventType
{
	/** The only event with a duration worth trusting: the wiki states it outright as two minutes. */
	ENT("ENT", "Friendly ent", 120),

	ROOTS("ROOTS", "Rising roots"),
	SAPLING("SAPLING", "Struggling sapling"),
	FLOWERS("FLOWERS", "Flowering bush"),
	BEEHIVE("BEEHIVE", "Beehive"),
	FOX("FOX", "Poachers"),
	PHEASANT("PHEASANT", "Pheasant control"),
	RITUAL("RITUAL", "Enchantment ritual"),
	LEPRECHAUN("LEPRECHAUN", "Woodcutting leprechaun"),
	SHIMMERING_SHOAL("SHIMMERING_SHOAL", "Shimmering shoal"),
	GLISTENING_SHOAL("GLISTENING_SHOAL", "Glistening shoal"),
	VIBRANT_SHOAL("VIBRANT_SHOAL", "Vibrant shoal");

	/**
	 * Holding window for untimed events. Not a duration -- just how long one is kept on screen and
	 * held for deduplication before being forgotten.
	 */
	public static final int UNTIMED_WINDOW_SECONDS = 300;

	/** Longest anything is tracked for. Sizes the fetch window. */
	public static final int MAX_LIFETIME_SECONDS = UNTIMED_WINDOW_SECONDS;

	private final String wireName;
	private final String displayName;
	private final int lifetimeSeconds;

	/** Whether {@link #getLifetimeSeconds()} is a real duration or just a holding window. */
	private final boolean timed;

	EventType(String wireName, String displayName, int lifetimeSeconds)
	{
		this.wireName = wireName;
		this.displayName = displayName;
		this.lifetimeSeconds = lifetimeSeconds;
		this.timed = true;
	}

	EventType(String wireName, String displayName)
	{
		this.wireName = wireName;
		this.displayName = displayName;
		this.lifetimeSeconds = UNTIMED_WINDOW_SECONDS;
		this.timed = false;
	}

	private static final Map<String, EventType> BY_WIRE = new HashMap<>();

	static
	{
		for (EventType type : values())
		{
			BY_WIRE.put(type.wireName, type);
		}
	}

	/** Null for a type this plugin has never heard of -- see {@link #lifetimeOf}. */
	public static EventType forWire(String wireName)
	{
		return wireName == null ? null : BY_WIRE.get(wireName);
	}

	/**
	 * Window for a wire name, including types added upstream since this was written. An unknown
	 * type must never be silently discarded, so it gets the same generous holding window.
	 */
	public static int lifetimeOf(String wireName)
	{
		EventType type = forWire(wireName);
		return type == null ? UNTIMED_WINDOW_SECONDS : type.lifetimeSeconds;
	}

	public static boolean isTimed(String wireName)
	{
		EventType type = forWire(wireName);
		return type != null && type.timed;
	}

	public static String displayNameOf(String wireName)
	{
		EventType type = forWire(wireName);
		return type == null ? wireName : type.displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
