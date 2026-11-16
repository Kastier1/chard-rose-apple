package com.combatgearorganizer.slayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemNameResolverTest
{
	private static ItemNameResolver.Candidate item(String name, boolean tradeable)
	{
		return new ItemNameResolver.Candidate(name, false, false, tradeable);
	}

	@Test
	public void exactCaseInsensitiveMatch()
	{
		ItemNameResolver r = new ItemNameResolver(Arrays.asList("Abyssal whip", "Dark totem base"));
		r.offer(10, item("Abyssal whip (or)", true));
		r.offer(11, item("ABYSSAL WHIP", true));
		r.offer(12, item("Dark totem", false));
		assertEquals(11, r.idOf("abyssal whip"));
		assertEquals(11, r.idOf(" Abyssal whip "));
		assertEquals(-1, r.idOf("Dark totem base"));
		assertEquals(-1, r.idOf("Something else"));
	}

	@Test
	public void skipsNotesAndPlaceholders()
	{
		ItemNameResolver r = new ItemNameResolver(Collections.singletonList("Ancient shard"));
		r.offer(1, new ItemNameResolver.Candidate("Ancient shard", true, false, true));
		r.offer(2, new ItemNameResolver.Candidate("Ancient shard", false, true, false));
		assertEquals(-1, r.idOf("Ancient shard"));
		r.offer(3, item("Ancient shard", true));
		assertEquals(3, r.idOf("Ancient shard"));
	}

	@Test
	public void tradeableBeatsUntradeableAndFirstWins()
	{
		ItemNameResolver r = new ItemNameResolver(Arrays.asList("Imbued heart", "Vorki"));
		r.offer(5, item("Imbued heart", false));
		r.offer(6, item("Imbued heart", true));
		r.offer(7, item("Imbued heart", true));
		r.offer(8, item("Vorki", false));
		r.offer(9, item("Vorki", false));
		assertEquals(6, r.idOf("Imbued heart"));
		assertEquals(8, r.idOf("Vorki"));
	}

	@Test
	public void walksInBatches()
	{
		Map<Integer, ItemNameResolver.Candidate> game = new HashMap<>();
		game.put(3, item("Vorki", false));
		game.put(7, item("Unsired", false));
		ItemNameResolver r = new ItemNameResolver(Arrays.asList("Vorki", "Unsired", "Not an item"));
		assertFalse(r.step(10, 4, game::get));
		assertEquals(3, r.idOf("Vorki"));
		assertEquals(-1, r.idOf("Unsired"));
		assertFalse(r.step(10, 4, game::get));
		assertEquals(7, r.idOf("Unsired"));
		assertTrue(r.step(10, 4, game::get));
		assertTrue(r.isDone());
		// Nothing more to do
		assertFalse(r.step(10, 4, game::get));
	}

	@Test
	public void stopsEarlyWhenEveryNameIsTradeable()
	{
		ItemNameResolver r = new ItemNameResolver(Collections.singletonList("Abyssal whip"));
		assertTrue(r.step(30_000, 100, id -> id == 50 ? item("Abyssal whip", true) : null));
		assertEquals(50, r.idOf("Abyssal whip"));
	}

	@Test
	public void nothingWantedIsDone()
	{
		assertTrue(new ItemNameResolver(Arrays.asList("", null)).isDone());
	}
}
