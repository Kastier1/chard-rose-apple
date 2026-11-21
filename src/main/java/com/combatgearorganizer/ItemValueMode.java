package com.combatgearorganizer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How the plugin values an item: what it sells for on the Grand Exchange, or what high alchemy gives. */
@Getter
@RequiredArgsConstructor
public enum ItemValueMode
{
	GRAND_EXCHANGE("Grand Exchange", "GE value"),
	HIGH_ALCHEMY("High alchemy", "High alch");

	private final String displayName;
	/** Short label for tooltips, e.g. "GE value: 1.2M". */
	private final String label;

	@Override
	public String toString()
	{
		return displayName;
	}
}
