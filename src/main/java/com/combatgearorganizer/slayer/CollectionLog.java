package com.combatgearorganizer.slayer;

import com.combatgearorganizer.CombatGearOrganizerConfig;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Remembers which slayer drops are in the player's collection log, per account.
 *
 * RuneLite can't read the collection log directly. It only sees it while the game draws it: each item the
 * player has logged runs a client script with the item and how many they have (items not logged yet don't run
 * it). So the player opens their collection log and searches it, and this keeps what it saw. After that the
 * "New item added to your collection log" chat message keeps it up to date. Which drops are log slots at all
 * comes from the bundled data.
 *
 * Only items that are drops in the bundled slayer data are kept, so the saved lists stay small.
 */
@Slf4j
@Singleton
public class CollectionLog
{
	/** Client script that draws one collection log item. Arguments: [script id, item id, quantity, ...]. */
	static final int DRAW_ITEM_SCRIPT = 4100;
	private static final String ITEMS_KEY = "clogItems";
	private static final String OBTAINED_KEY = "clogObtained";
	private static final String SEPARATOR = "|";
	private static final Pattern NEW_ITEM = Pattern.compile("New item added to your collection log: (.+)");

	public enum Status
	{
		/** Not a collection log item, or the log hasn't been opened yet. */
		UNKNOWN,
		LOGGED,
		MISSING,
	}

	/** An immutable copy for the panel. */
	@Value
	public static class Snapshot
	{
		public static final Snapshot EMPTY = new Snapshot(Collections.emptySet(), Collections.emptySet());

		Set<String> items;
		Set<String> obtained;

		public boolean isSynced()
		{
			return !items.isEmpty();
		}

		/** @param inLog whether the item is a collection log slot (from the bundled data) */
		public Status status(String item, boolean inLog)
		{
			if (!inLog || !isSynced())
			{
				return Status.UNKNOWN;
			}
			return obtained.contains(key(item)) ? Status.LOGGED : Status.MISSING;
		}
	}

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	/** Lower-case names of the drops we care about. */
	private Set<String> watched = Collections.emptySet();
	private final Set<String> items = new HashSet<>();
	private final Set<String> obtained = new HashSet<>();
	private String loadedProfile;
	private boolean dirty;
	private int version;

	public void watch(Collection<String> dropNames)
	{
		Set<String> names = new HashSet<>();
		for (String n : dropNames)
		{
			names.add(key(n));
		}
		watched = names;
	}

	/** Call on the client thread before reading; loads the saved lists when the account changes. */
	public void ensureLoaded()
	{
		String profile = configManager.getRSProfileKey();
		if (Objects.equals(profile, loadedProfile))
		{
			return;
		}
		flush();
		loadedProfile = profile;
		items.clear();
		obtained.clear();
		if (profile != null)
		{
			read(ITEMS_KEY, items);
			read(OBTAINED_KEY, obtained);
		}
		version++;
	}

	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != DRAW_ITEM_SCRIPT || event.getScriptEvent() == null)
		{
			return;
		}
		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}
		ensureLoaded();
		String name = key(itemManager.getItemComposition((Integer) args[1]).getName());
		if (watched.contains(name))
		{
			log.debug("Collection log item {}: {}", name, args[2]);
			record(name, (Integer) args[2] > 0);
		}
	}

	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		Matcher m = NEW_ITEM.matcher(Text.removeTags(event.getMessage()));
		if (m.find())
		{
			ensureLoaded();
			String name = key(m.group(1));
			if (watched.contains(name))
			{
				record(name, true);
			}
		}
	}

	/** Saves changes, at most once a tick. Call on the client thread. */
	public void flush()
	{
		if (!dirty || loadedProfile == null)
		{
			return;
		}
		dirty = false;
		configManager.setRSProfileConfiguration(CombatGearOrganizerConfig.GROUP, ITEMS_KEY, String.join(SEPARATOR, items));
		configManager.setRSProfileConfiguration(CombatGearOrganizerConfig.GROUP, OBTAINED_KEY, String.join(SEPARATOR, obtained));
	}

	/** Changes whenever what we know changes, so the panel can tell when to redraw. */
	public int getVersion()
	{
		return version;
	}

	public Snapshot snapshot()
	{
		return new Snapshot(Collections.unmodifiableSet(new HashSet<>(items)), Collections.unmodifiableSet(new HashSet<>(obtained)));
	}

	private void record(String name, boolean have)
	{
		boolean changed = items.add(name);
		changed |= have ? obtained.add(name) : obtained.remove(name);
		if (changed)
		{
			dirty = true;
			version++;
		}
	}

	private void read(String key, Set<String> into)
	{
		String saved = configManager.getRSProfileConfiguration(CombatGearOrganizerConfig.GROUP, key);
		if (saved == null || saved.isEmpty())
		{
			return;
		}
		for (String s : saved.split(Pattern.quote(SEPARATOR)))
		{
			if (!s.isEmpty())
			{
				into.add(s);
			}
		}
	}

	static String key(String itemName)
	{
		return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ROOT);
	}
}
