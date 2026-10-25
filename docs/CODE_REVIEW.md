# UniDict-Legacy — Code Review: Bugs, Optimizations, Missing Features

- **Repository state:** `main` @ `551d86b` (2026-10-23); working tree was clean before this report was added
- **Scope:** 147 files under `src/`, ~8.3k LOC of main Java, plus the build/config surface, resources
  (mixins, manifest), tests, and the `docs/` contract checked against the code
- **Baseline verified while reviewing:** `./gradlew compileJava` green, `./gradlew test` green
  (29 suites / **160 tests / 0 failures**), `checkstyle` + `spotless` clean
- **Not re-verified:** no in-game (`T3`) run was performed for this review; in-game claims below are
  derived from source + the shipped jars + dependency resolution

Companion docs: [`TODO.md`](TODO.md) (known backlog) · [`INTEGRATIONS.md`](INTEGRATIONS.md) ·
[`STATUS.md`](STATUS.md) · [`TestPlan.md`](TestPlan.md).

> **Status:** five batches have landed — batch 1 (BUG-1/3/4/5/6/7/11), batch 2 (BUG-17, OPT-3, latent
> `maxCompat()` carry-over, cleanup), batch 3 (OPT-1 remainder, OPT-5, OPT-6; OPT-7 subsumed, docs sync),
> batch 4 (headless **`verifyServer`** T3 harness + BUG-15) and batch 5 (BUG-13/14/18/20, BUG-8 **Phase B**
> diagnostic, harness in CI) — see §8 for the change lists and test coverage.
> **BUG-2 is closed as not-a-defect:** the access transformer is dev-only tooling for the local full-stack
> client (the production pack already widens the field), so it must not ship with the mod. Everything else
> below is still open, and the remaining items all need an in-game run to verify (or a larger refactor) —
> see the notes in §8.

Severity legend: **S1** = user-visible wrong behaviour / release blocker · **S2** = wrong or
misleading behaviour in a real configuration · **S3** = robustness / maintainability / docs.

---

## Executive summary — the five things to fix first

| # | Finding | Severity | File |
|---|---------|----------|------|
| 1 | `resourceBlackList` is applied **after** the canonical lookup index is built, so "excluded" resources are still unified (machines, drops, chest loot) while only disappearing from the report/NEI. The default preset blacklists `Aluminium`, so this is out-of-the-box behaviour. | **S1** | `resource/UniResourceHandler.java:150-161` |
| 2 | ~~The former CoFH/TE crash fix (`unidict_at.cfg`) is not wired~~ — **closed, not a defect** (dev-only tooling; see the BUG-2 section). | — | `gradle.properties:98`, `.gitignore:43-44` |
| 3 | Per-kind owner order (`ownerOfKind.*`) **never matches at runtime**: kinds are registered lowercase (`ingot`), while docs, javadoc and T1 tests use `INGOT`. A user copying the documented example gets a silent no-op. | **S1** | `resource/UniResourceContainer.java:104-107` |
| 4 | Canonicalisation **strips NBT**: `getMainEntry(size)` builds a bare `new ItemStack(item, size, meta)`, and every rewrite path (chest loot, crafting, furnace/IC2/IE/TE/EIO/AE2/TiC, `findMatchingRecipe`) uses it. Only `UnifyDrops` guards NBT. | **S1/S2** | `resource/UniResourceContainer.java:62-68`, `resource/ResourceHandler.java:190-200` |
| 5 | The `findMatchingRecipe` mixin **bypasses the config switches**: it only checks that the resource handler exists (always true after POST_INIT), so `craftingIntegration=false` and even `integration=false` still unify crafting results. | **S2** | `mixins/early/CraftingManagerMixin.java:35-50` |

Two structural issues follow from the same root cause as #1 and #4 and are worth fixing in the same
change (see `BUG-6`, `BUG-8`): the resolver always allocates a fresh stack (so "rewritten" counts are
inflated and re-runs never short-circuit), and the resource model is a frozen POST_INIT snapshot
(late OreDictionary registrations are invisible).

---

## 1. Bugs

### BUG-1 — `resourceBlackList` does not exclude a resource from unification (S1)

**Where:** `src/main/java/com/mrfuzzihead/unidict/resource/UniResourceHandler.java:150-161`

```java
final ResourceHandler resourceHandler = new ResourceHandler(resourceMap);
resourceHandler.populateIndividualStackAttributes();   // index + containerMap filled here
for (final String blackListedResource : Config.get().resourceBlackList) {
    resourceMap.remove(blackListedResource);           // too late
    apiResourceMap.remove(blackListedResource);
}
UniDict.resourceHandler = resourceHandler;
```

`ResourceHandler.resources` is a live `resourceMap.values()` view, so `remove()` does hide the
resource from the report/NEI iteration — but `individualStackAttributes` and `containerMap` were
already populated, and both are what `getMainItemStack()` uses.

**Impact**

- A blacklisted resource's variants are still canonicalised by every machine rewrite, `UnifyDrops`,
  chest loot, and the crafting path.
- `getMainItemStack()`'s OreDictionary fallback (`containerForOreName`) still resolves the
  blacklisted name, so even stacks absent from the index get rewritten.
- `containerExists()` still returns `true` for a blacklisted name (misleads `VerifyHarness`), and
  `UniDictReport`/NEI silently disagree with what machines actually emit.
- Default config blacklists `Aluminium` (`ConfigPresets.minimal/standard`). So out of the box,
  `ingotAluminium`/`oreAluminium`/… are still unified among themselves (per owner priority) even
  though the pack author asked for them to be left alone.

**Fix:** filter before indexing — remove blacklisted entries from `resourceMap`/`apiResourceMap`
(or skip them while registering containers) *before* constructing `ResourceHandler`, and make
`populateIndividualStackAttributes()` `containerMap.clear()` first so a future re-index cannot leave
stale containers. Add a T2 test: blacklist a resource, assert `getMainItemStack` returns identity and
`containerExists` is false.

---

### BUG-2 — the access transformer is dev-only tooling (closed, not a defect)

**Status: closed (2026-10-24).** The owner confirmed the CoFH/TE field widening is provided by the
**production modpack**; the AT in `src/main/resources/META-INF/` is only needed to boot a local full-stack
dev client, and it is deliberately kept out of the repo and out of the published jar. This is not a bug
and needs no code change.

The dev path is confirmed: RFG auto-detects ATs placed in `src/main/resources/META-INF/` and applies
them to dev runs ("Found and added access transformers in the resources folder"), while the built jar
still has no `FMLAT` — exactly the dev/prod split intended.

What the review originally observed (kept for context): `accessTransformersFile` is empty, the file is
git-ignored/untracked, and the built jar has no `FMLAT`. That is the intended state — the only residual
nit is that a **locally** built jar can differ from a CI-built jar when the untracked file happens to be
present (it ships an inert `META-INF/unidict_at.cfg`). Harmless, but if you want local and CI artifacts to
be byte-comparable, delete the untracked file before a local release build (or ignore `META-INF/` when
packaging). No action required for release correctness.

**Where:** `gradle.properties:98` (`accessTransformersFile =`), `.gitignore:43-44`,
`src/main/resources/META-INF/unidict_at.cfg` (untracked), root `META-INF/` (untracked)

Evidence gathered:

```text
$ grep -n accessTransformersFile gradle.properties
98:accessTransformersFile =

$ git ls-files src/main/resources
src/main/resources/LICENSE
src/main/resources/mcmod.info
src/main/resources/mixins.unidict.{early,late,}.json      # no META-INF/, no AT

$ unzip -p build/libs/…-dirty.jar META-INF/MANIFEST.MF
Manifest-Version: 1.0
FMLCorePluginContainsFMLMod: true
FMLCorePlugin: com.mrfuzzihead.unidict.mixins.EarlyMixinsLoader
TweakClass: org.spongepowered.asm.launch.MixinTweaker
MixinConfigs: mixins.unidict.json
ForceLoadAsMod: true                                      # no FMLAT
```

The locally-built jar *contains* `META-INF/unidict_at.cfg`, but nothing references it, so it is inert;
a clean clone does not even contain the file (it is git-ignored). Meanwhile `cofh-core` **is** on the
runtime classpath:

```text
$ ./gradlew dependencies --configuration runtimeClasspath | grep cofh
+--- com.github.GTNewHorizons:Railcraft:9.17.31
|    +--- curse.maven:cofh-lib-220333:2388748
+--- curse.maven:ThermalExpansion-69163:2388759
     +--- curse.maven:cofh-core-69162:2388751
```

