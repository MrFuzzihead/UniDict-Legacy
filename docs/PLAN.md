# Implementation Plan: Reimplementing UniDict as a Mixin-Based Mod

## Scope confirmation
- **Keep & port:** Crafting, Chest, Furnace, AE2, EnderIO, Forestry, IC2, IE, Railcraft, Thermal Expansion + API helpers (ForestryUniHelper, FurnaceUniHelper, IEUniHelper, TConUniHelper).
- **Drop:** AbyssalCraft, Foundry (+FoundryUniHelper), FSP, Hydraulicraft, Magneticraft, Mekanism, NuclearCraft.
- **Defer:** Galacticraft (stub note only); Forestry crate system (port as-is, flag for future rework).

---

## Phase 0 — Build infrastructure (Mixins on)

**Files:** `gradle.properties`, `dependencies.gradle`, new `src/main/resources/mixins.UniDict.json`

1. `gradle.properties`: set `usesMixins = true`, `mixinsPackage = wanion.unidict.mixin`, `usesMixinDebug = true` (dev), leave `separateMixinSourceSet` empty (mixin classes live alongside main; simpler given the accessors need main-package types).
2. `dependencies.gradle`: drop the dropped mods' `compileOnly` lines (AbyssalCraft, Foundry, FSP, HydCraft, Magneticraft, Mekanism, NuclearCraft, k4lib). Keep: NEI, CodeChickenCore, Mantle, Railcraft, TConstruct, Thermal Expansion/Foundation, AE2, IC2, Forestry, EnderCore/EnderIO, IE, Galacticraft (for the future stub). Add UniMixins implicitly via `usesMixins`.
3. Create `mixins.UniDict.json` with `"refmap": "mixins.UniDict.refmap.json"`, `"minVersion": "0.8"`, `"package": "wanion.unidict.mixin"`, empty `"mixins": []` initially (populate as mixins are added).

---

## Phase 1 — Core resource model (direct ports + fixes)

**Files:** `MetaItem`, `Config`, `resource/Resource`, `resource/UniAttributes`, `resource/UniResourceContainer`, `resource/ResourceHandler`, `resource/UniResourceHandler`

4. **Port `MetaItem`** unchanged (core `item+meta -> int` hashing). It uses `GameData.getItemRegistry()` which is public API — no Mixin needed.
5. **Port `Config`** preserving every user-facing config key (compat) but clean up the static-init pattern. Keep: `keepOneEntry`, `inputReplacement`, `keepOneEntryModBlackSet`, `autoHideInNEI`, `hideInNEIBlackSet`, `kindDebugMode`, `enableSpecificKindSort`, `ownerOfEveryThing`, `metalsToUnify`, `childrenOfMetals`, `resourceBlackList`, `customUnifiedResources`, per-kind owner maps, and the kept integration toggles (drop toggles for the 7 removed mods).
6. **Port `Resource`** unchanged (bitfield kind taxonomy). Note the 64-kind limit in a comment.
7. **Port `UniAttributes`** unchanged (package-private value pair).
8. **Port `UniResourceContainer`** with one fix: `removeBadEntriesFromNEI()` must **not** be called from a parallel stream. It stays as a method but is only invoked on the main thread (see step 11).
9. **Port `ResourceHandler`** unchanged (public read API + `populateIndividualStackAttributes`).
10. **Port `UniResourceHandler`** with two fixes:
    - `createResources()`: replace `parallelStream().parallel().forEach(...)` with a plain sequential `forEach` — the `^ingot` set is tiny; parallelism is pure overhead.
    - `postInit()`: replace `apiResourceMap.values().parallelStream().forEach(Resource::updateEntries)` with **sequential** `forEach`. This is the NEI-crash root cause: `updateEntries` -> `removeBadEntriesFromNEI` -> `API.hideItem` was running on fork-join pool threads. Now all on main thread.

---

## Phase 2 — UniOreDictionary -> Mixin (the big reflection win)

**Files:** new `mixin/OreDictionaryMixin.java`, rewritten `UniOreDictionary.java`, delete `common/Util.getField/setField` usages here.

