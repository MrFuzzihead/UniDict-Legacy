package com.mrfuzzihead.unidict;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.unidict.integration.IntegrationModule;
import com.mrfuzzihead.unidict.module.ModuleHandler;
import com.mrfuzzihead.unidict.nei.NEIHideModule;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceHandler;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = UniDict.MODID, version = Tags.VERSION, name = "UniDict", acceptedMinecraftVersions = "[1.7.10]")
public class UniDict {

    public static final String MODID = "unidict";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.mrfuzzihead.unidict.ClientProxy", serverSide = "com.mrfuzzihead.unidict.CommonProxy")
    public static CommonProxy proxy;

    /** Sequential driver for every module (M2): modules are registered explicitly, never discovered. */
    private final ModuleHandler moduleHandler = new ModuleHandler();

    /**
     * The resource model published by the M4 selection core once its pipeline runs at post-init.
     * Integrations (furnace) and the transparency report (BB-1) read the canonical lookups through
     * this (rework: "expose ResourceHandler directly via UniDict statics").
     */
    public static volatile ResourceHandler resourceHandler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        moduleHandler.addModule(new IntegrationModule());
        // P0 #1+#2: NEI variant hiding + keepOneEntry via hiding. Client-only, and only when
        // NotEnoughItems is actually present — so NEIHelper (which references codechicken.nei.api.API)
        // is never loaded on a dedicated server or an NEI-less client.
        if (event.getSide()
            .isClient() && Loader.isModLoaded("NotEnoughItems")) moduleHandler.addModule(new NEIHideModule());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        runResourcePipeline();
        moduleHandler.startModules(LoadStage.getStage(event.getClass()));
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // BB-1: run LOAD_COMPLETE-stage modules (e.g. the TE integration, @SpecifiedLoadStage) BEFORE
        // the verify/report pass, so the transparency report and [unidict-verify] summary capture every
        // kept rewrite (docs/PLAN.md §BB-1 gate: every kept rewrite has a matching report line).
        moduleHandler.startModules(LoadStage.getStage(event.getClass()));
        proxy.loadComplete(event);
        // M2 commit 2 ports the comparator cache lifecycle (SpecificKindItemStackComparator.nullify()).
    }

    /** M4 selection core: create the resource model, reconcile it, and publish the ResourceHandler. */
    private static void runResourcePipeline() {
        final UniResourceHandler handler = UniResourceHandler.create();
        if (handler == null) return; // already ran (defensive)
        handler.createResources();
        handler.postInit();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        // Galacticraft registers its compressor recipes during FMLServerStarting, so the compressor
        // rewrite is deferred until the server is fully up (see GalacticraftIntegration).
        IntegrationModule.runGalacticraftCompressor();
        // Crafting compaction/alloy recipes (and script edits) can likewise be registered only at
        // server start; re-run the (idempotent) crafting output rewrite here to catch them.
        IntegrationModule.runCraftingAtServerStart();
        // IC2 machine recipes (incl. the macerator) can be re-registered after POST_INIT; re-run the
        // (idempotent, in-place) machine-output rewrite so the final authoritative recipes are canonicalized.
        IntegrationModule.runIC2AtServerStart();
    }
}
