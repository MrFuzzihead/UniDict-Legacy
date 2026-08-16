package com.mrfuzzihead.unidict.mixins.late;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.te.IFurnaceManagerAccessor;

import cofh.thermalexpansion.util.crafting.FurnaceManager;

/**
 * Accessor mixin for TE's {@code FurnaceManager.recipeMap} (docs/PLAN.md §M7 #4) exposing the
 * {@code private static} recipe map through the {@link IFurnaceManagerAccessor} seam — no reflection.
 * Late + mod-gated: TE only exists when loaded.
 *
 * <p>
 * {@code recipeMap} is a <em>static</em> field, so per the proven M3 {@code OreDictionaryMixin} /
 * M6 {@code ChestGenHooksMixin} pattern the {@code @Accessor} stub is a concrete {@code private
 * static} method (Java forbids {@code abstract static}); the interface method - realised as an
 * instance method merged onto {@code FurnaceManager} - delegates to it and reads the live field on
 * demand. The stub returns the <b>exact</b> descriptor {@code Map} (the field is declared
 * {@code Map<ComparableItemStackFurnace, RecipeFurnace>}). {@code remap = false}: the field is
 * TE-added.
 */
@Mixin(FurnaceManager.class)
public abstract class FurnaceManagerMixin implements IFurnaceManagerAccessor {

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