11. **`OreDictionaryMixin`** (`@Mixin(OreDictionary.class)`) with `@Accessor` getters for the 5 private static fields: `nameToId` (`Map<String, Integer>`), `idToName` (`List<String>`), `idToStack` (`List<List<ItemStack>>`), `idToStackUn` (`List<List<ItemStack>>`), `stackToId` (`Map<Integer, List<Integer>>`). Since these are `static` fields, use `@Accessor` on an interface-style mixin with `@At("FIELD")` + `INVOKE_STATIC` remap, or a `@Mixin` with `@Accessor` methods that SpongePowered Mixin supports for static fields via `remap = false`. (Sponge supports `@Accessor` on static fields — confirm at implementation; fallback is a tiny `@Inject` accessor.)
12. **Rewrite `UniOreDictionary`** to obtain the 5 maps via the mixin accessors instead of `Util.getField`. All public methods (`get`, `getUn`, `getId`, `getThoseThatMatches`, `removeFromElsewhere`, `getFirstEntry`, etc.) keep their signatures for API compat. `removeFromElsewhere` keeps using `OreDictionary.getOreIDs` (public) + direct list manipulation via the accessor.
13. Remove `Util.getField`/`setField` calls from `UniOreDictionary` (the only reflection left in core).

---

## Phase 3 — Recipe layer (port + key rework)

**Files:** `recipe/IRecipeResearcher`, `recipe/VanillaRecipeResearcher`, `recipe/ForgeRecipeResearcher`, `recipe/IC2RecipeResearcher`, `integration/CraftingIntegration`, `helper/RecipeHelper`

14. **Port `IRecipeResearcher`** interface unchanged.
15. **Port `VanillaRecipeResearcher`** unchanged.
16. **Port `ForgeRecipeResearcher`** — keep the Forestry `ShapedRecipeCustom` `Class.forName` lookup but guard it behind `Loader.isModLoaded("Forestry")` (already there). Consider replacing with a direct import since Forestry is now a kept `compileOnly` dep — **decide: direct import** (cleaner, compile-time-checked).
17. **Port `IC2RecipeResearcher`** unchanged (IC2 is a kept dep).
18. **Rework recipe key generation** in all three researchers: replace the sum-of-`MetaItem`-hashes key (collision-prone) with a **structured key**: a sorted `TIntList` of main-entry ids joined with the recipe shape signature (width/height + a normalized grid pattern). This eliminates the false dedup merges that the commit history shows were repeatedly patched. Keep the `getShapedRecipeKey`/`getShapelessRecipeKey` method signatures.
19. **Port `CraftingIntegration`** logic unchanged (group -> sort -> keep best -> rewrite), but it now runs **sequentially** (see Phase 5). The `RecipeComparator` inner class stays.
20. **Audit `RecipeHelper`**: `singleWayCompressionRecipe` / `resourcesToCompressionRecipes` / `createCompressionRecipe` — verify callers. If unused, delete (trim). Keep `rawShapeToShape` (used by researchers).

---

## Phase 4 — Per-mod integrations (port + Mixin-ify reflection)

Each integration becomes a plain class implementing a `Runnable`/`Callable` (no thread pool — see Phase 5). Reflection points get Mixin replacements.

21. **`AE2Integration`** — pure public-API (`AEApi.instance().registries().grinder()`), no reflection. Direct port.
22. **`EnderIOIntegration`** — replace `Util.getField(OreDictionaryPreferences, "preferences", ...)` with a Mixin: new `mixin/EnderIO/OreDictionaryPreferencesMixin.java` (`@Mixin(OreDictionaryPreferences.class)`) `@Accessor` for the `preferences` map. Port the rest (alloy smelter + SAG mill) unchanged. Drop `FixedSizeList` usage if it's trivially replaceable with `ArrayList` (audit `FixedSizeList` — likely safe to inline).
23. **`ForestryIntegration`** — replace `Util.getField(CarpenterRecipeManager, "recipes", ...)` with: new `mixin/Forestry/CarpenterRecipeManagerMixin.java` `@Accessor` for the `recipes` set. **Note (deferred):** the crate system (`createCratesDefault` + `ForestryUniHelper.registerCratesAndCreateRecipes` registers new `ItemCrated` items at runtime) is a future rework candidate — port as-is for now, add a `// TODO: rework crate registration` comment.
24. **`IC2Integration`** — pure public-API (`Recipes.centrifuge` etc.), no reflection. Direct port.
25. **`IEIntegration`** — uses public static lists (`ArcFurnaceRecipe.recipeList`, etc.) + `UniOreDictionary.getFirstEntry`. The `UniCrusherRecipe` inner subclass accesses `protected` fields via `super.` — that's fine (subclass access), no Mixin needed. Direct port.
26. **`RailcraftIntegration`** — replace `Util.getField(BlastFurnaceCraftingManager, "recipes", instance, ...)` with: new `mixin/Railcraft/BlastFurnaceCraftingManagerMixin.java` `@Accessor` for the `recipes` list (instance field, so `@Accessor` on the instance). Port the rest unchanged.
27. **`TEIntegration`** — the heaviest reflection. Three private-constructor calls + three `recipeMap` field reads. Replace with: new `mixin/ThermalExpansion/FurnaceManagerMixin.java` (`@Accessor` for `recipeMap` + `@Invoker` for the private `RecipeFurnace` constructor); new `mixin/ThermalExpansion/PulverizerManagerMixin.java` (`@Accessor` for `recipeMap` + `@Invoker` for the private `RecipePulverizer` constructor); new `mixin/ThermalExpansion/SmelterManagerMixin.java` (`@Accessor` for `recipeMap` + `@Invoker` for the private `RecipeSmelter` constructor). `@Invoker` on private constructors is supported by SpongePowered Mixin — this is the cleanest AT-free path. **If `@Invoker` can't target a constructor in this Mixin version**, fallback: one AT entry per constructor (flag for review). Keep the `@SpecifiedLoadStage(LOAD_COMPLETE)` annotation.
28. **`ChestIntegration`** — replace `Util.getField(ChestGenHooks, "chestInfo"/"contents", ...)` with: new `mixin/ChestGenHooksMixin.java` — `@Accessor` for `chestInfo` (static) and `contents` (instance). Port the loot replacement logic unchanged.
29. **`FurnaceIntegration`** — uses public `FurnaceRecipes.smelting().getSmeltingList()` (public API). Direct port, no Mixin.

