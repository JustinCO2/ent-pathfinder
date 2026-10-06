package com.entpathfinder;

import java.util.regex.Pattern;

/**
 * Chat messages that tell us where the player is up to in an ent event.
 * <p>
 * Kept apart from the plugin so the matching can be unit tested without a client.
 */
final class EntChatSignals
{
	/**
	 * End of the Friendly Ent event for this player: "Well done, you've given 5 entlings haircuts!"
	 * <p>
	 * Matched loosely on purpose. The "Well done," prefix is dropped, the apostrophe accepts both
	 * ASCII and the typographic form, and the nouns accept singular -- the wiki transcript only
	 * documents the plural template, so the one-entling wording is unverified and worth tolerating
	 * rather than missing. The remainder is distinctive enough that nothing else will match it.
	 */
	private static final Pattern HAIRCUTS_DONE = Pattern.compile(
		"you['’]ve given \\d+ entlings? haircuts?", Pattern.CASE_INSENSITIVE);

	private EntChatSignals()
	{
	}

	/**
	 * @param message chat text with any colour tags already stripped
	 */
	static boolean isEntEventComplete(String message)
	{
		return message != null && HAIRCUTS_DONE.matcher(message).find();
	}
}
