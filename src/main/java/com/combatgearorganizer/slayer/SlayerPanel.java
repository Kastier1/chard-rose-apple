package com.combatgearorganizer.slayer;

import com.combatgearorganizer.ItemValueMode;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel showing the current slayer task and where to do it. Only touch it on the Swing thread.
 */
@Slf4j
public class SlayerPanel extends PluginPanel
{
	private static final Color WILDERNESS_COLOR = new Color(230, 80, 60);
	private static final Color RECOMMENDED_COLOR = new Color(110, 200, 90);
	/** Text widths in px, so labels wrap inside the 225px panel. */
	private static final int FULL_TEXT_WIDTH = 165;
	private static final int CARD_TEXT_WIDTH = 135;
	private static final int INFO_TEXT_WIDTH = 140;
	private static final int PREVIEW_LENGTH = 22;
	private static final int CAPTION_WIDTH = 46;
	private static final Color WEAKNESS_COLOR = new Color(255, 200, 90);
	private static final Color MISSING_TEXT_COLOR = new Color(255, 152, 31);

	private final Consumer<LocationOption> onGo;
	private final Consumer<List<LocationOption>> onGoNearest;
	private final Runnable onClear;

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
	/** Drawer titles the player has opened; kept while the plugin runs. */
	private final Set<String> openDrawers = new HashSet<>();
	/** Last task shown, so a drawer click can redraw it. */
	private Runnable lastShow;
	/** The variant the player is looking at, by name, and which task it belongs to. */
	private String selectedVariant;
	private int variantTaskId = -1;
	private String variantTaskName;
	private List<Variant> currentVariants = Collections.emptyList();
	private CollectionLog.Snapshot currentClog = CollectionLog.Snapshot.EMPTY;
	private DropItems currentItems = DropItems.EMPTY;

