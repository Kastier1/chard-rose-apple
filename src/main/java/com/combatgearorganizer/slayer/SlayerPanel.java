package com.combatgearorganizer.slayer;

import com.combatgearorganizer.ItemValueMode;
import com.combatgearorganizer.ItemValues;
import com.combatgearorganizer.slayer.SlayerLocationData.Variant;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Slayer section of the side panel: the current slayer task and where to do it. Only touch it on the Swing
 * thread.
 */
@Slf4j
public class SlayerPanel extends JPanel
{
	private static final Color WILDERNESS_COLOR = new Color(230, 80, 60);
	private static final Color RECOMMENDED_COLOR = new Color(110, 200, 90);
	/** Text widths in px, so labels wrap inside the 225px panel. */
	private static final int FULL_TEXT_WIDTH = 165;
	private static final int CARD_TEXT_WIDTH = 135;
	/**
	 * The route line shares its row with the Cancel button. Swing draws an HTML label about 1.3 times wider than its
	 * CSS width, so this keeps "On route to Catacombs of Kourend" wrapping instead of being cut off.
	 */
	private static final int ROUTE_TEXT_WIDTH = 105;
	private static final int INFO_TEXT_WIDTH = 140;
	private static final int PREVIEW_LENGTH = 22;
	private static final int CAPTION_WIDTH = 46;
	private static final Color WEAKNESS_COLOR = new Color(255, 200, 90);
	private static final Color MISSING_TEXT_COLOR = new Color(255, 152, 31);
	private static final Color LAST_TIME_COLOR = new Color(140, 190, 255);

	/** The empty and waiting states: a title and one short sentence each. */
	static final String LOGGED_OUT = "Log in to see your task.";
	static final String NO_TASK_TITLE = "No task";
	static final String NO_TASK = "Get one from any slayer master.";
	static final String NO_SHORTEST_PATH = "Without Shortest Path, Go marks the place on your world map.";
	static final String NO_DATA = "No places for this task in the data yet.";
	static final String NO_DATA_BOSS = "No places for boss tasks in the data yet.";
	static final String NO_WILDERNESS_PLACES = "No Wilderness places for this task in the data yet.";
	static final String ALL_IN_WILDERNESS = "Every place for this task is in the Wilderness; turn on \"Show Wilderness locations\" to see them.";
	static final String LAST_TIME = "Last time";

	private final Consumer<LocationOption> onGo;
	private final Consumer<List<LocationOption>> onGoNearest;
	private final Runnable onCancelRoute;
	private final Consumer<PanelMemory> onMemoryChanged;

