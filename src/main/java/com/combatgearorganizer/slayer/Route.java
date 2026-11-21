package com.combatgearorganizer.slayer;

import lombok.Value;

/**
 * The route the player started from the panel with Go, as the route line at the top of the panel shows it.
 */
@Value
public class Route
{
	public static final Route NONE = new Route(Kind.NONE, null);

	public enum Kind
	{
		NONE,
		/** Shortest Path is showing our path. */
		SHORTEST_PATH,
		/** No Shortest Path: the places are marked on the world map. */
		WORLD_MAP,
	}

	Kind kind;
	/** The place's name, or null for Go to nearest. */
	String place;

	public static Route shortestPath(String place)
	{
		return new Route(Kind.SHORTEST_PATH, place);
	}

	public static Route worldMap(String place)
	{
		return new Route(Kind.WORLD_MAP, place);
	}

	public boolean isShown()
	{
		return kind != Kind.NONE;
	}

	/** "On route to Catacombs of Kourend", or null when there is no route. */
	public String text()
	{
		switch (kind)
		{
			case SHORTEST_PATH:
				return "On route to " + (place != null ? place : "the nearest place");
			case WORLD_MAP:
				return "Marked on your world map";
			default:
				return null;
		}
	}

	/** The button that ends the route. */
	public String buttonText()
	{
		return kind == Kind.WORLD_MAP ? "Remove" : "Cancel";
	}

	public String buttonTooltip()
	{
		return kind == Kind.WORLD_MAP ? "Remove the marker from your world map" : "Clear this path in Shortest Path";
	}
}
