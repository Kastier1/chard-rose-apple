package com.combatgearorganizer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches RuneLite with this plugin loaded. Run with: ./gradlew run
 */
public class CombatGearOrganizerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CombatGearOrganizerPlugin.class);
		RuneLite.main(args);
	}
}
