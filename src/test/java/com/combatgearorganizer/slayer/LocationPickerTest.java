package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.combatgearorganizer.slayer.SlayerLocationData.LocationEntry;
import com.combatgearorganizer.slayer.SlayerLocationData.TaskEntry;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
}
