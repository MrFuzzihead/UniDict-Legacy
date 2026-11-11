# UniDict-Legacy — Independent Codebase & Dependency Analysis

- **Date:** 2026-11-10
- **Repository:** `C:/Users/Duncan/Documents/Workspace/UniDict-Legacy` (`main` @ `91f7498`)
- **Baseline I verified in this workspace:**
  - `./gradlew test --offline` — **220 tests, 0 failures** (pre-harness; now 241, see Appendix B)
  - `./gradlew verifyServer --offline` — **300 stage-1 + 5 stage-2 checks, 0 failures, ~40 s**
  - `./gradlew dependencies --configuration compileClasspath --offline` — resolves (with one notable
    IC2 substitution, see §7)
- **Scope:** main Java, tests, Mixins/accessors, resources, Gradle/config surface, CI, docs and
  `dependencies.gradle`. This is a follow-up to [`CODE_REVIEW.md`](CODE_REVIEW.md); it does **not**
  replace it.

> The existing review claims "no findings remain open". Several items below are new, and a few are
> old findings that the review lists as still open (BUG-10, OPT-2/4/8/9, docs item 5.2/5.4) or that
> were only partially fixed. The new highest-impact finding is that **the Immersive Engineering
> integration can never register because its FML mod id is checked with the wrong casing**.

---

## 0. Executive summary

| # | Severity | Finding | Primary location |
|---|----------|---------|------------------|
| 1 | **S1** | **IE integration never runs:** `Loader.isModLoaded("immersiveengineering")` is case-sensitive; IE's actual mod id is `ImmersiveEngineering`. | `integration/IntegrationModule.java:77` |
| 2 | **S1/S2 (fixed 2026-11-11)** | `ConfigReader.parse` never seeded `forestryIntegration` or `craftingIntegration` from the preset; the reflection completeness test caught both. | `pure/config/ConfigReader.java:113-130` |
| 3 | **S2 (fixed 2026-11-11)** | `metalsToUnify`/`resourceBlackList` and item-name tokens are now case-insensitive (`OreNames.resolveConfiguredNames`, `Text.containsIgnoreCase`). | `resource/UniResourceHandler.java:86-87,123,139-145` |
| 4 | **S2 (fixed 2026-11-11)** | The default full-stack runtime is now documented; `-PunidictDevLight` switches heavy integrations to `compileOnly` (no CoFH Core), and waila was removed. Full-stack dev still needs the dev AT. | `dependencies.gradle:36-72` |
| 5 | **S2 (fixed 2026-11-11)** | Server-start re-runs, the resource pipeline and module dispatch are now guarded (`Guarded` + per-module/thread `Throwable` handling); drops also guard the event bus. | `UniDict.java:78-125`; `module/ModuleHandler.java:34-46` |
| 6 | **S2 (fixed 2026-11-11)** | Remaining concrete casts use `Accessors.as` (Crafting/Galacticraft/Chest); TE invoker factories are guarded and all three invoker seams are checked. | §1.5 |
| 7 | **S2 (fixed 2026-11-11)** | `VerifyHarness` now uses the same canonical-entry index as NEI and the full late-OD classifier (excluded kinds + blacklist). | `VerifyHarness.java:162-250` |
| 8 | **S3 (fixed 2026-11-11)** | Report descriptors now include `@damage` / `@*`, so damage variants stay distinct. | `report/UniDictReport.java:57-78` |
| 9 | **S3 (fixed 2026-11-11)** | Centrifuge canonical collisions are merged explicitly (chances summed, capped at 1.0) and a failed map replacement restores the original (BUG-10). | `integration/ForestryIntegration.java:266-325` |
| 10 | **S3 (fixed 2026-11-11)** | `WeightedRandomChestContent.theItemId` is public in 1.7.10; the accessor mixin/interface/fake were removed in favour of direct field access. | `integration/ChestIntegration.java:85-110` |
| 11 | **S3** | The dev-light/dependency comments, `POST_INIT` comments/table, `EtFuturum:block_copper` examples and "no findings open" docs are stale or wrong. | §8 |
| 12 | **S3 / legal** | Many files are annotated as ports of MPL-2.0 code, but the repository only ships an MIT `LICENSE` and README says "MIT". MPL is file-level copyleft; add MPL notices/headers or clarify the licensing. | `LICENSE`, 15 ported files |

---

## 1. New correctness findings

### 1.1 [S1] Immersive Engineering is never integrated — wrong mod-id casing

**Status (2026-11-11): fixed.** The gate now uses `ModIds.IMMERSIVE_ENGINEERING`; `ModIdsTest` pins
`ImmersiveEngineering` and `ModIdUsageTest` forbids new `Loader.isModLoaded("...")` literals.

**Where (original):** `src/main/java/com/mrfuzzihead/unidict/integration/IntegrationModule.java:77`

```java
if (Config.ie() && Loader.isModLoaded("immersiveengineering")) executor.add(new IEIntegration());
```

**Evidence:**

- The actual IE 1.7.10 jar (`ImmersiveEngineering-0.7.7`) has
  `@Mod(modid = "ImmersiveEngineering", ...)` and `mcmod.info` `"modid": "ImmersiveEngineering"`.
- Forge 1.7.10 `Loader.isModLoaded(String)` is a plain `namedMods.containsKey(modId)`; `namedMods` is
  keyed by `ModContainer.getModId()` with **no lower-casing**. The check is therefore case-sensitive.
- Consequently `IEIntegration` is never constructed, even when IE is installed. The README (`:41`) and
  `docs/INTEGRATIONS.md` (`:177`) both claim IE support.

**Impact:** four advertised machine integrations (Arc Furnace, Blast Furnace, Crusher, Metal Press)
silently do nothing. Because IE is `compileOnly` and `verifyServer` excludes IE, the harness cannot
catch this.

**Fix:** use `Loader.isModLoaded("ImmersiveEngineering")` (ideally a shared `ModIds` constant), and add
a test that asserts the constant against the jar metadata (or a dev verification runtime that includes
IE). Consider checking both spellings only as a temporary compatibility shim, not as the primary id.

**Related:** `IntegrationModule` should log "IE present but integration disabled" or "IE absent" at
debug level so this class of typo is visible.

---

### 1.2 [S1/S2] `ConfigReader.parse` loses `forestryIntegration` and `craftingIntegration` from the preset

**Status (2026-11-11): fixed.** Both seeds were added; `ConfigReaderTest` now uses reflection to assert
that **every** public `ConfigData` field keeps the preset value with an empty raw map.

**Where:** `src/main/java/com/mrfuzzihead/unidict/pure/config/ConfigReader.java:113-130` (builder seed
chain), while the explicit key is handled at `:221`.

The builder chain seeds every `ConfigData` field except `forestryIntegration` and `craftingIntegration`:

```java
.furnaceIntegration(defaults.furnaceIntegration)
.ae2Integration(defaults.ae2Integration)
...
.enderIOIntegration(defaults.enderIOIntegration)
// <-- no .forestryIntegration(...)
// <-- no .craftingIntegration(...)
.railcraftIntegration(defaults.railcraftIntegration)
.thermalExpansionIntegration(defaults.thermalExpansionIntegration)
```

`ConfigData.Builder` defaults both to `true`, so:

- `ConfigPresets.minimal()` has `forestryIntegration=false` and `craftingIntegration=false`;
- `ConfigReader.parse(ConfigPresets.minimal(), new LinkedHashMap<>())` produces `true` for both.

