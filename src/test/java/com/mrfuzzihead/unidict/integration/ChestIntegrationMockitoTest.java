package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.chest.IChestGenHooksAccessor;

/**
 * Mockito smoke test for the test infrastructure and the chest category seam. The real 1.7.10
 * {@code WeightedRandomChestContent.theItemId} field is public, so only the category accessor needs a
 * fake/mock; the loot entry itself is a real object.
 */
class ChestIntegrationMockitoTest {

    @Test
    void mockitoCategorySeamRewritesLootItemInPlace() {
        final IChestGenHooksAccessor category = mock(IChestGenHooksAccessor.class);
        final Item item = new Item();
        final ItemStack original = new ItemStack(item, 3, 0);
        final ItemStack canonical = new ItemStack(item, 3, 1);
        final WeightedRandomChestContent content = new WeightedRandomChestContent(original, 0, 0, 1);
        final List<WeightedRandomChestContent> contents = new ArrayList<>();
        contents.add(content);

        when(category.getContents()).thenReturn(contents);

        assertEquals(1, ChestIntegration.rewriteCategory(category, stack -> canonical));
        assertSame(canonical, content.theItemId);
        verify(category).getContents();
    }

    @Test
    void mockitoCategorySeamLeavesIdentityResolutionUntouched() {
        final IChestGenHooksAccessor category = mock(IChestGenHooksAccessor.class);
        final ItemStack original = new ItemStack(new Item(), 1, 0);
        final WeightedRandomChestContent content = new WeightedRandomChestContent(original, 0, 0, 1);
        final List<WeightedRandomChestContent> contents = new ArrayList<>();
        contents.add(content);

        when(category.getContents()).thenReturn(contents);

        assertEquals(0, ChestIntegration.rewriteCategory(category, stack -> stack));
        assertSame(original, content.theItemId);
        verify(category).getContents();
    }
}
