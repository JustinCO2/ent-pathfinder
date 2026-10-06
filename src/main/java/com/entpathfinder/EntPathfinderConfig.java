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

	/** Where calls come from: the deployed relay. Source: https://github.com/JustinCO2/ent-relay */
	String DEFAULT_RELAY_URL = "wss://ent-relay.ent-calls.workers.dev/ws";

	@ConfigSection(
		name = "Tracking",
		description = "Which calls to follow",
		position = 0
	)
	String trackingSection = "trackingSection";

	@ConfigSection(
		name = "Response",
		description = "What happens when an ent is called",
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

	@ConfigItem(
		position = 0,
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
		position = 1,
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
		keyName = "notifyOnCall",
		name = "Notify on call",
		description = "Fire a RuneLite notification when a new ent is called",
		section = responseSection
	)
	default boolean notifyOnCall()
	{
		return true;
	}

	@ConfigItem(
		position = 1,
		keyName = "arrivalTiles",
		name = "Arrival distance",
		description = "How close counts as having arrived. On arrival the path switches to the "
			+ "next ent. Shortest Path cannot tell us this -- it is measured here",
		section = responseSection
	)
	@Range(min = 1, max = 30)
	default int arrivalTiles()
	{
		return 10;
	}

	@ConfigItem(
		position = 2,
		keyName = "clearOnArrival",
		name = "Remove on arrival",
		description = "Drop an ent from the list the moment you reach it, instead of keeping it "
			+ "listed until the game says you have finished with it",
		section = responseSection
	)
	default boolean clearOnArrival()
	{
		return false;
	}

	@ConfigItem(
		position = 3,
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show live ents and their remaining time on screen",
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
