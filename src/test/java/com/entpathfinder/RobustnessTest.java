package com.entpathfinder;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import okhttp3.OkHttpClient;
import org.junit.Test;

/** Failure modes that would otherwise be silent: a reopened connection, a nonsense timestamp. */
public class RobustnessTest
{
	private static final long NOW = 1_790_000_000L;

	/**
	 * After the plugin shuts down, a tick that was already running may still call connect(). It
	 * must not reopen a connection, because nothing would ever close it again.
	 */
	@Test
	public void aClosedRelayClientCannotBeReopened()
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			RelayClient client = new RelayClient(new OkHttpClient(), new Gson(), executor, new IgnoringListener());

			client.close();
			client.connect("ws://127.0.0.1:1/ws");

			assertEquals(RelayClient.Status.DISCONNECTED, client.getStatus());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void aRealServerTimeIsTrusted()
	{
		assertTrue(EntPathfinderPlugin.isPlausibleServerTime(NOW, NOW));
		assertTrue("a PC clock a few minutes out is still corrected for",
			EntPathfinderPlugin.isPlausibleServerTime(NOW + 300, NOW));
	}

	@Test
	public void aMissingOrGarbledServerTimeIsNot()
	{
		assertFalse("missing field, parsed as zero", EntPathfinderPlugin.isPlausibleServerTime(0, NOW));
		assertFalse(EntPathfinderPlugin.isPlausibleServerTime(-5, NOW));
		assertFalse("years out", EntPathfinderPlugin.isPlausibleServerTime(NOW * 2, NOW));
	}

	private static class IgnoringListener implements RelayClient.Listener
	{
		@Override
		public void onCalls(List<EventCall> calls, long serverTimeSeconds)
		{
		}

		@Override
		public void onStatusChanged(RelayClient.Status status)
		{
		}
	}
}
