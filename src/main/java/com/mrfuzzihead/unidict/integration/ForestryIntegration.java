package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt (and strictly scoped) from wanion.unidict.integration.ForestryIntegration (WanionCane, MPL-2.0)
 * as the M7 Forestry machine rewrite (docs/PLAN.md §M7 #2). Three non-destructive machine rewrites:
 * <ul>
 * <li><b>Carpenter</b> — each grid recipe's OUTPUT is canonicalised <em>in place</em>: Forestry's
 * {@code ShapedRecipeCustom} extends Forge's {@code ShapedOreRecipe}, so writing that private
 * {@code output} field (via the early {@code ShapedOreRecipeMixin} accessor) is all a rewrite is.
 * The manager's {@code Collections.unmodifiableSet} is only <em>iterated</em>, never mutated (BB-3).</li>
 * <li><b>Squeezer (container recipes)</b> — each {@code SqueezerRecipeManager.containerRecipes} value's
 * {@code remnants} byproduct is canonicalised by replacing the value for the SAME key
 * ({@code Map.Entry.setValue}) — the key (empty container) and entry count are preserved (BB-3). The
 * container-recipe map is Forestry's public static {@code ItemStackMap}, so no mixin is needed.</li>
 * <li><b>Centrifuge</b> — each recipe's product map ({@code CentrifugeRecipe.outputs}, a private final
 * {@code Map<ItemStack, Float>}) is canonicalised <em>in place</em> (clear + putAll canonical keys) via
 * the late {@code CentrifugeRecipeMixin} accessor. {@code getAllProducts()} returns an
 * {@code ImmutableMap} <em>copy</em>, but {@code getProducts(Random)} — what the machine rolls — reads
 * the original map, so rewriting its contents is all it takes; the recipe is never removed (BB-3). This
 * is what unifies a bee-comb → metal output when a pack adds such a recipe.</li>
 * </ul>
 * Two small <em>additive</em> complements are implemented here (upstream's {@code bronzeThings()} and
 * crate-recipe wiring, ported through Forestry's <em>supported</em> {@code
 * RecipeManagers.carpenterManager.addRecipe} — never the reflective {@code Set.add} upstream used):
 * <ul>
 * <li><b>Bronze-tool recycling</b> — when a unified {@code ingotBronze} container exists, an
 * {@code addSingleRecipe} per registered broken Bronze tool (pickaxe → 2 × canonical ingot, shovel →
 * 1 × canonical ingot) is added. Only ever additive (BB-3); the canonical output is baked in at add
 * time, so no carpenter-output rewrite is needed for these two recipes.</li>
 * <li><b>Crate wiring</b> — for every unified {@code ingot} resource whose <em>already-registered</em>
 * Forestry crate item ({@code Forestry:crated&lt;Name&gt;}) resolves, the two reciprocal carpenter
 * recipes (9 × ingot → crate via OreDictionary; crate → 9 × canonical ingot) are added through the
 * public manager. Only <em>recipes</em> for crates Forestry already ships are wired; creating a new
 * {@code ItemCrated} at POST_INIT is NOT attempted (see below).</li>
 * </ul>
 * Deliberately NOT implemented here (see docs/INTEGRATIONS.md §Forestry):
 * <ul>
 * <li><b>Crate-item creation</b> (runtime {@code ItemCrated} + {@code GameRegistry.registerItem} +
 * {@code PluginStorage.registerCrate}) — deferred (fragile: item registration is post-lock at
 * POST_INIT and Forestry's {@code createCrateRecipes()} drain has already run; BB-3). Only recipes
 * for crates that exist are wired.</li>
 * <li><b>Fluid outputs</b> (squeezer/fermenter/still) — fluid equivalence has no OreDictionary-style
 * model in 1.7.10 (BB-4 territory, deferred).</li>
 * <li><b>NEI hiding</b> — deferred (single guarded {@code NEIHelper} site, unused).</li>
 * </ul>
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.forestry.ICarpenterRecipeAdder;
import com.mrfuzzihead.unidict.forestry.ICentrifugeRecipeAccessor;
import com.mrfuzzihead.unidict.forestry.IShapedOreRecipeAccessor;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.Resource;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceContainer;

import cpw.mods.fml.common.registry.GameData;
import forestry.api.recipes.ICarpenterRecipe;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.api.recipes.IDescriptiveRecipe;
import forestry.api.recipes.RecipeManagers;
import forestry.core.fluids.Fluids;
import forestry.factory.recipes.CentrifugeRecipe;
import forestry.factory.recipes.ISqueezerContainerRecipe;
import forestry.factory.recipes.SqueezerContainerRecipe;
import forestry.factory.recipes.SqueezerRecipeManager;

final class ForestryIntegration extends AbstractModuleThread {

    /**
     * Lazy ({@link Supplier}) so {@code ForestryIntegration.<clinit>} never resolves Forestry's
     * classes — the T2 test can reach the generic {@link #rewriteContainerRecipes} seam with a neutral
     * view and no Forestry types on the test classpath (mirrors {@code EIOIntegration}'s lazy views).
     */
    private static final Supplier<ContainerRecipeView<ISqueezerContainerRecipe>> CONTAINER_VIEW = () -> new ContainerRecipeView<ISqueezerContainerRecipe>() {

        @Override
        public ItemStack getRemnants(final ISqueezerContainerRecipe recipe) {
            return recipe.getRemnants();
        }

        @Override
        public int getProcessingTime(final ISqueezerContainerRecipe recipe) {
            return recipe.getProcessingTime();
        }

        @Override
        public float getRemnantsChance(final ISqueezerContainerRecipe recipe) {
            return recipe.getRemnantsChance();
        }

        @Override
        public ISqueezerContainerRecipe rebuild(final ISqueezerContainerRecipe original,
            final ItemStack canonicalRemnants) {
            return new SqueezerContainerRecipe(
                original.getEmptyContainer(),
                original.getProcessingTime(),
                canonicalRemnants,
                original.getRemnantsChance());
        }
    };

    /** Seam over one squeezer container recipe for the generic rewrite core. */
    interface ContainerRecipeView<V> {

        ItemStack getRemnants(V recipe);

        int getProcessingTime(V recipe);

        float getRemnantsChance(V recipe);

        /** @return a new recipe equal to {@code original} but with the canonical remnants stack */
        V rebuild(V original, ItemStack canonicalRemnants);
    }

    /**
     * Lazy ({@link Supplier}) {@link ICarpenterRecipeAdder} bridging to Forestry's <em>supported</em>
     * public {@code RecipeManagers.carpenterManager.addRecipe(...)} (never the reflective
     * {@code Set.add} upstream used; see {@code ICarpenterRecipeAdder}). One-slot grids (tool recycling
     * / uncrating) run dry (no liquid, no box); the 3×3 crating grid asks for 100 mB water like
     * upstream's {@code ForestryUniHelper}. Lazy so {@code ForestryIntegration.<clinit>} never resolves
     * Forestry's classes — {@code RecipeManagers} / {@code Fluids} are only reached when the adder is
     * asked to add (mirrors {@link #CONTAINER_VIEW}).
     */
    private static final Supplier<ICarpenterRecipeAdder> CARPENTER_ADDER = () -> new ICarpenterRecipeAdder() {

        @Override
        public boolean addSingleRecipe(final ItemStack product, final Object ingredient) {
            // Explicit casts disambiguate the three ICarpenterManager.addRecipe overloads (null could be
            // box or liquid). Single-slot dry recipe: no liquid, no box.
            RecipeManagers.carpenterManager
                .addRecipe(5, (FluidStack) null, (ItemStack) null, product, "X  ", "   ", "   ", 'X', ingredient);
            return true;
        }

        @Override
        public boolean addGridRecipe(final ItemStack product, final Object ingredient) {
            // 3×3 crating: 100 mB water, no box (matches upstream ForestryUniHelper).
            RecipeManagers.carpenterManager.addRecipe(
                5,
                Fluids.WATER.getFluid(100),
                (ItemStack) null,
                product,
                "III",
                "III",
                "III",
                'I',
                ingredient);
            return true;
        }
    };

    ForestryIntegration() {
        super("Forestry", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walks.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.forestry()) {
                final UnaryOperator<ItemStack> resolveMain = resourceHandler::getMainItemStack;
                final int rewritten = rewriteCarpenter(resolveMain) + rewriteSqueezer(resolveMain)
                    + rewriteCentrifuge(resolveMain);
                // Additive complements (never a remove/rebuild, BB-3): recycle broken Bronze tools into
                // canonical ingots, and wire reciprocal crating/uncrating recipes for already-registered
                // Forestry crates. Each emits its own verify/journal line; the machines=3 line above and
                // the rewrite diff remain unchanged.
                final int added = registerBronzeRecipes(resourceHandler) + registerCrateRecipes(resourceHandler);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of "
                        + rewritten
                        + " Forestry machine recipes (carpenter grid outputs + squeezer container remnants"
                        + " + centrifuge products) to their canonical entries, and added "
                        + added
                        + " unification recipes (bronze tool recycling + crate wiring).");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=Forestry", "machines=3", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "All these bees... they can hurt, you know?";
    }

    /**
     * Carpenter: canonicalises each grid recipe's output <em>in place</em> (BB-3). The manager's
     * {@code recipes()} collection is live but unmodifiable — it is only ever iterated; the
     * rewrite writes the private {@code ShapedOreRecipe#output} field through the mixin accessor, which
     * is exactly what the inherited {@code IRecipe#getRecipeOutput()} reads.
     */
    private int rewriteCarpenter(final UnaryOperator<ItemStack> resolveMain) {
        final Collection<ICarpenterRecipe> recipes;
        if (RecipeManagers.carpenterManager == null) return 0;
        recipes = RecipeManagers.carpenterManager.recipes();
        if (recipes == null) return 0;
        final List<IShapedOreRecipeAccessor> gridRecipes = new ArrayList<>();
        for (final ICarpenterRecipe carpenterRecipe : recipes) {
            if (carpenterRecipe == null) continue; // never expected; stay defensive
            final IDescriptiveRecipe grid = carpenterRecipe.getCraftingGridRecipe();
            if (grid == null) continue; // a real ICarpenterRecipe always carries one
            // Only Forestry's ShapedRecipeCustom (= a ShapedOreRecipe) has the mutable output field we
            // rewrite; a foreign ICarpenterRecipe impl is safely skipped rather than crashing the walk.
            if (!(grid instanceof ShapedOreRecipe)) continue;
            gridRecipes.add((IShapedOreRecipeAccessor) (Object) grid);
        }
        final int n = rewriteCarpenterOutputs(gridRecipes, resolveMain);
        RewriteJournal.record("forestry", "carpenter", n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Forestry", "machine=carpenter", "rewritten=" + n);
        }
        return n;
    }

    /**
     * Squeezer: canonicalises each container recipe's {@code remnants} byproduct in place (BB-3). The
     * {@code containerRecipes} map is Forestry's public static {@code ItemStackMap}; each affected value
     * is replaced via {@code Map.Entry.setValue} under the SAME key — the entry count and the key
     * (empty container) are preserved, so no recipe is removed and none is added.
     */
    private int rewriteSqueezer(final UnaryOperator<ItemStack> resolveMain) {
        final Map<ItemStack, ISqueezerContainerRecipe> containerRecipes = SqueezerRecipeManager.containerRecipes;
        if (containerRecipes == null) return 0; // defensive: the field is a public static, never null
        final int n = rewriteContainerRecipes(containerRecipes, CONTAINER_VIEW.get(), resolveMain);
        RewriteJournal.record("forestry", "squeezer", n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Forestry", "machine=squeezer", "rewritten=" + n);
        }
        return n;
    }

    /**
     * Centrifuge: canonicalises each recipe's product-map keys in place (BB-3). The product map is the
     * private final {@code CentrifugeRecipe.outputs} field — reached via the late
     * {@code CentrifugeRecipeMixin} accessor — and {@code getProducts(Random)} (what the machine rolls)
     * reads exactly that map, so rewriting its <em>contents</em> is all it takes. The recipe object and
     * the manager's unmodifiable set are untouched: never a remove, never a rebuild. A recipe whose
     * product map is immutable (unusual for Forestry/GTNH) is skipped rather than aborting the module.
     */
    private int rewriteCentrifuge(final UnaryOperator<ItemStack> resolveMain) {
        final Collection<ICentrifugeRecipe> recipes;
        if (RecipeManagers.centrifugeManager == null) return 0;
        recipes = RecipeManagers.centrifugeManager.recipes();
        if (recipes == null) return 0;
        int rewritten = 0;
        for (final ICentrifugeRecipe recipe : recipes) {
            if (recipe == null || !(recipe instanceof CentrifugeRecipe)) continue; // foreign impl: no accessor
            final Map<ItemStack, Float> products = ((ICentrifugeRecipeAccessor) (Object) recipe).unidict$getProducts();
            if (products == null) continue; // defensive: the field is set in the constructor
            try {
                rewritten += rewriteCentrifugeProducts(products, resolveMain);
            } catch (final UnsupportedOperationException e) {
                // An immutable product map can't be cleared/refilled in place; skip this recipe only.
                UniDict.LOG.warn(threadName + "skipped an immutable centrifuge product map: " + e);
            }
        }
        RewriteJournal.record("forestry", "centrifuge", rewritten);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Forestry", "machine=centrifuge", "rewritten=" + rewritten);
        }
        return rewritten;
    }

    /**
     * Centrifuge product-map rewrite seam (T2-testable, no Forestry types on the test classpath):
     * maps each product key through the canonical resolver and, only when something actually changed,
     * clears and refills the SAME map in place (LinkedHashMap canonical keys, original chances). The
     * recipe's map reference (and therefore the recipe itself) is never swapped — only its contents,
     * so {@code getProducts(Random)} yields the canonical entries (BB-3). Never removes a recipe.
     *
     * @return number of product entries whose key mapped to a different canonical entry
     */
    static int rewriteCentrifugeProducts(final Map<ItemStack, Float> products,
        final UnaryOperator<ItemStack> resolveMain) {
        final Map<ItemStack, Float> mapped = new LinkedHashMap<>();
        int changed = 0;
        for (final Map.Entry<ItemStack, Float> entry : products.entrySet()) {
            final ItemStack product = entry.getKey();
            if (product == null) continue;
            final ItemStack canonical = resolveMain.apply(product);
            if (canonical != product) changed++;
            mapped.put(canonical, entry.getValue());
        }
        if (changed > 0) {
            products.clear();
            products.putAll(mapped);
        }
        return changed;
    }

    /**
     * Carpenter output-rewrite seam (T2-testable, no Forestry types on the test classpath): rewrites
     * each grid recipe's output against the canonical resolver, in place, via the accessor seam.
     * Never removes a recipe and never touches the recipe's inputs (BB-3).
     *
     * @return number of grid recipes actually rewritten
     */
    static int rewriteCarpenterOutputs(final Iterable<? extends IShapedOreRecipeAccessor> recipes,
        final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final IShapedOreRecipeAccessor recipe : recipes) {
            if (recipe == null) continue;
            final ItemStack output = recipe.unidict$getOutput();
            if (output == null) continue;
            final ItemStack canonical = resolveMain.apply(output);
            if (canonical != output) {
                recipe.unidict$setOutput(canonical);
                rewritten++;
            }
        }
        return rewritten;
    }

    /**
     * Squeezer container-recipe seam (T2-testable, no Forestry types on the test classpath): rewrites
     * each container recipe's {@code remnants} in place via {@code Map.Entry.setValue} (key + entry
     * count preserved — never a removal, never a global-registry mutation, BB-3). Recipes without a
     * byproduct ({@code null} remnants) are left alone.
     *
     * @return number of container recipes actually rewritten
     */
    static <V> int rewriteContainerRecipes(final Map<ItemStack, V> containerRecipes, final ContainerRecipeView<V> view,
        final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final Map.Entry<ItemStack, V> entry : containerRecipes.entrySet()) {
            final V recipe = entry.getValue();
            if (recipe == null) continue;
            final ItemStack remnants = view.getRemnants(recipe);
            if (remnants == null) continue; // no byproduct -> nothing to unify
            final ItemStack canonical = resolveMain.apply(remnants);
            if (canonical != remnants) {
                entry.setValue(view.rebuild(recipe, canonical));
                rewritten++;
            }
        }
        return rewritten;
    }

    /**
     * Production bronze-tool recycling wiring: when a unified {@code ingotBronze} container exists and
     * Forestry's broken Bronze tool items are in the item registry, adds one single-slot carpenter
     * recipe per tool via {@link #CARPENTER_ADDER}. Canonical output is baked at add time (pickaxe → 2,
     * shovel → 1), so no carpenter-output rewrite is needed for these recipes. Additive only (BB-3).
     *
     * @return number of recipes added
     */
    private int registerBronzeRecipes(final ResourceHandler resourceHandler) {
        if (RecipeManagers.carpenterManager == null) return 0;
        final UniResourceContainer ingotBronze = resourceHandler.getContainer("ingotBronze");
        if (ingotBronze == null) return 0; // no unified bronze ingot -> nothing canonical to recycle into
        final Item brokenPickaxe = GameData.getItemRegistry()
            .getRaw("Forestry:brokenBronzePickaxe");
        final Item brokenShovel = GameData.getItemRegistry()
            .getRaw("Forestry:brokenBronzeShovel");
        final int n = addBronzeRecycling(
            CARPENTER_ADDER.get(),
            ingotBronze.getMainEntry(1),
            brokenPickaxe == null ? null : new ItemStack(brokenPickaxe),
            brokenShovel == null ? null : new ItemStack(brokenShovel));
        RewriteJournal.record("forestry", "bronzeRecycling", n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Forestry", "module=bronzeRecycling", "added=" + n);
        }
        return n;
    }

    /**
     * Production crate wiring: for every unified {@code ingot} resource whose Forestry crate item
     * ({@code Forestry:crated<ResourceName>}) is <em>already registered</em>, adds the two reciprocal
     * carpenter recipes through {@link #CARPENTER_ADDER} — crating ({@code ingot<Name>} OD → crate) and
     * uncrating (crate → 9 × canonical ingot). A resource without a registered crate is skipped (logged
     * deferred), never fabricated: crate <em>item</em> creation at POST_INIT is the fragile part this
     * deliberately avoids (BB-3). Additive only.
     *
     * @return number of recipes added (crating + uncrating per wired crate)
     */
    private int registerCrateRecipes(final ResourceHandler resourceHandler) {
        if (RecipeManagers.carpenterManager == null) return 0;
        int added = 0;
        for (final Resource<UniResourceContainer> resource : resourceHandler.resources) {
            final UniResourceContainer ingot = resource.getChild("ingot");
            if (ingot == null) continue; // not an ingot-kind resource -> nothing to crate
            final Item crateItem = GameData.getItemRegistry()
                .getRaw("Forestry:crated" + resource.name);
            if (crateItem == null) {
                UniDict.LOG.debug(
                    threadName + "no registered Forestry crate for "
                        + ingot.name
                        + " — crate recipes skipped (item creation stays deferred).");
                continue;
            }
            added += addCrateRecipes(
                CARPENTER_ADDER.get(),
                ingot.getMainEntry(1),
                new ItemStack(crateItem),
                ingot.name);
        }
        RewriteJournal.record("forestry", "crateRecipes", added);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Forestry", "module=crateRecipes", "added=" + added);
        }
        return added;
    }

    /**
     * Bronze-tool recycling seam (T2-testable, no Forestry types on the test classpath): adds, when the
     * tool and the canonical ingot are present, a single-slot recipe per broken Bronze tool — pickaxe →
     * 2 × {@code canonicalIngot} copy, shovel → 1 × copy. {@code null} tools / ingot are no-ops; never
     * removes or rebuilds anything (BB-3).
     *
     * @return number of recipes added
     */
    static int addBronzeRecycling(final ICarpenterRecipeAdder adder, final ItemStack canonicalIngot,
        final ItemStack brokenPickaxe, final ItemStack brokenShovel) {
        if (adder == null || canonicalIngot == null) return 0;
        int added = 0;
        if (brokenPickaxe != null) {
            final ItemStack two = canonicalIngot.copy();
            two.stackSize = 2;
            if (adder.addSingleRecipe(two, brokenPickaxe)) added++;
        }
        if (brokenShovel != null) {
            final ItemStack one = canonicalIngot.copy();
            one.stackSize = 1;
            if (adder.addSingleRecipe(one, brokenShovel)) added++;
        }
        return added;
    }

    /**
     * Crate-recipe seam (T2-testable, no Forestry types on the test classpath): adds the two reciprocal
     * carpenter recipes for one already-registered crate — crating (a full 3×3 of {@code oredictName} →
     * {@code crateStack}) and uncrating ({@code crateStack} → 9 × {@code canonicalIngot} copy). Any
     * {@code null} input is a no-op; never removes or rebuilds anything (BB-3).
     *
     * @return number of recipes added (0, 1, or 2)
     */
    static int addCrateRecipes(final ICarpenterRecipeAdder adder, final ItemStack canonicalIngot,
        final ItemStack crateStack, final String oredictName) {
        if (adder == null || canonicalIngot == null || crateStack == null || oredictName == null) return 0;
        int added = 0;
        if (adder.addGridRecipe(crateStack, oredictName)) added++; // 9 × ingot -> crate
        final ItemStack nine = canonicalIngot.copy();
        nine.stackSize = 9;
        if (adder.addSingleRecipe(nine, crateStack)) added++; // crate -> 9 × canonical ingot
        return added;
    }
}
