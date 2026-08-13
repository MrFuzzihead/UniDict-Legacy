# UniDict — implementation status (M0–M9 + Build-better track)

Progress tracker matching `PLAN.md`. Check a box off in the same PR that satisfies it.
**Scope rework 2026-08-12:** full rework, not a faithful port — see the top of `PLAN.md`.

- T1 = JVM unit test · T2 = seam/fake test · T3 = in-game `[unidict-verify]` line.
- Legend: `[x]` done · `[~]` re-scoped/deferred (see PLAN) · `[ ]` open.

## M0 — Harness & risk retirement
- [x] `dependencies.gradle` finalized for kept mods; **TE = dev jar `curse.maven:ThermalExpansion-69163:2388759`** (not the API jar)
- [x] Placeholder `Mixins`/`TargetMods` enums emptied → project boots with zero mixins
- [x] JUnit 5 harness wired (`addon.gradle.kts` → `useJUnitPlatform`; `testImplementation` in deps)
- [x] Demo T1 test green: `TEST-…SmokeTest.xml` = `tests=2 failures=0 errors=0`
- [x] `./gradlew test build` compiles main + test, Spotless/Checkstyle clean
- [x] `docs/TestPlan.md` + `docs/STATUS.md` created
- [x] Verify harness `[unidict-verify]` writer scaffolded (dev-gated; enabled via `-PunidictDevVerify` **or** the `UNIDICT_DEV_VERIFY` environment variable)
- [~] **Spike A:** static `@Accessor` on OreDictionary's private statics — the accessors apply cleanly, but capturing via `@Inject` into `OreDictionary.rebakeMap()` **conflicts with Hodgepodge's `SpeedupOreDictionaryTransformer`** (it ASM-rewrites `rebakeMap`, stripping injected callbacks). **Defer to Hodgepodge — never disable it.** The M3 seam will use a **lazy-read** bridge (call the `@Accessor` getters on demand) instead of a one-time `rebakeMap` capture. Until M3, the verify harness emits `FAIL spikeA oredict-bridge` (see §M3 + §Interop decisions).
- [x] **Spike B:** 3 TE `@Invoker("<init>")` mixins **CONFIRMED in-game** — `fml-client-latest.log` DEBUG shows `Recipe{Furnace,Pulverizer,Smelter}Invoker` each mixed into its TE `Recipe*` class and the `@Invoker unidict$new(…)` renamed to `new$unidict_$md$…` (invoker wired), **no MixinApplyError**. (Actually *constructing* a recipe through them is exercised in M7.)
- [x] M0 gate: full `runClient` boots without a mixin/NEI error (both spikes observed mid-load)

## M1 — Pure resource model (kinds) — KEEP (substrate for selection + report)
- [x] `Resource`/`UniAttributes` ported; 64-kind guard enforced (`IllegalStateException` at 64) + 10 ResourceTest T1 tests green
- [x] Pure `MetaItem` hash helpers (`pure/MetaKey`) + 5 MetaKeyTest T1 tests green; pure/resource packages contain zero `net.minecraft*` imports (grep gate PASS)

## M2 — Determinism & infra rework (thread pool / DI / explicit registry)
Two commits (scope decision 2026-08-13): **Commit 1** = determinism/infra (lands first, before any
integration); **Commit 2** = config surface + presets (BB-2), which also ports the config-coupled
comparators.

### Commit 1 — infra determinism (DONE)
- [x] Sequential `LoadStageExecutor` replaces the upstream thread pool: registration order == execution order; threads run on the calling thread (`LoadStageExecutorTest` T2: 5 tests green)
- [x] `ModuleHandler` collapsed to an ordered module list; lazy `init()` once per module (`ModuleHandlerTest` T2: 3 tests green)
- [x] Explicit `new` registry in `IntegrationModule` (currently empty — M6/M7 add integrations); `@Module` search / `searchForModules` deleted; `LoadStage` + `SpecifiedLoadStage` ported
- [x] DI removed: `Dependencies`/`Instantiator`/`DependenceWatcher` never recreated; dead code path gone
- [x] `Util` trimmed to `getModName` (reflection `getField`/`setField` deleted); `FixedSizeList` not used
- [x] `UniDict` wires the sequential `ModuleHandler` at FML POST_INIT + LOAD_COMPLETE
- [x] **Determinism guard** (`DeterminismGuardTest`): greps `src/main` for thread pools / reflection / deleted DI types; fails build if reintroduced
- [x] `runClient` boots cleanly with all integrations off (T3 — confirmed 2026-08-13; config written, no UniDict errors). Verify harness now runs (via env var) and emits one known `FAIL spikeA oredict-bridge` — a **Hodgepodge interop conflict**, resolved in M3 (see §M3).

