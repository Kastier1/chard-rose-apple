package com.combatgearorganizer.slayer;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Puts game icons on the panel's labels: skill icons for combat styles, the prayer book's protection prayers and
 * rune icons for elemental weaknesses. Sprites and item images load in the background and appear when ready.
 */
@Singleton
public class SlayerIcons
{
	/** Every icon is scaled to this height so the chips line up. */
	private static final int ICON_SIZE = 18;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ItemManager itemManager;

	public void setStyle(JLabel label, TaskTags.Style style)
	{
		Skill skill = style == TaskTags.Style.MELEE ? Skill.ATTACK
			: style == TaskTags.Style.RANGED ? Skill.RANGED
			: Skill.MAGIC;
		label.setIcon(scaled(skillIconManager.getSkillImage(skill, true)));
	}

	public void setPrayer(JLabel label, TaskTags.Prayer prayer)
	{
		int sprite = prayer == TaskTags.Prayer.MELEE ? SpriteID.Prayeron.PROTECT_FROM_MELEE
			: prayer == TaskTags.Prayer.MISSILES ? SpriteID.Prayeron.PROTECT_FROM_MISSILES
			: SpriteID.Prayeron.PROTECT_FROM_MAGIC;
		spriteManager.getSpriteAsync(sprite, 0, image -> SwingUtilities.invokeLater(() -> label.setIcon(scaled(image))));
	}

	public void setElement(JLabel label, TaskTags.Element element)
	{
		int item = element == TaskTags.Element.AIR ? ItemID.AIRRUNE
			: element == TaskTags.Element.WATER ? ItemID.WATERRUNE
			: element == TaskTags.Element.EARTH ? ItemID.EARTHRUNE
			: ItemID.FIRERUNE;
		AsyncBufferedImage image = itemManager.getImage(item);
		image.onLoaded(() -> SwingUtilities.invokeLater(() -> label.setIcon(scaled(image))));
	}

	/** A cannonball: full colour where cannons are allowed, greyed out where they aren't. */
	public void setCannon(JLabel label, boolean allowed)
	{
		AsyncBufferedImage image = itemManager.getImage(ItemID.MCANNONBALL);
		image.onLoaded(() -> SwingUtilities.invokeLater(() ->
			label.setIcon(scaled(allowed ? image : ImageUtil.grayscaleImage(image)))));
	}

	private static ImageIcon scaled(BufferedImage image)
	{
		if (image == null)
		{
			return null;
		}
		int width = Math.max(1, image.getWidth() * ICON_SIZE / Math.max(1, image.getHeight()));
		return new ImageIcon(ImageUtil.resizeImage(image, width, ICON_SIZE, true));
	}
}
