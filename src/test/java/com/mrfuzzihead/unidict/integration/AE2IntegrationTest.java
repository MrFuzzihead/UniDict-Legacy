package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

/**
 * T2 test for the M6 AE2 grinder-output rewrite (docs/PLAN.md §M6 #2). A fake {@link
 * AE2Integration.GrinderRecipe} (the interface+fake seam — no AE2 types on the test classpath) drives
 * {@link AE2Integration#rewriteRecipe} through its resolver, asserting the BB-3 non-destructive
 * guarantee: only the primary/optional output stacks change, the recipe is never removed, and the
 * input is never touched. Absent (null) optional outputs are left alone.
 */
class AE2IntegrationTest {

    /** Simple in-memory {@link AE2Integration.GrinderRecipe} fake. */
    private static final class FakeGrinderRecipe implements AE2Integration.GrinderRecipe {

        ItemStack output;
        ItemStack optional;
        ItemStack secondOptional;

        FakeGrinderRecipe(final ItemStack output, final ItemStack optional, final ItemStack secondOptional) {
            this.output = output;
            this.optional = optional;
            this.secondOptional = secondOptional;
        }

        @Override
        public ItemStack getOutput() {
            return output;
        }

        @Override
        public void setOutput(final ItemStack output) {
            this.output = output;
        }

        @Override
        public ItemStack getOptionalOutput() {
            return optional;
        }

        @Override
        public void setOptionalOutput(final ItemStack output) {
            this.optional = output;
        }

        @Override
        public ItemStack getSecondOptionalOutput() {
            return secondOptional;
        }

        @Override
        public void setSecondOptionalOutput(final ItemStack output) {
            this.secondOptional = output;
        }
    }

    @Test
    void rewriteRecipeMapsPrimaryAndOptionalOutputsNonDestructively() {
        final Item itemA = new Item();
        final ItemStack output = new ItemStack(itemA, 1, 1);
        final ItemStack optional = new ItemStack(itemA, 1, 2);
        final ItemStack secondOptional = new ItemStack(itemA, 1, 3);

        final FakeGrinderRecipe recipe = new FakeGrinderRecipe(output, optional, secondOptional);

        final ItemStack canonicalOutput = new ItemStack(itemA, 9, 1);
        final ItemStack canonicalOptional = new ItemStack(itemA, 9, 2);
        final ItemStack canonicalSecond = new ItemStack(itemA, 9, 3);
        final UnaryOperator<ItemStack> resolve = s -> {
            if (s == output) return canonicalOutput;
            if (s == optional) return canonicalOptional;
            if (s == secondOptional) return canonicalSecond;
            return s;
        };

        final boolean changed = AE2Integration.rewriteRecipe(recipe, resolve);

        assertTrue(changed);
        assertSame(canonicalOutput, recipe.output);
        assertSame(canonicalOptional, recipe.optional);
        assertSame(canonicalSecond, recipe.secondOptional);
    }

    @Test
    void rewriteRecipeMapsPrimaryOnlyWhenOptionalIsNull() {
        final Item itemA = new Item();
        final ItemStack output = new ItemStack(itemA, 1, 1);

        final FakeGrinderRecipe recipe = new FakeGrinderRecipe(output, null, null);

        final ItemStack canonicalOutput = new ItemStack(itemA, 9, 1);
        final boolean changed = AE2Integration.rewriteRecipe(recipe, s -> (s == output) ? canonicalOutput : s);

        assertTrue(changed);
        assertSame(canonicalOutput, recipe.output);
        assertNull(recipe.optional);
        assertNull(recipe.secondOptional);
    }

    @Test
    void rewriteRecipeLeavesUnchangedRecipeAlone() {
        final Item itemA = new Item();
        final ItemStack output = new ItemStack(itemA, 1, 1);
        final ItemStack optional = new ItemStack(itemA, 1, 2);

        final FakeGrinderRecipe recipe = new FakeGrinderRecipe(output, optional, null);

        // Identity resolver: every stack "resolves" to itself -> nothing changes.
        final boolean changed = AE2Integration.rewriteRecipe(recipe, UnaryOperator.identity());

        assertFalse(changed);
        assertSame(output, recipe.output);
        assertSame(optional, recipe.optional);
        assertNull(recipe.secondOptional);
    }
}
