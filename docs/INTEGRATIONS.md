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
| **EnderIO**           | 🟡 impl + tests (T3 pending) | M7     | accessor (`OreDictionaryPreferences.preferences`)             | POST_INIT     | ✅ (`OutputRewriter`, lazy `OutputView`)| `…=EnderIO`                        |
| **Railcraft**         | 🟡 impl + tests (T3 pending) | M7     | accessor (`BlastFurnaceCraftingManager.recipes`)              | POST_INIT     | ✅ (`OutputRewriter`, `List.set`)      | `…=Railcraft`                      |
| **Thermal Expansion** | 🟡 impl + tests (T3 pending) | M7     | 3× accessor+invoker (`Furnace/Pulverizer/SmelterManager`)     | LOAD_COMPLETE | ✅ (`OutputRewriter`, `Map.setValue`)  | `…=ThermalExpansion`               |

**Legend:** ✅ done · 🟡 impl + tests (T3 verify pending) · ⏳ next milestone · ~~struck~~ deferred/removed.

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

## M7 — mixin-accessor integrations: EIO · Railcraft · TE (implemented; T3 verify pending)

Each was a **mechanical repeat** of the Chest / M3 pattern: interface + `@Mixin` impl in `mixins.early`/
`…late` per target + fake; `TargetMods` gating where the mod must be loaded. `Forestry` was **removed**
from M7 by the 2026-08-12 scope rework (see the plan's "Milestone impact").

| Target                | Mixin                                                                  | Seam                                                                                 | Notes                                                                                                                   |
|-----------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| **EnderIO**           | `OreDictionaryPreferencesMixin`                                        | `IOreDictionaryPreferencesAccessor` (`preferences` map)                              | `FixedSizeList` dropped — `OutputRewriter.rewriteList`                                                                    |
| **Railcraft**         | `BlastFurnaceCraftingManagerMixin`                                     | `IBlastFurnaceCraftingManagerAccessor` (`recipes` list, instance accessor)           |                                                                                                                         |
| **Thermal Expansion** | `FurnaceManagerMixin`, `PulverizerManagerMixin`, `SmelterManagerMixin` | `IFurnaceManagerAccessor` / `IPulverizerManagerAccessor` / `ISmelterManagerAccessor` | each `@Accessor` for `recipeMap` + `@Invoker` for the private `Recipe*` ctor; keep `@SpecifiedLoadStage(LOAD_COMPLETE)` |

TE invoker note (Spike B outcome, 2026-08-12): `@Invoker("<init>")` compiles and applies cleanly for
all three TE constructor signatures — **use `@Invoker`**. The 3 AT-entry fallback
(`PulverizerManager$RecipePulverizer`, `SmelterManager$RecipeSmelter`, `FurnaceManager$RecipeFurnace`)
is documented only, **no physical file** (a resource `*_at.cfg` would break the build under `applyJST`);
write + validate AT entries only if runtime `@Invoker` fails in-game.

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

- **What it rewrites:** every blast-furnace recipe OUTPUT to the canonical entry. Recipes are immutable
  (`BlastFurnaceRecipe`), so each is rebuilt and replaced **at its index** via
  `OutputRewriter.rewriteList` (`List.set`, BB-3). The `private final recipes` list is reached through
  `IBlastFurnaceCraftingManagerAccessor` (raw `List` — element type not on the JUnit classpath),
  replacing upstream's `Util.getField` reflection.
- **Tests:** `RailcraftIntegrationTest` drives the generic seam through `FakeBlastFurnaceCraftingManagerAccessor`
  (2 T2 green).
- **T3 gate (pending):** expected `[unidict-verify] PASS integration=Railcraft …`. **Config:**
  `Config.railcraft()`. Registered `Mixins.RAILCRAFT`.

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

**Gate (open):** full kept-mod `runClient` — one verify line per integration, all PASS; NEI stays safe
(M4 main-thread rule still enforced). The three integrations are currently verified only at T2 (JUnit);
T3 verify lines are blocked on the dev-LIGHT → full-stack flip (see gotchas #2 & #3).

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

`Galacticraft` (deferred stub) transitively drags in **`GT5-Unofficial`** (GregTech 5), which
replaces IC2 machine behavior (incl. the macerator) and confounds IC2 testing. Kept off the
classpath (commented in `dependencies.gradle`) until the GC stub is actually worked on.

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
