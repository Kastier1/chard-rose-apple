package com.combatgearorganizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TagItemResolverTest
{
	// Real ids from RuneLite's item_variations.json
	private static final int INFERNAL_CAPE_BROKEN = 21287; // group base
	private static final int INFERNAL_CAPE = 21295;
	private static final int SLAYER_HELMET = 11864; // group base
	private static final int SLAYER_HELMET_I = 11865;
	private static final int GLORY = 1704; // group base, uncharged
	private static final int GLORY_4 = 1712;
	private static final int GLORY_1 = 1706;
	private static final int WHIP = 4151;
	private static final int NOTED_WHIP = 4152;

	private static final Map<Integer, Integer> BASE = new HashMap<>();

	static
	{
		BASE.put(INFERNAL_CAPE, INFERNAL_CAPE_BROKEN);
		BASE.put(SLAYER_HELMET_I, SLAYER_HELMET);
		BASE.put(GLORY_4, GLORY);
		BASE.put(GLORY_1, GLORY);
	}

	private static final TagItemResolver.Items ITEMS = new TagItemResolver.Items()
	{
		@Override
		public int canonicalize(int itemId)
		{
			return itemId == NOTED_WHIP ? WHIP : itemId;
		}

		@Override
		public int variationBase(int itemId)
		{
			return BASE.getOrDefault(itemId, itemId);
		}
	};

	private static List<Integer> resolve(List<Integer> tags, int[] layout, List<Integer> owned)
	{
		// Fallback marks itself so tests can see when it was used
		return TagItemResolver.resolve(tags, layout, owned, ITEMS, base -> base + 100000);
	}

	@Test
	public void variationTagUsesTheVersionYouOwn()
	{
		// ::gearadd while wearing an Infernal cape stores -21287 (the broken cape)
		List<Integer> ids = resolve(Collections.singletonList(-INFERNAL_CAPE_BROKEN), null,
			Collections.singletonList(INFERNAL_CAPE));
		assertEquals(Collections.singletonList(INFERNAL_CAPE), ids);
	}

	@Test
	public void plainAndVariationTagOfTheSameItemGiveOneSlot()
	{
		// Tagged by hand earlier, then ::gearadd while wearing it
		List<Integer> ids = resolve(Arrays.asList(SLAYER_HELMET_I, -SLAYER_HELMET), null,
			Collections.singletonList(SLAYER_HELMET_I));
		assertEquals(Collections.singletonList(SLAYER_HELMET_I), ids);
	}

	@Test
	public void oldPlainTagOfAnotherVersionDoesNotAddAGhost()
	{
		// Found testing in game: the uncharged version was tagged on its own earlier, then Add worn gear tagged the
		// group while wearing the charged one. Only the charged one is owned, so only it is laid out.
		List<Integer> ids = resolve(Arrays.asList(GLORY, -GLORY), null, Collections.singletonList(GLORY_4));
		assertEquals(Collections.singletonList(GLORY_4), ids);
		// Same with a non-base version
		ids = resolve(Arrays.asList(GLORY_1, -GLORY), null, Collections.singletonList(GLORY_4));
		assertEquals(Collections.singletonList(GLORY_4), ids);
	}

	@Test
	public void onlyAPlaceholderOfAnotherVersionGivesNoExtraSlot()
	{
		// Found testing in game: the bank kept a placeholder of the old version after it was upgraded. The plugin no
		// longer passes placeholders as owned, so only the worn version is laid out
		List<Integer> ids = resolve(Collections.singletonList(-GLORY), null, Collections.singletonList(GLORY_4));
		assertEquals(Collections.singletonList(GLORY_4), ids);
		// Nothing owned at all: the old layout entry (or the fallback) still holds the slot
		ids = resolve(Collections.singletonList(-GLORY), new int[]{GLORY_1}, Collections.emptyList());
		assertEquals(Collections.singletonList(GLORY_1), ids);
	}

	@Test
	public void plainTagOfAnOwnedVersionStaysNextToTheOthers()
	{
		// Two real items: both shown
		List<Integer> ids = resolve(Arrays.asList(GLORY_1, -GLORY), null, Arrays.asList(GLORY_4, GLORY_1));
		assertEquals(2, ids.size());
		assertTrue(ids.containsAll(Arrays.asList(GLORY_4, GLORY_1)));
	}

	@Test
	public void plainTagWithoutAGroupTagIsKeptEvenIfNotOwned()
	{
		// No variation tag for the group: a plain tag is the player's own choice, keep it as a placeholder
		List<Integer> ids = resolve(Collections.singletonList(GLORY_1), null, Collections.singletonList(GLORY_4));
		assertEquals(Collections.singletonList(GLORY_1), ids);
	}

	@Test
	public void plainTagAlsoCoversItsGroupWhenNothingIsOwned()
	{
		// Helm tagged both ways, not owned right now (e.g. on a death pile): no ghost base id next to it
		List<Integer> ids = resolve(Arrays.asList(SLAYER_HELMET_I, -SLAYER_HELMET), null, Collections.emptyList());
		assertEquals(Collections.singletonList(SLAYER_HELMET_I), ids);
	}

	@Test
	public void everyOwnedVersionOfAGroupIsLaidOut()
	{
		List<Integer> ids = resolve(Collections.singletonList(-GLORY), null, Arrays.asList(GLORY_4, GLORY_1, WHIP));
		assertEquals(Arrays.asList(GLORY_4, GLORY_1), ids);
	}

	@Test
	public void untaggedItemsDropOutOfTheOldLayout()
	{
		int[] layout = {WHIP, -1, INFERNAL_CAPE};
		List<Integer> ids = resolve(Collections.singletonList(WHIP), layout, Collections.emptyList());
		assertEquals(Collections.singletonList(WHIP), ids);
	}

	@Test
	public void oldGhostBaseIdIsReplacedByTheOwnedVersion()
	{
		// An older version of the plugin wrote the broken cape into the layout
		int[] layout = {INFERNAL_CAPE_BROKEN};
		List<Integer> ids = resolve(Collections.singletonList(-INFERNAL_CAPE_BROKEN), layout,
			Collections.singletonList(INFERNAL_CAPE));
		assertEquals(Collections.singletonList(INFERNAL_CAPE), ids);
	}

	@Test
	public void oldLayoutEntryStandsInWhenNothingIsOwned()
	{
		int[] layout = {INFERNAL_CAPE, INFERNAL_CAPE_BROKEN};
		List<Integer> ids = resolve(Collections.singletonList(-INFERNAL_CAPE_BROKEN), layout, Collections.emptyList());
		assertEquals(Collections.singletonList(INFERNAL_CAPE), ids);
	}

	@Test
	public void fallbackOnlyWhenNothingElseIsKnown()
	{
		List<Integer> ids = resolve(Collections.singletonList(-INFERNAL_CAPE_BROKEN), null, Collections.emptyList());
		assertEquals(Collections.singletonList(INFERNAL_CAPE_BROKEN + 100000), ids);
	}

	@Test
	public void plainTagsAreCanonicalized()
	{
		List<Integer> ids = resolve(Collections.singletonList(NOTED_WHIP), null, Collections.emptyList());
		assertEquals(Collections.singletonList(WHIP), ids);
	}
}
