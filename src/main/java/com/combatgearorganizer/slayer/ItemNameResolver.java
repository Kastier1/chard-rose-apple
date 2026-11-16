package com.combatgearorganizer.slayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import lombok.Value;

/**
 * Finds item ids for the drop names in the bundled data, which has names but no ids. Walks every item in the game a
 * batch at a time (call {@link #step} on the client thread, once a tick) and keeps, for each wanted name, the first
 * exact, case-insensitive match that isn't a noted item or a bank placeholder. A tradeable match beats an
 * untradeable one, so prices work. Names it never finds stay unresolved and the panel shows them as text.
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
	private int next;
	private boolean done;

	public ItemNameResolver(Collection<String> names)
	{
		for (String n : names)
		{
			if (n != null && !n.trim().isEmpty())
			{
				wanted.add(CollectionLog.key(n));
			}
		}
		done = wanted.isEmpty();
	}

	/** Look at one item. Items must be offered in id order for "first match" to mean the lowest id. */
	public void offer(int id, Candidate c)
	{
		if (c == null || c.getName() == null || c.isNoted() || c.isPlaceholder())
		{
			return;
		}
		String key = CollectionLog.key(c.getName());
		if (!wanted.contains(key) || tradeable.contains(key))
		{
			return;
		}
		if (c.isTradeable())
		{
			tradeable.add(key);
			ids.put(key, id);
		}
		else
		{
			ids.putIfAbsent(key, id);
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

	/** The item id for a drop name, or -1 when there is none (or the walk hasn't got to it yet). */
	public int idOf(String name)
	{
		Integer id = ids.get(CollectionLog.key(name));
		return id != null ? id : -1;
	}
}
