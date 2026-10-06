package com.entpathfinder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(EntPathfinderConfig.GROUP)
public interface EntPathfinderConfig extends Config
{
	String GROUP = "entpathfinder";

	/** Where calls come from: the deployed relay. See ent-relay/README.md. */
	String DEFAULT_RELAY_URL = "wss://ent-relay.ent-calls.workers.dev/ws";

	@ConfigSection(
		name = "Tracking",
		description = "What to watch for and where",
		position = 0
	)
	String trackingSection = "trackingSection";

	@ConfigSection(
		name = "Response",
		description = "What happens when an event is called",
		position = 1
	)
	String responseSection = "responseSection";

	@ConfigSection(
		name = "Advanced",
		description = "Relay address override",
		position = 90,
		closedByDefault = true
	)
	String advancedSection = "advancedSection";

	// ------------------------------------------------------------------ tracking

	/**
	 * Off by default, as the Plugin Hub requires for anything that talks to a third-party server.
	 * Nothing connects anywhere until the player turns this on.
	 */
	@ConfigItem(
		position = 0,
		keyName = "relayEnabled",
		name = "Receive calls",
		description = "Connect to the Ent Pathfinder call relay to receive live event calls. "
			+ "Only connects while you are logged in on your forestry world",
		warning = "This feature submits your IP address to a 3rd-party server not controlled "
			+ "or verified by the RuneLite developers.",
		section = trackingSection
	)
	default boolean relayEnabled()
	{
		return false;
	}

	/**
	 * Always one specific world. Connecting only while on that world is what keeps the relay's
	 * load bounded: a single world holds at most 2,000 players.
	 */
	@ConfigItem(
		position = 1,
		keyName = "forestryWorld",
		name = "Forestry world",
		description = "The world to follow calls on. You only receive calls while logged in here",
		section = trackingSection
	)
	@Range(min = 301, max = 999)
	default int forestryWorld()
	{
		return 444;
	}

	@ConfigItem(
		position = 2,
		keyName = "ignoreSunbleak",
		name = "Ignore Sunbleak Island",
		description = "Skip calls on Sunbleak Island. Mooring there needs 72 Sailing and an adamant "
			+ "helm for the kelp, so its calls are unreachable without that",
		section = trackingSection
	)
	default boolean ignoreSunbleak()
	{
		return false;
	}

	@ConfigItem(
		position = 3,
		keyName = "ignoreDrumstickIsle",
		name = "Ignore Drumstick Isle",
		description = "Skip calls on Drumstick Isle. It holds the only rosewood trees in the game, "
			+ "so it does get Forestry calls, but mooring needs 79 Sailing and an adamant keel",
		section = trackingSection
	)
	default boolean ignoreDrumstickIsle()
	{
		return false;
	}

	// ------------------------------------------------------------------ response

	@ConfigItem(
		position = 0,
		keyName = "autoPath",
		name = "Draw path automatically",
		description = "Send the event to the Shortest Path plugin as soon as it is called. "
			+ "Requires the Shortest Path plugin to be installed and enabled",
		section = responseSection
	)
	default boolean autoPath()
	{
		return true;
	}

	@ConfigItem(
		position = 1,
		keyName = "clearPathOnEnd",
		name = "Clear path when event ends",
		description = "Clear the path once every event it was drawn for has run out of time",
		section = responseSection
	)
	default boolean clearPathOnEnd()
	{
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "notifyOnCall",
		name = "Notify on call",
		description = "Fire a RuneLite notification when a new event is called",
		section = responseSection
	)
	default boolean notifyOnCall()
	{
		return true;
	}

	@ConfigItem(
		position = 3,
		keyName = "arrivalTiles",
		name = "Arrival distance",
		description = "How close counts as having arrived. On arrival the path switches to the "
			+ "next event. Shortest Path cannot tell us this -- it is measured here",
		section = responseSection
	)
	@Range(min = 1, max = 30)
	default int arrivalTiles()
	{
		return 10;
	}

	@ConfigItem(
		position = 4,
		keyName = "clearOnArrival",
		name = "Remove on arrival",
		description = "Drop an event from the list the moment you reach it, instead of keeping it "
			+ "listed until the game says you have finished with it",
		section = responseSection
	)
	default boolean clearOnArrival()
	{
		return false;
	}

	@ConfigItem(
		position = 5,
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show live events and their remaining time on screen",
		section = responseSection
	)
	default boolean showOverlay()
	{
		return true;
	}

	// ------------------------------------------------------------------ advanced

	@ConfigItem(
		position = 0,
		keyName = "relayUrl",
		name = "Relay address",
		description = "Where to receive calls from. Leave as-is unless you run your own relay",
		section = advancedSection
	)
	default String relayUrl()
	{
		return DEFAULT_RELAY_URL;
	}
}
