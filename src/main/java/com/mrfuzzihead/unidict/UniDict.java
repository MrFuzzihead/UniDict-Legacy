package com.mrfuzzihead.unidict;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.unidict.integration.IntegrationModule;
import com.mrfuzzihead.unidict.module.ModuleHandler;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = UniDict.MODID, version = Tags.VERSION, name = "UniDict", acceptedMinecraftVersions = "[1.7.10]")
public class UniDict {

    public static final String MODID = "unidict";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.mrfuzzihead.unidict.ClientProxy", serverSide = "com.mrfuzzihead.unidict.CommonProxy")
    public static CommonProxy proxy;

    /** Sequential driver for every module (M2): modules are registered explicitly, never discovered. */
    private final ModuleHandler moduleHandler = new ModuleHandler();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        moduleHandler.addModule(new IntegrationModule());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        moduleHandler.startModules(LoadStage.getStage(event.getClass()));
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        moduleHandler.startModules(LoadStage.getStage(event.getClass()));
        // M2 commit 2 ports the comparator cache lifecycle (SpecificKindItemStackComparator.nullify()).
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
