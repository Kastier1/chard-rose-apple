package com.combatgearorganizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Works out which concrete item ids should go in a tag's layout.
 *
 * <p>Bank Tags stores a "variation" tag (any charge state or version of an item) as the negative id of the
 * variation group's base item. The base is just the first id in the group, which is often a broken, inactive or
 * uncharged version (Infernal cape (broken), Bow of faerdhinen (inactive)). Laying out or classifying that id gives
 * ghost placeholders and the wrong stats, so each variation tag is turned into the version the player really owns.
 */
public final class TagItemResolver
{
	/** How to look up items. In the plugin this wraps ItemManager and ItemVariationMapping. */
	public interface Items
	{
		/** Unnoted, non-placeholder id. */
		int canonicalize(int itemId);

		/** The variation group's base id (ItemVariationMapping.map). Returns the id itself if it has no group. */
		int variationBase(int itemId);
	}

	private TagItemResolver()
	{
	}

	/**
	 * @param tagKeys        ids from TagManager.getItemsForTag: positive for a plain tag, negative base id for a
	 *                       variation tag
	 * @param existingLayout ids in the tag's current layout (may contain -1)
	 * @param owned          ids the player has right now: bank, worn equipment and inventory
	 * @param fallback       picks a version of a variation group when the player has none and the layout has
	 *                       none (e.g. the version with the best stats). Given the base id.
	 * @return concrete ids, no duplicates
	 */
	public static List<Integer> resolve(Collection<Integer> tagKeys, int[] existingLayout, Collection<Integer> owned,
		Items items, IntUnaryOperator fallback)
	{
		final Set<Integer> plain = new LinkedHashSet<>();
		final Set<Integer> groups = new LinkedHashSet<>();
		for (int key : tagKeys)
		{
			if (key > 0)
			{
				plain.add(items.canonicalize(key));
			}
			else if (key < 0)
			{
				groups.add(-key);
			}
		}

		final Set<Integer> result = new LinkedHashSet<>(plain);

		// Groups that already have a concrete item in the result
		final Set<Integer> groupsFound = new HashSet<>();
		for (int id : plain)
		{
			groupsFound.add(items.variationBase(id));
		}

		// Each version of a tagged group the player owns. The tab shows all of them, so lay them all out.
		for (int id : owned)
		{
			int c = items.canonicalize(id);
			int base = items.variationBase(c);
			if (groups.contains(base))
			{
				result.add(c);
				groupsFound.add(base);
			}
		}

		// Things already laid out: keep them only while they're still tagged, so untagged items drop out.
		// For a variation group, one old entry stands in only when the player has no version of it.
		if (existingLayout != null)
		{
			for (int id : existingLayout)
			{
				if (id <= 0)
				{
					continue;
				}
				int c = items.canonicalize(id);
				int base = items.variationBase(c);
				if (plain.contains(c))
				{
					continue; // already in
				}
				if (groups.contains(base) && groupsFound.add(base))
				{
					result.add(c);
				}
			}
		}

		// Tagged groups with nothing owned or laid out (e.g. tagged, then sold or lost)
		for (int base : groups)
		{
			if (!groupsFound.contains(base))
			{
				result.add(fallback.applyAsInt(base));
			}
		}

		return new ArrayList<>(result);
	}
}
