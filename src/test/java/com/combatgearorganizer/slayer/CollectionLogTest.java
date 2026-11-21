package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.combatgearorganizer.CombatGearOrganizerConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GameState;
import org.junit.Before;
import org.junit.Test;

public class CollectionLogTest
{
	private static final int HARPOON = 21028;
	private static final int KNIFE = 22804;
	private static final int WHIP = 4151;

	/** Config per profile, like ConfigManager's RuneScape-profile storage. */
	static class MemoryStore implements CollectionLog.Store
	{
		String profile = "account-a";
		final Map<String, String> values = new HashMap<>();
		int writes;

		@Override
		public String currentProfile()
		{
			return profile;
		}

		@Override
		public String get(String profile, String group, String key)
		{
			return values.get(profile + "/" + group + "/" + key);
		}

		@Override
		public void set(String profile, String group, String key, String value)
		{
			writes++;
			values.put(profile + "/" + group + "/" + key, value);
		}

		@Override
		public void unset(String profile, String group, String key)
		{
			values.remove(profile + "/" + group + "/" + key);
		}
	}

	static class FakeGame implements CollectionLog.Game
	{
		boolean searching;
		String me = "Zezima";
		String advLogOwner;
		final Map<Integer, String> names = new HashMap<>();

		FakeGame()
		{
			names.put(HARPOON, "Dragon harpoon");
			names.put(KNIFE, "Dragon knife");
			names.put(WHIP, "Abyssal whip");
		}

		@Override
		public boolean isSearching()
		{
			return searching;
		}

		@Override
		public String localPlayerName()
		{
			return me;
		}

		@Override
		public String itemName(int itemId)
		{
			return names.get(itemId);
		}

		@Override
		public String adventureLogOwner()
		{
			return advLogOwner;
		}
	}

	private MemoryStore store;
	private FakeGame game;
	private CollectionLog clog;

	@Before
	public void setUp()
	{
		store = new MemoryStore();
		game = new FakeGame();
		clog = new CollectionLog(store, game);
		clog.watch(Arrays.asList("Dragon harpoon", "Dragon knife", "Abyssal whip"));
	}

	@Test
	public void nothingKnownBeforeSync()
	{
		assertFalse(CollectionLog.Snapshot.EMPTY.isSynced());
		assertEquals(CollectionLog.Status.UNKNOWN, CollectionLog.Snapshot.EMPTY.status("Dragon harpoon", true));
	}

	@Test
	public void chatMessageOnUnsyncedAccountDoesNotMarkOthersMissing()
	{
		clog.onNewItem("Dragon knife");
		CollectionLog.Snapshot s = clog.snapshot();
		assertFalse(s.isSynced());
		assertEquals(CollectionLog.Status.LOGGED, s.status("Dragon knife", true));
		assertEquals(CollectionLog.Status.UNKNOWN, s.status("Dragon harpoon", true));
	}

	@Test
	public void openingOnePageDoesNotSync()
	{
		clog.onItemDrawn(KNIFE, 1);
		CollectionLog.Snapshot s = clog.snapshot();
		assertFalse(s.isSynced());
		assertEquals(CollectionLog.Status.LOGGED, s.status("Dragon knife", true));
		assertEquals(CollectionLog.Status.UNKNOWN, s.status("Dragon harpoon", true));
	}

	@Test
	public void searchingSyncsEvenWithoutAnyWatchedItem()
	{
		game.searching = true;
		// The search draws some item the slayer data doesn't care about
		game.names.put(995, "Coins");
		clog.onItemDrawn(995, 1);
		CollectionLog.Snapshot s = clog.snapshot();
		assertTrue(s.isSynced());
		assertEquals(CollectionLog.Status.MISSING, s.status("Dragon harpoon", true));
		// Not a log slot at all
		assertEquals(CollectionLog.Status.UNKNOWN, s.status("Sapphire bolt tips", false));
	}

