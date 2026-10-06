package com.entpathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class EventClustererTest
{
	private static final long T = 1_700_000_000L;
	private static final String ENT = "ENT";
	private static final int WORLD = 444;

	/**
	 * Shape taken from a real burst on the live feed: seventeen rows, eight distinct coordinates
	 * spanning about eight tiles, arriving over roughly forty seconds. Every one of them is the
	 * same ent, reported by a different scout from wherever they were standing.
	 */
	private static List<EventCall> realBurst()
	{
		int[][] sightings = {
			{1383, 3280, 0}, {1385, 3277, 9}, {1383, 3282, 12}, {1384, 3282, 19},
			{1383, 3283, 24}, {1383, 3283, 25}, {1383, 3282, 26}, {1383, 3281, 37},
			{1382, 3279, 5}, {1382, 3279, 7}, {1382, 3279, 14}, {1382, 3279, 21},
			{1380, 3285, 8}, {1380, 3285, 16}, {1380, 3285, 29},
			{1384, 3282, 31}, {1383, 3280, 33},
		};

		List<EventCall> rows = new ArrayList<>();
		for (int i = 0; i < sightings.length; i++)
		{
			rows.add(new EventCall(ENT, WORLD, sightings[i][0], sightings[i][1],
				T + sightings[i][2]));
		}
		return rows;
	}

	@Test
	public void realBurstCollapsesToASingleEvent()
	{
		EventClusterer clusterer = new EventClusterer();

		List<EventCluster> started = clusterer.accept(realBurst(), T + 40);

		assertEquals("seventeen sightings are one ent", 1, started.size());
		assertEquals(1, clusterer.getActive(T + 40).size());
		assertEquals(17, started.get(0).getReportCount());
	}

	@Test
	public void burstSplitAcrossPollsStillAlertsOnce()
	{
		EventClusterer clusterer = new EventClusterer();
		List<EventCall> burst = realBurst();

		List<EventCluster> first = clusterer.accept(burst.subList(0, 4), T + 20);
		List<EventCluster> later = clusterer.accept(burst.subList(4, burst.size()), T + 40);

		assertEquals(1, first.size());
		assertTrue("trailing reports must not look like new events", later.isEmpty());
		assertEquals(1, clusterer.getActive(T + 40).size());
	}

	@Test
	public void centreIsAveragedAcrossReports()
	{
		EventClusterer clusterer = new EventClusterer();

		clusterer.accept(Arrays.asList(
			new EventCall(ENT, WORLD, 1380, 3280, T),
			new EventCall(ENT, WORLD, 1390, 3290, T + 1)), T + 2);

		EventCluster cluster = clusterer.getActive(T + 2).get(0);
		assertEquals(1385, cluster.getCentreX());
		assertEquals(3285, cluster.getCentreY());
	}

	@Test
	public void separateTreesAreSeparateEvents()
	{
		EventClusterer clusterer = new EventClusterer();

		List<EventCluster> started = clusterer.accept(Arrays.asList(
			new EventCall(ENT, WORLD, 1383, 3281, T),
			new EventCall(ENT, WORLD, 1383, 3400, T + 2)), T + 3);

		assertEquals(2, started.size());
	}

	@Test
	public void sameSpotOnAnotherWorldIsADifferentEvent()
	{
		EventClusterer clusterer = new EventClusterer();

		List<EventCluster> started = clusterer.accept(Arrays.asList(
			new EventCall(ENT, WORLD, 1383, 3281, T),
			new EventCall(ENT, 302, 1383, 3281, T + 1)), T + 2);

		assertEquals(2, started.size());
	}

	@Test
	public void eventExpiresAfterItsLifetime()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T)), T);

		assertEquals(1, clusterer.getActive(T + 119).size());

		List<EventCluster> ended = clusterer.prune(T + 121);
		assertEquals(1, ended.size());
		assertTrue(clusterer.getActive(T + 121).isEmpty());
	}

	/**
	 * The match window outlives the event, so a straggler arriving after the ent is over must be
	 * absorbed by the finished cluster rather than raising a fresh alert for something the player
	 * can no longer reach.
	 */
	@Test
	public void lateReportDoesNotResurrectAFinishedEvent()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T)), T);
		clusterer.prune(T + 121);

		List<EventCluster> started = clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1384, 3282, T + 118)), T + 125);

		assertTrue("straggler must not create a second event", started.isEmpty());
		assertTrue(clusterer.getActive(T + 125).isEmpty());
	}

	@Test
	public void eventAlreadyOverWhenFirstSeenIsNotAnnounced()
	{
		EventClusterer clusterer = new EventClusterer();

		List<EventCluster> started = clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T)), T + 200);

		assertTrue("nothing to path to -- it finished before we heard", started.isEmpty());
		assertTrue(clusterer.getActive(T + 200).isEmpty());
	}

	@Test
	public void remainingTimeCountsDownFromFirstSighting()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T)), T + 10);

		EventCluster cluster = clusterer.getActive(T + 10).get(0);
		assertEquals(110, cluster.secondsRemaining(T + 10));
		assertEquals(0, cluster.secondsRemaining(T + 500));
	}

	// ---------------------------------------------------------------- per-type lifetimes

	/**
	 * The regression that started this: a single 120s lifetime borrowed from the ent silently
	 * discarded live Rising Roots and Flowering Bush events, which are not 120s events at all.
	 */
	@Test
	public void untimedEventSurvivesPastTheEntLifetime()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall("ROOTS", WORLD, 1383, 3281, T)), T);

		assertEquals("roots is not a two minute event", 1, clusterer.getActive(T + 200).size());
	}

	@Test
	public void entStillExpiresAtTwoMinutes()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T)), T);

		assertEquals(1, clusterer.getActive(T + 119).size());
		assertTrue("the ent's own duration is known and must still apply",
			clusterer.getActive(T + 121).isEmpty());
	}

	@Test
	public void eventTypeAddedUpstreamIsNotDiscarded()
	{
		EventClusterer clusterer = new EventClusterer();

		List<EventCluster> started = clusterer.accept(Collections.singletonList(
			new EventCall("SOME_FUTURE_EVENT", WORLD, 1383, 3281, T)), T);

		assertEquals("an unknown type must never be silently dropped", 1, started.size());
		assertEquals(1, clusterer.getActive(T + 200).size());
	}

	@Test
	public void onlyTheEntReportsACountdown()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Arrays.asList(
			new EventCall(ENT, WORLD, 1383, 3281, T),
			new EventCall("FLOWERS", WORLD, 2705, 3462, T)), T);

		for (EventCluster cluster : clusterer.getActive(T))
		{
			assertEquals(ENT.equals(cluster.getEventType()), cluster.isTimed());
		}
	}

	/**
	 * A long event still collecting reports several minutes in is the same event, not a new one.
	 * The match window has to stretch to the event's own length to see that.
	 */
	@Test
	public void lateReportOnALongEventDoesNotCreateAPhantom()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall("ROOTS", WORLD, 1383, 3281, T)), T);

		List<EventCluster> started = clusterer.accept(Collections.singletonList(
			new EventCall("ROOTS", WORLD, 1384, 3282, T + 250)), T + 250);

		assertTrue("still the same roots event", started.isEmpty());
		assertEquals(1, clusterer.getActive(T + 250).size());
	}

	/**
	 * Display and path order both follow first-called, so with several events running the list on
	 * screen and the destination the path points at never disagree.
	 */
	@Test
	public void activeEventsAreListedOldestFirst()
	{
		EventClusterer clusterer = new EventClusterer();

		clusterer.accept(Arrays.asList(
			new EventCall(ENT, WORLD, 1383, 3281, T + 60),
			new EventCall(ENT, WORLD, 2705, 3462, T)), T + 60);

		List<EventCluster> live = clusterer.getActive(T + 60);

		assertEquals(2, live.size());
		assertEquals("first called must come first", T, live.get(0).getFirstSeen());
		assertEquals(T + 60, live.get(1).getFirstSeen());
	}

	/**
	 * The forestry world concentrates on a handful of popular trees, so the same spot spawns
	 * events repeatedly. A dedupe window wider than the event itself folded the next ent into the
	 * last one's dead cluster and never announced it -- indistinguishable, from the player's seat,
	 * from the plugin simply being late.
	 */
	@Test
	public void aSecondEntAtTheSameTreeIsANewEvent()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T)), T);
		clusterer.prune(T + 121);

		List<EventCluster> started = clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T + 150)), T + 150);

		assertEquals("an ent 150s later cannot be the same 120s event", 1, started.size());
		assertEquals(1, clusterer.getActive(T + 150).size());
	}

	/** The straggler case the above must not break: reported late, but from within the event. */
	@Test
	public void reportFromWithinTheEventStillAbsorbsAfterItEnds()
	{
		EventClusterer clusterer = new EventClusterer();
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1383, 3281, T)), T);
		clusterer.prune(T + 121);

		List<EventCluster> started = clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 1384, 3282, T + 119)), T + 130);

		assertTrue("timestamped inside the event, so it is the same event", started.isEmpty());
	}

	/**
	 * The place name must not move once a call is on screen.
	 * <p>
	 * It used to be derived from the live centroid, which shifts every time another scout reports.
	 * A call that started near the edge of a place's radius could therefore drift out of it and the
	 * overlay would quietly fall back to printing the event's name instead -- the label changing
	 * under the player for no reason they could see.
	 */
	@Test
	public void placeNameSurvivesTheCentroidDrifting()
	{
		EventClusterer clusterer = new EventClusterer();

		// 60 tiles from the Lunar Isle pin: inside its 64 tile radius, but only just.
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 2117, 3963, T)), T);

		EventCluster cluster = clusterer.getActive(T).get(0);
		assertEquals(CallLocation.LUNAR_ISLE, cluster.getLocation());

		// A second scout 20 tiles further out drags the centroid clear of the radius.
		clusterer.accept(Collections.singletonList(
			new EventCall(ENT, WORLD, 2117, 3983, T + 5)), T + 5);

		assertEquals("still one event", 1, clusterer.getActive(T + 5).size());
		assertNull("the drifted centroid matches no place at all",
			CallLocation.nearest(cluster.getCentreX(), cluster.getCentreY()));
		assertEquals("yet the call is still at Lunar Isle",
			CallLocation.LUNAR_ISLE, cluster.getLocation());
	}
}
