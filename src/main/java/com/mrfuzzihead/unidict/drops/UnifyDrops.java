package com.mrfuzzihead.unidict.drops;

import java.util.function.UnaryOperator;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Wired through the module framework: this is an {@link AbstractModuleThread} whose load-stage
 * {@link #call()} registers the persistent drop-time listener on Forge's event bus once. Running as
 * a POST_INIT integration thread means it is registered after the {@code ResourceHandler} pipeline
 * has run, so {@code UniDict.resourceHandler} is already populated; the handler still self-guards on
 * readiness for safety. Safe on both sides because the {@code world.isRemote} guard confines actual
 * conversion to the logical server.
 */
@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
public final class UnifyDrops extends AbstractModuleThread {

    /** Public because {@code IntegrationModule} (a different package) constructs it. */
    public UnifyDrops() {
        super("UnifyDrops", "Integration");
    }

    /** Registers this instance as the drop-listener on Forge's event bus (once, at its load stage). */
    @Override
    public String call() {
        MinecraftForge.EVENT_BUS.register(this);
        return threadName + "drop-time unification enabled (upgrades dropped items to the canonical entry).";
    }

    /**
     * Converts a newly-spawned item entity's stack to its canonical entry. Non-destructive and
     * identity-preserving: when the entity is not an item, the world is a client, the toggle is off,
     * the resource model is not ready, the stack is not "clean", or it has no unified resource, the
     * entity (and its stack) pass through untouched.
     */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!Config.unifyDrops() || event.world.isRemote) return;
        if (!(event.entity instanceof EntityItem)) return;
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (resourceHandler == null || resourceHandler.resources.isEmpty()) return;
        final EntityItem item = (EntityItem) event.entity;
        final ItemStack stack = item.getEntityItem();
        if (stack == null || stack.stackSize <= 0) return;
        final ItemStack main = unifyDrop(stack, resourceHandler::getMainItemStack);
        // unifyDrop returns the input identity unless it really changed; only then touch the entity.
        if (main != null && main != stack) item.setEntityItemStack(main);
    }

    /**
     * Maps a dropped stack to its canonical entry. Returns the stack unchanged (identity) when it is
     * not "clean" — i.e. it carries an NBT tag compound — so tagged/enchanted items are never touched.
     * Because the resolver allocates a fresh stack even for an already-canonical drop, the result is
     * compared <em>semantically</em> (item + damage) and the original identity is returned when the
     * drop is already the preferred variant — no pointless re-write. A {@code null}/{@code empty}
     * stack returns identity so callers can skip it.
     *
     * @param resolveMain the canonical-entry resolver (production: {@code ResourceHandler#getMainItemStack}).
     */
    static ItemStack unifyDrop(final ItemStack stack, final UnaryOperator<ItemStack> resolveMain) {
        if (stack == null || stack.stackSize <= 0 || stack.hasTagCompound()) return stack;
        final ItemStack main = resolveMain.apply(stack);
        if (main == null || (main.getItem() == stack.getItem() && main.getItemDamage() == stack.getItemDamage()))
            return stack;
        return main;
    }
}
