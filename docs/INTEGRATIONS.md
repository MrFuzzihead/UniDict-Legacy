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
| Vanilla **Furnace**   | ✅ done      | M4     | public `FurnaceRecipes.smelting().getSmeltingList()`          | POST_INIT     | ✅ outputs-only (`setValue`)      | `PASS integration=furnace …`       |
| **AE2** (grinder)     | ✅ done      | M6     | public `AEApi.instance().registries().grinder()`              | POST_INIT     | ✅ (interface+fake seam)          | `PASS integration=ae2 rewritten=…` |
| **IC2** (10 machines) | ✅ done      | M6     | public `Recipes.*` machine maps                               | POST_INIT     | ✅ (`OutputRewriter`)             | `PASS integration=ic2 …`           |
| **IE** (4 machines)   | ✅ done      | M6     | public static `api.crafting` lists                            | POST_INIT     | ✅ (`OutputRewriter`, `List.set`) | `PASS integration=ie …`            |
| **Chest** (loot)      | ✅ done      | M6     | accessor seam (`ChestGenHooks`, `WeightedRandomChestContent`) | POST_INIT     | ✅ in-place item rewrite          | `PASS integration=Chest …`         |
| **EnderIO**           | ⏳ next (M7) | —      | accessor (`OreDictionaryPreferences.preferences`)             | —             | planned                           | `…=EnderIO`                        |
| **Railcraft**         | ⏳ next (M7) | —      | accessor (`BlastFurnaceCraftingManager.recipes`)              | —             | planned                           | `…=Railcraft`                      |
| **Thermal Expansion** | ⏳ next (M7) | —      | 3× accessor+invoker (`Furnace/Pulverizer/SmelterManager`)     | LOAD_COMPLETE | planned                           | `…=ThermalExpansion`               |

**Legend:** ✅ done · ⏳ next milestone · ~~struck~~ deferred/removed.

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
- **Load stage:** POST_INIT (default). Early-skips on an empty resource model.
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

## Next up — M7 (mixin-accessor integrations): EIO · Railcraft · TE

Each is a **mechanical repeat** of the Chest / M3 pattern: interface + `@Mixin` impl in
`mixins.early`/`…late` per target + fake; `TargetMods` gating where the mod must be loaded. `Forestry`
was **removed** from M7 by the 2026-08-12 scope rework (see the plan's "Milestone impact").

| Target                | Mixin                                                                  | Seam                                                                                 | Notes                                                                                                                   |
|-----------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| **EnderIO**           | `OreDictionaryPreferencesMixin`                                        | `IOreDictionaryPreferencesAccessor` (`preferences` map)                              | drop `FixedSizeList` usage if trivially `ArrayList`-able                                                                |
| **Railcraft**         | `BlastFurnaceCraftingManagerMixin`                                     | `IBlastFurnaceCraftingManagerAccessor` (`recipes` list, instance accessor)           |                                                                                                                         |
| **Thermal Expansion** | `FurnaceManagerMixin`, `PulverizerManagerMixin`, `SmelterManagerMixin` | `IFurnaceManagerAccessor` / `IPulverizerManagerAccessor` / `ISmelterManagerAccessor` | each `@Accessor` for `recipeMap` + `@Invoker` for the private `Recipe*` ctor; keep `@SpecifiedLoadStage(LOAD_COMPLETE)` |

TE invoker note (Spike B outcome, 2026-08-12): `@Invoker("<init>")` compiles and applies cleanly for
all three TE constructor signatures — **use `@Invoker`**. The 3 AT-entry fallback
(`PulverizerManager$RecipePulverizer`, `SmelterManager$RecipeSmelter`, `FurnaceManager$RecipeFurnace`)
is documented only, **no physical file** (a resource `*_at.cfg` would break the build under `applyJST`);
write + validate AT entries only if runtime `@Invoker` fails in-game.

**Gate:** full kept-mod `runClient` — one verify line per integration, all PASS; NEI stays safe (M4
main-thread rule still enforced).

---

## Deferred / not-yet-started (from the 2026-08-12 scope rework)

These are *deferred*, not forgotten — do not start them without re-reading the scope-rework notice at
the top of `PLAN.md`.

- **Crafting recipe rewrite + recipe-key rework** (was M5) — revisit only via non-destructive
  in-place rewriting.
- **`keepOneEntry` / `removeFromElsewhere` / global OreDictionary mutation** — the historical crash
  source; revisit only if a clear need arises.
- **Forestry** (carpenter outputs + crate runtime `ItemCrated` registration) — fragile, low value;
  removed from M7. Crate rework marked `// TODO: rework crate registration` if revisited.
- **Fuel / coke equivalence (BB-4)** — the first non-OD equivalence class, deferred until *after*
  M6/M7 but prioritized as the next build-better milestone.
- **NEI hiding** beyond what kept rewrites require; **`customUnifiedResources`**; **Galacticraft**
  (`// TODO` stub in `IntegrationModule`); **reload / re-run module**.
- **Et Futurum raw-ore → IC2** — researched, likely unnecessary (IC2 re-caches on tag registration);
  deferred pending an in-game confirmation run.
