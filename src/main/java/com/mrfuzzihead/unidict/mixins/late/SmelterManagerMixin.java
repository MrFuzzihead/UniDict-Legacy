package com.mrfuzzihead.unidict.mixins.late;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.te.ISmelterManagerAccessor;

import cofh.thermalexpansion.util.crafting.SmelterManager;

/**
 * Accessor mixin for TE's {@code SmelterManager.recipeMap} (docs/PLAN.md §M7 #4). Late +
 * mod-gated. {@code recipeMap} is a {@code private static Map} field (declared
 * {@code Map<List<ComparableItemStackSmelter>, RecipeSmelter>}); the {@code @Accessor} stub is a
 * concrete {@code private static} method returning the exact descriptor {@code Map}, and the
 * interface method reads it on demand ({@code remap = false}: TE-added field).
 */
@Mixin(SmelterManager.class)
public abstract class SmelterManagerMixin implements ISmelterManagerAccessor {

    @Accessor(value = "recipeMap", remap = false)
    private static Map accessor$recipeMap() {
        return null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getRecipeMap() {
        return accessor$recipeMap();
    }
}