I confirmed this directly against the compiled classes:

```text
minimal forestryIntegration=false / craftingIntegration=false
parsed  forestryIntegration=true / craftingIntegration=true
```

**Why production currently survives:** `ForgeConfigIO.registerDefaults` writes the key with the preset
default, and `flatten` then feeds it back into the parser. A non-Forge front end, a future test, or a
missing/renamed key silently violates the preset contract.

**Fix:** add both missing seeds and a completeness test that asserts
**every** `ConfigData` field is seeded from a non-default preset (e.g. a reflection-based field list or
an explicit assertion for every field). The current tests omit both `craftingIntegration` and
`tinkersConstructIntegration` from the preset/integration assertions, which is why this slipped through.

---

### 1.3 [S2] Metal/blacklist/item-name matching is case-sensitive and inconsistent with the diagnostics

**Status (2026-11-11): fixed.** `OreNames.resolveConfiguredNames` resolves configured metals and
blacklist entries case-insensitively (configured order preserved); `Text.containsIgnoreCase` is used by
`isProtected`/`isCanonicalPreferred`; all EtF examples now use `etfuturum:copper_block`.

**Metal discovery (`metalsToUnify`)** — `src/main/java/com/mrfuzzihead/unidict/resource/UniResourceHandler.java:86-87`:

```java
Config.get().metalsToUnify.stream()
    .filter(apiResourceMap::containsKey)   // exact, case-sensitive
```

`apiResourceMap` keys come from `OreNames.metalResourceNames`, which capitalizes the first character
(`ingotCopper` -> `Copper`). A config value `copper` or `COPPER` therefore matches nothing. Meanwhile:

- `ConfigData`/README document metal names casually without a case rule;
- `VerifyHarness.checkModelCompleteness` uses `KindNames.containsIgnoreCase(config.metalsToUnify, resource)`;
- `OreNames.modelledNames` also uses `containsIgnoreCase`.

So the diagnostic says the resource should be modelled while the producer silently ignores it.

**Blacklist (`resourceBlackList`)** — `UniResourceHandler.java:123,139-145` and
`VerifyHarness.java:350` use exact `contains`/`remove`. `metalsToUnify` is exact; `childrenOfMetals`,
`hideInNEIBlackSet` and `ownerOfKind` are case-insensitive. That is an inconsistent config surface.

**Item-name matching (`protectedItemNames` / `canonicalItemNames`)** —
`ResourceHandler.java:72,82,104` uses `String.contains(token)` on registry names, case-sensitively.
The real EtF registry name is `etfuturum:copper_block`, while docs, `ConfigData` javadoc,
`ForgeConfigIO` help text, `ResourceHandler` javadoc and tests use `"EtFuturum:block_copper"`.
`ConfigPresets.standard()` uses the correct lowercase value, but anyone copying the documentation gets
a silent no-op. The existing review's §5.4 flagged this; it is fixed as described above.

**Fix:**

1. Normalize config keys/values at load or compare with `equalsIgnoreCase` / lowercase:
   - metal resource names vs `metalsToUnify`;
   - `resourceBlackList`;
   - OD-name tokens and item-name tokens.
2. Prefer exact `modid:path` matching for `protectedItemNames`/`canonicalItemNames` with an optional
   explicit glob/regex, or at least document that matching is a case-sensitive substring.
3. Fix every example/test fixture to `etfuturum:copper_block`; add a test that a lowercase user value
   actually matches.
4. Make `VerifyHarness` use the same comparison helpers as production so diagnostics cannot disagree.

---

### 1.4 [S2] "Dev-LIGHT" dependency intent is not what the file does; CoFH is on the runtime path

**Status (2026-11-11): fixed.** Full-stack is the documented default; `-PunidictDevLight` moves the
heavy integrations to `compileOnly` and I verified its runtime classpath has no CoFH Core. The unused
waila dependency (which pulled CoFH Core) was removed.

**Where:** `dependencies.gradle:40-65`

The comment says:

> "The heavy GTNH mods (TE, EnderIO, Forestry, Railcraft, Mantle, TiC, IE) ... These are therefore
> `compileOnly` (not devOnly) below"

but the actual entries are `devOnlyNonPublishable` for Mantle, Railcraft, TiC, Forestry, EnderCore,
EnderIO, ThermalExpansion and ThermalFoundation. Only IE is `compileOnly`.

I confirmed with `./gradlew dependencies --configuration runtimeClasspath`:

```text
+--- com.github.GTNewHorizons:Mantle:0.5.4
+--- com.github.GTNewHorizons:Railcraft:9.17.31
+--- com.github.GTNewHorizons:TinkersConstruct:1.14.97-GTNH
+--- com.github.GTNewHorizons:ForestryMC:4.11.35
+--- com.github.GTNewHorizons:EnderIO:2.10.39
+--- curse.maven:ThermalExpansion-69163:2388759
+--- curse.maven:ThermalFoundation-222880:2388753
     +--- curse.maven:cofh-core-69162:2388751
```

So the focused-IC2 "dev-light" state is not active, and CoFH Core is on the runtime classpath. The
access transformer that prevents the `World.collidingBoundingBoxes` `IllegalAccessError` is
git-ignored (`gradle.properties:98` has `accessTransformersFile =`; `.gitignore:43-44` ignores the AT
file). Locally RFG auto-detects the untracked AT and prints a warning; a clean clone does not have it.
`verifyServer` does not exercise entity collision, but a local `runClient` full-stack run can.

**Fix:** decide which state is intended:

- **Full-stack dev:** make the comment match reality; consider `accessTransformersFile = unidict_at.cfg`
  and commit the AT as dev tooling (or document that the production pack provides the widening and the
  local AT is optional), and remove the "compileOnly below" sentence.
- **Focused dev-light:** switch the heavy deps to `compileOnly` (and add the focused runtime set as
  `runtimeOnlyNonPublishable`), or implement a `-PunidictFullStack` Gradle property that selects the
  heavy runtime set without editing the file. The current mixed state is the worst of both.

---

### 1.5 [S2] Remaining hard casts / unchecked invoker seams

**Status (2026-11-11): fixed.** `CraftingIntegration`, `GalacticraftIntegration` and `ChestIntegration`
skip an unapplied accessor with a one-time warning; TE views use `Accessors.as` and return `null`
(which the shared core now treats as "leave untouched"), and `TEIntegration.checkSeams` checks all
three invoker interfaces.

`Accessors.as` was introduced for BUG-9, but several paths still cast directly:

- `CraftingIntegration.java:122-131` — if `recipe instanceof ShapedRecipes` / `ShapelessRecipes`, it
  casts to `IShapedRecipesAccessor` / `IShapelessRecipesAccessor`. If that Mixin did not apply, the
  cast throws `ClassCastException` (the `instanceof` test is on the vanilla class, not the accessor
  interface). The `call()` wrapper catches it, but the server-start re-run (`runCraftingAtServerStart`)
  does not; see §1.6.
- `GalacticraftIntegration.java:51-52` — same pattern for `ShapedRecipes`.
- `TEIntegration.java:62,78,98` — casts each recipe to `IRecipeFurnaceFactory` /
  `IRecipePulverizerFactory` / `IRecipeSmelterFactory`. These interfaces are implemented by the
  `@Invoker` Mixins, but `TEIntegration.checkSeams()` only checks the three manager accessors, not the
  invokers. A failed invoker Mixin is therefore invisible to the harness and aborts the whole TE
  integration.
