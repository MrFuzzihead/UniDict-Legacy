package com.mrfuzzihead.unidict.integration;

import com.mrfuzzihead.unidict.module.AbstractModule;

/**
 * The integration module — an explicit, ordered registry of machine/loot integrations. This
 * replaces upstream's reflection-based discovery ({@code searchForModules} reading the ASM data
 * table, {@code Class::newInstance}) with a plain, greppable list (docs/PLAN.md §M2, v1 steps 32-33).
 *
 * <p>
 * Each integration is added with an explicit {@code new} and only when its config toggle is on.
 * No integration is ported yet (M6 adds Furnace, AE2, IC2, IE and Chest on top of this; M7 adds the
 * mixin-accessor pairs EIO, Railcraft and TE). Until then {@code init()} is empty, so the mod boots
 * with all integrations disabled — a green start for the M2 gate.
 *
 * <p>
 * The pattern each landed integration will follow:
 * 
 * <pre>
 * if (Config.furnaceIntegration) executor.add(new FurnaceIntegration());
 * if (Config.ae2Integration) executor.add(new AE2Integration());
 * if (Config.ic2Integration) executor.add(new IC2Integration());
 * if (Config.ieIntegration) executor.add(new IEIntegration());
 * if (Config.chestIntegration) executor.add(new ChestIntegration());
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
        // M6/M7: add integrations here with explicit `new`, one line each, gated on their config
        // toggle. Until then nothing is registered, so runClient boots with no integrations.
    }
}
