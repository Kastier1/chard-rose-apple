package com.combatgearorganizer.dps;

import com.combatgearorganizer.Bonuses;
import com.combatgearorganizer.GearSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real item bonuses for the tests, copied from the OSRS Wiki's infobox_bonuses bucket (2026-11-18): attack bonuses
 * (stab, slash, crush, magic, ranged), melee strength, ranged strength, magic damage %, attack speed. The item ids are
 * made up: the ranking only goes by name and bonuses.
 */
public final class WikiGear
{
	private static final Map<String, GearPiece> BY_NAME = new HashMap<>();
	private static int nextId = 100_000;

	static
	{
		for (GearPiece p : new GearPiece[]{
			piece("Neitiznot faceguard", GearSlot.HEAD, false, 0, 0, 0, 0, 0, 6, 0, 0f, 0),
			piece("Bandos chestplate", GearSlot.BODY, false, 0, 0, 0, -15, -10, 4, 0, 0f, 0),
			piece("Bandos tassets", GearSlot.LEGS, false, 0, 0, 0, -21, -7, 2, 0, 0f, 0),
			piece("Ferocious gloves", GearSlot.HANDS, false, 16, 16, 16, -16, -16, 14, 0, 0f, 0),
			piece("Primordial boots", GearSlot.FEET, false, 2, 2, 2, -4, -1, 5, 0, 0f, 0),
			piece("Infernal cape", GearSlot.CAPE, false, 4, 4, 4, 1, 1, 8, 0, 0f, 0),
			piece("Amulet of torture", GearSlot.NECK, false, 15, 15, 15, 0, 0, 10, 0, 0f, 0),
			piece("Berserker ring (i)", GearSlot.RING, false, 0, 0, 0, 0, 0, 8, 0, 0f, 0),
			piece("Avernic defender", GearSlot.SHIELD, false, 30, 29, 28, -5, -4, 8, 0, 0f, 0),
			piece("Masori mask (f)", GearSlot.HEAD, false, 0, 0, 0, -1, 12, 0, 2, 0f, 0),
			piece("Masori body (f)", GearSlot.BODY, false, 0, 0, 0, -4, 43, 0, 4, 0f, 0),
			piece("Masori chaps (f)", GearSlot.LEGS, false, 0, 0, 0, -2, 27, 0, 2, 0f, 0),
			piece("Zaryte vambraces", GearSlot.HANDS, false, -8, -8, -8, 0, 18, 0, 2, 0f, 0),
			piece("Pegasian boots", GearSlot.FEET, false, 0, 0, 0, -12, 12, 0, 1, 0f, 0),
			piece("Ava's assembler", GearSlot.CAPE, false, 0, 0, 0, 0, 8, 0, 2, 0f, 0),
			piece("Necklace of anguish", GearSlot.NECK, false, 0, 0, 0, 0, 15, 0, 5, 0f, 0),
			piece("Venator ring", GearSlot.RING, false, 0, 0, 0, 0, 10, 0, 2, 0f, 0),
			piece("Twisted buckler", GearSlot.SHIELD, false, -7, -8, -7, -10, 18, 0, 10, 0f, 0),
			piece("Ancestral hat", GearSlot.HEAD, false, 0, 0, 0, 8, -2, 0, 0, 3f, 0),
			piece("Ancestral robe top", GearSlot.BODY, false, 0, 0, 0, 35, -8, 0, 0, 3f, 0),
			piece("Ancestral robe bottom", GearSlot.LEGS, false, 0, 0, 0, 26, -7, 0, 0, 3f, 0),
			piece("Tormented bracelet", GearSlot.HANDS, false, 0, 0, 0, 10, 0, 0, 0, 5f, 0),
			piece("Eternal boots", GearSlot.FEET, false, 0, 0, 0, 8, 0, 0, 0, 1f, 0),
			piece("Occult necklace", GearSlot.NECK, false, 0, 0, 0, 12, 0, 0, 0, 5f, 0),
			piece("Magus ring", GearSlot.RING, false, 0, 0, 0, 15, 0, 0, 0, 2f, 0),
			piece("Elidinis' ward (f)", GearSlot.SHIELD, false, 0, 0, 0, 25, 0, 0, 0, 5f, 0),
			piece("Darklight", GearSlot.WEAPON, false, 10, 16, -2, 0, 0, 13, 0, 0f, 5),
			piece("Arclight", GearSlot.WEAPON, false, 10, 38, 0, 0, 0, 8, 0, 0f, 4),
			piece("Emberlight", GearSlot.WEAPON, false, 63, 70, 0, 0, 0, 13, 0, 0f, 4),
			piece("Dual macuahuitl", GearSlot.WEAPON, true, 115, -4, 121, -4, 0, 81, 0, 0f, 4),
			piece("Abyssal whip", GearSlot.WEAPON, false, 0, 82, 0, 0, 0, 82, 0, 0f, 4),
			piece("Dragon hunter lance", GearSlot.WEAPON, false, 85, 65, 65, 0, 0, 70, 0, 0f, 4),
			piece("Dragon hunter crossbow", GearSlot.WEAPON, false, 0, 0, 0, 0, 95, 0, 0, 0f, 6),
			piece("Armadyl crossbow", GearSlot.WEAPON, false, 0, 0, 0, 0, 100, 0, 0, 0f, 6),
			piece("Twisted bow", GearSlot.WEAPON, true, 0, 0, 0, 0, 70, 0, 20, 0f, 6),
			piece("Toxic blowpipe", GearSlot.WEAPON, true, 0, 0, 0, 0, 30, 0, 20, 0f, 3),
			piece("Kodai wand", GearSlot.WEAPON, false, 0, 0, 0, 28, 0, 0, 0, 15f, 4),
			piece("Tumeken's shadow", GearSlot.WEAPON, true, 0, 0, 0, 35, 0, 0, 0, 0f, 5),
			piece("Sanguinesti staff", GearSlot.WEAPON, false, 0, 0, 0, 25, -4, 0, 0, 0f, 4),
			piece("Keris partisan", GearSlot.WEAPON, false, 58, -2, 57, 2, 0, 45, 0, 0f, 4),
			piece("Leaf-bladed battleaxe", GearSlot.WEAPON, false, -2, 72, 72, 0, 0, 92, 0, 0f, 5),
			piece("Slayer's staff (e)", GearSlot.WEAPON, false, 7, -1, 25, 12, 0, 35, 0, 0f, 4),
			piece("Ghrazi rapier", GearSlot.WEAPON, false, 100, 55, 0, 0, 0, 93, 0, 0f, 4),
			piece("Osmumten's fang", GearSlot.WEAPON, false, 105, 75, 0, 0, 0, 103, 0, 0f, 5),
			piece("Scythe of Vitur", GearSlot.WEAPON, true, 70, 125, 30, -6, 0, 75, 0, 0f, 5),
			piece("Blisterwood flail", GearSlot.WEAPON, false, 30, 44, 68, 8, 0, 46, 0, 0f, 5),
			piece("Ivandis flail", GearSlot.WEAPON, false, 24, 38, 62, 8, 0, 42, 0, 0f, 5),
			piece("Dragon hunter wand", GearSlot.WEAPON, false, 0, 0, 0, 16, 0, 0, 0, 10f, 4),
			piece("Scorching bow", GearSlot.WEAPON, true, 0, 0, 0, 0, 124, 0, 40, 0f, 5),
			piece("Dragon bolts", GearSlot.AMMO, false, 0, 0, 0, 0, 0, 0, 122, 0f, 0),
			piece("Ruby dragon bolts (e)", GearSlot.AMMO, false, 0, 0, 0, 0, 0, 0, 122, 0f, 0),
			piece("Dragon arrow", GearSlot.AMMO, false, 0, 0, 0, 0, 0, 0, 60, 0f, 0),
			piece("Broad bolts", GearSlot.AMMO, false, 0, 0, 0, 0, 0, 0, 100, 0f, 0),
			piece("Amethyst broad bolts", GearSlot.AMMO, false, 0, 0, 0, 0, 0, 0, 115, 0f, 0),
			piece("Silver bolts", GearSlot.AMMO, false, 0, 0, 0, 0, 0, 0, 36, 0f, 0),
			piece("Rune arrow", GearSlot.AMMO, false, 0, 0, 0, 0, 0, 0, 49, 0f, 0),
			piece("Dragon dart", GearSlot.WEAPON, false, 0, 0, 0, 0, 0, 0, 35, 0f, 3),
			piece("Slayer helmet (i)", GearSlot.HEAD, false, 0, 0, 0, 3, 3, 0, 0, 0f, 0),
			piece("Salve amulet(ei)", GearSlot.NECK, false, 0, 0, 0, 0, 0, 0, 0, 0f, 0),
			piece("Salve amulet", GearSlot.NECK, false, 0, 0, 0, 0, 0, 0, 0, 0f, 0),
			piece("Black mask", GearSlot.HEAD, false, 0, 0, 0, -3, -1, 0, 0, 0f, 0),
			piece("Trident of the Swamp", GearSlot.WEAPON, false, 0, 0, 0, 25, 0, 0, 0, 0f, 4),
			piece("Leaf-bladed sword", GearSlot.WEAPON, false, 67, 62, 0, 0, 0, 50, 0, 0f, 4),
			piece("Dragon chainbody", GearSlot.BODY, false, 0, 0, 0, -15, 0, 0, 0, 0f, 0),
			piece("Rune crossbow", GearSlot.WEAPON, false, 0, 0, 0, 0, 90, 0, 0, 0f, 6),
			piece("Magic shortbow (i)", GearSlot.WEAPON, true, 0, 0, 0, 0, 75, 0, 0, 0f, 4),
			piece("Amulet of fury", GearSlot.NECK, false, 10, 10, 10, 10, 10, 8, 0, 0f, 0),
			piece("Salve amulet(i)", GearSlot.NECK, false, 0, 0, 0, 0, 0, 0, 0, 0f, 0),
			piece("Salve amulet (e)", GearSlot.NECK, false, 0, 0, 0, 0, 0, 0, 0, 0f, 0),
			piece("Black mask (i)", GearSlot.HEAD, false, 0, 0, 0, 0, 0, 0, 0, 0f, 0),
		})
		{
			BY_NAME.put(p.getName(), p);
		}
	}

