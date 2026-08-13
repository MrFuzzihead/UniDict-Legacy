package com.mrfuzzihead.unidict.mixins.early;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.unidict.oredict.OreDictionaryBridge;

/**
 * M0 "Spike A": prove Sponge {@code @Accessor} works for {@link OreDictionary}'s five private
 * static fields. The five accessors (e.g. {@link #getNameToId()}) are the proofed seam M3 reads
 * through. This class also carries a short-lived {@code @Inject} into {@code rebakeMap} that copies
 * the fields into {@link OreDictionaryBridge} as a spike.
 *
 * <p>
 * All targets use {@code remap = false}: these are Forge-*added* members (not vanilla), so the
 * Mixin refmap has no MCP→SRG mapping for them and their names are the same in dev and obfuscated.
 *
 * <p>
 * <b>Hodgepodge conflict (see docs/PLAN.md §Interop decisions):</b> in a GTNH dev env,
 * Hodgepodge's {@code SpeedupOreDictionaryTransformer} ASM-rewrites {@code OreDictionary.rebakeMap()}
 * and strips injected callbacks, so the {@code @Inject} below does NOT fire there (the accessors
 * still apply cleanly). Per our interop rule we defer to Hodgepodge — we do not disable it. The M3
 * seam drops this one-time-capture approach in favour of <b>lazy reads</b>: {@code UniOreDictionary}
 * / the bridge call the {@code @Accessor} getters on demand. This class's {@code capture()}/inject
 * is a spike artifact to be retired in M3.
 */
@Mixin(OreDictionary.class)
public abstract class OreDictionaryMixin {

    private static final Logger LOG = LogManager.getLogger("UniDict");

    @Accessor(value = "nameToId", remap = false)
    private static Map<String, Integer> getNameToId() {
        return null;
    }

    @Accessor(value = "idToName", remap = false)
    private static List<String> getIdToName() {
        return null;
    }

    @Accessor(value = "idToStack", remap = false)
    private static List<List<ItemStack>> getIdToStack() {
        return null;
    }

    @Accessor(value = "idToStackUn", remap = false)
    private static List<List<ItemStack>> getIdToStackUn() {
        return null;
    }

    @Accessor(value = "stackToId", remap = false)
    private static Map<Integer, List<Integer>> getStackToId() {
        return null;
    }

    @Inject(method = "rebakeMap", remap = false, at = @At("TAIL"))
    private static void unidict$onRebakeMap(CallbackInfo ci) {
        final Map<String, Integer> nameToId = getNameToId();
        final List<String> idToName = getIdToName();
        final List<List<ItemStack>> idToStack = getIdToStack();
        final List<List<ItemStack>> idToStackUn = getIdToStackUn();
        final Map<Integer, List<Integer>> stackToId = getStackToId();
        OreDictionaryBridge.capture(nameToId, idToName, idToStack, idToStackUn, stackToId);
        LOG.info(
            "unidict.accessor OK: nameToId={} idToName={} idToStack={} idToStackUn={} stackToId={}",
            nameToId.size(),
            idToName.size(),
            idToStack.size(),
            idToStackUn.size(),
            stackToId.size());
    }
}