- `ChestIntegration.java:41` — casts `ChestGenHooks` to `IChestGenHooksAccessor` after
  `getChestInfo()` succeeded; this one is low-risk (same target class), but still an unchecked cast.
- `ForestryIntegration`, `EIOIntegration` and `RailcraftIntegration` correctly use `Accessors.as`.

**Fix:** use `Accessors.as` for every cast; return/skip with a named warning when null. Add
`VerifyHarness.checkSeam` for the TE invoker interfaces and for any other new seam. Consider a
generic `Accessors.require`/`tryAs` helper that records the failure once.

---

### 1.6 [S2] Server-start re-runs and pipeline/module initialization are unguarded

**Status (2026-11-11): fixed.** `UniDict` wraps every load-complete/server-start phase in `Guarded`,
`ModuleHandler` isolates each module, `LoadStageExecutor` catches `Throwable`, and `UnifyDrops`
guards the event handler with one-time failure logging.

**Where:** `UniDict.java:76-77,97-110`; `module/ModuleHandler.java:35-40`;
`module/LoadStageExecutor.java:83`.

`loadComplete` performs:

```java
runResourcePipeline();                              // no try/catch
moduleHandler.startModules(...);                    // init() is not guarded
integrationModule.checkSeams();
```

`serverStarted` performs four static re-runs:

```java
IntegrationModule.runGalacticraftCompressor();
IntegrationModule.runCraftingAtServerStart();
IntegrationModule.runIC2AtServerStart();
IntegrationModule.runTinkersConstructAtServerStart();
```

Each `run*` static method calls an integration static method directly. Those methods
(`GalacticraftIntegration.runCompressor`, `CraftingIntegration.runCraftingRewrite`,
`IC2Integration.runAtServerStart`, `TinkersConstructIntegration.runCasting`) do **not** have the
`try/catch` that their module-thread `call()` wrappers have. A late recipe map with an unexpected type,
an unmodifiable output list, a failed Mixin cast, etc. can throw out of `FMLServerStartedEvent` and
abort world load. `LoadStageExecutor` and `ModuleHandler` also catch only `Exception`; a
`NoClassDefFoundError` from an optional mod's class loading (or any other `Error`) escapes.

**Fix:** wrap each re-run in the same defensive boundary as the module threads (or reuse a
`runGuarded` helper), and catch `Throwable` per module/thread with a clear log. An integration should
never be able to take down the load/server-start sequence.

---

### 1.7 [S3] `UniResourceHandler.create()` marks the singleton initialised before the build succeeds

**Where:** `resource/UniResourceHandler.java:39-42`.

```java
if (hasInit) return null;
hasInit = true;
return new UniResourceHandler();
```

If `createResources()` or `postInit()` throws, `hasInit` is already true and a retry returns null. This
is mostly defensive (no reload exists), but it makes any future `/unidict reload` or recovery path
impossible. Set the flag after a successful build, or make the pipeline idempotent/retryable.

---

### 1.8 [S3] Nil/wildcard edge cases in the canonical container

- `UniResourceContainer.updateEntries()` (`:110-119`) assumes `entries.get(0)` is a non-null
  `ItemStack` with a non-null `Item`; a malformed OD registration can NPE the whole pipeline.
  `populateIndividualStackAttributes` already skips such entries, so this is inconsistent.
- `CanonicalRules.shouldReplace` and `NEIHideThread.isCanonicalElsewhere` treat
  `OreDictionary.WILDCARD_VALUE` as a literal damage value. If a container's main entry is registered
  wildcard, a concrete-damage variant is not treated as the same item, and `getMainItemStack` may
  return a wildcard-damage canonical stack. Decide explicitly whether wildcard means "all damages".
- If the same `(Item, damage)` appears in multiple modelled containers, `populateIndividualStackAttributes`
  is **last-write-wins** (resource/children iteration order), while `containerForOreName` is
  **first-OD-group-wins**. The canonical target for a multi-tag item is therefore order-dependent.
  Pick a deterministic rule (e.g. longest container name, configured resource priority, or explicit
  config) and document it.
- `protectedItemNames` / `canonicalItemNames` match only the registry `modid:path`, not the damage.
  They therefore cannot express "protect/promote only damage 0" for a subtype item such as EtF's
  `copper_block` (16 metadata variants). Today this is benign because `blockCopper` registers only
  damage 0, but the config surface cannot express the finer-grained intent. Support an optional
  `modid:path@damage` form or a glob, and document the current behavior.

**Fix:** skip null main entries, decide wildcard semantics, and make multi-container resolution
deterministic. Add T1 tests for each.

---

### 1.9 [S3] `ConfigPresets.minimal()` does not disable NEI hiding

**Status (2026-11-11): fixed.** `minimal` now sets `autoHideInNEI(false)`; `ConfigPresetsTest` pins it.

`ConfigData.Builder.autoHideInNEI` defaults to `true`, and `ConfigPresets.minimal()` does not set it.
So "minimal — vanilla-safe only (furnace + chest loot)" still hides non-main variants in NEI (for the
four minimal metals). That may be intended, but it contradicts the "lightest touch" wording. Set
`autoHideInNEI(false)` explicitly in `minimal`, or update the documentation and tests. The preset test
does not assert this field either.

---

## 2. Diagnostics and reporting correctness

### 2.1 [S2] `VerifyHarness` `hidden=` disagrees with the actual NEI pass

**Status (2026-11-11): fixed.** `ResourceHandler.isCanonicalEntry` (backed by `CanonicalIndex`) is
shared by `NEIHideThread` and `VerifyHarness.runResourceReport`, so both apply the same
protected-or-canonical-elsewhere rule in O(1).

`VerifyHarness.runResourceReport` (`:162-190`) calls `SelectionRules.hiddenIndices` with
`ResourceHandler::isProtected` as the only `alwaysVisible` predicate. `NEIHideThread` (`:69-75`) adds
`isCanonicalElsewhere(canonicalEntries, stack)` as well. Therefore a variant that is canonical in
another container is counted as "hidden" by the harness but is actually kept visible by NEI. The
`resource report ... hidden=` line can overstate the hide set.

**Fix:** extract one pure `hideSet`/`alwaysVisible` helper used by both, or at least pass the same
predicate in `runResourceReport`.

### 2.2 [S2] `lateOd modelled=` overcounts excluded kinds / blacklisted resources

**Status (2026-11-11): fixed.** The 6-arg `OreNames.modelledNames` applies `EXCLUDED_KINDS` and the
resource blacklist; `checkLateOd` passes both, and `OreNamesTest` pins the rule.

`OreNames.modelledNames` (`:261-277`) checks `metalsToUnify` and `childrenOfMetals` but does not skip:

- `OreNames.EXCLUDED_KINDS` (e.g. `stick`, `gem`, `sapling`);
- `ConfigData.resourceBlackList`.

`VerifyHarness.checkModelCompleteness` (`:346-350`) does skip both (though the blacklist comparison is
case-sensitive). So `lateOd ... modelled=N` can report late names as actionable that the model would
never have registered. Apply the same filters in `modelledNames`, and add a test for an excluded kind
and a blacklisted resource.

### 2.3 [S3] Transparency report drops damage/metadata

**Status (2026-11-11): fixed.** `UniDictReport.describe` and `VerifyHarness.describe` append
`@<damage>` (or `@*` for the wildcard); `UniDictReportTest` pins the suffix.

