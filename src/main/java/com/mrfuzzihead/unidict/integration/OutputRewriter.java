package com.mrfuzzihead.unidict.integration;

/*
 * Shared, non-destructive machine-output rewriter for every kept integration (docs/PLAN.md §BB-3,
 * M6). A unified machine rewrite must only ever rebuild an output's item list and setValue it on the
 * recipe map — never remove a recipe, never mutate a global registry.
 * <p>Integrations differ only in how an output exposes its item list and how a new output is rebuilt
 * from the mapped list; that difference is captured by {@link OutputView}. This core is the single
 * T2-tested source for that logic (docs/PLAN.md §0 rule 2): Furnace rewrites single-stack outputs
 * ({@link #rewriteSingleOutputs} over {@link #SINGLE_ITEM_VIEW}); IC2 rebuilds
 * {@code ic2.api.recipe.RecipeOutput}s through its own view in {@link IC2Integration}.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

final class OutputRewriter {

    private OutputRewriter() {}

    /**
     * Adapts one machine's output holder type to the item list it stores. Not a functional interface
     * (two operations), so callers supply an anonymous-class/field adapter rather than a lambda.
     */
    interface OutputView<V> {

        /** @return the output's current item list (may be empty, never null) */
        List<ItemStack> getItems(V output);

        /** @return a new holder equivalent to {@code original} but with the mapped item list */
        V rebuild(V original, List<ItemStack> mapped);
    }

    /**
     * Non-destructive output rewrite (BB-3): maps each output's {@link ItemStack}s through
     * {@code resolveMain} (in production the resource handler's canonical lookup) and, only when at
     * least one actually changed, replaces the output via {@code view.rebuild} — never adding or
     * removing a recipe.
     *
     * @return number of outputs (recipe values) actually changed
     */
    static <K, V> int rewriteOutputs(final Map<K, V> recipes, final OutputView<V> view,
        final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final Map.Entry<K, V> recipe : recipes.entrySet()) {
            final V output = recipe.getValue();
            if (output == null) continue;
            final List<ItemStack> original = view.getItems(output);
            final List<ItemStack> mapped = new ArrayList<>(original.size());
            boolean changed = false;
            for (final ItemStack stack : original) {
                final ItemStack main = resolveMain.apply(stack);
                if (main != stack) changed = true;
                mapped.add(main);
            }
            // Only rebuild when something actually changed; never touch the map otherwise.
            if (changed) {
                recipe.setValue(view.rebuild(output, mapped));
                rewritten++;
            }
        }
        return rewritten;
    }

    /** Adapts single-stack machine outputs (e.g. vanilla furnace) to the list-based core. */
    static final OutputView<ItemStack> SINGLE_ITEM_VIEW = new OutputView<ItemStack>() {

        @Override
        public List<ItemStack> getItems(final ItemStack output) {
            final List<ItemStack> list = new ArrayList<>(1);
            list.add(output);
            return list;
        }

        @Override
        public ItemStack rebuild(final ItemStack original, final List<ItemStack> mapped) {
            return mapped.get(0);
        }
    };

    /** Convenience for machine outputs that hold exactly one {@link ItemStack}. */
    static <K> int rewriteSingleOutputs(final Map<K, ItemStack> recipes, final UnaryOperator<ItemStack> resolveMain) {
        return rewriteOutputs(recipes, SINGLE_ITEM_VIEW, resolveMain);
    }
}
