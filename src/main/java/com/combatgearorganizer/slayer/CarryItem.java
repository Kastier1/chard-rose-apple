package com.combatgearorganizer.slayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

/**
 * Prayer and storage items worth an inventory slot on a task. Which ones apply to a monster comes from the bundled data
 * (see tools/build_slayer_locations.py, carry_for); the looting bag is added for Wilderness places.
 */
@Getter
public enum CarryItem
{
	BONECRUSHER("bonecrusher", "Bonecrusher", "Crushes the bones they always drop, for Prayer XP",
		ItemID.BONECRUSHER, ItemID.BONECRUSHER, ItemID.BONECRUSHER_NECKLACE),
	ASH_SANCTIFIER("ashSanctifier", "Ash sanctifier", "Scatters the demonic ashes they always drop, for Prayer XP",
		ItemID.ASH_SANCTIFIER, ItemID.ASH_SANCTIFIER),
	ECTOPLASMATOR("ectoplasmator", "Ectoplasmator", "Spectral: Prayer XP for each kill",
		ItemID.SOUL_WARS_ECTOPLASMATOR, ItemID.SOUL_WARS_ECTOPLASMATOR),
	HERB_SACK("herbSack", "Herb sack", "Grimy herbs",
		ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN,
		ItemID.SLAYER_HERB_SACK_SILK, ItemID.SLAYER_HERB_SACK_SILK_OPEN),
	SEED_BOX("seedBox", "Seed box", "Seeds",
		ItemID.SEED_BOX, ItemID.SEED_BOX, ItemID.SEED_BOX_OPEN),
	// The gem sack is a gem bag combined with a gem tote, and holds the same gems
	GEM_BAG("gemBag", "Gem bag", "Uncut gems",
		ItemID.GEM_BAG, ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN, ItemID.GEM_SACK, ItemID.GEM_SACK_OPEN),
	SOUL_BEARER("soulBearer", "Soul bearer", "Ensouled heads",
		ItemID.ARCEUUS_SOULBEARER, ItemID.ARCEUUS_SOULBEARER),
	LOOTING_BAG(null, "Looting bag", "Holds loot in the Wilderness",
		ItemID.LOOTING_BAG, ItemID.LOOTING_BAG, ItemID.LOOTING_BAG_OPEN);

	/** The key in the bundled data; null for items the plugin adds itself. */
	private final String key;
	private final String displayName;
	/** Why it's worth bringing. For drop-based items, what drops. */
	private final String reason;
	/** The picture to show. */
	private final int iconId;
	/** Every version that counts as carrying it (open, silklined, necklace, gem sack). */
	private final int[] itemIds;

	CarryItem(String key, String displayName, String reason, int iconId, int... itemIds)
	{
		this.key = key;
		this.displayName = displayName;
		this.reason = reason;
		this.iconId = iconId;
		this.itemIds = itemIds;
	}

	public static CarryItem byKey(String key)
	{
		for (CarryItem item : values())
		{
			if (item.key != null && item.key.equals(key))
			{
				return item;
			}
		}
		return null;
	}

	/** Which of these items are among the given item ids (inventory and worn). */
	public static Set<CarryItem> carried(Collection<Integer> itemIds)
	{
		Set<CarryItem> carried = EnumSet.noneOf(CarryItem.class);
		for (CarryItem item : values())
		{
			for (int id : item.itemIds)
			{
				if (itemIds.contains(id))
				{
					carried.add(item);
					break;
				}
			}
		}
		return carried;
	}

	/** A suggestion for the panel: the item and its tooltip. */
	@lombok.Value
	public static class Suggestion
	{
		CarryItem item;
		String tooltip;
	}

	/**
	 * What to bring for this monster, in a stable order. The looting bag is added when every place shown is in the
	 * Wilderness. Unknown keys in the data are skipped.
	 */
	public static List<Suggestion> suggest(List<SlayerLocationData.Carry> carry, boolean wildernessOnly)
	{
		List<Suggestion> out = new ArrayList<>();
		for (CarryItem item : values())
		{
			if (item == LOOTING_BAG)
			{
				if (wildernessOnly)
				{
					out.add(new Suggestion(item, item.displayName + ": " + item.reason));
				}
				continue;
			}
			for (SlayerLocationData.Carry c : carry)
			{
				if (item.key.equals(c.getItem()))
				{
					String why = c.getPer() != null
						? item.reason + " drop about " + (c.getPer() <= 1 ? "every kill" : "1 in " + c.getPer() + " kills")
						: item.reason;
					out.add(new Suggestion(item, item.displayName + ": " + why));
					break;
				}
			}
		}
		return out;
	}
}
