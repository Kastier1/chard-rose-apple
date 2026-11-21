package com.combatgearorganizer.slayer;

import com.combatgearorganizer.GearStyle;
import com.combatgearorganizer.dps.OwnedGear;
import com.combatgearorganizer.dps.Target;
import com.combatgearorganizer.dps.WikiGear;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The "Best with what you have" rows against the bundled monster data. */
public class GearUpTest
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
		SlayerLocationData.Variant v = Variants.byName(t.getVariants(), name);
		assertNotNull(name, v);
		return v;
	}

	private static GearUp.Row row(List<GearUp.Row> rows, GearStyle style)
	{
		return rows.stream().filter(r -> r.getStyle() == style).findFirst().orElse(null);
	}

	@Test
	public void everyMonsterHasDefenceStats()
	{
		List<String> missing = new ArrayList<>();
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				Target target = GearUp.target(v);
				if (target == null)
				{
					missing.add(t.getName() + " / " + v.getName());
				}
				else if (target.getSize() < 1 || target.getDefenceLevel() < 0)
				{
					fail(v.getName() + ": " + target);
				}
			}
		}
		assertTrue(String.join(", ", missing), missing.isEmpty());
	}

	@Test
	public void bundledGreaterDemon()
	{
		SlayerLocationData.Variant demon = variant("Greater demons", "Greater demon");
		Target t = GearUp.target(demon);
		assertTrue(t.is("demon"));
		assertEquals("water", t.getWeakElement());
		assertEquals(3, t.getSize());
		assertTrue(t.getDefenceLevel() > 0);

		OwnedGear bank = WikiGear.bank("Darklight", "Arclight", "Dual macuahuitl", "Kodai wand", "Slayer helmet (i)");
		List<GearUp.Row> rows = GearUp.rows(bank, demon);
		GearUp.Row melee = row(rows, GearStyle.MELEE);
		assertTrue(melee.getText(), melee.getText().startsWith("Arclight "));
		assertTrue(melee.getText(), melee.getText().endsWith(" DPS (demonbane)"));
		assertTrue(melee.getNext(), melee.getNext().startsWith("then "));
		assertTrue(melee.getTooltip(), melee.getTooltip().contains("Wearing your Slayer helmet (i)"));
		assertTrue(melee.getTooltip(), melee.getTooltip().contains("Next: "));
		// The rest of the gear the number assumes, so it can be checked against the wiki's DPS calculator
		assertTrue(melee.getTooltip(), melee.getTooltip().contains("\nWith Slayer helmet (i), "));
		assertTrue(melee.getTooltip(), melee.getTooltip().contains("Bandos chestplate"));
		assertTrue(melee.getTooltip(), melee.getTooltip().contains("Dual macuahuitl"));
		assertTrue(melee.getTooltip().endsWith(GearUp.ASSUMPTIONS));
		GearUp.Row magic = row(rows, GearStyle.MAGIC);
		assertTrue(magic.getText(), magic.getText().contains("(Water Surge, water weakness)"));
		assertNull(magic.getNext());
		// No ranged weapon: no ranged row
		assertNull(row(rows, GearStyle.RANGED));
	}

	@Test
	public void bundledDragonsAndUndeadAndLeafy()
	{
		OwnedGear bank = WikiGear.bank("Dragon hunter lance", "Abyssal whip", "Dragon hunter crossbow", "Armadyl crossbow",
			"Dragon bolts", "Slayer helmet (i)", "Salve amulet(ei)", "Kodai wand");
		SlayerLocationData.Variant black = variant("Black dragons", "Black dragon");
		assertTrue(GearUp.target(black).is("dragon"));
		List<GearUp.Row> rows = GearUp.rows(bank, black);
		assertTrue(row(rows, GearStyle.MELEE).getText().startsWith("Dragon hunter lance"));
		assertTrue(row(rows, GearStyle.RANGED).getText().startsWith("Dragon hunter crossbow"));
		assertTrue(row(rows, GearStyle.RANGED).getText().contains("dragonbane"));

		SlayerLocationData.Variant ankou = variant("Ankou", "Ankou");
		assertTrue(GearUp.target(ankou).is("undead"));
		for (GearUp.Row r : GearUp.rows(bank, ankou))
		{
			// The salve's bonus or the helmet's, never both
			long boosts = Arrays.stream(r.getTooltip().split("\n")).filter(l -> l.startsWith("Wearing your ")).count();
			assertTrue(r.getTooltip(), boosts <= 1);
			assertFalse(r.getTooltip(), r.getTooltip().contains("Wearing your Slayer helmet (i)")
				&& r.getTooltip().contains("Wearing your Salve amulet(ei)"));
		}

		SlayerLocationData.Variant kurask = variant("Kurask", "Kurask");
		List<GearUp.Row> leafy = GearUp.rows(bank, kurask);
		assertEquals(3, leafy.size());
		for (GearUp.Row r : leafy)
		{
			assertFalse(r.isUsable());
			assertTrue(r.getText().startsWith("Only leaf-bladed"));
		}
	}

	@Test
	public void textFormatting()
	{
		assertEquals("6.1", GearUp.dps(6.06));
		assertEquals("12.0", GearUp.dps(11.96));
		// No defence data: no rows
		SlayerLocationData.Variant none = new SlayerLocationData.Variant();
		none.setName("Nothing");
		assertNull(GearUp.target(none));
		assertTrue(GearUp.rows(WikiGear.bank("Abyssal whip"), none).isEmpty());
		// Size 0 in the file is 1
		SlayerLocationData.Variant small = new SlayerLocationData.Variant();
		small.setName("Small");
		small.setCombat(new SlayerLocationData.Combat());
		assertEquals(1, GearUp.target(small).getSize());
	}
}