**Impact:** the `IllegalAccessError: … World.collidingBoundingBoxes from cofh.asmhooks.HooksCore`
crash documented as P0 #3 is still live for any pack that has TE/CoFH; the AT that was written to fix
it is shipped but never applied. (`TODO.md` marks this "parked" — this finding is that the *repo
state* cannot ship the chosen fix even if it is un-parked.)

**Fix:** commit the AT (remove the `.gitignore` entry), add
`accessTransformersFile = unidict_at.cfg` to `gradle.properties`, rebuild, assert `FMLAT` in the jar
manifest, and run the full-stack `runClient`. If the AT must stay dev-only, delete it from
`src/main/resources` so release jars are reproducible and CI builds do not silently differ from local
builds.

---

### BUG-3 — `ownerOfKind.*` never applies at runtime because of kind-name casing (S1)

**Where:** `resource/UniResourceContainer.java:104-107`, `resource/UniResourceHandler.java:96-112`,
`pure/config/ConfigReader.java:123-128,237-243`

Kinds are registered from OreDictionary prefixes:

```java
final String kindName = matcher.replaceFirst("");     // "ingotCopper" -> "ingot"
...
Resource.registerAndGet(kindName);                    // name stored lowercase
```

but the lookup is case-sensitive:

```java
if (kindName != null && Config.get().ownerOfKind.containsKey(kindName))
```

while the contract documented in `ConfigData`, `OwnerOrder` (`"INGOT"`), `ConfigReaderTest`,
`OwnerOrderTest` and `ResourceTest` all use **uppercase** (`INGOT`, `DUST`, …). A user who writes
`ownerOfKind.INGOT=<mods>` (the shape every doc/example shows) gets a silent no-op. The legacy alias
`ownerOfEvery<Kind>` has the same problem: `ownerOfEveryIngot` becomes `ownerOfKind.Ingot`.

Note the adjacent inconsistency: `hideInNEIBlackSet` is matched by *exact* kind name too
(`UniResourceHandler.getKindBlackSet()` → `Resource.getKindOfName("ore")`), but its docs use
lowercase — so two neighbouring knobs silently use different conventions.

**Fix:** normalise kind names to lowercase in `ConfigReader`/`OwnerOrder` (and on `childrenOfMetals`
to avoid the next trap: `childrenOfMetals=[Ingot]` registers `Ingot` and then `unifiedKinds.contains("ingot")`
fails, producing **zero** containers). Add a T1 test that uses the names produced by
`UniResourceHandler` rather than hand-written ones, plus a startup WARN listing configured kind
names that do not exist (which would also give `kindDebugMode` a purpose — see BUG-18).

---

### BUG-4 — canonicalisation silently drops NBT (S1/S2)

**Where:** `resource/UniResourceContainer.java:62-68`, `resource/ResourceHandler.java:190-200`

```java
public ItemStack getMainEntry(final int size) {
    return new ItemStack(mainEntryItem, size, mainEntryMeta);   // no tag copy
}
```

`getMainItemStack()` returns this fresh stack for every modelled item (index hit or OD fallback).
`UnifyDrops` deliberately skips `stack.hasTagCompound()`, but **no other caller does**:
`ChestIntegration.rewriteContent`, `CraftingIntegration.rewriteCraftingOutputs`,
`OutputRewriter` (furnace/IC2/IE/TE/TiC), `AE2Integration`, `EIOIntegration`, and the
`CraftingManagerMixin` result hook all replace outputs with the tagless copy.

**Impact:** any OreDictionary-registered item whose identity depends on NBT (machine-state items,
tagged loot, mod variants distinguished by a tag) loses its tag when a recipe/loot entry/craft
result is rewritten. Chest loot is the riskiest surface: mods routinely add loot entries carrying
NBT, and the entry is mutated in place for all future world generation.

**Fix:** mirror `UnifyDrops` — return the input unchanged when `thing.hasTagCompound()`; or, when item
and damage already match the canonical entry, `copy()` the input and only normalise size. Add a T2
test asserting NBT survival for chest/crafting/furnace paths.

---

### BUG-5 — `CraftingManagerMixin` ignores the crafting/integration toggles (S2)

**Where:** `mixins/early/CraftingManagerMixin.java:35-50`

```java
final ResourceHandler handler = UniDict.resourceHandler;
if (handler == null) return;            // always non-null after POST_INIT
...
final ItemStack canonical = handler.getMainItemStack(result);
if (canonical != result) cir.setReturnValue(canonical);
```

`UniDict.postInit()` runs the resource pipeline unconditionally, so this hook canonicalises every
matching craft result even with `craftingIntegration=false` **and** `integration=false`. The mixin is
registered unconditionally (`Mixins.CRAFTING`, no `addRequiredMod`), so there is no way for a pack
author to disable it.

It is also on the hottest path in the mod: `findMatchingRecipe` is called on every crafting-grid
change and constantly by NEI previews, and each call pays `isProtected` (one `OreDictionary.getOreIDs`
allocation) plus an index lookup plus possibly a second `getOreIDs` (see OPT-1).

**Fix:** gate on `Config.crafting() && Config.integrationModule()`, and short-circuit on identity when
the result is already canonical (OPT-1). Long term, once the in-place mutator is trusted, consider
removing the hook entirely (it is documented as a "best-effort safety net").

---

### BUG-6 — "rewritten=N" counts are inflated and the re-runs are not true no-ops (S2)

**Where:** `resource/ResourceHandler.java:190-200`; consumers
`integration/OutputRewriter.java:73,98,146`, `integration/CraftingIntegration.java:98`,
`integration/ForestryIntegration.java:299,324`, `integration/ChestIntegration.java:105-114`,
`integration/AE2Integration.java:150-180`, `integration/GalacticraftIntegration.java:68-79`

`getMainItemStack` allocates a new stack for every modelled item, even when the input already *is*
the canonical entry. Every integration decides "did it change?" by **reference**
(`canonical != output`, `isChanged()` via `!=`), so all of them see change on every modelled output on
every pass.

**Impact**

- `/unidict report` and the `[unidict-verify]` totals are wrong by construction. Reported figures
  such as furnace `rewritten=465`, crafting `rewritten=187/200`, TE `rewritten=253`,
  IC2 `rewritten=69` count *recipes touching a unified resource*, not recipes whose output changed.
- The server-start re-runs (`runCraftingAtServerStart`, `runIC2AtServerStart`,
  `runTinkersConstructAtServerStart`) rewrite/re-report everything rather than the new entries.
- The `CraftingIntegration` javadoc claim "already-canonical outputs are skipped so re-running is
  safe" is not true in the counting sense (it is functionally harmless, but it hides real regressions).

**Fix (small, high leverage):** in `getMainItemStack`, return the **input identity** when the
resolved main entry has the same item+damage+size (and the input has no NBT). That single change makes
every existing `!=` check meaningful, makes re-runs genuine no-ops, removes a stack allocation per
resolved output, and fixes the report numbers — without touching any integration.

---

### BUG-7 — `Util.getModName` throws on unusual registry entries and sits on hot paths (S2)

**Where:** `common/Util.java:31-35`

```java
final String name = GameData.getItemRegistry().getNameForObject(itemStack.getItem());
return name.substring(0, name.indexOf(58));
```

No null check on `itemStack`, on `name`, or on the `:` index. Callers:

- `ResourceHandler.isModBlacklisted` (`:44-47`) — the NEI hide pass.
- `Util.itemStackComparatorByModName` / `SpecificKindItemStackComparator.compare` — **sorting during
  `postInit`**, which is *not* wrapped in a try/catch by `UniDict.postInit` → a crash at load.
- `VerifyHarness.describe` and `UniDictReport.describe` — report generation.

**Impact:** one unregistered/odd item inside an OreDictionary list (or an item registered without a
namespaced name) can take down the resource pipeline, the NEI pass, or `/unidict report`.

**Fix:** guard and fall back (`"unknown"`), e.g.:

```java
if (itemStack == null || itemStack.getItem() == null) return "unknown";
final String name = GameData.getItemRegistry().getNameForObject(itemStack.getItem());
if (name == null) return "unknown";
final int colon = name.indexOf(':');
return colon < 0 ? name : name.substring(0, colon);
```

and wrap `runResourcePipeline()` in `UniDict.postInit` with a `try/catch` that logs and degrades
instead of killing startup.

---

### BUG-8 — the resource model is a one-shot POST_INIT snapshot (S2)

**Where:** `resource/UniResourceContainer.java:37,48-55,88-91`; `resource/ResourceHandler.java:248-269`

- `entries` is `final` and taken once from OreDictionary in the constructor.
- `SelectionRules.shouldResort(...)` can therefore never return `true` in production (size can never
  change) — that path is effectively dead (`SelectionRulesTest` still exercises it in isolation).
