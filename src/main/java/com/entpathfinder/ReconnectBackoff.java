package com.entpathfinder;

import java.util.Random;

/**
 * How long to wait before reconnecting to the relay.
 * <p>
 * The relay runs on a free plan that allows 100,000 connections a day, shared by every player. A
 * client reconnecting in a tight loop would use most of that on its own, so each retry waits twice
 * as long as the last, up to a few minutes.
 * <p>
 * Every wait is also randomised. When the relay restarts and every player drops at the same moment,
 * they come back spread out instead of all at once.
 * <p>
 * The first wait is 2.5-5s rather than about a second because of that restart case. A relay object
 * handles roughly 1,000 requests a second; a full forestry world is 2,000 players, and spreading
 * their return over 2.5s keeps it to about 800 a second. Nothing is lost by waiting -- each push is
 * a full snapshot, so the first one after reconnecting carries everything.
 */
final class ReconnectBackoff
{
	static final long FIRST_DELAY_MILLIS = 5_000;
	static final long MAX_DELAY_MILLIS = 5 * 60_000;

	/** Far past the point where the cap applies, and far short of overflowing a long. */
	private static final int MAX_DOUBLINGS = 20;

	private final Random random;
	private int attempts;

	ReconnectBackoff(Random random)
	{
		this.random = random;
	}

	/** The wait before the next attempt: somewhere between half and all of the current ceiling. */
	long nextDelayMillis()
	{
		long ceiling = ceilingFor(attempts);
		attempts++;

		long floor = ceiling / 2;
		return floor + (long) (random.nextDouble() * (ceiling - floor));
	}

	/** Start again from the shortest wait, after a connection has proven itself. */
	void reset()
	{
		attempts = 0;
	}

	/** 5s, 10s, 20s, 40s... capped at MAX_DELAY_MILLIS. */
	static long ceilingFor(int attempts)
	{
		long doubled = FIRST_DELAY_MILLIS << Math.min(attempts, MAX_DOUBLINGS);
		return Math.min(doubled, MAX_DELAY_MILLIS);
	}
}
