package com.combatgearorganizer.slayer;

import com.combatgearorganizer.ItemValueMode;
import com.combatgearorganizer.ItemValues;
import java.util.Collections;
import java.util.Map;
import lombok.Value;

/**
 * Item ids and values for the drops on show, read on the client thread and handed to the panel as an immutable copy.
 * Drops missing from it are shown as text.
 */
@Value
public class DropItems
{
	public static final DropItems EMPTY = new DropItems(Collections.emptyMap(), ItemValueMode.GRAND_EXCHANGE);

	/** By drop name, as {@link CollectionLog#key}. */
	Map<String, Item> items;
	/** The setting the values were read with. */
	ItemValueMode mode;

	@Value
	public static class Item
	{
		int itemId;
		/** What one is worth with {@link #mode}; 0 when nothing. */
		long value;
		boolean tradeable;
	}

	/** The item for a drop name, or null when it couldn't be matched to an item. */
	public Item get(String name)
	{
		return items.get(CollectionLog.key(name));
	}

	/**
	 * The value line of a drop's tooltip: "GE value: 1.2M", "High alch: 30K-36K (1.2K each)",
	 * "GE value: not tradeable".
	 *
	 * @param quantity [lowest, highest] stack size; null for one
	 */
	public static String valueText(ItemValueMode mode, Item item, int[] quantity)
	{
		String label = mode.getLabel() + ": ";
		if (item.getValue() <= 0)
		{
			return label + (mode == ItemValueMode.GRAND_EXCHANGE && !item.isTradeable() ? "not tradeable" : "none");
		}
		long each = item.getValue();
		if (quantity == null || quantity.length == 0 || quantity[quantity.length - 1] <= 1)
		{
			return label + ItemValues.format(each);
		}
		int lo = quantity[0];
		int hi = quantity[quantity.length - 1];
		String stack = lo == hi ? ItemValues.format(each * lo)
			: ItemValues.format(each * lo) + "-" + ItemValues.format(each * hi);
		return label + stack + " (" + ItemValues.format(each) + " each)";
	}

	/** "25" or "25-30"; null for a single item. */
	public static String quantityText(int[] quantity)
	{
		if (quantity == null || quantity.length == 0 || quantity[quantity.length - 1] <= 1)
		{
			return null;
		}
		int lo = quantity[0];
		int hi = quantity[quantity.length - 1];
		return lo == hi ? Integer.toString(lo) : lo + "-" + hi;
	}
}
