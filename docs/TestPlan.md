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
.\gradlew.bat runClient -Dunidict.devVerify=true
grep "unidict-verify" logs/latest.log   # every line should end in PASS
grep "unidict-verify.*FAIL" logs/latest.log  # must be empty
```

## Verify-line contract

Format: `[unidict-verify] <LEVEL> <feature>` where LEVEL ∈ {PASS, FAIL, INFO}.
Register new checks in the dev verify routine when you add a feature.

Examples once features land:

- `[unidict-verify] PASS resource=ingotIron main=ThermalExpansion:ingotIron hidden=4`
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