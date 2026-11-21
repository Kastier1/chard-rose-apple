package com.combatgearorganizer.slayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import lombok.Value;

/**
 * Finds item ids for the drop names in the bundled data, which has names but no ids. Walks every item in the game a
 * batch at a time (call {@link #step} on the client thread, once a tick) and keeps, for each wanted name, the first
 * exact, case-insensitive match that isn't a noted item or a bank placeholder. A tradeable match beats an
 * untradeable one, so prices work. When a name has no exact match, a wiki-style name such as "Crawling hand (item)"
 * falls back to the item with the part in brackets left off ("Crawling hand"). Names it never finds stay unresolved
 * and the panel shows them as text.
 */
public class ItemNameResolver
{
	/** What the resolver needs to know about one item id. */
	@Value
	public static class Candidate
	{
		String name;
		boolean noted;
		boolean placeholder;
		boolean tradeable;
	}

	private final Set<String> wanted = new HashSet<>();
	private final Map<String, Integer> ids = new HashMap<>();
	private final Set<String> tradeable = new HashSet<>();
	/** Wanted names by their name with a trailing "(...)" left off, for names that have one. */
	private final Map<String, List<String>> byBase = new HashMap<>();
	private final Map<String, Integer> baseIds = new HashMap<>();
	private final Set<String> baseTradeable = new HashSet<>();
	private int next;
	private boolean done;

	public ItemNameResolver(Collection<String> names)
	{
		for (String n : names)
		{
			if (n != null && !n.trim().isEmpty())
			{
				String key = CollectionLog.key(n);
				wanted.add(key);
				String base = base(key);
				if (base != null)
				{
					List<String> full = byBase.computeIfAbsent(base, b -> new ArrayList<>());
					if (!full.contains(key))
					{
						full.add(key);
					}
				}
			}
		}
		done = wanted.isEmpty();
	}

	/** "crawling hand" for "crawling hand (item)"; null when the name doesn't end in brackets. */
	static String base(String key)
	{
		if (!key.endsWith(")"))
		{
			return null;
		}
		int open = key.lastIndexOf(" (");
		if (open <= 0)
		{
			return null;
		}
		return key.substring(0, open).trim();
	}

	/** Look at one item. Items must be offered in id order for "first match" to mean the lowest id. */
	public void offer(int id, Candidate c)
	{
		if (c == null || c.getName() == null || c.isNoted() || c.isPlaceholder())
		{
			return;
		}
		String key = CollectionLog.key(c.getName());
		if (wanted.contains(key))
		{
			keep(key, id, c.isTradeable(), ids, tradeable);
		}
		List<String> fullNames = byBase.get(key);
		if (fullNames != null)
		{
			for (String full : fullNames)
			{
				keep(full, id, c.isTradeable(), baseIds, baseTradeable);
			}
		}
	}

	private static void keep(String key, int id, boolean isTradeable, Map<String, Integer> found, Set<String> tradeableFound)
	{
		if (tradeableFound.contains(key))
		{
			return;
		}
		if (isTradeable)
		{
			tradeableFound.add(key);
			found.put(key, id);
		}
		else
		{
			found.putIfAbsent(key, id);
		}
	}

	/**
	 * Look at the next {@code batch} item ids.
	 *
	 * @param itemCount how many item ids the game has
	 * @param read      the item for an id, or null to skip it
	 * @return true when this call finished the walk
	 */
	public boolean step(int itemCount, int batch, IntFunction<Candidate> read)
	{
		if (done)
		{
			return false;
		}
		int end = Math.min(itemCount, next + Math.max(1, batch));
		for (; next < end; next++)
		{
			offer(next, read.apply(next));
		}
		// Stop early once every name has a tradeable match: nothing later can beat it
		if (next >= itemCount || tradeable.size() == wanted.size())
		{
			done = true;
			return true;
		}
		return false;
	}

	public boolean isDone()
	{
		return done;
	}

	/**
	 * The item id for a drop name, or -1 when there is none (or the walk hasn't got to it yet). An exact match always
	 * beats one found with the brackets left off, so those are only used once the walk is done.
	 */
	public int idOf(String name)
	{
		String key = CollectionLog.key(name);
		Integer id = ids.get(key);
		if (id == null && done)
		{
			id = baseIds.get(key);
		}
		return id != null ? id : -1;
	}
}
