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

	/** The master's id for a name such as "konar" (any case); 0 if it isn't a master. */
	public static int idForName(String name)
	{
		for (int id = 1; id <= 10; id++)
		{
			String n = name(id);
			if (n != null && n.equalsIgnoreCase(name.trim()))
			{
				return id;
			}
		}
		return 0;
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
