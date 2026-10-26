package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.enderio.FakeOreDictionaryPreferencesAccessor;
import com.mrfuzzihead.unidict.enderio.IOreDictionaryPreferencesAccessor;

/**
 * T2 test for the Ender IO integration. Two concerns:
 *
 * <p>
 * <b>Machine output rewrites</b> — {@link EIOIntegration#rewriteRecipes} is the package-private, generic
 * seam over the shared non-destructive {@link OutputRewriter#rewriteWeightedList} core; a neutral
 * {@link Holder} view stands in for Ender IO's immutable {@code IManyToOneRecipe}/{@code Recipe} types (not
 * on the JUnit test classpath — see the raw accessors). Pins the BB-3 guarantee (only outputs change, no
 * recipe added/removed, null entries untouched) and the BUG-16 guarantee: <b>every</b> output of a
 * multi-output recipe is remapped with its chance/experience preserved, so nothing is silently dropped.
 *
 * <p>
 * <b>Ore-dictionary preferences</b> — {@link EIOIntegration#fixOreDictPreferences} must clear the map so
 * Ender IO yields the canonical entry; driven through {@link FakeOreDictionaryPreferencesAccessor}, the
 * live {@code OreDictionaryPreferencesMixin} is exercised in-game (T3) and by the {@code seam=} self-check.
 *
 * <p>
 * The {@link Item}/{@link ItemStack} stand-ins touch MC <em>types</em> but no MC <em>statics</em> (T2).
 */
class EIOIntegrationTest {

    /** Neutral weighted-output holder so the generic seam is testable with no Ender IO classes. */
    private static final class Holder {

        final List<OutputRewriter.WeightedOutput> outputs;

        Holder(final List<OutputRewriter.WeightedOutput> outputs) {
            this.outputs = outputs;
        }
    }

    private static OutputRewriter.WeightedOutput weighted(final ItemStack stack, final float chance,
        final float experience) {
        return new OutputRewriter.WeightedOutput(stack, chance, experience);
    }

    private static final OutputRewriter.WeightedView<Holder> HOLDER_VIEW = new OutputRewriter.WeightedView<Holder>() {

        @Override
        public List<OutputRewriter.WeightedOutput> getOutputs(final Holder recipe) {
            return recipe.outputs;
        }

        @Override
        public Holder rebuild(final Holder original, final List<OutputRewriter.WeightedOutput> mapped) {
            return new Holder(new ArrayList<>(mapped));
        }
    };

    @Test
    void rewriteRecipesRemapsAlloyAndSagOutputsInPlacePreservingCount() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        final List<Holder> recipes = new ArrayList<>();
        recipes.add(new Holder(new ArrayList<>(Arrays.asList(weighted(outA, 1f, 0.1f)))));
        recipes.add(new Holder(new ArrayList<>(Arrays.asList(weighted(outA, 1f, 0.1f), weighted(outB, 0.5f, 0.3f)))));

        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> (s == outB) ? canonicalB : s;

        final int rewritten = EIOIntegration.rewriteRecipes(recipes, HOLDER_VIEW, resolveMain);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(outA, recipes.get(0).outputs.get(0).stack, "unchanged output keeps its identity");
        assertSame(outA, recipes.get(1).outputs.get(0).stack, "resolution to self leaves the entry untouched");
        assertSame(canonicalB, recipes.get(1).outputs.get(1).stack, "the mapped output is applied in place");
        assertEquals(0.5f, recipes.get(1).outputs.get(1).chance, "chance is preserved");
        assertEquals(0.3f, recipes.get(1).outputs.get(1).experience, "experience is preserved");
    }

    @Test
    void rewriteRecipesRemapsEveryOutputOfAMultiOutputRecipe() {
        // BUG-16: the old alloy view rebuilt a single-output recipe, silently dropping outputs[1..n].
        final Item item = new Item();
        final ItemStack first = new ItemStack(item, 1, 0);
        final ItemStack second = new ItemStack(item, 1, 1);
        final ItemStack third = new ItemStack(item, 1, 2);

        final List<Holder> recipes = new ArrayList<>();
        recipes.add(
            new Holder(
                new ArrayList<>(
                    Arrays.asList(
                        weighted(first, 1f, 0f),
                        weighted(second, 0.25f, 1.5f),
                        weighted(third, 0.75f, 2.5f)))));

        final ItemStack canonical = new ItemStack(item, 3, 4);
        final int rewritten = EIOIntegration.rewriteRecipes(recipes, HOLDER_VIEW, s -> canonical);

        assertEquals(1, rewritten);
        final List<OutputRewriter.WeightedOutput> outputs = recipes.get(0).outputs;
        assertEquals(3, outputs.size(), "no output may be lost");
        for (final OutputRewriter.WeightedOutput output : outputs) assertSame(canonical, output.stack);
        assertEquals(1f, outputs.get(0).chance);
        assertEquals(0.25f, outputs.get(1).chance);
        assertEquals(0.75f, outputs.get(2).chance);
        assertEquals(1.5f, outputs.get(1).experience);
        assertEquals(2.5f, outputs.get(2).experience);
    }

    @Test
    void rewriteRecipesLeavesNullEntriesUntouched() {
        final Item itemA = new Item();
        final Holder entry = new Holder(new ArrayList<>(Arrays.asList(weighted(new ItemStack(itemA, 1, 1), 1f, 0f))));
        final List<Holder> recipes = new ArrayList<>(Arrays.asList(entry, null));

        final int rewritten = EIOIntegration.rewriteRecipes(recipes, HOLDER_VIEW, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(2, recipes.size(), "a null entry must not be removed nor replaced");
        assertSame(entry, recipes.get(0), "unchanged entry keeps its identity");
        assertNull(recipes.get(1));
    }

    @Test
    void fixOreDictPreferencesClearsThePreferencesMap() {
        final Item itemA = new Item();
        final Map<String, ItemStack> preferences = new HashMap<>();
        preferences.put("ingotIron", new ItemStack(itemA, 1, 0));
        preferences.put("ingotGold", new ItemStack(itemA, 1, 0));
        final IOreDictionaryPreferencesAccessor accessor = new FakeOreDictionaryPreferencesAccessor(preferences);

        EIOIntegration.fixOreDictPreferences(accessor);

        assertTrue(preferences.isEmpty(), "Ender IO must yield the canonical entry, not the preference");
    }

    @Test
    void fixOreDictPreferencesToleratesANullMap() {
        // Some Ender IO builds may expose a null preferences map before load; clearing must be a no-op.
        EIOIntegration.fixOreDictPreferences(new FakeOreDictionaryPreferencesAccessor(null));
    }
}
