package com.mrfuzzihead.unidict.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    ORE_DICTIONARY(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("OreDictionaryMixin")),

    CHEST_GEN(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("ChestGenHooksMixin", "WeightedRandomChestContentMixin")),

    THERMAL_EXPANSION(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins(
            "RecipeFurnaceInvoker",
            "RecipePulverizerInvoker",
            "RecipeSmelterInvoker",
            "FurnaceManagerMixin",
            "PulverizerManagerMixin",
            "SmelterManagerMixin")
        .addRequiredMod(TargetMods.THERMAL_EXPANSION)),

    ENDER_IO(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("OreDictionaryPreferencesMixin")
        .addRequiredMod(TargetMods.ENDER_IO)),

    RAILCRAFT(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("BlastFurnaceCraftingManagerMixin")
        .addRequiredMod(TargetMods.RAILCRAFT));

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
