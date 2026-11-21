package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.combatgearorganizer.slayer.SlayerLocationData.LocationEntry;
import com.combatgearorganizer.slayer.SlayerLocationData.Requirements;
import com.combatgearorganizer.slayer.SlayerLocationData.TaskEntry;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LocationPickerTest
{
	private static LocationEntry loc(String key, Integer areaId, List<int[]> points, List<int[]> wildPoints)
	{
		LocationEntry l = new LocationEntry();
		l.setKey(key);
		l.setLabel(key);
		l.setAreaId(areaId);
		l.setPoints(points);
		l.setWildernessPoints(wildPoints);
		return l;
	}

	private static final int[] P1 = {1675, 10091, 0};
	private static final int[] P2 = {3418, 3567, 2};
	private static final int[] W1 = {3343, 10163, 0};

	private static TaskEntry abyssalDemons()
	{
		TaskEntry t = new TaskEntry();
		t.setTaskId(42);
		t.setName("Abyssal demons");
		t.setRecommended("catacombs");
		t.setLocations(Arrays.asList(
			loc("slayer_tower", 9, Collections.singletonList(P2), Collections.emptyList()),
			loc("catacombs", 1, Collections.singletonList(P1), Collections.emptyList()),
			loc("wildy_cave", null, Collections.emptyList(), Collections.singletonList(W1)),
			loc("no_point_yet", null, Collections.emptyList(), Collections.emptyList())));
		t.setWildernessLocations(Collections.singletonList(
			loc("wildy_cave", null, Collections.emptyList(), Collections.singletonList(W1))));
		return t;
	}

	@Test
	public void recommendedFirstAndWildernessHiddenByDefault()
	{
		List<LocationOption> options = LocationPicker.pick(abyssalDemons(), 5, 0, false);
		assertEquals(3, options.size());
		assertEquals("catacombs", options.get(0).getKey());
		assertTrue(options.get(0).isRecommended());
		assertEquals("slayer_tower", options.get(1).getKey());
		assertFalse(options.get(2).isRoutable());
	}

	@Test
	public void wildernessShownWhenEnabled()
	{
		List<LocationOption> options = LocationPicker.pick(abyssalDemons(), 5, 0, true);
		assertEquals(4, options.size());
		LocationOption wild = options.stream().filter(o -> o.getKey().equals("wildy_cave")).findFirst().get();
		assertTrue(wild.isWilderness());
		assertTrue(wild.isRoutable());
	}

	@Test
	public void krystiliaGetsOnlyHerWildernessList()
	{
		List<LocationOption> options = LocationPicker.pick(abyssalDemons(), SlayerMasters.KRYSTILIA, 0, false);
		assertEquals(1, options.size());
		assertEquals("wildy_cave", options.get(0).getKey());
		assertTrue(options.get(0).isWilderness());
	}

	@Test
	public void konarGetsOnlyTheAssignedArea()
	{
		List<LocationOption> options = LocationPicker.pick(abyssalDemons(), SlayerMasters.KONAR, 9, false);
		assertEquals(1, options.size());
		assertEquals("slayer_tower", options.get(0).getKey());
	}

	@Test
	public void konarFallsBackToAllWhenAreaUnknown()
	{
		assertEquals(3, LocationPicker.pick(abyssalDemons(), SlayerMasters.KONAR, 999, false).size());
	}

	@Test
	public void bundledDataLoadsAndHasCommonTasks() throws Exception
	{
		try (Reader r = new InputStreamReader(SlayerHelper.class.getResourceAsStream("slayer_locations.json"), StandardCharsets.UTF_8))
		{
			SlayerLocationData data = new Gson().fromJson(r, SlayerLocationData.class);
			assertTrue(data.getTasks().size() >= 100);

			TaskEntry demons = data.findTask(42, null);
			assertNotNull(demons);
			assertEquals("Abyssal demons", demons.getName());
			assertTrue(LocationPicker.pick(demons, 5, 0, false).get(0).isRoutable());

			// Name matching ignores case, for boss/unknown ids
			assertNotNull(data.findTask(-1, "GARGOYLES"));

			int[] point = demons.getLocations().get(0).getPoints().get(0);
			assertEquals(3, point.length);
		}
	}

	@Test
	public void krystiliaNeverFallsBackToPlacesOutsideTheWilderness()
	{
		TaskEntry t = abyssalDemons();
		t.setWildernessLocations(Collections.emptyList());
		List<LocationOption> options = LocationPicker.pick(t, SlayerMasters.KRYSTILIA, 0, false);
		assertEquals(1, options.size());
		assertEquals("wildy_cave", options.get(0).getKey());
		assertTrue(options.get(0).isWilderness());
	}

	@Test
	public void placesYouCantEnterGoLastAndSayWhy()
	{
		TaskEntry t = abyssalDemons();
		Requirements needsPriest = new Requirements();
		needsPriest.setQuestsFinished(Collections.singletonList("PRIEST_IN_PERIL"));
		t.getLocations().get(0).setRequirements(needsPriest); // slayer_tower
		t.setRecommended(null);

		RequirementChecker checker = r -> r.getQuestsFinished().isEmpty()
			? Collections.emptyList()
			: Collections.singletonList("Priest in Peril");
		List<LocationOption> options = LocationPicker.pick(t, 5, 0, false, checker);
		assertEquals("catacombs", options.get(0).getKey());
		LocationOption tower = options.stream().filter(o -> o.getKey().equals("slayer_tower")).findFirst().get();
		assertFalse(tower.isAccessible());
		assertEquals(Collections.singletonList("Priest in Peril"), tower.getMissingRequirements());
	}

	private static LocationOption option(String key, boolean routable, boolean accessible, boolean fromWiki)
	{
		return option(key, routable, accessible, fromWiki, false);
	}

	private static LocationOption option(String key, boolean routable, boolean accessible, boolean fromWiki, boolean lair)
	{
		return new LocationOption(key, key, false, false, false, null, null, null,
			routable ? Collections.singletonList(P1) : Collections.emptyList(),
			accessible ? Collections.emptyList() : Collections.singletonList("Priest in Peril"), fromWiki, lair);
	}

	@Test
	public void nearestNeverPicksABossLair()
	{
		LocationOption guide = option("guide", true, true, false);
		LocationOption wiki = option("wiki", true, true, true);
		LocationOption locked = option("locked", true, false, false);
		LocationOption lair = option("boss_vorkath", true, true, false, true);

		assertEquals(Collections.singletonList(guide), LocationPicker.nearestTargets(Arrays.asList(lair, guide, wiki)));
		// Not even when it's the only place that's open, routable or not from the wiki
		assertEquals(Collections.singletonList(wiki), LocationPicker.nearestTargets(Arrays.asList(lair, wiki, locked)));
		// A place the player may not get into yet beats an open lair: the lair is never Go to nearest's choice
		assertEquals(Collections.singletonList(locked), LocationPicker.nearestTargets(Arrays.asList(lair, locked)));
		assertTrue(LocationPicker.nearestTargets(Collections.singletonList(lair)).isEmpty());
	}

	@Test
	public void bossLairsGoLastAndFollowKonarAndKrystilia()
	{
		TaskEntry t = abyssalDemons();
		LocationEntry lair = loc("boss_abyssal_nexus", 9, Collections.singletonList(P2), Collections.emptyList());
		lair.setSource(SlayerLocationData.SOURCE_BOSS);
		LocationEntry wildLair = loc("boss_wild", null, Collections.emptyList(), Collections.singletonList(W1));
		wildLair.setSource(SlayerLocationData.SOURCE_BOSS);
		List<LocationEntry> all = new ArrayList<>();
		all.add(lair);
		all.addAll(t.getLocations());
		t.setLocations(all);
		t.setWildernessLocations(Collections.singletonList(wildLair));

		// Below every other place, even closed ones
		RequirementChecker closedTower = r -> r.getQuestsFinished().contains("PRIEST_IN_PERIL")
			? Collections.singletonList("Priest in Peril") : Collections.emptyList();
		List<LocationOption> options = LocationPicker.pick(t, 5, 0, false, closedTower);
		LocationOption last = options.get(options.size() - 1);
		assertEquals("boss_abyssal_nexus", last.getKey());
		assertTrue(last.isBossLair());
		assertFalse(options.get(0).isBossLair());
		assertTrue(lair.isBossLair());
		assertFalse(lair.isFromWiki());

		// Konar's area: the lair counts as it, so it's listed with the guide place of that area
		List<LocationOption> konar = LocationPicker.pick(t, 8, 9, false);
		assertEquals(2, konar.size());
		assertTrue(konar.stream().anyMatch(o -> o.getKey().equals("boss_abyssal_nexus")));
		assertTrue(konar.stream().anyMatch(o -> o.getKey().equals("slayer_tower")));
		assertTrue(LocationPicker.nearestTargets(konar).stream().noneMatch(LocationOption::isBossLair));

		// Krystilia: her list, with its Wilderness lair
		List<LocationOption> krystilia = LocationPicker.pick(t, SlayerMasters.KRYSTILIA, 0, false);
		assertEquals(1, krystilia.size());
		assertTrue(krystilia.get(0).isBossLair());
		assertTrue(LocationPicker.nearestTargets(krystilia).isEmpty());
	}

	@Test
	public void wikiSpawnPlacesCarryTheirSource()
	{
		TaskEntry t = abyssalDemons();
		LocationEntry wiki = loc("wiki_temple", null, Collections.singletonList(P2), Collections.emptyList());
		wiki.setSource(SlayerLocationData.SOURCE_WIKI);
		t.setLocations(Arrays.asList(t.getLocations().get(0), wiki));
		List<LocationOption> options = LocationPicker.pick(t, 5, 0, false);
		assertFalse(options.get(0).isFromWiki());
		assertTrue(options.get(1).isFromWiki());
		assertTrue(wiki.isFromWiki());
		assertFalse(t.getLocations().get(0).isFromWiki());
	}

	@Test
	public void nearestUsesGuidePlacesBeforeWikiSpawnPlaces()
	{
		LocationOption guide = option("guide", true, true, false);
		LocationOption locked = option("locked", true, false, false);
		LocationOption noPoint = option("no_point", false, true, false);
		LocationOption wiki = option("wiki", true, true, true);
		LocationOption lockedWiki = option("locked_wiki", true, false, true);

		assertEquals(Collections.singletonList(guide), LocationPicker.nearestTargets(Arrays.asList(wiki, locked, guide, noPoint)));
		// No guide place to go to: the wiki ones will do, still only open and routable ones
		assertEquals(Collections.singletonList(wiki), LocationPicker.nearestTargets(Arrays.asList(locked, noPoint, wiki, lockedWiki)));
		assertTrue(LocationPicker.nearestTargets(Collections.singletonList(noPoint)).isEmpty());
		assertTrue(LocationPicker.nearestTargets(Collections.emptyList()).isEmpty());
	}

	/**
	 * Review: Go to nearest was greyed out, with nothing on the panel saying why, when every place needed something
	 * the player lacked or the client hadn't sent yet (Gargoyles with nothing met). It now picks from those places,
	 * guide ones first, like each card's Go, and says they're gated.
	 */
	@Test
	public void nearestFallsBackToGatedPlacesInsteadOfGreyingOut()
	{
		LocationOption locked = option("locked", true, false, false);
		LocationOption noPoint = option("no_point", false, true, false);
		LocationOption lockedWiki = option("locked_wiki", true, false, true);
		LocationOption open = option("open", true, true, false);

		List<LocationOption> gated = LocationPicker.nearestTargets(Arrays.asList(locked, noPoint, lockedWiki));
		assertEquals(Collections.singletonList(locked), gated);
		assertTrue(LocationPicker.nearestIsGated(gated));
		assertEquals(Collections.singletonList(lockedWiki), LocationPicker.nearestTargets(Arrays.asList(noPoint, lockedWiki)));
		// An open place still wins, and then it isn't gated
		List<LocationOption> withOpen = LocationPicker.nearestTargets(Arrays.asList(locked, open));
		assertEquals(Collections.singletonList(open), withOpen);
		assertFalse(LocationPicker.nearestIsGated(withOpen));
		assertFalse(LocationPicker.nearestIsGated(Collections.emptyList()));
	}

	@Test
	public void everyKrystiliaTaskInTheSourceIsInTheData() throws Exception
	{
		try (Reader r = new InputStreamReader(SlayerHelper.class.getResourceAsStream("slayer_locations.json"), StandardCharsets.UTF_8))
		{
			SlayerLocationData data = new Gson().fromJson(r, SlayerLocationData.class);
			// These were dropped before because they're only on Krystilia's list
			for (String name : Arrays.asList("Revenants", "Pirates", "Black knights", "Chaos druids", "Lava dragons",
				"Green dragons", "Dark warriors", "Bandits", "Ents", "Rogues", "Mammoths", "Earth warriors", "Magic axes"))
			{
				TaskEntry task = data.findTask(-1, name);
				assertNotNull(name, task);
				assertFalse(name, LocationPicker.pick(task, SlayerMasters.KRYSTILIA, 0, false).isEmpty());
			}

			// Gear info for every task, including ones whose names differ in the source
			for (TaskEntry task : data.getTasks())
			{
				assertNotNull(task.getName(), task.getInfo());
			}
			assertEquals("Protect from Melee", data.findTask(-1, "Catablepon").getInfo().getProtectionPrayer());
			assertNotNull(data.findTask(-1, "Hydras").getInfo().getRequiredItems());

			// Requirements made it through
			TaskEntry demons = data.findTask(42, null);
			assertTrue(demons.getLocations().stream().anyMatch(l -> l.getRequirements() != null
				&& l.getRequirements().getQuestsFinished().contains("PRIEST_IN_PERIL")));
		}
	}
}
