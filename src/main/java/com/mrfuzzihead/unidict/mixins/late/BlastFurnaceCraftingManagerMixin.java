package com.mrfuzzihead.unidict.mixins.late;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.railcraft.IBlastFurnaceCraftingManagerAccessor;

import mods.railcraft.common.util.crafting.BlastFurnaceCraftingManager;

/**
 * Accessor mixin for Railcraft's {@code BlastFurnaceCraftingManager} (docs/PLAN.md §M7 #3) exposing
 * the {@code recipes} list through the {@link IBlastFurnaceCraftingManagerAccessor} seam — no
 * reflection. Late + mod-gated: Railcraft only exists when loaded.
 *
 * <p>
 * {@code recipes} is a {@code private final List<BlastFurnaceRecipe>} <em>instance</em> field, so per
 * the M6 toolchain lessons the {@code @Accessor} stub must be {@code protected abstract} and return
 * the <b>exact</b> field descriptor ({@code List<BlastFurnaceRecipe>}). The interface method is raw
 * {@link List} (so T2 fakes need no Railcraft on the test classpath); this typed stub satisfies both
 * the descriptor requirement and the {@code @Override}. Reads the live field on demand — nothing is
 * injected into any transformed method ({@code remap = false}: the field is Railcraft-added).
 */
@Mixin(BlastFurnaceCraftingManager.class)
public abstract class BlastFurnaceCraftingManagerMixin implements IBlastFurnaceCraftingManagerAccessor {

    @Accessor(value = "recipes", remap = false)
    protected abstract List<BlastFurnaceCraftingManager.BlastFurnaceRecipe> accessor$getRecipes();

    @Override
    @SuppressWarnings("rawtypes")
    public List getRecipes() {
        return accessor$getRecipes();
    }
}
