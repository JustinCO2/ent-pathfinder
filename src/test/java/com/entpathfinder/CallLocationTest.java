package com.entpathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Every coordinate here was taken off the live scouting feed, so these assert that the table
 * actually names the calls that turn up in practice rather than only the wiki's map pins.
 */
public class CallLocationTest
{
	@Test
	public void namesTheSailingIslandsTheIgnoreTogglesDependOn()
	{
		// SAPLING w495 (2150,3538), (2153,3537), (2152,3540)
		assertEquals(CallLocation.DRUMSTICK_ISLE, CallLocation.nearest(2150, 3538));
		assertEquals(CallLocation.DRUMSTICK_ISLE, CallLocation.nearest(2153, 3537));
		assertEquals(CallLocation.DRUMSTICK_ISLE, CallLocation.nearest(2152, 3540));

		// FLOWERS w444 (2209,2319) and ROOTS w444 (2204,2314)
		assertEquals(CallLocation.SUNBLEAK_ISLAND, CallLocation.nearest(2209, 2319));
		assertEquals(CallLocation.SUNBLEAK_ISLAND, CallLocation.nearest(2204, 2314));
	}

	/**
	 * Drumstick Isle sits about 120 tiles north of Mynydd and both are generously sized, so a
	 * first-match lookup could easily call it Mynydd.
	 */
	@Test
	public void nearestWinsWhenRadiiOverlap()
	{
		assertEquals(CallLocation.DRUMSTICK_ISLE, CallLocation.nearest(2150, 3538));
		assertEquals(CallLocation.MYNYDD, CallLocation.nearest(2158, 3423));
	}

	@Test
	public void namesTheForestryHotspots()
	{
		// ENT and FOX bursts on world 444.
		assertEquals(CallLocation.NEMUS_RETREAT, CallLocation.nearest(1383, 3281));
		assertEquals(CallLocation.NEMUS_RETREAT, CallLocation.nearest(1386, 3279));
		assertEquals(CallLocation.NEMUS_RETREAT, CallLocation.nearest(1401, 3292));

		// ROOTS w444 (2707,3463) -- the maples south of the village, not the village pin.
		assertEquals(CallLocation.SEERS_VILLAGE, CallLocation.nearest(2707, 3463));

		// BEEHIVE w444 (3101,3239)
		assertEquals(CallLocation.DRAYNOR_VILLAGE, CallLocation.nearest(3101, 3239));

		// FOX w444 (1752,3567)
		assertEquals(CallLocation.HOSIDIUS, CallLocation.nearest(1752, 3567));

		// PHEASANT w444 (3235,6097)
		assertEquals(CallLocation.PRIFDDINAS, CallLocation.nearest(3235, 6097));
	}

	/**
	 * The open ocean where shoals spawn is not a named place in this table, and must come back as
	 * nothing rather than being attached to whichever island happens to be least far away.
	 */
	@Test
	public void unknownCoordinatesResolveToNothing()
	{
		// VIBRANT_SHOAL w596 (3843,6259) and w303 (3851,6251)
		assertNull(CallLocation.nearest(3843, 6259));
		assertNull(CallLocation.nearest(3851, 6251));
	}

	@Test
	public void everyPlaceNamesItself()
	{
		for (CallLocation location : CallLocation.values())
		{
			assertEquals("a place must resolve at its own centre",
				location, CallLocation.nearest(location.getCentreX(), location.getCentreY()));
		}
	}
}
