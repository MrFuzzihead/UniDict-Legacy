# Implementation Plan: Reimplementing UniDict as a Mixin-Based Mod (v2 — gate-driven)

> **v2 restructure (supersedes v1):** the same 48 implementation steps are re-sequenced into ten
> milestones (**M0–M9**), each ending in a *gate* that can be checked in a few minutes with
> `./gradlew test build runClient`. Every v1 step is preserved and tagged `(v1 step N)`; use
> the §"Step → milestone traceability" table to relocate anything.
>
> The two top risks from v1 (`@Accessor` on static fields, `@Invoker` on private constructors)
> are now **spiked in M0** so no architecture depends on an unverified mechanism. The
> deterministic-execution requirements (kill the thread pool, explicit integration order) move
> **before** the feature ports (M2), so every later integration is an isolated, diffable change.

## Scope confirmation (unchanged)

- **Keep & port:** Crafting, Chest, Furnace, AE2, EnderIO, Forestry, IC2, IE, Railcraft, Thermal Expansion + API helpers (ForestryUniHelper, FurnaceUniHelper, IEUniHelper, TConUniHelper).
- **Drop:** AbyssalCraft, Foundry (+FoundryUniHelper), FSP, Hydraulicraft, Magneticraft, Mekanism, NuclearCraft.
- **Defer:** Galacticraft (stub note only); Forestry crate system (deferred — the carpenter/squeezer
  sliver is implemented in M7; crates stay out).
## ⚠ Scope rework — 2026-08-12 (decision; supersedes the scope above and re-scopes the milestones below)

**Direction:** full rework, not a faithful port. Original UniDict is treated as a *reference for
mechanics*, not a spec to reproduce. We keep only the low-risk, high-value behavior, rebuild it
with better code, and prioritize the features a mature "unify" mod should have had.

**Kept from the original feature set (rebuilt, "better code", covered by T1/T2/T3):**
- **Machine output rewrites — the flagship.** Vanilla furnace, AE2 grinder, IC2 (macerator,
  compressor, centrifuge, metal-former roller, blast furnace), IE (arc, blast, crusher, metal
  press), TE (redstone furnace, pulverizer, induction smelter), EIO (alloy smelter, SAG mill),
  Railcraft (blast furnace). Rewrite existing recipe *outputs* to the canonical entry,
  **non-destructively** — never remove a recipe unless required, never mutate forge's global
  OreDictionary source of truth.
- **Chest / loot rewrite** (`ChestIntegration`) — best-effort, minor.

**Deferred from the original (NOT ported now):**
- Crafting recipe rewrite + recipe-key rework (was M5) — it removed other mods' recipes from
  `CraftingManager`; revisit only later through non-destructive rewriting.
- `keepOneEntry` / `removeFromElsewhere` / global OreDictionary mutation — invasive; the
  historical crash source. Revisit only if a clear need arises.
- NEI hiding / item hiding beyond what the chosen rewrites require.
- Forestry **crate registration** (runtime `ItemCrated`) and **fluid outputs** (no 1.7.10 fluid-equivalence
  model, BB-4) — the carpenter/squeezer/centrifuge sliver is implemented (M7, non-destructive).
- API/helper surface (`UniDictAPI` + Forestry/Furnace/IE/TCon helpers) — mostly deferred; keep
  only the minimal read surface a kept integration actually uses.
- `customUnifiedResources`, Galacticraft stub.

**New first-class features (build-better; focus these first):**
1. **Transparency** — an in-game unification report/audit: per resource, the canonical entry,
   every variant, the owning mod, and what was rewritten. This productionizes the
   `[unidict-verify]` harness into a real user-facing + developer-facing feature
   (e.g. `/unidict report`). It is also the main way we "prove it works" at each stage.
2. **Cleaner config** — grouped categories plus **presets** (e.g. minimal / standard /
   max-compat), fewer overlapping knobs than the original.
3. **Non-destructive rewriting** — machine/loot rewrites never delete or mutate global
   registries; craft rewriting, when it returns, rewrites in place.
4. **Broader equivalence** — extend unification beyond OreDictionary tag-equality to other
   equivalence classes over time.

**Explicitly deferred (infrastructure):** reload / re-run module — a load-time one-shot is
acceptable for now.

**Unchanged:** M0 infrastructure — JUnit harness, `@Accessor` / `@Invoker` risk spikes,
sequential/deterministic execution, accessor-as-interface seam (T2). These apply to all kept work.

**Milestone impact (re-scope of the sections below):**
- **M1** (pure resource model) — keep: the kind×resource selection core is the substrate for
  main-entry selection and for the transparency report.
- **M2** (config + determinism/infra) — keep; config work absorbs the *presets* feature.
- **M3** (UniOreDictionary seam) — keep, trimmed to a **read-only** accessor (+ `getFirstEntry`
  for the IE crusher); mutation methods deferred.
- **M4** — reframed: the vertical slice is core selection + **vanilla furnace rewrite** + the
  first transparency-report output. No NEI hiding.
