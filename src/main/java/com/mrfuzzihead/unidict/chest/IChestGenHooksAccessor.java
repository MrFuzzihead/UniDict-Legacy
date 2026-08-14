package com.mrfuzzihead.unidict.chest;

import java.util.List;
import java.util.Map;

import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

/**
 * T2 seam over Forge's {@link ChestGenHooks} internals needed by {@code ChestIntegration}
 * (docs/PLAN.md §M6 #5 and the mixin summary table): Forge's static category registry
 * ({@code chestInfo}) and each category instance's weighted loot table ({@code contents}).
 *
 * <p>
 * This is the seam pattern from docs/PLAN.md §0 rule 1: a plain interface whose live implementation
 * is the {@code ChestGenHooksMixin} (mixins.early) and whose T2 fake ({@code FakeChestGenHooksAccessor})
 * lives in {@code src/test}. Plain interface, deliberately outside the mixin packages (rule 6).
 *
 * <p>
 * Both {@code chestInfo} and {@code contents} are Forge-<em>added</em> members (not vanilla), so the
 * mixin targets them with {@code remap = false} — their MCP names are identical in dev and SRG, and
 * reading them through the accessor never injects into any transformed method.
 */
public interface IChestGenHooksAccessor {

    /** Forge's static {@code category -> ChestGenHooks} registry ({@code chestInfo}). */
    Map<String, ChestGenHooks> getChestInfo();

    /** This category instance's weighted loot table ({@code contents}). */
    List<WeightedRandomChestContent> getContents();
}
