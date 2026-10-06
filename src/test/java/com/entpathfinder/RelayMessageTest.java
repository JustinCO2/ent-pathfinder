package com.entpathfinder;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Parsing what the relay pushes, in the exact shape ent-relay sends. */
public class RelayMessageTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void parsesASnapshot()
	{
		String json = "{\"type\":\"calls\",\"serverTime\":1790000000,\"calls\":["
			+ "{\"event_type\":\"ENT\",\"world\":444,\"x_coord\":1383,\"y_coord\":3281,\"plane\":0,"
			+ "\"discovered_time\":1789999990}]}";

		RelayClient.RelayMessage message = RelayClient.parseMessage(GSON, json);

		assertEquals(1790000000L, message.serverTime);
		assertEquals(1, message.calls.size());

		EventCall call = message.calls.get(0);
		assertEquals("ENT", call.getEventType());
		assertEquals(444, call.getWorld());
		assertEquals(1383, call.getXCoord());
		assertEquals(3281, call.getYCoord());
		assertEquals(1789999990L, call.getDiscoveredTime());
	}

	/** An older relay, or the raw API, might still send a scout name. It must simply be ignored. */
	@Test
	public void ignoresAnyScoutIdentityItIsSent()
	{
		RelayClient.RelayMessage message = RelayClient.parseMessage(GSON,
			"{\"type\":\"calls\",\"serverTime\":1,\"calls\":[{\"event_type\":\"ENT\",\"world\":444,"
				+ "\"x_coord\":1,\"y_coord\":2,\"discovered_time\":3,\"rsn\":\"Someone\",\"scout\":\"abc\"}]}");

		assertEquals(1, message.calls.size());
		assertEquals("ENT", message.calls.get(0).getEventType());
	}

	@Test
	public void anEmptySnapshotIsAnEmptyList()
	{
		RelayClient.RelayMessage message = RelayClient.parseMessage(GSON,
			"{\"type\":\"calls\",\"serverTime\":1,\"calls\":[]}");

		assertTrue(message.calls.isEmpty());
	}

	@Test
	public void aMissingCallsFieldIsAnEmptyListNotANull()
	{
		RelayClient.RelayMessage message = RelayClient.parseMessage(GSON,
			"{\"type\":\"calls\",\"serverTime\":1}");

		assertTrue(message.calls.isEmpty());
	}

	/** Leaves room for the relay to add message types later without older plugins misreading them. */
	@Test
	public void ignoresMessagesOfOtherTypes()
	{
		assertNull(RelayClient.parseMessage(GSON, "{\"type\":\"notice\",\"text\":\"hello\"}"));
	}

	@Test
	public void ignoresMalformedText()
	{
		assertNull(RelayClient.parseMessage(GSON, "not json at all"));
		assertNull(RelayClient.parseMessage(GSON, "pong"));
	}
}
