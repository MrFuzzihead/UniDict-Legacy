package com.mrfuzzihead.unidict.mixins.late;

import java.util.Map;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.enderio.IOreDictionaryPreferencesAccessor;

import crazypants.enderio.material.OreDictionaryPreferences;

/**
 * Accessor mixin for Ender IO's {@code OreDictionaryPreferences} (docs/PLAN.md §M7 #1) exposing the
 * {@code preferences} map through the {@link IOreDictionaryPreferencesAccessor} seam — no reflection.
 *
 * <p>
 * {@code preferences} is a {@code private final Map<String, ItemStack>} <em>instance</em> field, so
 * per the M6 toolchain lessons (docs/PLAN.md "Chest accessor note") the {@code @Accessor} stub must be
 * {@code protected abstract} and return the <b>exact</b> field descriptor ({@code Map<String,
 * ItemStack>}). The public {@code @Override} interface method delegates to it, reading the live field
 * on demand — nothing is injected into any transformed method ({@code remap = false}: the field is
 * Ender IO-added, not vanilla).
 */
@Mixin(OreDictionaryPreferences.class)
public abstract class OreDictionaryPreferencesMixin implements IOreDictionaryPreferencesAccessor {

    @Accessor(value = "preferences", remap = false)
    protected abstract Map<String, ItemStack> accessor$getPreferences();

    @Override
    public Map<String, ItemStack> getPreferences() {
        return accessor$getPreferences();
    }
}
