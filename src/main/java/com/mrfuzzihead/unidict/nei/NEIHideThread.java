package com.mrfuzzihead.unidict.nei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.helper.NEIHelper;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.pure.SelectionRules;
import com.mrfuzzihead.unidict.resource.Resource;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceContainer;
import com.mrfuzzihead.unidict.resource.UniResourceHandler;

/**
 * The NEI variant-hiding pass (TODO.md P0 #1). After resource selection it walks each unified
 * resource's ordered snapshot and hides every non-kept, non-blacklisted variant via the single
 * guarded {@link NEIHelper#hide} site. Hiding is driven by {@code autoHideInNEI} with three
 * exemptions (BB-3: entries are hidden, never removed from Forge's global OreDictionary):
 *
 * <ul>
 * <li><b>{@code hideInNEIBlackSet}</b> (per kind) — exempts a whole kind (e.g. {@code ore}).</li>
 * <li><b>{@code autoHideInNEIModBlackList}</b> (per owner mod) — exempts a specific mod's variants
 * from hiding.</li>
 * <li><b>{@code protectedOreDictionaryNames}</b> (e.g. {@code "raw"} for raw metals) — exempts
 * specific variants from hiding so they coexist with the canonical entry.</li>
 * <li><b>Canonical entries</b> — an item that is the main entry of <em>any</em> unified container is never
 * hidden, even when it is a non-main variant of another container (multi-tag items). Otherwise NEI would
 * hide something the machines actually hand the player (docs/CODE_REVIEW.md BUG-17).</li>
 * </ul>
 *
 * <p>
 * Upstream's separate {@code keepOneEntry} collapse is <b>deferred</b> as a stretch goal (TODO.md P0
 * #2) and is not wired here.
 *
 * <p>
 * Runs at POST_INIT (after {@code UniDict.postInit} published the {@code ResourceHandler}); the
 * module is only registered on a client with NotEnoughItems, so {@code API.hideItem} runs on the
 * client main thread (the historical crash source is calling it from worker threads).
 */
@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
final class NEIHideThread extends AbstractModuleThread {

    NEIHideThread() {
        super("NEI", "Hide");
    }

    @Override
    public String call() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        try {
            if (resourceHandler == null || resourceHandler.resources.isEmpty()) {
                if (VerifyHarness.isEnabled()) VerifyHarness.record(true, "integration=NEI", "hidden=0");
                return threadName + "no unified resources to hide.";
            }
            final boolean autoHideInNEI = Config.get().autoHideInNEI;
            final Set<Long> kindBlackSet = UniResourceHandler.getKindBlackSet();
            // Every canonical entry in the whole model: a variant must never be hidden if it is the main
            // entry of some other container (BUG-17).
            final List<ItemStack> canonicalEntries = new ArrayList<>();
            for (final Resource<UniResourceContainer> resource : resourceHandler.resources)
                for (final UniResourceContainer container : resource.getChildrenCollection())
                    canonicalEntries.add(container.getMainEntry());
            final Predicate<ItemStack> alwaysVisible = stack -> ResourceHandler.isProtected(stack)
                || isCanonicalElsewhere(canonicalEntries, stack);

            int hidden = 0;
            for (final Resource<UniResourceContainer> resource : resourceHandler.resources)
                for (final UniResourceContainer container : resource.getChildrenCollection()) {
                    final List<ItemStack> toHide = stacksToHide(
                        container.getEntries(),
                        container.kind,
                        autoHideInNEI,
                        kindBlackSet,
                        ResourceHandler::isModBlacklisted,
                        alwaysVisible);
                    for (final ItemStack stack : toHide) if (stack != null) NEIHelper.hide(stack);
                    hidden += toHide.size();
                }
            UniDict.LOG.info(threadName + "hid " + hidden + " non-main variants in NEI.");
            if (VerifyHarness.isEnabled()) VerifyHarness.record(true, "integration=NEI", "hidden=" + hidden);
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "gave NEI a cleaner view.";
    }

    /**
     * Whether {@code stack} is the canonical (main) entry of <b>any</b> unified container. Such a stack is
     * never hidden: hiding it would remove from NEI an item that machines/drops actually produce.
     *
     * <p>
     * Matches by item identity and damage (not stack identity — the entries are snapshots/copies, and the
     * candidate may be a different {@link ItemStack} instance for the same item). Package-private seam so
     * the decision is T2-testable without a game instance.
     *
     * @param canonicalEntries the main entry of every container in the model
     * @param stack            the variant being considered for hiding
     * @return {@code true} when the stack already is some container's canonical entry
     */
    static boolean isCanonicalElsewhere(final Collection<ItemStack> canonicalEntries, final ItemStack stack) {
        if (canonicalEntries == null || stack == null || stack.getItem() == null) return false;
        for (final ItemStack canonical : canonicalEntries) {
            if (canonical == null || canonical.getItem() == null) continue;
            if (canonical.getItem() == stack.getItem() && canonical.getItemDamage() == stack.getItemDamage())
                return true;
        }
        return false;
    }

    /**
     * The hide-decision seam (T2, TODO.md P0 #1 "hide-set builder fed by fakes"): returns the ordered
     * entry stacks that should be hidden, driven purely by {@link SelectionRules#hiddenIndices}. It
     * touches no MC statics, so a test can drive it with plain {@link ItemStack} fakes.
     *
     * @param entries        the container's ordered snapshot (index 0 is the canonical entry)
     * @param kind           the container's kind bit
     * @param autoHideInNEI  {@code Config.autoHideInNEI}
     * @param kindBlackSet   {@code hideInNEIBlackSet} as kind bits
     * @param modBlacklisted recognises an exempt {@code autoHideInNEIModBlackList} owner-mod entry
     * @param alwaysVisible  recognises an entry that must stay visible regardless — a protected item or a
     *                       canonical entry of another container (see {@link #isCanonicalElsewhere})
     */
    static List<ItemStack> stacksToHide(final List<ItemStack> entries, final long kind, final boolean autoHideInNEI,
        final Set<Long> kindBlackSet, final Predicate<? super ItemStack> modBlacklisted,
        final Predicate<? super ItemStack> alwaysVisible) {
        final List<ItemStack> toHide = new ArrayList<>();
        for (final int index : SelectionRules
            .hiddenIndices(entries, autoHideInNEI, kind, kindBlackSet, modBlacklisted, alwaysVisible))
            toHide.add(entries.get(index));
        return toHide;
    }
}