	private WikiGear()
	{
	}

	private static GearPiece piece(String name, GearSlot slot, boolean twoHanded, int stab, int slash, int crush,
		int magic, int range, int strength, int rangedStrength, float magicDamage, int speed)
	{
		Bonuses b = Bonuses.builder().stab(stab).slash(slash).crush(crush).magic(magic).range(range).strength(strength)
			.rangedStrength(rangedStrength).magicDamage(magicDamage).attackSpeed(speed).build();
		return new GearPiece(nextId++, name, slot, twoHanded, b);
	}

	public static GearPiece get(String name)
	{
		GearPiece p = BY_NAME.get(name);
		if (p == null)
		{
			throw new IllegalArgumentException("No test item " + name);
		}
		return p;
	}

	public static List<GearPiece> all(String... names)
	{
		List<GearPiece> out = new ArrayList<>();
		Arrays.stream(names).forEach(n -> out.add(get(n)));
		return out;
	}

	public static final String[] MELEE_ARMOUR = {"Neitiznot faceguard", "Bandos chestplate", "Bandos tassets", "Ferocious gloves",
		"Primordial boots", "Infernal cape", "Amulet of torture", "Berserker ring (i)", "Avernic defender"};
	public static final String[] RANGED_ARMOUR = {"Masori mask (f)", "Masori body (f)", "Masori chaps (f)", "Zaryte vambraces",
		"Pegasian boots", "Ava's assembler", "Necklace of anguish", "Venator ring", "Twisted buckler"};
	public static final String[] MAGIC_ARMOUR = {"Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Tormented bracelet",
		"Eternal boots", "Occult necklace", "Magus ring", "Elidinis' ward (f)"};

	/** A well geared player's armour for every style, plus these items. */
	public static OwnedGear bank(String... items)
	{
		List<GearPiece> pieces = new ArrayList<>();
		pieces.addAll(all(MELEE_ARMOUR));
		pieces.addAll(all(RANGED_ARMOUR));
		pieces.addAll(all(MAGIC_ARMOUR));
		pieces.addAll(all(items));
		return new OwnedGear(true, pieces, Levels.MAXED);
	}
}
