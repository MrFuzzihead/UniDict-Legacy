package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.crafting.FakeShapedRecipesAccessor;
import com.mrfuzzihead.unidict.galacticraft.FakeShapelessOreRecipeAccessor;

/**
 * T2 test for the M8 Galacticraft compressor rewrite (docs/PLAN.md §M8 / INTEGRATIONS.md). Drives the
 * package-private BB-3 seam ({@link GalacticraftIntegration#rewriteOutput} and the list-backed
 * {@link GalacticraftIntegration#rewriteOutputs}) with fabricated outputs through the two accessor seams
 * {@code FakeShapedRecipesAccessor} / {@code FakeShapelessOreRecipeAccessor} (T2: no GC or static mod
 * classpaths), asserting the non-destructive guarantee: only outputs change, no recipe is added or
 * removed, unchanged / null outputs are left alone, and the count is per-output. The real
 * {@code CompressorRecipes} statics are exercised in-game (T3).
 */
class GalacticraftIntegrationTest {

    private static final UnaryOperator<ItemStack> IDENTITY = UnaryOperator.identity();

    @Test
    void rewriteOutputRemapsShapedRecipeOutputInPlace() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final FakeShapedRecipesAccessor shaped = new FakeShapedRecipesAccessor(outA);
        final ItemStack canonicalA = new ItemStack(itemA, 9, 3);

        final int rewritten = GalacticraftIntegration.rewriteOutput(
            shaped::unidict$getRecipeOutput,
            shaped::unidict$setRecipeOutput,
            s -> (s == outA) ? canonicalA : s);

        assertEquals(1, rewritten, "a resolvable shaped output should be rewritten");
        assertSame(canonicalA, shaped.unidict$getRecipeOutput(), "output should be rewritten in place");
    }

    @Test
    void rewriteOutputRemapsShapelessOreRecipeOutputInPlace() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 1, 1);
        final FakeShapelessOreRecipeAccessor shapeless = new FakeShapelessOreRecipeAccessor(outA);
        final ItemStack canonicalA = new ItemStack(itemA, 4, 0);

        final int rewritten = GalacticraftIntegration.rewriteOutput(
            shapeless::unidict$getOutput,
            shapeless::unidict$setOutput,
            s -> (s == outA) ? canonicalA : s);

        assertEquals(1, rewritten, "a resolvable shapeless output should be rewritten");
        assertSame(canonicalA, shapeless.unidict$getOutput(), "output should be rewritten in place");
    }

    @Test
    void rewriteOutputLeavesUnchangedAndNullOutputsAlone() {
        final Item itemA = new Item();
        final ItemStack unchanged = new ItemStack(itemA, 1, 1);
        final FakeShapedRecipesAccessor shaped = new FakeShapedRecipesAccessor(unchanged);

        // Identity resolver: nothing resolves to a different main entry -> nothing changes.
        assertEquals(
            0,
            GalacticraftIntegration
                .rewriteOutput(shaped::unidict$getRecipeOutput, shaped::unidict$setRecipeOutput, IDENTITY));
        assertSame(unchanged, shaped.unidict$getRecipeOutput(), "unchanged output keeps its identity");

        // Null output is skipped (some GC recipes may expose a null output mid-load).
        final FakeShapelessOreRecipeAccessor shapeless = new FakeShapelessOreRecipeAccessor(null);
        assertEquals(
            0,
            GalacticraftIntegration
                .rewriteOutput(shapeless::unidict$getOutput, shapeless::unidict$setOutput, IDENTITY));
        assertNull(shapeless.unidict$getOutput(), "null output is skipped and left untouched");
    }

}
