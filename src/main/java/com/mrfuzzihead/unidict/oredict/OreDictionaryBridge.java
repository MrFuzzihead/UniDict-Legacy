package com.mrfuzzihead.unidict.oredict;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

/**
 * Live {@link IOreDictionaryAccessor} implementation. {@code OreDictionaryMixin} copies the real
 * private static OreDictionary caches into this bridge at a safe load point; all other code reads
 * through here so nothing depends on reflection or mixin internals.
 *
 * <p>
 * Plain class (not a mixin) — kept out of the mixin packages. See docs/TestPlan.md rule 6.
 */
public final class OreDictionaryBridge implements IOreDictionaryAccessor {

    private static final OreDictionaryBridge INSTANCE = new OreDictionaryBridge();

    private Map<String, Integer> nameToId;
    private List<String> idToName;
    private List<List<ItemStack>> idToStack;
    private List<List<ItemStack>> idToStackUn;
    private Map<Integer, List<Integer>> stackToId;

    private OreDictionaryBridge() {}

    @Nonnull
    public static OreDictionaryBridge instance() {
        return INSTANCE;
    }

    /**
     * Fills the bridge from the live maps. Called by {@code OreDictionaryMixin}; not for external use.
     */
    public static void capture(@Nonnull Map<String, Integer> nameToId, @Nonnull List<String> idToName,
        @Nonnull List<List<ItemStack>> idToStack, @Nonnull List<List<ItemStack>> idToStackUn,
        @Nonnull Map<Integer, List<Integer>> stackToId) {
        INSTANCE.nameToId = nameToId;
        INSTANCE.idToName = idToName;
        INSTANCE.idToStack = idToStack;
        INSTANCE.idToStackUn = idToStackUn;
        INSTANCE.stackToId = stackToId;
    }

    @Override
    public Map<String, Integer> getNameToId() {
        return nameToId;
    }

    @Override
    public List<String> getIdToName() {
        return idToName;
    }

    @Override
    public List<List<ItemStack>> getIdToStack() {
        return idToStack;
    }

    @Override
    public List<List<ItemStack>> getIdToStackUn() {
        return idToStackUn;
    }

    @Override
    public Map<Integer, List<Integer>> getStackToId() {
        return stackToId;
    }
}
