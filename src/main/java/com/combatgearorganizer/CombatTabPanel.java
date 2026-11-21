package com.combatgearorganizer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Combat tab section of the side panel: which tag, which layout, Organize now, Add worn gear and the setup
 * checklist. Only touch it on the Swing thread; it never reads the game itself.
 */
public class CombatTabPanel extends JPanel
{
	private static final int TEXT_WIDTH = 165;
	private static final int STEP_TEXT_WIDTH = 145;
	private static final Color DONE_COLOR = new Color(110, 200, 90);
	private static final Color REASON_COLOR = new Color(255, 152, 31);
	private static final ImageIcon TICK = checkIcon(true);
	private static final ImageIcon BOX = checkIcon(false);

	static final String ORGANIZE = "Organize now";
	static final String UNDO = "Undo";
	static final String ADD_WORN = "Add worn gear";
	static final String SETUP_DONE = "Setup done.";

	private final Consumer<String> onTagChosen;
	private final Consumer<Grouping> onGroupingChosen;

	private final JComboBox<String> tagBox = new JComboBox<>();
	private final Map<Grouping, JRadioButton> groupingButtons = new EnumMap<>(Grouping.class);
	private final JButton organizeButton = new JButton(ORGANIZE);
	private final JButton undoButton = new JButton(UNDO);
	private final JButton addWornButton = new JButton(ADD_WORN);
	private final JLabel organizeReason = new JLabel();
	private final JLabel addWornReason = new JLabel();
	private final JLabel note = new JLabel();
	private final JLabel summary = new JLabel();
	private final JPanel checklist = new JPanel();

	/** Set while the panel shows new settings, so that doesn't count as the player choosing them. */
	private boolean updating;
	private List<String> shownTabs = Collections.emptyList();
	private CombatTabStatus status = CombatTabStatus.LOGGED_OUT;

