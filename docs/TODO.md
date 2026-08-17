# UniDict-Legacy — Remaining Work (TODO)

Living backlog of everything **not yet done** for an initial release, as of `feature-Implementation` @ `9b6305f` (2026-08-15). It replaces the old `docs/To do.txt`.

- Companion docs: [`STATUS.md`](STATUS.md) (per-milestone checkbox tracker) · [`INTEGRATIONS.md`](INTEGRATIONS.md) (implemented machine/loot integrations) · [`PLAN.md`](PLAN.md) (implementation plan + gates) · [`TestPlan.md`](TestPlan.md) (T1/T2/T3). This list is the **forward-looking** view: the items below are the remaining gaps, most of which are *not* "add another mod integration" (that surface is effectively complete).

## Quick status snapshot (verified against source at HEAD)

| Area                                                                                         | State                                            |
|----------------------------------------------------------------------------------------------|--------------------------------------------------|
| Crafting output rewrite (non-destructive)                                                    | DONE                                             |
| Vanilla Furnace / **AE2** / **IC2** (10) / **IE** (4) / **Chest** loot                       | DONE                                             |
| **EnderIO** / **Railcraft** / **Thermal Expansion**                                          | DONE (T3 verified 2026-08-14)                    |
| **Forestry** carpenter/squeezer/centrifuge + **crate-recipe wiring** + bronze-tool recycling | DONE                                             |
| **Galacticraft** compressor (non-destructive in-place outputs)                               | DONE (impl + tests; T3 to confirm on a full run) |
| **UnifyDrops** — drop-time canonicalisation                                                  | DONE (new, not in upstream)                      |
| Config presets (minimal / standard / max-compat)                                             | DONE                                             |
| `/unidict report` transparency + `RewriteJournal`                                            | DONE                                             |
| **NEI variant hiding** (`autoHideInNEI`, kind + mod + protected OD names)                  | DONE (impl + T1/T2; T3 `hidden=N` to confirm)    |
| **Storage Drawers** compacting-drawer compat (seeds the canonical block/ingot/nugget chains) | DONE (impl + T1/T2; T3 to confirm) |

**Everything below this line is the remaining scope.** Prioritisation is P0 → P2; "dropped" items are deliberately out of scope.

---

## Legend

- **T1 / T2 / T3** mean the verification tiers from `TestPlan.md` (JVM unit / seam+fake / in-game `[unidict-verify]`).
- **Effort**: S / M / L (small / medium / large). **Risk**: how likely it is to reintroduce a crash or a stale/incorrect rewrite.

---

## P0 — Release-blocking (do before an initial release)

These ship the other half of the mod's promise ("unify the *whole* view", not just machine outputs) and de-risk the release build itself.

