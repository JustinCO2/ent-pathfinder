package com.entpathfinder;

import java.util.Random;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ReconnectBackoffTest
{
	@Test
	public void ceilingDoublesFromFiveSeconds()
	{
		assertEquals(5_000, ReconnectBackoff.ceilingFor(0));
		assertEquals(10_000, ReconnectBackoff.ceilingFor(1));
		assertEquals(20_000, ReconnectBackoff.ceilingFor(2));
		assertEquals(40_000, ReconnectBackoff.ceilingFor(3));
	}

	@Test
	public void ceilingStopsAtFiveMinutes()
	{
		assertEquals(ReconnectBackoff.MAX_DELAY_MILLIS, ReconnectBackoff.ceilingFor(6));
		assertEquals(ReconnectBackoff.MAX_DELAY_MILLIS, ReconnectBackoff.ceilingFor(1_000));
	}

	/**
	 * When the relay restarts, every connected player drops at the same instant. Their first
	 * reconnects must spread wide enough to stay under the relay object's roughly 1,000 requests a
	 * second, even with a full 2,000-player forestry world.
	 */
	@Test
	public void aFullWorldReconnectingAtOnceStaysUnderTheRelaysRequestRate()
	{
		int players = 2_000;
		int[] perSecond = new int[10];

		// Seeds drawn from one generator, not 0, 1, 2...: java.util.Random gives near-identical
		// first values for consecutive seeds, which would model every player picking the same
		// delay. Real clients each seed from their own clock, so they are independent.
		Random seeds = new Random(42);
		for (int i = 0; i < players; i++)
		{
			long delay = new ReconnectBackoff(new Random(seeds.nextLong())).nextDelayMillis();
			perSecond[(int) (delay / 1000)]++;
		}

		for (int second = 0; second < perSecond.length; second++)
		{
			assertTrue(perSecond[second] + " reconnects in second " + second, perSecond[second] < 1_000);
		}
	}

	/**
	 * Jitter spreads reconnects out, so a relay restart does not bring every player back in the
	 * same instant. Every delay must still fall between half and all of its ceiling.
	 */
	@Test
	public void everyDelayFallsBetweenHalfAndAllOfItsCeiling()
	{
		ReconnectBackoff backoff = new ReconnectBackoff(new Random(42));

		for (int attempt = 0; attempt < 15; attempt++)
		{
			long ceiling = ReconnectBackoff.ceilingFor(attempt);
			long delay = backoff.nextDelayMillis();
			assertTrue("attempt " + attempt + " waited " + delay + "ms", delay >= ceiling / 2 && delay <= ceiling);
		}
	}

	@Test
	public void delaysActuallyVary()
	{
		ReconnectBackoff first = new ReconnectBackoff(new Random(1));
		ReconnectBackoff second = new ReconnectBackoff(new Random(2));

		for (int i = 0; i < 5; i++)
		{
			first.nextDelayMillis();
			second.nextDelayMillis();
		}
		assertTrue("two clients at the same attempt should not wait the same time",
			first.nextDelayMillis() != second.nextDelayMillis());
	}

	@Test
	public void resetReturnsToTheShortestWait()
	{
		ReconnectBackoff backoff = new ReconnectBackoff(new Random(7));
		for (int i = 0; i < 6; i++)
		{
			backoff.nextDelayMillis();
		}

		backoff.reset();

		assertTrue(backoff.nextDelayMillis() <= ReconnectBackoff.FIRST_DELAY_MILLIS);
	}

	/**
	 * The point of the whole class: a client failing for a day must not make anywhere near the
	 * relay's 100,000 daily connection allowance on its own.
	 */
	@Test
	public void aFullDayOfFailuresStaysFarUnderTheDailyAllowance()
	{
		ReconnectBackoff backoff = new ReconnectBackoff(new Random(3));
		long dayMillis = 24 * 60 * 60 * 1000L;

		int attempts = 0;
		for (long elapsed = 0; elapsed < dayMillis; elapsed += backoff.nextDelayMillis())
		{
			attempts++;
		}

		assertTrue("a day of failures made " + attempts + " attempts", attempts < 1_000);
	}
}
