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
 * T2 test for the M7 Ender IO integration (docs/PLAN.md §M7 #1). Two concerns:
 *
 * <p>
 * <b>Machine output rewrites</b> — {@link EIOIntegration#rewriteRecipes} is the package-private,
 * generic seam over the shared non-destructive {@link OutputRewriter#rewriteList} core; a neutral
 * {@link Holder} view stands in for Ender IO's immutable {@code IManyToOneRecipe}/{@code Recipe} types
 * (not on the JUnit test classpath — see the raw accessors), asserting the BB-3 guarantee: only outputs
 * change, no recipe is added or removed, {@code null} entries and unchanged outputs are left alone.
 *
 * <p>
 * <b>Ore-dictionary preferences</b> — {@link EIOIntegration#fixOreDictPreferences} must clear the map so
 * Ender IO yields the canonical entry; driven through {@link FakeOreDictionaryPreferencesAccessor}, the
 * live {@code OreDictionaryPreferencesMixin} is exercised in-game (T3).
 *
 * <p>
 * The {@link Item}/{@link ItemStack} stand-ins touch MC <em>types</em> but no MC <em>statics</em> (T2).
 */
class EIOIntegrationTest {

    /** Neutral output holder so the generic seam is testable with no Ender IO classes. */
    private static final class Holder {

        final List<ItemStack> items;

        Holder(final List<ItemStack> items) {
            this.items = items;
        }
    }

    private static final OutputRewriter.OutputView<Holder> HOLDER_VIEW = new OutputRewriter.OutputView<Holder>() {

        @Override
        public List<ItemStack> getItems(final Holder output) {
            return output.items;
        }

        @Override
        public Holder rebuild(final Holder original, final List<ItemStack> mapped) {
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
        recipes.add(new Holder(new ArrayList<>(Arrays.asList(outA))));
        recipes.add(new Holder(new ArrayList<>(Arrays.asList(outA, outB))));

        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> (s == outB) ? canonicalB : s;

        final int rewritten = EIOIntegration.rewriteRecipes(recipes, HOLDER_VIEW, resolveMain);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(outA, recipes.get(0).items.get(0), "unchanged output keeps its identity");
        assertSame(outA, recipes.get(1).items.get(0), "resolution to self leaves the entry untouched");
        assertSame(canonicalB, recipes.get(1).items.get(1), "the mapped output is applied in place");
    }

    @Test
    void rewriteRecipesLeavesNullEntriesUntouched() {
        final Item itemA = new Item();
        final Holder entry = new Holder(new ArrayList<>(Arrays.asList(new ItemStack(itemA, 1, 1))));
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
