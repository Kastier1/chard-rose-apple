package com.combatgearorganizer.slayer;

import com.combatgearorganizer.CombatGearOrganizerConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Item;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Remembers which wearable items are in the player's bank, per account, from the last time the bank was open, so the
 * slayer panel can say which of their weapons is best for a monster without the bank being open. Also keeps what they
 * carry and wear right now.
 *
 * <p>Only item ids of things that can be worn are kept, and bank placeholders (quantity 0) don't count. Saved in its
 * own config group so saves don't look like a change to the plugin's settings, and always to the account the bank
 * was read for. Call everything on the client thread.
 */
@Singleton
public class BankGear
{
	/** Saved apart from the plugin's settings, like {@link CollectionLog#GROUP}. */
	static final String GROUP = CombatGearOrganizerConfig.GROUP + "bank";
	static final String ITEMS_KEY = "items";
	private static final String SEPARATOR = ",";

	/** Where the bank list is saved. One account (RuneScape profile) at a time; tests use an in-memory one. */
	interface Store
	{
		String currentProfile();

		String get(String profile, String group, String key);

		void set(String profile, String group, String key, String value);
	}

	private final Store store;
	/** Whether an item can be worn; only those are kept. */
	private final IntPredicate wearable;

	private final Set<Integer> bank = new TreeSet<>();
	private final Set<Integer> carried = new TreeSet<>();
	private boolean bankSeen;
	private String loadedProfile;
	private boolean dirty;
	private int version;

	@Inject
	BankGear(ConfigManager configManager, ItemManager itemManager)
	{
		this(new Store()
		{
			@Override
			public String currentProfile()
			{
				return configManager.getRSProfileKey();
			}

			@Override
			public String get(String profile, String group, String key)
			{
				return configManager.getConfiguration(group, profile, key);
			}

			@Override
			public void set(String profile, String group, String key, String value)
			{
				configManager.setConfiguration(group, profile, key, value);
			}
		}, id ->
		{
			ItemStats stats = itemManager.getItemStats(id);
			return stats != null && stats.isEquipable() && stats.getEquipment() != null;
		});
	}

	BankGear(Store store, IntPredicate wearable)
	{
		this.store = store;
		this.wearable = wearable;
	}

	/** Call before reading; saves the previous account's bank and loads this account's when it changes. */
	public void ensureLoaded()
	{
		String profile = store.currentProfile();
		if (Objects.equals(profile, loadedProfile))
		{
			return;
		}
		flush();
		loadedProfile = profile;
		bank.clear();
		bankSeen = false;
		if (profile != null)
		{
			String saved = store.get(profile, GROUP, ITEMS_KEY);
			if (saved != null)
			{
				bankSeen = true;
				read(saved);
			}
		}
		version++;
	}

	/** The bank's items changed (it was opened, or something went in or out). */
	public void onBank(Item[] items)
	{
		ensureLoaded();
		if (loadedProfile == null || items == null)
		{
			return;
		}
		Set<Integer> now = wearableIds(items);
		if (!bankSeen || !now.equals(bank))
		{
			bank.clear();
			bank.addAll(now);
			bankSeen = true;
			dirty = true;
			version++;
		}
	}

	/** The inventory or worn items changed; pass both. */
	public void onCarried(List<Item[]> containers)
	{
		Set<Integer> now = new TreeSet<>();
		for (Item[] items : containers)
		{
			if (items != null)
			{
				now.addAll(wearableIds(items));
			}
		}
		if (!now.equals(carried))
		{
			carried.clear();
			carried.addAll(now);
			version++;
		}
	}

	/** Logged out: what was carried belonged to that account. The bank list stays saved for it. */
	public void onLogout()
	{
		flush();
		if (!carried.isEmpty())
		{
			carried.clear();
			version++;
		}
	}

	/** Saves changes, at most once a tick. */
	public void onGameTick()
	{
		flush();
	}

	/** Saves the bank list to the account it was read for. */
	public void flush()
	{
		if (!dirty || loadedProfile == null)
		{
			return;
		}
		dirty = false;
		store.set(loadedProfile, GROUP, ITEMS_KEY, bank.stream().map(String::valueOf).collect(Collectors.joining(SEPARATOR)));
	}

	/** Whether this account's bank has been seen, now or in an earlier session. */
	public boolean isBankSeen()
	{
		return bankSeen;
	}

	/** Changes whenever the items change, so the panel can tell when to work its suggestions out again. */
	public int getVersion()
	{
		return version;
	}

	/** Wearable items in the bank (when seen), inventory and worn, lowest id first. */
	public List<Integer> owned()
	{
		Set<Integer> all = new TreeSet<>(bank);
		all.addAll(carried);
		return Collections.unmodifiableList(new ArrayList<>(all));
	}

	private Set<Integer> wearableIds(Item[] items)
	{
		Set<Integer> out = new TreeSet<>();
		for (Item item : items)
		{
			// Placeholders have a quantity of 0; empty slots an id of -1
			if (item != null && item.getId() >= 0 && item.getQuantity() > 0 && wearable.test(item.getId()))
			{
				out.add(item.getId());
			}
		}
		return out;
	}

	private void read(String saved)
	{
		for (String s : saved.split(SEPARATOR))
		{
			try
			{
				if (!s.isEmpty())
				{
					bank.add(Integer.parseInt(s.trim()));
				}
			}
			catch (NumberFormatException e)
			{
				// A damaged entry; the next bank visit rewrites the list
			}
		}
	}
}