`UniDictReport.describe` (`:57-67`) returns only `modid:path`. `describeAll` (`:70-80`) de-duplicates
by that string, so two variants with the same item but different damage (common for ore blocks,
subtype items, or EtF copper block subvariants) collapse into one reported variant. `ReportEntry` then
reports a wrong `variants=` count and a possibly misleading `main=`.

**Fix:** include `@<damage>` (or `@*` for wildcard) in descriptors, as `VerifyHarness.describeEntry`
already does; update the pure report tests. Consider making the descriptor a small value object rather
than a string.

### 2.4 [S3] `checkNoSpuriousRewrite` only checks damage 0

**Status (2026-11-11): fixed.** The sweep now covers damage 0 plus every concrete damage value seen in
live Ore Dictionary entries (wildcard markers skipped).

`VerifyHarness.checkNoSpuriousRewrite` (`:452-471`) constructs `new ItemStack(item, 1, 0)` for every
registered item. It cannot catch a spurious rewrite for a damage-specific item whose modeled group is
at damage 1+. Iterate the real container entries (or all OD entries) instead, or at least also test
each damage present in the model.

### 2.5 [S3] `VerifyHarness` comment/duplicate-javadoc cleanup

**Status (2026-11-11): fixed.** The duplicated sentence and stale stage-1 comment were cleaned up.

`VerifyHarness.java:386` has a duplicated sentence:

```java
/**
 * Every variant must still be resolvable after the remap /** Every variant must still be resolvable after the remap
```

Also, the class javadoc says stage 1 runs at `FMLPostInit`, but `CommonProxy.loadComplete` calls
`VerifyHarness.runChecks()` at `LOAD_COMPLETE` (as do the stale `POST_INIT` comments in
`NEIHideModule`, `NEIHideThread`, `UnifyDrops`, `IntegrationModule`, `IC2Integration` and
`OreNames`). Fix the comments; they actively mislead future load-stage work.

---

## 3. Remaining robustness / integration issues

### 3.1 [S3] Forestry centrifuge product map: latent collision + non-atomic replacement (BUG-10)

**Status (2026-11-11): fixed.** Semantic item+damage collisions are merged (chances summed and
capped at 1.0) with a warning, and `clear()+putAll` is backed by a restore-on-failure path. Tests
cover both the collision and an immutable map.

`ForestryIntegration.rewriteCentrifugeProducts` (`:266-282`) builds a `LinkedHashMap` and does
`mapped.put(canonical, entry.getValue())`. In 1.7.10 `ItemStack` uses identity equality, and Forestry
currently passes a `HashMap`, so this works today. But:

- a future/alternate `ItemStackMap`-backed map (Forestry already has `ItemStackMap`) uses item+damage
  equality; two variants mapping to one canonical key would silently collapse to the last chance;
- `products.clear(); products.putAll(mapped);` is not atomic; an exception between the two leaves the
  recipe with no products. Only `UnsupportedOperationException` is caught.

**Fix:** detect canonical-key collisions explicitly, merge chances (or warn + skip), and replace the
contents in one audited step. Covered by `ForestryIntegrationTest` (semantic collision + immutable map).

### 3.2 [S3] `ChestIntegration` carries an unnecessary Mixin/accessor

**Status (2026-11-11): fixed.** The `WeightedRandomChestContentMixin`,
`IWeightedRandomChestContentAccessor`, fake and verify seam were removed; `ChestIntegration` writes
the public `theItemId` field directly, and the chest tests use real `WeightedRandomChestContent`
entries.

`WeightedRandomChestContent.theItemId` is **public** in 1.7.10 (`javap`:
`public net.minecraft.item.ItemStack theItemId;`). The accessor javadoc
(`mixins/early/WeightedRandomChestContentMixin.java:17`) says it is private, which is wrong. The
`@Accessor` mixin, `IWeightedRandomChestContentAccessor`, the `instanceof` check, the fake and the
`verifyServer` seam are all unnecessary. Direct field access in `ChestIntegration.rewriteContent`
would remove a Mixin and a failure mode.

**Caveat:** if RFG ever deobfuscates a different visibility, keep the accessor; but for this 1.7.10
baseline it is dead complexity. `AdvShapelessRecipe.output` is also public/non-final, so
`AdvShapelessRecipeMixin` is similarly optional (the interface seam still has testability value).

### 3.3 [S3] `UniOreDictionary` exposes two unused accessors

**Status (2026-11-11): fixed.** `getIdToName`/`getStackToId` were removed from the interface, bridge,
mixin and test fake; only the three caches the model actually reads remain.

`IOreDictionaryAccessor.getIdToName()` and `getStackToId()` (and their mixin/bridge getters) are not
used by production code. `BUG-19` deliberately keeps the "five-field seam complete", but the two
unused accessors increase the Mixin surface for no runtime benefit. Either use them (e.g. for a future
`/unidict why`) or remove them from the interface, bridge, mixin and fake.

### 3.4 [S3] `LoadStage` advertises stages that are never dispatched

`LoadStage` has `PRE_INIT`, `INIT`, `POST_INIT`, `LOAD_COMPLETE`, but `UniDict` only calls
`moduleHandler.startModules(...)` from `loadComplete`. A future `@SpecifiedLoadStage(PRE_INIT)` thread
would silently never run. Either dispatch modules from each event or remove the unused stages/make the
default `LOAD_COMPLETE` explicit. At minimum, add a startup warning if a thread is registered for a
stage that is never dispatched.

### 3.5 [S3] Warning/logger consistency

- `CraftingIntegration` and `VerifyHarness` use `LogManager.getLogger("UniDict")`; `UniDict.LOG` uses
  `"unidict"`. Logger names are case-sensitive; log filters/configs may treat them differently.
- `CraftingIntegration` emits one warning per unhandled recipe (OPT-9). Aggregate by recipe class and
  report counts; a GT pack can otherwise flood the log.
- `LoadStageExecutor` logs `thread.toString()` (class name + hash) instead of `threadName` on failure.
- `ChestIntegration` now warns once if the `ChestGenHooks` accessor is missing (fixed 2026-11-11).
- `UnifyDrops.onEntityJoinWorld` now catches `Throwable` and logs only the first failure (fixed
  2026-11-11), so one malformed drop cannot break the listener.

### 3.6 [S3] `Config` data publication / static test state

`Config.data` is a mutable static reference without `volatile`; it is written in pre-init and read by
the integrated server thread and any later event. The thread start normally provides a happens-before
edge, but `volatile` is cheap and documents the intent. Similarly, `UniResourceHandler.hasInit` and
`kindBlackSet` are static non-thread-safe state; any future reload/test parallelisation will expose it.

---

## 4. Optimizations (including still-open OPT items)

**Status (2026-11-11): items 1–9 done.** The resolver now checks the exact container before
the protection lookup (unmodelled stacks pay no OD lookup), protection is cached per modelled entry,
NEI canonical membership is O(1), `getEntries()` is a read-only view, only selected resources are
reconciled, owner namespaces are cached, Crafting warnings are aggregated by recipe class, and the
empty mod-blacklist path returns early, and the report is paged at 20 lines. Item 10 remains a candidate.

These are not release blockers, but several are directly on hot paths (`findMatchingRecipe`, drops,
NEI load):