---

## Phase 5 — Module/infra rework (kill the thread pool + DI)

**Files:** `UniDict`, `module/ModuleHandler`, `module/AbstractModule`, `module/AbstractModuleThread`, `module/SpecifiedLoadStage`, `LoadStage`, `integration/IntegrationModule`, `common/Dependencies`, `common/Instantiator`, `common/FixedSizeList`, `common/Util`, `common/SpecificKindItemStackComparator`

30. **Kill the `ExecutorService` thread pool** in `AbstractModule.start`: replace `Executors.newFixedThreadPool(...).invokeAll(threadList)` with a sequential `for` loop calling each `AbstractModuleThread.call()`. Rationale: integrations mutate shared global state (CraftingManager recipe list, OreDictionary lists, each mod's recipe managers) — the thread pool was racing. Sequential is correct and the perf cost is negligible (these are fast in-memory passes). Log the total time as before.
31. **`AbstractModuleThread`**: keep as `Callable<String>` (return the log line) or simplify to `Runnable` + name field. Keep `Callable` for minimal diff.
32. **`IntegrationModule`**: drop the 7 removed integrations from `init()`. Keep the 10 kept ones. Keep the `Class::newInstance` instantiator (or switch to explicit `new` per integration — cleaner; **decide: explicit `new`**).
33. **`UniDict`**: replace the ASM-data `@Module` annotation discovery (`searchForModules`) with explicit `moduleHandler.addModule(new IntegrationModule())` only. Delete the `@Module` annotation + `searchForModules` method. Keep `@Mod`, `NetworkCheckHandler`, lifecycle handlers.
34. **`Dependencies`/`DependenceWatcher`/`Instantiator`**: simplify. The DI container exists only to lazy-init `UniDictAPI` and `ResourceHandler`. Replace with direct instantiation in `UniResourceHandler` + static accessors on `UniDict`. Delete `Dependencies`, `DependenceWatcher`, `Instantiator`. Update `UniDict.getDependencies()`/`getAPI()`/`getResourceHandler()` accordingly.
35. **`FixedSizeList`**: audit; if only used by EnderIO/Railcraft/IE for pre-sized recipe lists, replace with `ArrayList` (capacity hint) and delete the class.
36. **`Util`**: after removing `getField`/`setField` callers (all moved to Mixins), `Util` keeps only `getModName` + `itemStackComparatorByModName`. Delete `getField`/`setField`.
37. **`SpecificKindItemStackComparator`**: port unchanged (static comparator cache). Keep `nullify()` at LoadComplete.
38. **`LoadStage` / `SpecifiedLoadStage`**: port unchanged.

---

## Phase 6 — API surface (stable, unchanged)

**Files:** `api/UniDictAPI`, `api/helper/ForestryUniHelper`, `api/helper/FurnaceUniHelper`, `api/helper/IEUniHelper`, `api/helper/TConUniHelper`

39. **Port `UniDictAPI`** unchanged (public read API).
40. **Port `ForestryUniHelper`** — replace its `Util.getField(CarpenterRecipeManager, "recipes", ...)` with the same Mixin accessor used in `ForestryIntegration`. Keep the crate-registration logic (deferred rework).
41. **Port `FurnaceUniHelper`** unchanged (uses public `FurnaceRecipes.smelting().getSmeltingList()`).
42. **Port `IEUniHelper`** unchanged (uses `MetalPressRecipe` public API + `NEIHelper`).
43. **Port `TConUniHelper`** unchanged (uses `TConstructRegistry` public API + `NEIHelper`).
44. **`NEIHelper`** — keep as the single NEI call site (`API.hideItem`). It's now only ever called on the main thread (Phase 1 fix). Consider adding a guard/assert that it's called from the main thread in dev.

---

## Phase 7 — Cleanup & verification

45. Delete: `AbyssalCraftIntegration`, `FoundryIntegration`, `FSPIntegration`, `HydraulicraftIntegration`, `MagneticraftIntegration`, `MekanismIntegration`, `NuclearCraftIntegration`, `GalacticraftIntegration` (replace with a stub `// TODO: Galacticraft integration` note in `IntegrationModule`), `FoundryUniHelper`, `Dependencies`, `DependenceWatcher`, `Instantiator`, `FixedSizeList` (if audited out), `Util.getField/setField`.
46. Update `mcmod.info` description if desired.
47. `./gradlew build` — confirm compiles, Spotless/Checkstyle pass.
48. `./gradlew runClient` with the dev dependency set — verify: no NEI crash (the parallel-stream fix); recipe unification output looks correct (check a few ore types in NEI/JEI); log shows sequential integration timing; each kept mod's recipes are unified (spot-check AE2 grinder, IC2 macerator, TE pulverizer, IE crusher, Railcraft blast furnace, EIO SAG mill, Forestry carpenter).

---

## Mixin summary table (one row per Mixin)

| Mixin class | Target | Accessor/Invoker | Replaces |
|-------------|--------|------------------|----------|
| `OreDictionaryMixin` | `OreDictionary` | `@Accessor` x5 (static) | `UniOreDictionary` reflection |
| `ChestGenHooksMixin` | `ChestGenHooks` | `@Accessor` x2 | `ChestIntegration` reflection |
| `CarpenterRecipeManagerMixin` | `CarpenterRecipeManager` | `@Accessor` x1 | `ForestryIntegration` + `ForestryUniHelper` reflection |
| `OreDictionaryPreferencesMixin` | `OreDictionaryPreferences` | `@Accessor` x1 | `EnderIOIntegration` reflection |
| `BlastFurnaceCraftingManagerMixin` | `BlastFurnaceCraftingManager` | `@Accessor` x1 | `RailcraftIntegration` reflection |
| `FurnaceManagerMixin` | `FurnaceManager` | `@Accessor` x1 + `@Invoker` x1 | `TEIntegration` reflection |
| `PulverizerManagerMixin` | `PulverizerManager` | `@Accessor` x1 + `@Invoker` x1 | `TEIntegration` reflection |
| `SmelterManagerMixin` | `SmelterManager` | `@Accessor` x1 + `@Invoker` x1 | `TEIntegration` reflection |

**AT fallback note:** if `@Invoker` can't target a private constructor in the UniMixins/Sponge version in use, the three TE constructors fall back to 3 AT entries (`PulverizerManager$RecipePulverizer`, `SmelterManager$RecipeSmelter`, `FurnaceManager$RecipeFurnace` ctors). Flag for review at Phase 4 step 27.

---

## Key risks / things to watch

- **`@Accessor` on static fields**: confirm SpongePowered Mixin in the GTNH/UniMixins toolchain supports `@Accessor` for `static` fields (it should; if not, the 5 OreDictionary fields need `@Inject`-based accessors). This is the single biggest "could go wrong" item.
- **`@Invoker` on constructors**: same — confirm before committing to it for TE. AT is the documented fallback.
- **Recipe key rework** (Phase 3 step 18) changes dedup behavior — verify with spot-checks that we don't over- or under-merge compared to the old sum-of-hashes.
- **Forestry crate runtime item registration** is left as-is but is the most fragile kept feature; flagged for future rework.

---

## Deferred items (not implemented now)

- **Galacticraft integration** — stub note only; real implementation to be added later.
- **Forestry crate system** — ported as-is; the runtime `ItemCrated` registration is a future rework candidate (flagged `// TODO: rework crate registration`).