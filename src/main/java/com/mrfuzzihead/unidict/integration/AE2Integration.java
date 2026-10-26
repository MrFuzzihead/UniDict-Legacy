package com.mrfuzzihead.unidict.integration;

import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import appeng.api.AEApi;
import appeng.api.features.IGrinderEntry;

@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
final class AE2Integration extends AbstractModuleThread {

    /**
     * Non-destructive output rewrite seam over a grinder recipe (interface + fake, docs/PLAN.md §0).
     * Exposes only the output accessors; a test fake and the {@link AE2GrinderRecipe} adapter share
     * this contract so the {@link #rewriteRecipe} logic is purely T2-testable without AE2 types.
     */
    interface GrinderRecipe {

        ItemStack getOutput();

        void setOutput(ItemStack output);

        /** @return the optional output stack, or {@code null} when absent */
        ItemStack getOptionalOutput();

        void setOptionalOutput(ItemStack output);

        /** @return the second optional output stack, or {@code null} when absent */
        ItemStack getSecondOptionalOutput();

        void setSecondOptionalOutput(ItemStack output);
    }

    /** Adapts a real {@link IGrinderEntry} to {@link GrinderRecipe}, preserving each optional chance. */
    private static final class AE2GrinderRecipe implements GrinderRecipe {

        private final IGrinderEntry delegate;

        AE2GrinderRecipe(final IGrinderEntry delegate) {
            this.delegate = delegate;
        }

        @Override
        public ItemStack getOutput() {
            return delegate.getOutput();
        }

        @Override
        public void setOutput(final ItemStack output) {
            delegate.setOutput(output);
        }

        @Override
        public ItemStack getOptionalOutput() {
            return delegate.getOptionalOutput();
        }

        @Override
        public void setOptionalOutput(final ItemStack output) {
            delegate.setOptionalOutput(output, delegate.getOptionalChance());
        }

        @Override
        public ItemStack getSecondOptionalOutput() {
            return delegate.getSecondOptionalOutput();
        }

        @Override
        public void setSecondOptionalOutput(final ItemStack output) {
            delegate.setSecondOptionalOutput(output, delegate.getSecondOptionalChance());
        }
    }

    AE2Integration() {
        super("AE2", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: no unified resource -> the canonical lookup is a no-op.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.ae2()) {
                final List<IGrinderEntry> recipes = AEApi.instance()
                    .registries()
                    .grinder()
                    .getRecipes();
                int rewritten = 0;
                for (final IGrinderEntry entry : recipes) {
                    if (rewriteRecipe(new AE2GrinderRecipe(entry), resourceHandler::getMainItemStack)) rewritten++;
                }
                UniDict.LOG.info(
                    threadName + "rewrote outputs of "
                        + rewritten
                        + " AE2 grinder recipes to their canonical entries.");
                RewriteJournal.record("ae2", "grinder", rewritten);
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=ae2", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "The world of energistics has never been so powerful.";
    }

    /**
     * Non-destructive output rewrite (T2 seam): maps a grinder recipe's primary + optional outputs
     * through {@code resolveMain} (in production the resource handler's canonical lookup) and
     * {@code setOutput} only when something actually changed. Never removes the recipe and never
     * rewrites its input (BB-3 / M5).
     *
     * @return {@code true} if at least one output was rewritten
     */
    static boolean rewriteRecipe(final GrinderRecipe recipe, final UnaryOperator<ItemStack> resolveMain) {
        boolean changed = false;

        final ItemStack output = resolveMain.apply(recipe.getOutput());
        if (output != recipe.getOutput()) {
            recipe.setOutput(output);
            changed = true;
        }

        final ItemStack optional = recipe.getOptionalOutput();
        if (optional != null) {
            final ItemStack mainOptional = resolveMain.apply(optional);
            if (mainOptional != optional) {
                recipe.setOptionalOutput(mainOptional);
                changed = true;
            }
        }

        final ItemStack secondOptional = recipe.getSecondOptionalOutput();
        if (secondOptional != null) {
            final ItemStack mainSecond = resolveMain.apply(secondOptional);
            if (mainSecond != secondOptional) {
                recipe.setSecondOptionalOutput(mainSecond);
                changed = true;
            }
        }

        return changed;
    }
}
