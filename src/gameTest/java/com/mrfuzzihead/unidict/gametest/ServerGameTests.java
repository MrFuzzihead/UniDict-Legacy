package com.mrfuzzihead.unidict.gametest;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.drops.UnifyDrops;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

/**
 * Assertions that require a live server world. The model-level checks are shared with the client smoke
 * path; these add the furnace and drop-time paths that the headless unit tests can only fake.
 */
final class ServerGameTests {

    private ServerGameTests() {}

    static void runAll() {
        ModelGameTests.runAll();
        checkFurnaceOutput();
        checkDropEvent();
        checkRewriteJournal();
    }

    /**
     * The vanilla iron-ore recipe is deliberately chosen: it exists in every pack, and after the furnace
     * integration its output must already be the canonical iron ingot. If the integration did not run
     * (or a Mixin/load-stage regression skipped it), this fails with a readable message.
     */
    private static void checkFurnaceOutput() {
        final ItemStack result = FurnaceRecipes.smelting()
            .getSmeltingResult(new ItemStack(Blocks.iron_ore));
        GameTestAssert.check("vanilla iron-ore furnace recipe exists", result != null);
        if (result == null) return;

        final ResourceHandler handler = UniDict.resourceHandler;
        GameTestAssert.check("resource handler available for furnace check", handler != null);
        if (handler == null) return;
        GameTestAssert.check(
            "furnace output resolves by identity (already canonical)",
            handler.getMainItemStack(result) == result);
    }

    /**
     * Drives the real {@link UnifyDrops} event handler with a live {@link EntityItem}. The chosen sample
     * came from the model in {@link ModelGameTests}; a tagged or protected stack is never selected.
     */
    private static void checkDropEvent() {
        final ItemStack sample = ModelGameTests.nonCanonicalSample;
        GameTestAssert.check("drop-time sample available", sample != null);
        if (sample == null) return;

        final MinecraftServer server = MinecraftServer.getServer();
        final WorldServer world = (server == null) ? null : server.worldServerForDimension(0);
        GameTestAssert.check("overworld is available", world != null);
        if (world == null) return;

        final EntityItem entity = new EntityItem(world, 0.0D, 128.0D, 0.0D, sample.copy());
        new UnifyDrops().onEntityJoinWorld(new EntityJoinWorldEvent(entity, world));
        GameTestAssert.check(
            "drop-time event rewrote the sample to the canonical entry",
            ModelGameTests.sameItem(entity.getEntityItem(), ModelGameTests.canonicalSample));
    }

    private static void checkRewriteJournal() {
        GameTestAssert.check(
            "rewrite journal captured at least one rewrite",
            !RewriteJournal.snapshot()
                .isEmpty());
    }
}
