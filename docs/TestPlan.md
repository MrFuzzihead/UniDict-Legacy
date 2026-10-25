# TestPlan — how UniDict is verified (T1/T2/T3)

Companion to `PLAN.md` §0 ("Testing doctrine"). Every feature ships with the tier(s) that
apply; a feature is **done** when its applicable gates are all green.

## Tier summary

| Tier | Command | Scope | Notes |
|---|---|---|---|
| **T1 — JVM unit test** | `./gradlew test` | Pure logic, zero `net.minecraft*` imports | Kind taxonomy, `MetaItem` hash math, recipe keys, Config parsing, `SelectionRules`, comparators, load-stage order |
| **T2 — seam/fake test** | `./gradlew test` | MC-*typed* but not MC-*static* logic, driven by fakes | Every mixin accessor is an interface; `Fake…` lives in `src/test`, `@Mixin` class is the live impl |
| **T3 — in-game regression** | `./gradlew build runClient` + grep | End-to-end | Dev-only `[unidict-verify]` lines; deterministic because execution is sequential |

## How to run a verification pass

```
.\gradlew.bat test build          # T1 + T2 + Spotless/Checkstyle
.\gradlew.bat verifyServer         # T3 (headless): dedicated server + assertions
.\gradlew.bat runClient -PunidictDevVerify   # T3 (client): manual, for NEI/rendering paths
grep "unidict-verify" logs/latest.log   # every line should end in PASS
grep "unidict-verify.*FAIL" logs/latest.log  # must be empty
```

### T3 automation — `./gradlew verifyServer` (2026-10-24)

The verify oracle runs on a **dedicated server** too, so most of T3 no longer needs a client or a
display. `verifyServer`:

1. prepares `run/server/` (`eula.txt`, headless `server.properties`; `run/` is gitignored),
2. boots the dev dedicated server with `-Dunidict.devVerify=true` and
   `-Dunidict.verify.exitAfterStart=true` (a dev-only flag that stops the JVM right after the stage-2
   checks — see `VerifyHarness#shouldExitAfterChecks`),
3. captures the server console to `build/unidict-verify-server.log` (the verify lines go to the console
   appender, not `logs/latest.log`),
4. asserts: no `FAIL` line, a stage-1 and stage-2 summary with `0 failed` and `> 0` passes, the
   `harness complete` line (exit hook ran), a non-empty `report summary=resources=… rewrites=…`, every
   server-side `integration=…` line, and the `spikeA`/`resourceIndex …` checks.

Current baseline: **281 stage-1 checks + 5 stage-2 checks, 0 failures, ~40 s**. It also enforces the
BUG-8 Phase A gate in three steps: stage-1 `lateOd added=0` (the model was built from the complete
dictionary), `lateOdServer withEntries=0` (nothing with entries appeared later) and
`resourceIndex model-complete expected=… missing=0` (every modelled name and live entry is in the model).
`lateOd` also reports `withEntries=` because 1.7.10 mods can reserve names via `getOreID(...)` with no
entries.

CI: `.github/workflows/verify-server.yml` runs `./gradlew verifyServer --no-daemon` on every PR/push to
`master`/`main` (and on manual dispatch), with the Gradle cache and the captured harness log as an
artifact.

**Covered:** the resource model/index (including the post-remap `FMLModIdMappingEvent` stage), drops
registration, every machine/loot integration on the dev runtime (crafting, furnace, IC2, AE2, Chest, TE,
EIO, Railcraft, Forestry, TiC, Galacticraft), the transparency report and the server-start re-runs.

**Not covered by design:** NEI hiding (`API.hideItem` is client-only — the *hide decision* is still
computed and reported as `hidden=N`, and `NEIHideThread.stacksToHide` has T2 coverage), client
rendering/GUI/chat, and Immersive Engineering (it is `compileOnly`, so it is not on the dev **runtime**
classpath). Adding a required line to the task is the way to make a new behaviour part of the gate.

## Verify-line contract

Format: `[unidict-verify] <LEVEL> <feature>` where LEVEL ∈ {PASS, FAIL, INFO}.
Register new checks in the dev verify routine when you add a feature.

Examples once features land:

- `[unidict-verify] PASS resource=ingotIron main=ThermalExpansion:ingotIron variants=4`  (M4 report prototype — per unified resource; "variants=" since M4 has no NEI hiding)
- `[unidict-verify] PASS report resource=ingotIron main=ThermalFoundation:ingotIron variants=3 owners=IC2,ThermalFoundation,minecraft`  (BB-1 transparency report — per-resource canonical + variants + owner mods)
- `[unidict-verify] PASS report rewrite=ic2 macerator rewritten=16`  (BB-1 — every kept rewrite has a matching line)
- `[unidict-verify] PASS report summary=resources=105 rewrites=19`  (BB-1 — report summary)
- `[unidict-verify] PASS resource=ingotIron main=ThermalExpansion:ingotIron hidden=4`  (old default format with NEI hiding)
- `[unidict-verify] PASS integration=AE2Grinder`
- `[unidict-verify] INFO oredict.accessor nameToId.size=12345`  (Spike A)

## Rules that keep this possible

1. Accessor mixins = interface + `@Mixin` impl + test fake.
2. Decision logic lives in pure MC-free helpers; MC glue stays thin.
3. Integration execution is explicit + sequential (never a thread pool).
4. Every behavior promise gets a verify line.
5. One risk per milestone; mechanisms spiked before anything depends on them.
6. Mixin packages (`mixins.early` / `mixins.late`) contain **only `@Mixin` classes**; seam interfaces, bridges, and helper classes live in normal feature packages — never inside a mixin package.
7. Forge glue whose type isn't constructible off-game is **T3-only**. Example: `net.minecraftforge.common.config.Configuration` requires `FMLInjectionData.data()[6]` (set only during bootstrap), so `ForgeConfigIO` (M2) is a thin adapter fully delegated to the pure, T1-tested `ConfigReader`; its forge calls are covered by an in-game run, not a JVM unit test.
8. **Defer to Hodgepodge (hard interop rule).** UniDict must run at GTNH pack defaults — we never disable/override a Hodgepodge (or GTNHLib/pack-infra) feature. If a target method is ASM-rewritten by a pack coremod (`SpeedupOreDictionaryTransformer` rewrites `OreDictionary.rebakeMap`, stripping `@Inject`), use an approach that coexists (e.g. `@Accessor`/`@Invoker` read/write on demand, vanilla-safe hooks) — never a side-effecting `@Inject` into the transformed method. Each conflict is documented in `PLAN.md §Interop decisions`.