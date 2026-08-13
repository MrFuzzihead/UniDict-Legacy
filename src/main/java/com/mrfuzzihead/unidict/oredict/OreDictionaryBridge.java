package com.mrfuzzihead.unidict.oredict;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Live {@link IOreDictionaryAccessor} implementation for production.
 *
 * <p>
 * Because {@code OreDictionary}'s caches are {@code private static} and Mixin only generates their
 * accessors on the target class, the bridge holds a single {@code OreDictionary} instance cast to the
 * accessor interface ({@code OreDictionaryMixin} implements {@code IOreDictionaryAccessor} as instance
 * methods merged onto the target). Each getter is a <b>lazy read</b>: the interface method calls the
 * generated {@code @Accessor} getter on every call, so the current field value is read fresh from
 * Forge's live maps. Nothing is cached and nothing is injected into a transformed method, so this
 * coexists with Hodgepodge's {@code SpeedupOreDictionaryTransformer} (which would strip a one-time
 * {@code @Inject} capture — see docs/PLAN.md §Interop decisions).
 *
 * <p>
 * Plain class (not a mixin) — kept out of the mixin packages (docs/TestPlan.md rule 6).
 */
public final class OreDictionaryBridge implements IOreDictionaryAccessor {

    private static final IOreDictionaryAccessor ACCESSOR = (IOreDictionaryAccessor) (Object) new OreDictionary();
    private static final OreDictionaryBridge INSTANCE = new OreDictionaryBridge();

    private OreDictionaryBridge() {}

    public static OreDictionaryBridge instance() {
        return INSTANCE;
    }

    @Override
    public Map<String, Integer> getNameToId() {
        return ACCESSOR.getNameToId();
    }

    @Override
    public List<String> getIdToName() {
        return ACCESSOR.getIdToName();
    }

    @Override
    public List<List<ItemStack>> getIdToStack() {
        return ACCESSOR.getIdToStack();
    }

    @Override
    public List<List<ItemStack>> getIdToStackUn() {
        return ACCESSOR.getIdToStackUn();
    }

    @Override
    public Map<Integer, List<Integer>> getStackToId() {
        return ACCESSOR.getStackToId();
    }
}