### 1. NEI variant hiding (`autoHideInNEI`) — DONE (2026-08-15)
- **What:** hide non-`main` variants of a unified resource in NEI so the player sees one copper ingot/plate/etc. instead of seven. This is the most visible "unify" feature a player notices.
- **Current state:** **WIRED.** `nei.NEIHideThread` walks every resource at POST_INIT (client + NEI-present gated in `UniDict.preInit`) and calls the single guarded `NEIHelper.hide` for each non-kept variant. Hiding is driven **only** by `autoHideInNEI`, with **three exemptions**: `hideInNEIBlackSet` (per kind), `autoHideInNEIModBlackList` (per owner mod; legacy `keepOneEntryModBlackList` maps to it), and `protectedOreDictionaryNames` (default `"raw"`). `SelectionRules.hiddenIndices` is the pure hide-set builder; T1 tests cover the rule, the T2 seam `NEIHideThread.stacksToHide` covers the hide-set builder fed by `ItemStack` fakes, and `VerifyHarness` now emits `hidden=N`. The `NEIHelper` main-thread guard stays. (`keepOneEntry` is **deferred** — see #2.)
- **Raw metals / EtF:** `ResourceHandler#getMainItemStack` returns protected items unchanged (the default `"raw"` OD-name substring matches `rawCopper`/…), so a mined EtF raw-copper drop **stays raw** instead of morphing into a mod's copper ore block, and it stays visible in NEI alongside the canonical block. Pair with EtF's `disableCopperOreAndIngotOnly` so TF is the sole canonical copper ore/ingot in world and only the raw form is carved out.
- **`ore` kind collapses by default now:** `autoHideInNEIBlackList` no longer defaults to `["ore"]` (it is empty in the standard preset), so non-canonical ore variants are auto-hidden too. Protected raw metals (above) still stay visible, and EtF's `disableCopperOreAndIngotOnly` removes EtF's copper ore block so TF's is the canonical ore shown.
- **Decorative copper blocks (EtF `blockCopper`):** EtF and TF share the `blockCopper` tag *and* both register a same-pattern 9-ingot → block recipe, so vanilla crafting picks one and you can't choose. Two item-level knobs (matched on qualified `modid:path` names, substring covers aged/oxidized variants):
  1. **`protectedItemNames`** — keep a variant craftable/visible without making it canonical (for "coexist with the storage block" cases).
  2. **`canonicalItemNames`** — make a variant the canonical (main) entry of its container, which **resolves the craft conflict**: `"etfuturum:copper_block"` is the **standard/max-compat default** (EtF's block becomes the one copper block, so TF's colliding recipe output is rewritten to it too and only one block is craftable; it's a no-op when EtF is absent since matching is against live registry names).
- **Work (was):** after resource selection, walk variants and call `NEIHelper.hide(stack)` for every non-main, non-blacklisted variant; drive with `SelectionRules.shouldHideNonMain`; honour `hideInNEIBlackSet`. Keep `keepOneEntry == true → hide all non-main` semantics (see #2).
- **Verify:** T1 on the "which to hide" rule; T2 on the hide decision fed by fakes; T3 `[unidict-verify] … hidden=N` line.
- **Risks/notes:** `API.hideItem` must run on the client main thread (the historical crash was it running on worker threads — the `NEIHelper` guard already enforces this under the dev verify switch). Confirm it coexists with which NEI version the pack ships. T3 on the live GTNH pack still to do.

### 2. `keepOneEntry` (strict one-entry collapse) — DEFERRED (stretch goal, 2026-08-15)
- **What:** upstream's strict collapse — surface only the canonical entry (+ `keepOneEntryModBlackSet` survivors) across the *whole* game view.
- **Current state:** **DEFERRED / not wired.** The config key (`autoHideInNEIModBlackList` is the canonical per-mod NEI-hide exemption; legacy `keepOneEntryModBlackList` maps to it) is still parsed for back-compat. But `keepOneEntry` itself no longer drives anything at runtime.
- **Why deferred:** in this fork, the only safe way to make "the player sees one entry" observable is NEI hiding — which `autoHideInNEI` already does with the same two blacklists. Keeping a second switch that maps to the identical NEI mechanism is redundant (the two previously overlapped). We will design a **safer / more efficient** implementation later if a genuinely different (e.g. functional, non-NEI) collapse is wanted.
- **Risks/notes:** for the initial release this is **deliberately omitted**; the previous `removeFromElsewhere` / live-OD mutation crash is permanently out of scope (see "Dropped").

### 3. CoFH Core / TE runtime crash (release gate)
- **What:** a **full-stack** run (TE + CoFH Core at runtime) crashes on the first item-entity tick: `IllegalAccessError: tried to access field World.collidingBoundingBoxes from cofh.asmhooks.HooksCore`. Not a UniDict bug — CoFH Core ASM needs `World.collidingBoundingBoxes` widened, which in working packs GregTech happened to do.
- **Current state:** documented in `INTEGRATIONS.md §Environment gotchas #2` and **parked**. The pack runs without TE/CoFH, so dev utilizes a "dev-LIGHT" classpath (heavy mods are `compileOnly`).
- **Work:** either widen the field via a UniDict access-transformer whose only entry is an MC class (the `applyJST`-legal route), or document it as a known limitation for any pack that includes TE. Choose one before tagging a release.
- **Verify:** a full-`devOnly` `runClient` that boots without the `IllegalAccessError`.
- **Risks/notes:** this is the single real blocker for "release build with TE". Revisit whatever the target pack actually ships.

### 4. Docs ↔ HEAD sync (release-notes correctness)
- **What:** bring every doc checkbox/at-a-glance in line with HEAD before tagging so release notes are accurate.
- **Current state:** `STATUS.md`/`INTEGRATIONS.md`/`PLAN.md` lag the landed commits (GC compressor, Forestry crates+recycling, `UnifyDrops`, and the 2026-08-14 EIO/Railcraft/TE T3 regression). See the HEAD-status notes added in each file.
- **Verify:** grep the docs for "pending"/"deferred" against the actual code once more at tag time.

---

## P1 — High-value after P0

### 5. `inputReplacement` — machine input rewrite
- **What:** upstream's "Input Replacement" (2.9.1): replace non-standard *inputs* on machines that don't otherwise go through the OreDictionary, so a non-canonical ingot no longer crafts a non-canonical result.
- **Current state:** `ConfigData.inputReplacement` and `Config.inputReplacement()` exist (default on only in `max-compat`), but **the behavior is not ported** — `integration/FurnaceIntegration.java` states the branch "removed recipes and is NOT ported (craft-rewrite territory, deferred)".
- **Work:** implement as a *non-destructive* input-mapping rewrite (rewrite input stacks in place via the same `ResourceHandler.getMainItemStack` lane), never as recipe removal.
- **Verify:** T2 per machine seam that reads inputs; T3.
- **Risks/notes:** M; input-field rewrite touches every kept machine's input surface; go slow, one machine at a time.

### 6. `customUnifiedResources`
- **What:** user-defined equivalence classes (`Obsidian:dustTiny|dust`) so resources upstream didn't model can still be unified.
- **Current state:** explicitly deferred (`resource/UniResourceHandler.java` comment); `ConfigReader` accepts the legacy key but nothing stores/applies it.
- **Work:** parse into the pure model, register the extra equivalence edges, feed the selection pipeline; keep BB‑3 non-destructive.
- **Verify:** T1 (parse), T2 (equivalence resolve), T3.
- **Risks/notes:** S/M; the edge-case space (which kind wins, blocks of gems) is why upstream shipped it "may break some recipes" — keep it opt-in behind the config.

### 7. BB‑4 — non-OD equivalence class (fuels / coal coke)
- **What:** the pack genuinely wants a **single stackable coal-coke** (Railcraft + IE both emit `fuelCoke`). First non-OreDictionary equivalence class.
- **Current state:** deferred/prioritized as the next build-better milestone. Engine is already resource-agnostic (`getMainItemStack`).
- **Work:** (1) a new kind in the M1 taxonomy (within the 64-kind guard); (2) a config surface / "fuels" preset toggle; (3) a **fuel-equivalence classifier** that preserves per-item burn time and exact-item checks — *not* naive OreDictionary tag-equality (metals are near-interchangeable, fuels are not).
- **Verify:** confirm in the live GTNH pack that Railcraft/IE coke actually share a tag and what each fuel value is; add a verify/report line + T3.
- **Risks/notes:** M; gate is "≥1 non-OD equivalence class implemented, tested, reported".

---

## P2 — Nice-to-have (not required for the initial release)

### 8. Reload / re-run module
- **What:** a `/unidict` reload so unification can be re-invoked without a restart (most rewrites are load-time one-shots).
- **Current state:** deferred (`docs/PLAN.md` infrastructure list; only `/unidict report` exists).
- **Risks/notes:** S/L; the value is low for a one-shot mod, and re-running machine rewrites mid-session risks double-applying. Hold unless a clear need appears.

### 9. `UniDictAPI` + helper surface (`Forestry` / `Furnace` / `IE` / `TCon`)
- **What:** the upstream mod-dev API and helper classes so other mods can query/append to the unified model.
- **Current state:** deferred; only the minimal read surface (e.g. `ResourceHandler.getMainItemStack`) that kept integrations use.
- **Risks/notes:** only worth it if there is an external audience; internal/jar-versioned use is better served by the kept package API.

### 10. Forestry fluid outputs
- **What:** unify squeezer/fermenter/still fluid outputs.
- **Current state:** deferred — 1.7.10 has no fluid-equivalence/OreDictionary-style model (BB‑4 territory).
- **Risks/notes:** M; requires deciding a fluid "canonical" semantics for the version.

### 11. Galacticraft non-compressor machines
- **What:** circuit fabricator (GC waivers on which ore template to rewrite) and refinery/oil (fluid outputs).
- **Current state:** deferred (only the Ingot / Electric-Ingot compressor is wired).
- **Risks/notes:** S–M per machine; mostly waiting on the fluid decision (#10) for the refinery.

### 12. Optional/integration backlog (non-kept upstream mods)
- **What:** upstream integrations this fork deliberately did not keep, in rough priority if the target pack ever needs them: **Mekanism**, **Magneticraft**, **Hydraulicraft**, **AbyssalCraft**, **Foundry**, **FSP**, **NuclearCraft**, **ElectricalAge**, plus any 1.12.2-era upstream additions worth a 1.7.10 port — each is a new module, not "core feature" work.
- **Notes:** none are needed by the current pack; do not start without re-reading the scope-rework notice in `PLAN.md`.

---

## Dropped — deliberately out of scope (do not re-introduce)

- **`removeFromElsewhere` / global OreDictionary mutation / live-list entry removal** — the historical crash source (`removeBadEntriesFromNEI` on worker threads, global OD mutation). NEI variant hiding (`autoHideInNEI`, P0 #1) is the <b>only</b> "surface one entry per resource" mechanism — never by mutating Forge's source of truth. A strict `keepOneEntry` collapse remains a deferred stretch goal (P0 #2).
- **Physical removal of non-canonical recipes** from any manager (upstream's craft rewrite deleted other mods' recipes) — replaced by the BB‑3 non-destructive in-place output rewrite.
- **Making the mod "faithful" to upstream's removed integrations** — the 2026-08-12 rework is a deliberate rework, not a port; keep the better-code direction.

---

## Suggested order of work

1. **P0 #4** (sync docs) + **P0 #3** (CoFH/TE release decision) — unlock a clean full-stack `runClient`.
2. **P0 #1** (NEI variant hiding via `autoHideInNEI`, both blacklists) — T1/T2 done, T3 on a live pack; `keepOneEntry` deferred as a stretch goal. One PR.
3. Tag the initial release with P1–P2 items tracked here.
4. Then **P1** in order 5 → 6 → 7 (input rewrite → custom resources → fuels).