	@Test
	public void searchedLogShowsLoggedAndMissing()
	{
		game.searching = true;
		clog.onItemDrawn(KNIFE, 1);
		CollectionLog.Snapshot s = clog.snapshot();
		assertEquals(CollectionLog.Status.LOGGED, s.status("Dragon knife", true));
		assertEquals(CollectionLog.Status.MISSING, s.status("Dragon harpoon", true));
	}

	@Test
	public void someoneElsesAdventureLogChangesNothing()
	{
		game.advLogOwner = "Lynx Titan";
		clog.onAdventureLogOpened();
		clog.onGameTick();

		game.searching = true;
		clog.onItemDrawn(HARPOON, 1);
		CollectionLog.Snapshot s = clog.snapshot();
		assertFalse(s.isSynced());
		assertEquals(CollectionLog.Status.UNKNOWN, s.status("Dragon harpoon", true));

		// Leaving the house: the player's own log counts again
		clog.onGameStateChanged(GameState.LOADING);
		clog.onItemDrawn(HARPOON, 1);
		assertEquals(CollectionLog.Status.LOGGED, clog.snapshot().status("Dragon harpoon", true));
	}

	@Test
	public void ownAdventureLogCounts()
	{
		game.advLogOwner = "zezima";
		clog.onAdventureLogOpened();
		clog.onGameTick();
		clog.onItemDrawn(HARPOON, 1);
		assertEquals(CollectionLog.Status.LOGGED, clog.snapshot().status("Dragon harpoon", true));
	}

	@Test
	public void flushAfterAccountSwitchWritesToTheOldAccount()
	{
		game.searching = true;
		clog.onItemDrawn(KNIFE, 1);
		// Account changes before the next tick's save
		store.profile = "account-b";
		clog.ensureLoaded();

		assertEquals("dragon knife", store.get("account-a", CollectionLog.GROUP, CollectionLog.OBTAINED_KEY));
		assertEquals("true", store.get("account-a", CollectionLog.GROUP, CollectionLog.SYNCED_KEY));
		assertNull(store.get("account-b", CollectionLog.GROUP, CollectionLog.OBTAINED_KEY));
		assertFalse(clog.snapshot().isSynced());
	}

	@Test
	public void savesOncePerTickAndReloads()
	{
		game.searching = true;
		clog.onItemDrawn(KNIFE, 1);
		clog.onItemDrawn(WHIP, 1);
		clog.onGameTick();
		assertEquals(2, store.writes);
		clog.onGameTick();
		assertEquals(2, store.writes);

		CollectionLog again = new CollectionLog(store, game);
		again.watch(Arrays.asList("Dragon knife", "Abyssal whip"));
		again.ensureLoaded();
		assertTrue(again.snapshot().isSynced());
		assertEquals(CollectionLog.Status.LOGGED, again.snapshot().status("Abyssal whip", true));
	}

	@Test
	public void legacyListIsKeptButMustBeSearchedAgain()
	{
		store.values.put("account-a/" + CombatGearOrganizerConfig.GROUP + "/" + CollectionLog.LEGACY_OBTAINED_KEY, "dragon knife");
		store.values.put("account-a/" + CombatGearOrganizerConfig.GROUP + "/" + CollectionLog.LEGACY_ITEMS_KEY, "dragon knife|dragon harpoon");
		clog.ensureLoaded();
		CollectionLog.Snapshot s = clog.snapshot();
		assertEquals(CollectionLog.Status.LOGGED, s.status("Dragon knife", true));
		assertEquals(CollectionLog.Status.UNKNOWN, s.status("Dragon harpoon", true));
		assertNull(store.get("account-a", CombatGearOrganizerConfig.GROUP, CollectionLog.LEGACY_OBTAINED_KEY));
	}

	@Test
	public void ownerFromAdventureLogTitle()
	{
		assertEquals("Lynx Titan", CollectionLog.matchOwner("<col=ff981f>The Exploits of Lynx Titan</col>"));
		assertNull(CollectionLog.matchOwner("Collection Log"));
	}
}
