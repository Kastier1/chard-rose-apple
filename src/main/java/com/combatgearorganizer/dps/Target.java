package com.combatgearorganizer.dps;

import java.util.Collections;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/** The monster being fought: its defences and the attributes some weapons care about. */
@Value
@Builder(toBuilder = true)
public class Target
{
	String name;
	int defenceLevel;
	int magicLevel;
	int magicAttack;
	int defStab;
	int defSlash;
	int defCrush;
	int defMagic;
	int defLight;
	int defStandard;
	int defHeavy;
	/** Tiles per side, at least 1. */
	@Builder.Default
	int size = 1;
	/** Lower-case wiki attributes: demon, dragon, undead, kalphite, leafy, vampyre1, vampyre2, vampyre3... */
	@Builder.Default
	Set<String> attributes = Collections.emptySet();
	/** "air", "water", "earth" or "fire"; null for none. */
	String weakElement;
	int weakPercent;

	public boolean is(String attribute)
	{
		return attributes.contains(attribute);
	}

	/** 0 for no vampyre, else its tier (1 to 3). */
	public int vampyreTier()
	{
		for (int tier = 3; tier >= 1; tier--)
		{
			if (attributes.contains("vampyre" + tier))
			{
				return tier;
			}
		}
		return 0;
	}
}
