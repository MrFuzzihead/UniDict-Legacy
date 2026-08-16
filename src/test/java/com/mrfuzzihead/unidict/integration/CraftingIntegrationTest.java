package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.crafting.FakeShapedRecipesAccessor;
import com.mrfuzzihead.unidict.crafting.FakeShapelessRecipesAccessor;
import com.mrfuzzihead.unidict.forestry.IShapedOreRecipeAccessor;
import com.mrfuzzihead.unidict.galacticraft.IShapelessOreRecipeAccessor;
import com.mrfuzzihead.unidict.ic2.IAdvRecipeAccessor;
import com.mrfuzzihead.unidict.ic2.IAdvShapelessRecipeAccessor;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;

/**
 * T2 test for the M5 crafting output rewrite.
 * Fakes drive CraftingIntegration.rewriteCraftingOutputs through
 * its resolver seam -- asserting the BB-3 non-destructive guarantee:
 * only each recipe's output reference changes, count is preserved.
 *
 * Item/ItemStack stand-ins touch MC types but no MC statics (T2).
 */
class CraftingIntegrationTest {

    @Test
    void craftingRewriteIsPinnedToLoadComplete() {
        // Regression guard (TODO.md / the "9 TF nuggets -> railcraft ingot" report): the crafting
        // output rewrite must run at LOAD_COMPLETE (not POST_INIT) so it sees recipe-managers after
        // every mod's postInit + late scripts. A POST_INIT pass rewrites an incomplete list.
        final SpecifiedLoadStage annotation = CraftingIntegration.class.getAnnotation(SpecifiedLoadStage.class);
        assertTrue(annotation != null, "CraftingIntegration must declare a @SpecifiedLoadStage");
        assertEquals(LoadStage.LOAD_COMPLETE, annotation.stage());
    }

