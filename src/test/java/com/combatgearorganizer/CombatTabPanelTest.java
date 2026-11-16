package com.combatgearorganizer;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Draws the Combat tab section headless, in each state a player can see.
 */
public class CombatTabPanelTest
{
	private final List<String> events = new ArrayList<>();

	@BeforeClass
	public static void headless()
	{
		System.setProperty("java.awt.headless", "true");
	}

	private CombatTabPanel panel()
	{
		return new CombatTabPanel(t -> events.add("tag " + t), g -> events.add("grouping " + g),
			() -> events.add("organize"), () -> events.add("undo"), () -> events.add("add"));
	}

	private static CombatTabStatus status(boolean bankOpen, int tagged, boolean layout, boolean canUndo)
	{
		return new CombatTabStatus(true, true, bankOpen, "combat", Arrays.asList("weapons", "combat"), tagged, layout,
			canUndo);
	}

	@Test
	public void newPlayerSeesEveryStepAndWhy() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel p = panel();
			p.update(new CombatTabStatus(true, false, false, "combat", Collections.emptyList(), 0, false, false),
				Grouping.BY_STYLE);
			for (SetupChecklist.Step step : SetupChecklist.steps(p.getStatus()))
			{
				assertTrue(step.getTitle(), containsText(p, step.getTitle()));
				assertTrue(step.getHelp(), containsText(p, step.getHelp()));
			}
			assertFalse(button(p, CombatTabPanel.ORGANIZE).isEnabled());
			assertTrue(visibleText(p, "Turn on the Bank Tags plugin first."));
			assertTrue(button(p, CombatTabPanel.ADD_WORN).isEnabled());
			assertFalse(button(p, CombatTabPanel.UNDO).isVisible());
			assertFalse(containsText(p, CombatTabPanel.SETUP_DONE));
		});
	}

	@Test
	public void organizeIsOffWithAReasonWhileTheBankIsClosed() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel p = panel();
			p.update(status(false, 48, true, false), Grouping.BY_STYLE);
			assertFalse(button(p, CombatTabPanel.ORGANIZE).isEnabled());
			assertTrue(visibleText(p, "Open your bank first."));
			assertTrue(containsText(p, CombatTabPanel.SETUP_DONE));

			p.update(status(true, 48, true, false), Grouping.BY_STYLE);
			assertTrue(button(p, CombatTabPanel.ORGANIZE).isEnabled());
			assertFalse(visibleText(p, "Open your bank first."));
			button(p, CombatTabPanel.ORGANIZE).doClick(0);
			button(p, CombatTabPanel.ADD_WORN).doClick(0);
			assertEquals(Arrays.asList("organize", "add"), events);
		});
	}

	@Test
	public void undoShowsOnlyWhenTheresSomethingToPutBack() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel p = panel();
			p.update(status(true, 48, true, true), Grouping.BY_STYLE);
			assertTrue(button(p, CombatTabPanel.UNDO).isVisible());
			button(p, CombatTabPanel.UNDO).doClick(0);
			assertEquals(Collections.singletonList("undo"), events);
			p.update(status(true, 48, true, false), Grouping.BY_STYLE);
			assertFalse(button(p, CombatTabPanel.UNDO).isVisible());
		});
	}

	@Test
	public void layoutStaysInSyncBothWays() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel p = panel();
			p.update(status(true, 48, true, false), Grouping.BY_STYLE);
			assertTrue(radio(p, Grouping.BY_STYLE.getShortName()).isSelected());
			assertTrue(containsText(p, Grouping.BY_STYLE.getDescription()));
			assertTrue(containsText(p, Grouping.BY_SLOT.getDescription()));

			// Changed in the settings: shown, and not sent back
			p.update(status(true, 48, true, false), Grouping.BY_SLOT);
			assertTrue(radio(p, Grouping.BY_SLOT.getShortName()).isSelected());
			assertFalse(radio(p, Grouping.BY_STYLE.getShortName()).isSelected());
			assertTrue(events.isEmpty());

			// Picked in the panel: sent to the settings
			radio(p, Grouping.BY_STYLE.getShortName()).doClick(0);
			assertEquals(Collections.singletonList("grouping " + Grouping.BY_STYLE), events);
		});
	}

	@Test
	public void tagComesFromTheTabListOrTyping() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel p = panel();
			p.update(status(true, 48, true, false), Grouping.BY_STYLE);
			@SuppressWarnings("unchecked")
			JComboBox<String> box = (JComboBox<String>) find(p, JComboBox.class);
			assertEquals(2, box.getItemCount());
			assertEquals("weapons", box.getItemAt(0));
			assertEquals("combat", box.getSelectedItem());
			assertTrue(events.isEmpty());

			box.setSelectedItem("weapons");
			assertEquals(Collections.singletonList("tag weapons"), events);

			// A name that isn't a tab yet
			events.clear();
			box.setSelectedItem("slayer gear");
			assertEquals(Collections.singletonList("tag slayer gear"), events);

			// The setting changed elsewhere: shown, and not sent back
			events.clear();
			p.update(new CombatTabStatus(true, true, true, "range", Arrays.asList("weapons", "combat"), 0, false, false),
				Grouping.BY_STYLE);
			assertEquals("range", box.getSelectedItem());
			assertTrue(events.isEmpty());
		});
	}

	@Test
	public void summaryLineAfterOrganizing() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel p = panel();
			p.update(status(true, 48, true, false), Grouping.BY_STYLE);
			p.showNote("Pick Keep or Undo in the chat box.", "48 items: 14 melee, 12 ranged, 10 magic, 12 other");
			assertTrue(visibleText(p, "Pick Keep or Undo in the chat box."));
			assertTrue(visibleText(p, "48 items: 14 melee, 12 ranged, 10 magic, 12 other"));
			p.showNote(null, null);
			assertFalse(visibleText(p, "48 items"));
		});
	}

	@Test
	public void organizerPanelSwitchesBetweenSections() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel combat = panel();
			OrganizerPanel p = new OrganizerPanel(combat);
			assertTrue(containsText(p, OrganizerPanel.SLAYER_OFF));
			assertFalse(isInside(combat, p));
			p.showCombatTab();
			assertTrue(isInside(combat, p));
			assertFalse(containsText(p, OrganizerPanel.SLAYER_OFF));
			p.showSlayer();
			assertFalse(isInside(combat, p));
		});
	}

	static boolean isInside(Component child, Container root)
	{
		for (Component c = child; c != null; c = c.getParent())
		{
			if (c == root)
			{
				return true;
			}
		}
		return false;
	}

	private static JButton button(Component root, String text)
	{
		final List<AbstractButton> found = new ArrayList<>();
		collect(root, text, found);
		for (AbstractButton b : found)
		{
			if (b instanceof JButton)
			{
				return (JButton) b;
			}
		}
		throw new AssertionError("No button " + text);
	}

	private static JRadioButton radio(Component root, String text)
	{
		final List<AbstractButton> found = new ArrayList<>();
		collect(root, text, found);
		for (AbstractButton b : found)
		{
			if (b instanceof JRadioButton)
			{
				return (JRadioButton) b;
			}
		}
		throw new AssertionError("No radio button " + text);
	}

	private static void collect(Component c, String text, List<AbstractButton> out)
	{
		if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()))
		{
			out.add((AbstractButton) c);
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collect(child, text, out);
			}
		}
	}

	private static Component find(Component c, Class<?> type)
	{
		if (type.isInstance(c))
		{
			return c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				Component found = find(child, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	static boolean containsText(Component c, String text)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().contains(text))
		{
			return true;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				if (containsText(child, text))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean visibleText(Component c, String text)
	{
		if (!c.isVisible())
		{
			return false;
		}
		if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().contains(text))
		{
			return true;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				if (visibleText(child, text))
				{
					return true;
				}
			}
		}
		return false;
	}
}
