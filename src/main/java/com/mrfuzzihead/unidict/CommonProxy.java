package com.mrfuzzihead.unidict;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());

        UniDict.LOG.info("I am UniDict at version " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    /**
     * M4: the verify/report pass runs at load-complete, after the resource pipeline + integrations ran at post-init.
     */
    public void loadComplete(FMLLoadCompleteEvent event) {
        if (VerifyHarness.isEnabled()) VerifyHarness.runChecks();
    }

    public void serverStarting(FMLServerStartingEvent event) {}
}
