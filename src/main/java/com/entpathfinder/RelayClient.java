package com.entpathfinder;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Holds one WebSocket to the call relay and hands every snapshot it pushes to a listener.
 * <p>
 * The plugin decides <em>whether</em> to be connected; this class only handles <em>how</em>:
 * opening the connection, reading messages, and reconnecting safely when it drops.
 * <p>
 * The relay's free plan allows 100,000 connections a day across every player, so three things stop
 * any one client from costing more than its share:
 * <ul>
 * <li>reconnects back off exponentially with jitter (see {@link ReconnectBackoff})</li>
 * <li>the backoff only resets once a connection has stayed up for a while, so a relay that accepts
 * connections and then immediately drops them cannot pull clients into a tight loop</li>
 * <li>keep-alives are WebSocket protocol pings, which the relay's runtime answers on its own without
 * waking the relay</li>
 * </ul>
 */
@Slf4j
public class RelayClient
{
	/** How long a connection must stay up before the next drop counts as a fresh start. */
	static final long STABLE_CONNECTION_MILLIS = 60_000;

	private static final long PING_INTERVAL_SECONDS = 45;

	private static final int CLOSE_NORMAL = 1000;

	enum Status
	{
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
		WAITING_TO_RETRY
	}

	/**
	 * Called from OkHttp's threads. Implementations must be quick and must not call back into the
	 * client.
	 */
	interface Listener
	{
		void onCalls(List<EventCall> calls, long serverTimeSeconds);

		void onStatusChanged(Status status);
	}

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Listener listener;
	private final ReconnectBackoff backoff = new ReconnectBackoff(new Random());

	// Everything below is guarded by "this".
	private String url;
	private WebSocket socket;
	private ScheduledFuture<?> pendingRetry;
	private long connectedAtMillis;
	private Status status = Status.DISCONNECTED;
	private boolean closed;

	RelayClient(OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor, Listener listener)
	{
		this.httpClient = httpClient.newBuilder()
			.pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
		this.executor = executor;
		this.listener = listener;
	}

	/**
	 * Be connected to {@code url}. Safe to call as often as you like: if already connected, or
	 * already waiting to retry the same address, it does nothing.
	 */
	synchronized void connect(String url)
	{
		if (closed)
		{
			return;
		}
		if (url.equals(this.url) && status != Status.DISCONNECTED)
		{
			return;
		}

		closeCurrent();
		this.url = url;
		backoff.reset();
		open();
	}

	/** Close the connection and stop retrying. A later connect() opens it again. */
	synchronized void disconnect()
	{
		if (status == Status.DISCONNECTED)
		{
			return;
		}

		closeCurrent();
		url = null;
		setStatus(Status.DISCONNECTED);
	}

	/**
	 * Close for good: unlike {@link #disconnect()}, every later {@link #connect} is ignored.
	 * <p>
	 * For plugin shutdown. The plugin's tick can still be mid-run when the plugin is turned off, and
	 * if its connect() landed after a plain disconnect() it would reopen a connection -- with its
	 * retries -- that nothing would ever close again.
	 */
	synchronized void close()
	{
		closed = true;
		disconnect();
	}

	synchronized Status getStatus()
	{
		return status;
	}

	private void open()
	{
		connectedAtMillis = 0;
		setStatus(Status.CONNECTING);

		try
		{
			Request request = new Request.Builder().url(url).build();
			socket = httpClient.newWebSocket(request, new Callbacks());
		}
		catch (IllegalArgumentException e)
		{
			// A malformed relay address. Retrying will not fix it, but backing off keeps the
			// status honest without spinning.
			log.warn("Invalid relay URL: {}", url);
			socket = null;
			scheduleRetry();
		}
	}

	private void closeCurrent()
	{
		if (pendingRetry != null)
		{
			pendingRetry.cancel(false);
			pendingRetry = null;
		}
		if (socket != null)
		{
			socket.close(CLOSE_NORMAL, null);
			socket = null;
		}
	}

	private synchronized void handleOpen(WebSocket opened)
	{
		if (opened != socket)
		{
			return;
		}
		connectedAtMillis = System.currentTimeMillis();
		setStatus(Status.CONNECTED);
	}

	private void handleMessage(WebSocket from, String text)
	{
		synchronized (this)
		{
			if (from != socket)
			{
				return;
			}
		}

		RelayMessage message = parseMessage(gson, text);
		if (message != null)
		{
			// Outside the lock, so a slow listener can never hold up connecting or closing.
			listener.onCalls(message.calls, message.serverTime);
		}
	}

	/** The connection ended, cleanly or not. Reconnect, unless it was closed on purpose. */
	private synchronized void handleDrop(WebSocket dropped)
	{
		if (dropped != socket)
		{
			// A socket already replaced, or closed deliberately by connect() or disconnect().
			return;
		}
		socket = null;

		if (url == null)
		{
			return;
		}
		if (wasStable())
		{
			backoff.reset();
		}
		scheduleRetry();
	}

	/**
	 * Only a connection that lasted a while earns a fast reconnect. Resetting on every successful
	 * open would let a relay that accepts and then immediately drops connections pull every client
	 * into reconnecting once a second.
	 */
	private boolean wasStable()
	{
		return connectedAtMillis > 0
			&& System.currentTimeMillis() - connectedAtMillis >= STABLE_CONNECTION_MILLIS;
	}

	private void scheduleRetry()
	{
		long delay = backoff.nextDelayMillis();
		log.debug("Relay connection lost, retrying in {}ms", delay);

		setStatus(Status.WAITING_TO_RETRY);
		pendingRetry = executor.schedule(this::retry, delay, TimeUnit.MILLISECONDS);
	}

	private synchronized void retry()
	{
		pendingRetry = null;
		if (!closed && url != null && socket == null)
		{
			open();
		}
	}

	private void setStatus(Status newStatus)
	{
		if (status != newStatus)
		{
			status = newStatus;
			listener.onStatusChanged(newStatus);
		}
	}

	/** Parse one relay message. Returns null for anything that is not a well-formed snapshot. */
	static RelayMessage parseMessage(Gson gson, String text)
	{
		try
		{
			RelayMessage message = gson.fromJson(text, RelayMessage.class);
			if (message == null || !RelayMessage.TYPE_CALLS.equals(message.type))
			{
				return null;
			}
			if (message.calls == null)
			{
				message.calls = Collections.emptyList();
			}
			return message;
		}
		catch (JsonParseException e)
		{
			log.debug("Ignoring malformed relay message", e);
			return null;
		}
	}

	/** One push from the relay: every current call matching this client's subscription. */
	static class RelayMessage
	{
		static final String TYPE_CALLS = "calls";

		@SerializedName("type")
		String type;

		@SerializedName("serverTime")
		long serverTime;

		@SerializedName("calls")
		List<EventCall> calls;
	}

	private class Callbacks extends WebSocketListener
	{
		@Override
		public void onOpen(WebSocket webSocket, Response response)
		{
			handleOpen(webSocket);
		}

		@Override
		public void onMessage(WebSocket webSocket, String text)
		{
			handleMessage(webSocket, text);
		}

		@Override
		public void onClosing(WebSocket webSocket, int code, String reason)
		{
			// Complete the close handshake the relay started.
			webSocket.close(CLOSE_NORMAL, null);
		}

		@Override
		public void onClosed(WebSocket webSocket, int code, String reason)
		{
			handleDrop(webSocket);
		}

		@Override
		public void onFailure(WebSocket webSocket, Throwable t, Response response)
		{
			log.debug("Relay connection failed", t);
			handleDrop(webSocket);
		}
	}
}
