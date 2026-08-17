# UniDict — Integration Status

A living reference for which machine / loot integrations have been **implemented**, what each one
does, and how it was verified. This is the single place to look before starting a new integration
(M7 is next): it records the established pattern, the seam every integration uses, and the exact
verify line each one is expected to emit.

- Companion docs: [`PLAN.md`](PLAN.md) (implementation plan + milestone gates) and
  [`STATUS.md`](STATUS.md) (the per-milestone checkbox tracker). This file is **integration-focused**:
  everything below refers to the *kept, ported* integrations only. Removed mods (AbyssalCraft,
  Foundry, FSP, Hydraulicraft, Magneticraft, Mekanism, NuclearCraft) are **not** listed.
- T1 = JVM unit test · T2 = seam/fake test · T3 = in-game `[unidict-verify]` line (`runClient`).
- Scope rework (2026-08-12): **full rework, not a faithful port.** Missing items are *deferred*,
  not forgotten — see the "Deferred / not-yet-started" section at the end.

---

## At a glance

| Integration           | Stage        | Landed | Source                                                        | Load stage    | Non-destructive (BB-3)            | Verify line                        |
|-----------------------|--------------|--------|---------------------------------------------------------------|---------------|-----------------------------------|------------------------------------|
| Vanilla **Furnace**   | ✅ done      | M4     | public `FurnaceRecipes.smelting().getSmeltingList()`          | LOAD_COMPLETE | ✅ outputs-only (`setValue`)      | `PASS integration=furnace …`       |
| **Crafting table**    | ✅ done      | M5     | in-place output mutator via accessors: `Shaped/ShapelessRecipes`, Forge `ShapedOreRecipe`/`ShapelessOreRecipe` (+ Forestry `ShapedRecipeCustom`), IC2 `AdvShapelessRecipe` (public field) and `AdvRecipe` (public-`final` field accessor); + `findMatchingRecipe` read-side safety net | LOAD_COMPLETE + server start (idempotent, re-run) | ✅ outputs rewritten in place (BB-3, never removes recipes) | `report rewrite=crafting table rewritten=N` |
| **AE2** (grinder)     | ✅ done      | M6     | public `AEApi.instance().registries().grinder()`              | POST_INIT     | ✅ (interface+fake seam)          | `PASS integration=ae2 rewritten=…` |
| **IC2** (10 machines) | ✅ done      | M6     | public `Recipes.*` machine maps                               | POST_INIT     | ✅ (`OutputRewriter`)             | `PASS integration=ic2 …`           |
| **IE** (4 machines)   | ✅ done      | M6     | public static `api.crafting` lists                            | POST_INIT     | ✅ (`OutputRewriter`, `List.set`) | `PASS integration=ie …`            |
| **Chest** (loot)      | ✅ done      | M6     | accessor seam (`ChestGenHooks`, `WeightedRandomChestContent`) | POST_INIT     | ✅ in-place item rewrite          | `PASS integration=Chest …`         |
| **EnderIO**           | ✅ done (T3 vfy 2026-08-14) | M7     | accessor (`OreDictionaryPreferences.preferences`)             | POST_INIT     | ✅ (`OutputRewriter`, lazy `OutputView`)| `…=EnderIO`                        |
| **Railcraft**         | ✅ impl + tests (T3 verified) | M7     | accessor (`BlastFurnaceCraftingManager.recipes`) + public `RockCrusherCraftingManager.getRecipes()` | POST_INIT     | ✅ (`OutputRewriter`, `List.set`) / in-place chance-outputs rewrite | `…=Railcraft` |
| **Thermal Expansion** | ✅ done (T3 vfy 2026-08-14) | M7     | 3× accessor+invoker (`Furnace/Pulverizer/SmelterManager`)     | LOAD_COMPLETE | ✅ (`OutputRewriter`, `Map.setValue`)  | `…=ThermalExpansion`               |
| **Forestry**           | ✅ impl + T2 + T3 | M7     | early `@Accessor` for Forge `ShapedOreRecipe.output` + public `SqueezerRecipeManager.containerRecipes` + late `@Accessor` for `CentrifugeRecipe.outputs` | POST_INIT | ✅ (in-place output / `Map.Entry.setValue` / in-place product map) | `…=Forestry` |
| **Galacticraft**       | ✅ done (impl; T3 to confirm) | M8     | public `CompressorRecipes.getRecipeList()` (`List<IRecipe>`), in-place via `IShapedRecipesAccessor` (shaped) + `IShapelessOreRecipeAccessor` (shapeless) | FMLServerStarting | ✅ (in-place output write) | `…=Galacticraft` |
| **Drops** (ground item) | ✅ done (new, not upstream) | 2026 | `EntityJoinWorldEvent` → `ResourceHandler.getMainItemStack` (clean-NBT only) | POST_INIT | ✅ output-only, identity-preserving | INFO (log only) |
| **Storage Drawers**    | ✅ impl + T2 (T3 to confirm) | 2026 | public `StorageDrawers.compRegistry.register(upper, lower, rate)` (same blessed path as Minetweaker `Compaction`) | POST_INIT | ✅ additive registry seeding — no recipe mutation | INFO (log only) |


