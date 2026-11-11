package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.chest.IWeightedRandomChestContentAccessor;

/**
 * Small Mockito smoke test for the newly added test infrastructure. It exercises the same
 * {@code ChestIntegration.rewriteContent} seam as the hand-written fake, but with Mockito so future tests
 * can mock interfaces/final MC types without adding another fake class.
 */
class ChestIntegrationMockitoTest {

    @Test
    void mockitoAccessorRewritesLootItemInPlace() {
        final IWeightedRandomChestContentAccessor content = mock(IWeightedRandomChestContentAccessor.class);
        final Item item = new Item();
        final ItemStack original = new ItemStack(item, 3, 0);
        final ItemStack canonical = new ItemStack(item, 3, 1);

        when(content.getTheItemId()).thenReturn(original);

        assertTrue(ChestIntegration.rewriteContent(content, stack -> canonical));
        verify(content).setTheItemId(canonical);
    }

    @Test
    void mockitoAccessorIsUntouchedWhenResolverReturnsIdentity() {
        final IWeightedRandomChestContentAccessor content = mock(IWeightedRandomChestContentAccessor.class);
        final ItemStack original = new ItemStack(new Item(), 1, 0);

        when(content.getTheItemId()).thenReturn(original);

        assertFalse(ChestIntegration.rewriteContent(content, stack -> stack));
        verify(content, never()).setTheItemId(any(ItemStack.class));
    }
}