### Commit 2 — config + presets (BB-2) (DONE)
- [x] `ConfigData` value object (pure, grouped: general / resources-owners / integrations) + `ConfigReader` (map→config, last-write-wins) + `ConfigPresets` (minimal / standard / max-compat), T1 tests: ConfigReaderTest 5, ConfigPresetsTest 4
- [x] `OwnerOrder` pure owner model (dedupes `enableSpecificKindSort`/`ownerOfEveryThing`/`ownerOfEvery<Kind>`): per-kind override else global; T1 OwnerOrderTest 5
- [x] Runtime `Config` reworked to load via thin `ForgeConfigIO` adapter into `ConfigData`; legacy keys accepted-but-ignored; **no config-file deletion on version mismatch** (upstream fix)
- [x] Legacy aliases: `ownerOfEveryThing`→`ownerPriorities`, `ownerOfEvery<Kind>`→`ownerOfKind.<Kind>`; removed-mod/deferred keys collected for INFO (never fatal)
- [x] `SpecificKindItemStackComparator` + `Util.itemStackComparatorByModName()` ported, driven by `OwnerOrder` (keep-one-entry black set accumulated for M4)
- [x] Config `.cfg` round-trip covered at the pure layer (fixture); forge `Configuration` glue is T3-only (rule 7 in TestPlan.md — needs `FMLInjectionData`)
- [x] `./gradlew build` green: 40 tests, Spotless/Checkstyle clean