1. **Reorder the resolver (`ResourceHandler.getMainItemStack`, `:202-218`).** It calls `isProtected`
   first, which performs `OreDictionary.getOreIDs` (and registry lookup if `protectedItemNames` is set)
   for **every** stack, including unmodeled stacks. Resolve the container first; only call
   `isProtected` when a container matched. For unmodeled stacks this removes one OD lookup entirely.
2. **Precompute protection/canonical preference (`OPT-2`).** Protection is a function of
   `(Item, damage)` + fixed config. Build an `IdentityDamageIndex<Boolean>` alongside
   `populateIndividualStackAttributes`, and a similar set for `isCanonicalPreferred`. The current
   `isProtected`/`isCanonicalPreferred` substring scans run per output and per NEI entry.
3. **Make NEI's "canonical elsewhere" check O(1) (`NEIHideThread:69-115`).** It builds a
   `List<ItemStack>` and linearly scans it for every variant — O(variants × containers). Use an
   `IdentityDamageIndex<Boolean>`/`Set` built once. Also avoid `getEntries()` copies.
4. **Avoid `getEntries()` copies (`OPT-4`).** `UniResourceContainer.getEntries()` returns a new
   `ArrayList` on every call; the NEI pass, report and harness call it repeatedly. Expose a read-only
   `List` view (or `forEach`/`size` accessors) for read-only callers; keep a copy only for mutating
   seams.
5. **Cache owner names for sorting.** `Util.getModName` / `SpecificKindItemStackComparator` perform a
   registry lookup per comparison (`O(n log n)` per container). Cache `Item -> namespace` in an
   `IdentityHashMap` for the duration of a sort/model build.
6. **Do not register/update `apiResourceMap` resources that are not in `metalsToUnify`.** `postInit`
   calls `updateEntries()` on every discovered metal, including non-unified ones; only the filtered
   clones are published. Update only what will be used.
7. **Report pagination/batching (`OPT-8`). Fixed 2026-11-11.** `/unidict report [resource] [page]`
   now pages the filtered report at 20 lines via the pure `ReportPager`, so a GT pack no longer sends
   100–200 chat packets at once.
8. **Aggregate CraftingIntegration warnings (`OPT-9`).** One line per unhandled recipe class + count,
   instead of one per recipe.
9. **`isModBlacklisted` early return** when `autoHideInNEIModBlackSet` is empty; it currently calls
   `Util.getModName` for every NEI entry even when no mod is exempted.
10. **Canonical stack cache.** Container main entries are stable after `updateEntries`; precompute a
    canonical `ItemStack` (or at least the item+damage pair, already done) for report/NEI/Storage
    Drawers callers to avoid repeated allocation. Do not share the mutable stack across callers; cache
    only immutable facts.

---

## 5. Refactors, dead code, cleanup

The review's BUG-19 removed a lot, but these remnants are still worth addressing:

| Item | Location | Note |
|---|---|---|
| `MetaKey` packing helpers | `pure/MetaKey.java` | Removed 2026-11-11; only the `WILDCARD` sentinel remains. |
| `SelectionRules.mainIndex` | `pure/SelectionRules.java` | Removed 2026-11-11 (test-only). |
| `UniResourceContainer(String,long,boolean)` | `resource/UniResourceContainer.java` | Removed 2026-11-11 (unused). |
| `Config.keepOneEntry()` / `Config.inputReplacement()` | `Config.java` | Removed 2026-11-11 (no production callers). |
| `ConfigPresets.defaults()` | `pure/config/ConfigPresets.java` | Removed 2026-11-11 (alias of `standard()`). |
| `UniDictReport.rewriteRecords()` | `report/UniDictReport.java` | Removed 2026-11-11 (no caller). |
| `UniOreDictionary` unused accessors | `oredict/UniOreDictionary.java` + bridge/mixin/fake | `getIdToName`/`getStackToId` removed 2026-11-11; `get(Integer)` remains test-only. |
| `ResourceHandler.resourceExists` | `resource/ResourceHandler.java` | Unused. |
| `ModuleHandler.isEmpty` / `LoadStageExecutor.isEmpty(...)` | `module/` | `ModuleHandler.isEmpty` removed 2026-11-11; `LoadStageExecutor.isEmpty` remains test-used. |
| `Resource.register` / `getResources` / `addChild` / `kindExists` | `resource/Resource.java` | Test-only or future API; BUG-19 says the taxonomy API is deliberately kept. Document that, or move to a test fixture. |
| `IShapedRecipesAccessor` javadoc | `crafting/IShapedRecipesAccessor.java` | Claims `ShapedOreRecipe extends ShapedRecipes`; in 1.7.10 it implements `IRecipe` directly (which the code itself handles). |
| Stale `POST_INIT` comments | `IntegrationModule`, `IC2Integration`, `NEIHideModule`, `NEIHideThread`, `UnifyDrops`, `OreNames`, `VerifyHarness` | Actual model/integrations run at `LOAD_COMPLETE`. |
| `VerifyHarness` duplicated javadoc line | `VerifyHarness.java:386` | Cleanup. |
| Duplicate report/describe logic | `UniDictReport.describe` vs `VerifyHarness.describe` | Extract a shared MC-bound descriptor helper (or a pure descriptor value) and include damage. |
| `SpecificKindItemStackComparator` cache key | `common/SpecificKindItemStackComparator.java` | Fine (≤64 kinds), but document that it is bounded. |

**Suggested refactors with real value:**

1. **`ModIds` constants** for every `Loader.isModLoaded` caller, with a test/verification line. This
   directly prevents the IE bug class.
2. **Pure `ProtectionRules` / `CanonicalPreference`** extracted from `ResourceHandler`, with
   `(registryName, odNames, config)` inputs. Then case-matching, substring matching and metadata
   handling are T1-testable without MC.
3. **Pure `HideSet` decision** shared by `NEIHideThread` and `VerifyHarness` (see §2.1).
4. **Config seeding completeness test** using a reflection-based field inventory or an explicit
   all-fields assertion; this is the cheapest guard against §1.2 and future `ConfigData` additions.
5. **One `runGuarded` integration boundary** used by both `LoadStageExecutor` and the server-start
   re-runs (see §1.6).
6. **Remove the public-field Mixin** (§3.2) and unused OreDictionary accessors (§3.3).

---

## 6. Features / product gaps

These are already partly in `TODO.md`; I list them here with a suggested order and the code seams that
make them cheap:

### Tier 1 (highest value)

1. **GregTech 5 machine rewrites.** Biggest missing integration; GT has no `GT_Recipe` output rewrite.
   Start with `GT_Recipe.mOutputs` for macerator/centrifuge/ore washer/electrolyzer/alloy smelter/
   forming press; use the existing `OutputRewriter`/weighted core where possible. Needs a GT research
   spike and compileOnly GT types.
2. **`inputReplacement`.** Currently parsed and inert. Implement as a non-destructive input rewrite,
   one machine at a time; a non-canonical ingot should not produce a non-canonical result.
3. **IE fix + verifier.** Fix §1.1 and add IE to a dev runtime/harness or a T2 seam; otherwise this
   integration is unverified forever.
4. **NEI live-entries pass.** Hide non-canonical live OD entries at world join so late registrations
   (the reported 5 Railcraft plates) stop appearing as duplicates. The model need not change.
5. **`customUnifiedResources`.** User-defined equivalence classes (e.g. `Obsidian:dustTiny|dust`) fed
   into `OreNames`/`UniResourceHandler`; T1-testable.
