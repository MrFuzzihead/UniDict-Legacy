package com.mrfuzzihead.unidict.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    MINECRAFT(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("EarlyMinecraftMixinExample")),

    EXAMPLEMOD(new MixinBuilder().setPhase(Phase.LATE)
        .addClientMixins("modid.ExampleModMixinExample")
        .addRequiredMod(TargetMods.EXAMPLEMODNOCORE));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
