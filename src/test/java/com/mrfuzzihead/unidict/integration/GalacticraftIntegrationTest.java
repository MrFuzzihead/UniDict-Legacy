package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        final int rewritten = GalacticraftIntegration
            .rewriteOutput(shaped::getRecipeOutput, shaped::setRecipeOutput, s -> (s == outA) ? canonicalA : s);

        assertEquals(1, rewritten, "a resolvable shaped output should be rewritten");
        assertSame(canonicalA, shaped.getRecipeOutput(), "output should be rewritten in place");
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
            GalacticraftIntegration.rewriteOutput(shaped::getRecipeOutput, shaped::setRecipeOutput, IDENTITY));
        assertSame(unchanged, shaped.getRecipeOutput(), "unchanged output keeps its identity");

        // Null output is skipped (some GC recipes may expose a null output mid-load).
        final FakeShapelessOreRecipeAccessor shapeless = new FakeShapelessOreRecipeAccessor(null);
        assertEquals(
            0,
            GalacticraftIntegration
                .rewriteOutput(shapeless::unidict$getOutput, shapeless::unidict$setOutput, IDENTITY));
        assertNull(shapeless.unidict$getOutput(), "null output is skipped and left untouched");
    }

    @Test
    void rewriteOutputsPreservesListAndCountsPerOutput() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);
        final ItemStack canonicalB = new ItemStack(itemB, 9, 9);

        final List<Object> recipes = new ArrayList<>(
            Arrays.asList(new FakeShapedRecipesAccessor(outA), new FakeShapelessOreRecipeAccessor(outB)));

        final GalacticraftIntegration.AccessorPairFactory adapter = recipe -> {
            if (recipe instanceof FakeShapedRecipesAccessor shaped) {
                return new GalacticraftIntegration.AccessorPair(shaped::getRecipeOutput, shaped::setRecipeOutput);
            }
            if (recipe instanceof FakeShapelessOreRecipeAccessor shapeless) {
                return new GalacticraftIntegration.AccessorPair(
                    shapeless::unidict$getOutput,
                    shapeless::unidict$setOutput);
            }
            return null;
        };

        final int rewritten = GalacticraftIntegration
            .rewriteOutputs(recipes, adapter, s -> (s == outB) ? canonicalB : s);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting a list must never change its size (no remove)");
        assertSame(
            outA,
            ((FakeShapedRecipesAccessor) recipes.get(0)).getRecipeOutput(),
            "unchanged output keeps identity");
        assertSame(
            canonicalB,
            ((FakeShapelessOreRecipeAccessor) recipes.get(1)).unidict$getOutput(),
            "mapped output applied in place");
    }

    @Test
    void rewriteOutputsSkipsUnknownTypes() {
        final Item itemA = new Item();
        final Object unknown = new Object();
        final ItemStack outA = new ItemStack(itemA, 1, 1);

        final List<Object> recipes = new ArrayList<>(Arrays.asList(new FakeShapedRecipesAccessor(outA), unknown));

        // Adapter returns null for the unknown element -> it is skipped, never a crash.
        final GalacticraftIntegration.AccessorPairFactory adapter = recipe -> (recipe instanceof FakeShapedRecipesAccessor shaped)
            ? new GalacticraftIntegration.AccessorPair(shaped::getRecipeOutput, shaped::setRecipeOutput)
            : null;

        assertEquals(0, GalacticraftIntegration.rewriteOutputs(recipes, adapter, IDENTITY));
        assertEquals(2, recipes.size(), "unknown recipe types must be skipped, not removed");
    }
}
