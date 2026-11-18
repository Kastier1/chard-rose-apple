package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;
import org.junit.BeforeClass;
import org.junit.Test;

public class CarryItemTest
{
	private static SlayerLocationData data;

	@BeforeClass
	public static void load() throws Exception
	{
		try (Reader r = new InputStreamReader(SlayerHelper.class.getResourceAsStream("slayer_locations.json"), StandardCharsets.UTF_8))
		{
			data = new Gson().fromJson(r, SlayerLocationData.class);
		}
	}

	private static SlayerLocationData.Variant variant(String task, String name)
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, task);
		assertNotNull(task, t);
		return t.getVariants().stream().filter(v -> v.getName().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError(task + " / " + name));
	}

	private static Set<CarryItem> suggested(SlayerLocationData.Variant v, boolean wilderness)
	{
		return CarryItem.suggest(v.getCarry(), wilderness).stream().map(CarryItem.Suggestion::getItem)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(CarryItem.class)));
	}

	@Test
	public void everyCarryKeyInTheDataIsKnown()
	{
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				for (SlayerLocationData.Carry c : v.getCarry())
				{
					assertNotNull(t.getName() + " / " + v.getName() + ": " + c.getItem(), CarryItem.byKey(c.getItem()));
				}
			}
		}
	}

	@Test
	public void demonsGetTheAshSanctifier()
	{
		assertTrue(suggested(variant("Abyssal demons", "Abyssal demon"), false).contains(CarryItem.ASH_SANCTIFIER));
		assertTrue(suggested(variant("Greater demons", "Greater demon"), false).contains(CarryItem.ASH_SANCTIFIER));
		assertTrue(suggested(variant("Bloodveld", "Bloodveld"), false).contains(CarryItem.ASH_SANCTIFIER));
		assertFalse(suggested(variant("Abyssal demons", "Abyssal demon"), false).contains(CarryItem.BONECRUSHER));
	}

	@Test
	public void skotizoGetsNoAshSanctifier()
	{
		// The wiki: the sanctifier works on demonic ashes from every demon except Skotizo
		assertFalse(suggested(variant("Greater demons", "Skotizo"), false).contains(CarryItem.ASH_SANCTIFIER));
		assertFalse(suggested(variant("Black demons", "Skotizo"), false).contains(CarryItem.ASH_SANCTIFIER));
	}

	private static Integer per(SlayerLocationData.Variant v, String key)
	{
		return v.getCarry().stream().filter(c -> c.getItem().equals(key)).map(SlayerLocationData.Carry::getPer)
			.findFirst().orElse(null);
	}

	@Test
	public void versionsOfAMonsterAreNotAddedTogether()
	{
		// The abyssal demon page has two drop tables, each about 1 herb in 4 to 7 kills; added together they'd be 1 in 2
		Integer herbs = per(variant("Abyssal demons", "Abyssal demon"), "herbSack");
		assertNotNull(herbs);
		assertTrue("herbs 1 in " + herbs, herbs >= 3);
		// Each of the ankou's tables drops herbs about 1 in 33 kills: not worth a slot
		assertFalse(suggested(variant("Ankou", "Ankou"), false).contains(CarryItem.HERB_SACK));
	}

	@Test
	public void crystalTeleportSeedsDontNeedTheSeedBox()
	{
		// Elves drop crystal teleport seeds 25/128, but they aren't farming seeds
		assertFalse(suggested(variant("Elves", "Elf Archer"), false).contains(CarryItem.SEED_BOX));
	}

	@Test
	public void spectralBoneDroppersGetBoth()
	{
		Set<CarryItem> ankou = suggested(variant("Ankou", "Ankou"), false);
		assertTrue(ankou.contains(CarryItem.BONECRUSHER));
		assertTrue(ankou.contains(CarryItem.ECTOPLASMATOR));
		assertTrue(suggested(variant("Aberrant spectres", "Aberrant spectre"), false).contains(CarryItem.ECTOPLASMATOR));
	}

	@Test
	public void storageFromDropRates()
	{
		assertTrue(suggested(variant("Nechryael", "Nechryael"), false).contains(CarryItem.SEED_BOX));
		assertTrue(suggested(variant("Aberrant spectres", "Aberrant spectre"), false).contains(CarryItem.HERB_SACK));
		// Gargoyles drop no bones, ashes, herbs or seeds worth a slot
		assertTrue(suggested(variant("Gargoyles", "Gargoyle"), false).isEmpty());
	}

	@Test
	public void lootingBagOnlyForWildernessPlaces()
	{
		SlayerLocationData.Variant demon = variant("Abyssal demons", "Abyssal demon");
		assertFalse(suggested(demon, false).contains(CarryItem.LOOTING_BAG));
		assertTrue(suggested(demon, true).contains(CarryItem.LOOTING_BAG));
	}

	@Test
	public void tooltipSaysHowOften()
	{
		SlayerLocationData.Carry herbs = new SlayerLocationData.Carry();
		herbs.setItem("herbSack");
		herbs.setPer(12);
		SlayerLocationData.Carry bones = new SlayerLocationData.Carry();
		bones.setItem("bonecrusher");
		SlayerLocationData.Carry unknown = new SlayerLocationData.Carry();
		unknown.setItem("fishBarrel");
		List<CarryItem.Suggestion> s = CarryItem.suggest(Arrays.asList(unknown, herbs, bones), false);
		assertEquals(2, s.size());
		// Fixed order: prayer items first
		assertEquals(CarryItem.BONECRUSHER, s.get(0).getItem());
		assertEquals("Herb sack: Grimy herbs drop about 1 in 12 kills", s.get(1).getTooltip());
	}

	@Test
	public void carriedCountsEveryVersion()
	{
		assertEquals(EnumSet.of(CarryItem.BONECRUSHER, CarryItem.HERB_SACK),
			CarryItem.carried(Arrays.asList(ItemID.BONECRUSHER_NECKLACE, ItemID.SLAYER_HERB_SACK_SILK_OPEN, 995)));
		assertEquals(EnumSet.of(CarryItem.SEED_BOX), CarryItem.carried(Collections.singletonList(ItemID.SEED_BOX_OPEN)));
		// A gem sack holds the gem bag's gems
		assertEquals(EnumSet.of(CarryItem.GEM_BAG), CarryItem.carried(Collections.singletonList(ItemID.GEM_SACK_OPEN)));
		assertTrue(CarryItem.carried(Collections.emptyList()).isEmpty());
	}

	@Test
	public void dragonsGetDragonfireProtection()
	{
		String[][] dragons = {
			{"Blue dragons", "Blue dragon"}, {"Blue dragons", "Brutal blue dragon"}, {"Blue dragons", "Vorkath"},
			{"Black dragons", "Black dragon"}, {"Black dragons", "Brutal black dragon"},
			{"Black dragons", "King Black Dragon"}, {"Red dragons", "Red dragon"}, {"Green dragons", "Green dragon"},
			{"Lava dragons", "Lava dragon"}, {"Frost dragons", "Frost dragon"},
			{"Metal dragons", "Bronze dragon"}, {"Metal dragons", "Iron dragon"}, {"Metal dragons", "Steel dragon"},
			{"Metal dragons", "Mithril dragon"}, {"Metal dragons", "Adamant dragon"}, {"Metal dragons", "Rune dragon"},
		};
		for (String[] d : dragons)
		{
			Set<CarryItem> s = suggested(variant(d[0], d[1]), false);
			assertTrue(d[1], s.contains(CarryItem.DRAGONFIRE));
			assertFalse(d[1], s.contains(CarryItem.WYVERN_SHIELD));
		}
	}

	@Test
	public void wyvernsGetTheShieldOnlyNote()
	{
		String[][] wyverns = {
			{"Skeletal wyverns", "Skeletal Wyvern"}, {"Fossil Island wyverns", "Spitting Wyvern"},
			{"Fossil Island wyverns", "Taloned Wyvern"}, {"Fossil Island wyverns", "Long-tailed Wyvern"},
			{"Fossil Island wyverns", "Ancient Wyvern"},
		};
		for (String[] w : wyverns)
		{
			Set<CarryItem> s = suggested(variant(w[0], w[1]), false);
			assertTrue(w[1], s.contains(CarryItem.WYVERN_SHIELD));
			assertFalse(w[1], s.contains(CarryItem.DRAGONFIRE));
		}
	}

	@Test
	public void noProtectionForDrakesBabyDragonsOrOtherMonsters()
	{
		String[][] others = {
			// Drakes' breath counts as dragonfire on the wiki, but a step aside dodges it
			{"Drakes", "Drake"}, {"Drakes", "Guardian Drake"},
			// Baby dragons can't breathe fire
			{"Blue dragons", "Baby blue dragon"}, {"Black dragons", "Baby black dragon"},
			{"Wyrms", "Wyrm"}, {"Hydras", "Hydra"}, {"Abyssal demons", "Abyssal demon"},
			{"Revenants", "Revenant dragon"},
		};
		for (String[] o : others)
		{
			Set<CarryItem> s = suggested(variant(o[0], o[1]), false);
			assertFalse(o[1], s.contains(CarryItem.DRAGONFIRE));
			assertFalse(o[1], s.contains(CarryItem.WYVERN_SHIELD));
		}
	}

	@Test
	public void protectionComesFirstWithItsRule()
	{
		List<CarryItem.Suggestion> blue = CarryItem.suggest(variant("Blue dragons", "Blue dragon").getCarry(), false);
		assertEquals(CarryItem.DRAGONFIRE, blue.get(0).getItem());
		assertEquals("Dragonfire: Bring an anti-dragon shield and/or an antifire potion.\nSuper antifire on its own, or"
			+ " a shield with any antifire, blocks normal dragonfire fully", blue.get(0).getTooltip());
		List<CarryItem.Suggestion> wyvern = CarryItem.suggest(variant("Skeletal wyverns", "Skeletal Wyvern").getCarry(), false);
		assertEquals(CarryItem.WYVERN_SHIELD, wyvern.get(0).getItem());
		assertTrue(wyvern.get(0).getTooltip(), wyvern.get(0).getTooltip().startsWith("Icy breath: Wear an elemental"));
		assertTrue(wyvern.get(0).getTooltip().contains("An anti-dragon shield or antifire potion doesn't help"));
	}

	@Test
	public void shieldOrPotionCountsForDragonfire()
	{
		// Either one ticks it
		assertEquals(EnumSet.of(CarryItem.DRAGONFIRE),
			CarryItem.carried(Collections.singletonList(ItemID.ANTIDRAGONBREATHSHIELD)));
		assertEquals(EnumSet.of(CarryItem.DRAGONFIRE),
			CarryItem.carried(Collections.singletonList(ItemID._1DOSE3ANTIDRAGON)));
		assertEquals(EnumSet.of(CarryItem.DRAGONFIRE),
			CarryItem.carried(Collections.singletonList(ItemID.BRUTAL_1DOSE4ANTIDRAGON)));
		assertEquals(EnumSet.of(CarryItem.DRAGONFIRE),
			CarryItem.carried(Arrays.asList(ItemID.ANTIDRAGONBREATHSHIELD, ItemID._4DOSE1ANTIDRAGON)));
		// The mounted shield in a player-owned house isn't worn
		assertTrue(CarryItem.carried(Collections.singletonList(ItemID.POH_TROPHY_ANTIDRAGONBREATH)).isEmpty());
	}

	@Test
	public void onlyTheRightShieldsCountForIcyBreath()
	{
		assertEquals(EnumSet.of(CarryItem.WYVERN_SHIELD), CarryItem.carried(Collections.singletonList(ItemID.ELEMENTAL_SHIELD)));
		assertEquals(EnumSet.of(CarryItem.WYVERN_SHIELD), CarryItem.carried(Collections.singletonList(ItemID.ELEMENTAL_MIND_SHIELD)));
		// Dragonfire shields and wards, and the ancient wyvern shield, cover both
		for (int id : new int[]{ItemID.DRAGONFIRE_SHIELD, ItemID.DRAGONFIRE_SHIELD_UNCHARGED, ItemID.DRAGONFIRE_WARD,
			ItemID.DRAGONFIRE_WARD_UNCHARGED, ItemID.WYVERN_SHIELD, ItemID.WYVERN_SHIELD_UNCHARGED})
		{
			assertEquals(String.valueOf(id), EnumSet.of(CarryItem.DRAGONFIRE, CarryItem.WYVERN_SHIELD),
				CarryItem.carried(Collections.singletonList(id)));
		}
		// An anti-dragon shield or a super antifire potion does nothing against icy breath
		assertFalse(CarryItem.carried(Arrays.asList(ItemID.ANTIDRAGONBREATHSHIELD, ItemID._4DOSE3ANTIDRAGON))
			.contains(CarryItem.WYVERN_SHIELD));
	}

	@Test
	public void everyVersionOfTheShieldsAndPotionsCounts()
	{
		// RuneLite's item variation groups (item_variations.json in the client jar) list every version of an item
		Set<Integer> dragonfire = ids(CarryItem.DRAGONFIRE);
		for (int base : new int[]{ItemID.ANTIDRAGONBREATHSHIELD, ItemID.DRAGONFIRE_SHIELD, ItemID.DRAGONFIRE_WARD,
			ItemID.WYVERN_SHIELD, ItemID._4DOSE1ANTIDRAGON, ItemID.BRUTAL_2DOSE1ANTIDRAGON, ItemID._4DOSE2ANTIDRAGON,
			ItemID.BRUTAL_2DOSE2ANTIDRAGON, ItemID._4DOSE3ANTIDRAGON, ItemID.BRUTAL_2DOSE3ANTIDRAGON,
			ItemID._4DOSE4ANTIDRAGON, ItemID.BRUTAL_2DOSE4ANTIDRAGON})
		{
			Collection<Integer> versions = ItemVariationMapping.getVariations(base);
			assertTrue(base + " has versions", versions.size() > 1);
			for (int id : versions)
			{
				if (id != ItemID.POH_TROPHY_ANTIDRAGONBREATH)
				{
					assertTrue(base + ": " + id, dragonfire.contains(id));
				}
			}
		}
		Set<Integer> wyvern = ids(CarryItem.WYVERN_SHIELD);
		for (int base : new int[]{ItemID.DRAGONFIRE_SHIELD, ItemID.DRAGONFIRE_WARD, ItemID.WYVERN_SHIELD,
			ItemID.ELEMENTAL_SHIELD, ItemID.ELEMENTAL_MIND_SHIELD})
		{
			assertTrue(String.valueOf(base), wyvern.containsAll(ItemVariationMapping.getVariations(base)));
		}
		assertFalse(wyvern.contains(ItemID.ANTIDRAGONBREATHSHIELD));
		// Elemental and mind shields protect against icy breath only
		assertFalse(dragonfire.contains(ItemID.ELEMENTAL_SHIELD));
		assertFalse(dragonfire.contains(ItemID.ELEMENTAL_MIND_SHIELD));
	}

	private static Set<Integer> ids(CarryItem item)
	{
		return Arrays.stream(item.getItemIds()).boxed().collect(Collectors.toSet());
	}
}
