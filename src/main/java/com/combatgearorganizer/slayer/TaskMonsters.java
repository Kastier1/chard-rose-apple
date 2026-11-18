package com.combatgearorganizer.slayer;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether a monster, by name, counts for a task. Used to decide which loot belongs to the task. Matches the bundled
 * variant names exactly, or the task name as a whole word the way the core Slayer plugin does ("Abyssal demons"
 * matches "Abyssal demon" and "Greater abyssal demon").
 */
final class TaskMonsters
{
	private TaskMonsters()
	{
	}

	static boolean matches(String npcName, String taskName, Collection<String> variantNames)
	{
		return matches(npcName, taskName, variantNames, Collections.emptyList());
	}

	/**
	 * As above, but never for the names in {@code notCounting}: the task's bosses that don't count for this master,
	 * which the task name can match too (the King Black Dragon on Krystilia's black dragon task).
	 */
	static boolean matches(String npcName, String taskName, Collection<String> variantNames, Collection<String> notCounting)
	{
		if (npcName == null)
		{
			return false;
		}
		String name = normalise(npcName);
		if (name.isEmpty() || isAny(name, notCounting))
		{
			return false;
		}
		for (String v : variantNames)
		{
			if (v != null && normalise(v).equals(name))
			{
				return true;
			}
		}
		if (taskName == null || taskName.isEmpty())
		{
			return false;
		}
		String task = normalise(taskName);
		return word(task).matcher(name).find() || (task.endsWith("s") && word(task.substring(0, task.length() - 1)).matcher(name).find());
	}

	/** Whether the NPC's name (normalised) is one of these monster names. */
	static boolean isAny(String npcName, Collection<String> names)
	{
		if (npcName == null)
		{
			return false;
		}
		String name = normalise(npcName);
		return names.stream().anyMatch(n -> n != null && normalise(n).equals(name));
	}

	private static Pattern word(String s)
	{
		return Pattern.compile("(?:\\s|^)" + Pattern.quote(s) + "(?:\\s|$)");
	}

	private static String normalise(String s)
	{
		return s.replace('\u00A0', ' ').replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ENGLISH);
	}
}
