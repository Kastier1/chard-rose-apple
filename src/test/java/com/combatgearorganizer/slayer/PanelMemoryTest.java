package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.Test;

public class PanelMemoryTest
{
	private final Gson gson = new Gson();

	@Test
	public void drawersToggle()
	{
		PanelMemory m = new PanelMemory();
		assertFalse(m.isOpen("Bring"));
		m.toggle("Bring");
		assertTrue(m.isOpen("Bring"));
		m.toggle("Bring");
		assertFalse(m.isOpen("Bring"));
	}

	@Test
	public void monsterAndPlacePerTask()
	{
		PanelMemory m = new PanelMemory();
		String demons = PanelMemory.taskKey(42, "Abyssal demons");
		String hydras = PanelMemory.taskKey(113, "Hydras");
		m.setMonster(demons, "Abyssal Sire");
		m.setLastPlace(demons, "catacombs");
		assertEquals("Abyssal Sire", m.monster(demons));
		assertEquals("catacombs", m.lastPlace(demons));
		assertNull(m.monster(hydras));
		assertNull(m.lastPlace(hydras));
		m.setMonster(demons, null);
		assertNull(m.monster(demons));
	}

	@Test
	public void bossTasksAreKeptApartByName()
	{
		assertFalse(PanelMemory.taskKey(98, "Vorkath").equals(PanelMemory.taskKey(98, "Zulrah")));
		assertEquals(PanelMemory.taskKey(42, "Abyssal demons"), PanelMemory.taskKey(42, "abyssal Demons "));
	}

	@Test
	public void savesAndLoads()
	{
		PanelMemory m = new PanelMemory();
		m.setProfile("account");
		m.toggle("Weakness");
		m.setMonster("42:abyssal demons", "Abyssal demon");
		m.setLastPlace("42:abyssal demons", "catacombs");
		String json = m.toJson(gson);
		assertFalse("the account isn't saved in the memory", json.contains("account"));

		PanelMemory back = PanelMemory.fromJson(gson, json);
		assertTrue(back.isOpen("Weakness"));
		assertEquals("Abyssal demon", back.monster("42:abyssal demons"));
		assertEquals("catacombs", back.lastPlace("42:abyssal demons"));
		assertNull(back.getProfile());
	}

	@Test
	public void badOrMissingSavesGiveEmptyMemory()
	{
		assertTrue(PanelMemory.fromJson(gson, null).getOpenDrawers().isEmpty());
		assertTrue(PanelMemory.fromJson(gson, "").getOpenDrawers().isEmpty());
		assertTrue(PanelMemory.fromJson(gson, "{not json").getOpenDrawers().isEmpty());
		PanelMemory nulls = PanelMemory.fromJson(gson, "{\"openDrawers\":null,\"monsters\":{\"a\":null},\"places\":null}");
		assertFalse(nulls.isOpen("Bring"));
		assertNull(nulls.monster("a"));
		nulls.setLastPlace("a", "b");
		assertEquals("b", nulls.lastPlace("a"));
	}

	@Test
	public void oldestTasksAreForgotten()
	{
		PanelMemory m = new PanelMemory();
		for (int i = 0; i < PanelMemory.MAX_TASKS + 10; i++)
		{
			m.setMonster("task" + i, "monster");
		}
		// The first one is used again, so it is kept
		m.setMonster("task0", "again");
		assertEquals(PanelMemory.MAX_TASKS, m.monsters().size());
		assertEquals("again", m.monster("task0"));
		assertNull(m.monster("task1"));
		assertEquals("monster", m.monster("task" + (PanelMemory.MAX_TASKS + 9)));
	}
}
