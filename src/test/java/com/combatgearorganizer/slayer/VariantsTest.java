package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.combatgearorganizer.slayer.SlayerLocationData.TaskEntry;
import com.combatgearorganizer.slayer.SlayerLocationData.Variant;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class VariantsTest
{
	private static Variant variant(String name, double xp, boolean superior, String... places)
	{
		Variant v = new Variant();
		v.setName(name);
		v.setLevel(new int[]{10, 10});
		v.setXp(new double[]{xp, xp});
		v.setSuperior(superior);
		v.setPlaces(Arrays.asList(places));
		v.setAttackTypes(Collections.emptyList());
		return v;
	}

	private static LocationOption place(String key)
	{
		return new LocationOption(key, key, false, false, false, null, null, null,
			Collections.emptyList(), Collections.emptyList(), false, false);
	}

	@Test
	public void defaultIsTheVariantAtTheTopPlace()
	{
		List<Variant> wyrms = Arrays.asList(
			variant("Wyrmling", 65, false, "wyrmscraig"),
			variant("Wyrm", 123, false, "karuulm"),
			variant("Shadow Wyrm", 3520, true, "karuulm"));
		assertEquals("Wyrm", Variants.pickDefault(wyrms, Arrays.asList(place("karuulm"), place("wyrmscraig"))).getName());
		// No listed place has one: the first regular variant
		assertEquals("Wyrmling", Variants.pickDefault(wyrms, Collections.singletonList(place("elsewhere"))).getName());
		assertNull(Variants.pickDefault(Collections.singletonList(wyrms.get(2)), Collections.emptyList()));
		assertEquals(Collections.singletonList("Wyrm"), Variants.at(wyrms, "karuulm"));
	}

	@Test
	public void tableListsEveryRegularVariantThoseAtShownPlacesFirst()
	{
		List<Variant> dogs = Arrays.asList(
			variant("Shadow Hound", 62, false),
			variant("Jackal", 27, false, "nardah"),
			variant("Hellhound", 116, false, "taverley"),
			variant("Wild dog", 62, false, "brimhaven"),
			variant("Superior dog", 500, true, "brimhaven"));
		List<LocationOption> shown = Arrays.asList(place("brimhaven"), place("nardah"));
		// At a shown place first (data order), then the rest; never the superior
		assertEquals(Arrays.asList(dogs.get(1), dogs.get(3), dogs.get(0), dogs.get(2)), Variants.listed(dogs, shown));
		// Nothing matches a place: all of them, in the data's order
		assertEquals(dogs.subList(0, 4), Variants.listed(dogs, Collections.singletonList(place("elsewhere"))));
		assertEquals(dogs.subList(0, 4), Variants.listed(dogs, Collections.emptyList()));
		assertTrue(Variants.listed(Collections.singletonList(dogs.get(4)), shown).isEmpty());
	}

	@Test
	public void bossesAreListedAfterTheRegularMonstersButNeverPickedByDefault()
	{
		Variant whelp = variant("Baby blue dragon", 50, false, "taverley");
		Variant dragon = variant("Blue dragon", 107, false, "taverley");
		Variant vorkath = variant("Vorkath", 750, false, "boss_vorkath");
		vorkath.setBoss(true);
		Variant superior = variant("Superior blue", 900, true, "taverley");
		List<Variant> blues = Arrays.asList(whelp, vorkath, dragon, superior);
		List<LocationOption> shown = Arrays.asList(place("boss_vorkath"), place("taverley"));

		// Regular monsters first, the boss after them; no superior
		assertEquals(Arrays.asList(whelp, dragon, vorkath), Variants.listed(blues, shown));
		assertEquals(Arrays.asList(whelp, dragon, vorkath), Variants.listed(blues, Collections.emptyList()));
		assertEquals(Collections.singletonList(vorkath), Variants.bosses(blues));
		assertFalse(vorkath.isRegular());
		// Even with the lair at the top of the list, the default is a regular monster
		assertEquals(whelp, Variants.pickDefault(blues, shown));
		assertNull(Variants.pickDefault(Collections.singletonList(vorkath), shown));
		// Its lair's card names it, so Go there switches to it; the guide place doesn't
		assertEquals(Collections.singletonList("Vorkath"), Variants.at(blues, "boss_vorkath"));
		assertEquals(Arrays.asList("Baby blue dragon", "Blue dragon"), Variants.at(blues, "taverley"));
		assertEquals(Collections.singletonList("boss_vorkath"), Variants.foundAt(vorkath, shown));
		// A boss whose lair isn't listed: the bosses that don't count are left out already, so it's the Wilderness setting
		assertEquals(Collections.singletonList(Variants.BOSS_IN_WILDERNESS), Variants.foundAt(vorkath, Collections.singletonList(place("taverley"))));
	}

	@Test
	public void foundAtSaysWhereOrWhyNot()
	{
		Variant jackal = variant("Jackal", 27, false, "nardah", "pollnivneach");
		Variant hound = variant("Shadow Hound", 62, false);
		Variant hellhound = variant("Hellhound", 116, false, "taverley");
		LocationOption nardah = new LocationOption("nardah", "West of Nardah", false, false, false, null, null, null,
			Collections.emptyList(), Collections.emptyList(), false, false);
		LocationOption pollnivneach = new LocationOption("pollnivneach", "Pollnivneach", false, false, false, null, null, null,
			Collections.emptyList(), Collections.emptyList(), false, false);
		List<LocationOption> shown = Arrays.asList(pollnivneach, place("brimhaven"), nardah);

		// The shown places' labels, in list order
		assertEquals(Arrays.asList("Pollnivneach", "West of Nardah"), Variants.foundAt(jackal, shown));
		assertTrue(Variants.isAtAny(jackal, shown));
		// The data has no place for it at all
		assertEquals(Collections.singletonList(Variants.NO_PLACE), Variants.foundAt(hound, shown));
		assertEquals("No place in the data yet", Variants.NO_PLACE);
		assertTrue(!Variants.isAtAny(hound, shown));
		// It has places, just none of the shown ones (Wilderness setting off, Konar's area)
		assertEquals(Collections.singletonList(Variants.NOT_SHOWN), Variants.foundAt(hellhound, shown));
		assertEquals(Collections.singletonList(Variants.NOT_SHOWN), Variants.foundAt(hellhound, Collections.emptyList()));
		assertEquals(Collections.singletonList(Variants.NO_PLACE), Variants.foundAt(hound, Collections.emptyList()));
	}

	@Test
	public void formatting()
	{
		assertEquals("97", Variants.range(new int[]{97, 97}));
		assertEquals("13-142", Variants.range(new int[]{13, 142}));
		assertEquals("?", Variants.range(null));

		assertEquals("123", Variants.xpEach(new double[]{123, 123}));
		assertEquals("157.5", Variants.xpEach(new double[]{157.5, 157.5}));
		assertEquals("17-110", Variants.xpEach(new double[]{17, 110}));

		assertEquals("15,129", Variants.xpTotal(new double[]{123, 123}, 123));
		assertEquals("1,700-11,000", Variants.xpTotal(new double[]{17, 110}, 100));

		assertEquals("15k", Variants.xpTotalShort(new double[]{123, 123}, 123));
		assertEquals("4.9k", Variants.xpTotalShort(new double[]{65, 65}, 75));
		assertEquals("980", Variants.xpTotalShort(new double[]{49, 49}, 20));
		assertEquals("~6.4k", Variants.xpTotalShort(new double[]{17, 110}, 100));
	}

	@Test
	public void bundledWyrmsAndDogs() throws Exception
	{
		SlayerLocationData data;
		try (Reader r = new InputStreamReader(SlayerHelper.class.getResourceAsStream("slayer_locations.json"), StandardCharsets.UTF_8))
		{
			data = new Gson().fromJson(r, SlayerLocationData.class);
		}
		TaskEntry wyrms = data.getTasks().stream().filter(t -> t.getName().equals("Wyrms")).findFirst().orElse(null);
		assertNotNull(wyrms);
		List<String> regular = Variants.regular(wyrms.getVariants()).stream().map(Variant::getName).collect(Collectors.toList());
		assertTrue(regular.containsAll(Arrays.asList("Wyrm", "Wyrmling", "Lava Strykewyrm")));
		assertTrue(wyrms.getVariants().stream().anyMatch(v -> v.isSuperior() && v.getName().equals("Shadow Wyrm")));
		assertTrue(Variants.at(wyrms.getVariants(), "karuulm_slayer_dungeon").contains("Wyrm"));
		assertTrue(Variants.at(wyrms.getVariants(), "neypotzli").contains("Wyrmling"));
		Variant wyrm = Variants.byName(wyrms.getVariants(), "Wyrm");
		Variant lava = Variants.byName(wyrms.getVariants(), "Lava Strykewyrm");
		assertEquals("Shadow Wyrm", Variants.superiorsOf(wyrms.getVariants(), wyrm).get(0).getName());
		assertEquals("Magma strykewyrm", Variants.superiorsOf(wyrms.getVariants(), lava).get(0).getName());
		assertTrue(Variants.superiorsOf(wyrms.getVariants(), Variants.byName(wyrms.getVariants(), "Wyrmling")).isEmpty());
		// Drops are per monster: only wyrms drop the dragon harpoon
		assertTrue(Variants.byName(wyrms.getVariants(), "Wyrmling").getDrops().isEmpty());
		assertEquals(Collections.singletonList("Wyrmling bones"), Variants.byName(wyrms.getVariants(), "Wyrmling").getAlwaysDrops());
		SlayerLocationData.Drop harpoon = wyrm.getDrops().stream().filter(d -> d.getItem().equals("Dragon harpoon")).findFirst().orElse(null);
		assertNotNull(harpoon);
		assertEquals("1/2,000", harpoon.getOnTask());
		assertTrue(harpoon.isClog());
		// League-only superiors are left out
		assertTrue(data.getTasks().stream().flatMap(t -> t.getVariants().stream()).noneMatch(v -> v.getName().startsWith("Bloodthirst")));

		TaskEntry dogs = data.getTasks().stream().filter(t -> t.getName().equals("Dogs")).findFirst().orElse(null);
		assertNotNull(dogs);
		assertEquals(Collections.singletonList("Jackal"), Variants.at(dogs.getVariants(), "west_of_nardah"));
	}

	private static SlayerLocationData.Drop drop(String item)
	{
		SlayerLocationData.Drop d = new SlayerLocationData.Drop();
		d.setItem(item);
		d.setRarity("1/100");
		return d;
	}

	private static List<String> items(List<SlayerLocationData.Drop> drops)
	{
		return drops.stream().map(SlayerLocationData.Drop::getItem).collect(Collectors.toList());
	}

	@Test
	public void notableDropsOfSuperiorsAndBosses()
	{
		Variant demon = variant("Abyssal demon", 150, false, "slayer_tower");
		demon.setDrops(Arrays.asList(drop("Abyssal whip"), drop("Ancient shard")));
		Variant greater = variant("Greater abyssal demon", 4200, true);
		greater.setSuperiorOf("abyssal demon");
		greater.setDrops(Arrays.asList(drop("Abyssal whip"), drop("Imbued heart"), drop("ancient shard")));
		Variant sire = variant("Abyssal Sire", 478, false);
		sire.setBoss(true);
		sire.setDrops(Collections.singletonList(drop("Unsired")));
		List<Variant> all = Arrays.asList(demon, greater, sire);

		assertEquals(Collections.singletonList("Imbued heart"), items(Variants.notableDrops(all, greater)));
		assertEquals(Collections.singletonList("Unsired"), items(Variants.notableDrops(all, sire)));

		// Nothing of its own: show what it drops anyway
		greater.setDrops(Collections.singletonList(drop("Abyssal whip")));
		assertEquals(Collections.singletonList("Abyssal whip"), items(Variants.notableDrops(all, greater)));
		// Base monster not in the list
		assertEquals(1, Variants.notableDrops(Collections.singletonList(greater), greater).size());
	}

	@Test
	public void tableXpFitsItsColumn()
	{
		assertEquals("157.5", Variants.xpEachShort(new double[]{157.5, 157.5}));
		assertEquals("17-182", Variants.xpEachShort(new double[]{17, 181.5}));
		assertEquals("600-2.1k", Variants.xpEachShort(new double[]{600, 2137.5}));
		assertEquals("~50", Variants.xpEachShort(new double[]{49.6, 50}));
		assertEquals("?", Variants.xpEachShort(null));
		// The card keeps the exact range
		assertEquals("17-181.5", Variants.xpEach(new double[]{17, 181.5}));
	}

	@Test
	public void longNamesWrapToFitTheTable()
	{
		java.awt.FontMetrics fm = new javax.swing.JLabel().getFontMetrics(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
		assertEquals(Collections.singletonList("Vorkath"), SlayerPanel.wrapName("Vorkath", fm, 200));
		int room = fm.stringWidth("Grotesque") + 2;
		assertEquals(Arrays.asList("Grotesque", "Guardians"), SlayerPanel.wrapName("Grotesque Guardians", fm, room));
		// A word wider than the room is split where it must be, and nothing is lost
		List<String> lines = SlayerPanel.wrapName("Thermonuclear smoke devil", fm, fm.stringWidth("Thermo"));
		assertTrue(lines.size() > 2);
		assertEquals("Thermonuclearsmokedevil", String.join("", lines).replace(" ", ""));
		for (String line : lines)
		{
			assertTrue(line, fm.stringWidth(line) <= fm.stringWidth("Thermo"));
		}
	}

	@Test
	public void boostableLevelsCountABoost()
	{
		assertTrue(RequirementChecker.meets(91, 91, 91, false));
		assertFalse(RequirementChecker.meets(89, 94, 91, false));
		// A wild pie for Cerberus
		assertTrue(RequirementChecker.meets(89, 94, 91, true));
		assertFalse(RequirementChecker.meets(89, 89, 91, true));
		// A drained level doesn't close a place the real level opens
		assertTrue(RequirementChecker.meets(91, 80, 91, true));
		assertEquals("91 Slayer (boostable)", RequirementChecker.skillText(91, "Slayer", true));
		assertEquals("87 Slayer", RequirementChecker.skillText(87, "Slayer", false));
	}

	@Test
	public void aBossCountsOnlyForItsMasters()
	{
		Variant eldric = variant("Eldric the Ice King", 1, false, "boss_royal_titans");
		eldric.setBoss(true);
		eldric.setMasters(Collections.singletonList("Vannaka"));
		java.util.Set<String> open = Collections.singleton("boss_royal_titans");
		assertTrue(Variants.countsFor(eldric, "Vannaka", open));
		assertFalse(Variants.countsFor(eldric, "Duradel", open));
		// Unknown master: no master rule
		assertTrue(Variants.countsFor(eldric, null, open));
		// Its lair isn't among the places that count (Krystilia, Konar's area)
		assertFalse(Variants.countsFor(eldric, "Vannaka", Collections.emptySet()));
		Variant kbd = variant("King Black Dragon", 1, false, "boss_kbd");
		kbd.setBoss(true);
		kbd.setNotMasters(Collections.singletonList("Krystilia"));
		assertFalse(Variants.countsFor(kbd, "Krystilia", Collections.singleton("boss_kbd")));
		assertTrue(Variants.countsFor(kbd, "Nieve", Collections.singleton("boss_kbd")));
	}
}
