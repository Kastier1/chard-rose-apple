package com.combatgearorganizer.slayer;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * What the slayer panel remembers for one account: which drawers are open, the monster picked for each task, and the
 * place last chosen with Go for each task. Saved as JSON in the account's RS-profile config.
 *
 * <p>Only touch it on one thread (the panel's Swing thread).
 */
public class PanelMemory
{
	/** Tasks remembered; the oldest are forgotten after this many. There are about 120 tasks. */
	static final int MAX_TASKS = 200;
	/** Drawer titles remembered. There are only a handful. */
	static final int MAX_DRAWERS = 20;

	/** Drawer titles the player has opened. The same for every task. */
	private LinkedHashSet<String> openDrawers = new LinkedHashSet<>();
	/** Task key to the name of the monster the player picked. */
	private LinkedHashMap<String, String> monsters = new LinkedHashMap<>();
	/** Task key to the key of the place the player last chose with Go. */
	private LinkedHashMap<String, String> places = new LinkedHashMap<>();

	/** The account (RS profile key) this belongs to. Not saved. */
	@Getter
	@Setter
	private transient String profile;

	/** The key a task is remembered by. Boss tasks share one id, so the name is part of it. */
	public static String taskKey(int taskId, String name)
	{
		return taskId + ":" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
	}

	public boolean isOpen(String drawer)
	{
		return openDrawers.contains(drawer);
	}

	/** Opens a closed drawer or closes an open one. */
	public void toggle(String drawer)
	{
		if (!openDrawers.remove(drawer))
		{
			openDrawers.add(drawer);
			trim(openDrawers, MAX_DRAWERS);
		}
	}

	public Set<String> getOpenDrawers()
	{
		return Collections.unmodifiableSet(openDrawers);
	}

	/** The monster the player picked for this task, or null. */
	public String monster(String taskKey)
	{
		return monsters.get(taskKey);
	}

	public void setMonster(String taskKey, String monster)
	{
		put(monsters, taskKey, monster);
	}

	/** The place the player last chose with Go for this task, or null. */
	public String lastPlace(String taskKey)
	{
		return places.get(taskKey);
	}

	public void setLastPlace(String taskKey, String placeKey)
	{
		put(places, taskKey, placeKey);
	}

	public String toJson(Gson gson)
	{
		return gson.toJson(this);
	}

	/** Reads saved memory; empty memory when there is none or it can't be read. */
	public static PanelMemory fromJson(Gson gson, String json)
	{
		PanelMemory m = null;
		if (json != null && !json.isEmpty())
		{
			try
			{
				m = gson.fromJson(json, PanelMemory.class);
			}
			catch (RuntimeException e)
			{
				m = null;
			}
		}
		if (m == null)
		{
			return new PanelMemory();
		}
		// A hand-edited or older file can hold nulls
		if (m.openDrawers == null)
		{
			m.openDrawers = new LinkedHashSet<>();
		}
		if (m.monsters == null)
		{
			m.monsters = new LinkedHashMap<>();
		}
		if (m.places == null)
		{
			m.places = new LinkedHashMap<>();
		}
		m.openDrawers.remove(null);
		m.monsters.values().removeIf(v -> v == null);
		m.places.values().removeIf(v -> v == null);
		return m;
	}

	private static void put(LinkedHashMap<String, String> map, String key, String value)
	{
		if (key == null)
		{
			return;
		}
		// Re-insert so the most recently used task is last and the oldest is forgotten first
		map.remove(key);
		if (value != null)
		{
			map.put(key, value);
		}
		trim(map.keySet(), MAX_TASKS);
	}

	private static void trim(Set<String> set, int max)
	{
		Iterator<String> it = set.iterator();
		while (set.size() > max && it.hasNext())
		{
			it.next();
			it.remove();
		}
	}

	/** For tests. */
	Map<String, String> monsters()
	{
		return monsters;
	}
}
