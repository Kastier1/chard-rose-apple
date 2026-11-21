package com.combatgearorganizer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * The one place the plugin asks what an item is worth, using the "Item value" setting: Grand Exchange price or high
 * alchemy value. Call {@link #value} on the client thread.
 */
@Singleton
public class ItemValues
{
	private final ItemManager itemManager;
	private final CombatGearOrganizerConfig config;
	/** High alchemy values never change, so each is read once. */
	private final Map<Integer, Integer> haPrices = new ConcurrentHashMap<>();

	@Inject
	public ItemValues(ItemManager itemManager, CombatGearOrganizerConfig config)
	{
		this.itemManager = itemManager;
		this.config = config;
	}

	/** The setting in use. */
	public ItemValueMode mode()
	{
		ItemValueMode mode = config.itemValue();
		return mode != null ? mode : ItemValueMode.GRAND_EXCHANGE;
	}

	/** What one of the item is worth with the current setting; 0 when it has no value. Client thread only. */
	public long value(int itemId)
	{
		return value(mode(), itemId);
	}

	/** What one of the item is worth with the given setting; 0 when it has no value. Client thread only. */
	public long value(ItemValueMode mode, int itemId)
	{
		int id = itemManager.canonicalize(itemId);
		long money = moneyValue(id);
		if (money >= 0)
		{
			return money;
		}
		if (mode == ItemValueMode.HIGH_ALCHEMY)
		{
			return haPrices.computeIfAbsent(id, i -> itemManager.getItemComposition(i).getHaPrice());
		}
		return itemManager.getItemPrice(id);
	}

	/**
	 * What coins and platinum tokens are worth, whatever the setting: casting High Level Alchemy on coins gives
	 * nothing, but they are still money. -1 for anything else.
	 */
	static long moneyValue(int canonicalId)
	{
		switch (canonicalId)
		{
			case ItemID.COINS:
				return 1;
			case ItemID.PLATINUM:
				return 1000;
			default:
				return -1;
		}
	}

	/** Short value text, the way RuneLite writes stack sizes: "999", "35K", "1.2M". */
	public static String format(long value)
	{
		return QuantityFormatter.quantityToStackSize(value);
	}
}