## M3 — UniOreDictionary seam — KEEP, TRIMMED to read-only accessor (+ `getFirstEntry` for IE)
- [x] `IOreDictionaryAccessor` + mixin + `FakeOreDictionaryAccessor`; read-only `UniOreDictionary` reads all maps through the interface (T2 `UniOreDictionaryTest`, 6 tests)
- [x] **Spike-A carry-over:** bridge is **lazy-read** — `OreDictionaryMixin` implements `IOreDictionaryAccessor` (instance `@Override` methods merged onto the target, each delegating to a `private static` `@Accessor` and thus reading the live field on every call); `OreDictionaryBridge` holds a target instance cast to the interface. The one-time `@Inject` into `OreDictionary.rebakeMap()` was **removed** (Hodgepodge's `SpeedupOreDictionaryTransformer` strips it). We defer to Hodgepodge (never disable). Green line = `[unidict-verify] PASS spikeA oredict-bridge`.
- [x] Mutation methods (`removeFromElsewhere`/`keepOneEntry` collapse) NOT ported (deferred) — no `Util.getField`/`setField` remains anywhere in src/main
- [x] **Startup crash fixed (see below):** an early M3 attempt added `public static unidict$get*` readers to the mixin, but Mixin forbids non-private static members (`InvalidMixinException` → broke `OreDictionary` load → GT `NoClassDefFoundError`). Reworked to the interface-impl pattern above; `compileJava` (Mixin AP) + full build green.
- [x] `runClient` re-verified: boot clean through all 76 mods (incl. GregTech + Hodgepodge); `OreDictionaryMixin` applied with 5 accessors renamed, no `InvalidMixinException`; log shows `[unidict-verify] PASS spikeA oredict-bridge nameToId=26794` + `summary: 1 passed, 0 failed` (2026-08-13)

## M4 — Vertical slice (reframed): selection + vanilla furnace + report prototype — NO NEI hiding
- [x] Pure `SelectionRules` (keep-one-entry / NEI-hide eligibility / sort-trigger decisions) + 7 `SelectionRulesTest` T1 tests green
- [x] `MetaItem` MC glue over `pure/MetaKey` (exposes `MetaItemProvider`; `UniOreDictionary.instance()` now reuses it)
- [x] `UniResourceContainer` — non-destructive snapshot selection (sorts a **private copy**, never mutates forge's live OD list — BB-3); live-list `removeBadEntriesFromNEI`/`keepOneEntry` removed (deferred)
- [x] `ResourceHandler` (canonical-query seam) + `UniResourceHandler` (sequential `createResources`/`postInit` — no `parallelStream`; publishes `UniDict.resourceHandler`)
- [x] Vanilla furnace rewrite — `FurnaceIntegration.rewriteOutputs` **non-destructive** (only `setValue` outputs, never removes recipes / never mutates global registries) + 3 `FurnaceIntegrationTest` T2 tests green (fabricated-map)
- [x] `NEIHelper` present as the single guarded `API.hideItem` site with a dev-mode main-thread guard — **not invoked** in M4 (NEI hiding deferred)
- [x] First transparency report (BB-1 seed): verify pass emits `[unidict-verify] PASS resource=<name> main=<owner:registry> variants=<n>` per unified resource at load-complete (**lines sorted so the dump is diffable run-to-run**)
- [x] **In-game crash found + fixed (2026-08-13, GTNH pack):** `createResources()` was calling `Resource::register` for **every OD prefix** in the registry, blowing past the 64-kind cap (`Cannot register more than 64 resource kinds`). Since only the configured `childrenOfMetals` child kinds survive `filteredClone(childrenOfMetals)` in the metal map, we now **register bits / build containers only for `childrenOfMetals`** children — the taxonomy stays ≤ 64 and nothing the selection/report/furnace acts on is lost.
- [x] **T3 in-game gate VERIFIED (2026-08-13, GTNH pack, 76 mods):** `runClient` booted clean (no NEI crash, no `Cannot register more than 64`), `[unidict-verify] summary: 147 passed, 0 failed` — `PASS spikeA oredict-bridge nameToId=52267`, 145 per-resource `PASS resource=<name> main=… variants=<n>` lines (incl. `ingotIron`), and `Furnace Integration: rewrote outputs of 465 furnace recipes`.
- [ ] `./gradlew build` (Spotless/Checkstyle) on the final M4 tree — `test` is green (already confirmed)

## M5 — Crafting rewrite + recipe-key — DEFERRED (~)

## M6 — Machine rewrites (one PR each): AE2 · IC2 · IE · Chest(loot)  *(Furnace landed in M4)*
- [ ] AE2 `…integration=AE2`
- [x] IC2 `…integration=IC2` — **COMPLETE (2026-08-13, T3 gate).** Non-destructive (only rebuild/setValue `RecipeOutput` item-lists via `getMainItemStack`, never removes recipes / never mutates global registries — BB-3) across **10** `Recipes.*` machine maps (upstream's five — centrifuge, metalformerRolling, blastfurance, compressor, macerator — plus extractor, metalformerExtruding, metalformerCutting, blockcutter, oreWashing). Built on the shared **`OutputRewriter`** core (`rewriteOutputs(map, view, resolve)` + `rewriteSingleOutputs` convenience) that **Furnace also delegates to**; per-machine dev-verify lines + summary; early-skip + null-map guard. Tests: 3 `IC2IntegrationTest` + 4 `OutputRewriterTest` + 3 `FurnaceIntegrationTest` green; `./gradlew test build` + Spotless/Checkstyle green. **T3 gate (dev-verify-enabled run):** `[unidict-verify] summary: 158 passed, 0 failed`, 11 `PASS integration=ic2 …` lines (10 machines, rewritten 16+14+0+14+0+0+7+7+5+6 = **69**), 145 `PASS resource=…` lines (prior lines unchanged), `PASS spikeA oredict-bridge nameToId=52267`; runtime `IC2 Integration: rewrote outputs of 69 IC2 machine recipes`, no ERROR/NPE. **History:** run-1 NPE = `Recipes.recycler.getRecipes()` returns null (recycler is a randomizer; `RecyclerRecipeManager` = `aconst_null/areturn`, verified vs IC2 2.2.828) → removed recycler + defensive null-guard.
- [ ] IE `…integration=IE` · [ ] Chest `…integration=Chest`

## M7 — Machine rewrites (accessor/mixin): EIO · Railcraft · TE (Forestry removed)
- [ ] EIO `…=EnderIO` · [ ] Railcraft `…=Railcraft` · [ ] TE `…=ThermalExpansion`

## M8 — API/helper surface — MOSTLY DEFERRED (~)
- [ ] Only the minimal read surface kept integrations actually use

## M9 — Cleanup sweep + full regression
- [ ] Verify dump all-PASS over full mod set; Spotless/Checkstyle clean

## Build-better track (`PLAN.md` has detail + gates)
- [ ] **BB-1 Transparency** — `/unidict report`: per-resource canonical entry, variants, owners, what got rewritten
- [ ] **BB-2 Config presets** — grouped categories; minimal / standard / max-compat
- [ ] **BB-3 Non-destructive rewriting** — outputs-only rewrites; grep guard; fabricated-map T2 test
- [ ] **BB-4 Broader equivalence** — ≥1 non-OD equivalence class implemented, tested, reported
- [~] Reload / re-run module — deferred