package com.combatgearorganizer.dps;

import com.combatgearorganizer.GearStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeaponRankerTest
{
	// Monster stats from the wiki's infobox_monster bucket (tools/data/wiki_monsters.json)
	static final Target GREATER_DEMON = Target.builder().name("Greater demon").defenceLevel(81).magicLevel(1)
		.defMagic(-10).size(3).attributes(new HashSet<>(Arrays.asList("demon"))).weakElement("water").weakPercent(40)
		.build();
	static final Target BLACK_DRAGON = Target.builder().name("Black dragon").defenceLevel(200).magicLevel(100)
		.defSlash(70).defCrush(70).defMagic(60).defLight(50).defStandard(50).defHeavy(10).size(4)
		.attributes(new HashSet<>(Arrays.asList("dragon"))).build();
	static final Target ANKOU = Target.builder().name("Ankou").defenceLevel(80).magicLevel(1)
		.attributes(new HashSet<>(Arrays.asList("undead"))).weakElement("fire").weakPercent(0).build();
	static final Target KURASK = Target.builder().name("Kurask").defenceLevel(105).magicLevel(1).defSlash(20)
		.defCrush(20).size(3).attributes(new HashSet<>(Arrays.asList("leafy"))).build();
	static final Target KALPHITE_SOLDIER = Target.builder().name("Kalphite Soldier").defenceLevel(70).magicLevel(1)
		.defStab(25).defSlash(25).defCrush(5).defMagic(50).defLight(50).defStandard(30).defHeavy(50).size(3)
		.attributes(new HashSet<>(Arrays.asList("kalphite"))).build();
	static final Target VYREWATCH_SENTINEL = Target.builder().name("Vyrewatch Sentinel").defenceLevel(180).magicLevel(120)
		.defLight(100).defStandard(100).defHeavy(100).attributes(new HashSet<>(Arrays.asList("vampyre3"))).build();
	static final Target JUVINATE = Target.builder().name("Vampyre Juvinate").defenceLevel(50).magicLevel(1)
		.attributes(new HashSet<>(Arrays.asList("vampyre2"))).build();
	static final Target FERAL_VAMPYRE = Target.builder().name("Feral Vampyre").defenceLevel(30).magicLevel(1)
		.attributes(new HashSet<>(Arrays.asList("vampyre1"))).build();
	static final Target PLAIN = Target.builder().name("Dummy").defenceLevel(100).magicLevel(100).build();

	static String names(List<WeaponScore> scores)
	{
		List<String> out = new ArrayList<>();
		scores.forEach(s -> out.add(String.format(java.util.Locale.ROOT, "%s %.2f (%s, max %d, %s)", s.getName(), s.getDps(),
			s.getUsing(), s.getMaxHit(), s.getEffects())));
		return String.join("; ", out);
	}

	static WeaponScore score(List<WeaponScore> scores, String name)
	{
		return scores.stream().filter(s -> s.getName().equals(name)).findFirst().orElse(null);
	}

	static int rank(List<WeaponScore> scores, String name)
	{
		for (int i = 0; i < scores.size(); i++)
		{
			if (scores.get(i).getName().equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	@Test
	public void demonbaneBeatsTheDualMacuahuitlOnGreaterDemons()
	{
		OwnedGear bank = WikiGear.bank("Darklight", "Arclight", "Dual macuahuitl", "Abyssal whip", "Slayer helmet (i)");
		List<WeaponScore> melee = WeaponRanker.rank(bank, GREATER_DEMON).best(GearStyle.MELEE);
		String all = names(melee);
		assertEquals(all, "Arclight", melee.get(0).getName());
		assertTrue(all, melee.get(0).getEffects().contains("demonbane"));
		assertTrue(all, rank(melee, "Arclight") < rank(melee, "Dual macuahuitl"));
		// Darklight's 60% is less than it looks: a 5-tick sword with little strength (9.3 against the macuahuitl's 10.4)
		assertTrue(all, score(melee, "Darklight").getDps() > 9);
		// Wearing the slayer helmet on task
		assertEquals("Slayer helmet (i)", melee.get(0).getSlayerItem());
		// Off demons the same swords are poor
		List<WeaponScore> plain = WeaponRanker.rank(bank, PLAIN).best(GearStyle.MELEE);
		assertTrue(names(plain), rank(plain, "Arclight") > rank(plain, "Dual macuahuitl"));
		assertTrue(names(plain), score(plain, "Arclight").getEffects().isEmpty());
	}

	@Test
	public void dragonHuntersRankFirstOnDragons()
	{
		OwnedGear bank = WikiGear.bank("Dragon hunter lance", "Abyssal whip", "Ghrazi rapier", "Dual macuahuitl",
			"Dragon hunter crossbow", "Armadyl crossbow", "Dragon bolts", "Magic shortbow (i)", "Rune arrow",
			"Dragon hunter wand", "Kodai wand", "Slayer helmet (i)");
		GearRanking r = WeaponRanker.rank(bank, BLACK_DRAGON);
		List<WeaponScore> melee = r.best(GearStyle.MELEE);
		assertEquals(names(melee), "Dragon hunter lance", melee.get(0).getName());
		assertTrue(melee.get(0).getEffects().contains("dragonbane"));
		List<WeaponScore> ranged = r.best(GearStyle.RANGED);
		assertEquals(names(ranged), "Dragon hunter crossbow", ranged.get(0).getName());
		assertEquals("Dragon bolts", ranged.get(0).getUsing());
		List<WeaponScore> magic = r.best(GearStyle.MAGIC);
		assertEquals(names(magic), "Dragon hunter wand", magic.get(0).getName());
	}

	@Test
	public void dragonHunterCrossbowAddsToTheSlayerHelmet()
	{
		// "Dragon hunter crossbow": 15% + 25% = 40% more damage with the helmet, not 1.15 x 1.25
		OwnedGear helm = new OwnedGear(true, WikiGear.all("Dragon hunter crossbow", "Dragon bolts", "Slayer helmet (i)"),
			Levels.MAXED);
		OwnedGear none = new OwnedGear(true, WikiGear.all("Dragon hunter crossbow", "Dragon bolts"), Levels.MAXED);
		int withHelm = WeaponRanker.rank(helm, BLACK_DRAGON).best(GearStyle.RANGED).get(0).getMaxHit();
		int without = WeaponRanker.rank(none, BLACK_DRAGON).best(GearStyle.RANGED).get(0).getMaxHit();
		// 112 Ranged, Rigour x1.23 = 137, +8 = 145; floor(0.5 + 145 x 186 / 640) = 42
		assertEquals(42 * 140 / 100, withHelm);
		assertEquals(42 * 125 / 100, without);
	}

	@Test
	public void salveReplacesTheSlayerHelmetOnUndeadOnlyWhenItDoesMore()
	{
		String[] weapons = {"Abyssal whip", "Armadyl crossbow", "Dragon bolts", "Kodai wand"};
		for (GearStyle style : WeaponRanker.STYLES)
		{
			List<String> both = new ArrayList<>(Arrays.asList(weapons));
			both.add("Slayer helmet (i)");
			both.add("Salve amulet(ei)");
			List<String> helmOnly = new ArrayList<>(Arrays.asList(weapons));
			helmOnly.add("Slayer helmet (i)");
			List<String> salveOnly = new ArrayList<>(Arrays.asList(weapons));
			salveOnly.add("Salve amulet(ei)");
			WeaponScore best = top(both, style);
			double helm = top(helmOnly, style).getDps();
			double salve = top(salveOnly, style).getDps();
			assertEquals(style + ": " + best, Math.max(helm, salve), best.getDps(), 1e-9);
			assertEquals(style.toString(), salve > helm ? "Salve amulet(ei)" : "Slayer helmet (i)", best.getSlayerItem());
			assertEquals(salve > helm, best.getEffects().contains("salve"));
		}
		// Against undead with the (ei) and nothing better for the neck, the salve's 20% wins for magic
		List<String> bare = new ArrayList<>(Arrays.asList("Kodai wand", "Slayer helmet (i)", "Salve amulet(ei)"));
		WeaponScore magic = WeaponRanker.rank(new OwnedGear(true, WikiGear.all(bare.toArray(new String[0])), Levels.MAXED), ANKOU)
			.best(GearStyle.MAGIC).get(0);
		assertEquals("Salve amulet(ei)", magic.getSlayerItem());
		assertTrue(magic.getEffects().contains("salve"));
	}

	@Test
	public void plainSalveOnlyHelpsMelee()
	{
		OwnedGear bank = new OwnedGear(true, WikiGear.all("Armadyl crossbow", "Dragon bolts", "Salve amulet",
			"Slayer helmet (i)"), Levels.MAXED);
		WeaponScore ranged = WeaponRanker.rank(bank, ANKOU).best(GearStyle.RANGED).get(0);
		// The un-imbued salve does nothing for ranged, so the imbued helmet's 15% applies
		assertEquals("Slayer helmet (i)", ranged.getSlayerItem());
		OwnedGear melee = new OwnedGear(true, WikiGear.all("Abyssal whip", "Salve amulet (e)", "Black mask"), Levels.MAXED);
		WeaponScore whip = WeaponRanker.rank(melee, ANKOU).best(GearStyle.MELEE).get(0);
		assertEquals("Salve amulet (e)", whip.getSlayerItem());
		// Off undead the salve is never worn
		WeaponScore plain = WeaponRanker.rank(melee, PLAIN).best(GearStyle.MELEE).get(0);
		assertEquals("Black mask", plain.getSlayerItem());
	}

	private static WeaponScore top(List<String> items, GearStyle style)
	{
		List<String> all = new ArrayList<>(items);
		all.addAll(Arrays.asList(WikiGear.MELEE_ARMOUR));
		all.addAll(Arrays.asList(WikiGear.RANGED_ARMOUR));
		all.addAll(Arrays.asList(WikiGear.MAGIC_ARMOUR));
		OwnedGear bank = new OwnedGear(true, WikiGear.all(all.toArray(new String[0])), Levels.MAXED);
		return WeaponRanker.rank(bank, ANKOU).best(style).get(0);
	}

	@Test
	public void onlyLeafBladedWeaponsBroadAmmoAndMagicDartHurtKurask()
	{
		OwnedGear bank = WikiGear.bank("Abyssal whip", "Leaf-bladed battleaxe", "Leaf-bladed sword", "Armadyl crossbow",
			"Dragon bolts", "Broad bolts", "Twisted bow", "Dragon arrow", "Kodai wand", "Slayer's staff (e)");
		GearRanking r = WeaponRanker.rank(bank, KURASK);
		List<WeaponScore> melee = r.best(GearStyle.MELEE);
		assertEquals(names(melee), 2, melee.size());
		assertEquals("Leaf-bladed battleaxe", melee.get(0).getName());
		assertTrue(melee.get(0).getEffects().contains("leaf-bladed"));
		List<WeaponScore> ranged = r.best(GearStyle.RANGED);
		assertEquals(names(ranged), 1, ranged.size());
		assertEquals("Broad bolts", ranged.get(0).getUsing());
		List<WeaponScore> magic = r.best(GearStyle.MAGIC);
		assertEquals(names(magic), 1, magic.size());
		assertEquals("Slayer's staff (e)", magic.get(0).getName());
		assertEquals("Magic Dart", magic.get(0).getUsing());
		// floor(103 / 6) + 13 = 30; worn magic damage 27% + 4% (Augury): floor(30 x 1.31) = 39; no helmet owned
		assertEquals(39, magic.get(0).getMaxHit());

		OwnedGear noLeaf = WikiGear.bank("Abyssal whip", "Kodai wand", "Twisted bow", "Dragon arrow");
		GearRanking none = WeaponRanker.rank(noLeaf, KURASK);
		for (GearStyle style : WeaponRanker.STYLES)
		{
			assertTrue(style.toString(), none.best(style).isEmpty());
			assertEquals(style.toString(), WeaponRanker.LEAFY, none.cannotHarm(style));
		}
	}

	@Test
	public void kerisAgainstKalphites()
	{
		OwnedGear bank = WikiGear.bank("Keris partisan", "Abyssal whip");
		List<WeaponScore> melee = WeaponRanker.rank(bank, KALPHITE_SOLDIER).best(GearStyle.MELEE);
		WeaponScore keris = score(melee, "Keris partisan");
		assertTrue(keris.getEffects().contains("vs kalphites"));
		List<WeaponScore> plain = WeaponRanker.rank(bank, PLAIN).best(GearStyle.MELEE);
		assertTrue(keris.getMaxHit() > score(plain, "Keris partisan").getMaxHit());
		assertEquals("Keris partisan", melee.get(0).getName());
	}

	@Test
	public void vampyreTiers()
	{
		OwnedGear bank = WikiGear.bank("Abyssal whip", "Blisterwood flail", "Darklight", "Armadyl crossbow", "Dragon bolts",
			"Silver bolts", "Kodai wand");
		GearRanking sentinel = WeaponRanker.rank(bank, VYREWATCH_SENTINEL);
		assertEquals(names(sentinel.best(GearStyle.MELEE)), 1, sentinel.best(GearStyle.MELEE).size());
		assertEquals("Blisterwood flail", sentinel.best(GearStyle.MELEE).get(0).getName());
		assertTrue(sentinel.best(GearStyle.RANGED).isEmpty());
		assertEquals(WeaponRanker.VAMPYRE_3, sentinel.cannotHarm(GearStyle.RANGED));
		assertTrue(sentinel.best(GearStyle.MAGIC).isEmpty());

		GearRanking juvinate = WeaponRanker.rank(bank, JUVINATE);
		List<WeaponScore> melee = juvinate.best(GearStyle.MELEE);
		assertEquals(names(melee), 2, melee.size());
		assertNull(score(melee, "Abyssal whip"));
		assertEquals("Silver bolts", juvinate.best(GearStyle.RANGED).get(0).getUsing());
		assertEquals(WeaponRanker.NO_MAGIC_VAMPYRE, juvinate.cannotHarm(GearStyle.MAGIC));

		// Tier 1: anything works, silver does 10% more
		GearRanking feral = WeaponRanker.rank(bank, FERAL_VAMPYRE);
		assertTrue(score(feral.best(GearStyle.MELEE), "Darklight").getEffects().contains("silver"));
		assertTrue(score(feral.best(GearStyle.MELEE), "Blisterwood flail").getEffects().contains("vampyrebane"));
		assertNotNull(score(feral.best(GearStyle.MELEE), "Abyssal whip"));
		assertEquals("Dragon bolts", feral.best(GearStyle.RANGED).get(0).getUsing());
	}

	@Test
	public void elementalWeaknessPicksTheSpell()
	{
		OwnedGear bank = WikiGear.bank("Kodai wand");
		WeaponScore kodai = WeaponRanker.rank(bank, GREATER_DEMON).best(GearStyle.MAGIC).get(0);
		assertEquals("Water Surge", kodai.getUsing());
		assertTrue(kodai.getEffects().contains("water weakness"));
		// Base 24; magic damage 15 + 3 + 3 + 3 + 5 + 1 + 5 + 2 + 5 = 42%, +4% Augury: floor(24 x 1.46) = 35; +40% of 24 = 9
		assertEquals(35 + 9, kodai.getMaxHit());
		// No weakness: Fire Surge (or any surge), 35
		WeaponScore plain = WeaponRanker.rank(bank, PLAIN).best(GearStyle.MAGIC).get(0);
		assertEquals(35, plain.getMaxHit());
		assertTrue(plain.getUsing().endsWith("Surge"));
	}

	@Test
	public void poweredStavesUseTheirOwnSpell()
	{
		OwnedGear bank = WikiGear.bank("Tumeken's shadow", "Sanguinesti staff", "Trident of the Swamp", "Kodai wand");
		List<WeaponScore> magic = WeaponRanker.rank(bank, PLAIN).best(GearStyle.MAGIC);
		assertEquals(names(magic), "Tumeken's shadow", magic.get(0).getName());
		WeaponScore shadow = magic.get(0);
		assertEquals("built-in spell", shadow.getUsing());
		// 103 Magic: base 35. Worn magic damage 27% (ward and all, no 2h shield: 22%) x3 = 66%, +4%: floor(35 x 1.70)
		assertEquals(59, shadow.getMaxHit());
		assertEquals(5, shadow.getSpeed());
		WeaponScore sang = score(magic, "Sanguinesti staff");
		assertEquals(4, sang.getSpeed());
		// 103 / 3 = 34; 27% + 4%: floor(34 x 1.31) = 44
		assertEquals(44, sang.getMaxHit());
	}

	@Test
	public void scytheHitsBigMonstersThreeTimes()
	{
		OwnedGear bank = WikiGear.bank("Scythe of Vitur");
		Target small = PLAIN.toBuilder().size(1).build();
		Target big = PLAIN.toBuilder().size(3).build();
		double one = WeaponRanker.rank(bank, small).best(GearStyle.MELEE).get(0).getDps();
		double three = WeaponRanker.rank(bank, big).best(GearStyle.MELEE).get(0).getDps();
		assertTrue(three > one * 1.6 && three < one * 1.8);
		// "Scythe of Vitur": a base max of 47 is 47 + 23 + 11
		assertEquals(CombatMath.averageHit(1, 47) + CombatMath.averageHit(1, 23) + CombatMath.averageHit(1, 11),
			WeaponRanker.meleeAverage("Scythe of Vitur", 1, 47, 3), 1e-12);
		// "Dual macuahuitl": a max hit of 41 is split 20 + 21, the second needing the first
		assertEquals(CombatMath.averageHit(0.5, 20) + 0.5 * CombatMath.averageHit(0.5, 21),
			WeaponRanker.meleeAverage("Dual macuahuitl", 0.5, 41, 1), 1e-12);
		// "Osmumten's fang": 15% to 85% of 60 is 9 to 51
		assertEquals(30, WeaponRanker.meleeAverage("Osmumten's fang", 1, 60, 1), 1e-12);
	}

	@Test
	public void abyssalWhipWorkedByHandFromTheWikiFormulas()
	{
		// Only the whip, maxed levels, a super combat potion and Piety, against defence 100 with no slash bonus.
		// Attack and Strength 99 + 5 + 14 = 118; defence roll (100 + 9) x 64 = 6976; whip +82 slash, +82 strength.
		OwnedGear whip = new OwnedGear(true, WikiGear.all("Abyssal whip"), Levels.MAXED);
		WeaponScore s = WeaponRanker.rank(whip, PLAIN).best(GearStyle.MELEE).get(0);
		// Accurate: attack floor(118 x 1.2) + 3 + 8 = 152, strength floor(118 x 1.23) + 8 = 153
		double accurateHit = 1 - (6976 + 2.0) / (2.0 * (152 * 146 + 1));
		int accurateMax = (int) Math.floor(0.5 + 153 * 146 / 640.0);
		// Aggressive: attack 141 + 8 = 149, strength 145 + 3 + 8 = 156
		double aggressiveHit = 1 - (6976 + 2.0) / (2.0 * (149 * 146 + 1));
		int aggressiveMax = (int) Math.floor(0.5 + 156 * 146 / 640.0);
		assertEquals(35, accurateMax);
		assertEquals(36, aggressiveMax);
		double accurate = accurateHit * (accurateMax / 2.0 + 1.0 / (accurateMax + 1)) / 2.4;
		double aggressive = aggressiveHit * (aggressiveMax / 2.0 + 1.0 / (aggressiveMax + 1)) / 2.4;
		assertTrue(aggressive > accurate);
		assertEquals(aggressive, s.getDps(), 1e-9);
		assertEquals(36, s.getMaxHit());
		assertEquals(aggressiveHit, s.getHitChance(), 1e-12);
		assertEquals(4, s.getSpeed());
		assertEquals("slash", s.getUsing());
		assertTrue(s.getGear().isEmpty());
	}

	@Test
	public void silverlightAndBurningClawsAreDemonbane()
	{
		OwnedGear bank = WikiGear.bank("Silverlight", "Burning claws");
		List<WeaponScore> demon = WeaponRanker.rank(bank, GREATER_DEMON).best(GearStyle.MELEE);
		List<WeaponScore> plain = WeaponRanker.rank(bank, GREATER_DEMON.toBuilder().attributes(new HashSet<>()).build())
			.best(GearStyle.MELEE);
		for (String name : new String[]{"Silverlight", "Burning claws"})
		{
			assertTrue(names(demon), score(demon, name).getEffects().contains("demonbane"));
			assertTrue(names(plain), score(plain, name).getEffects().isEmpty());
		}
		// 60% more accuracy and damage: Silverlight's hit chance goes up on demons too, not just its max hit
		assertTrue(score(demon, "Silverlight").getHitChance() > score(plain, "Silverlight").getHitChance());
		assertEquals(score(plain, "Silverlight").getMaxHit() * 160 / 100, score(demon, "Silverlight").getMaxHit());
		assertEquals(score(plain, "Burning claws").getMaxHit() * 105 / 100, score(demon, "Burning claws").getMaxHit());
	}

	@Test
	public void scoresListTheGearTheyAssume()
	{
		OwnedGear bank = WikiGear.bank("Abyssal whip", "Dual macuahuitl", "Armadyl crossbow", "Dragon bolts", "Kodai wand",
			"Slayer helmet (i)");
		GearRanking r = WeaponRanker.rank(bank, GREATER_DEMON);
		WeaponScore whip = score(r.best(GearStyle.MELEE), "Abyssal whip");
		// On task the helmet replaces the faceguard; every other melee slot is the best melee piece, shield included
		assertEquals("Slayer helmet (i)", whip.getSlayerItem());
		assertTrue(whip.getGear().toString(), whip.getGear().contains("Slayer helmet (i)"));
		assertFalse(whip.getGear().toString(), whip.getGear().contains("Neitiznot faceguard"));
		assertTrue(whip.getGear().toString(), whip.getGear().contains("Avernic defender"));
		assertEquals(9, whip.getGear().size());
		// Two-handed: no shield
		WeaponScore mac = score(r.best(GearStyle.MELEE), "Dual macuahuitl");
		assertFalse(mac.getGear().toString(), mac.getGear().contains("Avernic defender"));
		assertEquals(8, mac.getGear().size());
		// Ranged: the ammo is what it shoots, not part of the worn list; the gear is ranged gear
		WeaponScore acb = r.best(GearStyle.RANGED).get(0);
		assertEquals("Dragon bolts", acb.getUsing());
		assertFalse(acb.getGear().contains("Dragon bolts"));
		assertTrue(acb.getGear().contains("Masori body (f)"));
		assertTrue(r.best(GearStyle.MAGIC).get(0).getGear().contains("Ancestral robe top"));
	}

	@Test
	public void ammoAndTwoHandedWeapons()
	{
		// A crossbow with no bolts can't be ranked; rune crossbow can't fire dragon bolts
		OwnedGear noBolts = new OwnedGear(true, WikiGear.all("Rune crossbow", "Dragon bolts"), Levels.MAXED);
		GearRanking r = WeaponRanker.rank(noBolts, PLAIN);
		assertTrue(r.best(GearStyle.RANGED).isEmpty());
		assertEquals(WeaponRanker.NO_AMMO, r.cannotHarm(GearStyle.RANGED));
		// The best ammo that fits: amethyst broad bolts in a rune crossbow
		OwnedGear rune = new OwnedGear(true, WikiGear.all("Rune crossbow", "Dragon bolts", "Amethyst broad bolts",
			"Broad bolts"), Levels.MAXED);
		assertEquals("Amethyst broad bolts", WeaponRanker.rank(rune, PLAIN).best(GearStyle.RANGED).get(0).getUsing());
		// The blowpipe uses the player's darts, or assumes dragon darts
		OwnedGear pipe = new OwnedGear(true, WikiGear.all("Toxic blowpipe"), Levels.MAXED);
		WeaponScore bp = WeaponRanker.rank(pipe, PLAIN).best(GearStyle.RANGED).get(0);
		assertEquals("Dragon darts (assumed)", bp.getUsing());
		assertEquals(2, bp.getSpeed());
		// A two-handed weapon drops the shield: the whip gets the defender's bonuses, the macuahuitl doesn't
		OwnedGear shield = new OwnedGear(true, WikiGear.all("Dual macuahuitl", "Avernic defender"), Levels.MAXED);
		OwnedGear noShield = new OwnedGear(true, WikiGear.all("Dual macuahuitl"), Levels.MAXED);
		assertEquals(WeaponRanker.rank(noShield, PLAIN).best(GearStyle.MELEE).get(0).getDps(),
			WeaponRanker.rank(shield, PLAIN).best(GearStyle.MELEE).get(0).getDps(), 1e-12);
		// Nothing of a style: an empty list and no reason
		GearRanking empty = WeaponRanker.rank(new OwnedGear(true, WikiGear.all(WikiGear.MELEE_ARMOUR), Levels.MAXED), PLAIN);
		for (GearStyle style : WeaponRanker.STYLES)
		{
			assertTrue(empty.best(style).isEmpty());
			assertNull(empty.cannotHarm(style));
		}
	}

	@Test
	public void lowerLevelsHitLess()
	{
		List<String> items = Arrays.asList("Abyssal whip", "Armadyl crossbow", "Dragon bolts", "Kodai wand");
		OwnedGear maxed = new OwnedGear(true, WikiGear.all(items.toArray(new String[0])), Levels.MAXED);
		OwnedGear low = new OwnedGear(true, maxed.getPieces(), Levels.of(70, 70, 70, 70));
		for (GearStyle style : WeaponRanker.STYLES)
		{
			assertTrue(style.toString(), WeaponRanker.rank(low, PLAIN).best(style).get(0).getDps()
				< WeaponRanker.rank(maxed, PLAIN).best(style).get(0).getDps());
		}
		// At 70 Magic the best spell is a wave
		assertTrue(WeaponRanker.rank(low, PLAIN).best(GearStyle.MAGIC).get(0).getUsing().endsWith("Wave"));
		assertEquals(Levels.MAXED, Levels.of(0, -1, 0, 0));
	}

	@Test
	public void blowpipeIsFastAgainstDragonsToo()
	{
		// The blowpipe shoots every 2 ticks on rapid; against black dragons' light defence it keeps up with the
		// dragon hunter crossbow (about 10 DPS each with this gear), so the order between them is the maths, not a rule
		OwnedGear bank = WikiGear.bank("Dragon hunter crossbow", "Dragon bolts", "Toxic blowpipe", "Dragon dart",
			"Slayer helmet (i)");
		List<WeaponScore> ranged = WeaponRanker.rank(bank, BLACK_DRAGON).best(GearStyle.RANGED);
		assertEquals(names(ranged), 3, ranged.size());
		assertTrue(names(ranged), Math.abs(ranged.get(0).getDps() - ranged.get(1).getDps()) < 0.5);
	}

	@Test
	public void attackTypesFollowTheWeaponsCategory()
	{
		// The scythe has +70 stab but no stab style; flails only crush; a whip only slashes
		assertEquals(Arrays.asList("slash", "crush"), WeaponRanker.attackTypes("Scythe of Vitur",
			WikiGear.get("Scythe of Vitur").getBonuses()));
		assertEquals(Arrays.asList("crush"), WeaponRanker.attackTypes("Blisterwood flail",
			WikiGear.get("Blisterwood flail").getBonuses()));
		assertEquals(Arrays.asList("slash"), WeaponRanker.attackTypes("Abyssal whip", WikiGear.get("Abyssal whip").getBonuses()));
		// Others: every type with a bonus (the dual macuahuitl stabs and crushes, the lance all three)
		assertEquals(Arrays.asList("stab", "crush"), WeaponRanker.attackTypes("Dual macuahuitl",
			WikiGear.get("Dual macuahuitl").getBonuses()));
		assertEquals(Arrays.asList("stab", "slash", "crush"), WeaponRanker.attackTypes("Dragon hunter lance",
			WikiGear.get("Dragon hunter lance").getBonuses()));
		// A pickaxe isn't an axe
		assertEquals(Arrays.asList("stab", "crush"), WeaponRanker.attackTypes("Dragon pickaxe",
			com.combatgearorganizer.Bonuses.builder().stab(38).slash(32).crush(42).build()));
		// No positive bonus: the best allowed one
		assertEquals(Arrays.asList("slash"), WeaponRanker.attackTypes("Dragon battleaxe",
			com.combatgearorganizer.Bonuses.builder().stab(-2).slash(-1).crush(-3).build()));

		// Against a monster with no stab defence and huge slash and crush defence the scythe still can't stab
		Target stabWeak = PLAIN.toBuilder().defSlash(300).defCrush(300).size(3).build();
		WeaponScore scythe = WeaponRanker.rank(WikiGear.bank("Scythe of Vitur"), stabWeak).best(GearStyle.MELEE).get(0);
		assertTrue(scythe.getUsing(), !"stab".equals(scythe.getUsing()));
	}

	@Test
	public void eclipseAtlatlHitsWithStrength()
	{
		com.combatgearorganizer.Bonuses atlatlStats = com.combatgearorganizer.Bonuses.builder().range(87).strength(16)
			.attackSpeed(4).build();
		GearPiece atlatl = new GearPiece(1, "Eclipse atlatl", com.combatgearorganizer.GearSlot.WEAPON, true, atlatlStats);
		GearPiece darts = new GearPiece(2, "Atlatl dart", com.combatgearorganizer.GearSlot.AMMO, false,
			com.combatgearorganizer.Bonuses.builder().build());
		GearPiece torture = WikiGear.get("Amulet of torture");
		List<GearPiece> pieces = Arrays.asList(atlatl, darts, torture);
		// The torture is a melee amulet, so it isn't worn for ranged: strength 16 from the atlatl alone
		WeaponScore strong = WeaponRanker.rank(new OwnedGear(true, pieces, Levels.of(99, 99, 99, 99)), PLAIN)
			.best(GearStyle.RANGED).get(0);
		WeaponScore weak = WeaponRanker.rank(new OwnedGear(true, pieces, Levels.of(99, 60, 99, 99)), PLAIN)
			.best(GearStyle.RANGED).get(0);
		// 99 Strength: 118 x 1.23 = 145, +8 = 153; floor(0.5 + 153 x 80 / 640) = 19
		assertEquals(19, strong.getMaxHit());
		assertTrue(weak.getMaxHit() < strong.getMaxHit());
		// With no atlatl darts it can't fire
		assertTrue(WeaponRanker.rank(new OwnedGear(true, Arrays.asList(atlatl), Levels.MAXED), PLAIN).best(GearStyle.RANGED).isEmpty());
		// A plain slayer helmet counts for it (melee bonus): 7/6
		GearPiece helm = new GearPiece(3, "Slayer helmet", com.combatgearorganizer.GearSlot.HEAD, false,
			com.combatgearorganizer.Bonuses.builder().build());
		WeaponScore helmed = WeaponRanker.rank(new OwnedGear(true, Arrays.asList(atlatl, darts, helm), Levels.MAXED), PLAIN)
			.best(GearStyle.RANGED).get(0);
		assertEquals("Slayer helmet", helmed.getSlayerItem());
		assertEquals(19 * 7 / 6, helmed.getMaxHit());
	}

	@Test
	public void sameNameKeepsTheBestCopy()
	{
		List<GearPiece> pieces = new ArrayList<>(WikiGear.all("Abyssal whip"));
		GearPiece whip = WikiGear.get("Abyssal whip");
		pieces.add(new GearPiece(1, whip.getName(), whip.getSlot(), false, whip.getBonuses()));
		List<WeaponScore> melee = WeaponRanker.rank(new OwnedGear(true, pieces, Levels.MAXED), PLAIN).best(GearStyle.MELEE);
		assertEquals(1, melee.size());
	}
}
