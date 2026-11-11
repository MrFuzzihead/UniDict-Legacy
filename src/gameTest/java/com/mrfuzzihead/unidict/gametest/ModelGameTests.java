package com.mrfuzzihead.unidict.gametest;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.oredict.OreDictionary;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.report.UniDictReport;
import com.mrfuzzihead.unidict.resource.Resource;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceContainer;

/**
 * Model assertions that work on both a dedicated server and a client at load-complete. These are the
 * in-game equivalent of the headless verify harness's resource/report checks, plus a live canonical
 * sample that the server-world tests reuse.
 */
final class ModelGameTests {

    /** A canonical stack chosen from the live model, or {@code null} if no container exists. */
    static ItemStack canonicalSample;

    /** A different (non-protected) variant in the same container, or {@code null}. */
    static ItemStack nonCanonicalSample;

    private ModelGameTests() {}

    static void runAll() {
        final ResourceHandler handler = UniDict.resourceHandler;
        GameTestAssert.check("resource handler published", handler != null);
        if (handler == null) return;

        GameTestAssert.check("resource model is non-empty", !handler.resources.isEmpty());
        GameTestAssert.check(
            "dictionary snapshot captured",
            handler.getDictionarySnapshot() != null && !handler.getDictionarySnapshot()
                .isEmpty());
        GameTestAssert.check("Ore Dictionary is populated", OreDictionary.getOreNames().length > 0);
        GameTestAssert.check("configured metal set is non-empty", !Config.get().metalsToUnify.isEmpty());
        GameTestAssert.check("configured kind set is non-empty", !Config.get().childrenOfMetals.isEmpty());

        final List<String> lines = UniDictReport.lines(handler);
        GameTestAssert.check("transparency report rendered", !lines.isEmpty());
        GameTestAssert.check(
            "transparency report has a summary",
            lines.stream()
                .anyMatch(line -> line.startsWith("report summary=")));
        GameTestAssert.check(
            "transparency report has a resource line",
            lines.stream()
                .anyMatch(line -> line.startsWith("report resource=")));
        GameTestAssert.check(
            "transparency report has a rewrite line",
            lines.stream()
                .anyMatch(line -> line.startsWith("report rewrite=")));
        GameTestAssert.check(
            "crafting recipe list is populated",
            !CraftingManager.getInstance()
                .getRecipeList()
                .isEmpty());
        GameTestAssert.check(
            "furnace recipe map is populated",
            !FurnaceRecipes.smelting()
                .getSmeltingList()
                .isEmpty());
        GameTestAssert.check(
            "crafting unification gate is active",
            Config.get()
                .craftingUnificationEnabled());

        findCanonicalSample(handler);
        GameTestAssert.check("canonical/non-canonical sample found", nonCanonicalSample != null);
        if (nonCanonicalSample == null) return;

        final ItemStack resolved = handler.getMainItemStack(nonCanonicalSample);
        GameTestAssert
            .check("non-canonical variant resolves to the canonical entry", sameItem(resolved, canonicalSample));
        GameTestAssert.check(
            "canonical sample resolves by identity",
            handler.getMainItemStack(canonicalSample) == canonicalSample);
    }

    /**
     * Picks the first non-protected variant that differs from its container's main entry. The sample is
     * copied so later tests cannot mutate the model's snapshot.
     */
    private static void findCanonicalSample(final ResourceHandler handler) {
        canonicalSample = null;
        nonCanonicalSample = null;
        for (final Resource<UniResourceContainer> resource : handler.resources) {
            for (final UniResourceContainer container : resource.getChildrenCollection()) {
                final ItemStack main = container.getMainEntry();
                for (final ItemStack entry : container.getEntries()) {
                    if (entry == null || entry.getItem() == null || ResourceHandler.isProtected(entry)) continue;
                    if (sameItem(entry, main)) continue;
                    canonicalSample = main.copy();
                    nonCanonicalSample = entry.copy();
                    return;
                }
            }
        }
    }

    static boolean sameItem(final ItemStack a, final ItemStack b) {
        if (a == null || b == null) return a == b;
        if (a.getItem() == null || b.getItem() == null) return a.getItem() == b.getItem();
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
    }
}
