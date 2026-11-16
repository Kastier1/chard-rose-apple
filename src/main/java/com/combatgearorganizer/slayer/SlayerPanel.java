package com.combatgearorganizer.slayer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel showing the current slayer task and where to do it. Only touch it on the Swing thread.
 */
public class SlayerPanel extends PluginPanel
{
	private static final Color WILDERNESS_COLOR = new Color(230, 80, 60);
	private static final Color RECOMMENDED_COLOR = new Color(110, 200, 90);
	/** Text widths in px, so labels wrap inside the 225px panel. */
	private static final int FULL_TEXT_WIDTH = 165;
	private static final int CARD_TEXT_WIDTH = 135;

	private final Consumer<LocationOption> onGo;
	private final Consumer<List<LocationOption>> onGoNearest;
	private final Runnable onClear;

	private final JPanel content = new JPanel();

	public SlayerPanel(Consumer<LocationOption> onGo, Consumer<List<LocationOption>> onGoNearest, Runnable onClear)
	{
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
		content.removeAll();
		content.add(title("Slayer task"));
		content.add(Box.createVerticalStrut(8));
		content.add(wrapped("No task right now. Get one from a slayer master, or check your enchanted gem.", ColorScheme.LIGHT_GRAY_COLOR));
		refresh();
	}

	public void showTask(SlayerTaskState task, List<LocationOption> locations, boolean hasData, boolean shortestPathAvailable)
	{
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

		List<String> tags = new ArrayList<>();
		if (location.getCombat() != null)
		{
			tags.add(capitalise(location.getCombat()) + "-combat");
		}
		if ("yes".equals(location.getCannon()))
		{
			tags.add("Cannon");
		}
		if (!tags.isEmpty())
		{
			text.add(line(String.join(" · ", tags), ColorScheme.LIGHT_GRAY_COLOR));
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
		go.addActionListener(e -> onGo.accept(location));
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

	private void refresh()
	{
		content.revalidate();
		content.repaint();
	}
}
