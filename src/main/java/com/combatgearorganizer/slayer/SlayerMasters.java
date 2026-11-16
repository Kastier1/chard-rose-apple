package com.combatgearorganizer.slayer;

/**
 * Slayer master ids as stored in the SLAYER_MASTER varbit.
 */
public final class SlayerMasters
{
	public static final int KRYSTILIA = 7;
	public static final int KONAR = 8;

	private SlayerMasters()
	{
	}

	public static String name(int id)
	{
		switch (id)
		{
			case 1:
				return "Turael";
			case 2:
				return "Mazchna";
			case 3:
				return "Vannaka";
			case 4:
				return "Chaeldar";
			case 5:
				return "Duradel";
			case 6:
				return "Nieve";
			case KRYSTILIA:
				return "Krystilia";
			case KONAR:
				return "Konar";
			case 9:
				return "Spria";
			case 10:
				return "Mortimer";
			default:
				return null;
		}
	}
}
