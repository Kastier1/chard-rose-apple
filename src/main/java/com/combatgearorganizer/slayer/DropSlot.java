package com.combatgearorganizer.slayer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * One drop in the panel's item grid. Shows the collection log status by shape as well as colour: a logged item has a
 * tick in its corner, a missing one is faded with a dashed outline, anything else is plain.
 */
class DropSlot extends JLabel
{
	static final int WIDTH = 36;
	static final int HEIGHT = 32;
	private static final Color MISSING_OUTLINE = new Color(255, 152, 31);
	private static final Color TICK = new Color(110, 200, 90);
	private static final Color BADGE = new Color(20, 20, 20, 210);

	private final CollectionLog.Status status;

	DropSlot(CollectionLog.Status status, String tooltip)
	{
		this.status = status;
		setToolTipText(tooltip);
		setHorizontalAlignment(SwingConstants.CENTER);
		setVerticalAlignment(SwingConstants.CENTER);
		// Reserve the space before the picture loads, so the grid doesn't jump
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		setMinimumSize(new Dimension(WIDTH, HEIGHT));
	}

	CollectionLog.Status getStatus()
	{
		return status;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (status == CollectionLog.Status.MISSING)
			{
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
				super.paintComponent(g2);
				g2.setComposite(AlphaComposite.SrcOver);
				g2.setColor(MISSING_OUTLINE);
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 2f}, 0f));
				g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
			}
			else
			{
				super.paintComponent(g2);
			}
			if (status == CollectionLog.Status.LOGGED)
			{
				int size = 9;
				int x = getWidth() - size - 1;
				int y = getHeight() - size - 1;
				g2.setColor(BADGE);
				g2.fillOval(x, y, size, size);
				g2.setColor(TICK);
				g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawPolyline(new int[]{x + 2, x + 4, x + 7}, new int[]{y + 5, y + 7, y + 2}, 3);
			}
		}
		finally
		{
			g2.dispose();
		}
	}
}
