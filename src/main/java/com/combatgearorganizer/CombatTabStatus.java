package com.combatgearorganizer;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * What the Combat tab section of the side panel shows, read from the game and the Bank Tags settings on the client
 * thread and handed to the panel. No RuneLite or Swing types, so the checklist logic can be tested.
 */
@Value
public class CombatTabStatus
{
	public static final CombatTabStatus LOGGED_OUT = new CombatTabStatus(false, false, false, "", Collections.emptyList(),
		0, false, false);

	boolean loggedIn;
	/** The core Bank Tags plugin is turned on. */
	boolean bankTagsOn;
	boolean bankOpen;
	/** The tag from the "Bank tag" setting, standardized (lower case, trimmed). */
	String tag;
	/** The player's tag tabs, in their bank order. */
	List<String> tagTabs;
	/** Items tagged with {@link #tag}. */
	int taggedItems;
	/** The tag's tab has a layout (Enable layout). */
	boolean layoutEnabled;
	/** The plugin changed this tab's layout without a preview and can put it back. */
	boolean canUndo;

	public boolean hasTagTab()
	{
		return !tag.isEmpty() && tagTabs.contains(tag);
	}
}
