package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.te.FakeFurnaceManagerAccessor;
import com.mrfuzzihead.unidict.te.FakePulverizerManagerAccessor;
import com.mrfuzzihead.unidict.te.FakeSmelterManagerAccessor;

/**
 * T2 test for the M7 Thermal Expansion integration (docs/PLAN.md §M7 #4). The three per-manager recipe
 * maps are reached through the accessor fakes ({@link FakeFurnaceManagerAccessor} &amp; co., the seams
 * realised by {@code FurnaceManagerMixin} &amp; co.) and driven through the package-private, generic
 * {@link TEIntegration#rewriteOutputs} seam over the shared non-destructive {@link OutputRewriter#rewriteOutputs}
 * core. A neutral {@link Holder} view stands in for TE's immutable {@code Recipe*} values (not on the
 * JUnit test classpath), asserting BB-3: only outputs change ({@code Map.setValue}), no recipe is added
 * or removed, {@code null} values and unchanged outputs are left alone.
 *
 * <p>
 * The {@link Item}/{@link ItemStack} stand-ins touch MC <em>types</em> but no MC <em>statics</em> (T2);
 * the real {@code FurnaceManager}/{@code PulverizerManager}/{@code SmelterManager} statics and the
 * invoked constructors are exercised in-game (T3).
 */
class TEIntegrationTest {

    /** Neutral output holder so the generic seam is testable with no TE classes. */
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void rewriteOutputsThroughFurnaceAccessorRemapsValuesInPlace() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        final Map<String, Holder> underlying = new HashMap<>();
        underlying.put("f1", new Holder(new ArrayList<>(Arrays.asList(outA))));
        underlying.put("f2", new Holder(new ArrayList<>(Arrays.asList(outB))));

        final FakeFurnaceManagerAccessor manager = new FakeFurnaceManagerAccessor(underlying);
        final Map<String, Holder> recipes = (Map<String, Holder>) manager.getRecipeMap();

        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final int rewritten = TEIntegration.rewriteOutputs(recipes, HOLDER_VIEW, s -> (s == outB) ? canonicalB : s);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(outA, recipes.get("f1").items.get(0), "unchanged output keeps its identity");
        assertSame(canonicalB, recipes.get("f2").items.get(0), "the mapped output is set in place");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void rewriteOutputsThroughPulverizerAndSmelterAccessorsPreserveNullValues() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        // Multi-output (pulverizer/smelter secondary) shape + a null value, from both other TE seams.
        final Map<String, Holder> pulvUnderlying = new HashMap<>();
        pulvUnderlying.put("p1", new Holder(new ArrayList<>(Arrays.asList(outA))));
        pulvUnderlying.put("p2", null);
        final Map<String, Holder> pulv = (Map<String, Holder>) new FakePulverizerManagerAccessor(pulvUnderlying)
            .getRecipeMap();

        final Map<String, Holder> smeltUnderlying = new HashMap<>();
        smeltUnderlying.put("s1", new Holder(new ArrayList<>(Arrays.asList(outB))));
        final Map<String, Holder> smelt = (Map<String, Holder>) new FakeSmelterManagerAccessor(smeltUnderlying)
            .getRecipeMap();

        // Identity resolver: nothing maps away, so nothing is rewritten, nulls are preserved.
        assertEquals(0, TEIntegration.rewriteOutputs(pulv, HOLDER_VIEW, UnaryOperator.identity()));
        assertEquals(2, pulv.size(), "a null recipe value must not be removed nor replaced");
        assertSame(outA, pulv.get("p1").items.get(0));
        assertNull(pulv.get("p2"));

        assertEquals(0, TEIntegration.rewriteOutputs(smelt, HOLDER_VIEW, UnaryOperator.identity()));
        assertEquals(1, smelt.size());
        assertSame(outB, smelt.get("s1").items.get(0));
    }
}
