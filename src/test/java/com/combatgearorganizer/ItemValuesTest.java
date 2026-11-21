package com.combatgearorganizer;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ItemValuesTest
{
	@Test
	public void moneyKeepsItsFaceValue()
	{
		assertEquals(1, ItemValues.moneyValue(ItemID.COINS));
		assertEquals(1000, ItemValues.moneyValue(ItemID.PLATINUM));
		assertEquals(-1, ItemValues.moneyValue(ItemID.ABYSSAL_WHIP));
	}

}
