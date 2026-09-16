package com.mrfuzzihead.unidict.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    ORE_DICTIONARY(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("OreDictionaryMixin")),

    CHEST_GEN(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("ChestGenHooksMixin", "WeightedRandomChestContentMixin")),

    FORESTRY(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("ShapedOreRecipeMixin")),

    GALACTICRAFT(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("ShapelessOreRecipeMixin")),

    FORESTRY_CENTRIFUGE(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("forestry.CentrifugeRecipeMixin")
        .addRequiredMod(TargetMods.FORESTRY)),

    THERMAL_EXPANSION(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins(
            "thermalexpansion.RecipeFurnaceInvoker",
            "thermalexpansion.RecipePulverizerInvoker",
            "thermalexpansion.RecipeSmelterInvoker",
            "thermalexpansion.FurnaceManagerMixin",
            "thermalexpansion.PulverizerManagerMixin",
            "thermalexpansion.SmelterManagerMixin")
        .addRequiredMod(TargetMods.THERMAL_EXPANSION)),

    ENDER_IO(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("enderio.OreDictionaryPreferencesMixin")
        .addRequiredMod(TargetMods.ENDER_IO)),

    RAILCRAFT(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("railcraft.BlastFurnaceCraftingManagerMixin")
        .addRequiredMod(TargetMods.RAILCRAFT)),

    IC2(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ic2.AdvRecipeMixin", "ic2.AdvShapelessRecipeMixin")
        .addRequiredMod(TargetMods.IC2)),

    CRAFTING(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("ShapedRecipesMixin", "ShapelessRecipesMixin", "CraftingManagerMixin"));

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
