package com.entpathfinder;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Launches a dev client with every locally developed plugin loaded at once, rather than just this
 * project's.
 * <p>
 * RuneLite is happy to load several -- {@code loadBuiltin} takes varargs. The obstacle is Gradle:
 * each plugin is an independent build, so one project's classpath knows nothing about its
 * siblings. The {@code runClientAll} task bolts the siblings' build output onto the classpath, and
 * this class picks up whatever actually turned up.
 * <p>
 * Plugins are named as strings and resolved reflectively on purpose. Importing them would make
 * this project fail to compile whenever a sibling is missing or unbuilt, which would couple two
 * otherwise unrelated plugins together for the sake of a dev convenience. Missing ones are simply
 * reported and skipped.
 */
public class DevClient
{
	private static final String[] PLUGINS = {
		"com.entpathfinder.EntPathfinderPlugin",
		"com.doomutilities.DoomUtilitiesPlugin",
	};

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		List<Class<? extends Plugin>> loaded = new ArrayList<>();

		for (String name : PLUGINS)
		{
			try
			{
				loaded.add(Class.forName(name).asSubclass(Plugin.class));
				System.out.println("[dev] loading  " + name);
			}
			catch (ClassNotFoundException e)
			{
				System.out.println("[dev] skipping " + name + "  (not on the classpath -- build it first)");
			}
		}

		if (loaded.isEmpty())
		{
			throw new IllegalStateException("No plugin classes found on the classpath");
		}

		ExternalPluginManager.loadBuiltin(loaded.toArray(new Class[0]));
		RuneLite.main(args);
	}
}