	/**
	 * @param onTagChosen      the player picked or typed a tag; called on the Swing thread with the raw text
	 * @param onGroupingChosen the player picked By style, By slot or By set
	 * @param onOrganize       Organize now
	 * @param onUndo           Undo (after an organize that had no Keep / Undo preview)
	 * @param onAddWorn        Add worn gear
	 */
	public CombatTabPanel(Consumer<String> onTagChosen, Consumer<Grouping> onGroupingChosen, Runnable onOrganize,
		Runnable onUndo, Runnable onAddWorn)
	{
		this.onTagChosen = onTagChosen;
		this.onGroupingChosen = onGroupingChosen;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(title("Bank tag"));
		add(Box.createVerticalStrut(4));
		tagBox.setEditable(true);
		tagBox.setToolTipText("Pick one of your tag tabs, or type a name");
		tagBox.setAlignmentX(LEFT_ALIGNMENT);
		tagBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, tagBox.getPreferredSize().height));
		tagBox.addActionListener(e -> commitTag(tagBox.getSelectedItem()));
		tagBox.getEditor().getEditorComponent().addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commitTag(tagBox.getEditor().getItem());
			}
		});
		add(tagBox);
		add(Box.createVerticalStrut(10));

		add(title("Layout"));
		add(Box.createVerticalStrut(2));
		final ButtonGroup group = new ButtonGroup();
		for (Grouping g : Grouping.values())
		{
			final JRadioButton button = new JRadioButton(g.getShortName());
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setForeground(Color.WHITE);
			button.setOpaque(false);
			button.setAlignmentX(LEFT_ALIGNMENT);
			button.setFocusPainted(false);
			button.addActionListener(e ->
			{
				if (!updating && button.isSelected())
				{
					onGroupingChosen.accept(g);
				}
			});
			group.add(button);
			groupingButtons.put(g, button);
			add(button);
			// Indented under the radio button, so it gets the narrower width the checklist's indented text uses
			final JLabel description = wrapped(g.getDescription(), ColorScheme.LIGHT_GRAY_COLOR, STEP_TEXT_WIDTH);
			description.setBorder(new EmptyBorder(0, 22, 4, 0));
			add(description);
		}
		add(Box.createVerticalStrut(8));

		add(fullWidth(organizeButton));
		organizeButton.addActionListener(e -> onOrganize.run());
		add(reasonLine(organizeReason));
		add(fullWidth(undoButton));
		undoButton.setToolTipText("Put back the layout from before the plugin last organized this tab");
		undoButton.addActionListener(e -> onUndo.run());
		add(Box.createVerticalStrut(4));
		add(fullWidth(addWornButton));
		addWornButton.setToolTipText("Tag everything you're wearing with this tag");
		addWornButton.addActionListener(e -> onAddWorn.run());
		add(reasonLine(addWornReason));

		note.setFont(FontManager.getRunescapeSmallFont());
		note.setAlignmentX(LEFT_ALIGNMENT);
		note.setBorder(new EmptyBorder(4, 0, 0, 0));
		add(note);
		summary.setFont(FontManager.getRunescapeSmallFont());
		summary.setForeground(DONE_COLOR);
		summary.setAlignmentX(LEFT_ALIGNMENT);
		add(summary);
		add(Box.createVerticalStrut(10));

		checklist.setLayout(new BoxLayout(checklist, BoxLayout.Y_AXIS));
		checklist.setOpaque(false);
		checklist.setAlignmentX(LEFT_ALIGNMENT);
		add(checklist);

		showNote(null, null);
		update(CombatTabStatus.LOGGED_OUT, Grouping.BY_STYLE);
	}

	/** Shows the game state and the settings; the settings may have changed in the plugin's config. */
	public void update(CombatTabStatus status, Grouping grouping)
	{
		this.status = status;
		updating = true;
		try
		{
			if (!status.getTagTabs().equals(shownTabs))
			{
				shownTabs = new ArrayList<>(status.getTagTabs());
				tagBox.setModel(new DefaultComboBoxModel<>(shownTabs.toArray(new String[0])));
			}
			if (!status.getTag().equals(tagBox.getSelectedItem()))
			{
				tagBox.setSelectedItem(status.getTag());
			}
			final JRadioButton button = groupingButtons.get(grouping);
			if (button != null && !button.isSelected())
			{
				button.setSelected(true);
			}
		}
		finally
		{
			updating = false;
		}

		final String blocker = SetupChecklist.organizeBlocker(status);
		organizeButton.setEnabled(blocker == null);
		setReason(organizeReason, blocker);
		undoButton.setVisible(status.isCanUndo());
		final String addBlocker = SetupChecklist.addWornBlocker(status);
		addWornButton.setEnabled(addBlocker == null);
		setReason(addWornReason, addBlocker);

		drawChecklist();
		revalidate();
		repaint();
	}

	/**
	 * A line about the last thing the player did, and the summary of the organized tab.
	 *
	 * @param text        e.g. "Pick Keep or Undo in the chat box."; null for none
	 * @param summaryText e.g. "48 items: 14 melee, 12 ranged, 10 magic, 12 other"; null for none
	 */
	public void showNote(String text, String summaryText)
	{
		note.setText(text != null ? html(text, TEXT_WIDTH) : "");
		note.setForeground(Color.WHITE);
		note.setVisible(text != null);
		summary.setText(summaryText != null ? html(summaryText, TEXT_WIDTH) : "");
		summary.setVisible(summaryText != null);
		revalidate();
		repaint();
	}

	CombatTabStatus getStatus()
	{
		return status;
	}

	private void commitTag(Object typed)
	{
		if (updating)
		{
			return;
		}
		final String text = typed != null ? typed.toString().trim() : "";
		if (text.isEmpty())
		{
			// A cleared box doesn't clear the setting: show the tag that's still set
			if (!status.getTag().isEmpty())
			{
				updating = true;
				try
				{
					tagBox.setSelectedItem(status.getTag());
				}
				finally
				{
					updating = false;
				}
			}
			return;
		}
		if (!text.equalsIgnoreCase(status.getTag()))
		{
			onTagChosen.accept(text);
		}
	}

	private void drawChecklist()
	{
		checklist.removeAll();
		final List<SetupChecklist.Step> steps = SetupChecklist.steps(status);
		final boolean complete = steps.stream().allMatch(SetupChecklist.Step::isDone);
		checklist.add(title("Setup"));
		checklist.add(Box.createVerticalStrut(4));
		if (complete)
		{
			checklist.add(wrapped(SETUP_DONE, DONE_COLOR, TEXT_WIDTH));
			checklist.add(Box.createVerticalStrut(4));
		}
		for (SetupChecklist.Step step : steps)
		{
			final JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setBorder(new EmptyBorder(0, 0, 6, 0));
			final JLabel box = new JLabel(step.isDone() ? TICK : BOX);
			box.setVerticalAlignment(JLabel.TOP);
			box.setToolTipText(step.isDone() ? "Done" : "Not done yet");
			row.add(box, BorderLayout.WEST);

			final JPanel text = new JPanel();
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
			text.setOpaque(false);
			text.add(wrapped(step.getTitle(), step.isDone() ? DONE_COLOR : Color.WHITE, STEP_TEXT_WIDTH));
			if (step.getHelp() != null)
			{
				text.add(wrapped(step.getHelp(), ColorScheme.LIGHT_GRAY_COLOR, STEP_TEXT_WIDTH));
			}
			row.add(text, BorderLayout.CENTER);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			checklist.add(row);
		}
	}

	private static void setReason(JLabel label, String reason)
	{
		label.setText(reason != null ? html(reason, TEXT_WIDTH) : "");
		label.setVisible(reason != null);
	}

	private static JLabel reasonLine(JLabel label)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(REASON_COLOR);
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setBorder(new EmptyBorder(2, 0, 2, 0));
		return label;
	}

	private static Component fullWidth(JButton button)
	{
		button.setFocusPainted(false);
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
		return button;
	}

	private static JLabel title(String text)
	{
		final JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel wrapped(String text, Color color, int width)
	{
		final JLabel label = new JLabel(html(text, width));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static String html(String text, int width)
	{
		final String escaped = Objects.requireNonNull(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><body style='width:" + width + "px'>" + escaped + "</body></html>";
	}

	/** A 12px box, ticked when done. */
	private static ImageIcon checkIcon(boolean ticked)
	{
		final BufferedImage img = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ticked ? DONE_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		g.setStroke(new BasicStroke(1.2f));
		g.drawRect(1, 1, 10, 10);
		if (ticked)
		{
			g.setStroke(new BasicStroke(2f));
			g.drawLine(3, 6, 5, 9);
			g.drawLine(5, 9, 10, 2);
		}
		g.dispose();
		return new ImageIcon(img);
	}
}
