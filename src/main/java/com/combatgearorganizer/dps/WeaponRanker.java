package com.combatgearorganizer.dps;

import com.combatgearorganizer.Bonuses;
import com.combatgearorganizer.GearClassifier;
import com.combatgearorganizer.GearSlot;
import com.combatgearorganizer.GearStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks the player's own weapons against one monster, per combat style, by expected damage per second.
 *
 * <p>Fixed assumptions (shown in the panel's tooltip): the player's real levels with a super combat potion, ranging
 * potion or magic potion, and Piety, Rigour or Augury; the rest of their gear is their best piece per slot for that
 * style by the bank tab's ranking ({@link GearClassifier#power}), with a slayer helmet, black mask or salve amulet
 * swapped in when that gives more damage; on a slayer task. Melee uses whichever of accurate or aggressive does more,
 * ranged uses rapid, powered staves use accurate. Spellbook staves cast the best standard elemental spell (strike to
 * surge) the player can cast, or Magic Dart from a staff that casts it. No special attacks, set effects, void, bolt
 * effects or ancient spells.
 */
public final class WeaponRanker
{
	public static final List<GearStyle> STYLES = Collections.unmodifiableList(
		Arrays.asList(GearStyle.MELEE, GearStyle.RANGED, GearStyle.MAGIC));

	static final String LEAFY = "Only leaf-bladed weapons, broad arrows and bolts, and Magic Dart hurt it.";
	static final String VAMPYRE_2 = "Only silver weapons or silver bolts hurt it.";
	static final String VAMPYRE_3 = "Only blisterwood and Ivandis weapons hurt it.";
	static final String NO_MAGIC_VAMPYRE = "Spells don't hurt it.";
	static final String NO_AMMO = "No ammo that fits your bows and crossbows.";

	private static final GearSlot[] OTHER_SLOTS = {GearSlot.SHIELD, GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS,
		GearSlot.HANDS, GearSlot.FEET, GearSlot.CAPE, GearSlot.NECK, GearSlot.RING};

	// Prayers ("Maximum melee hit", "Damage per second/Ranged", "Damage per second/Magic")
	private static final int PIETY_ATTACK_NUM = 120;
	private static final int PIETY_STRENGTH_NUM = 123;
	private static final int RIGOUR_ATTACK_NUM = 120;
	private static final int RIGOUR_STRENGTH_NUM = 123;
	private static final int AUGURY_ACCURACY_NUM = 125;
	/** Augury adds 4% magic damage ("Maximum magic hit", prayer bonus). */
	private static final int AUGURY_DAMAGE_PCT = 4;
	private static final int PERCENT = 100;
	/** Attack styles: accurate or aggressive add 3 to the effective level. */
	private static final int STANCE = 3;
	/** "Damage per second/Magic": accurate on a powered staff adds 2. */
	private static final int POWERED_STAFF_ACCURATE = 2;
	/** Spellbook spells take 5 ticks, whatever the staff ("Damage per second/Magic": 3 seconds). */
	private static final int SPELL_SPEED = 5;
	private static final int HARMONISED_SPELL_SPEED = 4;
	/** Weapons with no speed in the item data are treated as 4-tick weapons. */
	private static final int DEFAULT_SPEED = 4;
	/** Dragon darts, assumed loaded in a blowpipe when the player has no darts ("Ammunition": +35). */
	private static final int ASSUMED_DART_STRENGTH = 35;
	/** "Keris": 1 in 51 hits on a kalphite deal triple damage. */
	private static final double KERIS_TRIPLE_CHANCE = 1.0 / 51;
	/** "Tumeken's shadow": triples worn magic attack and magic damage, damage capped at 100%. */
	private static final int SHADOW_MULTIPLIER = 3;

	private WeaponRanker()
	{
	}

	public static GearRanking rank(OwnedGear owned, Target target)
	{
		Map<GearStyle, List<WeaponScore>> byStyle = new EnumMap<>(GearStyle.class);
		Map<GearStyle, String> cannotHarm = new EnumMap<>(GearStyle.class);
		for (GearStyle style : STYLES)
		{
			Map<GearSlot, GearPiece> others = bestOthers(owned.getPieces(), style);
			List<GearPiece> helms = owned.getPieces().stream().filter(p -> p.getSlot() == GearSlot.HEAD
				&& WeaponRules.isSlayerHelm(p.getName())).collect(java.util.stream.Collectors.toList());
			List<GearPiece> salves = target.is("undead") ? owned.getPieces().stream().filter(p -> p.getSlot() == GearSlot.NECK
				&& WeaponRules.isSalve(p.getName())).collect(java.util.stream.Collectors.toList()) : Collections.emptyList();

			Map<String, WeaponScore> best = new LinkedHashMap<>();
			String reason = null;
			boolean anyWeapon = false;
			for (GearPiece weapon : owned.getPieces())
			{
				if (weapon.getSlot() != GearSlot.WEAPON
					|| GearClassifier.classify(weapon.getName(), GearSlot.WEAPON, weapon.getBonuses()) != style)
				{
					continue;
				}
				anyWeapon = true;
				String[] why = new String[1];
				WeaponScore top = null;
				for (GearPiece head : withNull(helms))
				{
					for (GearPiece neck : withNull(salves))
					{
						WeaponScore s = score(style, weapon, others, head, neck, owned, target, why);
						if (s != null && (top == null || s.getDps() > top.getDps()))
						{
							top = s;
						}
					}
				}
				if (top == null)
				{
					reason = reason != null ? reason : why[0];
					continue;
				}
				WeaponScore old = best.get(top.getName());
				if (old == null || top.getDps() > old.getDps())
				{
					best.put(top.getName(), top);
				}
			}
			List<WeaponScore> list = new ArrayList<>(best.values());
			list.sort(Comparator.comparingDouble(WeaponScore::getDps).reversed().thenComparing(WeaponScore::getName));
			byStyle.put(style, Collections.unmodifiableList(list));
			if (list.isEmpty() && anyWeapon && reason != null)
			{
				cannotHarm.put(style, reason);
			}
		}
		return new GearRanking(Collections.unmodifiableMap(byStyle), Collections.unmodifiableMap(cannotHarm));
	}

	private static List<GearPiece> withNull(List<GearPiece> items)
	{
		List<GearPiece> out = new ArrayList<>();
		out.add(null);
		out.addAll(items);
		return out;
	}

	/** The player's strongest piece of this style in every slot but the weapon and ammo. */
	static Map<GearSlot, GearPiece> bestOthers(List<GearPiece> pieces, GearStyle style)
	{
		Map<GearSlot, GearPiece> best = new EnumMap<>(GearSlot.class);
		Map<GearSlot, Integer> power = new EnumMap<>(GearSlot.class);
		for (GearPiece p : pieces)
		{
			if (!Arrays.asList(OTHER_SLOTS).contains(p.getSlot())
				|| GearClassifier.classify(p.getName(), p.getSlot(), p.getBonuses()) != style)
			{
				continue;
			}
			int score = GearClassifier.power(style, p.getName(), p.getSlot(), p.getBonuses());
			Integer old = power.get(p.getSlot());
			if (old == null || score > old || (score == old && p.getItemId() < best.get(p.getSlot()).getItemId()))
			{
				best.put(p.getSlot(), p);
				power.put(p.getSlot(), score);
			}
		}
		return best;
	}

	/** What a slayer helmet, black mask or salve amulet adds for this style against this monster. */
	static final class SlayerBoost
	{
		static final SlayerBoost NONE = new SlayerBoost(null, false, 1, 1, 1, 1, 0);

		final String item;
		final boolean salve;
		final int accuracyNum;
		final int accuracyDen;
		final int damageNum;
		final int damageDen;
		/** Salve's magic damage, which is added to the magic damage bonus instead of multiplying. */
		final int magicDamagePct;

		SlayerBoost(String item, boolean salve, int accuracyNum, int accuracyDen, int damageNum, int damageDen,
			int magicDamagePct)
		{
			this.item = item;
			this.salve = salve;
			this.accuracyNum = accuracyNum;
			this.accuracyDen = accuracyDen;
			this.damageNum = damageNum;
			this.damageDen = damageDen;
			this.magicDamagePct = magicDamagePct;
		}

		boolean isHelm()
		{
			return item != null && !salve;
		}
	}

	/**
	 * "Salve amulet", "Slayer helmet", "Black mask", "Maximum ranged hit", "Maximum magic hit": the helmet or mask
	 * gives 7/6 to melee on task, and its imbued version 15% to ranged and magic. Salve amulets give 7/6 to melee
	 * against undead ((e): 20%), the imbued ones also 7/6 to ranged and 15% to magic ((ei): 20% to everything).
	 * The two don't stack: a salve that works for the style replaces the helmet's bonus.
	 */
	static SlayerBoost slayerBoost(GearStyle style, GearPiece helm, GearPiece salve, Target target)
	{
		if (salve != null && target.is("undead"))
		{
			boolean enchanted = WeaponRules.isEnchantedSalve(salve.getName());
			boolean imbued = WeaponRules.isImbued(salve.getName());
			if (style == GearStyle.MELEE)
			{
				return enchanted ? new SlayerBoost(salve.getName(), true, 6, 5, 6, 5, 0)
					: new SlayerBoost(salve.getName(), true, 7, 6, 7, 6, 0);
			}
			if (imbued && style == GearStyle.RANGED)
			{
				return enchanted ? new SlayerBoost(salve.getName(), true, 6, 5, 6, 5, 0)
					: new SlayerBoost(salve.getName(), true, 7, 6, 7, 6, 0);
			}
			if (imbued && style == GearStyle.MAGIC)
			{
				return enchanted ? new SlayerBoost(salve.getName(), true, 6, 5, 1, 1, 20)
					: new SlayerBoost(salve.getName(), true, 115, 100, 1, 1, 15);
			}
		}
		if (helm != null)
		{
			if (style == GearStyle.MELEE)
			{
				return new SlayerBoost(helm.getName(), false, 7, 6, 7, 6, 0);
			}
			if (WeaponRules.isImbued(helm.getName()))
			{
				return new SlayerBoost(helm.getName(), false, 115, 100, 115, 100, 0);
			}
		}
		return SlayerBoost.NONE;
	}

	/** The weapon's score with this head and neck in place of the usual ones (null: keep those); null if it can't hurt it. */
	private static WeaponScore score(GearStyle style, GearPiece weapon, Map<GearSlot, GearPiece> others, GearPiece head,
		GearPiece neck, OwnedGear owned, Target target, String[] why)
	{
		Map<GearSlot, GearPiece> worn = new EnumMap<>(others);
		if (head != null)
		{
			worn.put(GearSlot.HEAD, head);
		}
		if (neck != null)
		{
			worn.put(GearSlot.NECK, neck);
		}
		if (weapon.isTwoHanded())
		{
			worn.remove(GearSlot.SHIELD);
		}
		// The atlatl gets the slayer helmet's and salve's melee bonus ("Eclipse atlatl")
		GearStyle boostStyle = style == GearStyle.RANGED && WeaponRules.isAtlatl(weapon.getName()) ? GearStyle.MELEE : style;
		SlayerBoost boost = slayerBoost(boostStyle, head, neck, target);
		if (boost.item == null && (head != null || neck != null))
		{
			// Wearing it for nothing: the usual piece is never worse
			return null;
		}
		List<String> effects = new ArrayList<>();
		if (boost.salve)
		{
			effects.add("salve");
		}
		switch (style)
		{
			case MELEE:
				return melee(weapon, worn, boost, owned.getLevels(), target, effects, why);
			case RANGED:
				return ranged(weapon, worn, boost, owned, target, effects, why);
			default:
				return magic(weapon, worn, boost, owned.getLevels(), target, effects, why);
		}
	}

	private static WeaponScore melee(GearPiece weapon, Map<GearSlot, GearPiece> worn, SlayerBoost boost, Levels levels,
		Target t, List<String> effects, String[] why)
	{
		String name = weapon.getName();
		WeaponRules.Bane vampyrebane = WeaponRules.vampyrebane(name);
		int tier = t.vampyreTier();
		if (t.is("leafy") && !WeaponRules.isLeafBladed(name))
		{
			why[0] = LEAFY;
			return null;
		}
		if (tier == 3 && vampyrebane == null)
		{
			why[0] = VAMPYRE_3;
			return null;
		}
		if (tier == 2 && vampyrebane == null && !WeaponRules.isSilver(name))
		{
			why[0] = VAMPYRE_2;
			return null;
		}

		Bonuses total = sum(worn.values(), weapon, null);
		List<WeaponRules.Bane> banes = new ArrayList<>();
		if (t.is("demon") && WeaponRules.demonbane(name) != null)
		{
			banes.add(WeaponRules.demonbane(name));
		}
		if (t.is("dragon") && WeaponRules.dragonbane(name) != null)
		{
			banes.add(WeaponRules.dragonbane(name));
		}
		if (tier > 0 && vampyrebane != null)
		{
			banes.add(vampyrebane);
		}
		else if (tier == 1 && WeaponRules.isSilver(name))
		{
			banes.add(new WeaponRules.Bane(0, WeaponRules.SILVER_TIER1_DAMAGE, false, "silver"));
		}
		boolean keris = t.is("kalphite") && WeaponRules.isKeris(name);
		boolean leafAxe = t.is("leafy") && WeaponRules.isLeafBladedBattleaxe(name);
		banes.forEach(b -> addOnce(effects, b.label));
		if (keris)
		{
			effects.add("vs kalphites");
		}
		if (leafAxe)
		{
			effects.add("leaf-bladed");
		}

		int speed = speed(weapon);
		int attack = CombatMath.superCombat(levels.getAttack());
		int strength = CombatMath.superCombat(levels.getStrength());
		WeaponScore best = null;
		for (String type : attackTypes(name, weapon.getBonuses()))
		{
			int bonus = "stab".equals(type) ? total.getStab() : "slash".equals(type) ? total.getSlash() : total.getCrush();
			int defBonus = "stab".equals(type) ? t.getDefStab() : "slash".equals(type) ? t.getDefSlash() : t.getDefCrush();
			long defence = CombatMath.defenceRoll(t.getDefenceLevel(), defBonus);
			for (int stance = 0; stance < 2; stance++)
			{
				int effAttack = CombatMath.effectiveLevel(attack, PIETY_ATTACK_NUM, PERCENT, stance == 0 ? STANCE : 0);
				int effStrength = CombatMath.effectiveLevel(strength, PIETY_STRENGTH_NUM, PERCENT, stance == 1 ? STANCE : 0);
				long roll = CombatMath.times(CombatMath.attackRoll(effAttack, bonus), boost.accuracyNum, boost.accuracyDen);
				int max = CombatMath.times(CombatMath.baseMaxHit(effStrength, total.getStrength()), boost.damageNum, boost.damageDen);
				for (WeaponRules.Bane b : banes)
				{
					roll = CombatMath.times(roll, PERCENT + b.accuracy, PERCENT);
					max = CombatMath.times(max, PERCENT + b.damage, PERCENT);
				}
				if (keris)
				{
					max = CombatMath.times(max, 133, 100);
				}
				if (leafAxe)
				{
					max = CombatMath.times(max, 1175, 1000);
				}
				if (WeaponRules.isColossalBlade(name))
				{
					max += 2 * Math.min(t.getSize(), 5);
				}
				boolean fang = WeaponRules.isFang(name);
				double hit = fang && "stab".equals(type) ? CombatMath.fangHitChance(roll, defence) : CombatMath.hitChance(roll, defence);
				double average = meleeAverage(name, hit, max, t.getSize());
				if (keris)
				{
					average *= 1 + 2 * KERIS_TRIPLE_CHANCE;
				}
				double dps = CombatMath.dps(average, speed);
				if (best == null || dps > best.getDps())
				{
					best = new WeaponScore(GearStyle.MELEE, weapon.getItemId(), name, dps, max, hit, speed, type,
						Collections.unmodifiableList(new ArrayList<>(effects)), boost.item);
				}
			}
		}
		return best;
	}

	/** Average damage per attack for a melee weapon, with the multi-hit and damage-range weapons. */
	static double meleeAverage(String name, double hit, int max, int size)
	{
		if (WeaponRules.isScythe(name))
		{
			// One hit per tile of size, up to 3, each with half the max of the one before, each rolling accuracy
			int hits = Math.min(3, Math.max(1, size));
			double total = 0;
			int m = max;
			for (int i = 0; i < hits; i++)
			{
				total += CombatMath.averageHit(hit, m);
				m /= 2;
			}
			return total;
		}
		if (WeaponRules.isDualMacuahuitl(name))
		{
			// The first hit takes half (rounded down), the second the rest, and needs the first to land
			int first = max / 2;
			return CombatMath.averageHit(hit, first) + hit * CombatMath.averageHit(hit, max - first);
		}
		if (WeaponRules.isFang(name))
		{
			// Hits between 15% and 85% of the max, so the average is unchanged and never 0 on a hit
			int low = max * 15 / 100;
			int high = max * 85 / 100;
			return hit * (low + high) / 2.0;
		}
		return CombatMath.averageHit(hit, max);
	}

	private static WeaponScore ranged(GearPiece weapon, Map<GearSlot, GearPiece> worn, SlayerBoost boost, OwnedGear owned,
		Target t, List<String> effects, String[] why)
	{
		String name = weapon.getName();
		RangedAmmo.Kind kind = RangedAmmo.kind(name);
		int tier = t.vampyreTier();
		if (tier == 3)
		{
			why[0] = VAMPYRE_3;
			return null;
		}
		GearPiece ammo = null;
		int extraStrength = 0;
		String using;
		if (kind.needsAmmo())
		{
			ammo = bestAmmo(owned.getPieces(), kind, name, t);
			if (ammo == null)
			{
				why[0] = t.is("leafy") ? LEAFY : tier == 2 ? VAMPYRE_2 : NO_AMMO;
				return null;
			}
			using = ammo.getName();
		}
		else if (kind == RangedAmmo.Kind.BLOWPIPE)
		{
			GearPiece dart = bestDart(owned.getPieces());
			extraStrength = dart != null ? dart.getBonuses().getRangedStrength() : ASSUMED_DART_STRENGTH;
			using = dart != null ? dart.getName() : "Dragon darts (assumed)";
		}
		else
		{
			using = kind == RangedAmmo.Kind.NO_AMMO ? "its own arrows" : name;
		}
		if (t.is("leafy") && (ammo == null || !RangedAmmo.isBroad(ammo.getName())))
		{
			why[0] = LEAFY;
			return null;
		}
		if (tier == 2 && (ammo == null || !RangedAmmo.isSilver(ammo.getName())))
		{
			why[0] = VAMPYRE_2;
			return null;
		}

		Bonuses total = sum(worn.values(), weapon, ammo);
		int ranged = CombatMath.rangingPotion(owned.getLevels().getRanged());
		// Rapid: no stance bonus, one tick faster
		int effAttack = CombatMath.effectiveLevel(ranged, RIGOUR_ATTACK_NUM, PERCENT, 0);
		long roll = CombatMath.attackRoll(effAttack, total.getRange());
		int max;
		if (WeaponRules.isAtlatl(name))
		{
			// "Eclipse atlatl": the boosted Strength level (a super combat potion) with Rigour's damage multiplier and
			// the melee strength bonus; the slayer helmet's or salve's melee bonus is picked in score()
			int strength = CombatMath.superCombat(owned.getLevels().getStrength());
			max = CombatMath.baseMaxHit(CombatMath.effectiveLevel(strength, RIGOUR_STRENGTH_NUM, PERCENT, 0), total.getStrength());
		}
		else
		{
			int effStrength = CombatMath.effectiveLevel(ranged, RIGOUR_STRENGTH_NUM, PERCENT, 0);
			max = CombatMath.baseMaxHit(effStrength, total.getRangedStrength() + extraStrength);
		}

		WeaponRules.Bane bane = null;
		if (t.is("demon") && WeaponRules.demonbane(name) != null)
		{
			bane = WeaponRules.demonbane(name);
		}
		else if (t.is("dragon") && WeaponRules.dragonbane(name) != null)
		{
			bane = WeaponRules.dragonbane(name);
		}
		if (bane != null && bane.addsToSlayerHelm && boost.isHelm())
		{
			// The crossbow's and bow's bonuses add to the helmet's 15% instead of multiplying it
			roll = CombatMath.times(roll, 115 + bane.accuracy, PERCENT);
			max = CombatMath.times(max, 115 + bane.damage, PERCENT);
		}
		else
		{
			roll = CombatMath.times(roll, boost.accuracyNum, boost.accuracyDen);
			max = CombatMath.times(max, boost.damageNum, boost.damageDen);
			if (bane != null)
			{
				roll = CombatMath.times(roll, PERCENT + bane.accuracy, PERCENT);
				max = CombatMath.times(max, PERCENT + bane.damage, PERCENT);
			}
		}
		if (bane != null)
		{
			effects.add(bane.label);
		}
		if (WeaponRules.isTwistedBow(name))
		{
			int magic = Math.max(t.getMagicLevel(), t.getMagicAttack());
			roll = (long) Math.floor(roll * CombatMath.twistedBowAccuracy(magic) / PERCENT);
			max = (int) Math.floor(max * CombatMath.twistedBowDamage(magic) / PERCENT);
		}
		if (tier == 1 && ammo != null && RangedAmmo.isSilver(ammo.getName()))
		{
			max = CombatMath.times(max, PERCENT + WeaponRules.SILVER_TIER1_DAMAGE, PERCENT);
			effects.add("silver");
		}
		if (t.is("leafy"))
		{
			effects.add("broad ammo");
		}

		long defence = CombatMath.defenceRoll(t.getDefenceLevel(), rangedDefence(t, kind.defence));
		double hit = CombatMath.hitChance(roll, defence);
		int speed = Math.max(1, speed(weapon) - 1);
		double dps = CombatMath.dps(CombatMath.averageHit(hit, max), speed);
		return new WeaponScore(GearStyle.RANGED, weapon.getItemId(), name, dps, max, hit, speed, using,
			Collections.unmodifiableList(effects), boost.item);
	}

	private static int rangedDefence(Target t, RangedAmmo.Defence d)
	{
		switch (d)
		{
			case LIGHT:
				return t.getDefLight();
			case HEAVY:
				return t.getDefHeavy();
			default:
				return t.getDefStandard();
		}
	}

	/** The strongest owned ammo the weapon can fire (and that can hurt this monster), or null. */
	static GearPiece bestAmmo(List<GearPiece> pieces, RangedAmmo.Kind kind, String weaponName, Target t)
	{
		GearPiece best = null;
		for (GearPiece p : pieces)
		{
			if (p.getSlot() != GearSlot.AMMO || !RangedAmmo.fits(kind, weaponName, p.getName()))
			{
				continue;
			}
			if (t.is("leafy") && !RangedAmmo.isBroad(p.getName()))
			{
				continue;
			}
			if (t.vampyreTier() == 2 && !RangedAmmo.isSilver(p.getName()))
			{
				continue;
			}
			if (best == null || betterAmmo(p, best))
			{
				best = p;
			}
		}
		return best;
	}

	private static boolean betterAmmo(GearPiece a, GearPiece b)
	{
		Bonuses x = a.getBonuses();
		Bonuses y = b.getBonuses();
		if (x.getRangedStrength() != y.getRangedStrength())
		{
			return x.getRangedStrength() > y.getRangedStrength();
		}
		if (x.getRange() != y.getRange())
		{
			return x.getRange() > y.getRange();
		}
		return a.getItemId() < b.getItemId();
	}

	private static GearPiece bestDart(List<GearPiece> pieces)
	{
		GearPiece best = null;
		for (GearPiece p : pieces)
		{
			if (p.getSlot() == GearSlot.WEAPON && RangedAmmo.isDart(p.getName()) && (best == null || betterAmmo(p, best)))
			{
				best = p;
			}
		}
		return best;
	}

	private static WeaponScore magic(GearPiece weapon, Map<GearSlot, GearPiece> worn, SlayerBoost boost, Levels levels,
		Target t, List<String> effects, String[] why)
	{
		String name = weapon.getName();
		int tier = t.vampyreTier();
		if (tier >= 2)
		{
			why[0] = tier == 3 ? VAMPYRE_3 : NO_MAGIC_VAMPYRE;
			return null;
		}
		int magic = CombatMath.magicPotion(levels.getMagic());
		int powered = WeaponRules.poweredStaffBase(name, magic);
		boolean shadow = powered > 0 && WeaponRules.isTumekensShadow(name);
		Bonuses total = sum(worn.values(), weapon, null);
		int magicAttack = total.getMagic() * (shadow ? SHADOW_MULTIPLIER : 1);
		double magicDamage = Math.min(100, total.getMagicDamage() * (shadow ? SHADOW_MULTIPLIER : 1));
		// Of the dragonbane weapons only the wand is a magic weapon
		WeaponRules.Bane dragonbane = t.is("dragon") ? WeaponRules.dragonbane(name) : null;
		long defence = CombatMath.defenceRoll(t.getMagicLevel(), t.getDefMagic());

		List<Cast> casts = new ArrayList<>();
		if (powered > 0)
		{
			if (t.is("leafy"))
			{
				why[0] = LEAFY;
				return null;
			}
			casts.add(new Cast("built-in spell", powered, null, speed(weapon), POWERED_STAFF_ACCURATE));
		}
		else
		{
			int spellSpeed = WeaponRules.isHarmonisedStaff(name) ? HARMONISED_SPELL_SPEED : SPELL_SPEED;
			if (!t.is("leafy"))
			{
				for (StandardSpells.Spell s : StandardSpells.castable(magic))
				{
					casts.add(new Cast(s.getName(), s.getMaxHit(), s.getElement(), spellSpeed, 0));
				}
			}
			if (WeaponRules.castsMagicDart(name))
			{
				int dart = StandardSpells.magicDart(magic, WeaponRules.isEnchantedSlayerStaff(name));
				if (dart > 0)
				{
					casts.add(new Cast("Magic Dart", dart, null, SPELL_SPEED, 0));
				}
			}
			if (casts.isEmpty())
			{
				why[0] = LEAFY;
				return null;
			}
		}

		WeaponScore best = null;
		for (Cast cast : casts)
		{
			String spell = cast.name;
			int base = cast.baseMaxHit;
			String element = cast.element;
			int speed = cast.speed;
			int stance = cast.stance;
			int weakness = element != null && element.equals(t.getWeakElement()) ? t.getWeakPercent() : 0;

			int eff = CombatMath.effectiveMagicLevel(magic, AUGURY_ACCURACY_NUM, PERCENT, stance);
			long roll = CombatMath.times(CombatMath.attackRoll(eff, magicAttack), boost.accuracyNum, boost.accuracyDen);
			if (dragonbane != null)
			{
				roll = CombatMath.times(roll, PERCENT + dragonbane.accuracy, PERCENT);
			}
			if (weakness > 0)
			{
				// "Elemental weakness": 1% more accuracy and damage per point
				roll = CombatMath.times(roll, PERCENT + weakness, PERCENT);
			}
			// "Maximum magic hit" steps two and three
			int max = (int) Math.floor(base * (PERCENT + magicDamage + boost.magicDamagePct + AUGURY_DAMAGE_PCT) / PERCENT + 1e-9);
			if (boost.isHelm())
			{
				max = CombatMath.times(max, boost.damageNum, boost.damageDen);
			}
			if (dragonbane != null)
			{
				max = CombatMath.times(max, PERCENT + dragonbane.damage, PERCENT);
			}
			max += base * weakness / PERCENT;

			double hit = CombatMath.hitChance(roll, defence);
			double dps = CombatMath.dps(CombatMath.averageHit(hit, max), speed);
			if (best == null || dps > best.getDps())
			{
				List<String> fx = new ArrayList<>(effects);
				if (dragonbane != null)
				{
					fx.add(dragonbane.label);
				}
				if (weakness > 0)
				{
					fx.add(element + " weakness");
				}
				best = new WeaponScore(GearStyle.MAGIC, weapon.getItemId(), name, dps, max, hit, speed, spell,
					Collections.unmodifiableList(fx), boost.item);
			}
		}
		return best;
	}

	/** One way a magic weapon can attack: its built-in spell, a standard spell or Magic Dart. */
	private static final class Cast
	{
		final String name;
		final int baseMaxHit;
		/** For elemental weakness; null for spells that aren't elemental. */
		final String element;
		final int speed;
		final int stance;

		Cast(String name, int baseMaxHit, String element, int speed, int stance)
		{
			this.name = name;
			this.baseMaxHit = baseMaxHit;
			this.element = element;
			this.speed = speed;
			this.stance = stance;
		}
	}

	/**
	 * Stab, slash or crush: the types the weapon has a bonus in and a style for ({@link WeaponRules#meleeTypes}), or its
	 * best one when it has a bonus in none of them.
	 */
	static List<String> attackTypes(String name, Bonuses b)
	{
		java.util.Set<String> allowed = WeaponRules.meleeTypes(name);
		List<String> types = new ArrayList<>();
		int[] bonus = {b.getStab(), b.getSlash(), b.getCrush()};
		String[] names = {"stab", "slash", "crush"};
		for (int i = 0; i < 3; i++)
		{
			if (bonus[i] > 0 && (allowed == null || allowed.contains(names[i])))
			{
				types.add(names[i]);
			}
		}
		if (types.isEmpty())
		{
			int best = -1;
			for (int i = 0; i < 3; i++)
			{
				if ((allowed == null || allowed.contains(names[i])) && (best < 0 || bonus[i] > bonus[best]))
				{
					best = i;
				}
			}
			types.add(names[best]);
		}
		return types;
	}

	private static int speed(GearPiece weapon)
	{
		int s = weapon.getBonuses().getAttackSpeed();
		return s > 0 ? s : DEFAULT_SPEED;
	}

	private static void addOnce(List<String> list, String s)
	{
		if (!list.contains(s))
		{
			list.add(s);
		}
	}

	/** Every bonus added up: the worn pieces, the weapon and the ammo. */
	static Bonuses sum(Iterable<GearPiece> worn, GearPiece weapon, GearPiece ammo)
	{
		List<GearPiece> all = new ArrayList<>();
		worn.forEach(all::add);
		all.add(weapon);
		if (ammo != null)
		{
			all.add(ammo);
		}
		int stab = 0;
		int slash = 0;
		int crush = 0;
		int magic = 0;
		int range = 0;
		int strength = 0;
		int rangedStrength = 0;
		float magicDamage = 0;
		for (GearPiece p : all)
		{
			Bonuses b = p.getBonuses();
			stab += b.getStab();
			slash += b.getSlash();
			crush += b.getCrush();
			magic += b.getMagic();
			range += b.getRange();
			strength += b.getStrength();
			rangedStrength += b.getRangedStrength();
			magicDamage += b.getMagicDamage();
		}
		return Bonuses.builder().stab(stab).slash(slash).crush(crush).magic(magic).range(range).strength(strength)
			.rangedStrength(rangedStrength).magicDamage(magicDamage).build();
	}
}