	public SlayerPanel(SlayerIcons icons, Consumer<LocationOption> onGo, Consumer<List<LocationOption>> onGoNearest,
		Runnable onClear)
	{
		this.icons = icons;
		this.onGo = onGo;
		this.onGoNearest = onGoNearest;
		this.onClear = onClear;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.NORTH);
		showNoTask();
	}

	public void showNoTask()
	{
		lastShow = null;
		content.removeAll();
		content.add(title("Slayer task"));
		content.add(Box.createVerticalStrut(8));
		content.add(wrapped("No task right now. Get one from a slayer master, or check your enchanted gem.", ColorScheme.LIGHT_GRAY_COLOR));
		refresh();
	}

	public void showTask(SlayerTaskState task, SlayerLocationData.TaskInfo info, List<Variant> variants,
		List<LocationOption> locations, boolean hasData, boolean shortestPathAvailable, CollectionLog.Snapshot clog,
		DropItems items)
	{
		lastShow = () -> showTask(task, info, variants, locations, hasData, shortestPathAvailable, clog, items);
		currentClog = clog;
		currentItems = items != null ? items : DropItems.EMPTY;

		// Keep the player's chosen variant while the task lasts; pick a sensible one for a new task
		if (task.getTaskId() != variantTaskId || !Objects.equals(task.getName(), variantTaskName))
		{
			variantTaskId = task.getTaskId();
			variantTaskName = task.getName();
			selectedVariant = null;
		}
		currentVariants = variants;
		Variant selected = Variants.byName(Variants.listed(variants, locations), selectedVariant);
		if (selected == null)
		{
			selected = Variants.pickDefault(variants, locations);
		}
		selectedVariant = selected == null ? null : selected.getName();

		content.removeAll();
		content.add(title(task.getName()));

		String count = task.getInitial() > 0
			? task.getRemaining() + " of " + task.getInitial() + " left"
			: task.getRemaining() + " left";
		content.add(line(count, ColorScheme.LIGHT_GRAY_COLOR));

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

		boolean variantWeaknesses = Variants.regular(variants).stream().anyMatch(v -> v.getWeakness() != null);
		if (info != null || selected != null)
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
			content.add(wrapped(task.getTaskId() == 98
				? "Boss tasks don't have location data yet."
				: "No location data for this task yet.", ColorScheme.LIGHT_GRAY_COLOR));
			refresh();
			return;
		}

		if (!shortestPathAvailable)
		{
			content.add(wrapped("Install or turn on the Shortest Path plugin to get directions. Until then, Go marks the place on your world map.",
				ColorScheme.BRAND_ORANGE));
			content.add(Box.createVerticalStrut(6));
		}

		List<LocationOption> routable = new ArrayList<>();
		// "Nearest" leaves out places the player can't get into yet
		locations.stream().filter(l -> l.isRoutable() && l.isAccessible()).forEach(routable::add);

		JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton nearest = new JButton("Go to nearest");
		nearest.setEnabled(routable.size() > 0);
		nearest.setToolTipText("Path to whichever of these places is quickest to reach");
		nearest.addActionListener(e -> onGoNearest.accept(routable));
		JButton clear = new JButton("Clear path");
		clear.addActionListener(e -> onClear.run());
		actions.add(nearest);
		actions.add(clear);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		actions.setAlignmentX(LEFT_ALIGNMENT);
		content.add(actions);
		content.add(Box.createVerticalStrut(8));

		if (locations.isEmpty() && task.getMasterId() == SlayerMasters.KRYSTILIA)
		{
			content.add(wrapped("No Wilderness places in the data for this task yet.", ColorScheme.LIGHT_GRAY_COLOR));
		}
		else if (locations.isEmpty())
		{
			content.add(wrapped("All places for this task are in the Wilderness. Turn on \"Show Wilderness locations\" in the plugin settings to see them.",
				ColorScheme.LIGHT_GRAY_COLOR));
		}

		for (LocationOption location : locations)
		{
			content.add(card(location));
			content.add(Box.createVerticalStrut(4));
		}
		refresh();
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
		card.add(variantRow("Monster", null, "Lvl", "XP", "Task", ColorScheme.MEDIUM_GRAY_COLOR, false, null));
		for (Variant v : Variants.listed(variants, locations))
		{
			boolean on = v == selected;
			card.add(variantRow(v.getName(), superiorTip(variants, v), Variants.range(v.getLevel()), Variants.xpEach(v.getXp()),
				Variants.xpTotalShort(v.getXp(), left), on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR, on,
				on ? null : () ->
				{
					selectedVariant = v.getName();
					redraw();
				}));
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

		card.add(factRow("XP", textChip("<font color='#ffc85a'>" + Variants.xpEach(selected.getXp()) + "</font> each · "
			+ "<font color='#ffc85a'>" + Variants.xpTotal(selected.getXp(), left) + "</font> for " + left + " left",
			ColorScheme.LIGHT_GRAY_COLOR)));

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

		List<String> where = new ArrayList<>();
		for (LocationOption l : locations)
		{
			if (selected.getPlaces().contains(l.getKey()))
			{
				where.add(escape(l.getLabel()));
			}
		}
		card.add(factRow("Found at", textChip(where.isEmpty() ? "Not matched to a listed place" : String.join(", ", where),
			where.isEmpty() ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR)));
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

	/** Monsters left out of the table, superiors and bosses. */
	private void addVariantExtras(JPanel card, List<Variant> variants, List<LocationOption> locations)
	{
		// Monsters that count for the task but don't live at any listed place stay out of the way
		List<String> elsewhere = new ArrayList<>();
		for (Variant v : Variants.unlisted(variants, locations))
		{
			elsewhere.add(v.getName() + " (level " + Variants.range(v.getLevel()) + ", " + Variants.xpEach(v.getXp()) + " XP)");
		}
		if (!elsewhere.isEmpty())
		{
			addDrawer(card, "Also counts", String.join(", ", elsewhere) + ". Not at any of the places below.");
		}

		// Superiors already shown with their monster aren't repeated
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
		boolean selected, Runnable onClick)
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
		row.add(cell(total, color, 40, SwingConstants.RIGHT), c);

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

	/** Wrapping HTML text for the right-hand side of a fact row. */
	private static JLabel textChip(String html, Color color)
	{
		JLabel label = new JLabel("<html><body style='width:" + (INFO_TEXT_WIDTH - CAPTION_WIDTH) + "px'>" + html + "</body></html>");
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
		boolean open = openDrawers.contains(title);

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
				if (!openDrawers.remove(title))
				{
					openDrawers.add(title);
				}
				redraw();
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

	private JPanel card(LocationOption location)
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
		if (location.getCombat() != null || cannonKnown)
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
				ball.setPreferredSize(new Dimension(16, 18));
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
		if (!here.isEmpty())
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
		if (location.getNote() != null)
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
				redraw();
			}
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
		selectedVariant = name;
		redraw();
	}

	/** The monster whose details are shown. */
	String getSelectedVariant()
	{
		return selectedVariant;
	}

	private void redraw()
	{
		if (lastShow != null)
		{
			lastShow.run();
		}
	}

	private void refresh()
	{
		content.revalidate();
		content.repaint();
	}
}
