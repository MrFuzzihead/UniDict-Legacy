package com.mrfuzzihead.unidict.mixins.early;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.chest.IChestGenHooksAccessor;

/**
 * Accessor mixin for {@link ChestGenHooks} (docs/PLAN.md §M6 #5, mixin summary table) — the first
 * accessor seam of the M6 Chest integration. Exposes the two internals {@code ChestIntegration} needs:
 * Forge's static {@code chestInfo} category registry and each {@code ChestGenHooks} instance's
 * {@code contents} loot table. Implements {@link IChestGenHooksAccessor}; tests use a fake.
 *
 * <p>
 * The {@code @Accessor} for the static {@code chestInfo} registry is a concrete method whose body Mixin
 * replaces at apply time — mirroring the proven M3 {@code OreDictionaryMixin} (a static accessor cannot
 * be {@code abstract} in Java). The instance {@code contents} accessor, however, <b>must be
 * {@code abstract}</b>: Mixin's transformer rejects a non-abstract instance {@code @Accessor} at apply
 * time with {@code InvalidAccessorException: @Accessor method … is not abstract} (JVM phase differs from
 * the AP). The public {@code @Override} interface methods delegate to these stubs. Each reads the live
 * field on demand — nothing is injected into any transformed method (docs/PLAN.md §Interop decisions /
 * Hodgepodge hard rule).
 * <p>
 * Both {@code @Accessor} stubs must declare the <b>exact</b> field type (the Mixin AP resolves the
 * accessor by its field descriptor and rejects a supertype): {@code chestInfo} is
 * {@code HashMap<String, ChestGenHooks>} and {@code contents} is
 * {@code ArrayList<WeightedRandomChestContent>}. The public {@code @Override} interface methods expose
 * the covariant {@code Map}/{@code List} types.
 *
 * <p>
 * Both fields are Forge-<em>added</em> members, so {@code remap = false}: their names are identical in
 * dev and SRG (the MCP→SRG refmap has no entry for them).
 */
@Mixin(ChestGenHooks.class)
public abstract class ChestGenHooksMixin implements IChestGenHooksAccessor {

    @Accessor(value = "chestInfo", remap = false)
    private static HashMap<String, ChestGenHooks> accessor$chestInfo() {
        return null;
    }

    @Accessor(value = "contents", remap = false)
    protected abstract ArrayList<WeightedRandomChestContent> accessor$contents();

    @Override
    public Map<String, ChestGenHooks> getChestInfo() {
        return accessor$chestInfo();
    }

    @Override
    public List<WeightedRandomChestContent> getContents() {
        return accessor$contents();
    }
}