- `ResourceHandler.populateIndividualStackAttributes()` is called exactly once (from
  `UniResourceHandler.postInit`), although its javadoc advertises "safe to re-run".
- `UniResourceHandler.hasInit` prevents a second pipeline, and `containerMap` is never cleared.

**Impact:** any OreDictionary registration that happens *after* UniDict's POST_INIT handler — another
mod's `postInit` that runs later in the FML order, scripted/custom recipes, a late-loaded mod — is
invisible to selection, rewrites, drops, NEI hiding and the report. It also blocks the deferred
`/unidict reload` feature (TODO P2 #8), because there is no refresh path to re-use.

**Status:** **Phase B (diagnostic) done in batch 5** — see below. Phase A (the actual fix) remains.

**Fix:** (a) re-read the live OreDictionary list at the start of `populateIndividualStackAttributes`
(or add a `refreshEntries()` that re-snapshots + re-sorts), and (b) call the re-index at `LOAD_COMPLETE`
(before the verify pass) so late registrations are picked up; optionally subscribe to
`OreDictionary.OreRegisterEvent` to append new members incrementally. Clear `containerMap` on
re-index.

#### Measured gap (dev pack, 2026-10-24) — this bug is real

`verifyServer` now reports the added names and, separately, the ones the **configured** taxonomy would
have modelled (the raw 373 also include unconfigured alloys such as Adamantine):

```text
[unidict-verify] PASS lateOd added=373 modelled=26 total=1002
  example=[blockBrass, blockOsmium, blockZinc, dustBrass, dustMagnesium, dustOsmium, dustSteel,
           dustTitanium, dustTungsten, dustZinc, ingotBrass, ingotDesh, ingotMagnesium, ingotOsmium,
           ingotTungsten, ingotUranium, ingotZinc, oreBronze, oreElectrum, oreInvar, oreMagnesium,
           oreOsmium, oreSteel, oreTitanium, oreTungsten, oreZinc]
```

So in this pack **configured metals and kinds are missing from the model**: ore/dust/ingot/block variants
of Brass, Osmium, Zinc, Magnesium, Steel, Titanium, Tungsten, Uranium and Desh. (Thermal Foundation and
Galacticraft register their Ore Dictionary entries during their own `postInit`, which runs after UniDict's;
TiC/ExtraTiC add alloy families the same way.) None of those entries get unified, hidden in NEI or counted
in the report today. Phase A is therefore not theoretical — `OreNamesTest.modelledNames…` pins the rule.


#### Why this fix is higher risk than it looks

The snapshot itself is trivial to refresh; the risk is **acting on a changed model after other code has
already consumed the old one**. Concrete failure modes, in order of severity:

1. **A/B split across integrations.** Re-snapshotting can change the canonical entry (a late variant
   with a higher owner priority, or a new container that wins the (item, damage) slot in the index).
   Only crafting, IC2, TiC and Galacticraft re-run at server start; furnace, AE2, IE, TE, EIO, Railcraft,
   Chest and Forestry do not. A second index would leave crafting pointing at B while the furnace still
   emits A — two "unified" entries, and a report that disagrees with machine output.
2. **`updated` is a one-shot latch.** `Resource.updateEntries()` and
   `UniResourceContainer.updateEntries()` both early-return once `updated` is set, and the main entry is
   only computed in that one pass. A refresh needs new entry points and an explicit decision about the
   canonical entry: re-sort (may move index 0) or append-only (stable, but a late higher-priority
   variant can never win). `SelectionRules.shouldResort` has never actually fired in production, so its
   first real use is untested in-game.
3. **NEI hide set is computed once, at POST_INIT.** Newly discovered non-main variants would never be
   hidden, so NEI silently shows duplicates again — the exact user-facing problem the mod exists to
   solve. Re-running the pass needs `API.hideItem` on the client main thread and a working assumption
   about when NEI builds its item panel.
4. **Index rebuild is not atomic as written.** `populateIndividualStackAttributes` clears the index and
   `containerMap` and repopulates them in place. A mid-session refresh (e.g. from an `OreRegisterEvent`
   fired by another mod or a script) on a different thread could expose a half-built index: a miss is
   benign, but a partially rebuilt `containerMap` weakens the OD fallback and can silently change which
   container a stack resolves to. Build a fresh index and swap the reference instead.
5. **Selection heuristics are order-dependent, and the index is last-wins.** For a stack registered in
   several modelled groups, the container is whichever was indexed last. Adding a late container can
   flip an existing stack's canonical target — the same class of cross-wiring the 2026-10-23 identity
   fix addressed, just at a second point in time.
6. **Empty-model integrations never re-run.** Every integration early-skips on
   `resourceHandler.resources.isEmpty()`. If the model is empty at POST_INIT and only fills at
   LOAD_COMPLETE, all POST_INIT integrations (AE2, IC2, IE, Chest, EIO, Railcraft, Forestry, Storage
   Drawers, drops) have already skipped; a refresh must also re-invoke them, which is a schedule change
   (the project has hit that twice: furnace POST_INIT → LOAD_COMPLETE, GC → server start).
7. **Additive integrations are not idempotent.** Forestry's crate/bronze recipes are *added* through
   `carpenterManager.addRecipe` with no "already present" guard; re-running the module on a refresh
   would duplicate them. The machine rewriters are now delta-idempotent (batch 1), the additive ones are
   not.
8. **`OreRegisterEvent` volume and re-entrancy.** A GTNH pack fires thousands of `registerOre` calls
   during load. Per-event re-indexing is O(names × variants) and runs while the model is still being
   built, i.e. re-entrant with `createResources`. It would need coalescing to a single end-of-stage
   rebuild.
9. **Determinism guarantee.** Reports are meant to be stable/diffable run-to-run. A second build point
   depends on which mod registers what, when, which varies with mod order — the reason M2 replaced the
   thread pool with a fixed sequence in the first place.

**Low-risk alternatives, best first:**

- **A. Build the model later, once (recommended first step).** Move `runResourcePipeline()` from
  `postInit` to `loadComplete` (after every mod's `postInit`, before the integration/verify pass) and
  move the POST_INIT integrations to `LOAD_COMPLETE` accordingly. No mid-session refresh, no A/B split,
  no new index machinery — just a later single build. Cost: it is a **schedule change**, so it needs one
  in-game run (the verify harness already proves the model contents), and the load order inside
  `loadComplete` must publish the handler before the rewritten modules run.
- **B. Observe without acting (cheap, fully unit-testable).** At `LOAD_COMPLETE`, diff the live OD names
  against the model snapshot and emit a `[unidict-verify]`/report line (`late-od=<n>`). That gives a
  field diagnostic for the exact packs that would need A, with zero behavioural risk.
- **C. Add-only incremental refresh (only if A is impossible).** Append new variants to existing
  containers (never change index 0), rebuild the index into a new object and swap it, then re-run only
  the delta-idempotent integrations and re-run the NEI hide set (never hide a known main entry). Guard
  behind a config flag; additive integrations must be skipped on re-run.

---

### BUG-9 — hard accessor casts abort a whole integration when a mixin did not apply (S2)

**Where:** `integration/ForestryIntegration.java:222-223` (and the same pattern in
`EIOIntegration:105`, `TEIntegration:130-166`, `RailcraftIntegration:87-90`,
`ChestIntegration.getChestInfo:75-76`)

```java
if (!(grid instanceof ShapedOreRecipe)) continue;
gridRecipes.add((IShapedOreRecipeAccessor) (Object) grid);   // no interface check
```

`CraftingIntegration` gets this right (`recipe instanceof IShapedOreRecipeAccessor`). Here the cast is
unchecked; if the mixin failed to apply (another coremod transforming the same class, a future Mixin
version rejecting the merge, a foreign `IRecipe` subclass), a `ClassCastException` is caught by the
integration's outer `catch (Exception)` and **all** of that integration's work is skipped —
carpenter, squeezer, centrifuge, bronze recycling and crates for Forestry.

**Fix:** check `instanceof <AccessorInterface>` before each cast (skip with a warn), and/or isolate
each machine in its own try/catch so one failure cannot disable the others.

---

### BUG-10 — centrifuge product rewrite is only correct for identity-keyed maps (S3)

**Where:** `integration/ForestryIntegration.java:290-305`

```java
final Map<ItemStack, Float> mapped = new LinkedHashMap<>();
for (...) mapped.put(canonical, entry.getValue());    // fresh stack per canonical
if (changed > 0) { products.clear(); products.putAll(mapped); }
```

`ItemStack` in 1.7.10 has no value `equals`, so today the new keys are distinct objects and nothing is
merged. But Forestry has `ItemStackMap`-style maps in the same package; if `CentrifugeRecipe.outputs`
is ever backed by a value-equality map (or a future Forestry version switches), two variants mapping
to the same canonical entry would collapse into one product and the last chance value would silently
win. The replacement is also not atomic (`clear()` then `putAll()`); the caller catches
`UnsupportedOperationException`, so an immutable map is handled, but a failure between the two calls
would leave the recipe with no products at all.

**Fix:** build the replacement map into a temporary, detect key collisions explicitly (merge chances
or skip+warn), and swap contents in one `clear(); putAll()` after all mapping is done (as now), with
the collision decision documented.

---

### BUG-11 — "empty" is used as the "not initialised" sentinel in `ModuleHandler` (S3)

**Where:** `module/ModuleHandler.java:32` (`if (module.isEmpty()) module.init();`)

`AbstractModule.isEmpty()` returns `executor.isEmpty()`. A module that legitimately registers nothing
is therefore re-`init()`ed on every `startModules` call; a module that registers something can never
re-init. Today this is benign (a re-init registers nothing new), but it is exactly the mechanism a
`/unidict reload` would need, and a config reload that starts registering threads would construct
**new** `AbstractModuleThread` instances (`LoadStageExecutor.add` dedupes by identity), duplicating
`UnifyDrops`'s event registration.

**Fix:** add an explicit `private boolean initialized` and set it after `init()`.

---

### BUG-12 — per-kind comparator cache captures the `ConfigData` forever (S3)

**Where:** `common/SpecificKindItemStackComparator.java:31,42-44`; `UniDict.java:71`

`CACHE.computeIfAbsent(kindName, k -> new SpecificKindItemStackComparator(k, Config.get()))` captures
the config instance at first use, whereas the global comparator (`Util.itemStackComparatorByModName`)
resolves `Config.get()` live on every comparison. After any reload the two paths disagree, and
`UniDict.loadComplete` refers to a `SpecificKindItemStackComparator.nullify()` lifecycle method that
**does not exist** (stale comment from M2).

**Fix:** drop the captured `ConfigData` and read `Config.get()` in `compare()` (matching `Util`), or
implement the referenced `nullify()` and call it after `Config.load`/reload.

---

### BUG-13 — Galacticraft report line is frozen at the first run

**Status: fixed in batch 5.** The `RAN_PER_STAGE` guard is gone; every server start records its delta into
the accumulating journal (the harness asserts the `integration=Galacticraft` line).

**Where (original):** `integration/GalacticraftIntegration.java`

`RAN_PER_STAGE.add("compressor")` guards the journal/log/verify lines. The rewrite itself reruns on
every `FMLServerStartedEvent` (world load), but the reported count never updates, so the second and
later worlds report stale numbers. The guard is also unnecessary now that `RewriteJournal.record`
updates rather than appends.

**Fix:** always record; let `RewriteJournal` de-duplicate.

---

### BUG-14 — ignored/legacy config keys are collected but never logged

**Status: fixed in batch 5.** `Config.load` now logs
`[unidict] config preset=<name> ignoredKeys=[…]` (sorted, de-duplicated via the new
`ConfigReader.describeIgnored`); `verifyServer` asserts the line.

**Where (original):** `config/ForgeConfigIO.java`, `Config.java`

`ConfigReader.parse` returns `Result.ignored`, but `ForgeConfigIO.load` throws it away. `TODO.md`
and the `Config` javadoc both promise "removed-mod/deferred keys collected for INFO (never fatal)".
A user editing an old `keepOneEntry`/`customUnifiedResources`/removed-mod key gets no feedback that it
does nothing.

**Fix:** log `result.ignored` once at INFO from `Config.load` (and include the resolved preset name).

---

### BUG-15 — the furnace integration has no `[unidict-verify]` line

**Status: fixed in batch 4** (`integration=furnace rewritten=N`, asserted by `verifyServer`).

**Where (original):** `integration/FurnaceIntegration.java`
Every other integration emits `VerifyHarness.record(...)`; furnace only wrote `RewriteJournal` and an
INFO log, so the T3 contract silently missed it.

---

### BUG-16 — EIO alloy rebuild drops extra outputs (S3)

**Where:** `integration/EIOIntegration.java:52-70` (`ALLOY_VIEW`)

`getItems()` returns only `recipe.getOutput()` (the first output) and `rebuild()` constructs a new
`Recipe` with a single `RecipeOutput`. If an alloy-smelter recipe has more than one output, the
rebuilt recipe silently loses them. The SAG view (`:77-95`) correctly maps every output.

**Fix:** mirror the SAG view — map `original.getOutputs()` and rebuild with the full array.

---

### BUG-17 — NEI hiding can hide an item that is canonical elsewhere

**Status: fixed in batch 2.** The hide set now exempts every container's canonical main entry, so an
item that is a non-main variant of container A but the canonical entry of container B stays visible.
Covered by `NEIHideThreadTest` (`canonicalEntryOfAnotherContainerIsNeverHidden`,
`variantThatIsNotCanonicalAnywhereIsStillHidden`, `isCanonicalElsewhereMatchesItemAndDamageNotInstanceIdentity`).

**Where (original):** `nei/NEIHideThread.java:60-73`, `pure/SelectionRules.java:53-76`

The hide set is computed per container (indices 1..n). An item that is a non-main variant of container
A but the **canonical main** of container B (multi-tag registration, or an item that is a member of
two modelled OreDictionary groups) will be hidden in NEI even though machines will hand it to the
player. The per-container logic cannot see this.

**Fix:** build the global set of every container's `getMainEntry()` first and pass it as an
"alwaysVisible" predicate (the seam already supports that), so no canonical entry is ever hidden.

---

### BUG-18 — `kindDebugMode` is a dead config key

**Status: fixed in batch 5.** `kindDebugMode` now logs
`[unidict] kindDebug registered=[…] unknown=[…]`, and the diagnostic is **always** emitted as the
`kindDiagnostics` verify line (asserted by `verifyServer`). `KindNames.unknownKindNames` /
`configuredKindNames` / `sorted` are pure and T1-tested. (A configured kind with no matching dictionary
entries is legitimate — e.g. `chunk` in the dev pack — so it is informational, not a FAIL.)

**Where (original):** `pure/config/ConfigData.java`, `config/ForgeConfigIO.java`

Documented as "Log kind information for the transparency report (BB-1)" and advertised in the
`max-compat` preset / README. It does nothing. Given BUG-3, this is the natural place to emit
"registered kinds: …", "configured kind `X` is unknown", and the per-resource kind bits.

**Fix:** either implement it (recommended — it directly mitigates BUG-3) or remove the key and its
README/preset mentions.

---

### BUG-19 — dead code and stale comments (S3)

Verified-unused or misleading code:

- `MetaItem.toItemStack`, `getArray`, `getList`, `getSet`, `populateMap`, `getCumulative` — no
  production callers (the `MetaKey` javadoc still discusses `toItemStack`'s lossiness).
- `UniOreDictionary`: `getName`, `getFirstEntry`, `getLastEntry`, `get(ItemStack)`,
  `get(Collection)`, `getId` are unused (only tests use some); `stackToName` is id-keyed and stale
  after `FMLModIdMappingEvent` (already noted in `TODO.md` #3b).
- `ResourceHandler.getMainItemStackList`, `setMainItemStacks` (both overloads), `getResources` —
  unused public surface.
- `Resource.register`, `getChildrenMap`, `getResources(..., String...)` — unused.
- `GalacticraftIntegration.rewriteOutputs` + `AccessorPair` + `AccessorPairFactory` — only used by
  its test; production uses `rewriteOutput`.
- `UniResourceContainer.id` field, `initialSize` (see BUG-8), `UniOreDictionary.entryToName`
  (identity map keyed by lists that no caller passes back).
- `UniDict.java:71` references `SpecificKindItemStackComparator.nullify()`, which does not exist.
- Root `META-INF/` (`MANIFEST.MF`, `cofh_at.cfg`, `gregtech_at.cfg`, `hodgepodge_at.cfg`) is a
  leftover of another project; it is untracked but shipping it in a local build is confusing.

**Fix:** delete or wire up; keep the `pure` package as the canonical source of truth. The
`DeterminismGuardTest` already greps `src/main`, so extended greps for dead type usage would be cheap.

---

### BUG-20 — mixin method-name shadowing risk

**Status: fixed in batch 5.** The accessors are now `unidict$getRecipeOutput()` / `unidict$setRecipeOutput()`
(same prefix style as the Ore/IC2 accessors), so the mixins **add** methods instead of shadowing the
target's `IRecipe#getRecipeOutput()`. Verified by a clean `verifyServer` boot plus every crafting rewrite
line.

**Where (original):** `crafting/IShapedRecipesAccessor.java` / `IShapelessRecipesAccessor.java` +
`mixins/early/ShapedRecipesMixin` / `ShapelessRecipesMixin`

The accessor interface declares `ItemStack getRecipeOutput()`, which is **the same signature as
`IRecipe.getRecipeOutput()`**, so the mixin merges an override of the target's own method (rather than
a new `unidict$…` method as `IShapedOreRecipeAccessor`/`IAdvRecipeAccessor` correctly do). It works
today, but it is the kind of merge Mixin is allowed to reject on a future version, and it makes the
accessor's intent ambiguous. Note that the `AdvRecipe`/`AdvShapelessRecipe` variant also writes a
`final` field through an `@Accessor` setter; that is load-bearing (per the M5/IC2 notes) and should
carry a test or a startup check so a Mixin upgrade failure is diagnosed, not silent.

**Fix:** rename the interface methods to `unidict$getRecipeOutput()` / `unidict$setRecipeOutput()` and
update `CraftingIntegration` (the `instanceof` checks already discriminate the recipe classes).

---

## 2. Optimizations

### OPT-1 — add a canonical identity fast path (also fixes BUG-6)

**Status: done.** Batch 1 added the identity return (accurate change detection / no-op re-runs) via
`CanonicalRules`; batch 3 removed the last allocation on that path: `ResourceHandler.getMainItemStack`
now reads `UniResourceContainer.getMainEntryItem()/getMainEntryDamage()` for the decision and only builds
a stack when it will actually replace the input. So a canonical drop, a canonical crafting lookup and a
re-run over already-rewritten recipes allocate **nothing** — which also subsumes OPT-7.

_Original note:_ return `thing` when the resolved entry matches item+damage and `thing` has no NBT; this
makes every `!=` check correct and lets the `findMatchingRecipe` hook short-circuit.

### OPT-2 — cache the protection decision

`isProtected` (`ResourceHandler:64-90`) is invoked before *every* resolution and performs
`OreDictionary.getOreIDs` (allocates an `int[]`) plus up to N substring scans, and the registry lookup
if `protectedItemNames` is set. `containerForOreName` then repeats `getOreIDs` on a miss, so an
unmodelled stack pays 2–3 OreDictionary lookups on every dropped item. Since protection is a function
of `(Item, damage)` and config (fixed after load), precompute it into an
`IdentityDamageIndex<Boolean>` (wildcard-aware) at index-build time — or at least check the exact index
first and only consult `isProtected` for misses.

### OPT-3 — avoid boxing in `IdentityDamageIndex`

`Map<Object, Map<Integer, V>>` boxes the damage value on every `get`/`put` — this is the hot resolver.
Trove is already a dependency (`TLongObjectMap` etc.), so the inner map can be a
`TIntObjectHashMap<V>` (or a single `TLongObjectMap<V>` keyed by `System.identityHashCode(item) << 32 | damage`
with an identity guard). At minimum, size the outer map with a capacity hint (`resources × kinds`).

### OPT-4 — `getEntries()` copies on every call

`UniResourceContainer.getEntries()` returns `new ArrayList<>(entries)`. The NEI pass, the verify
harness (twice per container) and the report all copy it again. Expose an unmodifiable view (or a
`forEach`/`size` pair) for read-only callers; keep the copy only for the T2 seams that mutate.

### OPT-5 — `UniResourceHandler.createResources()` regex work is O(names × resources)

**Status: done (batch 3).** The taxonomy rules moved into the pure, T1-tested `pure/OreNames` helper:
one snapshot of the dictionary names feeds two linear passes (ingot-prefixed discovery, then longest-
suffix kind grouping) with no `Matcher`/`replaceFirst` allocation per entry, and `OreNamesTest` +
`OreNamesLegacyEquivalenceTest` prove the new classifier produces exactly the old regex pipeline's
resource/kind map on a realistic mixed fixture.

Two full scans of every OreDictionary name plus, for each name, `Pattern.matcher(...).replaceFirst("")`
and a regex `find()`; the second pass uses a giant alternation `(Aluminum|Aluminium|Ardite|…)`.
On a GTNH-sized dictionary (50k+ names) this is measurable at load time and allocates a `Matcher` per
name. A single pass that extracts the suffix once (e.g. `startsWith("ingot")` + a suffix `HashSet`, then
prefix extraction by length) removes the regex entirely; the kind filter is already a set lookup.

### OPT-6 — cache comparators (and the owner order)

**Status: owner ranks done (batch 3).** `ConfigData` precomputes rank maps (global + per non-empty kind
override, first occurrence wins, case-insensitive kind key), so `OwnerOrder.compare`/`indexOf` are map
lookups instead of two `List.indexOf` scans per comparison (`ConfigDataTest` covers duplicate entries,
null mods, case-insensitive kinds and empty-override fallback). The comparator *instance* cache is still
in place and still captures a `ConfigData` (see BUG-12) — that part stays open.

`Util.itemStackComparatorByModName()` allocates a new lambda per `sort()`; a per-kind
`SpecificKindItemStackComparator` is cached but captures config (BUG-12). Cache one comparator per
`(kindName, ConfigData identity)`, or precompute `Map<String, Long>` owner ranks once per config load so
`compare` is two hash lookups instead of `List.indexOf` per comparison (currently each comparison
scans the owner list).

### OPT-7 — skip redundant re-runs

**Status: subsumed by OPT-1 (batch 3).** With the allocation-free canonical check, a re-run over
already-rewritten recipes walks the list but allocates nothing and reports a 0 delta, so the extra
`LOAD_COMPLETE` + server-start pass is cheap enough not to need a "recipes touched" journal.

Once OPT-1 lands, the `LOAD_COMPLETE` + `serverStarted` double passes become cheap for unchanged
recipes. Until then, consider a "recipes touched" journal to re-walk only the entries that were
rewritten, and skip the full `CraftingManager`/`IC2`/`TiC` walk when the previous pass's counts are
unchanged.

### OPT-8 — report rendering

`/unidict report` sends one `ChatComponentText` per line; on a GTNH pack that is 100–200 chat packets
and several hundred string allocations per invocation. Batch into pages (e.g. 8 lines per component
with `\n`) and add pagination (`/unidict report <resource>` already filters, but the unfiltered dump is
loud).

### OPT-9 — `CraftingIntegration` per-recipe warning spam

The "not a Shaped/Shapeless recipe — not rewritten" warning is emitted per recipe. In a GT pack this
can flood the log at every load. Aggregate into "N recipes of type X were not rewritable" (one line per
class) and include the count in the report.

---

## 3. Features / integrations not yet added

Ordered by value for the stated target pack (GTNH, 1.7.10).

### 3.1 GregTech 5 machine rewrites (highest-value missing integration)

GT is the dominant source of dusts/ingots/plates in GTNH, and it is currently **excluded** from the
dev classpath (`dependencies.gradle`, GC exclusion) and has **no integration at all** — GT machines
are only covered incidentally through the OD-based crafting/furnace rewrites. A `GTIntegration` that
rewrites `GT_Recipe.mOutputs` (macerator, centrifuge, ore washer, electrolyzer, chemical reactor,
alloy smelter, forming press, …) would dwarf every existing integration in effect. This deserves a
research spike first (GT recipe maps are per-machine classes and several outputs are `GT_MetaGenerated`
sub-items whose metadata encodes the material).

### 3.2 Other deferred feature work (already tracked, still missing)

| Feature | State | Notes |
|---|---|---|
| `keepOneEntry` | parsed, **not wired** | Needs a non-NEI, functional collapse (or a decision to drop the key). |
| `inputReplacement` | parsed, **no behaviour** | `max-compat` and the README advertise it as on; nothing reads `Config.inputReplacement()` outside the accessor. Needed so non-OD machine inputs stop producing non-canonical results. |
| `customUnifiedResources` | key accepted-but-ignored | User-defined equivalence classes; `UniResourceHandler` has no edge model. |
| BB-4 non-OD equivalence | not started | Fuels/coke is the motivating case (Railcraft + IE both emit `fuelCoke`); needs a fuel-value-aware classifier, not tag equality. |
| Fluid unification | not started | Forestry still/fermenter/squeezer, GC refinery, TiC melt/alloy. No fluid-equivalence model in 1.7.10. |
| Forestry crate *item* creation | deferred | Only recipes for crates that already exist are wired. |
| GC circuit fabricator / refinery | deferred | Compressor is done. |
| `/unidict reload` | deferred | Blocked on BUG-8/BUG-11. |
| `UniDictAPI` + helper surface | deferred | Only the minimal read surface exists. |
| Storage Drawers "Compact Drawer" UI canonical block | QoL | The registry seeding exists; the UI case is TODO P2 #13. |

### 3.3 Other mod integrations worth considering (1.7.10 / GTNH)

- **GregTech** (see above), **Railcraft coke oven** (non-metal but coke/coal outputs),
  **Thaumcraft** (crucible aspects are not items — low value), **Botania** (mana pool outputs),
  **MineFactory Reloaded**, **Extra Utilities** (compressed blocks), **Big Reactors** (ingots/blocks),
  **Factorization**, **Chisel** (blocks), **EnderCore**, **AE2 inscriber/press** (research said no
  cross-mod equivalents — skip), **IC2 crafting/compressor plates** (partly covered by crafting).
- Upstream's 1.12-era list (Mekanism, Magneticraft, Hydraulicraft, AbyssalCraft, Foundry, FSP,
  NuclearCraft, ElectricalAge) — only worth porting if a target pack needs it.

### 3.4 User-facing features not yet present

1. **`/unidict why <item>`** — a diagnostic for "why did/didn't this stack unify?" (the report is
   resource-centric today; the most common user question is item-centric).
2. **`/unidict list`** — configured metals, kinds, resolved owner order, and the *valid* kind names
   (mitigates BUG-3/BUG-18).
3. **JSON report export** (`/unidict report json` or a file in `logs/`) for pack authors / CI diffing.
4. **Config GUI** (`GuiFactory`) for presets and toggles — 1.7.10 supports it; a `.cfg` with 30 keys is
   unfriendly.
5. **Tooltip / NEI overlay** showing "unified → `main` entry" and the variant's owner priority.
6. **Localization** — there is no `src/main/resources/assets/unidict/` at all: the command strings and
   all user-facing text are hardcoded English, and `mcmod.info` has an empty `logoFile`. A lang file is
   cheap and expected for a released mod.
7. **Pagination and chat formatting** for `/unidict report` (see OPT-8).
8. **A "dry run" / preview mode** that logs what would be rewritten without mutating recipes (useful
   while adding the missing integrations above).
9. **Optional-dependency metadata** in `mcmod.info` (the integrations are optional, but pack tools get
   no hint).

### 3.5 CI / verification

- **No automated T3.** CI runs `test` + `build` only; the `[unidict-verify]` oracle needs a client
  launch. A headless `runClient`/`runServer` job (or a small "harness mod" integration test) that
  asserts `summary: N passed, 0 failed` would have caught BUG-1/BUG-6-class regressions. The
  `VerifyHarness` is already deterministic and greppable; it just needs a driver.
- **Open T3 gates** recorded in `TODO.md`/`INTEGRATIONS.md`: NEI `hidden=N`, Galacticraft compressor,
  StorageDrawers, TiC casting, and the docs sync (P0 #4). `STATUS.md` still has an unchecked
  `./gradlew build` checkbox from M4.

---

## 4. Test gaps (these are why the S1 bugs above are not caught)

| Missing test | Would catch |
|---|---|
| `UniResourceHandler` blacklist behaviour (fake containers) | BUG-1 |
| Kind-name contract: `ConfigReader`/`OwnerOrder` against the names `UniResourceHandler` actually registers | BUG-3 |
| NBT survival through `getMainItemStack` / chest / crafting | BUG-4 |
| `CraftingManagerMixin` gate (or an extracted decision helper with a config predicate) | BUG-5 |
| `getMainItemStack` identity/no-op semantics (counts, re-runs) | BUG-6 |
| `Util.getModName` edge cases | BUG-7 |
| `ResourceHandler` re-index / late-OD refresh | BUG-8 |
| `ModuleHandler` re-init semantics | BUG-11 |
| `CommandUniDict` (filter, usage, null handler) | reporting |
| `UnifyDrops` event adapter (`onEntityJoinWorld` guards) | drops |

The T1/T2 split is otherwise in good shape (160 tests, fake seams for every accessor, a determinism
guard that greps `src/main`), which makes adding these cheap.

---

## 5. Documentation / config drift (S3)

1. **README / `ForgeConfigIO` comment advertise `max-compat` as "input replacement + keep-one-entry"**
   — neither is implemented (`keepOneEntry` is deliberately still `false` in `maxCompat()`), so
   `max-compat` only differs from `standard` by `kindDebugMode` (which is dead, BUG-18).
2. **`docs/INTEGRATIONS.md` §Environment gotchas #3 says the heavy mods are `compileOnly`.** The actual
   `dependencies.gradle` has TE/TF/EnderIO/Forestry/Railcraft/Mantle/TiC at `devOnlyNonPublishable`,
   which is why `cofh-core` is on the runtime classpath (BUG-2). The `dependencies.gradle` comment
   block says the same thing, contradicting the lines that follow it.
3. **Furnace verify line** (`PASS integration=furnace …`) is documented but never emitted (BUG-15).
4. **Registry-name example mismatch:** docs/TODO/`ConfigData` use `"EtFuturum:block_copper"` in
   several places, but the real name is `etfuturum:copper_block` (verified against the
   `Et-Futurum-Requiem` sources: `BlockCopper("copper_block", …)`; the lowercase mod id is also what
   this project's own verify output reports, `owners=Forestry,IC2,TConstruct,ThermalFoundation,etfuturum`).
   `ConfigPresets.standard()` uses the correct `"etfuturum:copper_block"`. Anyone copying the doc
   value into `protectedItemNames`/`canonicalItemNames` gets a silent no-op. Pick one spelling and use
   it everywhere (and document that matching is a case-sensitive substring).
5. **Kind-name conventions are undocumented and inconsistent** (BUG-3): `ownerOfKind` examples are
   uppercase, `hideInNEIBlackSet` examples are lowercase.
6. **`README` mod table** does not mention the Tinkers' Construct casting, Storage Drawers, drop-time
   unification, or Galacticraft integrations (they are in `INTEGRATIONS.md`). The README's
   "Which metals?" list also omits the newer default metals (Tungsten/Uranium/Magnesium) added to
   `ConfigPresets.DEFAULT_METALS`.
7. **`TargetMods.THERMAL_EXPANSION`** sets `setCoreModClass("ThermalExpansion")`, which is a mod id,
   not a coremod class (TE's actual coremod is CoFH's `cofh.asm.LoadingPlugin`). Harmless because
   `setModId` is also set, but misleading.
8. **`STATUS.md` / `INTEGRATIONS.md` "T3 to confirm" notes** and the unchecked M4 build checkbox
   predate the last commits; sync at tag time (already TODO P0 #4).

---

## 6. Repo / build hygiene (S3)

- `.gitignore` ignores `src/main/resources/META-INF/` and the AT file (BUG-2) — a clean clone builds a
  different jar than a local working tree. Hidden, untracked shipped resources are a reproducibility
  hazard.
- Root-level `META-INF/` (with `cofh_at.cfg`, `gregtech_at.cfg`, `hodgepodge_at.cfg`, `MANIFEST.MF`)
  is unrelated leftover; delete.
- `build/libs/` currently holds two jars built from a *dirty* tree
  (`…-dirty-dev.jar` and `…-dirty.jar`); ensure release tagging builds from a clean tree.
- `mixins.unidict.json` (the manifest's host config) is empty (no `package`, no mixins) while the early
  and late configs carry the real bundles. That is the standard GTNHMixins pattern, but the file could
  document that it is the host so nobody "fixes" it.
- Consider linking the new docs from the README, which currently does not reference anything under
  `docs/` (`TODO.md`/`STATUS.md` cross-link each other).

---

## 7. Suggested order of work

> Items 1–3 and most of item 5 are done in batch 1 (§8). The list is kept as the original plan.

1. **BUG-2** (AT wiring) + **BUG-1** (blacklist) + **BUG-3** (kind casing) — three small changes that
   unblock a correct release and the documented TE/CoFH gate.
2. **OPT-1 / BUG-6** — one change in `getMainItemStack` that fixes reporting, removes allocations and
   makes re-runs true no-ops.
3. **BUG-4** (NBT) + **BUG-5** (crafting mixin gate) — data-safety and config-honesty.
4. **BUG-8** (re-index at LOAD_COMPLETE) — unlocks late mods, more accurate models, and the eventual
   `/unidict reload` (BUG-11).
5. **BUG-7**, **BUG-9**, **BUG-15**, **BUG-17**, **BUG-18** — robustness, reporting correctness, and
   diagnostics (BUG-18 doubles as the mitigation for BUG-3).
6. Docs sync (README kind names + registry-name spelling + preset claims) and the test gaps in §4.
7. Then the feature work: **GT5 integration** → `inputReplacement` → `customUnifiedResources` →
   BB-4 fuels → fluids → `/unidict reload` → the QoL/API items.

---

## 8. Implemented batches

### Batch 1

Scope chosen as "highest severity with the lowest regression risk": every change is a local decision
(no load-order changes, no recipe-enumeration changes, no mod-specific seams touched). Verification:
full `./gradlew build` green.

| Finding | Change | Test coverage |
|---|---|---|
| **BUG-3** (kind casing) | New pure `config/KindNames` (case-insensitive matching that preserves the registered spelling). `ConfigData.ownerOfKindFor` / `hasOwnerOverride` add a case-insensitive lookup over the unchanged public map; `OwnerOrder` and `UniResourceContainer` use it. `UniResourceHandler` matches `childrenOfMetals` ignoring case, registers the **OD-derived** kind spelling (so camelCase `dustTiny` is not lower-cased away) and derives the kind mask from what it actually registered. The NEI kind blacklist resolves configured → registered names ignoring case. | `KindNamesTest` (match/find/null/lookup key), `ConfigDataTest` (override lookup, legacy alias via `OwnerOrder`, child/hide lists, camelCase survival), `ConfigReaderTest` (updated + new `kindListsAcceptAnyCasing`), existing `OwnerOrderTest` (now also proves uppercase keys resolve) |
| **BUG-4 + BUG-6 + OPT-1** | New pure `CanonicalRules.shouldReplace` is the single replace/keep decision; `ResourceHandler.getMainItemStack` now returns the input **by identity** when it already is the canonical item+damage, and returns it unchanged when it carries NBT. This makes every integration's reference-based change check, all `RewriteJournal` counts and the server-start re-runs correct, and avoids a stack allocation per already-canonical resolution. `RewriteJournal.record` now **accumulates** per-pass deltas into one line (see the behaviour note below). | `CanonicalRulesTest` (no-op, tagged, different item, different damage, null items), `RewriteJournalTest` (accumulation, no duplicates, zero-count pass does not erase, defensive snapshot), `TinkersConstructIntegrationTest` journal test updated to the accumulate contract |
| **BUG-1** (`resourceBlackList`) | `UniResourceHandler.applyResourceBlackList` runs **before** the `ResourceHandler` indexes the model; `populateIndividualStackAttributes` also clears `containerMap` so a future re-index cannot keep stale containers. | `ResourceBlackListTest` (removed from both maps before construction; handler view + `containerExists`; unknown names / empty / null list are no-ops) |
| **BUG-5** (crafting gate) | `ConfigData.craftingUnificationEnabled()` (`craftingIntegration && integrationModuleEnabled`) is now the single source of truth for both `CraftingIntegration.runCraftingRewrite` and the `CraftingManagerMixin` result hook. | `ConfigDataTest.craftingGateRequiresBothSwitches` (all four combinations) |
| **BUG-7** (`getModName`) | `Util.getModName` returns `Util.UNKNOWN_MOD` for a null stack, an unregistered item or a name without a namespace; `VerifyHarness.describe` / `UniDictReport.describe` also guard a null registry name. | `UtilTest` (null stack and unregistered item — the exact inputs that used to throw NPE / `StringIndexOutOfBounds`) |
| **BUG-11** (module init) | `AbstractModule.initOnce()` with an explicit `initialized` flag; `ModuleHandler` calls it instead of using `isEmpty()` as the lazy-init sentinel. | `ModuleHandlerTest.moduleRegisteringNoThreadsIsInitialisedOnceNotPerStage` (fails against the old implementation) |

Result: **33 suites / 180 tests**, Checkstyle + Spotless clean.

### Batch 2

Same rule, tightened: **only changes whose decision logic is fully exercisable in the JVM** (no in-game
run was available). Verification: full `./gradlew build` green — **34 suites / 189 tests / 0 failures**,
Checkstyle + Spotless clean. Each new test was checked against the pre-fix code where it matters.

| Finding | Change | Test coverage |
|---|---|---|
| **BUG-17** (NEI) | `NEIHideThread` collects every container's canonical main entry once and passes a combined "always visible" predicate (protected **or** canonical elsewhere), so an item that is the main entry of another container is never hidden even when it is a non-main variant here. | `NEIHideThreadTest`: canonical-elsewhere exempt, ordinary variant still hidden (fix is not a blanket un-hide), and `isCanonicalElsewhere` matches item+damage (not instance identity), null/list-safe |
| **OPT-3** (perf) | `IdentityDamageIndex`'s inner damage map swapped from `HashMap<Integer,V>` to trove `TIntObjectHashMap<V>` — no boxing on the hot resolver path; behaviour identical (wildcard fallback and exact-precedence preserved). | existing `IdentityDamageIndexTest` suite (identity, damage keying, wildcard, exact-wins, clear, null handling) |
| **Latent: `maxCompat()` carry-over** | `ConfigPresets.maxCompat()` never copied `standard.protectedItemNames` (invisible today because both are empty, but `max-compat` would silently drop a future standard default). Extracted `maxCompatFrom(ConfigData)` so the carry-over contract is testable with a non-empty standard, and added the missing copy. | `ConfigPresetsTest.maxCompatFrom*` — verified to **fail** without the copy (`expected: <[mod:protected]> but was: <[]>`), plus a guard that the real `standard()`/`maxCompat()` shared collections stay equal |
| **BUG-19 (subset)** | Removed the now-dead `AbstractModule.isEmpty()` (the misleading lazy-init sentinel) and the stale `SpecificKindItemStackComparator.nullify()` comment in `UniDict.loadComplete`. No behaviour. | compiler + full suite |

Not included in batch 2 because they cannot be verified without an in-game run (kept in §7 and the
"still open" list): BUG-9, BUG-12, BUG-13, BUG-14, BUG-15, BUG-16, BUG-18, BUG-20 and BUG-8.

### Batch 3 — docs sync + verifiable optimizations

Verification: full `./gradlew build` green — **36 suites / 208 tests / 0 failures**, Checkstyle +
Spotless clean. The only behaviour changes here are performance/classification internals; the taxonomy
output is covered by a differential test against the code it replaced.

| Item | Change | Test coverage |
|---|---|---|
| **Docs sync** | README (preset claims, TiC/Storage Drawers/drop integrations, metal list, NEI canonical-entry note, report counts, developer-docs index), `STATUS.md` (+ re-encoded from mojibake to UTF-8, M4 build box, BB-3 note), `INTEGRATIONS.md` (HEAD note, delta-count semantics, furnace verify line, CoFH/dev-LIGHT gotchas), `TODO.md` (authoritative-backlog pointer, CoFH closed, docs-sync done), `PLAN.md` (HEAD update). | n/a (docs) |
| **OPT-5** | New pure `pure/OreNames` classifies the dictionary in two linear passes (ingot-prefixed discovery, longest-suffix kind grouping, excluded resources/kinds) instead of a giant alternation regex + `Matcher`/`replaceFirst` per name per pass; `UniResourceHandler.createResources` now delegates to it, and `UniOreDictionary.getOreNames()` supplies one ordered snapshot. | `OreNamesTest` (discovery/exclusions/dedupe/longest suffix/excluded kinds/null safety) + **`OreNamesLegacyEquivalenceTest`**, which reimplements the old regex pipeline as an oracle and asserts an identical resource/kind map on a realistic mixed fixture (documenting the two intentional edge-case differences). |
| **OPT-6** | `ConfigData` precomputes owner rank maps (global + per non-empty kind override, first occurrence wins, case-insensitive kind key); `OwnerOrder.indexOf/globalIndexOf` become map lookups instead of `List.indexOf` scans. | `ConfigDataTest` (first-occurrence wins, unlisted/null, per-kind override + case-insensitivity, empty-override fallback to global); existing `OwnerOrderTest` unchanged. |
| **OPT-1 remainder** | `UniResourceContainer` exposes package-private `getMainEntryItem()/getMainEntryDamage()`; `ResourceHandler.getMainItemStack` decides (via the already-tested `CanonicalRules`) without allocating, and only builds the canonical stack when it will replace the input. Canonical drops, crafting lookups and server-start re-runs now allocate nothing. | `CanonicalRulesTest` covers every branch of the decision; the two accessors are field reads on the same state `getMainEntry()` uses. |

**Still JVM-verifiable but not done:** OPT-2 (protection cache — needs a cache lifecycle tied to the
re-index/config, plus the protection rules extracted), OPT-4 (`getEntries()` view — needs the MC-bound
container, no JVM seam), OPT-8 (report batching — pure pagination is testable, but 1.7.10 chat rendering
of multi-line components cannot be verified without a client), OPT-9 (warning aggregation — the
collection lives in the MC-bound loop; only a trivial formatter would be testable).

### Batch 4 — headless T3 harness (+ the first item it verifies)

Verification: `./gradlew verifyServer` green — **271 stage-1 checks + 3 stage-2 checks, 0 failures,
~35 s**, on a dedicated server with no display; `./gradlew build` still green (208 tests).

| Item | Change | Verification |
|---|---|---|
| **T3 harness** | New `verifyServer` Gradle task: prepares `run/server/` (EULA + headless `server.properties`), boots the dev dedicated server with the verify switch and a new dev-only `unidict.verify.exitAfterStart` hook that stops the JVM after stage 2, captures the console to `build/unidict-verify-server.log`, then asserts the summaries, the report, every server-side `integration=` line and the `spikeA`/`resourceIndex` checks. RFG's console EULA/online-mode prompts are bypassed by a `prepareDevServer` dependency (it runs before RFG's own `doFirst`). | the task is the gate; **mutation-tested** — adding a bogus required line makes it fail with exit 1 and a named missing line |
| **BUG-15** | Furnace now emits `[unidict-verify] PASS integration=furnace rewritten=N`, closing the one gap in the verify-line contract. | asserted by `verifyServer` (and the required-integration list) |
| **Docs** | `TestPlan.md` gained the T3-automation section; `INTEGRATIONS.md` gotcha #2 records that RFG auto-detects the dev-only AT; this file and `STATUS.md` note the harness. | n/a |

Coverage of the harness: the resource model/index **including the post-remap `FMLModIdMappingEvent`
stage**, drops registration, crafting, furnace, IC2 (11 machines), AE2, Chest, TE, EIO, Railcraft,
Forestry, TiC, Galacticraft (server-start), the transparency report and the server-start re-runs.
Deliberately not covered: NEI's `API.hideItem` (client-only; its decision is still exercised via
`hidden=N`), client rendering/GUI/chat, and Immersive Engineering (compileOnly, not on the dev runtime
classpath).

### Batch 5 — the first batch behind the harness (bugs + CI)

Verification: `./gradlew test` green (214 tests) and `./gradlew verifyServer` green —
**273 stage-1 + 3 stage-2 checks, 0 failures, ~35 s**; the workflow `.github/workflows/verify-server.yml`
now runs the harness on every PR/push (plus manual dispatch).

| Item | Change | Verification |
|---|---|---|
| **BUG-14** | `Config.load` logs `[unidict] config preset=<name> ignoredKeys=[…]`; new pure `ConfigReader.describeIgnored` (sorted/de-duplicated). | `ConfigReaderTest.describeIgnoredRendersAStableSortedLine`; the harness asserts the log line |
| **BUG-18** | `kindDebugMode` logs registered/unknown kinds; the same diagnostic is always emitted as `kindDiagnostics` (new pure `KindNames.unknownKindNames` / `configuredKindNames` / `sorted`). | `KindNamesTest` (unknown/blank/null/dedupe/sorting/config gathering); the harness asserts `PASS kindDiagnostics` |
| **BUG-8 Phase B** | `ResourceHandler` carries the dictionary snapshot from build time; at load-complete the harness reports `lateOd added=N modelled=M total=T` (new pure `OreNames.additions` + `modelledNameCount`) and WARNs with samples. | `OreNamesTest.additions…` / `modelledNameCount…`; the harness asserts `PASS lateOd`. **Result: `added=373 modelled=26`** — Phase A's impact quantified |
| **BUG-13** | Removed Galacticraft's `RAN_PER_STAGE` guard; every run records its delta into the accumulating journal. | harness `integration=Galacticraft` line; `RewriteJournalTest` for accumulation |
| **BUG-20** | Accessor methods renamed to `unidict$getRecipeOutput()` / `unidict$setRecipeOutput()` so the mixins no longer shadow `IRecipe#getRecipeOutput()`. | clean `verifyServer` boot + all crafting rewrite lines; T2 fakes/crafting tests updated |
| **CI** | New `verify-server.yml` job (JDK 25, Gradle cache, `./gradlew verifyServer`, harness-log artifact). | the job *is* the harness; the same command passes locally |

### Behaviour changes to expect (all intended)

1. **`rewritten=N` counts drop and are now authoritative.** They count outputs that actually changed, not
   every recipe that touches a unified resource, and a later pass adds only what it newly rewrote. The
   historical figures quoted in `STATUS.md` / `INTEGRATIONS.md` (furnace 465, crafting 187/200, TE 253,
   IC2 69, …) are **not comparable** — treat the next run as a fresh baseline. A server-start pass with
   nothing new now reports `0` for that pass (and the journal keeps the earlier total).
2. **NBT-bearing stacks are no longer rewritten.** Tagged chest loot, recipe outputs and craft results
   keep their tag instead of being replaced by the bare canonical stack.
3. **`resourceBlackList` really excludes a resource** from unification (machines, drops, loot and the
   report now agree). With the default `Aluminium` entry, `ingotAluminium`-family outputs are no longer
   re-pointed at a priority variant.
4. **Kind configuration is case-insensitive.** `ownerOfKind.INGOT`, `childrenOfMetals=[Ingot, DustTiny]`,
   `ownerOfEveryIngot` and `autoHideInNEIBlackList=[Ore]` now work; camelCase kinds such as `dustTiny`
   keep their registered spelling.
5. **Crafting unification can actually be switched off** (`craftingIntegration=false` or
   `integration=false` stops the `findMatchingRecipe` result rewrite too).
6. **NEI keeps every canonical entry visible** (batch 2): a variant is no longer hidden just because it
   loses to another variant in one container — if it is the main entry of any container it stays.
   Expect the `integration=NEI hidden=N` count to be **lower** than before (possibly by a lot on packs
   with multi-tag items); that is the fix, not a regression.

### In-game check (optional, not required to trust the batch)

Everything above is covered by JVM tests. If you do run the pack, the useful smoke checks are: boot
with `-PunidictDevVerify` and confirm `[unidict-verify] summary: … 0 failed`; confirm a tagged item
(e.g. an enchanted book or NBT loot entry) passes through untouched; and compare the new
`report rewrite=…` numbers against a previous run knowing they are now deltas summed over passes.

### Still open (next batch candidates)

**Next up — BUG-8 Phase A** (build the model once at `LOAD_COMPLETE` instead of `POST_INIT`, moving the
POST_INIT integrations and the NEI hide pass with it). The harness is the gate: `lateOd added` should drop
to ~0 and `report summary=resources=` should grow by ~26 in this pack. It is a schedule change, so it gets
its own batch.

**Still needs a different seam:** BUG-9 (accessor-cast guards — boot success only; a missing mixin is
usually fatal earlier, so the payoff is small), BUG-12 (comparator cache lifecycle — needs a config-reload
seam the harness cannot drive), BUG-16 (EIO multi-output — EIO types are not on the test classpath and a
single boot cannot observe it).

**Pure/optimization candidates that can be fully unit-tested:** OPT-2 (protection cache — needs the
rules extracted plus a cache lifecycle tied to the re-index/config), OPT-4 (`getEntries()` view — no
JVM seam for the MC-bound container), OPT-8 (pagination helper is pure, chat rendering is not), OPT-9
(aggregation lives in the MC-bound loop). Done: OPT-1, OPT-3, OPT-5, OPT-6, OPT-7 (subsumed).

**Cleanup:** the rest of BUG-19 (unused `MetaItem`/`UniOreDictionary`/`ResourceHandler` surface).

---

## Appendix — how the findings were verified

```bash
./gradlew compileJava --offline          # green (Mixin AP, refmap written)
./gradlew test --offline                 # green: 29 suites, 160 tests, 0 failures
./gradlew dependencies --configuration runtimeClasspath --offline | grep cofh
unzip -p build/libs/*.jar META-INF/MANIFEST.MF        # no FMLAT
unzip -l build/libs/*.jar | grep -i "at.cfg"          # file shipped but unreferenced
git ls-files src/main/resources                       # META-INF/ untracked
grep -n accessTransformersFile gradle.properties      # empty
# EtF registry name cross-check (sources jar in the Gradle cache):
#   ganymedes01/etfuturum/blocks/BlockCopper.java -> "copper_block"; @Mod(modid = Tags.MOD_ID)
# Forge 1.7.10 API cross-checks (javap, Gradle cache):
#   ChestGenHooks has a public (String) ctor; WeightedRandomChestContent.theItemId -> field_76297_b
#   OreDictionary fields are List<ArrayList<ItemStack>> (erasure-compatible with the @Accessor
#   signatures); World.collidingBoundingBoxes is private (the AT target).
```

Items the review intentionally did **not** count as new bugs because they are already documented as
deferred in `TODO.md`/`PLAN.md`: `keepOneEntry`, `inputReplacement`, `customUnifiedResources`,
BB-4 fuels/fluids, the API surface, Forestry crate-item creation, and the `CoFH/TE` decision itself
(the finding above is that the repo *state* cannot ship the chosen fix).
