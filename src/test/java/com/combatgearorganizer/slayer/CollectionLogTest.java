package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public class CollectionLogTest
{
	@Test
	public void statusByName()
	{
		CollectionLog.Snapshot clog = new CollectionLog.Snapshot(
			new HashSet<>(Arrays.asList("dragon harpoon", "dragon knife")),
			Collections.singleton("dragon knife"));
		assertTrue(clog.isSynced());
		assertEquals(CollectionLog.Status.MISSING, clog.status("Dragon harpoon", true));
		assertEquals(CollectionLog.Status.LOGGED, clog.status("Dragon knife", true));
		// Logged items are the only ones the game reports, so a log slot never seen is missing
		assertEquals(CollectionLog.Status.MISSING, clog.status("Dragon thrownaxe", true));
		// Not a collection log item
		assertEquals(CollectionLog.Status.UNKNOWN, clog.status("Sapphire bolt tips", false));
	}

	@Test
	public void nothingKnownBeforeSync()
	{
		assertFalse(CollectionLog.Snapshot.EMPTY.isSynced());
		assertEquals(CollectionLog.Status.UNKNOWN, CollectionLog.Snapshot.EMPTY.status("Dragon harpoon", true));
	}
}