- **M5** (crafting rewrite + recipe-key) — **DEFERRED.**
- **M6** — machine rewrites: Furnace, AE2, IC2, IE + Chest (loot). One PR each.
- **M7** — machine rewrites (accessor/mixin): EIO, Railcraft, TE, Forestry (carpenter + squeezer).
- **M8** — mostly deferred; keep only the minimal read surface kept integrations need.
- **M9** — cleanup + full regression (unchanged).
- **Build-better track** — see dedicated section below.


## Build-better track (the "accomplish-more" features)

These are first-class deliverables, not post-port extras. Each is testable and ships with its
own verify line. Priority order as decided 2026-08-12: transparency, config presets,
non-destructive rewriting, broader equivalence. **Reload / re-run module is deferred.**

### BB-1 — Transparency (unification report / audit)
- **Scope:** a dev + user command (e.g. `/unidict report`) that prints, per resource, the
  canonical (main) entry, every variant and owner mod, and — for the kept integrations — what
  got rewritten. Productionizes the `[unidict-verify]` harness; the report doubles as the T3
  oracle ("prove it works" at every stage).
- **Builds on:** M1 selection core, M2 sequential execution, the T3 writer.
- **Tests:** T1 on a pure `ReportEntry` computation/formatting; T3 grep of report lines.
- **Gate:** `[unidict-verify] report` output is stable and diffable across runs; every kept
  rewrite has a matching report line.

### BB-2 — Cleaner config + presets
- **Scope:** grouped categories, fewer overlapping knobs than the original (dedupe
  `ownerOfEveryThing` vs per-kind owners vs `enableSpecificKindSort`), and **presets** —
  minimal / standard / max-compat. Presets pick defaults; explicit keys still override.
- **Builds on:** M2 Config port; keep legacy key names as aliases where cheap for compat.
- **Tests:** T1 `Config` fixture covers every key + preset resolution.
- **Gate:** each preset yields a documented, deterministic default set; fixture test green.