    @Test
    void shapedRecipesAreRewrittenNonDestructively() {
        final Item itemA = new Item();
        final ItemStack outA = new ItemStack(itemA, 1, 1);
        final ItemStack outB = new ItemStack(itemA, 1, 2);
        final FakeShapedRecipesAccessor r1 = new FakeShapedRecipesAccessor(outA);
        final FakeShapedRecipesAccessor r2 = new FakeShapedRecipesAccessor(outB);
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(r1);
        recipes.add(r2);

        final ItemStack canonical = new ItemStack(itemA, 9, 3);
        final UnaryOperator<ItemStack> resolve = s -> (s == outA) ? canonical : s;

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, resolve);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(canonical, r1.getRecipeOutput());
        assertSame(outB, r2.getRecipeOutput());
    }

    @Test
    void shapelessRecipesAreRewrittenNonDestructively() {
        final Item itemA = new Item();
        final ItemStack outA = new ItemStack(itemA, 1, 1);
        final ItemStack outB = new ItemStack(itemA, 1, 2);
        final FakeShapelessRecipesAccessor r1 = new FakeShapelessRecipesAccessor(outA);
        final FakeShapelessRecipesAccessor r2 = new FakeShapelessRecipesAccessor(outB);
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(r1);
        recipes.add(r2);

        final ItemStack canonical = new ItemStack(itemA, 9, 3);
        final UnaryOperator<ItemStack> resolve = s -> (s == outB) ? canonical : s;

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, resolve);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(outA, r1.getRecipeOutput());
        assertSame(canonical, r2.getRecipeOutput());
    }

    @Test
    void unresolvedRecipesAreLeftAlone() {
        final Item itemA = new Item();
        final ItemStack out = new ItemStack(itemA, 1, 1);
        final FakeShapedRecipesAccessor recipe = new FakeShapedRecipesAccessor(out);
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(recipe);

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(1, recipes.size());
        assertSame(out, recipe.getRecipeOutput());
    }

    @Test
    void nullRecipesAreSkipped() {
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(null);

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(1, recipes.size());
    }

    @Test
    void forgeShapedOreRecipesAreRewrittenNonDestructively() {
        // GTNH 1.7.10 ShapedOreRecipe implements IRecipe directly (not ShapedRecipes); Forestry's
        // ShapedRecipeCustom extends it. The rewrite must canonicalize these via IShapedOreRecipeAccessor
        // (realised on them by ShapedOreRecipeMixin) — this was the railcraft-copper/forestry-bronze gap.
        final Item itemA = new Item();
        final ItemStack out = new ItemStack(itemA, 1, 1);
        final RecipeIRecipeAndShapedOreAccessor fake = new RecipeIRecipeAndShapedOreAccessor(out);
        final List<IRecipe> recipes = new ArrayList<>();
        recipes.add(fake);

        final ItemStack canonical = new ItemStack(itemA, 9, 3);
        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, s -> (s == out) ? canonical : s);

        assertEquals(1, rewritten);
        assertEquals(1, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(canonical, fake.unidict$getOutput());
    }

    @Test
    void forgeShapelessOreRecipesAreRewrittenNonDestructively() {
        final Item itemA = new Item();
        final ItemStack out = new ItemStack(itemA, 1, 1);
        final RecipeIRecipeAndShapelessOreAccessor fake = new RecipeIRecipeAndShapelessOreAccessor(out);
        final List<IRecipe> recipes = new ArrayList<>();
        recipes.add(fake);

        final ItemStack canonical = new ItemStack(itemA, 9, 3);
        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, s -> (s == out) ? canonical : s);

        assertEquals(1, rewritten);
        assertEquals(1, recipes.size());
        assertSame(canonical, fake.unidict$getOutput());
    }

    /** Inline IRecipe + {@link IShapedOreRecipeAccessor} fake (no MC statics) for the Forge shaped path. */
    private static final class RecipeIRecipeAndShapedOreAccessor implements IRecipe, IShapedOreRecipeAccessor {

        private ItemStack output;

        RecipeIRecipeAndShapedOreAccessor(final ItemStack output) {
            this.output = output;
        }

        @Override
        public boolean matches(final InventoryCrafting crafting, final World world) {
            return false;
        }

        @Override
        public ItemStack getCraftingResult(final InventoryCrafting crafting) {
            return output;
        }

        @Override
        public int getRecipeSize() {
            return 9;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }

        @Override
        public ItemStack unidict$getOutput() {
            return output;
        }

        @Override
        public void unidict$setOutput(final ItemStack output) {
            this.output = output;
        }
    }

    /** Inline IRecipe + {@link IShapelessOreRecipeAccessor} fake (no MC statics) for the Forge shapeless path. */
    private static final class RecipeIRecipeAndShapelessOreAccessor implements IRecipe, IShapelessOreRecipeAccessor {

        private ItemStack output;

        RecipeIRecipeAndShapelessOreAccessor(final ItemStack output) {
            this.output = output;
        }

        @Override
        public boolean matches(final InventoryCrafting crafting, final World world) {
            return false;
        }

        @Override
        public ItemStack getCraftingResult(final InventoryCrafting crafting) {
            return output;
        }

        @Override
        public int getRecipeSize() {
            return 9;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }

        @Override
        public ItemStack unidict$getOutput() {
            return output;
        }

        @Override
        public void unidict$setOutput(final ItemStack output) {
            this.output = output;
        }
    }

    @Test
    void ic2AdvShapelessRecipesAreRewrittenNonDestructively() {
        // IC2's free-form shapeless recipes (e.g. tiny-copper-dust -> copper-dust) use ic2.core.AdvShapelessRecipe
        // whose output field is public/non-final; the mutator canonicalises them via IAdvShapelessRecipeAccessor.
        final Item itemA = new Item();
        final ItemStack out = new ItemStack(itemA, 1, 0);
        final RecipeIRecipeAnIc2ShapelessOreAccessor fake = new RecipeIRecipeAnIc2ShapelessOreAccessor(out);
        final List<IRecipe> recipes = new ArrayList<>();
        recipes.add(fake);

        final ItemStack canonical = new ItemStack(itemA, 1, 41);
        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, s -> (s == out) ? canonical : s);

        assertEquals(1, rewritten);
        assertEquals(1, recipes.size());
        assertSame(canonical, fake.unidict$getOutput());
    }

    /**
     * Inline IRecipe + {@link IAdvShapelessRecipeAccessor} fake (no MC statics) for the IC2 shapeless path.
     */
    private static final class RecipeIRecipeAnIc2ShapelessOreAccessor implements IRecipe, IAdvShapelessRecipeAccessor {

        private ItemStack output;

        RecipeIRecipeAnIc2ShapelessOreAccessor(final ItemStack output) {
            this.output = output;
        }

        @Override
        public boolean matches(final InventoryCrafting crafting, final World world) {
            return false;
        }

        @Override
        public ItemStack getCraftingResult(final InventoryCrafting crafting) {
            return output;
        }

        @Override
        public int getRecipeSize() {
            return 9;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }

        @Override
        public ItemStack unidict$getOutput() {
            return output;
        }

        @Override
        public void unidict$setOutput(final ItemStack output) {
            this.output = output;
        }
    }

    @Test
    void ic2AdvRecipesAreRewrittenNonDestructively() {
        // IC2's SHAPED recipes (e.g. 9 tiny copper dust -> copper dust) use ic2.core.AdvRecipe whose
        // output field is public/final; the mutator exposes it via IAdvRecipeAccessor (warning: the
        // accessor MUTATES the final field from inside the declaring class at runtime).
        final Item itemA = new Item();
        final ItemStack out = new ItemStack(itemA, 1, 0);
        final RecipeIRecipeAndIc2ShapedAccessor fake = new RecipeIRecipeAndIc2ShapedAccessor(out);
        final List<IRecipe> recipes = new ArrayList<>();
        recipes.add(fake);

        final ItemStack canonical = new ItemStack(itemA, 1, 41);
        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, s -> (s == out) ? canonical : s);

        assertEquals(1, rewritten);
        assertEquals(1, recipes.size());
        assertSame(canonical, fake.unidict$getOutput());
        assertSame(canonical, fake.getRecipeOutput());
    }

    /** Inline IRecipe + {@link IAdvRecipeAccessor} fake (no MC statics) for the IC2 shaped path. */
    private static final class RecipeIRecipeAndIc2ShapedAccessor implements IRecipe, IAdvRecipeAccessor {

        private ItemStack output;

        RecipeIRecipeAndIc2ShapedAccessor(final ItemStack output) {
            this.output = output;
        }

        @Override
        public boolean matches(final InventoryCrafting crafting, final World world) {
            return false;
        }

        @Override
        public ItemStack getCraftingResult(final InventoryCrafting crafting) {
            return output;
        }

        @Override
        public int getRecipeSize() {
            return 9;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }

        @Override
        public ItemStack unidict$getOutput() {
            return output;
        }

        @Override
        public void unidict$setOutput(final ItemStack output) {
            this.output = output;
        }
    }
}
