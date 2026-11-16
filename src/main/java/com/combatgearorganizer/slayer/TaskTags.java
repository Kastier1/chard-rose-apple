package com.combatgearorganizer.slayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * The clear-cut facts in a task's notes, pulled out so the panel can show them as icons:
 * which styles to use, which protection prayer, and an elemental weakness.
 */
@Value
public class TaskTags
{
	public enum Style
	{
		MELEE, RANGED, MAGIC
	}

	public enum Prayer
	{
		MELEE, MISSILES, MAGIC
	}

	public enum Element
	{
		AIR, WATER, EARTH, FIRE
	}

	/** "Protect from Missiles", also catching the short "Protect from Missiles/Magic". */
	private static final Pattern PRAYER = Pattern.compile("protect from (melee|missiles|magic)((?:\\s*/\\s*(?:melee|missiles|magic))*)",
		Pattern.CASE_INSENSITIVE);
	/** One weakness: "Earth 50% (wyrms)" or "Fire 35 to 100% (level 69 mountain trolls, ice trolls)". */
	private static final Pattern ELEMENT = Pattern.compile("(air|water|earth|fire)\\s+([^,(]*?)\\s*(?:\\(([^)]*)\\))?\\s*(?:,|$)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern STYLE_WORD = Pattern.compile("\\b(melee|ranged|range|magic)\\b");

	/** Styles to kill with, best first. Empty when any style works. */
	List<Style> styles;
	/** Extra advice from the style note, e.g. "crush", "leaf-bladed", "barrage". Null if none. */
	String styleNote;
	/** Protection prayers in the order the notes give them. Several means it depends on the variant or phase. */
	List<Prayer> prayers;
	/** True when the data says the prayer is only "if needed". */
	boolean prayerOptional;
	/** True when the prayer notes say more than which prayer to use, so the panel should offer the full text. */
	boolean prayerNeedsDetail;
	/** Elemental weaknesses; several when variants differ (wyrms: earth, lava strykewyrms: water). */
	List<Weakness> weaknesses;

	@Value
	public static class Weakness
	{
		Element element;
		/** e.g. "50%" or "30 to 50%". Null if not given. */
		String amount;
		/** Which variants, e.g. "lava strykewyrms". Null when it's all of them. */
		String variants;
	}

	/** First prayer, or null. */
	public Prayer getPrayer()
	{
		return prayers.isEmpty() ? null : prayers.get(0);
	}

	public static TaskTags from(SlayerLocationData.TaskInfo info)
	{
		if (info == null)
		{
			return new TaskTags(Collections.emptyList(), null, Collections.emptyList(), false, false, Collections.emptyList());
		}

		// "Melee or magic (barrage)": styles before the bracket, the note inside it
		String style = info.getStyle() == null ? "" : info.getStyle().trim();
		String note = null;
		int bracket = style.indexOf('(');
		if (bracket >= 0)
		{
			int end = style.indexOf(')', bracket);
			note = style.substring(bracket + 1, end > bracket ? end : style.length()).trim();
			style = style.substring(0, bracket);
		}
		List<Style> styles = new ArrayList<>();
		Matcher m = STYLE_WORD.matcher(style.toLowerCase(Locale.ROOT));
		while (m.find())
		{
			Style s = m.group(1).startsWith("range") ? Style.RANGED : Style.valueOf(m.group(1).toUpperCase(Locale.ROOT));
			if (!styles.contains(s))
			{
				styles.add(s);
			}
		}

		List<Prayer> prayers = new ArrayList<>();
		boolean optional = false;
		boolean detail = false;
		String prayerText = info.getProtectionPrayer();
		if (prayerText != null)
		{
			Matcher p = PRAYER.matcher(prayerText);
			while (p.find())
			{
				addPrayer(prayers, p.group(1));
				for (String more : p.group(2).split("/"))
				{
					addPrayer(prayers, more);
				}
			}
			String lower = prayerText.toLowerCase(Locale.ROOT);
			// "None strictly needed ... Protect from Magic optional" still counts as an optional prayer
			optional = !prayers.isEmpty()
				&& (lower.contains("if needed") || lower.contains("optional") || lower.startsWith("none"));
			// Anything beyond "Protect from X" / "Protect from X if needed" is worth reading in full
			String plain = lower.replaceAll("protect from (melee|missiles|magic)", "").replace("if needed", "").trim();
			detail = !plain.isEmpty() && !plain.equals("none");
		}

		List<Weakness> weaknesses = new ArrayList<>();
		String elementText = info.getElementalWeakness();
		if (elementText != null)
		{
			Matcher e = ELEMENT.matcher(elementText.trim());
			while (e.find())
			{
				String amount = e.group(2) == null ? "" : e.group(2).trim();
				String variants = e.group(3) == null ? "" : e.group(3).trim();
				weaknesses.add(new Weakness(Element.valueOf(e.group(1).toUpperCase(Locale.ROOT)),
					amount.isEmpty() ? null : amount, variants.isEmpty() ? null : variants));
			}
		}

		return new TaskTags(Collections.unmodifiableList(styles), note == null || note.isEmpty() ? null : note,
			Collections.unmodifiableList(prayers), optional, detail, Collections.unmodifiableList(weaknesses));
	}

	private static void addPrayer(List<Prayer> prayers, String word)
	{
		String w = word.trim();
		if (w.isEmpty())
		{
			return;
		}
		Prayer prayer = Prayer.valueOf(w.toUpperCase(Locale.ROOT));
		if (!prayers.contains(prayer))
		{
			prayers.add(prayer);
		}
	}
}