	private static final ImageIcon ARROW_CLOSED = arrowIcon(false);
	private static final ImageIcon ARROW_OPEN = arrowIcon(true);
	private static final java.util.regex.Pattern ELEMENT_NOTE = java.util.regex.Pattern.compile(
		"\\b(air|water|earth|fire)\\b[^.;]*\\b(spells?|weak(ness)?|%)|\\b(weak(ness)?|elemental)\\b[^.;]*\\b(air|water|earth|fire)\\b",
		java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern WEAPON_NOTE = java.util.regex.Pattern.compile(
		"\\b(crush|stab|slash|\\w*bane|weapons?)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final Color SUPERIOR_COLOR = new Color(255, 120, 60);
	private static final ImageIcon SUPERIOR_STAR = starIcon();
	/** Item pictures per row: five 36px slots fit the card. */
	private static final int GRID_COLUMNS = 5;

	private final JPanel content = new JPanel();
	private final SlayerIcons icons;
	/** Open drawers, the monster picked per task and the last place per task, for this account. */
	private PanelMemory memory = new PanelMemory();
	/** Only the header, progress, route line and places. */
	private boolean compact;
	private boolean previewing;
	private Runnable onStopPreview;
	/** Carry items the player has with them, for the ticks in the Carry row. */
	private Set<CarryItem> carried = EnumSet.noneOf(CarryItem.class);
	private Route route = Route.NONE;
	/** Holds the route line, so it can change without redrawing the panel. */
	private final JPanel routeSlot = new JPanel(new BorderLayout());

	/** The task shown, so a click or a kill can redraw it. */
	private boolean showingTask;
	private SlayerTaskState shownTask;
	private SlayerLocationData.TaskInfo shownInfo;
	private List<LocationOption> shownLocations = Collections.emptyList();
	private boolean shownHasData;
	private boolean shownShortestPath;
	private TaskProgress.View shownProgress;

	/** The variant the player is looking at, by name, and the task (see {@link PanelMemory#taskKey}) it belongs to. */
	private String selectedVariant;
	private String variantTaskKey;
	private List<Variant> currentVariants = Collections.emptyList();
	private CollectionLog.Snapshot currentClog = CollectionLog.Snapshot.EMPTY;
	private DropItems currentItems = DropItems.EMPTY;

	/** What a kill changes, kept so a kill can update them without redrawing the panel. */
	private JLabel countLabel;
	private final JPanel progressBox = new JPanel();
	private final List<VariantTotal> variantTotals = new ArrayList<>();
	private JLabel xpChip;
	private Variant xpChipVariant;

	/** A "Task" cell of the variant table: XP for the kills left. */
	@Value
	private static class VariantTotal
	{
		JLabel label;
		Variant variant;
	}

	public SlayerPanel(SlayerIcons icons, Consumer<LocationOption> onGo, Consumer<List<LocationOption>> onGoNearest,
		Runnable onCancelRoute)
	{
		this(icons, onGo, onGoNearest, onCancelRoute, m -> { });
	}

	/**
	 * @param onCancelRoute   the player pressed Cancel (or Remove) on the route line
	 * @param onMemoryChanged the player opened or closed a drawer, picked a monster or pressed Go; called on the
	 *                        Swing thread with the memory to save
	 */
	public SlayerPanel(SlayerIcons icons, Consumer<LocationOption> onGo, Consumer<List<LocationOption>> onGoNearest,
		Runnable onCancelRoute, Consumer<PanelMemory> onMemoryChanged)
	{
		this.icons = icons;
		this.onGo = onGo;
		this.onGoNearest = onGoNearest;
		this.onCancelRoute = onCancelRoute;
		this.onMemoryChanged = onMemoryChanged;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.NORTH);
		progressBox.setLayout(new BoxLayout(progressBox, BoxLayout.Y_AXIS));
		progressBox.setOpaque(false);
		progressBox.setAlignmentX(LEFT_ALIGNMENT);
		routeSlot.setOpaque(false);
		routeSlot.setAlignmentX(LEFT_ALIGNMENT);
		routeSlot.setBorder(new EmptyBorder(0, 0, 8, 0));
		showLoggedOut();
	}

	/** Use this account's memory (open drawers, picked monsters, last places) from now on. */
	public void setMemory(PanelMemory memory)
	{
		this.memory = memory != null ? memory : new PanelMemory();
		// Read the picked monster for the shown task again
		variantTaskKey = null;
		redraw();
	}

	PanelMemory getMemory()
	{
		return memory;
	}

	/** Takes effect on the next {@link #showTask}. */
	public void setCompact(boolean compact)
	{
		this.compact = compact;
	}

	/**
	 * Whether the task shown is a preview the player asked for rather than their real task. A preview gets a banner
	 * with a Stop button, and doesn't change what the panel remembers about real tasks.
	 */
	public void setPreview(boolean previewing, Runnable onStop)
	{
		this.previewing = previewing;
		this.onStopPreview = onStop;
	}

	boolean isPreviewing()
	{
		return previewing;
	}

	private JPanel previewBanner()
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 4));
		row.add(wrapped("Preview, not your current task. Kills and progress aren't counted.", ColorScheme.BRAND_ORANGE,
			FontManager.getRunescapeSmallFont(), ROUTE_TEXT_WIDTH), BorderLayout.CENTER);
		JButton stop = new JButton("Stop");
		stop.setToolTipText("Go back to your current task");
		stop.addActionListener(e ->
		{
			if (onStopPreview != null)
			{
				onStopPreview.run();
			}
		});
		row.add(stop, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** Shows, changes or hides the route line, without redrawing the rest of the panel. */
	public void showRoute(Route route)
	{
		this.route = route != null ? route : Route.NONE;
		fillRoute();
		refresh();
	}

	Route getRoute()
	{
		return route;
	}

	/** Not logged in, so no task to read. */
	public void showLoggedOut()
	{
		showMessage("Slayer task", null, LOGGED_OUT);
	}

	public void showNoTask()
	{
		showNoTask(null);
	}

	/** No task; {@code summary} is how the last task went, or null. */
	public void showNoTask(String summary)
	{
		showMessage(NO_TASK_TITLE, summary != null ? summary + "." : null, NO_TASK);
	}

	private void showMessage(String heading, String summary, String text)
	{
		showingTask = false;
		content.removeAll();
		content.add(title(heading));
		content.add(Box.createVerticalStrut(8));
		if (summary != null)
		{
			content.add(wrapped(summary, RECOMMENDED_COLOR));
			content.add(Box.createVerticalStrut(6));
		}
		content.add(notice(text, ColorScheme.LIGHT_GRAY_COLOR));
		refresh();
	}

	/** One short sentence for an empty or waiting state, the same way everywhere. */
	private static JLabel notice(String text, Color color)
	{
		JLabel label = wrapped(text, color);
		label.setBorder(new EmptyBorder(0, 0, 6, 0));
		return label;
	}

	public void showTask(SlayerTaskState task, SlayerLocationData.TaskInfo info, List<Variant> variants,
		List<LocationOption> locations, boolean hasData, boolean shortestPathAvailable, CollectionLog.Snapshot clog,
		DropItems items)
	{
		showTask(task, info, variants, locations, hasData, shortestPathAvailable, clog, items, null);
	}

	/** As above, with how the task is going under the kill count; {@code progress} may be null. */
	public void showTask(SlayerTaskState task, SlayerLocationData.TaskInfo info, List<Variant> variants,
		List<LocationOption> locations, boolean hasData, boolean shortestPathAvailable, CollectionLog.Snapshot clog,
		DropItems items, TaskProgress.View progress)
	{
		showingTask = true;
		shownTask = task;
		shownInfo = info;
		currentVariants = variants;
		shownLocations = locations;
		shownHasData = hasData;
		shownShortestPath = shortestPathAvailable;
		currentClog = clog;
		currentItems = items != null ? items : DropItems.EMPTY;
		shownProgress = progress;
		drawTask();
	}

	/**
	 * A kill or new progress on the task already shown: updates the count, the progress and the XP for the kills left,
	 * without redrawing the rest of the panel. Ignored if it is another task.
	 */
	public void updateCount(SlayerTaskState task, TaskProgress.View progress)
	{
		if (!showingTask || !task.sameTaskAs(shownTask))
		{
			log.debug("Count update for a task that isn't shown: {}", task);
			return;
		}
		shownTask = task;
		shownProgress = progress;
		int left = task.getRemaining();
		countLabel.setText(countText(task));
		fillProgress();
		for (VariantTotal t : variantTotals)
		{
			t.getLabel().setText(Variants.xpTotalShort(t.getVariant().getXp(), left));
		}
		if (xpChip != null)
		{
			xpChip.setText(chipHtml(xpHtml(xpChipVariant, left)));
		}
		refresh();
	}

	private static String countText(SlayerTaskState task)
	{
		return task.getInitial() > 0
			? task.getRemaining() + " of " + task.getInitial() + " left"
			: task.getRemaining() + " left";
	}

	private void drawTask()
	{
		SlayerTaskState task = shownTask;
		SlayerLocationData.TaskInfo info = shownInfo;
		List<Variant> variants = currentVariants;
		List<LocationOption> locations = shownLocations;
		boolean hasData = shownHasData;
		boolean shortestPathAvailable = shownShortestPath;

		// Keep the player's chosen variant for this task, even across relogs; pick a sensible one otherwise
		String key = PanelMemory.taskKey(task.getTaskId(), task.getName());
		if (!key.equals(variantTaskKey))
		{
			variantTaskKey = key;
			selectedVariant = memory.monster(key);
		}
		Variant selected = Variants.byName(Variants.listed(variants, locations), selectedVariant);
		if (selected == null)
		{
			selected = Variants.pickDefault(variants, locations);
		}
		selectedVariant = selected == null ? null : selected.getName();

		content.removeAll();
		variantTotals.clear();
		xpChip = null;
		xpChipVariant = null;
		content.add(title(task.getName()));
		if (previewing)
		{
			content.add(previewBanner());
		}

		countLabel = line(countText(task), ColorScheme.LIGHT_GRAY_COLOR);
		content.add(countLabel);
		fillProgress();
		content.add(progressBox);

		String master = SlayerMasters.name(task.getMasterId());
		if (task.getKonarArea() != null)
		{
			content.add(line("Konar: " + task.getKonarArea(), ColorScheme.BRAND_ORANGE));
		}
		else if (master != null)
		{
			content.add(line("From " + master, ColorScheme.LIGHT_GRAY_COLOR));
		}
		content.add(Box.createVerticalStrut(8));
		fillRoute();
		content.add(routeSlot);

		boolean variantWeaknesses = Variants.regular(variants).stream().anyMatch(v -> v.getWeakness() != null);
		if (!compact && (info != null || selected != null))
		{
			try
			{
				content.add(taskCard(info, !variantWeaknesses, variants, selected, locations, task.getRemaining()));
				content.add(Box.createVerticalStrut(8));
			}
			catch (RuntimeException e)
			{
				// One bad record must not take the location list and Go buttons down with it
				log.warn("Couldn't show the details for {} ({})", task.getName(), selectedVariant, e);
				content.add(wrapped("Couldn't show the details for this monster.", ColorScheme.LIGHT_GRAY_COLOR));
				content.add(Box.createVerticalStrut(8));
			}
		}

		if (!hasData)
		{
			content.add(notice(task.getTaskId() == 98 ? NO_DATA_BOSS : NO_DATA, ColorScheme.LIGHT_GRAY_COLOR));
			refresh();
			return;
		}

		if (!shortestPathAvailable)
		{
			JLabel sp = notice(NO_SHORTEST_PATH, ColorScheme.BRAND_ORANGE);
			sp.setToolTipText("Install or turn on the Shortest Path plugin from the Plugin Hub to get directions");
			content.add(sp);
		}

		// "Nearest" leaves out places the player can't get into yet, and the wiki-spawn places while a guide place will do
		List<LocationOption> routable = LocationPicker.nearestTargets(locations);

		JPanel actions = new JPanel(new BorderLayout());
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton nearest = new JButton("Go to nearest");
		nearest.setEnabled(routable.size() > 0);
		nearest.setToolTipText("Path to whichever of these places is quickest to reach");
		nearest.addActionListener(e -> onGoNearest.accept(routable));
		actions.add(nearest, BorderLayout.CENTER);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		actions.setAlignmentX(LEFT_ALIGNMENT);
		content.add(actions);
		content.add(Box.createVerticalStrut(8));

		if (locations.isEmpty())
		{
			content.add(notice(task.getMasterId() == SlayerMasters.KRYSTILIA ? NO_WILDERNESS_PLACES : ALL_IN_WILDERNESS,
				ColorScheme.LIGHT_GRAY_COLOR));
		}

		String lastPlace = memory.lastPlace(key);
		for (LocationOption location : locations)
		{
			content.add(card(location, lastPlace != null && lastPlace.equals(location.getKey())));
			content.add(Box.createVerticalStrut(4));
		}
		refresh();
	}

	/** The route line: where Go is taking the player, with a button to stop. Hidden when there is no route. */
	private void fillRoute()
	{
		routeSlot.removeAll();
		routeSlot.setVisible(route.isShown());
		if (!route.isShown())
		{
			return;
		}
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(5, 8, 5, 6));
		row.add(wrapped(route.text(), RECOMMENDED_COLOR, FontManager.getRunescapeSmallFont(), ROUTE_TEXT_WIDTH),
			BorderLayout.CENTER);
		JButton stop = new JButton(route.buttonText());
		stop.setMargin(new java.awt.Insets(0, 4, 0, 4));
		stop.setToolTipText(route.buttonTooltip());
		stop.addActionListener(e -> onCancelRoute.run());
		JPanel stopWrap = new JPanel(new BorderLayout());
		stopWrap.setOpaque(false);
		stopWrap.add(stop, BorderLayout.NORTH);
		row.add(stopWrap, BorderLayout.EAST);
		routeSlot.add(row, BorderLayout.CENTER);
		routeSlot.setMaximumSize(new Dimension(Integer.MAX_VALUE, routeSlot.getPreferredSize().height));
	}

	/** The progress lines under the kill count; only the kill rate line in compact mode. */
	private void fillProgress()
	{
		progressBox.removeAll();
		if (shownProgress != null)
		{
			addProgress(progressBox, shownProgress, compact);
		}
	}

	/** Kill rate, time left, XP, superiors and loot so far, under the kill count. */
	private static void addProgress(JPanel content, TaskProgress.View progress, boolean rateOnly)
	{
		String rate = progress.rateText();
		content.add(wrapped(rate != null ? rate : "Kill rate shows after your next kill",
			rate != null ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR));
		if (rateOnly)
		{
			return;
		}
		if (progress.getXp() > 0)
		{
			content.add(line(String.format(Locale.ENGLISH, "%,d slayer XP this task", progress.getXp()), ColorScheme.LIGHT_GRAY_COLOR));
		}
		if (progress.getSuperiors() > 0)
		{
			content.add(line(progress.getSuperiors() + (progress.getSuperiors() == 1 ? " superior" : " superiors") + " this task",
				SUPERIOR_COLOR));
		}
		if (progress.getLoot() > 0)
		{
			ItemValueMode mode = progress.getMode() != null ? progress.getMode() : ItemValueMode.GRAND_EXCHANGE;
			JLabel loot = line("Loot: " + ItemValues.format(progress.getLoot()) + " (" + mode.getLabel() + ")",
				ColorScheme.LIGHT_GRAY_COLOR);
			loot.setToolTipText("What task monsters dropped for you this task. Change \"Item value\" in the plugin settings to switch between GE and high alch.");
			content.add(loot);
		}
		if (progress.getTrackedFrom() >= 0)
		{
			content.add(line("Counted from " + progress.getTrackedFrom() + " left", ColorScheme.MEDIUM_GRAY_COLOR));
		}
	}

	/**
	 * Everything about the task in one card: the monster table, how to fight it (task-wide), the selected monster's
	 * facts, the longer notes as drawers, then superiors and bosses. Either half may be missing.
	 */
	private JPanel taskCard(SlayerLocationData.TaskInfo info, boolean showTaskWeakness, List<Variant> variants,
		Variant selected, List<LocationOption> locations, int left)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));

		if (selected != null)
		{
			addVariantTable(card, variants, selected, locations, left);
		}
		if (info != null)
		{
			card.add(chips(info, TaskTags.from(info), showTaskWeakness, showsPrayer(info, selected)));
		}
		if (selected != null)
		{
			addVariantDetails(card, selected, locations, left);
		}
		if (info != null)
		{
			// With a monster selected, its own drops replace the task-wide drop notes
			addInfoDrawers(card, info, selected);
		}
		if (selected != null)
		{
			addVariantExtras(card, variants, locations);
		}
		return card;
	}

	/** The task's monsters as a clickable table: level, XP each, XP for the kills left. */
	private void addVariantTable(JPanel card, List<Variant> variants, Variant selected, List<LocationOption> locations, int left)
	{
		card.add(variantRow("Monster", null, "Lvl", "XP", "Task", ColorScheme.MEDIUM_GRAY_COLOR, false, null, null));
		for (Variant v : Variants.listed(variants, locations))
		{
			boolean on = v == selected;
			card.add(variantRow(v.getName(), superiorTip(variants, v), Variants.range(v.getLevel()), Variants.xpEach(v.getXp()),
				Variants.xpTotalShort(v.getXp(), left), on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR, on,
				on ? null : () -> pickVariant(v.getName()),
				totalCell -> variantTotals.add(new VariantTotal(totalCell, v))));
		}
		card.add(Box.createVerticalStrut(6));
	}

	/** The selected monster's own facts, under the task-wide attack and prayer rows. */
	private void addVariantDetails(JPanel card, Variant selected, List<LocationOption> locations, int left)
	{
		String hp = selected.getHitpoints() != null ? " · " + Variants.range(selected.getHitpoints()) + " HP" : "";
		card.add(factRow("Level", textChip(Variants.range(selected.getLevel()) + hp, Color.WHITE)));

		if (!selected.getAttackTypes().isEmpty())
		{
			JPanel hits = iconRow();
			String tip = "Attacks with " + String.join(", ", selected.getAttackTypes());
			int shown = 0;
			for (String type : selected.getAttackTypes())
			{
				TaskTags.Style style = TaskTags.styleOf(type);
				if (style == null)
				{
					// Data we don't have an icon for; skip it rather than break the panel
					continue;
				}
				if (shown++ > 0)
				{
					hits.add(chipText("+", ColorScheme.MEDIUM_GRAY_COLOR, tip));
				}
				JLabel icon = chipIcon(tip);
				icons.setStyle(icon, style);
				hits.add(icon);
			}
			if (shown > 0)
			{
				card.add(factRow("Hits with", hits));
			}
		}

		TaskTags.Element element = selected.getWeakness() != null ? TaskTags.elementOf(selected.getWeakness().getElement()) : null;
		if (element != null)
		{
			JPanel weak = iconRow();
			String tip = "Weak to " + selected.getWeakness().getElement() + " spells";
			JLabel icon = chipIcon(tip);
			icons.setElement(icon, element);
			weak.add(icon);
			if (selected.getWeakness().getPercent() != null)
			{
				weak.add(chipText(selected.getWeakness().getPercent() + "%", WEAKNESS_COLOR, tip));
			}
			card.add(factRow("Weak to", weak));
		}

		xpChip = textChip(xpHtml(selected, left), ColorScheme.LIGHT_GRAY_COLOR);
		xpChipVariant = selected;
		card.add(factRow("XP", xpChip));

		List<Variant> superiors = Variants.superiorsOf(currentVariants, selected);
		if (!superiors.isEmpty())
		{
			JPanel sup = iconRow();
			List<String> parts = new ArrayList<>();
			for (Variant s : superiors)
			{
				parts.add(escape(s.getName()) + " <font color='#ffc85a'>" + Variants.xpEach(s.getXp()) + " XP</font>");
			}
			JLabel star = new JLabel(SUPERIOR_STAR);
			star.setToolTipText("Can spawn while on task with the Bigger and Badder unlock");
			sup.add(star);
			sup.add(Box.createHorizontalStrut(4));
			sup.add(textChip(String.join(", ", parts), ColorScheme.LIGHT_GRAY_COLOR));
			card.add(factRow("Superior", sup));
			for (Variant s : superiors)
			{
				addNotableDrops(card, currentVariants, s);
			}
		}

		addDrops(card, selected);
		addCarry(card, selected, locations);

		List<String> found = Variants.foundAt(selected, locations);
		boolean placed = Variants.isAtAny(selected, locations);
		List<String> where = new ArrayList<>();
		for (String label : found)
		{
			where.add(escape(label));
		}
		card.add(factRow("Found at", textChip(String.join(", ", where),
			placed ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR)));
	}

	/**
	 * Protection, prayer and storage items worth an inventory slot for the selected monster (dragonfire protection,
	 * bonecrusher, herb sack...), ticked
	 * when the player is already carrying them. Hover one for why.
	 */
	private void addCarry(JPanel card, Variant selected, List<LocationOption> locations)
	{
		boolean wildernessOnly = !locations.isEmpty() && locations.stream().allMatch(LocationOption::isWilderness);
		List<CarryItem.Suggestion> suggestions = CarryItem.suggest(selected.getCarry(), wildernessOnly);
		if (suggestions.isEmpty())
		{
			return;
		}
		List<JComponent> slots = new ArrayList<>();
		int missing = 0;
		for (CarryItem.Suggestion s : suggestions)
		{
			boolean have = carried.contains(s.getItem());
			missing += have ? 0 : 1;
			String tip = "<html>" + escape(s.getTooltip()).replace("\n", "<br>") + "<br>" + (have ? "You have one with you" : "Not in your inventory or worn") + "</html>";
			CarrySlot slot = new CarrySlot(s.getItem(), have, tip);
			icons.setItem(slot, s.getItem().getIconId(), 1);
			slots.add(slot);
		}
		card.add(factRow("Carry", textChip(missing == 0 ? "All with you" : missing + " worth bringing",
			missing == 0 ? RECOMMENDED_COLOR : ColorScheme.LIGHT_GRAY_COLOR)));
		card.add(dropGrid(slots));
	}

	/** The carry items the player has in their inventory or worn. Redraws the monster card when that changes. */
	public void updateCarried(Set<CarryItem> now)
	{
		if (now.equals(carried))
		{
			return;
		}
		carried = EnumSet.copyOf(now.isEmpty() ? EnumSet.noneOf(CarryItem.class) : now);
		if (showingTask && !compact)
		{
			redraw();
		}
	}

	/**
	 * The selected monster's rare drops, rarest first, then what it always drops, as a grid of item pictures. Hover
	 * one for its rate, collection log status and value. Drops that couldn't be matched to an item are listed as text.
	 */
	private void addDrops(JPanel card, Variant selected)
	{
		if (selected.getDrops().isEmpty() && selected.getAlwaysDrops().isEmpty())
		{
			return;
		}
		List<JComponent> slots = new ArrayList<>();
		StringBuilder html = new StringBuilder();
		int missing = 0;
		boolean anyClog = false;
		for (SlayerLocationData.Drop d : selected.getDrops())
		{
			CollectionLog.Status status = currentClog.status(d.getItem(), d.isClog());
			anyClog |= d.isClog();
			if (status == CollectionLog.Status.MISSING)
			{
				missing++;
			}
			DropItems.Item item = currentItems.get(d.getItem());
			if (item != null)
			{
				slots.add(dropSlot(item, d.getQuantity(), status, dropTooltip(d, null, status, item, currentItems.getMode(),
					currentClog.isSynced())));
				continue;
			}
			// No item to show: the old text line, with the log status spelled out as well as coloured
			String color = status == CollectionLog.Status.MISSING ? "#ff981f"
				: status == CollectionLog.Status.LOGGED ? "#9a9a9a" : "#c8c8c8";
			String rate = d.getOnTask() != null ? d.getOnTask() + " on task" : d.getRarity();
			html.append("<font color='").append(color).append("'>").append(escape(d.getItem())).append("</font> ")
				.append("<font color='#8a8a8a'>").append(escape(rate))
				.append(status == CollectionLog.Status.MISSING ? ", not logged" : "").append("</font><br>");
		}
		List<String> alwaysText = new ArrayList<>();
		for (String always : selected.getAlwaysDrops())
		{
			DropItems.Item item = currentItems.get(always);
			if (item != null)
			{
				slots.add(dropSlot(item, null, CollectionLog.Status.UNKNOWN,
					alwaysTooltip(always, item, currentItems.getMode())));
			}
			else
			{
				alwaysText.add(always);
			}
		}
		if (!alwaysText.isEmpty())
		{
			html.append("<font color='#8a8a8a'>Always: ").append(escape(String.join(", ", alwaysText))).append("</font>");
		}

		String summary = slots.isEmpty() ? null
			: currentClog.isSynced() && anyClog
				? (missing == 0 ? "All logged" : missing + " not logged yet")
				: "Hover for rate and value";
		if (summary != null)
		{
			JLabel head = textChip(escape(summary), missing > 0 ? MISSING_TEXT_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			head.setToolTipText(missing > 0 ? "Faded with a dashed outline: not in your collection log yet" : null);
			card.add(factRow("Drops", head));
			card.add(dropGrid(slots));
		}
		if (html.length() > 0)
		{
			JLabel drops = textChip(html.toString(), ColorScheme.LIGHT_GRAY_COLOR);
			drops.setToolTipText(!currentClog.isSynced()
				? "Open your collection log and search it to highlight drops you haven't logged"
				: missing > 0 ? "Orange: not in your collection log yet" : null);
			card.add(factRow(summary == null ? "Drops" : "", drops));
		}

		if (!currentClog.isSynced() && anyClog)
		{
			card.add(factRow("", textChip("Open your collection log and search it once to mark drops you're missing.",
				ColorScheme.MEDIUM_GRAY_COLOR)));
		}
	}

	/** A superior's or boss's notable drops as a row of item pictures, when any of them match an item. */
	private void addNotableDrops(JPanel card, List<Variant> variants, Variant v)
	{
		List<JComponent> slots = new ArrayList<>();
		for (SlayerLocationData.Drop d : Variants.notableDrops(variants, v))
		{
			DropItems.Item item = currentItems.get(d.getItem());
			if (item == null)
			{
				continue;
			}
			CollectionLog.Status status = currentClog.status(d.getItem(), d.isClog());
			slots.add(dropSlot(item, d.getQuantity(), status, dropTooltip(d, v.getName(), status, item,
				currentItems.getMode(), currentClog.isSynced())));
			if (slots.size() == GRID_COLUMNS)
			{
				break;
			}
		}
		if (!slots.isEmpty())
		{
			card.add(dropGrid(slots));
		}
	}

	private DropSlot dropSlot(DropItems.Item item, int[] quantity, CollectionLog.Status status, String tooltip)
	{
		DropSlot slot = new DropSlot(status, tooltip);
		int stack = quantity != null && quantity.length > 0 ? quantity[0] : 1;
		icons.setItem(slot, item.getItemId(), stack);
		return slot;
	}

	private static JPanel dropGrid(List<JComponent> slots)
	{
		JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 0, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		grid.setBorder(new EmptyBorder(2, 0, 2, 0));
		slots.forEach(grid::add);
		// Keep empty cells after a short last row: the grid is always five pictures wide
		for (int i = slots.size(); i < GRID_COLUMNS; i++)
		{
			grid.add(Box.createRigidArea(new Dimension(DropSlot.WIDTH, DropSlot.HEIGHT)));
		}
		grid.setMaximumSize(grid.getPreferredSize());
		return grid;
	}

	/**
	 * A drop's tooltip: its name, where it comes from (for a superior or boss), the drop rate, the better rate on
	 * task, the stack size, the collection log status and the value.
	 */
	static String dropTooltip(SlayerLocationData.Drop d, String from, CollectionLog.Status status, DropItems.Item item,
		ItemValueMode mode, boolean clogSynced)
	{
		StringBuilder tip = new StringBuilder("<html><b>").append(escape(d.getItem())).append("</b>");
		if (from != null)
		{
			tip.append("<br>From ").append(escape(from));
		}
		tip.append("<br>Drop rate: ").append(escape(nullToEmpty(d.getRarity())));
		if (d.getOnTask() != null && !d.getOnTask().equals(d.getRarity()))
		{
			tip.append("<br>On task: ").append(escape(d.getOnTask()));
		}
		String quantity = DropItems.quantityText(d.getQuantity());
		if (quantity != null)
		{
			tip.append("<br>Stack: ").append(quantity);
		}
		if (d.isClog())
		{
			tip.append("<br>").append(status == CollectionLog.Status.LOGGED ? "In your collection log"
				: status == CollectionLog.Status.MISSING ? "Not in your collection log yet"
				: clogSynced ? "Collection log item" : "Collection log item. Search your log to check it.");
		}
		tip.append("<br>").append(DropItems.valueText(mode, item, d.getQuantity()));
		return tip.append("</html>").toString();
	}

	static String alwaysTooltip(String name, DropItems.Item item, ItemValueMode mode)
	{
		return "<html><b>" + escape(name) + "</b><br>Always dropped<br>" + DropItems.valueText(mode, item, null) + "</html>";
	}

	/** "Can spawn a superior: Shadow Wyrm (3,520 XP)", or null when it has none. */
	private static String superiorTip(List<Variant> variants, Variant v)
	{
		List<String> names = new ArrayList<>();
		for (Variant s : Variants.superiorsOf(variants, v))
		{
			names.add(s.getName() + " (" + Variants.xpEach(s.getXp()) + " XP)");
		}
		return names.isEmpty() ? null
			: "Can spawn a superior: " + String.join(", ", names) + ". Needs the Bigger and Badder unlock.";
	}

	/** Superiors and bosses, which aren't in the table. */
	private void addVariantExtras(JPanel card, List<Variant> variants, List<LocationOption> locations)
	{
		// Every regular monster is in the table now, so superiors already shown with their monster aren't repeated
		List<Variant> listed = Variants.listed(variants, locations);
		addSpecials(card, "Superior", variants, s -> s.isSuperior()
			&& listed.stream().noneMatch(v -> Variants.superiorsOf(variants, v).contains(s)));
		addSpecials(card, "Boss", variants, Variant::isBoss);
	}

	/** "Superior: Shadow Wyrm 3,520 XP", one row per monster, each with its notable drops as pictures. */
	private void addSpecials(JPanel card, String caption, List<Variant> variants, Predicate<Variant> which)
	{
		boolean first = true;
		for (Variant v : variants)
		{
			if (!which.test(v))
			{
				continue;
			}
			card.add(factRow(first ? caption : "", textChip(escape(v.getName()) + " <font color='#ffc85a'>"
				+ Variants.xpEach(v.getXp()) + " XP</font>", ColorScheme.LIGHT_GRAY_COLOR)));
			addNotableDrops(card, variants, v);
			first = false;
		}
	}

	/** One line of the variant table. Clicking it runs onClick; null for the header and the selected row. */
	private static JPanel variantRow(String name, String superiorTip, String level, String xp, String total, Color color,
		boolean selected, Runnable onClick, Consumer<JLabel> totalCell)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(selected);
		row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(2, 3, 2, 3));
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 0;

		c.weightx = 1;
		JLabel nameCell = cell(name, color, 0, SwingConstants.LEFT);
		if (superiorTip != null)
		{
			// Star after the name; the tooltip says which superior
			nameCell.setIcon(SUPERIOR_STAR);
			nameCell.setHorizontalTextPosition(SwingConstants.LEFT);
			nameCell.setIconTextGap(3);
		}
		// Let the name give up space (and clip) rather than squeezing the number columns
		nameCell.setPreferredSize(new Dimension(10, 16));
		nameCell.setMinimumSize(new Dimension(0, 16));
		row.add(nameCell, c);
		c.weightx = 0;
		row.add(cell(level, color, 34, SwingConstants.RIGHT), c);
		row.add(cell(xp, color, 38, SwingConstants.RIGHT), c);
		JLabel totalLabel = cell(total, color, 40, SwingConstants.RIGHT);
		row.add(totalLabel, c);
		if (totalCell != null)
		{
			totalCell.accept(totalLabel);
		}

		if (onClick != null)
		{
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.setToolTipText(superiorTip != null ? superiorTip : "Show " + name);
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					row.setOpaque(true);
					row.repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setOpaque(false);
					row.repaint();
				}
			});
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static JLabel cell(String text, Color color, int width, int align)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setHorizontalAlignment(align);
		if (width > 0)
		{
			label.setPreferredSize(new Dimension(width, 16));
			label.setMinimumSize(new Dimension(width, 16));
		}
		return label;
	}

	/** "1,234 each · 61,700 for 50 left" */
	private static String xpHtml(Variant v, int left)
	{
		return "<font color='#ffc85a'>" + Variants.xpEach(v.getXp()) + "</font> each · "
			+ "<font color='#ffc85a'>" + Variants.xpTotal(v.getXp(), left) + "</font> for " + left + " left";
	}

	private static String chipHtml(String html)
	{
		return "<html><body style='width:" + (INFO_TEXT_WIDTH - CAPTION_WIDTH) + "px'>" + html + "</body></html>";
	}

	/** Wrapping HTML text for the right-hand side of a fact row. */
	private static JLabel textChip(String html, Color color)
	{
		JLabel label = new JLabel(chipHtml(html));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setBorder(new EmptyBorder(3, 0, 0, 0));
		return label;
	}

	/** Drawers for the task's longer notes. */
	private void addInfoDrawers(JPanel card, SlayerLocationData.TaskInfo info, Variant selected)
	{
		boolean showDrops = selected == null;
		String weakness = info.getWeakness();
		if (selected != null)
		{
			// The selected monster's own "Weak to" row covers elements (no row: the wiki gives it none). The
			// task-wide notes can name another variant's element (Earth for wyrms while looking at lava strykewyrms)
			weakness = withoutElementNotes(weakness);
		}
		else if (notBlank(info.getElementalWeakness()) && notBlank(weakness))
		{
			weakness = info.getElementalWeakness() + ". " + weakness;
		}
		addDrawer(card, "Weakness", weakness);
		if (showsPrayer(info, selected) && TaskTags.from(info).isPrayerNeedsDetail())
		{
			addDrawer(card, "Prayer", info.getProtectionPrayer());
		}
		addDrawer(card, "They attack with", info.getMonsterAttackStyle());
		addDrawer(card, "Bring", info.getRequiredItems());
		if (showDrops)
		{
			addDrawer(card, "Drops", info.getNotableDrops());
		}
	}

	/** Labelled rows: what to attack with, what to pray, and elemental weaknesses (one row per variant group). */
	/**
	 * Whether to give protection prayer advice. Never for a boss: the plugin hub's guidelines rule out prayer
	 * recommendations for bosses, slayer bosses included. Also not when the data has no advice for the task.
	 */
	static boolean showsPrayer(SlayerLocationData.TaskInfo info, Variant selected)
	{
		return info != null && notBlank(info.getProtectionPrayer()) && (selected == null || !selected.isBoss());
	}

	private void addPrayerRow(JPanel rows, SlayerLocationData.TaskInfo info, TaskTags tags)
	{
		String prayerTip = "Pray: " + nullToEmpty(info.getProtectionPrayer());
		JPanel pray = iconRow();
		if (tags.getPrayers().isEmpty())
		{
			pray.add(chipText("No protection prayer", ColorScheme.LIGHT_GRAY_COLOR, prayerTip));
		}
		for (int i = 0; i < tags.getPrayers().size(); i++)
		{
			if (i > 0)
			{
				pray.add(chipText("/", ColorScheme.MEDIUM_GRAY_COLOR, prayerTip));
			}
			JLabel icon = chipIcon(prayerTip);
			icons.setPrayer(icon, tags.getPrayers().get(i));
			pray.add(icon);
		}
		if (tags.getPrayers().size() > 1)
		{
			pray.add(chipText("varies", WEAKNESS_COLOR, prayerTip));
		}
		else if (tags.isPrayerOptional())
		{
			pray.add(chipText("if needed", ColorScheme.LIGHT_GRAY_COLOR, prayerTip));
		}
		rows.add(factRow("Pray", pray));
	}

	private JPanel chips(SlayerLocationData.TaskInfo info, TaskTags tags, boolean showWeakness, boolean showPrayer)
	{
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);
		rows.setAlignmentX(LEFT_ALIGNMENT);

		// Attack with: skill icons, best style first
		String styleTip = "Attack with: " + nullToEmpty(info.getStyle());
		JPanel attack = iconRow();
		if (tags.getStyles().isEmpty())
		{
			attack.add(chipText("Any style", Color.WHITE, styleTip));
		}
		for (int i = 0; i < tags.getStyles().size(); i++)
		{
			if (i > 0)
			{
				attack.add(chipText("or", ColorScheme.MEDIUM_GRAY_COLOR, styleTip));
			}
			JLabel icon = chipIcon(styleTip);
			icons.setStyle(icon, tags.getStyles().get(i));
			attack.add(icon);
		}
		if (tags.getStyleNote() != null)
		{
			attack.add(chipText(tags.getStyleNote(), ColorScheme.LIGHT_GRAY_COLOR, styleTip));
		}
		rows.add(factRow("Attack", attack));

		// Pray: protection prayer icons; several means it depends on the variant
		if (showPrayer)
		{
			addPrayerRow(rows, info, tags);
		}

		// Weak to: one row per element, naming the variants when they differ
		boolean first = true;
		// Per-variant weaknesses are shown with the variants instead
		for (TaskTags.Weakness w : showWeakness ? tags.getWeaknesses() : Collections.<TaskTags.Weakness>emptyList())
		{
			String tip = "Weak to " + info.getElementalWeakness();
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(false);
			JLabel icon = chipIcon(tip);
			icons.setElement(icon, w.getElement());
			row.add(icon, BorderLayout.WEST);
			String text = (w.getAmount() != null ? w.getAmount() : "")
				+ (w.getVariants() != null ? " <font color='#a0a0a0'>" + escape(w.getVariants()) + "</font>" : "");
			JLabel label = new JLabel("<html><body style='width:" + (INFO_TEXT_WIDTH - 70) + "px'>"
				+ "<font color='#ffc85a'>" + text + "</font></body></html>");
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setToolTipText(tip);
			row.add(label, BorderLayout.CENTER);
			rows.add(factRow(first ? "Weak to" : "", row));
			first = false;
		}
		return rows;
	}

	private static JPanel iconRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		return row;
	}

	/** A grey caption on the left ("Attack", "Pray") and its content on the right. */
	private static JPanel factRow(String caption, JComponent content)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		JLabel label = new JLabel(caption);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setPreferredSize(new Dimension(CAPTION_WIDTH, 20));
		label.setVerticalAlignment(JLabel.TOP);
		label.setBorder(new EmptyBorder(3, 0, 0, 0));
		row.add(label, BorderLayout.WEST);
		row.add(content, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static JLabel chipIcon(String tooltip)
	{
		JLabel label = new JLabel();
		label.setToolTipText(tooltip);
		label.setBorder(new EmptyBorder(0, 0, 0, 3));
		// Reserve the space before the icon loads so the row doesn't jump
		label.setPreferredSize(new Dimension(20, 20));
		return label;
	}

	private static JLabel chipText(String text, Color color, String tooltip)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setToolTipText(tooltip);
		label.setBorder(new EmptyBorder(0, 0, 0, 3));
		return label;
	}

	private static JLabel gap()
	{
		JLabel gap = new JLabel();
		gap.setPreferredSize(new Dimension(10, 20));
		return gap;
	}

	/**
	 * A row that shows the label and the start of the text, and opens to the full text when clicked.
	 * Stays open or closed when the panel is rebuilt after each kill.
	 */
	private void addDrawer(JPanel card, String title, String text)
	{
		if (!notBlank(text))
		{
			return;
		}
		boolean open = memory.isOpen(title);

		JPanel drawer = new JPanel(new BorderLayout());
		drawer.setOpaque(false);
		drawer.setAlignmentX(LEFT_ALIGNMENT);
		drawer.setBorder(new EmptyBorder(4, 0, 0, 0));

		// The RuneScape font has no arrow characters, so draw the arrow
		JLabel arrow = new JLabel(open ? ARROW_OPEN : ARROW_CLOSED);
		arrow.setBorder(new EmptyBorder(0, 0, 0, 4));
		JLabel header = new JLabel("<html><b>" + escape(title) + "</b>" + (open ? "" : "  " + escape(preview(text))) + "</html>");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(arrow, BorderLayout.WEST);
		top.add(header, BorderLayout.CENTER);
		top.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		top.setToolTipText(open ? "Hide" : "Show all");
		top.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleDrawer(title);
			}
		});
		drawer.add(top, BorderLayout.NORTH);

		if (open)
		{
			JLabel body = wrapped(text, Color.WHITE, FontManager.getRunescapeSmallFont(), INFO_TEXT_WIDTH);
			body.setBorder(new EmptyBorder(2, 14, 0, 0));
			drawer.add(body, BorderLayout.CENTER);
		}
		drawer.setMaximumSize(new Dimension(Integer.MAX_VALUE, drawer.getPreferredSize().height));
		card.add(drawer);
	}

	/** A small orange star marking monsters that can spawn a superior. */
	private static ImageIcon starIcon()
	{
		int size = 9;
		int[] xs = new int[10];
		int[] ys = new int[10];
		for (int i = 0; i < 10; i++)
		{
			double r = i % 2 == 0 ? size / 2.0 : size / 4.6;
			double a = Math.PI / 2 + i * Math.PI / 5;
			xs[i] = (int) Math.round(size / 2.0 + r * Math.cos(a));
			ys[i] = (int) Math.round(size / 2.0 - r * Math.sin(a) + 0.5);
		}
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(SUPERIOR_COLOR);
		g.fillPolygon(xs, ys, 10);
		g.dispose();
		return new ImageIcon(image);
	}

	/** A small grey triangle pointing right (closed) or down (open). */
	private static ImageIcon arrowIcon(boolean open)
	{
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.fillPolygon(open ? new int[]{0, 8, 4} : new int[]{1, 7, 1}, open ? new int[]{2, 2, 7} : new int[]{0, 4, 8}, 3);
		g.dispose();
		return new ImageIcon(image);
	}

	/** Drops the sentences of a note that talk about elemental (spell) weaknesses. */
	static String withoutElementNotes(String note)
	{
		if (note == null)
		{
			return null;
		}
		StringBuilder kept = new StringBuilder();
		for (String sentence : note.split("(?<=[.;])\\s+"))
		{
			// Keep sentences that also give weapon advice ("Water spells and demonbane weapons")
			if (!ELEMENT_NOTE.matcher(sentence).find() || WEAPON_NOTE.matcher(sentence).find())
			{
				kept.append(kept.length() > 0 ? " " : "").append(sentence);
			}
		}
		return kept.length() > 0 ? kept.toString() : null;
	}

	/** The first few words of a note, cut at a word boundary. */
	static String preview(String text)
	{
		String t = text.trim();
		if (t.length() <= PREVIEW_LENGTH)
		{
			return t;
		}
		int cut = t.lastIndexOf(' ', PREVIEW_LENGTH);
		return t.substring(0, cut > PREVIEW_LENGTH / 2 ? cut : PREVIEW_LENGTH).replaceAll("[,;:.]$", "") + "...";
	}

	private static String nullToEmpty(String s)
	{
		return s == null ? "" : s;
	}

	private static boolean notBlank(String s)
	{
		return s != null && !s.trim().isEmpty();
	}

	/** One place: its name and facts, and its Go button. {@code lastTime}: the place the player chose last time. */
	private JPanel card(LocationOption location, boolean lastTime)
	{
		JPanel card = new JPanel(new BorderLayout(6, 2));
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 6));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		text.add(wrapped(location.getLabel(), Color.WHITE, FontManager.getRunescapeBoldFont(), CARD_TEXT_WIDTH));

		// Combat type, and whether a cannon can be set up here
		boolean cannonKnown = "yes".equals(location.getCannon()) || "no".equals(location.getCannon());
		if (!compact && (location.getCombat() != null || cannonKnown))
		{
			JPanel facts = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			facts.setOpaque(false);
			facts.setAlignmentX(LEFT_ALIGNMENT);
			if (location.getCombat() != null)
			{
				JLabel combat = line(capitalise(location.getCombat()) + "-combat", ColorScheme.LIGHT_GRAY_COLOR);
				combat.setBorder(new EmptyBorder(0, 0, 0, 8));
				facts.add(combat);
			}
			if (cannonKnown)
			{
				boolean allowed = "yes".equals(location.getCannon());
				JLabel ball = chipIcon(allowed ? "You can use a cannon here" : "Cannons can't be set up here");
				// Room for the 16px icon plus the chip's 3px right border, so the text doesn't touch the ball
				ball.setPreferredSize(new Dimension(20, 18));
				icons.setCannon(ball, allowed);
				facts.add(ball);
				facts.add(chipText(allowed ? "Cannon" : "No cannon",
					allowed ? RECOMMENDED_COLOR : ColorScheme.MEDIUM_GRAY_COLOR,
					ball.getToolTipText()));
			}
			facts.setMaximumSize(new Dimension(Integer.MAX_VALUE, facts.getPreferredSize().height));
			text.add(facts);
		}
		List<String> here = Variants.at(currentVariants, location.getKey());
		if (!compact && !here.isEmpty())
		{
			StringBuilder names = new StringBuilder();
			for (String name : here)
			{
				if (names.length() > 0)
				{
					names.append(", ");
				}
				names.append(name.equals(selectedVariant) ? "<font color='#ff981f'>" + escape(name) + "</font>" : escape(name));
			}
			JLabel monsters = new JLabel("<html><body style='width:" + CARD_TEXT_WIDTH + "px'>" + names + "</body></html>");
			monsters.setFont(FontManager.getRunescapeSmallFont());
			monsters.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			monsters.setAlignmentX(LEFT_ALIGNMENT);
			text.add(monsters);
		}
		if (lastTime)
		{
			JLabel last = line(LAST_TIME, LAST_TIME_COLOR);
			last.setToolTipText("You chose this place the last time you had this task");
			text.add(last);
		}
		if (location.isRecommended())
		{
			text.add(line("Recommended", RECOMMENDED_COLOR));
		}
		if (location.isWilderness())
		{
			text.add(line("Wilderness", WILDERNESS_COLOR));
		}
		else if (location.isTouchesWilderness())
		{
			text.add(line("Some spawns in the Wilderness", WILDERNESS_COLOR));
		}
		if (!location.isAccessible())
		{
			text.add(wrapped("Needs " + String.join(", ", location.getMissingRequirements()), WILDERNESS_COLOR,
				FontManager.getRunescapeSmallFont(), CARD_TEXT_WIDTH));
		}
		if (!compact && location.getNote() != null)
		{
			text.add(wrapped(location.getNote(), ColorScheme.LIGHT_GRAY_COLOR, FontManager.getRunescapeSmallFont(), CARD_TEXT_WIDTH));
		}
		card.add(text, BorderLayout.CENTER);

		JButton go = new JButton("Go");
		go.setPreferredSize(new Dimension(40, 22));
		go.setMargin(new java.awt.Insets(0, 0, 0, 0));
		go.setEnabled(location.isRoutable());
		go.setToolTipText(!location.isRoutable() ? "No map point for this place yet"
			: location.isAccessible() ? "Path to " + location.getLabel()
			: "Path to " + location.getLabel() + " (you may not be able to get in yet)");
		go.addActionListener(e ->
		{
			// Going somewhere switches the variant details to what lives there
			if (!here.isEmpty() && !here.contains(selectedVariant))
			{
				selectedVariant = here.get(0);
				memory.setMonster(variantTaskKey, selectedVariant);
			}
			// Marked "Last time" when this task comes up again (not for a preview: it isn't a real trip)
			if (!previewing)
			{
				memory.setLastPlace(variantTaskKey, location.getKey());
			}
			onMemoryChanged.accept(memory);
			redraw();
			onGo.accept(location);
		});
		JPanel goWrap = new JPanel(new BorderLayout());
		goWrap.setOpaque(false);
		goWrap.add(go, BorderLayout.NORTH);
		card.add(goWrap, BorderLayout.EAST);
		return card;
	}

	private static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel line(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel wrapped(String text, Color color)
	{
		return wrapped(text, color, FontManager.getRunescapeSmallFont(), FULL_TEXT_WIDTH);
	}

	private static JLabel wrapped(String text, Color color, java.awt.Font font, int width)
	{
		// HTML lets Swing wrap the text to the given width
		JLabel label = new JLabel("<html><body style='width:" + width + "px'>" + escape(text) + "</body></html>");
		label.setFont(font);
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String capitalise(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Show another monster's details, as clicking its row does. */
	void selectVariant(String name)
	{
		pickVariant(name);
	}

	/** The player picked a monster in the table: show it, and remember it for this task. */
	private void pickVariant(String name)
	{
		selectedVariant = name;
		if (variantTaskKey != null)
		{
			memory.setMonster(variantTaskKey, name);
			onMemoryChanged.accept(memory);
		}
		redraw();
	}

	/** Open or close a drawer, as clicking its title does. Remembered for every task. */
	void toggleDrawer(String title)
	{
		memory.toggle(title);
		onMemoryChanged.accept(memory);
		redraw();
	}

	/** The monster whose details are shown. */
	String getSelectedVariant()
	{
		return selectedVariant;
	}

	private void redraw()
	{
		if (showingTask)
		{
			drawTask();
		}
	}

	private void refresh()
	{
		content.revalidate();
		content.repaint();
	}
}
