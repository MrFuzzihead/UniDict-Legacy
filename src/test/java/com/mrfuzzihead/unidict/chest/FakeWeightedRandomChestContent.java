package com.mrfuzzihead.unidict.chest;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

/**
 * Test {@link WeightedRandomChestContent} that implements {@link IWeightedRandomChestContentAccessor}
 * so T2 tests can exercise {@code ChestIntegration} item rewrites without the mixin applied at runtime.
 *
 * <p>
 * The real class stores {@code theItemId} in a {@code private} field with no setter, so the fake keeps
 * its own {@code item} field and super-delegates the {@code WeightedRandomChestContent} contract
 * (constructed with weight 1). Only MC <em>types</em> are used — no MC statics (T2).
 *
 * <p>
 * Test-only fake, kept out of the mixin packages (docs/TestPlan.md rule 6).
 */
public final class FakeWeightedRandomChestContent extends WeightedRandomChestContent
    implements IWeightedRandomChestContentAccessor {

    private ItemStack item;

    public FakeWeightedRandomChestContent(final ItemStack item) {
        super(item, 0, 0, 1); // min/max 0, weight 1 — irrelevant to the unit under test
        this.item = item;
    }

    @Override
    public ItemStack getTheItemId() {
        return item;
    }

    @Override
    public void setTheItemId(final ItemStack item) {
        this.item = item;
    }
}
