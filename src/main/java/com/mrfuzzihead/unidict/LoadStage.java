package com.mrfuzzihead.unidict;

import javax.annotation.Nonnull;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLStateEvent;

/**
 * FML lifecycle stages at which a module thread may run. Ported faithfully from
 * {@code wanion.unidict.LoadStage} (WanionCane, MPL-2.0); it is the good part of the original
 * module system — a declarative way to pin an integration to a specific load event — and is kept
 * in M2 (docs/PLAN.md §M2). The thread pool that drove it is removed.
 */
public enum LoadStage {

    PRE_INIT(FMLPreInitializationEvent.class),
    INIT(FMLInitializationEvent.class),
    POST_INIT(FMLPostInitializationEvent.class),
    LOAD_COMPLETE(FMLLoadCompleteEvent.class);

    public final Class<? extends FMLStateEvent> stage;

    LoadStage(final Class<? extends FMLStateEvent> stage) {
        this.stage = stage;
    }

    /** @return the {@link LoadStage} matching a concrete FML event type, or {@code null}. */
    public static LoadStage getStage(@Nonnull final Class<? extends FMLStateEvent> stage) {
        for (final LoadStage loadStage : values()) if (loadStage.stage == stage) return loadStage;
        return null;
    }
}
