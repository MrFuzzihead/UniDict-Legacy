package com.mrfuzzihead.unidict.integration;

import cpw.mods.fml.common.Loader;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.module.AbstractModule;

/**
 * The integration module — an explicit, ordered registry of machine/loot integrations. This
 * replaces upstream's reflection-based discovery ({@code searchForModules} reading the ASM data
 * table, reflection-based instantiation) with a plain, greppable list (docs/PLAN.md §M2, v1 steps 32-33).
 *
 * <p>
 * Each integration is added with an explicit {@code new} and only when its config toggle is on
 * ({@link Config#furnace()} etc., from the M2 config rework). M4 lands the vanilla furnace rewrite
 * on top of this (which shipped empty); M6 adds AE2, IC2, IE and Chest, and M7 adds the
 * mixin-accessor pairs EIO, Railcraft and TE.
 *
 * <p>
 * The pattern each landed integration will follow:
 *
 * <pre>
 * if (Config.furnace()) executor.add(new FurnaceIntegration());
 * if (Config.ae2()) executor.add(new AE2Integration());
 * if (Config.ic2()) executor.add(new IC2Integration());
 * if (Config.ie()) executor.add(new IEIntegration());
 * if (Config.chest()) executor.add(new ChestIntegration());
 * // M7 (accessor/mixin): EIO, Railcraft, TE.
 * // TODO: Galacticraft integration (deferred stub).
 * </pre>
 */
public final class IntegrationModule extends AbstractModule {

    public IntegrationModule() {
        super("Integration");
    }

    @Override
    protected void init() {
        // M6/M7: add more integrations here with explicit `new`, one line each, gated on both the
        // config toggle AND `Loader.isModLoaded(<modid>)` for mod integrations. The mod-loaded guard
        // is essential: an optional integration's class references its target mod's types (e.g.
        // AE2Integration -> appeng.api.AEApi, IEIntegration -> blusunrize.immersiveengineering...),
        // so if that mod is absent at runtime (e.g. a focused "dev-light" classpath, or a user's own
        // lighter pack), running the integration throws NoClassDefFoundError and crashes the game.
        // Vanilla targets (furnace, chest) need no guard.
        if (Config.integrationModule()) {
            if (Config.furnace()) executor.add(new FurnaceIntegration()); // vanilla, always present
            if (Config.ic2() && Loader.isModLoaded("IC2")) executor.add(new IC2Integration());
            if (Config.ae2() && Loader.isModLoaded("appliedenergistics2")) executor.add(new AE2Integration());
            if (Config.ie() && Loader.isModLoaded("immersiveengineering")) executor.add(new IEIntegration());
            if (Config.chest()) executor.add(new ChestIntegration());      // vanilla, always present
            // M7 (accessor/mixin): EIO, Railcraft, TE — add their Loader.isModLoaded guards too.
        }
    }
}
