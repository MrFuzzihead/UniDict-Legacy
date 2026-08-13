package com.mrfuzzihead.unidict.integration;

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
        // M6/M7: add more integrations here with explicit `new`, one line each, gated on their config
        // toggle. M4 landed the vanilla furnace rewrite as the vertical slice; M6 adds IC2 (AE2, IE,
        // Chest next), M7 the mixin-accessor pairs (EIO, Railcraft, TE).
        if (Config.integrationModule() && Config.furnace()) executor.add(new FurnaceIntegration());
        if (Config.integrationModule() && Config.ic2()) executor.add(new IC2Integration());
    }
}
