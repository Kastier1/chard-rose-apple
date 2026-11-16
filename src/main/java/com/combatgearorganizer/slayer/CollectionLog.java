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
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Remembers which slayer drops are in the player's collection log, per account.
 *
 * <p>RuneLite can't read the collection log directly. It only sees it while the game draws it: each item the player
 * has logged runs a client script with the item and how many they have (items not logged yet don't run it). Opening a
 * single page only shows that page's items, so "everything else is missing" is only known after the player has
 * <em>searched</em> the log, which draws every logged item at once. That search sets a per-account "synced" flag.
 * Before it, items we've seen show as logged and everything else as unknown, never as missing. The "New item added to
 * your collection log" chat message keeps the list up to date afterwards.
 *
 * <p>Items seen in another player's log (the Adventure Log in their house) are ignored, and changes are always saved
 * to the account they were read for, even if the player switches accounts before the save.
 *
 * <p>Only items that are drops in the bundled slayer data are kept, so the saved lists stay small. Call everything on
 * the client thread.
 */
@Slf4j
@Singleton
public class CollectionLog
{
	/** Client script that draws one collection log item. Arguments: [script id, item id, quantity, ...]. */
	static final int DRAW_ITEM_SCRIPT = 4100;
	/**
	 * Saved in its own group so our saves don't look like a change to the plugin's settings (which would redraw the
	 * panel twice per save).
	 */
	static final String GROUP = CombatGearOrganizerConfig.GROUP + "clog";
	static final String OBTAINED_KEY = "obtained";
	static final String SYNCED_KEY = "synced";
	/** Where an earlier version kept the list, read once and then removed. */
	static final String LEGACY_OBTAINED_KEY = "clogObtained";
	static final String LEGACY_ITEMS_KEY = "clogItems";
	private static final String SEPARATOR = "|";
	private static final Pattern NEW_ITEM = Pattern.compile("New item added to your collection log: (.+)");
	private static final Pattern ADVENTURE_LOG_TITLE = Pattern.compile("The Exploits of (.+)");

	public enum Status
	{
		/** Not a collection log item, or not known yet: the player hasn't searched their log. */
		UNKNOWN,
		LOGGED,
		MISSING,
	}

	/** An immutable copy for the panel. */
	@Value
	public static class Snapshot
	{
		public static final Snapshot EMPTY = new Snapshot(Collections.emptySet(), false);

		Set<String> obtained;
		/** Whether the player has searched their log, so anything not in {@link #obtained} is really missing. */
		boolean synced;

		/** @param inLog whether the item is a collection log slot (from the bundled data) */
		public Status status(String item, boolean inLog)
		{
			if (!inLog)
			{
				return Status.UNKNOWN;
			}
			if (obtained.contains(key(item)))
			{
				return Status.LOGGED;
			}
			return synced ? Status.MISSING : Status.UNKNOWN;
		}
	}

	/** Where the lists are saved. One account (RuneScape profile) at a time; tests use an in-memory one. */
	interface Store
	{
		String currentProfile();

		String get(String profile, String group, String key);

		void set(String profile, String group, String key, String value);

		void unset(String profile, String group, String key);
	}

	/** Reads the game's widgets and the local player; replaced in tests. */
	interface Game
	{
		/** Whether the collection log is in search mode right now. */
		boolean isSearching();

		/** The logged-in player's name, or null. */
		String localPlayerName();

		/** The item's name. */
		String itemName(int itemId);

		/** The owner named in an open Adventure Log ("The Exploits of ..."), or null if none is open. */
		String adventureLogOwner();
	}

	private final Store store;
	private final Game game;

	/** Lower-case names of the drops we care about. */
	private Set<String> watched = Collections.emptySet();
	private final Set<String> obtained = new HashSet<>();
	private boolean synced;
	private String loadedProfile;
	private boolean dirty;
	private int version;
	/** Whose Adventure Log the player opened in a house; null when it's their own or none. */
	private String pohOwner;
	private boolean adventureLogOpened;

	@Inject
	CollectionLog(ConfigManager configManager, ItemManager itemManager, Client client)
	{
		this(new Store()
		{
			@Override
			public String currentProfile()
			{
				return configManager.getRSProfileKey();
			}

			@Override
			public String get(String profile, String group, String key)
			{
				return configManager.getConfiguration(group, profile, key);
			}

			@Override
			public void set(String profile, String group, String key, String value)
			{
				configManager.setConfiguration(group, profile, key, value);
			}

			@Override
			public void unset(String profile, String group, String key)
			{
				configManager.unsetConfiguration(group, profile, key);
			}
		}, new Game()
		{
			@Override
			public boolean isSearching()
			{
				Widget searchBar = client.getWidget(InterfaceID.Collection.SEARCHBAR_CONTAINER);
				return searchBar != null && !searchBar.isHidden();
			}

			@Override
			public String localPlayerName()
			{
				Player p = client.getLocalPlayer();
				return p != null ? p.getName() : null;
			}

			@Override
			public String itemName(int itemId)
			{
				return itemManager.getItemComposition(itemId).getName();
			}

			@Override
			public String adventureLogOwner()
			{
				String owner = ownerIn(client.getWidget(InterfaceID.MenuNew.TITLE));
				return owner != null ? owner : ownerIn(client.getWidget(InterfaceID.Menu.LJ_LAYER2));
			}
		});
	}

	CollectionLog(Store store, Game game)
	{
		this.store = store;
		this.game = game;
	}

	public void watch(Collection<String> dropNames)
	{
		Set<String> names = new HashSet<>();
		for (String n : dropNames)
		{
			names.add(key(n));
		}
		watched = names;
	}

	/** Call before reading; saves the previous account's changes and loads this account's lists when it changes. */
	public void ensureLoaded()
	{
		String profile = store.currentProfile();
		if (Objects.equals(profile, loadedProfile))
		{
			return;
		}
		flush();
		loadedProfile = profile;
		obtained.clear();
		synced = false;
		if (profile != null)
		{
			load(profile);
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
		onItemDrawn((Integer) args[1], (Integer) args[2]);
	}

	/** The collection log drew one of the player's logged items. */
	void onItemDrawn(int itemId, int quantity)
	{
		if (!isOwnLog())
		{
			return;
		}
		ensureLoaded();
		if (loadedProfile == null)
		{
			return;
		}
		if (game.isSearching() && !synced)
		{
			// A search draws every logged item, so from now on anything not drawn is really missing
			synced = true;
			dirty = true;
			version++;
		}
		String name = key(game.itemName(itemId));
		if (quantity > 0 && watched.contains(name))
		{
			add(name);
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
			onNewItem(m.group(1));
		}
	}

	/** The game said a new item went into the player's own log. Never marks the log as searched. */
	void onNewItem(String itemName)
	{
		ensureLoaded();
		String name = key(itemName);
		if (loadedProfile != null && watched.contains(name))
		{
			add(name);
		}
	}

	/** An Adventure Log interface opened; who owns it is read on the next tick, once its title is drawn. */
	public void onAdventureLogOpened()
	{
		adventureLogOpened = true;
	}

	public void onGameStateChanged(GameState state)
	{
		if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			// Leaving the house (or the world) ends any visit to someone else's Adventure Log
			pohOwner = null;
		}
	}

	/** Call every tick. Saves changes, at most once a tick. */
	public void onGameTick()
	{
		if (adventureLogOpened)
		{
			adventureLogOpened = false;
			String owner = game.adventureLogOwner();
			if (owner != null)
			{
				pohOwner = owner;
			}
		}
		flush();
	}

	/** Saves changes to the account they were read for. */
	public void flush()
	{
		if (!dirty || loadedProfile == null)
		{
			return;
		}
		dirty = false;
		store.set(loadedProfile, GROUP, OBTAINED_KEY, String.join(SEPARATOR, obtained));
		store.set(loadedProfile, GROUP, SYNCED_KEY, Boolean.toString(synced));
	}

	/** Changes whenever what we know changes, so the panel can tell when to redraw. */
	public int getVersion()
	{
		return version;
	}

	public Snapshot snapshot()
	{
		return new Snapshot(Collections.unmodifiableSet(new HashSet<>(obtained)), synced);
	}

	private boolean isOwnLog()
	{
		if (pohOwner == null)
		{
			return true;
		}
		String me = game.localPlayerName();
		return me != null && Text.standardize(me).equals(Text.standardize(pohOwner));
	}

	private void add(String name)
	{
		if (obtained.add(name))
		{
			dirty = true;
			version++;
		}
	}

	private void load(String profile)
	{
		read(store.get(profile, GROUP, OBTAINED_KEY), obtained);
		synced = Boolean.parseBoolean(store.get(profile, GROUP, SYNCED_KEY));

		// An earlier version kept the list with the plugin's settings and guessed "synced" from it. Keep what it
		// saw as logged, but don't trust its guess: the player searches their log once more.
		String legacy = store.get(profile, CombatGearOrganizerConfig.GROUP, LEGACY_OBTAINED_KEY);
		if (legacy != null)
		{
			read(legacy, obtained);
			store.unset(profile, CombatGearOrganizerConfig.GROUP, LEGACY_OBTAINED_KEY);
			store.unset(profile, CombatGearOrganizerConfig.GROUP, LEGACY_ITEMS_KEY);
			dirty = !obtained.isEmpty();
		}
	}

	private static void read(String saved, Set<String> into)
	{
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

	private static String ownerIn(Widget container)
	{
		if (container == null)
		{
			return null;
		}
		String owner = matchOwner(container.getText());
		if (owner != null || container.getChildren() == null)
		{
			return owner;
		}
		for (Widget child : container.getChildren())
		{
			owner = child != null ? matchOwner(child.getText()) : null;
			if (owner != null)
			{
				return owner;
			}
		}
		return null;
	}

	static String matchOwner(String text)
	{
		if (text == null)
		{
			return null;
		}
		Matcher m = ADVENTURE_LOG_TITLE.matcher(Text.removeTags(text));
		return m.find() ? m.group(1) : null;
	}

	static String key(String itemName)
	{
		return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ROOT);
	}
}