6. **`/unidict why <item>` and `/unidict list`.** Turn the existing decision data into a diagnostic:
   container, canonical entry, owner rank, protection/blacklist reason. `/list` should print configured
   metals/kinds, resolved owner order, valid kind names and unknown config entries.

### Tier 2 (semantic extensions)

- **BB-4 fuels/coal coke** (Railcraft + IE `fuelCoke`): first non-OD equivalence class; preserve burn
  times rather than naive tag equality.
- **Fluid outputs** (Forestry still/fermenter/squeezer, GC refinery, TiC melt/alloy): needs a 1.7.10
  fluid-equivalence model; high risk.
- **`keepOneEntry`**: decide to drop the key or implement a genuinely different collapse. Currently it
  is parsed but inert and `ConfigPresets`/`ForgeConfigIO` text still implies it works.
- **Forestry crate-item creation** for metals without a crate; current recipe wiring only covers
  already-registered crates.
- **GC circuit fabricator** outputs; deferred with the fluid/wafer decision.
- **Storage Drawers QA**: confirm the compacting drawer shows the canonical EtF block in-game.

### Tier 3 (UX / polish)

- `assets/unidict/lang` — there is **no** language file; command/log/user text is hardcoded English.
- Config GUI (`GuiFactory`).
- NEI tooltip/overlay showing "unified -> main" and owner rank.
- JSON report export (`/unidict report json` or `logs/unidict-report.json`) for pack authors/CI diffing
  (pagination itself landed 2026-11-11).
- Dry-run mode: log what would be rewritten without mutating recipes; useful while adding GT/inputs.
- Icon (`logoFile` empty) and optional-dependency metadata in `mcmod.info`.
- Client-side T3 in CI (virtual display) for NEI/GUI/tooltip paths.

### Tier 4 (API / ecosystem / verification)

- `UniDictAPI` + helpers, if there is an external audience.
- CraftTweaker/MineTweaker bridge for pack-scripted equivalences/blacklist entries.
- Harness extensions: synthetic `EntityItem` for the drop listener, console-driven `/unidict report`,
  NEI hide-count assertions, a StorageDrawers verify line, an IE verify line.
- Publishing docs / "use as a dependency" section.

---

## 7. Dependency analysis (`dependencies.gradle`)

### 7.1 Current vs latest published (updated and applied 2026-11-11)

> All rows below were applied in `dependencies.gradle` on 2026-11-11 and verified with
> `./gradlew check`, `gameTestServer` and `verifyServer`.

| Dependency | Current in repo | Latest published | Recommended pin / note |
|---|---|---|---|
| NotEnoughItems | `2.9.4-GTNH:dev` | `2.9.4-GTNH` | `2.9.4-GTNH:dev`; its POM requires CCC **1.4.22** |
| CodeChickenCore | `1.4.22:dev` | `1.5.0` | Use **`1.4.22:dev`** for NEI 2.9.4; `1.5.0` is newer but not the version NEI declares. Test before using 1.5.0. |
| Mantle | `0.5.4:dev` | `0.6.0` | TiC 1.15.0 POM still pins **0.5.4**; stay on 0.5.4 unless testing 0.6.0. |
| Railcraft | `9.18.0:dev` | `9.18.0` | `9.18.0:dev`; API source-compatible (verified constructors/getters). Keep `exclude group: "thaumcraft"`. |
| TinkersConstruct | `1.15.0-GTNH:dev` | `1.15.0-GTNH` | `1.15.0-GTNH:dev`; API source-compatible (`CastingRecipe.output`, `getCastingRecipes`, registry accessors). Pulls newer GTNHLib/Angelica/Natura/ForgeMultipart transitively. |
| ForestryMC | `4.12.0:dev` | `4.12.0` | `4.12.0:dev`; API source-compatible (`CentrifugeRecipe.outputs`, `SqueezerRecipeManager.containerRecipes`, `ShapedRecipeCustom`, `ICarpenterManager.addRecipe`). |
| EnderCore | `0.5.15:dev` | `0.6.0` | EnderIO 2.11.0 POM requires **0.5.15**; use 0.5.15 (or test 0.6.0). |
| EnderIO | `2.11.0:dev` | `2.11.0` | `2.11.0:dev`; API source-compatible (`Recipe`, `RecipeOutput`, `OreDictionaryPreferences`, manager `getRecipes`). |
| ThermalExpansion | `curse.maven:...:2388759` | 1.7.10 final `4.1.5` | No newer 1.7.10 file; cached jar reports 4.1.5. Keep. |
| ThermalFoundation | `curse.maven:...:2388753` | 1.7.10 final `1.2.6` | No newer 1.7.10 file; cached jar reports 1.2.6. Keep. |
| ImmersiveEngineering | `compileOnly ...:2299020` | 1.7.10 final `0.7.7` | No newer 1.7.10 file. **Fix the code's mod-id check (IE bug), not the dependency.** |
| Galacticraft | `3.5.0-GTNH:dev` | `3.5.0-GTNH` | `3.5.0-GTNH:dev`; API source-compatible (`CompressorRecipes.getRecipeList`). Keep GT5/TiC exclusions. |
| Applied Energistics 2 | `rv3-beta-1099-GTNH:dev` | `rv3-beta-1099-GTNH` | `rv3-beta-1099-GTNH:dev`; `IGrinderEntry` API unchanged. Note: Maven metadata's `<latest>` is stale at 999; the directory/cache contains 1099. |
| IC2 | `net.industrial-craft:...:2.2.828-experimental:dev` | actual 2.2.828 file `6833054` | **Important:** GTNH's `useIC2FromCurseforge` substitution rewrites this to `curse.maven:ic2-242638:2353971`, whose jar reports **2.2.827**, not 2.2.828. Keep the standard coordinate unless you deliberately pin `curse.maven:ic2-242638:6833054`; document the discrepancy. |
| Et-Futurum-Requiem | `2.7.0-GTNH:dev` | `2.7.0-GTNH` | `2.7.0-GTNH:dev`; `etfuturum:copper_block` registry name and `blockCopper` registration unchanged (verified source). |
| waila | removed | — | Removed 2026-11-11: no code uses it, and its `cofh-core` transitive dependency defeated the `-PunidictDevLight` profile. |
| StorageDrawers | `2.3.0-GTNH:dev` | `2.3.0-GTNH` | `2.3.0-GTNH:dev`; `CompTierRegistry.register(upper, lower, rate)` unchanged (verified source). |
| JUnit Jupiter | `5.14.4` via BOM | `6.1.3` (or 5.x latest `5.14.4`) | Applied `org.junit:junit-bom:5.14.4`; JUnit 6 requires Java 17+, so 5.14.4 is the right Java-8 line. |
| `junit-platform-launcher` | BOM-managed `1.14.4` | `1.14.4` (5.x) | Resolved by the JUnit BOM above. |
| Mockito | `4.11.0` (`core` + `inline`) | `5.x` | Kept at 4.11: the last Java-8-compatible line; Mockito 5.x needs Java 11. |

### 7.2 "Correct tag" observations

- The `-GTNH` suffix is used correctly on NEI, TiC, Galacticraft, AE2, StorageDrawers and EtF.
- CCC/Mantle/Railcraft/Forestry/EnderCore/EnderIO have no `-GTNH` suffix in their published tags;
  that is correct for those artifacts. waila was removed.
- `curse.maven` entries use file IDs, not semantic tags. The current TE/TF/IE IDs resolve to the last
  1.7.10 releases; the cached jars confirm versions 4.1.5 / 1.2.6 / 0.7.7.
