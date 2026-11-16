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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
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
		assertTrue(CarryItem.carried(Collections.emptyList()).isEmpty());
	}
}
