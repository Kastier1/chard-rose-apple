package com.combatgearorganizer;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The plugin's one side panel: a Slayer / Combat tab switcher above the chosen section. Swing thread only.
 */
public class OrganizerPanel extends PluginPanel
{
	static final String SLAYER = "Slayer";
	static final String COMBAT_TAB = "Combat tab";
	static final String SLAYER_OFF = "Turn on \"Slayer task panel\" in the plugin settings to see your task here.";

	private final JPanel slayerHolder = new JPanel(new BorderLayout());
	private final MaterialTab slayerTab;
	private final MaterialTab combatTab;
	private final MaterialTabGroup tabs;

	public OrganizerPanel(CombatTabPanel combatTabPanel)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// BorderLayout so the section gets the panel's full width, not just its preferred width
		final JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);
		tabs = new MaterialTabGroup(display);
		tabs.setBorder(new EmptyBorder(0, 0, 8, 0));
		slayerHolder.setOpaque(false);
		slayerTab = new MaterialTab(SLAYER, tabs, slayerHolder);
		combatTab = new MaterialTab(COMBAT_TAB, tabs, combatTabPanel);
		tabs.addTab(slayerTab);
		tabs.addTab(combatTab);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
		setSlayerPanel(null);
		tabs.select(slayerTab);
	}

	/** The slayer section, or null while the slayer panel is turned off. */
	public void setSlayerPanel(JComponent slayerPanel)
	{
		slayerHolder.removeAll();
		if (slayerPanel != null)
		{
			slayerHolder.add(slayerPanel, BorderLayout.NORTH);
		}
		else
		{
			final JLabel off = new JLabel("<html><body style='width:165px'>" + SLAYER_OFF + "</body></html>");
			off.setFont(FontManager.getRunescapeSmallFont());
			off.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			slayerHolder.add(off, BorderLayout.NORTH);
		}
		slayerHolder.revalidate();
		slayerHolder.repaint();
	}

	public void showCombatTab()
	{
		tabs.select(combatTab);
	}

	public void showSlayer()
	{
		tabs.select(slayerTab);
	}

	boolean isCombatTabShown()
	{
		return combatTab.isSelected();
	}
}
