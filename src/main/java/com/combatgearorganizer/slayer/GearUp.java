package com.combatgearorganizer.slayer;

import com.combatgearorganizer.GearStyle;
import com.combatgearorganizer.dps.GearRanking;
import com.combatgearorganizer.dps.OwnedGear;
import com.combatgearorganizer.dps.Target;
import com.combatgearorganizer.dps.WeaponRanker;
import com.combatgearorganizer.dps.WeaponScore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/**
 * The "Best with what you have" rows of the monster card: the player's best weapon per style for the selected
 * monster, as text. No Swing here.
 */
public final class GearUp
{
	/** Shown instead of the rows until the bank has been open once on this account. */
	public static final String OPEN_BANK = "Open your bank once so the plugin can see your gear.";
	public static final String NO_WEAPONS = "No weapons in your bank, inventory or worn.";
	/** How many runner-ups the tooltip lists. */
	private static final int RUNNER_UPS = 3;

	/** The fixed assumptions behind the numbers, for the tooltip. */
	public static final String ASSUMPTIONS = "Assumes your real levels with a super combat, ranging or magic potion "
		+ "and Piety, Rigour or Augury; your best other gear for the style from your bank; and that you're on task. "
		+ "Melee uses accurate or aggressive, whichever does more; ranged uses rapid. Staves cast the best standard "
		+ "spell you can (or Magic Dart). No special attacks, set effects, void or bolt effects.";

	private GearUp()
	{
	}

	/** One style's line: the best weapon, the next best, and the tooltip. */
	@Value
	public static class Row
	{
		GearStyle style;
		/** "Arclight 12.2 DPS (demonbane)", or why nothing works. */
		String text;
		/** "then Abyssal whip 11.2", or null. */
		String next;
		/** Plain text with line breaks. */
		String tooltip;
		/** False when the line says why none of the player's weapons hurt it. */
		boolean usable;
	}

	/** The monster for the DPS maths, or null when the data has no defence stats for it. */
	public static Target target(SlayerLocationData.Variant v)
	{
		SlayerLocationData.Combat c = v.getCombat();
		if (c == null)
		{
			return null;
		}
		SlayerLocationData.VariantWeakness weak = v.getWeakness();
		return Target.builder()
			.name(v.getName())
			.defenceLevel(c.getDefence())
			.magicLevel(c.getMagic())
			.magicAttack(c.getMagicAttack())
			.defStab(c.getStab())
			.defSlash(c.getSlash())
			.defCrush(c.getCrush())
			.defMagic(c.getMagicDefence())
			.defLight(c.getLight())
			.defStandard(c.getStandard())
			.defHeavy(c.getHeavy())
			.size(Math.max(1, c.getSize()))
			.attributes(new HashSet<>(v.getAttributes()))
			.weakElement(weak != null ? weak.getElement() : null)
			.weakPercent(weak != null && weak.getPercent() != null ? weak.getPercent() : 0)
			.build();
	}

	/**
	 * The rows for this monster: one per style the player has a weapon for, in melee, ranged, magic order. Empty when
	 * the monster has no defence stats in the data.
	 */
	public static List<Row> rows(OwnedGear owned, SlayerLocationData.Variant v)
	{
		Target target = target(v);
		List<Row> rows = new ArrayList<>();
		if (target == null)
		{
			return rows;
		}
		GearRanking ranking = WeaponRanker.rank(owned, target);
		for (GearStyle style : WeaponRanker.STYLES)
		{
			List<WeaponScore> best = ranking.best(style);
			if (best.isEmpty())
			{
				String why = ranking.cannotHarm(style);
				if (why != null)
				{
					rows.add(new Row(style, why, null, why, false));
				}
				continue;
			}
			WeaponScore top = best.get(0);
			String next = best.size() > 1 ? "then " + best.get(1).getName() + " " + dps(best.get(1).getDps()) : null;
			rows.add(new Row(style, headline(top), next, tooltip(best), true));
		}
		return rows;
	}

	/** "Arclight 12.2 DPS (demonbane)"; for spells, the spell: "Kodai wand 6.1 DPS (Water Surge, water weakness)". */
	static String headline(WeaponScore s)
	{
		List<String> notes = new ArrayList<>();
		if (s.getStyle() == GearStyle.MAGIC)
		{
			notes.add(s.getUsing());
		}
		notes.addAll(s.getEffects());
		return s.getName() + " " + dps(s.getDps()) + " DPS" + (notes.isEmpty() ? "" : " (" + String.join(", ", notes) + ")");
	}

	static String tooltip(List<WeaponScore> best)
	{
		WeaponScore top = best.get(0);
		StringBuilder tip = new StringBuilder();
		tip.append(top.getName()).append(": ").append(dps(top.getDps())).append(" damage per second\n")
			.append("Max hit ").append(top.getMaxHit()).append(", ").append(Math.round(top.getHitChance() * 100))
			.append("% to hit, every ").append(top.getSpeed()).append(top.getSpeed() == 1 ? " tick" : " ticks")
			.append(" (").append(top.getUsing()).append(")\n");
		if (top.getSlayerItem() != null)
		{
			tip.append("Wearing your ").append(top.getSlayerItem()).append('\n');
		}
		if (best.size() > 1)
		{
			List<String> next = new ArrayList<>();
			for (int i = 1; i < best.size() && i <= RUNNER_UPS; i++)
			{
				next.add(best.get(i).getName() + " " + dps(best.get(i).getDps()));
			}
			tip.append("Next: ").append(String.join(", ", next)).append('\n');
		}
		tip.append(ASSUMPTIONS);
		return tip.toString();
	}

	static String dps(double dps)
	{
		return String.format(Locale.ROOT, "%.1f", dps);
	}
}
