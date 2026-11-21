package com.combatgearorganizer.slayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.api.Item;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BankGearTest
{
	/** An in-memory config, one account logged in at a time. */
	private static class MemoryStore implements BankGear.Store
	{
		final Map<String, String> values = new HashMap<>();
		String profile;
		int writes;

		@Override
		public String currentProfile()
		{
			return profile;
		}

		@Override
		public String get(String profile, String group, String key)
		{
			return values.get(profile + "." + group + "." + key);
		}

		@Override
		public void set(String profile, String group, String key, String value)
		{
			writes++;
			values.put(profile + "." + group + "." + key, value);
		}
	}

	/** Items below 100 can be worn; the rest (coins, food, a whip in this test) can't. */
	private static final IntPredicate WEARABLE = id -> id < 100;

	private static Item[] items(int... idQuantity)
	{
		Item[] out = new Item[idQuantity.length / 2];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = new Item(idQuantity[2 * i], idQuantity[2 * i + 1]);
		}
		return out;
	}

	@Test
	public void bankSurvivesARelogPerAccount()
	{
		MemoryStore store = new MemoryStore();
		store.profile = "alice";
		BankGear gear = new BankGear(store, WEARABLE);
		gear.ensureLoaded();
		assertFalse(gear.isBankSeen());
		gear.onBank(items(4151, 1, 995, 1_000_000, 11802, 1));
		// Coins and the made-up unwearable ids are left out
		assertTrue(gear.isBankSeen());
		gear.onBank(items(10, 1, 20, 3, 995, 100, 30, 0));
		assertEquals(Arrays.asList(10, 20), gear.owned());
		gear.onGameTick();
		assertEquals("10,20", store.values.get("alice." + BankGear.GROUP + "." + BankGear.ITEMS_KEY));

		// Logged out and back in, or the client restarted: the list comes back
		BankGear restarted = new BankGear(store, WEARABLE);
		restarted.ensureLoaded();
		assertTrue(restarted.isBankSeen());
		assertEquals(Arrays.asList(10, 20), restarted.owned());

		// Another account has its own bank
		store.profile = "bob";
		restarted.ensureLoaded();
		assertFalse(restarted.isBankSeen());
		assertTrue(restarted.owned().isEmpty());
		restarted.onBank(items(40, 1));
		restarted.onGameTick();
		store.profile = "alice";
		restarted.ensureLoaded();
		assertEquals(Arrays.asList(10, 20), restarted.owned());
		assertEquals("40", store.values.get("bob." + BankGear.GROUP + "." + BankGear.ITEMS_KEY));
	}

	@Test
	public void savesGoToTheAccountTheBankWasReadFor()
	{
		MemoryStore store = new MemoryStore();
		store.profile = "alice";
		BankGear gear = new BankGear(store, WEARABLE);
		gear.onBank(items(10, 1));
		// The account changes before the tick's save
		store.profile = "bob";
		gear.ensureLoaded();
		assertEquals("10", store.values.get("alice." + BankGear.GROUP + "." + BankGear.ITEMS_KEY));
		assertNull(store.values.get("bob." + BankGear.GROUP + "." + BankGear.ITEMS_KEY));
	}

	@Test
	public void placeholdersAndEmptySlotsDontCount()
	{
		MemoryStore store = new MemoryStore();
		store.profile = "alice";
		BankGear gear = new BankGear(store, WEARABLE);
		gear.onBank(items(10, 0, -1, 1, 20, 1));
		assertEquals(Collections.singletonList(20), gear.owned());
		// An empty bank is still a bank seen
		gear.onBank(new Item[0]);
		assertTrue(gear.isBankSeen());
		assertTrue(gear.owned().isEmpty());
		gear.flush();
		assertEquals("", store.values.get("alice." + BankGear.GROUP + "." + BankGear.ITEMS_KEY));
		BankGear again = new BankGear(store, WEARABLE);
		again.ensureLoaded();
		assertTrue(again.isBankSeen());
	}

	@Test
	public void carriedItemsCountWithoutTheBank()
	{
		MemoryStore store = new MemoryStore();
		store.profile = "alice";
		BankGear gear = new BankGear(store, WEARABLE);
		gear.ensureLoaded();
		int v = gear.getVersion();
		gear.onCarried(Arrays.asList(items(50, 1, 2000, 5), items(60, 1)));
		assertEquals(Arrays.asList(50, 60), gear.owned());
		assertTrue(gear.getVersion() > v);
		// Eating food doesn't change the gear, so nothing is redrawn
		v = gear.getVersion();
		gear.onCarried(Arrays.asList(items(50, 1), items(60, 1)));
		assertEquals(v, gear.getVersion());
		assertFalse(gear.isBankSeen());
		// Carried items aren't saved as bank
		gear.flush();
		assertEquals(0, store.writes);
	}

	@Test
	public void loggingOutForgetsWhatWasCarried()
	{
		MemoryStore store = new MemoryStore();
		store.profile = "alice";
		BankGear gear = new BankGear(store, WEARABLE);
		gear.onBank(items(10, 1));
		gear.onCarried(Collections.singletonList(items(50, 1)));
		gear.onLogout();
		// The bank was saved on the way out; the carried bow belonged to alice's character
		assertEquals("10", store.values.get("alice." + BankGear.GROUP + "." + BankGear.ITEMS_KEY));
		store.profile = "bob";
		gear.ensureLoaded();
		assertTrue(gear.owned().isEmpty());
	}

	@Test
	public void noAccountNoSave()
	{
		MemoryStore store = new MemoryStore();
		BankGear gear = new BankGear(store, WEARABLE);
		gear.onBank(items(10, 1));
		assertFalse(gear.isBankSeen());
		gear.flush();
		assertEquals(0, store.writes);
	}

	@Test
	public void unchangedBankIsNotSavedAgain()
	{
		MemoryStore store = new MemoryStore();
		store.profile = "alice";
		BankGear gear = new BankGear(store, WEARABLE);
		gear.onBank(items(10, 1));
		gear.flush();
		gear.onBank(items(10, 2));
		gear.flush();
		assertEquals(1, store.writes);
		// A damaged saved entry is skipped
		store.values.put("carol." + BankGear.GROUP + "." + BankGear.ITEMS_KEY, "10,x,20");
		store.profile = "carol";
		gear.ensureLoaded();
		assertEquals(Arrays.asList(10, 20), gear.owned());
	}
}