**Legend:** ✅ done · 🟡 impl + tests (T3 verify pending) · ⏳ next milestone · ~~struck~~ deferred/removed.

### Cross-cutting module — `UnifyDrops` (landed, new — not from upstream)

Registered in `IntegrationModule` **outside** the `Config.integrationModule()` master switch (gated only on its own `Config.unifyDrops()`, default on), `UnifyDrops` listens for `EntityJoinWorldEvent` and upgrades a dropped/generated `EntityItem`'s stack to the canonical entry of its unified resource. Runs at POST_INIT (after the `ResourceHandler` pipeline) and is server-side only (`world.isRemote` guard). Only **clean** stacks are replaced — any stack with an NBT tag compound (enchanted/tagged/lore) is left untouched, and already-canonical drops are returned by identity (no spurious re-writes). It reuses the exact resolver (`ResourceHandler.getMainItemStack`) the machine integrations use, so it stays implicitly coupled to whatever the resource model covers (e.g. if BB‑4 fuels land, drops unify fuels automatically). Because it is a runtime listener, its effect is currently reported by the log line in `call()`; a T3 `[unidict-verify]` gate does not exist yet and is a candidate to add.

### Cross-cutting module — `StorageDrawersIntegration` (landed, new — not from upstream)

**Why it exists:** a **compacting drawer** resolves its three tiers (block / ingot / nugget) by FIRST
consulting `StorageDrawers.compRegistry` (a public `CompTierRegistry`) and, only if that misses, by
searching the live `CraftingManager` recipes — where `findMatchingModCandidate` prefers the candidate
whose owning mod matches the base item. So when the canonical copper *ingot* is TF's but the canonical
copper *block* is EtF's (`canonicalItemNames`), the recipe-search + mod-bias path picks the **TF**
block — the exact collision reported in `TODO.md`. This is a Storage-Drawers-internal canonicalism
question that drop-time `UnifyDrops` cannot fix (it only upgrades world-`EntityItem` drops, not the
drawer's stored view or manual in-inventory extraction).

**What it does:** at POST_INIT (after the `ResourceHandler` pipeline) it walks every unified resource,
resolves the canonical `block`/`ingot`/`nugget` entries from the model, and seeds
`StorageDrawers.compRegistry` through the mod's **own public `register(upper, lower, rate)` API** —
the same path Minetweaker's `Compaction` integration uses. Because the registry is consulted *before*
the recipe search, the compacting drawer then deterministically honors the canonical entries (e.g. a
drawer seeded with a TF copper ingot shows the **EtF block** as its top tier), independent of recipe
order or the mod-matching bias.

**Safety:** purely **additive** registry seeding — no recipe mutation, no mixins/ASM, no global
OreDictionary mutation (BB-3). Records match exact item pairs, so only the canonical chains we
register are affected. Registered pairs are idempotent (`register` unregisters a prior same-target
record, so re-running is safe). A degenerate same-item pair (block ≡ ingot) is never written.

**Verify:** T1 config parse (`storageDrawers` toggle), T2 on the `registerChain` seam (which pairs +
rate, null/degenerate/refused handling); T3 to confirm in-pack: insert a TF copper ingot into a
compacting drawer and confirm the block tier is EtF's.

---

## The pattern every landed integration follows

All five landed integrations share the same shape (established by M2/M4/M6), which is exactly what a
new M7 integration must replicate:

1. **Explicit registration** — one line in `IntegrationModule.init()` with an explicit `new`, gated
   on its config toggle: `if (Config.integrationModule() && Config.<mod>()) executor.add(new <X>Integration());`
   (no reflection / `searchForModules` — that was deleted in M2).
2. **Sequential execution** — runs on the calling thread via the M2 `LoadStageExecutor`
   (registration order == execution order; no thread pool).
3. **Non-destructive rewriting (BB-3)** — only *outputs* are rewritten; a recipe is never removed,
   and a global registry is never mutated. Where a target's recipe objects are mutable this is
   `setValue`/in-place; where they are immutable (IE), a rebuilt recipe replaces the original **at
   its index** (`List.set`) so count + order are preserved. Never `Iterator.remove` on shared recipe
   lists (guarded by `DeterminismGuardTest`).
4. **The `getMainItemStack` seam** — every rewrite resolves the canonical item through the resource
   model (`ResourceHandler.getMainItemStack`), so the engine is resource-agnostic.
5. **`OutputRewriter`** — the shared core (`rewriteOutputs(map, view, resolve)` for maps,
   `rewriteList(list, view, resolve)` for lists, `rewriteSingleOutputs` convenience). Furnace, IC2
   and IE all delegate to it; reuse it for new machines.
6. **Accessor-as-interface seam (T2)** — where a mod's internals are private, reach them through a
   mixin accessor declared as an interface (e.g. `IChestGenHooksAccessor`) with a `Fake…` in test
   sources. Chest introduced the first such seam (M6); M7's mixin integrations are mechanical
   repeats.
7. **Verify line + gate** — each integration emits a `[unidict-verify] PASS integration=<mod> …`
   line so its T3 gate is checkable, and prior lines must be **unchanged** (diffable dump) after the
   new integration lands.
---

## Vanilla Furnace — ✅ done (landed in M4)

- **Source:** public `FurnaceRecipes.smelting().getSmeltingList()` — no accessor needed.
- **What it rewrites:** the output `ItemStack` of every furnace smelting recipe to the canonical
  `getMainItemStack`. Non-destructive: only `setValue` outputs, never removes recipes, never mutates
  global registries.
- **Load stage:** LOAD_COMPLETE (via `@SpecifiedLoadStage`) — not the POST_INIT default. This global
  {@code FurnaceRecipes} map is read by vanilla, IC2's electric/induction furnace and Galacticraft's
  electric/arc furnace alike; running it after every mod's `init`/`postInit` is what makes late-registered
  smelting recipes (Et Futurum's raw-ore/raw-copper, GC) resolve to the priority (Thermal Foundation) ingot.
  Early-skips on an empty resource model.
- **Tests:** `FurnaceIntegrationTest` (3 T2 tests, fabricated-map via `OutputRewriter`).
- **T3 gate (M4, GTNH pack):** `Furnace Integration: rewrote outputs of 465 furnace recipes`.
- **Config:** `Config.furnace()`.

---

## AE2 — ✅ done (landed in M6)

- **Source:** public `AEApi.instance().registries().grinder()` — no accessor needed.
- **What it rewrites:** each `IGrinderEntry`'s primary + both optional OUTPUTs to the canonical
  `getMainItemStack`, in place, via a `GrinderRecipe` interface + fake seam. Never `Iterator.remove`,
  never `setInput()` (deviates from upstream, which removed/deduped and set input — we respect
  BB-3 + M5-deferred input work).
- **Note:** AE2 exposes no other unification-relevant machine — `IRegistryContainer` lists only the
  grinder as an output-rewrite candidate; the Inscriber's press outputs are AE2-specific (no
  cross-mod equivalents).
- **Tests:** `AE2IntegrationTest` (3 T2; no AE2 types on the test classpath).
- **T3 gate:** `PASS integration=ae2 rewritten=98`; runtime `AE2 Integration: rewrote outputs of 98
  AE2 grinder recipes`.
- **Config:** `Config.ae2()`.

---

## IC2 — ✅ done (landed in M6, over 10 machines)

- **Source:** public `Recipes.*` machine maps — no accessor needed.
- **What it rewrites:** RecipeOutput item-lists via the shared `OutputRewriter`, non-destructively
  (never removes recipes / never mutates global registries).
- **Machines (10):** centrifuge, metalformerRolling, blastfurance, compressor, macerator (upstream's
  five) **plus** extractor, metalformerExtruding, metalformerCutting, blockcutter, oreWashing.
- **History catch:** run-1 NPE — `Recipes.recycler.getRecipes()` returns `null` (recycler is a
  randomizer); recycler was removed and a null-map guard added.
- **Tests:** `IC2IntegrationTest` (3) + `OutputRewriterTest` (4) + `FurnaceIntegrationTest` (3).
- **T3 gate:** 11 `PASS integration=ic2 …` lines (10 machines, rewritten 16+14+0+14+0+0+7+7+5+6 =
  **69**); runtime `IC2 Integration: rewrote outputs of 69 IC2 machine recipes`.
- **Config:** `Config.ic2()`.

---

## Immersive Engineering — ✅ done (landed in M6, 4 machines)

- **Source:** public static `api.crafting` lists; crusher's public non-final `secondaryOutput` /
  `secondaryChance` used directly (no `UniCrusherRecipe` subclass).
- **What it rewrites:** Arc Furnace, Blast Furnace, Crusher, Metal Press — the complete IE
  `api.crafting` surface with cross-mod (metal) outputs. Coke Oven / Bottling Machine / Blueprint
  Crafting are mod-specific/non-metal and intentionally untouched.
- **Immutability wrinkle:** IE recipe `output` fields are `final` (verified via `javap`), so unlike
  Furnace/IC2 they cannot be mutated in place. Each affected recipe is **rebuilt** with the canonical
  `getMainItemStack` and replaced **at its index** (`List.set`) via `OutputRewriter.rewriteList`,
  preserving count + order.
- **Preserved details:** Arc furnace keeps `specialRecipeType` (upstream dropped it); Metal Press
  preserves a non-default `inputSize`. Upstream's `getFirstEntry` input lookup + `uniques` dedup were
  dropped (input rewriting = M5-deferred).
- **Tests:** `OutputRewriterTest` +2 (`rewriteList` count-preserving + unchanged-identity).
- **T3 gate:** `PASS integration=ie` — `machine=arcFurnace rewritten=12`, `machine=blastFurnace
  rewritten=2`, `machine=crusher rewritten=1`, `machine=metalPress rewritten=4`, `machines=4
  rewritten=19` — matches runtime `IE Integration: rewrote outputs of 19 IE machine recipes`.
- **Config:** `Config.ie()`.

---

## Chest (loot rewrite) — ✅ done (landed in M6) — *first accessor seam*

- **What it is:** a best-effort, minor rewrite of **loot** (not a machine). Rewrites the ITEM of every
  Forge chest-loot entry (dungeons, mineshafts, villages, strongholds, bonus chests) in place to the
  canonical `getMainItemStack`.
- **Non-destructive:** only the entry's item reference changes via the accessor seam; the `contents`
  list keeps its exact count/order and each entry keeps its weights/min/max (BB-3).
- **Accessors:**
  - `ChestGenHooksMixin` — `@Accessor` ×2: static `chestInfo` registry + instance `contents`.
    Forge-added fields → `remap=false`. Interface `IChestGenHooksAccessor`.
  - `WeightedRandomChestContentMixin` — a **1.7.10-necessitated second accessor**: upstream
    (1.12.2) rewrote `theItemId` as a *public* field, but in 1.7.10 it is `private` (notch `qx.b`,
    SRG `field_76297_b`). Exposes get+set; `remap=true` (vanilla field). Interface
    `IWeightedRandomChestContentAccessor`.
- **Tests:** `ChestIntegrationTest` (3 T2, fake-driven `rewriteContents`/`rewriteCategory`/
  `rewriteContent`); fakes `FakeChestGenHooksAccessor` + `FakeWeightedRandomChestContent`.
- **Config:** `Config.chest()`. Registered early via `Mixins.CHEST_GEN`.

### Toolchain rules learned here (apply to M7 accessors)

1. **Mixin AP:** an `@Accessor` stub must return the **exact field descriptor** (`HashMap`/`ArrayList`,
   not `Map`/`List`), or it fails `Could not locate @Accessor target …`.
2. **Mixin transformer:** an instance `@Accessor` must be `protected abstract` (a
   *non-abstract instance* accessor throws `InvalidAccessorException: @Accessor method … is not
   abstract` at apply time — the run-1 ChestGenHooksMixin crash). A **static** field accessor stays a
   concrete `private static` method (Java forbids `abstract static`; the proven `OreDictionaryMixin`
   pattern).

---

## M7 — mixin-accessor integrations: EIO · Railcraft · TE · Forestry (implemented; T3 verify pending)

Each was a **mechanical repeat** of the Chest / M3 pattern: interface + `@Mixin` impl in `mixins.early`/
`…late` per target + fake; `TargetMods` gating where the mod must be loaded. `Forestry` is included as a non-destructive sliver (carpenter grid outputs + squeezer container
remnants) — see its section below.

| Target                | Mixin                                                                  | Seam                                                                                 | Notes                                                                                                                   |
|-----------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| **EnderIO**           | `OreDictionaryPreferencesMixin`                                        | `IOreDictionaryPreferencesAccessor` (`preferences` map)                              | `FixedSizeList` dropped — `OutputRewriter.rewriteList`                                                                    |
| **Railcraft**         | `BlastFurnaceCraftingManagerMixin`                                     | `IBlastFurnaceCraftingManagerAccessor` (`recipes` list, instance accessor)           |                                                                                                                         |
| **Thermal Expansion** | `FurnaceManagerMixin`, `PulverizerManagerMixin`, `SmelterManagerMixin` | `IFurnaceManagerAccessor` / `IPulverizerManagerAccessor` / `ISmelterManagerAccessor` | each `@Accessor` for `recipeMap` + `@Invoker` for the private `Recipe*` ctor; keep `@SpecifiedLoadStage(LOAD_COMPLETE)` |
| **Forestry**          | `ShapedOreRecipeMixin` (early)                                        | `IShapedOreRecipeAccessor` (`ShapedOreRecipe.output`)                                | squeezer needs **no** mixin — public `SqueezerRecipeManager.containerRecipes` (`Map.Entry.setValue`) |
| **Forestry (centrifuge)** | `CentrifugeRecipeMixin` (late, `TargetMods.FORESTRY`) | `ICentrifugeRecipeAccessor` (`CentrifugeRecipe.outputs`)                        | rewrite is a clear+putAll of the private product map (in place)                   |

TE invoker note (Spike B outcome, 2026-08-12): `@Invoker("<init>")` compiles and applies cleanly for
all three TE constructor signatures — **use `@Invoker`**. The 3 AT-entry fallback
(`PulverizerManager$RecipePulverizer`, `SmelterManager$RecipeSmelter`, `FurnaceManager$RecipeFurnace`)
is documented only, **no physical file** (a resource `*_at.cfg` would break the build under `applyJST`);
write + validate AT entries only if runtime `@Invoker` fails in-game.

### Forestry — ✅ impl + T2 + T3 (in-game verified)

A deliberately scoped, **non-destructive (BB-3)** set — upstream's destructive crate/craft work is not
reproduced. Three rewrite surfaces:

- **Carpenter grid outputs (in place).** Source: `RecipeManagers.carpenterManager.recipes()` — a live but
  *unmodifiable* collection, iterated only, never mutated. Forestry's `ICarpenterRecipe.getCraftingGridRecipe()`
  is a `ShapedRecipeCustom extends ShapedOreRecipe`, so rewriting its output is just writing the private
  `ShapedOreRecipe.output` field — reached through the early `ShapedOreRecipeMixin` `@Accessor`
  (`IShapedOreRecipeAccessor` seam, `remap=false`: Forge-added field). No recipe is removed or rebuilt.
- **Squeezer container-recipe remnants (in place).** Source: Forestry's **public static**
  `SqueezerRecipeManager.containerRecipes` (`ItemStackMap`) — no mixin. Each value's materials byproduct is
  canonicalised by replacing the value under the **same** key (`Map.Entry.setValue`), preserving entry
  count and the empty-container key. Containers with no byproduct (`null` remnants) are skipped.
- **Centrifuge product keys (in place).** Source: `RecipeManagers.centrifugeManager.recipes()` —
  unmodifiable, iterated only. Each recipe's product map is the private final
  `CentrifugeRecipe.outputs` `Map<ItemStack, Float>`; `getAllProducts()` returns an `ImmutableMap`
  <em>copy</em>, but `getProducts(Random)` — what the machine actually rolls — reads the original map, so
  the late `CentrifugeRecipeMixin` `@Accessor("outputs")` exposes it and we clear + refill the canonical
  keys in place — never a recipe remove/rebuild (BB-3). This is what unifies a bee-comb → metal product
  when a pack adds such a recipe. An immutable product map (unusual) is skipped, not fatal.

All three rewrite only *outputs*; inputs, fluids and crate-*item* creation are untouched (inputs = M5-deferred;
fluids = no 1.7.10 fluid-equivalence model, BB-4; crate-item creation = fragile, deferred). Runs at POST_INIT
(default), early-skips on an empty resource model.

Two small **additive** complements (upstream's `bronzeThings()` + crate recipes, ported through Forestry's
*supported* `RecipeManagers.carpenterManager.addRecipe` — never the reflective `Set.add` upstream used) are
also here, driven by a neutral `ICarpenterRecipeAdder` seam (lazy `Supplier`, like the squeezer view). Only
ever *added* recipes; nothing is removed, replaced, or registered globally (BB-3):

- **Bronze-tool recycling.** When a unified `ingotBronze` container exists and Forestry's
  `Forestry:brokenBronzePickaxe` / `brokenBronzeShovel` are in the item registry, one single-slot carpenter
  recipe per tool is added (pickaxe → **2** × canonical ingot, shovel → **1** × canonical ingot). The canonical
  main entry is baked in at add time, so no carpenter-output rewrite is needed for these two recipes.
- **Crate wiring.** For every unified `ingot` resource whose Forestry crate item (`Forestry:crated<Name>`) is
  *already registered*, the two reciprocal carpenter recipes are added — crating (9 × `ingot<Name>`
  OreDictionary → crate) and uncrating (crate → 9 × canonical ingot). A resource without a registered crate is
  skipped (logged debug), never fabricated: creating a new `ItemCrated` at POST_INIT is the fragile part this
  deliberately avoids (post-lock item registration + Forestry's `createCrateRecipes()` drain has already run).

**T3 (in-game, 2026-08-15):** `PASS integration=Forestry machine=carpenter rewritten=7`,
`machine=squeezer rewritten=1`, `machine=centrifuge rewritten=0`, `machines=3 rewritten=8`; the report
reflects the three rewrites (`report rewrite=forestry carpenter=7 / squeezer=1 / centrifuge=0`);
`CentrifugeRecipeMixin` mixed cleanly into `forestry.factory.recipes.CentrifugeRecipe`; summary
`212 passed, 0 failed`, no strict FAIL, no UniDict error. (The centrifuge rewrote **0** products here because
Forestry 4.11.35's centrifuge recipes produce Forestry-unique bee output — the rewrite fires only when a
pack/config adds a comb → metal product, which is exactly the latent case it exists to cover.) The
bronze/crate *addition* lines (`module=bronzeRecycling` / `module=crateRecipes`) are emitted by the same
verify hook and are additive to (never a replacement of) the three rewrite lines.

- **Tests:** `ForestryIntegrationTest` (15 T2) — the three original rewrites via `FakeShapedOreRecipeAccessor`,
  a neutral `Holder` map + `ContainerRecipeView`, and the generic product-map seam; plus the bronze/crate
  additions via `FakeCarpenterRecipeAdder` (`addBronzeRecycling` / `addCrateRecipes`). **No Forestry types on
  the test classpath** (lazy `Supplier` views + our own accessor/adder interface seams).
- **Config:** `Config.forestry()` (`forestry` key). Registered with `Loader.isModLoaded("Forestry")` in
  `IntegrationModule`. Mixins: `Mixins.FORESTRY` (early, no `TargetMods` — `ShapedOreRecipe` is a Forge
  class, harmless with or without Forestry) and `Mixins.FORESTRY_CENTRIFUGE` (late,
  `TargetMods.FORESTRY` required-mod — `CentrifugeRecipe` only exists when Forestry is loaded).
- **Verify lines:** `[unidict-verify] PASS integration=Forestry machine=carpenter rewritten=N`,
  `machine=squeezer rewritten=N`, `machine=centrifuge rewritten=N`, `machines=3 rewritten=<sum>`;
  journal `forestry.carpenter` / `forestry.squeezer` / `forestry.centrifuge`.


### EnderIO — 🟡 impl + T2 tests (T3 pending)

- **What it rewrites:** Alloy Smelter (`IManyToOneRecipe` list) + SAG Mill (`Recipe` list) outputs to
  the canonical `getMainItemStack`, via `OutputRewriter.rewriteList` (`List.set`) — EIO recipes are
  immutable, so each affected recipe is rebuilt and replaced **at its index** (count + order preserved,
  never `Iterator.remove`, BB-3). Also **clears Ender IO's `OreDictionaryPreferences.preferences`**
  through the accessor seam so EIO yields the canonical ore-dict entry rather than a player preference.
- **Tests:** `EIOIntegration.rewriteRecipes` is a package-private generic seam over
  `OutputRewriter.rewriteList`; tests drive it with a neutral `Holder` view + assert
  `fixOreDictPreferences` via `FakeOreDictionaryPreferencesAccessor`. (4 T2 tests green; no EIO types on
  the test classpath — see the `Supplier` note below.)
- **Design note — lazy `OutputView`s:** EIO's `ALLOY_VIEW`/`SAG_VIEW` are declared as
  `Supplier<OutputView<…>>`, not eager static finals. An eager field's anonymous class forces
  `EIOIntegration.<clinit>` to resolve `IManyToOneRecipe` (a `checkcast` operand), which throws
  `NoClassDefFoundError` on a test classpath without Ender IO. The `Supplier` defers construction to
  `call()` (in-game, where Ender IO exists), so `<clinit>` stays Ender IO-free and the pure seams are
  T2-reachable.
- **T3 gate (pending):** expected `[unidict-verify] PASS integration=EIO machines=2 …`. **Config:**
  `Config.enderIO()`. Registered `Mixins.ENDER_IO`.

### Railcraft — 🟡 impl + T2 tests (T3 pending)

- **What it rewrites:** every Railcraft machine OUTPUT to the canonical entry, across two machines.
  - **Blast Furnace** — single final output; recipes are immutable (`BlastFurnaceRecipe`), so each is
    rebuilt and replaced **at its index** via `OutputRewriter.rewriteList` (`List.set`, BB-3). The
    `private final recipes` list is reached through `IBlastFurnaceCraftingManagerAccessor` (raw `List` —
    element type not on the JUnit classpath), replacing upstream's `Util.getField` reflection.
  - **Rock Crusher** — multi-output with per-output **chance**; `IRockCrusherRecipe#getOutputs()` is a
    public live `List<Map.Entry<ItemStack, Float>>` and `RockCrusherCraftingManager.getInstance().getRecipes()`
    is public, so **no accessor mixin is needed**. Each changed output entry is rewritten **in place**
    (`List.set` a new immutable entry preserving the `Float` chance) via
    `OutputRewriter.rewriteChanceOutputs` (BB-3: no recipe/output removed).
- **Tests:** `RailcraftIntegrationTest` drives the generic seams through `FakeBlastFurnaceCraftingManagerAccessor`
  (+ the chance-outputs seam via a neutral `CrushRecipe` holder). (4 T2 green, no Railcraft on test runtime.)
- **T3 gate (verified 2026-08-14):** `[unidict-verify] PASS integration=Railcraft machine=blastFurnace …` +
  `machine=rockCrusher …` + total `integration=Railcraft rewritten=…`. **Config:** `Config.railcraft()`.
  Registered `Mixins.RAILCRAFT` (blast-furnace accessor only).

### Thermal Expansion — 🟡 impl + T2 tests (T3 pending)

- **What it rewrites:** Redstone Furnace / Pulverizer / Induction Smelter recipe OUTPUTS to the canonical
  entry. Recipes are immutable with **package-private constructors**, rebuilt via the M0-Spike-B
  `@Invoker`s surfaced through the `IRecipeFurnaceFactory`/`IRecipePulverizerFactory`/`IRecipeSmelterFactory`
  interface seams, and replaced by `Map.setValue` (`OutputRewriter.rewriteOutputs`) — never a removal,
  never a global-registry mutation (BB-3). The per-manager `private static recipeMap` fields are read
  through the 3 accessor seams (raw `Map`). Runs at `@SpecifiedLoadStage(LOAD_COMPLETE)`.
- **Tests:** `TEIntegrationTest` drives the generic seam through `FakeFurnaceManagerAccessor` /
  `FakePulverizerManagerAccessor` / `FakeSmelterManagerAccessor` (2 T2 green).
- **Toolchain lesson — @Invoker surfaces via an interface, not a wrapper in the mixin package.** An
  earlier `TERecipeFactory` helper lived in `com.mrfuzzihead.unidict.mixins.late` so it could call the
  `@Invoker`s, and `TEIntegration` referenced it — but mixin packages are **closed**: any class in a
  `mixins.*.json`-owned package throws `IllegalClassLoadError … cannot be referenced directly` from a
  non-mixin caller, producing a fatal `LoaderException: NoClassDefFoundError` at `serverAboutToStart`
  (no `integration=TE` verify line). Fix: each `@Invoker` mixin **implements a plain factory interface**
  in `com.mrfuzzihead.unidict.te` (non-mixin), and `TEIntegration` casts the recipe value object to it —
  the `@Invoker` is invoked *inside* the mixin package (legal) and the rebuilt recipe is handed out via
  the interface. Same rule as the accessors (interface + mixin impl + fake).
- **T3 gate (pending reconfirm):** expected `[unidict-verify] PASS integration=TE machines=3 …`.
  **Config:** `Config.thermalExpansion()`. Registered `Mixins.THERMAL_EXPANSION`.

### Galacticraft — ✅ impl + T2 tests (T3 pending)

- **What it rewrites:** the compressor family — hand-cranked **Ingot Compressor** + powered **Electric
  Ingot Compressor** share the public static `CompressorRecipes.getRecipeList()` (`List<IRecipe>`). Each
  recipe's OUTPUT is rewritten to the canonical `getMainItemStack`, **in place** — GC's compressor recipes
  (vanilla `ShapedRecipes` and Forge `ShapelessOreRecipe`) hold a *mutable* output field (unlike IE/TE's
  immutable value objects), so a direct setter write is the whole rewrite (BB-3; no remove, no rebuild, no
  global registry mutation).
- **Two accessor seams** (both Forge/MC classes, `remap=false`):
  - `ShapedRecipes.recipeOutput` → existing early `ShapedRecipesMixin` /
    `IShapedRecipesAccessor` (already present for the crafting module).
  - `ShapelessOreRecipe.output` → **new** early `ShapelessOreRecipeMixin` /
    `IShapelessOreRecipeAccessor`. **1.7.10 wrinkle:** `ShapelessOreRecipe` `implements IRecipe` directly
    and owns its own private `output` — it does **not** extend `ShapedOreRecipe`, so it needs a dedicated
    mixin (the `ShapedOreRecipeMixin` does not cover it).
- **Load stage:** `FMLServerStartedEvent` — GC registers its *configurable* compressor recipes only in
  `RecipeManagerGC.setConfigurableRecipes()`, called from GC Core's `FMLServerStarting` handler (after any
  `LoadStage`; a `rewritten=0` at POST_INIT confirmed the list is cold there). So the compressor rewrite is
  NOT a module `LoadStage` thread — `UniDict.serverStarted` calls `IntegrationModule.runGalacticraftCompressor()`
  → `GalacticraftIntegration.runCompressor()`, gated on `Config.galacticraft()` + GC loaded.
- **Covered separately by the furnace integration (no code here):** GC's **Electric Furnace / Electric
  Arc Furnace** call vanilla `FurnaceRecipes.smelting().getSmeltingResult(...)`, i.e. the exact global map
  {@code FurnaceIntegration} rewrites. After the recent fix, that furnace rewrite runs at `LOAD_COMPLETE`
  (after every mod's `postInit`) so late-registered smelting recipes (Et Futurum raw copper, GC) resolve to
  the priority (Thermal Foundation) ingot — incl. tier-2 double-ingots in the arc furnace.
- **Deliberately NOT implemented:** the **Circuit Fabricator** (its `recipes` map is private and its wafer
  outputs are GC-specific — no cross-mod OD equivalents), the **Refinery**/oil and oxygen/fuel machines
  (fluid outputs — BB-4 fluid-equivalence deferred).
- **Tests:** `GalacticraftIntegrationTest` (5 T2) — drives the package-private `rewriteOutput` /
  `rewriteOutputs` BB-3 seam through `FakeShapedRecipesAccessor` / `FakeShapelessOreRecipeAccessor`
  (shaped + shapeless in-place, unchanged/null skipped, list preserved, unknown types skipped). No GC
  types on the test classpath.
- **Config:** `Config.galacticraft()` (`galacticraft` key), default on in `standard()`/`maxCompat()`,
  off in `minimal()`. Gated on `Loader.isModLoaded("GalacticraftCore")`; invoked from `UniDict.serverStarted`
  via `IntegrationModule.runGalacticraftCompressor()` (not the module executor — see Load stage).
  Mixin: `Mixins.GALACTICRAFT` (early, `ShapelessOreRecipe` is a Forge class — harmless with or without GC).
- **Verify lines:** `[unidict-verify] PASS integration=Galacticraft machine=compressor rewritten=N`;
  journal `galacticraft.compressor`. Plus the GC metals (`Titanium`, `Desh`, `MeteoricIron`) were added
  to the standard metal set (`ConfigPresets`).

**Gate (open → closed for EIO/Railcraft/TE):** full kept-mod `runClient` — one verify line per integration, all PASS; NEI stays safe
(M4 main-thread rule still enforced). EIO/Railcraft/TE were **T3-verified in the 2026-08-14 full dev-mod regression** (see `STATUS.md`): EIO `machines=2 rewritten=0`, Railcraft `rewritten=0`, TE `machines=3 rewritten=253` — all `PASS`. The remaining open T3 item is **Galacticraft** (compressor), whose verify line needs a run with `GalacticraftCore` at runtime (dev-LIGHT keeps GC off runtime; see gotchas #2 & #3).

## Environment gotchas & dev-tooling notes

Hard-won lessons about running the dev client and about what the code must tolerate. Read before
changing `dependencies.gradle` or adding an integration.

### 1. Guard every optional integration against its mod being absent

An integration for an optional mod must **never crash the game just because that mod isn't loaded**
on the running classpath (dev-light, or a user's own lighter pack). There are two guarded seams:

- **Integration classes** (`AE2Integration`, `IC2Integration`, `IEIntegration`, …) hold the target
  mod's types (e.g. `appeng.api.AEApi`, `blusunrize.immersiveengineering.api.*`, `ic2.api.*`). They
  are registered in `IntegrationModule.init()` **only when** `Config.<mod>() && Loader.isModLoaded("<modid>")`
  — vanilla targets (furnace, chest) need no guard. Without this, a kept integration's `call()` at
  POST_INIT throws `NoClassDefFoundError` (hit for real: AE2 off runtime → `NoClassDefFoundError:
  appeng/api/AEApi`).
- **Accessor/invoker mixins that reference a mod's classes** (e.g. the M7 TE
  `*RecipeInvoker`s targeting `cofh.thermalexpansion.*`) are gated in the **`Mixins` enum** with
  `.addRequiredMod(TargetMods.X)`. gtnhmixins then skips the bundle (the mixin classes are never
  loaded) when the mod is absent — the native, quieter mechanism for mixins. Don't register an
  un-gated mixin whose `@Mixin(...)`/`@Invoker` signature names a mod class.

Every `src/main` reference to a non-vanilla type must land behind exactly one of these two gates.
M7's new EIO/Railcraft/TE pairs must follow both (mixin `addRequiredMod` + integration
`Loader.isModLoaded`).

### 2. CoFH Core ASM crash — `HooksCore` / `World.collidingBoundingBoxes` (PARKED)

- **Symptom:** `java.lang.IllegalAccessError: tried to access field net.minecraft.world.World
  .collidingBoundingBoxes from class cofh.asmhooks.HooksCore` on the **first item-entity tick**
  (`EntityItem.onUpdate` → `Entity.moveEntity`). The client then "crashes" with
  `io.netty.channel.ChannelException: connection refused` — that's just the integrated server thread
  having already died. **Not a UniDict bug** (no UniDict frame in the stack).
- **Mechanism:** CoFH Core ASM-injects a collision hook into `Entity.moveEntity` that reads the
  *private* `World.collidingBoundingBoxes`. It only links if some other transformer widened that
  field. **GregTech (GT5-Unofficial) was doing that**, which is why earlier GT-full runs booted;
  removing GT exposed the frailty. CoFH/GT are **not required** (our own pack runs without them).
- **Messy fact:** **no GTNH-maintained CoFH Core exists** to swap in for the stale curse one. A real
  fix means widening the field (e.g. a UniDict AT whose only entry is an MC class — `applyJST`
  allows MC-class entries, rejects non-MC). **Parked**; revisit only before a full-stack `runClient`
  (TE at runtime).
- **While parked:** keep CoFH off the runtime classpath when not testing TE (see #3).

### 3. "dev-LIGHT" classpath — heavy mods are `compileOnly`, not `devOnly`

- The heavy GTNH mods (**TE, TF, EnderIO, EnderCore, Forestry, Railcraft, Mantle, TiC, IE, AE2**)
  transitively drag **CoFH Core** onto the runtime classpath. To test a focused integration (IC2)
  free of CoFH/GT, they're declared `compileOnly` (still on the compile classpath — every
  integration and the TE invoker mixins still compile) while **IC2, NEI, CodeChickenCore, Et Futurum**
  stay `devOnlyNonPublishable`/`runtimeOnlyNonPublishable` at runtime.
- **AE2 surprise:** AE2 also drags `cofh-core` transitively, so keeping AE2 at runtime re-adds CoFH —
  it's why AE2 had to drop to `compileOnly` too.
- Flip a mod back to `devOnlyNonPublishable` only for a full-stack M7/M9 regression run (and then
  #2's CoFH issue must be solved first).

### 4. Galacticraft → GregTech (transitive)

`Galacticraft` transitively drags in **`GT5-Unofficial`** (GregTech 5), which replaces IC2 machine
behavior (incl. the macerator) and confounds IC2 testing. It is now excluded explicitly from the GC
dependency in `dependencies.gradle` (`exclude module: "GT5-Unofficial"`), alongside TinkersConstruct
(to keep the heavy TiC/CoFH transitive set off the dev classpath). GC's own deps pin NEI + IC2 +
GTNHLib at the same versions we already use, so those flow through additively.

---

## Deferred / not-yet-started (from the 2026-08-12 scope rework)

These are *deferred*, not forgotten — do not start them without re-reading the scope-rework notice at
the top of `PLAN.md`.

- **Crafting recipe rewrite + recipe-key rework** (was M5) — revisit only via non-destructive
  in-place rewriting.
- **`keepOneEntry` / `removeFromElsewhere` / global OreDictionary mutation** — the historical crash
  source; revisit only if a clear need arises.
- **Forestry: crate-*item* creation + fluid outputs** — the carpenter/squeezer/centrifuge rewrites and the
  bronze-tool recycling + crate-*recipe* wiring are implemented (all non-destructive/additive). These stay
  deferred: creating a new runtime `ItemCrated` (fragile — post-lock item registration + Forestry's
  `createCrateRecipes()` drain already run; marked `// TODO: rework crate registration`) and **fluid outputs**
  (no 1.7.10 fluid-equivalence model, BB-4).
- **Fuel / coke equivalence (BB-4)** — the first non-OD equivalence class, deferred until *after*
  M6/M7 but prioritized as the next build-better milestone.
- **NEI hiding** beyond what kept rewrites require; **`customUnifiedResources`**;
  **reload / re-run module**.
- **Et Futurum raw-ore → IC2** — researched, likely unnecessary (IC2 re-caches on tag registration);
  deferred pending an in-game confirmation run.
