package com.combatgearorganizer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Grouping
{
	BY_STYLE("Combat style"),
	BY_SLOT("Equipment slot");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
