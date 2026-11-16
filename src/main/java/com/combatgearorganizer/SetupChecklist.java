package com.combatgearorganizer;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * The Combat tab setup steps for new players, and why a button can't be used yet. Pure logic.
 */
public final class SetupChecklist
{
	static final String BANK_TAGS = "Bank Tags plugin on";
	static final String TAB_EXISTS = "Tag tab exists";
	static final String HAS_GEAR = "Tab has gear in it";
	static final String LAYOUT = "Layout enabled for the tab";

	@Value
	public static class Step
	{
		String title;
		boolean done;
		/** What to click to do the step; null when done. */
		String help;
	}

	private SetupChecklist()
	{
	}

	public static List<Step> steps(CombatTabStatus s)
	{
		final String tab = s.getTag().isEmpty() ? "combat" : s.getTag();
		final List<Step> steps = new ArrayList<>();
		steps.add(step(BANK_TAGS, s.isBankTagsOn(),
			"Click the wrench at the top of the side bar, search for Bank Tags and turn it on."));
		steps.add(step(TAB_EXISTS, s.hasTagTab(),
			"In your bank, click New tag tab (the + below the tabs on the left) and name it \"" + tab
				+ "\". Or pick one of your tabs above."));
		steps.add(step(HAS_GEAR, s.getTaggedItems() > 0,
			"Wear your gear and click Add worn gear. Or drag items from your bank onto the \"" + tab + "\" tab."));
		steps.add(step(LAYOUT, s.isLayoutEnabled(),
			"Click Organize now and pick Keep. Or right-click the \"" + tab + "\" tab and pick Enable layout."));
		return steps;
	}

	public static boolean isComplete(CombatTabStatus s)
	{
		return steps(s).stream().allMatch(Step::isDone);
	}

	/** Why Organize now can't run, or null when it can. */
	public static String organizeBlocker(CombatTabStatus s)
	{
		if (!s.isLoggedIn())
		{
			return "Log in first.";
		}
		if (!s.isBankTagsOn())
		{
			return "Turn on the Bank Tags plugin first.";
		}
		if (s.getTag().isEmpty())
		{
			return "Pick a tag first.";
		}
		if (s.getTaggedItems() == 0)
		{
			return "Put some gear in the tab first.";
		}
		if (!s.isBankOpen())
		{
			return "Open your bank first.";
		}
		return null;
	}

	/** Why Add worn gear can't run, or null when it can. */
	public static String addWornBlocker(CombatTabStatus s)
	{
		if (!s.isLoggedIn())
		{
			return "Log in first.";
		}
		if (s.getTag().isEmpty())
		{
			return "Pick a tag first.";
		}
		return null;
	}

	private static Step step(String title, boolean done, String help)
	{
		return new Step(title, done, done ? null : help);
	}
}
