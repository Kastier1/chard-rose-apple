package com.combatgearorganizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Which armour set an item belongs to, from its name. Pure logic.
 *
 * <p>Names are matched by prefix after {@link #normalize} (lower case, no apostrophes, "dragonhide" as "dhide"), so
 * charged, degraded and ornament versions ("Dharok's helm 100", "Torva full helm (damaged)", "Bandos tassets (or)")
 * match their set. The plugin also tries the name of the item's RuneLite variation group base, for versions whose own
 * name doesn't start like the set. A prefix only matches whole words, and the longest matching prefix wins, so
 * "Bandos d'hide body" is blessed d'hide and not the Bandos set.
 *
 * <p>To add a set, add a line to {@link #SETS}: the set's name, then the start of each piece's name, including its own
 * weapon or shield when that isn't named after the set (Blood moon's Dual macuahuitl); By set puts those under the
 * set's armour.
 */
public final class GearSets
{
	private static final String[] GODS = {"Ancient", "Armadyl", "Bandos", "Guthix", "Saradomin", "Zamorak"};
	private static final String[] DHIDE_COLOURS = {"Green", "Blue", "Red", "Black"};
	private static final String[] PLATE = {"full helm", "med helm", "platebody", "chainbody", "platelegs", "plateskirt"};

	private static final List<Entry> SETS = buildSets();

	private GearSets()
	{
	}

	/** One set: its name and the (normalized) starts of its pieces' names. */
	private static final class Entry
	{
		final String name;
		final List<String> prefixes;

		Entry(String name, String... prefixes)
		{
			this.name = name;
			final List<String> normalized = new ArrayList<>();
			for (String prefix : prefixes)
			{
				normalized.add(normalize(prefix));
			}
			this.prefixes = Collections.unmodifiableList(normalized);
		}
	}

	private static List<Entry> buildSets()
	{
		final List<Entry> sets = new ArrayList<>(Arrays.asList(
			// Barrows brothers
			new Entry("Ahrim's", "Ahrim's"),
			new Entry("Dharok's", "Dharok's"),
			new Entry("Guthan's", "Guthan's"),
			new Entry("Karil's", "Karil's"),
			new Entry("Torag's", "Torag's"),
			new Entry("Verac's", "Verac's"),
			// Raids and boss armour
			new Entry("Bandos", "Bandos chestplate", "Bandos tassets", "Bandos boots"),
			new Entry("Armadyl", "Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt"),
			new Entry("Torva", "Torva"),
			new Entry("Masori", "Masori mask", "Masori body", "Masori chaps"),
			new Entry("Virtus", "Virtus"),
			new Entry("Ancestral", "Ancestral", "Twisted ancestral"),
			new Entry("Inquisitor's", "Inquisitor's"),
			new Entry("Justiciar", "Justiciar"),
			new Entry("Oathplate", "Oathplate helm", "Oathplate chest", "Oathplate legs"),
			new Entry("Crystal", "Crystal helm", "Crystal body", "Crystal legs"),
			new Entry("Obsidian", "Obsidian helmet", "Obsidian platebody", "Obsidian platelegs"),
			new Entry("Void knight", "Void knight", "Elite void", "Void melee helm", "Void ranger helm", "Void mage helm"),
			new Entry("Blood moon", "Blood moon", "Dual macuahuitl"),
			new Entry("Blue moon", "Blue moon"),
			new Entry("Eclipse moon", "Eclipse moon", "Eclipse atlatl"),
			new Entry("Sunfire fanatic", "Sunfire fanatic"),
			new Entry("Proselyte", "Proselyte"),
			new Entry("Granite", "Granite helm", "Granite body", "Granite legs"),
			new Entry("Hueycoatl hide", "Hueycoatl hide"),
			new Entry("Bloodbark", "Bloodbark"),
			new Entry("Swampbark", "Swampbark"),
			// PvP armour
			new Entry("Statius's", "Statius's"),
			new Entry("Vesta's", "Vesta's"),
			new Entry("Morrigan's", "Morrigan's"),
			new Entry("Zuriel's", "Zuriel's"),
			// Robes
			new Entry("Mystic", "Mystic hat", "Mystic robe", "Mystic gloves", "Mystic boots"),
			new Entry("Infinity", "Infinity"),
			new Entry("Lunar", "Lunar helm", "Lunar torso", "Lunar legs", "Lunar gloves", "Lunar boots", "Lunar staff"),
			new Entry("Dagon'hai", "Dagon'hai"),
			new Entry("Elder chaos", "Elder chaos"),
			new Entry("Ghostly", "Ghostly"),
			// Skilling
			new Entry("Graceful", "Graceful"),
			new Entry("Spirit angler", "Spirit angler")
		));

		for (String colour : DHIDE_COLOURS)
		{
			// "Black d'hide body", "Black d'hide chaps", "Black d'hide vambraces"
			sets.add(new Entry(colour + " d'hide", colour + " d'hide"));
		}
		for (String metal : new String[]{"Adamant", "Rune", "Dragon"})
		{
			sets.add(new Entry(metal + " armour", prefixed(metal, PLATE)));
		}
		for (String god : GODS)
		{
			// Blessed d'hide: "Saradomin coif", "Saradomin d'hide body", "Saradomin chaps", "Saradomin bracers",
			// "Saradomin d'hide boots"
			sets.add(new Entry(god + " d'hide", prefixed(god, "coif", "d'hide", "chaps", "bracers")));
			// God rune armour: "Saradomin full helm", "Saradomin platebody", "Saradomin platelegs"
			sets.add(new Entry(god + " armour", prefixed(god, "full helm", "platebody", "platelegs", "plateskirt")));
		}
		return Collections.unmodifiableList(sets);
	}

	private static String[] prefixed(String first, String... rest)
	{
		return Arrays.stream(rest).map(r -> first + " " + r).toArray(String[]::new);
	}

	/**
	 * The set the item belongs to, or null if none. Tries the item's own name, then the name of its variation group's
	 * base item, which is only looked up when needed (and may be null).
	 */
	public static String setOf(String name, Supplier<String> variationBaseName)
	{
		final String set = setOf(name);
		return set != null ? set : setOf(variationBaseName.get());
	}

	/** The set an item of this name belongs to, or null if none. */
	public static String setOf(String name)
	{
		if (name == null)
		{
			return null;
		}

		final String n = normalize(name);
		String best = null;
		int bestLength = 0;
		for (Entry set : SETS)
		{
			for (String prefix : set.prefixes)
			{
				if (prefix.length() > bestLength && startsWithWord(n, prefix))
				{
					best = set.name;
					bestLength = prefix.length();
				}
			}
		}
		return best;
	}

	/** Lower case, apostrophes dropped, "dragonhide" as "dhide", single spaces. */
	static String normalize(String name)
	{
		return name.toLowerCase(Locale.ROOT)
			.replace("'", "")
			.replace("’", "")
			.replace("dragonhide", "dhide")
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static boolean startsWithWord(String name, String prefix)
	{
		if (!name.startsWith(prefix))
		{
			return false;
		}
		if (name.length() == prefix.length())
		{
			return true;
		}
		final char next = name.charAt(prefix.length());
		return next == ' ' || next == '(';
	}
}