- The IC2 coordinate is the GTNH-canonical coordinate, but it does not resolve to 2.2.828; see above.
- `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` without a version is legal because
  the JUnit BOM aligns it, but it is an implicit tag. Pin it or use a BOM for reproducibility.

### 7.3 Scope classification (fixed 2026-11-11)

The default dev runtime is the full stack, matching the CI/verify/game-test expectations. `dependencies.gradle`
now implements an explicit `heavy(...)` helper: pass `-PunidictDevLight` to move the heavy optional
integrations to `compileOnly` for a focused IC2/EtF runtime. I verified that the dev-light
`runtimeClasspath` contains no CoFH Core (and removed the unused waila dependency, which pulled it
in). Full-stack `runClient` still needs the untracked dev AT or the production pack's equivalent
`World.collidingBoundingBoxes` widening; server verify/game-test runs do not tick item entities.

### 7.4 Applied dependency block (2026-11-11)

The audited versions below were applied to `dependencies.gradle`. The `heavy(...)` helper is the
scope switch: full-stack runtime by default, `-PunidictDevLight` for a focused IC2/EtF runtime.

```groovy
def devLight = project.hasProperty("unidictDevLight")
def heavy = { String notation -> devLight ? compileOnly(notation) : devOnlyNonPublishable(notation) }

devOnlyNonPublishable("com.github.GTNewHorizons:NotEnoughItems:2.9.4-GTNH:dev")
devOnlyNonPublishable("com.github.GTNewHorizons:CodeChickenCore:1.4.22:dev") // NEI 2.9.4 POM pin
heavy("com.github.GTNewHorizons:Mantle:0.5.4:dev")                           // TiC 1.15 POM pin
def railcraft = heavy("com.github.GTNewHorizons:Railcraft:9.18.0:dev")
railcraft.exclude group: "thaumcraft"
heavy("com.github.GTNewHorizons:TinkersConstruct:1.15.0-GTNH:dev")
heavy("com.github.GTNewHorizons:ForestryMC:4.12.0:dev")
heavy("com.github.GTNewHorizons:EnderCore:0.5.15:dev")                       // EnderIO 2.11 POM pin
heavy("com.github.GTNewHorizons:EnderIO:2.11.0:dev")
heavy("curse.maven:ThermalExpansion-69163:2388759")                          // last 1.7.10
heavy("curse.maven:ThermalFoundation-222880:2388753")                        // last 1.7.10
compileOnly("curse.maven:ImmersiveEngineering-231951:2299020")               // last 1.7.10
def galacticraft = heavy("com.github.GTNewHorizons:Galacticraft:3.5.0-GTNH:dev")
galacticraft.exclude module: "GT5-Unofficial"
galacticraft.exclude module: "TinkersConstruct"
heavy("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-1099-GTNH:dev")
devOnlyNonPublishable("net.industrial-craft:industrialcraft-2:2.2.828-experimental:dev")
devOnlyNonPublishable("com.github.GTNewHorizons:StorageDrawers:2.3.0-GTNH:dev")
runtimeOnlyNonPublishable("ganymedes01.etfuturum:Et-Futurum-Requiem:2.7.0-GTNH:dev")
// waila was removed: unused by code and its cofh-core transitive defeated -PunidictDevLight.

testImplementation("net.industrial-craft:industrialcraft-2:2.2.828-experimental:dev")
testImplementation(platform("org.junit:junit-bom:5.14.4"))
testImplementation("org.junit.jupiter:junit-jupiter")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
testImplementation("org.mockito:mockito-core:4.11.0")
testImplementation("org.mockito:mockito-inline:4.11.0")
```

**Do not** bump `Mantle` to 0.6.0, `EnderCore` to 0.6.0 or `CodeChickenCore` to 1.5.0 without a test
run: the current dependent POMs pin older versions, and those latest artifacts may target newer
dependents. The source APIs this mod uses are compatible with the applied table (verified against the
extracted source jars for Forestry 4.12, EnderIO 2.11, Railcraft 9.18, TiC 1.15, Galacticraft 3.5,
AE2 1099, StorageDrawers 2.3 and NEI 2.9.4).

### 7.5 Toolchain / buildscript

- `settings.gradle.kts` uses `gtnhsettingsconvention 2.0.34`, and the wrapper is Gradle **9.7.1**;
  these build updates are committed (`86154d9`). No action needed.