### BB-3 — Non-destructive rewriting
- **Scope:** machine/loot rewrites rewrite outputs **in place** and never remove or mutate
  global registries (forge OD lists, CraftingManager, a mod manager's structural recipe list).
- **Tests:** T2 on a fake machine-recipe map asserts rewrite changes only outputs and preserves
  entry counts; a grep guard ensures no kept integration calls a destructive API.
- **Gate:** grep guard passes (no `Iterator.remove` on shared recipe lists in kept code);
  fabricated-map T2 test green.

### BB-4 — Broader equivalence
- **Scope:** extend unification beyond OreDictionary tag-equality to other equivalence classes
  (e.g. fuel/energy-providing stacks, "same item" grouped across mods) as the core matures.
  Research + spike first; exact equivalences are TBD and added iteratively.
- **Tests:** T1 on each equivalence classifier; T3 verify lines per equivalence class.
- **Gate:** at least one non-OD equivalence class implemented, tested, and reported.
- **Scope decision (2026-08-13):** the first non-metal equivalence class is **fuel/coke** (coal coke from
  Railcraft + IE). It is **deferred until after M6/M7 but prioritized as the next build-better milestone** —
  it is NOT part of the metal machine-rewrite work. Rationale + plan:
  - The unification *engine* is already resource-agnostic (`ResourceHandler.getMainItemStack` is what every
    machine rewrite calls), so registering coke needs no machine code — only (a) a new resource *kind* for
    the fuel/coke form in the M1 taxonomy (within the 64-kind guard), (b) a config surface (resurrect the
    deferred `customUnifiedResources` / a "fuels" preset toggle), and (c) a **fuel-equivalence classifier**.
  - Fuels are NOT naive OD tag-equality: metals are near-interchangeable, but coke variants can differ in
    burn time and some recipes/fuel slots check the exact item. The classifier must pick the canonical coke
    by fuel value and preserve per-item burn semantics (config-gated, opt-in).
  - Before implementing, verify in the live GTNH pack that Railcraft/IE coke actually share a tag and what
    each fuel value is, so the canonical-selection rule is data-backed.
  - Shipping shape: register Coke, let the existing machine rewrites pick up its outputs via
    `getMainItemStack`, add a verify/report line, then satisfy the BB-4 gate with a T3-run equivalence line.

### Deferred (infrastructure)
- Reload / re-run module — a load-time one-shot is acceptable for now.

## Boilerplate checkpoint (repo state at v2)

- GTNH toolchain (gtnhconvention 2.x, Gradle 9.3.1, Java 25 via Jabel → J8 bytecode), MC 1.7.10 / Forge 10.13.4.1614.
- Mixins on: `usesMixins = true`, `usesMixinDebug = false` (turn on in dev when debugging), `separateMixinSourceSet` empty.
- `Mixins` / `TargetMods` enums + `EarlyMixinsLoader` / `LateMixinsLoader` exist, but the enum still references **non-existent example mixin classes** → must be emptied in M0 or the game won't boot.
- `mixins.unidict.json`, `mixins.unidict.early.json` (pkg `com.mrfuzzihead.unidict.mixins.early`), `mixins.unidict.late.json` (pkg `...late`) already exist.
- NEI 2.8.122-GTNH is in as `devOnlyNonPublishable` (uncommitted working-tree change — keep). No JUnit in the `test` source set yet.
- `migrate/UniDict-Upstream/` holds the authoritative `wanion.unidict` source to port (gitignored).

## 0. Testing doctrine (read first)

Three verification tiers. Every gate below is expressed in these terms.

- **T1 — JVM unit test** (`./gradlew test`): pure logic, zero `net.minecraft`/`net.minecraftforge` imports. Targets: kind taxonomy, `MetaItem` hash arithmetic, recipe keys, Config parsing, selection rules, comparator ordering, load-stage/manager ordering.
- **T2 — seam/fake test** (`./gradlew test`): logic that touches MC *types* but not MC *statics*, run through fakes. **Rule: every mixin accessor is declared as an interface** (`IOreDictionaryAccessor`, `IChestGenHooksAccessor`, …) whose `@Mixin` class is the live impl and whose `Fake…` lives in `src/test`. Same seam idea for the OreDict view and the MetaItem provider.
- **T3 — in-game regression harness** (`./gradlew build runClient`, then grep): a dev-only verify routine emits `[unidict-verify] PASS|FAIL <feature>` lines. Because execution is sequential (M2), the output is deterministic and diffable.

**Rules that make all of this possible**

1. Accessor mixins are "interface + mixin impl + test fake" (T2).
2. Decision logic lives in pure MC-free helpers (`SelectionRules`, `RecipeKey`, kind registry); MC glue stays thin.
3. Integration execution is explicit and sequential — never off the main thread.
4. Every behavior promise gets a verify line; a feature is "done" when its line is PASS in the full suite.
5. One risk per milestone; risky mechanisms are spiked before the port depends on them.
## M0 — Harness & risk retirement *(v1 steps 1–3; spike of v1 steps 11 & 27)*

**Goal:** prove the toolchain (tests, mixin mechanism, CI) and retire v1's two biggest risks before anything depends on them.

Tasks

- **Fix the boot:** empty/replace the placeholder `Mixins` enum entries (see boilerplate checkpoint) so the project boots with zero registered mixins.
- **Dependencies (v1 step 2):** finalize `dependencies.gradle` — keep NEI, CodeChickenCore, Mantle, Railcraft, TConstruct, Thermal Expansion/Foundation, AE2, IC2, Forestry, EnderCore/EnderIO, IE, Galacticraft (future stub); remove AbyssalCraft, Foundry, FSP, HydCraft, Magneticraft, Mekanism, NuclearCraft, k4lib. UniMixins comes in implicitly via `usesMixins`.
- **JUnit:** add `testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")` + platform launcher (and `tasks.test { useJUnitPlatform() }` in `addon.gradle.kts` if `./gradlew test` doesn't already use the platform). Land one demo T1 test; confirm `./gradlew test build` green; confirm CI (`.github/workflows/build-and-test.yml` → shared GTNH workflow) runs `test`.
- **Verify harness scaffold:** dev-only `[unidict-verify]` writer, system-property-gated (`-Dunidict.devVerify=true`), stubbed initially.
- **Spike A — static `@Accessor` on `OreDictionary`:** minimal mixin in `mixins.early` registered via the `Mixins` enum, exposing the 5 static fields (`nameToId`, `idToName`, `idToStack`, `idToStackUn`, `stackToId`) and logging sizes at `Init`. Success ⇒ lock the M3 interface design. Failure ⇒ adopt the `@Inject`-based accessor fallback now and update M3.
- **Spike B — `@Invoker` on a private constructor:** target a vanilla class with a non-public ctor (try `Block`, `Vec3`, `Potion`) or a tiny TE probe in `late` with `TargetMods` gating; alternatively pre-write the 3 TE AT entries (`FurnaceManager$RecipeFurnace`, `PulverizerManager$RecipePulverizer`, `SmelterManager$RecipeSmelter` ctors) so M7 becomes a mechanical swap. Record the outcome in M7.

Toolchain adaptation (replaces v1 step 3): with gtnhmixins, mixin classes are declared in the **`Mixins` enum** and auto-resolved against the config packages (early → `...mixins.early`, late → `...mixins.late`). Never hand-edit a JSON `"mixins": [...]` list.

Create `docs/TestPlan.md` (defines T1/T2/T3 + how to run a verification pass) and `docs/STATUS.md` (checkboxes per milestone/integration, updated every commit).

**Gate**

```
[ ] `./gradlew test` && `./gradlew build` green (demo test runs; CI passes)
[ ] `runClient` boots; Spike A logs `accessor OK: nameToId.size=<n>` (or fallback documented)
[ ] `docs/TestPlan.md` + `docs/STATUS.md` exist and are reviewed
```
---

## M1 — Pure resource model (kinds) *(v1 steps 6, 7 + pure part of v1 step 4)*

**Files:** `resource/Resource`, `resource/UniAttributes`, new pure hash helpers (the `id | damage<<16` arithmetic extracted from `MetaItem`).

- Port `Resource` unchanged in behavior (bitfield kind taxonomy) but **enforce the 64-kind limit with a guard and test**, not just a comment (v1 step 6 upgrade).
- Port `UniAttributes` unchanged (package-private value pair).
- Extract `MetaItem`'s hash arithmetic as pure static helpers; the MC-glue `get(ItemStack)` wrapper lands in M4.

Tests (T1): kind bit assignment; `register`/`registerAndGet` idempotence; 64-kind overflow guard; `addChild` merge + name-suffix semantics; `filteredClone` bit math; `getResources` AND-filter; kind name ↔ bit round-trip; `toString`.

**Gate:** `./gradlew test` green; new pure packages contain **zero** `net.minecraft*` imports (scriptable grep gate).

---

## M2 — Determinism & infra rework FIRST *(v1 steps 5, 30–38)*

**Goal:** no thread pool, no DI container, explicit sequential ordering — in place **before** any feature is ported, so every later integration is an isolated diff whose verify output is stable run-to-run.

**Files:** `Config`, `UniDict`, `module/AbstractModule`, `module/AbstractModuleThread`, `module/SpecifiedLoadStage`, `LoadStage`, `IntegrationModule`, `common/Dependencies`, `common/Instantiator`, `common/FixedSizeList`, `common/Util`, `common/SpecificKindItemStackComparator`.

- **`Config` (v1 step 5):** port preserving every user-facing key — `keepOneEntry`, `inputReplacement`, `keepOneEntryModBlackSet`, `autoHideInNEI`, `hideInNEIBlackSet`, `kindDebugMode`, `enableSpecificKindSort`, `ownerOfEveryThing`, `metalsToUnify`, `childrenOfMetals`, `resourceBlackList`, `customUnifiedResources`, per-kind owner maps, kept integration toggles (drop toggles for the 7 removed mods). Clean up the static-init pattern. Add a sample `.cfg` fixture so T1 tests cover every kept key (name → default → type).
- **Thread pool (v1 step 30):** `AbstractModule.start` → sequential `for` loop over the thread list, calling each `AbstractModuleThread.call()`. Log total time as before. Keep `Callable<String>` (v1 step 31).
- **Explicit registry (v1 steps 32–33):** `IntegrationModule` builds an explicit list with **explicit `new`** per integration (drop `Class::newInstance`), minus the 7 removed integrations; `UniDict` registers via `moduleHandler.addModule(new IntegrationModule())`; `searchForModules` and the `@Module` annotation are deleted.
- **DI removal (v1 step 34):** delete `Dependencies`/`DependenceWatcher`/`Instantiator`; instantiate `UniDictAPI` + `ResourceHandler` directly and expose via `UniDict` statics.
- **Trim (v1 steps 35–36):** `FixedSizeList` → `ArrayList` (capacity hint) unless the audit says otherwise; keep `Util.getModName` + `itemStackComparatorByModName`, delete `getField`/`setField`.
- **Port (v1 steps 37–38):** `SpecificKindItemStackComparator` (static comparator cache; `nullify()` at LoadComplete), `LoadStage`, `SpecifiedLoadStage`.

Tests (T1/T2): `Config` fixture round-trip; `Manager`/`LoadStage` — registration order == execution order; zero threads reachable in the integration path.

**Gate:** `./gradlew test build` green; `runClient` boots with all integrations off; grep confirms no `ExecutorService` in `src/main` integration code.

**M2 sequencing (scope decision 2026-08-13):** M2 lands as **two commits**. **Commit 1** = determinism/infra, in place before any integration exists so every later integration is an isolated, diffable change: sequential `LoadStageExecutor` (replaces the thread pool; registration order == execution order), explicit-`new` registry in `IntegrationModule`, DI classes deleted, `Util` trimmed, `LoadStage`/`SpecifiedLoadStage` ported, and a `DeterminismGuardTest` JUnit grep guard (the enforceable form of the gate grep above). **Commit 2** = `Config` port + BB-2 presets; it also ports the config-coupled `SpecificKindItemStackComparator` and `Util.itemStackComparatorByModName`, which are intentionally deferred out of Commit 1 because they depend on `Config`'s owner-of-kind maps (clean commit boundary). `runClient` gate verified at the end of Commit 1 (mechanism unit-verified by the T2 executor/handler tests) and re-verified after Commit 2.
---

## M3 — UniOreDictionary via the accessor seam *(v1 steps 11–13; depends on M0 Spike A)*

**Files:** `mixins.early/OreDictionaryMixin.java`, `IOreDictionaryAccessor.java`, rewritten `UniOreDictionary.java`, `src/test/.../FakeOreDictionaryAccessor.java`.

- Define `IOreDictionaryAccessor` (the 5 maps); the mixin implements it, the fake lives in test sources.
- **Spike-A carry-over (Hodgepodge):** do NOT capture via `@Inject` into `OreDictionary.rebakeMap()` — Hodgepodge's `SpeedupOreDictionaryTransformer` strips it. Read the maps **lazily**: `oreDictBridge`/`UniOreDictionary` call the mixin's `@Accessor` getters on demand. See §Interop decisions.
- Rewrite `UniOreDictionary` to read all maps through the interface. Public methods keep signatures for API compat (`get`, `getUn`, `getId`, `getThoseThatMatches`, `removeFromElsewhere`, `getFirstEntry`, …). `removeFromElsewhere` keeps public `OreDictionary.getOreIDs` + direct list manipulation via the accessor.
- Delete every `Util.getField`/`setField` call site here — the last reflection in core (v1 step 13).

Tests (T2): fake accessor drives `getThoseThatMatches` (pure on names), `checkId` bounds, `getName(ItemStack | List | other)`, `removeFromElsewhere` entry removal on fake lists, first/last-entry copy semantics.

**Gate:** `./gradlew test` green; `runClient` shows a `[unidict-verify]` line proving `removeFromElsewhere("oreberry…")` mutates live lists.

---

## M4 — End-to-end vertical slice: selection + NEI on the main thread *(v1 steps 4, 8, 9, 10, 44)*

**Files:** `MetaItem` (MC glue), `resource/UniResourceContainer`, `resource/ResourceHandler`, `resource/UniResourceHandler`, `helper/NEIHelper`, new pure `SelectionRules`.

- **`MetaItem` (v1 step 4):** thin wrapper over the M1 pure helpers; `GameData.getItemRegistry()` is public API — no Mixin; `get(ItemStack)` / `get(Item)` / `toItemStack` / cumulative variants kept.
- **`UniResourceContainer` (v1 step 8):** port with one fix — `removeBadEntriesFromNEI()` stays a method but is **only invoked on the main thread**.
- **`ResourceHandler` (v1 step 9):** unchanged (public read API + `populateIndividualStackAttributes`).
- **`UniResourceHandler` (v1 step 10):** two fixes — `createResources()` → sequential `forEach`; `postInit()` → sequential `forEach`. Root cause: `updateEntries` → `removeBadEntriesFromNEI` → `API.hideItem` was running on fork-join threads (the NEI crash).
- **`NEIHelper` (v1 step 44):** single NEI call site; add dev-mode main-thread guard/assert.
- **Extract `SelectionRules` (new):** keep-one-entry semantics, NEI-hide eligibility, black-set handling, sort-trigger conditions — pure functions over `List<T>` + predicates so T1 tests need no `ItemStack`.

Tests (T1/T2): `SelectionRules` matrix (keepOneEntry on/off, black set empty/non-empty, kind black set); comparator ordering via `SpecificKindItemStackComparator` / `Util.itemStackComparatorByModName` (mock `ItemStack` if needed; Mockito allowed in test sources).

**Gate — the vertical-slice moment:** Forge + NEI `runClient`, **no mods installed**: no NEI crash; `[unidict-verify] PASS resource=ingotIron …` lines present. Everything after this is additive.

---

## M5 — Recipe layer + key rework (two commits) *(v1 steps 14–20)*

**Files:** `recipe/IRecipeResearcher`, `recipe/VanillaRecipeResearcher`, `recipe/ForgeRecipeResearcher`, `recipe/IC2RecipeResearcher`, `integration/CraftingIntegration`, `helper/RecipeHelper`, new pure `RecipeKey`.

**Commit 1 — behavior-preserving port**

- `IRecipeResearcher` (14), `VanillaRecipeResearcher` (15), `IC2RecipeResearcher` (17) unchanged.
- `ForgeRecipeResearcher` (16): keep the Forestry `ShapedRecipeCustom` path, but resolve the v1 `decide:` → **direct import** (Forestry is a kept `compileOnly` dep; drop `Class.forName`).
- `CraftingIntegration` logic unchanged (group → sort → keep best → rewrite; `RecipeComparator` stays) — already sequential thanks to M2.
- `RecipeHelper` audit (20): verify `singleWayCompressionRecipe` / `resourcesToCompressionRecipes` / `createCompressionRecipe` callers — delete if unused; keep `rawShapeToShape`.

**Commit 2 — key rework, isolated (v1 step 18)**

- Replace the sum-of-`MetaItem`-hashes key with `RecipeKey = sorted TIntList of main-entry ids + RecipeShape` (width/height + normalized grid pattern). Keep the `getShapedRecipeKey` / `getShapelessRecipeKey` signatures.
- Land as its **own commit** so any dedup regression is attributable; run the M4 gate before and after.

Tests (T1): `RecipeKey` — same recipe, different source order ⇒ same key; different shapes ⇒ different keys; a regression test for the historical false-merge that the commit history patched repeatedly.

**Gate:** `./gradlew test` green; vanilla crafting verify lines still PASS after both commits.
---

## M6 — API-only integrations, one PR each *(v1 steps 29, 21, 24, 25, 28)*

Order builds the integration pattern (config toggle + sequential entry + verify line) before any reflection work is needed.

1. **`FurnaceIntegration` (29)** — public `FurnaceRecipes.smelting().getSmeltingList()`.
2. **`AE2Integration` (21)** — `AEApi.instance().registries().grinder()`.
3. **`IC2Integration` (24)** — `Recipes.centrifuge` etc.
4. **`IEIntegration` (25)** — public static lists; `UniCrusherRecipe` subclass access via `super.` is fine (subclass privilege), no Mixin.
5. **`ChestIntegration` (28)** — first accessor: `ChestGenHooksMixin` (`chestInfo` static + `contents` instance) as interface + fake.

**Per-PR checklist (repeat for each of the five):**

```
[ ] T1/T2 tests touched or added (any pure logic extracted)
[ ] `./gradlew test` && `./gradlew build` green
[ ] `runClient` with that mod installed → `[unidict-verify] PASS integration=<mod>`
[ ] Full verify dump re-run: prior lines unchanged (diff against last run)
[ ] Single squashed commit referencing the v1 step(s)
```

**Gate:** all five PASS in one runClient; prior lines unchanged.

---

## M7 — Mixin-accessor integrations *(v1 steps 22, 23, 26, 27)*

Each is a mechanical repeat of the M3 pattern: interface + `@Mixin` impl in `mixins.early` or `…late` per target + fake; `TargetMods` gating where the mod must be loaded.

1. **`EnderIOIntegration` (22)** + `OreDictionaryPreferencesMixin` (`preferences` map). Drop `FixedSizeList` usage if trivially `ArrayList`-able (likely).
2. **`ForestryIntegration` (23)** — scoped, all non-destructive: `ShapedOreRecipeMixin` (early `@Accessor` on the Forge `ShapedOreRecipe.output`) behind `IShapedOreRecipeAccessor` rewrites carpenter grid outputs **in place**; squeezer container-recipe remnants via `Map.Entry.setValue` on the public `SqueezerRecipeManager.containerRecipes` (no mixin); `CentrifugeRecipeMixin` (late, `TargetMods.FORESTRY`) behind `ICentrifugeRecipeAccessor` rewrites each recipe's private product map **in place** (clear+putAll — the machine reads that exact map via `getProducts(Random)`, so bee-comb → metal outputs unify). **Not ported:** crate registration (deferred), fluid outputs (no 1.7.10 fluid-equivalence model).
3. **`RailcraftIntegration` (26)** + `BlastFurnaceCraftingManagerMixin` (`recipes` list, instance accessor).
4. **`TEIntegration` (27)** — `FurnaceManagerMixin`, `PulverizerManagerMixin`, `SmelterManagerMixin` (each `@Accessor` for `recipeMap` + `@Invoker` for the private `Recipe*` ctor). Keep `@SpecifiedLoadStage(LOAD_COMPLETE)`. Prefer `@Invoker` per Spike B; on failure, flip the pre-written 3 AT entries and flag for review.

**Gate:** full kept-mod `runClient` — one verify line per integration, all PASS; NEI safe (M4 main-thread rule still enforced).

---

## M8 — API surface + helpers *(v1 steps 39–44, 46)*

**Files:** `api/UniDictAPI`, `api/helper/ForestryUniHelper`, `api/helper/FurnaceUniHelper`, `api/helper/IEUniHelper`, `api/helper/TConUniHelper`, `NEIHelper`.

- `UniDictAPI` (39) unchanged (public read API).
- `ForestryUniHelper` (40): deferred (crates). Revisit via the M7 `ShapedOreRecipeMixin` accessor interface (fake-driven T2); keep crate-registration as-is (deferred rework).
- `FurnaceUniHelper` (41), `IEUniHelper` (42), `TConUniHelper` (43): unchanged (public APIs + `NEIHelper`).
- `NEIHelper` (44): main-thread guard already in (M4); stays the single `API.hideItem` site.
- `mcmod.info` (46) — currently example-mod boilerplate (author/URL/description); update.
- Add a compile-compat consumer test reaching `UniDictAPI.getResourceHandler()` as an API-surface guard.

**Gate:** `./gradlew test build` green; verify dump unchanged vs M7 (API is read-only).

---

## M9 — Cleanup sweep + full regression *(v1 steps 45, 47, 48)*

- v1 step 45 deletions are mostly folded into M2 (dead integrations, DI classes, `FixedSizeList`, `Util.getField/setField`); sweep any stragglers and add the `// TODO: Galacticraft integration` stub note in `IntegrationModule`.
- v1 step 47: `./gradlew build` — confirm compiles, Spotless/Checkstyle pass.
- v1 step 48 as the **automated** full check: `runClient` with the full dev dependency set; every `[unidict-verify]` line PASS; then the eyeball pass (a few ore types in NEI/JEI; sequential timing in the log; spot-check AE2 grinder, IC2 macerator, TE pulverizer, IE crusher, Railcraft blast furnace, EIO SAG mill, Forestry carpenter).

**Gate:** full verify dump all-PASS; Spotless/Checkstyle clean; `git log --oneline` reads like the milestone list.
---

## Mixin summary table (v1, + interface/fake column)

| Mixin class                        | Target                        | Accessor/Invoker               | Interface (+ test fake)                                  | Replaces                                               |
|------------------------------------|-------------------------------|--------------------------------|----------------------------------------------------------|--------------------------------------------------------|
| `OreDictionaryMixin`               | `OreDictionary`               | `@Accessor` x5 (static)        | `IOreDictionaryAccessor` (+ `FakeOreDictionaryAccessor`) | `UniOreDictionary` reflection                          |
| `ChestGenHooksMixin`               | `ChestGenHooks`               | `@Accessor` x2                 | `IChestGenHooksAccessor`                                 | `ChestIntegration` reflection                          |
| `WeightedRandomChestContentMixin`  | `WeightedRandomChestContent`  | `@Accessor` x2 (get+set)       | `IWeightedRandomChestContentAccessor`                    | `ChestIntegration` reflection (\*1.7.10 only)          |
| `ShapedOreRecipeMixin`              | `ShapedOreRecipe` (Forge)     | `@Accessor` x2 (get+set, instance) | `IShapedOreRecipeAccessor` (+ `FakeShapedOreRecipeAccessor`) | `ForestryIntegration` carpenter output rewrite            |
| `CentrifugeRecipeMixin`             | `CentrifugeRecipe` (Forestry) | `@Accessor` x1 (get, instance)    | `ICentrifugeRecipeAccessor` (+ generic product-map seam)     | `ForestryIntegration` centrifuge product-key rewrite (late, `TargetMods.FORESTRY`) |
| `OreDictionaryPreferencesMixin`    | `OreDictionaryPreferences`    | `@Accessor` x1                 | `IOreDictionaryPreferencesAccessor`                      | `EnderIOIntegration` reflection                        |
| `BlastFurnaceCraftingManagerMixin` | `BlastFurnaceCraftingManager` | `@Accessor` x1                 | `IBlastFurnaceCraftingManagerAccessor`                   | `RailcraftIntegration` reflection                      |
| `FurnaceManagerMixin`              | `FurnaceManager`              | `@Accessor` x1 + `@Invoker` x1 | `IFurnaceManagerAccessor`                                | `TEIntegration` reflection                             |
| `PulverizerManagerMixin`           | `PulverizerManager`           | `@Accessor` x1 + `@Invoker` x1 | `IPulverizerManagerAccessor`                             | `TEIntegration` reflection                             |
| `SmelterManagerMixin`              | `SmelterManager`              | `@Accessor` x1 + `@Invoker` x1 | `ISmelterManagerAccessor`                                | `TEIntegration` reflection                             |

**Chest accessor note (\*1.7.10):** the plan's `ChestGenHooksMixin` covers the two Forge-added fields (`chestInfo` registry + `contents`). The added `WeightedRandomChestContentMixin` is a **1.7.10-necessitated second accessor**: upstream (1.12.2) rewrote loot items by mutating `WeightedRandomChestContent.theItemId` as a *public* field, but in MC 1.7.10 that field is **private** (notch `qx.b`, SRG `field_76297_b`), so `ChestIntegration` reaches it through this get+set accessor to rewrite in place (BB-3). Toolchain catches from M6 (2026-08-13): (1) the Mixin AP requires an `@Accessor` stub to return the **exact field descriptor**, not a supertype (`HashMap`/`ArrayList`, not `Map`/`List`) — a covariant `Map<...>` return fails with `Could not locate @Accessor target …`; (2) the Mixin transformer rejects a **non-abstract instance** `@Accessor` at apply time (`InvalidAccessorException: @Accessor method … is not abstract`, a run-1 crash), so every *instance* accessor is `protected abstract`, while a *static* field accessor (e.g. `chestInfo`) stays a concrete `private static` method (the proven M3 `OreDictionaryMixin` pattern — Java forbids `abstract static`).

**AT fallback note (unchanged):** if `@Invoker` can't target a private constructor in the UniMixins / Sponge 0.8.5-GTNH build, the three TE constructors fall back to 3 AT entries (`PulverizerManager$RecipePulverizer`, `SmelterManager$RecipeSmelter`, `FurnaceManager$RecipeFurnace`). **Spike B outcome (2026-08-12):** `@Invoker("<init>")` compiles for all three TE constructor signatures (Mixins AP 0.8.7 validates them) — use `@Invoker` in M7. The AT fallback is **documented only, no physical file** (the toolchain `applyJST` auto-applies any `*_at.cfg` in resources to the *decompiled MC* and rejects non-MC class entries — a resource AT would break the build). Write + validate AT entries only if runtime `@Invoker` fails in-game.

---

## Interop decisions — defer to Hodgepodge (hard rule)

**Rule:** UniDict must work with **every GTNH environment mod at its default settings**. We never
disable, override, or work around a Hodgepodge (or GTNHLib, or any pack-infra) feature to make our
code work; if a target is being transformed by such a mod, we adopt the approach that coexists with
that transformation. Document each conflict here.

- **Hodgepodge `SpeedupOreDictionaryTransformer` (`com.mitchej123.hodgepodge…mc.SpeedupOreDictionaryTransformer`)** — ASM-rewrites `OreDictionary.rebakeMap()`, `getOreID`, `getOres`, `registerOreImpl`, … as a speedup, **stripping injected callbacks**. Effect on us: our M0 spike captured the bridge via `@Inject(at = @At("TAIL"))` into `rebakeMap`, which **never fires** under Hodgepodge (the mixin applies cleanly — the 5 `@Accessor`s are proofed — but the injector is dropped). Decision: **do not disable the transformer.** The M3 seam reads the maps **lazily** — expose the `@Accessor` getters and read the current field values on demand whenever `UniOreDictionary`/`OreDictionaryBridge` queries them, instead of relying on a one-time `capture()`. The fields remain populated under Hodgepodge (it optimizes, not removes); lazy reads coexist with it. Green line becomes `[unidict-verify] PASS spikeA oredict-bridge`.
  - Bottom line for M3: **`@Accessor` getters yes; side-effecting `@Inject` into transform-targeted methods no.**
  - See `docs/TestPlan.md` rule 8 and `docs/STATUS.md` (M0 Spike A is `[~]` for this reason).

---

## Key risks / watch items (updated)

- **`@Accessor` on static fields** — demoted from "single biggest could go wrong" to **M0 Spike A**. **Resolved:** accessors apply cleanly, but the capture hook conflicts with Hodgepodge's `SpeedupOreDictionaryTransformer` (see §Interop decisions) → M3 uses a **lazy-read** bridge, never a `rebakeMap` `@Inject`.
- **GTNH environment transformers (Hodgepodge et al.)** — **NEW RISK.** Any `@Inject` into a method a pack coremod ASM-rewrites is silently dropped (observed with `SpeedupOreDictionaryTransformer`/`rebakeMap`). Hard rule: defer to Hodgepodge, never disable it; prefer `@Accessor`/`@Invoker`/vanilla-safe hooks over `@Inject` into transformed methods. Enforced on a per-case basis in §Interop decisions.
- **`@Invoker` on constructors** — **M0 Spike B**; the 3 TE AT entries are the pre-written fallback.
- **Recipe key rework** changes dedup behavior — isolated to M5 commit 2, protected by T1 regression tests and before/after verify dumps.
- **Forestry crate runtime item registration** remains the most fragile kept feature; **deferred** (not ported) — only the non-destructive carpenter/squeezer sliver is implemented; flagged `// TODO: rework crate registration` if revisited.
- **JUnit wiring depends on the toolchain** — settled by the M0 demo test; the checkout does not yet prove `useJUnitPlatform()` is wired.

---

## Step → milestone traceability (v1 → v2)

| v1 step | v2 milestone                                                                                                                         |
|---------|--------------------------------------------------------------------------------------------------------------------------------------|
| 1–3     | M0 (step 3 adapted: enum-driven registration, no JSON hand-editing; `usesMixins` already true, `usesMixinDebug` flip when debugging) |
| 4       | M1 (pure arithmetic) + M4 (MC glue)                                                                                                  |
| 5       | M2                                                                                                                                   |
| 6       | M1 (64-kind guard, not just a comment)                                                                                               |
| 7       | M1                                                                                                                                   |
| 8       | M4                                                                                                                                   |
| 9       | M4                                                                                                                                   |
| 10      | M4                                                                                                                                   |
| 11      | M0 (Spike A) + M3                                                                                                                    |
| 12      | M3                                                                                                                                   |
| 13      | M3                                                                                                                                   |
| 14–17   | M5 commit 1                                                                                                                          |
| 18      | M5 commit 2                                                                                                                          |
| 19–20   | M5 commit 1                                                                                                                          |
| 21      | M6                                                                                                                                   |
| 22      | M7                                                                                                                                   |
| 23      | M7                                                                                                                                   |
| 24      | M6                                                                                                                                   |
| 25      | M6                                                                                                                                   |
| 26      | M7                                                                                                                                   |
| 27      | M7                                                                                                                                   |
| 28      | M6                                                                                                                                   |
| 29      | M6                                                                                                                                   |
| 30–38   | M2                                                                                                                                   |
| 39–43   | M8                                                                                                                                   |
| 44      | M4                                                                                                                                   |
| 45      | M2 (mostly) + M9 (sweep)                                                                                                             |
| 46      | M8                                                                                                                                   |
| 47      | every gate; final run in M9                                                                                                          |
| 48      | per-milestone T3 slice; full run in M9                                                                                               |

---

## STATUS.md template (per milestone)

```markdown
## M0 — Harness & risk retirement
- [ ] Mixins enum emptied; project boots
- [ ] JUnit demo test green (./gradlew test)
- [ ] Verify harness writes [unidict-verify] lines
- [ ] Spike A: static @Accessor OK (or fallback documented)
- [ ] Spike B: @Invoker / AT decision recorded
```

Each integration row in M6/M7 repeats the 5-item per-PR checklist; check them off in the same PR.

---

## Deferred items (updated 2026-08-12) — see also the "Scope rework" notice at the top

From the original feature set (deferred, NOT ported now):
- Crafting recipe rewrite + recipe-key rework (was M5).
- `keepOneEntry` / `removeFromElsewhere` / global OreDictionary mutation.
- NEI / item hiding beyond what the kept rewrites require.
- Forestry crate registration (runtime `ItemCrated`) + fluid outputs (see M7 / deferred).
- API/helper surface (`UniDictAPI` + Forestry/Furnace/IE/TCon helpers) — keep only the minimal read surface kept integrations use.
- `customUnifiedResources`, Galacticraft stub.

Infrastructure:
- Reload / re-run module.
