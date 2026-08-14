package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt (and adapted) from wanion.unidict.integration.ChestIntegration (WanionCane, MPL-2.0) as the
 * M6 Chest / loot rewrite (docs/PLAN.md §M6 #5). Rewrites the ITEM of every Forge chest-loot entry
 * (the WeightedRandomChestContent the game can drop from dungeons, mineshafts, villages, strongholds,
 * bonus chests, ...) to the canonical (main) entry of its unified resource.
 * <p>Unlike the machine integrations we do not rebuild a recipe — each loot entry's item is rewritten
 * <b>in place</b> through the {@link com.mrfuzzihead.unidict.chest.IWeightedRandomChestContentAccessor}
 * seam: only the private {@code theItemId} reference changes, so the contents list keeps its exact
 * count and order and every entry keeps its weights/min/max — fully non-destructive (BB-3), faithful to
 * upstream which mutated {@code theItemId} directly. This is the "best-effort, minor" integration.
 * <p>Two accessor seams underpin it (interface + fake, rule 1): {@code ChestGenHooksMixin} (this plan's
 * first accessor: the static {@code chestInfo} registry + each instance's {@code contents}) and
 * {@code WeightedRandomChestContentMixin} (the {@code theItemId} field, which is {@code private} in
 * MC 1.7.10 — a 1.7.10-necessitated extension, see docs/STATUS.md). No reflection remains.
 */

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.chest.IChestGenHooksAccessor;
import com.mrfuzzihead.unidict.chest.IWeightedRandomChestContentAccessor;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

final class ChestIntegration extends AbstractModuleThread {

    ChestIntegration() {
        super("Chest", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: no unified resource -> the canonical lookup is a no-op.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.chest()) {
                int rewritten = 0;
                final Map<String, ChestGenHooks> chestInfo = getChestInfo();
                if (chestInfo != null) {
                    for (final ChestGenHooks category : chestInfo.values()) {
                        rewritten += rewriteCategory(
                            (IChestGenHooksAccessor) category,
                            resourceHandler::getMainItemStack);
                    }
                }
                UniDict.LOG.info(threadName + "rewrote " + rewritten + " chest loot entries to their canonical items.");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=Chest", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Now you can find things that aren't so useless in chests.";
    }

    /**
     * Reads Forge's static category registry ({@code chestInfo}) through the accessor seam. The mixin
     * realises {@link IChestGenHooksAccessor#getChestInfo()} as instance methods merged onto every
     * {@code ChestGenHooks}, so a throwaway instance (never registered; the plain {@code String} ctor
     * only sets the category) suffices to reach the static field. Mirrors the M3 {@code OreDictionaryBridge}.
     */
    static Map<String, ChestGenHooks> getChestInfo() {
        return ((IChestGenHooksAccessor) (Object) new ChestGenHooks("unidict$chorus")).getChestInfo();
    }

    /** Non-destructive rewrite of a single chest category's loot table, via {@link #rewriteContents}. */
    static int rewriteCategory(final IChestGenHooksAccessor category, final UnaryOperator<ItemStack> resolveMain) {
        final List<WeightedRandomChestContent> contents = category.getContents();
        return contents == null ? 0 : rewriteContents(contents, resolveMain);
    }

    /**
     * Rewrites every loot entry's item in place to its canonical (main) entry. Only the private
     * {@code theItemId} reference changes (through the accessor seam); the list is structurally
     * untouched — same count and order, entries keep their weights/min/max (BB-3).
     *
     * @return number of entries whose item actually changed
     */
    static int rewriteContents(final List<WeightedRandomChestContent> contents,
        final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final WeightedRandomChestContent content : contents) {
            // Guard: a content is only rewritable after the accessor mixin was applied to its class.
            if (content instanceof IWeightedRandomChestContentAccessor
                && rewriteContent((IWeightedRandomChestContentAccessor) content, resolveMain)) rewritten++;
        }
        return rewritten;
    }

    /**
     * Rewrites a single loot entry's item when it maps to a different canonical stack (BB-3
     * non-destructive: mutate the item reference in place, never remove/add the entry).
     *
     * @return {@code true} when the item was rewritten
     */
    static boolean rewriteContent(final IWeightedRandomChestContentAccessor content,
        final UnaryOperator<ItemStack> resolveMain) {
        final ItemStack original = content.getTheItemId();
        final ItemStack main = resolveMain.apply(original);
        if (main == original) return false;
        content.setTheItemId(main);
        return true;
    }
}
