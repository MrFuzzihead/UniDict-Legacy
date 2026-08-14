package com.mrfuzzihead.unidict.mixins.early;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.chest.IWeightedRandomChestContentAccessor;

/**
 * Accessor mixin for the vanilla {@link WeightedRandomChestContent} field {@code theItemId}
 * (get + set). Implements {@link IWeightedRandomChestContentAccessor}; tests use a fake.
 *
 * <p>
 * {@code theItemId} is the item a chest loot entry generates, and upstream (1.12.2) rewrote it by
 * direct field access because it is {@code public} there. In <b>MC 1.7.10</b> it is {@code private}
 * (notch {@code qx.b}, SRG {@code field_76297_b}), so {@code ChestIntegration} must reach it through
 * this accessor to perform the in-place, non-destructive loot rewrite (BB-3). This is a documented
 * extension beyond the plan's single {@code ChestGenHooksMixin} row, forced by the 1.7.10 field
 * visibility (see docs/STATUS.md M6 Chest entry).
 *
 * <p>
 * Unlike the Forge-added members handled with {@code remap = false}, this is a <em>vanilla</em> field
 * (MCP {@code theItemId} → SRG {@code field_76297_b}), so the {@code @Accessor}s use the default
 * {@code remap = true} to apply the MCP→SRG mapping at runtime. They are instance accessors, so they
 * are declared {@code abstract} (non-private): Mixin's transformer rejects a non-abstract instance
 * {@code @Accessor} at apply time ({@code InvalidAccessorException: … is not abstract}); the public
 * {@code @Override} interface methods delegate to these generated accessors.
 *
 * @see IWeightedRandomChestContentAccessor
 */
@Mixin(WeightedRandomChestContent.class)
public abstract class WeightedRandomChestContentMixin implements IWeightedRandomChestContentAccessor {

    @Accessor("theItemId")
    protected abstract ItemStack accessor$getTheItemId();

    @Accessor("theItemId")
    protected abstract void accessor$setTheItemId(final ItemStack item);

    @Override
    public ItemStack getTheItemId() {
        return accessor$getTheItemId();
    }

    @Override
    public void setTheItemId(final ItemStack item) {
        accessor$setTheItemId(item);
    }
}
