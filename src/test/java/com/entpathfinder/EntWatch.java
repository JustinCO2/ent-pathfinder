package com.entpathfinder;

import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/**
 * Headless client for the call relay. Needs no game client and no login.
 * <p>
 * Uses the plugin's real {@link RelayClient} and {@link EventClusterer}, so it exercises the
 * connection, reconnect backoff, message parsing and clustering exactly as the plugin does. Point
 * it at a local relay ({@code npm run dev} in ent-relay) or a deployed one.
 * <pre>
 *   gradlew entWatch                                   local relay, all events
 *   gradlew entWatch -Ptype=ENT -Pworld=444            local relay, ents on 444
 *   gradlew entWatch -Prelay=wss://.../ws              a deployed relay
 * </pre>
 */
public class EntWatch
{
	private static final DateTimeFormatter CLOCK =
		DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private static final EventClusterer clusterer = new EventClusterer();

	public static void main(String[] args)
	{
		String relay = argOrDefault(args, 0, "ws://127.0.0.1:8787/ws");
		String type = argOrDefault(args, 1, "ANY").toUpperCase();
		String world = argOrDefault(args, 2, "");

		String address = relay + "?type=" + type + (world.isEmpty() ? "" : "&world=" + world);
		System.out.println("Connecting to " + address + "  (Ctrl-C to stop)\n");

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		RelayClient client = new RelayClient(new OkHttpClient(), new Gson(), executor, new Printer());
		client.connect(address);

		// Expire finished events once a second, as the plugin's tick does.
		executor.scheduleWithFixedDelay(EntWatch::pruneAndReport, 1, 1, TimeUnit.SECONDS);
	}

	private static class Printer implements RelayClient.Listener
	{
		@Override
		public void onCalls(List<EventCall> calls, long serverTimeSeconds)
		{
			synchronized (clusterer)
			{
				for (EventCluster cluster : clusterer.accept(calls, serverTimeSeconds))
				{
					log(serverTimeSeconds, "NEW    " + describe(cluster, serverTimeSeconds));
				}
			}
		}

		@Override
		public void onStatusChanged(RelayClient.Status status)
		{
			log(System.currentTimeMillis() / 1000L, "STATUS " + status);
		}
	}

	private static void pruneAndReport()
	{
		long now = System.currentTimeMillis() / 1000L;
		synchronized (clusterer)
		{
			for (EventCluster cluster : clusterer.prune(now))
			{
				log(now, "ENDED  " + cluster);
			}
		}
	}

	private static String describe(EventCluster cluster, long now)
	{
		CallLocation where = cluster.getLocation();
		String place = where == null ? "" : " at " + where.getDisplayName();
		String time = cluster.isTimed() ? cluster.secondsRemaining(now) + "s left" : cluster.age(now) + "s old";
		return cluster + place + "  " + time;
	}

	private static void log(long epochSeconds, String text)
	{
		System.out.println(CLOCK.format(Instant.ofEpochSecond(epochSeconds)) + "  " + text);
	}

	private static String argOrDefault(String[] args, int index, String fallback)
	{
		return args.length > index && !args[index].isEmpty() ? args[index] : fallback;
	}
}
