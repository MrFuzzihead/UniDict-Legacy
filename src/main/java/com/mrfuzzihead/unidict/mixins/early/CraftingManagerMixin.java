package com.mrfuzzihead.unidict.mixins.early;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

/**
 * Best-effort read-side safety net: canonicalizes {@link CraftingManager#findMatchingRecipe}'s returned
 * result through {@link ResourceHandler#getMainItemStack}. The <b>primary</b> crafting-unification
 * mechanism is the in-place mutator ({@code CraftingIntegration.rewriteCraftingOutputs}), which writes
 * each recipe's {@code output} field through the per-recipe accessor mixins — that is what is reliably
 * observed by NEI and the container. This hook duplicates that for any recipe class the mutator cannot
 * reach; it is guarded (never breaks a craft) and cheap, and the returned value is only replaced when it
 * resolves to a different unified resource.
 * <p>
 * <b>Config gate:</b> this hook runs only when {@code ConfigData#craftingUnificationEnabled()}
 * ({@code craftingIntegration} + the integration master switch) is on. It used to gate only on the
 * resource handler being published — which is unconditional after POST_INIT — so crafts were unified
 * even with the crafting integration (or the whole integration module) disabled
 * (docs/CODE_REVIEW.md BUG-5).
 */
@Mixin(CraftingManager.class)
public abstract class CraftingManagerMixin {

    /**
     * @param craftingMatrix the crafting-grid contents ({@link InventoryCrafting})
     * @param world          the crafting world
     * @param cir            the {@code findMatchingRecipe} return, which we replace when it resolves to
     *                       a non-canonical (or non-main) unified resource
     */
    @SuppressWarnings("unused")
    @Inject(method = "findMatchingRecipe", at = @At("RETURN"), remap = true)
    private void unidict$canonicalizeResult(final InventoryCrafting craftingMatrix, final World world,
        final CallbackInfoReturnable<ItemStack> cir) {
        final ItemStack result = cir.getReturnValue();
        if (result == null) return;
        final ResourceHandler handler = UniDict.resourceHandler;
        if (handler == null) return;
        if (!Config.get()
            .craftingUnificationEnabled()) return;
        try {
            final ItemStack canonical = handler.getMainItemStack(result);
            // getMainItemStack returns identity for protected / non-unified / not-yet-ready items.
            if (canonical != result) cir.setReturnValue(canonical);
        } catch (final Exception ignored) {
            // Never let unification break crafting.
        }
    }
}
