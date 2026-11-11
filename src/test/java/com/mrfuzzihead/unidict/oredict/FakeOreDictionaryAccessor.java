package com.mrfuzzihead.unidict.oredict;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * Deterministic, in-memory {@link IOreDictionaryAccessor} for T2 unit tests — no MC statics, so
 * tests can drive {@code UniOreDictionary} (M3) logic without a live game.
 *
 * <p>
 * Test-only fake, kept out of the mixin packages (docs/TestPlan.md rule 6).
 */
public final class FakeOreDictionaryAccessor implements IOreDictionaryAccessor {

    private final Map<String, Integer> nameToId;
    private final List<List<ItemStack>> idToStack;
    private final List<List<ItemStack>> idToStackUn;

    public FakeOreDictionaryAccessor(Map<String, Integer> nameToId, List<List<ItemStack>> idToStack,
        List<List<ItemStack>> idToStackUn) {
        this.nameToId = nameToId;
        this.idToStack = idToStack;
        this.idToStackUn = idToStackUn;
    }

    @Override
    public Map<String, Integer> getNameToId() {
        return nameToId;
    }

    @Override
    public List<List<ItemStack>> getIdToStack() {
        return idToStack;
    }

    @Override
    public List<List<ItemStack>> getIdToStackUn() {
        return idToStackUn;
    }

}
