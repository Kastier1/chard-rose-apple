package com.combatgearorganizer.slayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

/**
 * Protection, prayer and storage items worth an inventory slot on a task. Which ones apply to a monster comes from the
 * bundled data (see tools/build_slayer_locations.py, carry_for); the looting bag is added for Wilderness places.
 */
@Getter
public enum CarryItem
{
	// Dragonfire rules from the wiki's Dragonfire page. Either a shield or a potion ticks it: they stack, and super
	// antifire alone blocks a normal dragon's fire. Vorkath's fire still hits up to 60 through super antifire alone and
	// 10 through a shield with a regular antifire; only a shield with super antifire blocks it. The Nightmare Zone
	// shield and the Last Man Standing dragonfire shield are their item's variations; the mounted shield isn't worn, so
	// it's left out.
	DRAGONFIRE("dragonfire", "Dragonfire", "Bring an anti-dragon shield and/or an antifire potion.\nSuper antifire on its"
		+ " own, or a shield with any antifire, blocks a normal dragon's fire fully.\nVorkath needs a shield and super"
		+ " antifire together",
		ItemID.ANTIDRAGONBREATHSHIELD, with(Shields.BOTH_BREATHS,
		ItemID.ANTIDRAGONBREATHSHIELD, ItemID.NZONE_ANTIDRAGONSHIELD,
		// Antifire, antifire mix, extended antifire (and mix), super antifire (and mix), extended super (and mix)
		ItemID._4DOSE1ANTIDRAGON, ItemID._3DOSE1ANTIDRAGON, ItemID._2DOSE1ANTIDRAGON, ItemID._1DOSE1ANTIDRAGON,
		ItemID.BRUTAL_2DOSE1ANTIDRAGON, ItemID.BRUTAL_1DOSE1ANTIDRAGON,
		ItemID._4DOSE2ANTIDRAGON, ItemID._3DOSE2ANTIDRAGON, ItemID._2DOSE2ANTIDRAGON, ItemID._1DOSE2ANTIDRAGON,
		ItemID.BRUTAL_2DOSE2ANTIDRAGON, ItemID.BRUTAL_1DOSE2ANTIDRAGON,
		ItemID._4DOSE3ANTIDRAGON, ItemID._3DOSE3ANTIDRAGON, ItemID._2DOSE3ANTIDRAGON, ItemID._1DOSE3ANTIDRAGON,
		ItemID.BRUTAL_2DOSE3ANTIDRAGON, ItemID.BRUTAL_1DOSE3ANTIDRAGON,
		ItemID._4DOSE4ANTIDRAGON, ItemID._3DOSE4ANTIDRAGON, ItemID._2DOSE4ANTIDRAGON, ItemID._1DOSE4ANTIDRAGON,
		ItemID.BRUTAL_2DOSE4ANTIDRAGON, ItemID.BRUTAL_1DOSE4ANTIDRAGON)),
	// Wyverns' icy breath: only these shields cut it to 10; an anti-dragon shield and antifire potions do nothing
	WYVERN_SHIELD("wyvernShield", "Icy breath", "Wear an elemental, mind, dragonfire or ancient wyvern shield.\nAn"
		+ " anti-dragon shield or antifire potion doesn't help.\nOnly the ancient wyvern shield stops the freeze",
		ItemID.WYVERN_SHIELD, with(Shields.BOTH_BREATHS, ItemID.ELEMENTAL_SHIELD, ItemID.ELEMENTAL_MIND_SHIELD)),
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
	/**
	 * Why it's worth bringing. For drop-based items, what drops; for protection, what blocks the attack, one sentence
	 * per line ("\n").
	 */
	private final String reason;
	/** The picture to show. */
	private final int iconId;
	/** Every version that counts as carrying it (open, silklined, necklace, gem sack, each shield and potion dose). */
	private final int[] itemIds;

	/** Shields that protect against both dragonfire and wyverns' icy breath, in a holder so the constants can use it. */
	private static final class Shields
	{
		static final int[] BOTH_BREATHS = {
			ItemID.DRAGONFIRE_SHIELD, ItemID.DRAGONFIRE_SHIELD_UNCHARGED, ItemID.BR_DRAGONFIRE_SHIELD,
			ItemID.DRAGONFIRE_WARD, ItemID.DRAGONFIRE_WARD_UNCHARGED,
			ItemID.WYVERN_SHIELD, ItemID.WYVERN_SHIELD_UNCHARGED,
		};
	}

	private static int[] with(int[] shared, int... more)
	{
		return IntStream.concat(IntStream.of(shared), IntStream.of(more)).toArray();
	}

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
	public static Set<CarryItem> carried(Collection<Integer> ids)
	{
		Set<Integer> itemIds = new HashSet<>(ids);
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
