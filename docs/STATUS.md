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
- [ ] Verify harness `[unidict-verify]` writer scaffolded (dev-gated)
- [x] **Spike A:** static `@Accessor` on OreDictionary's private statics **CONFIRMED in-game** — log shows `unidict.accessor OK: nameToId=75904 idToName=75904 idToStack=75904 idToStackUn=75904 stackToId=21740` (all non-zero).
- [x] **Spike B:** 3 TE `@Invoker("<init>")` mixins **CONFIRMED in-game** — `fml-client-latest.log` DEBUG shows `Recipe{Furnace,Pulverizer,Smelter}Invoker` each mixed into its TE `Recipe*` class and the `@Invoker unidict$new(…)` renamed to `new$unidict_$md$…` (invoker wired), **no MixinApplyError**. (Actually *constructing* a recipe through them is exercised in M7.)
- [x] M0 gate: full `runClient` boots without a mixin/NEI error (both spikes observed mid-load)

## M1 — Pure resource model (kinds) — KEEP (substrate for selection + report)
- [x] `Resource`/`UniAttributes` ported; 64-kind guard enforced (`IllegalStateException` at 64) + 10 ResourceTest T1 tests green
- [x] Pure `MetaItem` hash helpers (`pure/MetaKey`) + 5 MetaKeyTest T1 tests green; pure/resource packages contain zero `net.minecraft*` imports (grep gate PASS)

## M2 — Determinism & infra rework (thread pool / DI / explicit registry)
- [ ] `Config` port + sample-`.cfg` T1 tests (absorbs BB-2 presets work later)
- [ ] Thread pool → sequential; DI removed; explicit `new` registry; `@Module` search deleted

## M3 — UniOreDictionary seam — KEEP, TRIMMED to read-only accessor (+ `getFirstEntry` for IE)
- [ ] `IOreDictionaryAccessor` + mixin + `FakeOreDictionaryAccessor`
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