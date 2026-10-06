package com.entpathfinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class EntChatSignalsTest
{
	@Test
	public void matchesTheDocumentedCompletionMessage()
	{
		assertTrue(EntChatSignals.isEntEventComplete(
			"Well done, you've given 5 entlings haircuts!"));
	}

	@Test
	public void matchesATypographicApostrophe()
	{
		assertTrue("the client may use a curly apostrophe", EntChatSignals.isEntEventComplete(
			"Well done, you’ve given 5 entlings haircuts!"));
	}

	@Test
	public void matchesSingularWording()
	{
		// Only the plural template is documented, so the one-entling case is a guess worth
		// tolerating: missing it would strand the event on screen forever.
		assertTrue(EntChatSignals.isEntEventComplete(
			"Well done, you've given 1 entling haircut!"));
	}

	@Test
	public void matchesRegardlessOfCaseOrSurroundingText()
	{
		assertTrue(EntChatSignals.isEntEventComplete(
			"WELL DONE, YOU'VE GIVEN 12 ENTLINGS HAIRCUTS!"));
	}

	@Test
	public void ignoresOtherForestryMessages()
	{
		assertFalse(EntChatSignals.isEntEventComplete("You get some logs."));
		assertFalse(EntChatSignals.isEntEventComplete(
			"You've been awarded 5 Anima-infused bark."));
		assertFalse(EntChatSignals.isEntEventComplete(
			"As you weren't chopping near the event when it started, "
				+ "you aren't eligible for full rewards."));
	}

	@Test
	public void ignoresNullAndEmpty()
	{
		assertFalse(EntChatSignals.isEntEventComplete(null));
		assertFalse(EntChatSignals.isEntEventComplete(""));
	}
}