- The auto-update removed the comment explaining why `org.gradle.configuration-cache` must stay
  disabled (RetroFuturaGradle's reobf file collection cannot be serialized). Restore that rationale.
- `accessTransformersFile` is empty while the local AT is auto-detected by RFG; see §1.4. The build
  warning "Found and added access transformers ... configure gradle.properties" is a reproducibility
  smell.

---

## 8. Build / repo / docs / legal hygiene

1. **Docs still say `POST_INIT` for integrations that run at `LOAD_COMPLETE`.** `docs/INTEGRATIONS.md`
   lines 35-45 (AE2, IC2, IE, Chest, EnderIO, Railcraft, Forestry, Drops, StorageDrawers) and several
   source comments are stale. The harness proves the real stage is `LOAD_COMPLETE`.
2. **Fixed 2026-11-11: the `EtFuturum:block_copper` example** in `ConfigData`, `ForgeConfigIO`,
   `ResourceHandler`, `ConfigReaderTest` and `ConfigDataTest` now uses `etfuturum:copper_block`; token
   matching is case-insensitive via `Text.containsIgnoreCase`.
3. **`CODE_REVIEW.md` says "No findings remain open" (line 20/1089) while §5 items and BUG-10 are
   still open.** Either re-open those items or update the status. The review also says the docs
   `max-compat` description is wrong; `ConfigPresets` and `ForgeConfigIO` still say max-compat includes
   `keep-one-entry`, although `maxCompatFrom` pins it `false`.
4. **IC2 machine count:** `docs/INTEGRATIONS.md` says 10; `CODE_REVIEW.md` says 11; the code has 10
   maps (three metal-former managers count as 3). Pick one and state the counting rule.
5. **`README` mod table vs actual registration:** IE now uses the correct id (§1.1, fixed
   2026-11-11); a real IE-installed run is still the final verification. The table also omits
   TiC/Storage Drawers/drops in some older sections.
6. **License:** `LICENSE` and `src/main/resources/LICENSE` are MIT ("Copyright (c) 2026 Duncan
   Campbell"), but 15 source files are explicitly marked "Ported/Rebuilt from ... (WanionCane,
   MPL-2.0)". MPL-2.0 is a file-level copyleft with notice/source obligations. Ship the MPL notice and
   mark the affected files, or obtain clarification from the original author. This is not legal advice;
   it is a compliance risk to resolve before a public release.
7. **CI supply chain:** `build-and-test.yml` and `release-tags.yml` use external reusable workflows at
   `@master`; `verify-server.yml` uses action tags (`@v4`), not commit SHAs. Pin to commits/releases
   for reproducible, reviewable CI. This is a hardening item, not a functional bug.
8. **JitPack:** `jitpack.yml` runs `setupCIWorkspace` (valid) but does not specify a JDK; the project
   needs JDK 25/Gradle 9.7.1. Add a `jdk:` key or verify JitPack's default before advertising the
   artifact.
9. **`mcmod.info`** has no `logoFile`, no optional dependency metadata, and `useDependencyInformation`
   false; this matches the feature backlog but is worth doing at release.
10. **Stale artifacts:** `build/libs/` contains dirty-tree jars; ensure release builds come from a clean
    tree. `build/`, `run/`, `logs/` and the untracked AT are ignored, so CI and local builds can differ.

---

## 9. Test coverage gaps (directly tied to the findings)

| Gap | Why it matters |
|---|---|
| IE mod id / `Loader.isModLoaded` constant | covered by `ModIdsTest` (exact ids) and `ModIdUsageTest` (no new literals) |
| ConfigReader completeness test | now covers every public `ConfigData` field (reflection) |
| Case-insensitive metals/blacklist | now covered by `OreNamesTest.resolveConfiguredNames...` and `ResourceBlackListTest.blackListMatchingIsCaseInsensitive` |
| Item-name token matching | `TextTest` covers the case-insensitive matcher; corrected every code/test example to `etfuturum:copper_block`. The MC-bound registry lookup remains T3. |
| Centrifuge semantic collisions / immutable map | `ForestryIntegrationTest` covers both (BUG-10) |
| Harness/NEI hidden decision | shared `CanonicalIndex` + `CanonicalIndexTest`; same predicate in both paths |
| Late-OD modelled filter | `OreNamesTest.modelledNamesHonoursExcludedKindsAndBlacklistedResources` |
| Report damage suffix | `UniDictReportTest.damageSuffixDistinguishesMetadataFromWildcard`; full `GameData` rendering remains T3 |
| Server-start / module failure injection | `GuardedTest`, `LoadStageExecutorTest` (Error) and `ModuleHandlerTest` (failing module) now cover it |
| No `Accessors.as` failure-path test for the concrete-class branches / TE invokers | §1.5 |
| No test for null main entries / wildcard main entries | §1.8 |
| Minimal preset NEI hiding | `ConfigPresetsTest` now asserts `autoHideInNEI(false)` |

Cheap additions: a reflection-based `ConfigData` field-completeness test; a pure `ModIds` constant
test; a `ForestryIntegrationTest` collision case; a `ReportFormatter`/`UniDictReport` damage case.

---

## 10. Suggested order of work

1. **Fix §1.1 (IE id) and add a verifier** — highest user-visible bug, tiny change.
2. **Fix §1.2 (preset seeds), §1.3 (case matching) and §3.2 (remove unnecessary chest Mixin)** —
   done 2026-11-11; covered by the reflection completeness test, `OreNamesTest`,
   `ResourceBlackListTest`, `TextTest` and the real-loot-entry chest tests.
3. **Fix §1.6 (guarded re-runs/module init) and §1.5 (remaining casts/invoker seams)** — done
   2026-11-11 (`Guarded`, per-module/thread Throwable handling, `Accessors.as`, TE invoker seams).
4. **Fix §2.1/§2.2/§2.3 diagnostics/report** — the harness and `/unidict report` are the product's
   transparency promise; they must be accurate.
5. **Resolve §1.4 dependency scope + AT state**, then apply the dependency table in §7.4 with
   `./gradlew test` + `./gradlew verifyServer` + one full-stack client run.
6. **Fix §3.1 (BUG-10) and §3.3/§3.4 cleanup** — done 2026-11-11 (collision/immutable-map tests;
   unused OreDictionary accessors removed).
7. **Then feature work:** GT5 integration -> `inputReplacement` -> NEI live entries -> `/unidict
   why`/`list`/JSON -> custom resources -> fuels/fluids -> UX/API.

---

## Appendix A — evidence used in this analysis

```bash
# Unit + seam baseline (220 tests)
./gradlew test --offline

# Headless T3 baseline: 300 stage-1 + 5 stage-2 checks, 0 failures, ~40 s
./gradlew verifyServer --offline

# Dependency resolution (IC2 substitution, CoFH runtime)
./gradlew dependencies --configuration compileClasspath --offline
./gradlew dependencies --configuration runtimeClasspath --offline | grep -i cofh

# IE mod id evidence (cached 1.7.10 jar)
unzip -p ~/.gradle/caches/modules-2/files-2.1/curse.maven/ImmersiveEngineering-231951/*/*.jar mcmod.info
javap -classpath <ie.jar> -v blusunrize.immersiveengineering.ImmersiveEngineering | grep -A6 'cpw.mods.fml.common.Mod('

# Forge isModLoaded is case-sensitive: namedMods.containsKey(args[0])
javap -classpath <forgeBin.jar> -c cpw.mods.fml.common.Loader | sed -n '/isModLoaded/,/ireturn/p'

# WeightedRandomChestContent.theItemId is PUBLIC in 1.7.10 (accessor unnecessary)
javap -classpath <forgeBin.jar> -p net.minecraft.util.WeightedRandomChestContent | grep theItemId

# ConfigReader bug: parsed minimal config loses craftingIntegration=false
# (compiled classes, no test changes)
javac -cp build/classes/java/main TestCfg.java
java -cp .:build/classes/java/main TestCfg
# -> minimal craftingIntegration=false / parsed craftingIntegration=true

# Build-tree state: wrapper/settings/plugin updates are uncommitted
git status --short
git diff -- gradle.properties settings.gradle.kts gradle/wrapper/gradle-wrapper.properties
```

**Latest-version sources:** GTNH Maven `maven-metadata.xml` / directory listings for
`com.github.GTNewHorizons:*`, inspected 2026-11-10; CurseForge file API for the 1.7.10
TE/TF/IE/IC2 files; source jars for Forestry 4.12, EnderIO 2.11, EnderCore 0.6, Railcraft 9.18,
TiC 1.15, Galacticraft 3.5, AE2 1099, StorageDrawers 2.3, NEI 2.9.4 and EtF 2.7 to check API/registry
compatibility.

**Not verified in-game in this pass:** a full client run with IE installed (the id bug is already
certain from metadata/FML bytecode), and a full-stack CoFH `runClient` from a clean clone (the AT
mismatch is a reproducibility/robustness risk, not a JVM-proven failure here).

---

## Appendix B — test harness added after this analysis (2026-11-10)

The baseline was extended before starting the fixes, using `MrFuzzihead/PeripheralsPlusPlus` as the
reference for JaCoCo, Mockito, the non-shipped `src/gameTest` source set and the marker-driven
one-shot server/client tasks:

- `./gradlew test` — **241 tests**, 0 failures (JUnit 5.14.4 + Mockito 4.11.0 + mockito-inline).
- `./gradlew jacocoTestReport` — JaCoCo 0.8.16; **44.7% line / 40.6% branch** at the baseline.
  `check` enforces a **44% line / 40% branch** floor and compiles the game-test mod.
- `./gradlew gameTestServer` — **23/23** live-world assertions from the non-shipped
  `src/gameTest` mod (model/report publication, canonical resolution, furnace output, the real
  `UnifyDrops` event, and the rewrite journal).
- `./gradlew clientTest` — the same mod's load-complete checks on the client; needs a display/Xvfb
  and is non-blocking in CI until it has a stable history.
- `./gradlew verifyServer` — unchanged (300 stage-1 + 5 stage-2 checks).

CI: `.github/workflows/test-harness.yml` runs the unit/coverage job and the in-game server job;
`.github/workflows/verify-server.yml` keeps the verify oracle. The next step is to write regression
unit tests for the findings in §1 as the fixes land (they are intentionally not added as failing tests
before the baseline, so the harness stays green).
