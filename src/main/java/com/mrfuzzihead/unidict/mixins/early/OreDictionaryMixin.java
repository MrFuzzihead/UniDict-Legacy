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
 * static fields, and copy them into {@link OreDictionaryBridge} so UniOreDictionary (M3) can read
 * them without reflection.
 *
 * <p>
 * All targets use {@code remap = false}: these are Forge-*added* members (not vanilla), so the
 * Mixin refmap has no MCP→SRG mapping for them and their names are the same in dev and obfuscated.
 *
 * <p>
 * Verification: on `runClient`, the log line below must appear with non-zero sizes (see
 * docs/PLAN.md §M0). If it never fires at runtime, fall back to {@code @Shadow @Final} field
 * capture and update this spike note.
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
