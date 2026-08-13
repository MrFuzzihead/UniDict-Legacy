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
- [ ] `IOreDictionaryAccessor` + mixin + `FakeOreDictionaryAccessor`; `UniOreDictionary` reads all maps through the interface
- [ ] **Spike-A carry-over:** bridge must be **lazy-read** (call the `@Accessor` getters on demand) — **not** a one-time `@Inject` into `OreDictionary.rebakeMap()`, which Hodgepodge's `SpeedupOreDictionaryTransformer` rewrites. Defer to Hodgepodge (never disable). Green line = `[unidict-verify] PASS spikeA oredict-bridge`.
- [ ] Mutation methods (`removeFromElsewhere`/`keepOneEntry` collapse) NOT ported now (deferred)

## M4 — Vertical slice (reframed): selection + vanilla furnace + report prototype — NO NEI hiding
- [ ] `SelectionRules` + `MetaItem`/containers/handlers; main-thread rule applies
- [ ] Vanilla furnace rewrite (first machine rewrite) → `[unidict-verify] PASS integration=Furnace`
- [ ] First transparency-report output (BB-1 seed)

## M5 — Crafting rewrite + recipe-key — DEFERRED (~)

## M6 — Machine rewrites (one PR each): Furnace · AE2 · IC2 · IE · Chest(loot)
- [ ] Furnace `[unidict-verify] PASS integration=Furnace`
- [ ] AE2 `…integration=AE2` · [ ] IC2 `…integration=IC2`
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