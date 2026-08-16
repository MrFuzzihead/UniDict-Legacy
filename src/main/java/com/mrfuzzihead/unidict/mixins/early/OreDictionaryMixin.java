package com.mrfuzzihead.unidict.mixins.early;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.oredict.IOreDictionaryAccessor;

/**
 * Accessor mixin for {@link OreDictionary}'s five private static caches (M3). Implements
 * {@link IOreDictionaryAccessor} so the bridge / {@code UniOreDictionary} query Forge's maps through
 * the interface — no reflection.
 *
 * <p>
 * The {@code @Accessor} stubs are {@code private static} (static fields require static accessors).
 * The {@code @Override} instance methods that realise the interface delegate to those stubs; Mixin
 * rewrites the {@code accessor$*} call sites to the real generated target accessors at apply time, so
 * each interface call reads the live static field <b>on demand</b>.
 *
 * <p>
 * Note: Mixin forbids non-private <em>static</em> members in a mixin class, which is why these readers
 * are instance interface methods rather than {@code public static} helpers — a {@code public static}
 * accessor would be copied onto the target, which Mixin rejects (an early attempt at exactly that
 * crashed startup with {@code InvalidMixinException}).
 *
 * <p>
 * All targets use {@code remap = false}: these are Forge-<em>added</em> members (not vanilla), so the
 * Mixin refmap has no MCP→SRG mapping for them; their names are identical in dev and obfuscated.
 *
 * <p>
 * <b>Hodgepodge conflict (docs/PLAN.md §Interop decisions, hard rule):</b> Hodgepodge's
 * {@code SpeedupOreDictionaryTransformer} ASM-rewrites {@code OreDictionary.rebakeMap()} and strips
 * injected callbacks, so a one-time {@code @Inject} capture would silently never fire there. We defer
 * to Hodgepodge (never disable it) — these lazy readers only call {@code @Accessor} getters on demand
 * and never inject into a transform-targeted method.
 */
@Mixin(OreDictionary.class)
public abstract class OreDictionaryMixin implements IOreDictionaryAccessor {

    @Accessor(value = "nameToId", remap = false)
    private static Map<String, Integer> accessor$nameToId() {
        return null;
    }

    @Accessor(value = "idToName", remap = false)
    private static List<String> accessor$idToName() {
        return null;
    }

    @Accessor(value = "idToStack", remap = false)
    private static List<List<ItemStack>> accessor$idToStack() {
        return null;
    }

    @Accessor(value = "idToStackUn", remap = false)
    private static List<List<ItemStack>> accessor$idToStackUn() {
        return null;
    }

    @Accessor(value = "stackToId", remap = false)
    private static Map<Integer, List<Integer>> accessor$stackToId() {
        return null;
    }

    @Override
    public Map<String, Integer> getNameToId() {
        return accessor$nameToId();
    }

    @Override
    public List<String> getIdToName() {
        return accessor$idToName();
    }

    @Override
    public List<List<ItemStack>> getIdToStack() {
        return accessor$idToStack();
    }

    @Override
    public List<List<ItemStack>> getIdToStackUn() {
        return accessor$idToStackUn();
    }

    @Override
    public Map<Integer, List<Integer>> getStackToId() {
        return accessor$stackToId();
    }
}
