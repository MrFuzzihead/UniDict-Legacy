package com.mrfuzzihead.unidict.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

public enum TargetMods implements ITargetMod {

    // Keep these as literals: the early/late mixin loaders read this enum before the normal mod
    // classes are safely visible, so referencing common.ModIds here can break mixin-loader class init.
    THERMAL_EXPANSION("ThermalExpansion"),
    ENDER_IO("EnderIO"),
    RAILCRAFT("Railcraft"),
    FORESTRY("Forestry"),
    IC2("IC2");

    private final TargetModBuilder builder;

    TargetMods(String coreModClass, String modId) {
        this.builder = new TargetModBuilder().setCoreModClass(coreModClass)
            .setModId(modId);
    }

    TargetMods(String modId) {
        this.builder = new TargetModBuilder().setModId(modId);
    }

    @Nonnull
    @Override
    public TargetModBuilder getBuilder() {
        return builder;
    }
}
