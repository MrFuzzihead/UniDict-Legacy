package com.mrfuzzihead.unidict.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    ORE_DICTIONARY(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("OreDictionaryMixin")),

    THERMAL_EXPANSION(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("RecipeFurnaceInvoker", "RecipePulverizerInvoker", "RecipeSmelterInvoker")
        .addRequiredMod(TargetMods.THERMAL_EXPANSION));

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
