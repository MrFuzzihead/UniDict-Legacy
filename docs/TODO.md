# UniDict-Legacy — Remaining Work (TODO)

Living backlog of everything **not yet done** for an initial release, as of `feature-Refactor` @ `a839a60` (2026-10-25). It replaces the old `docs/To do.txt`.

> **Status update (2026-10-25):** the **bug backlog is empty** — all findings from [`CODE_REVIEW.md`](CODE_REVIEW.md)
> are fixed across batches 1–8 (the review file keeps the severity-ranked history). Verification is now
> automated: `.github/workflows/verify-server.yml` runs `./gradlew verifyServer` (298 stage-1 + 5 stage-2
> checks, 0 failures) on every PR/push, and a production-pack pair of logs (single-player client +
> dedicated server) confirms the same: **136 unified resources, 0 FAIL, 17/17 seam checks**, with only
> **5 late Railcraft plate entries** (`plateCopper/Lead/Iron/Steel/Tin`) not in the model — they already
> unify at runtime via the OD fallback and only show as NEI duplicates (see the *NEI live-entries pass* below).
> - **Decision:** the CoFH/TE field widening is provided by the production pack — the access transformer
>   is dev-only tooling and is **not** shipped, so item #3 below is closed, not a blocker.

- Companion docs: [`STATUS.md`](STATUS.md) (per-milestone checkbox tracker) · [`INTEGRATIONS.md`](INTEGRATIONS.md) (implemented machine/loot integrations) · [`PLAN.md`](PLAN.md) (implementation plan + gates) · [`TestPlan.md`](TestPlan.md) (T1/T2/T3) · [`CODE_REVIEW.md`](CODE_REVIEW.md) (review findings + fix status). This list is the **forward-looking** view: the items below are the remaining gaps, most of which are *not* "add another mod integration" (that surface is effectively complete).

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
| **NEI variant hiding** (`autoHideInNEI`, kind + mod + protected OD names)                  | DONE (impl + T1/T2; canonical entries are never hidden; T3 `hidden=N` still to confirm) |
| **Storage Drawers** compacting-drawer compat (seeds the canonical block/ingot/nugget chains) | DONE (impl + T1/T2; T3 to confirm) |

**Everything below this line is the remaining scope.** Prioritisation is P0 → P2; "dropped" items are deliberately out of scope.

---

## Next feature batch (2026-10-25)

The prioritized **feature** plan for the next development cycle (bug work is done). "Harness" = covered by
`./gradlew verifyServer`; "client" = needs one NEI/GUI run.

### Tier 1 — highest value for the target GTNH pack

| Feature | What it adds | State | Effort / risk | Verification |
|---|---|---|---|---|
| **GregTech 5 machine rewrites** | GT is the largest source of dusts/plates/ingots and has **no integration** today (GT items only unify via the OD fallback). Rewrite `GT_Recipe.mOutputs` for macerator, centrifuge, ore washer, electrolyzer, chemical reactor, alloy smelter, forming press, … | not started — research spike first (`GT_Recipe` per machine, `GT_MetaGenerated` outputs, compileOnly GT types) | L / medium | per-machine verify lines + harness |
| **`inputReplacement`** | Rewrite machine *inputs* so a non-canonical ingot no longer produces a non-canonical result. Config key exists but is inert. | parsed, no behaviour | M / medium | T2 per-machine input seam + harness |
| **NEI live-entries pass** | Hide non-canonical **live** OD entries for modelled names at world join, so late registrations (the 5 Railcraft plates) stop appearing as NEI duplicates. Model unchanged; no re-writes. | not started (production logs quantify it) | S–M / low | T2 hide decision + one client boot |
| **`customUnifiedResources`** | User-defined equivalence classes (e.g. `Obsidian:dustTiny\|dust`, pack aliases) beyond the metal taxonomy. | key accepted-but-ignored | S–M / low | pure model T1 + harness |
| **`/unidict why <item>`** | Item-centric diagnostic: container, canonical entry, owner rank, and why a stack was protected/blacklisted/left alone. | not started (report is resource-centric) | S / low | console dispatch + harness |
| **`/unidict list`** | Configured metals/kinds, resolved owner order, valid kind names and unresolved config entries (extends `kindDiagnostics`). | not started | S / low | pure formatter T1 + harness |

### Tier 2 — semantic extensions

| Feature | What it adds | State | Effort / risk | Verification |
|---|---|---|---|---|
| **BB-4 fuels / coke** | First non-OD equivalence class: single stackable coal coke (Railcraft + IE emit `fuelCoke`), with per-item burn-time semantics (not naive tag equality). | deferred (prioritized) | M / medium | pure classifier T1 + burn-time harness check |
| **Fluid outputs** | Forestry still/fermenter/squeezer, GC refinery, TiC melt/alloy — needs a 1.7.10 fluid-equivalence model. | deferred (#10) | L / high | design + harness |
| **`keepOneEntry`** | Strict one-entry collapse beyond NEI hiding. Currently redundant with `autoHideInNEI`; decide: implement a functional collapse **or** drop the key. | parsed, not wired (#2) | S (drop) / L (implement) | T2 decision + client |
| **Forestry crate items** | Create `ItemCrated` at runtime for metals Forestry doesn't ship (recipes for existing crates are wired). | deferred (#9 details) | M / high | harness + client |
| **GC circuit fabricator** | The other GC machine with cross-mod-unifiable wafer outputs. | deferred (#11) | S–M / medium | harness |
| **Storage Drawers QA** | Registry seeding is implemented; confirm the Compact Drawer shows the canonical (EtF) block in-game and close #13. | implemented, T3 pending | S / none | one client check |

### Tier 3 — UX / polish

| Feature | What it adds | Effort | Verification |
|---|---|---|---|
| **Localization** (`assets/unidict/lang`) | No lang file exists at all; command messages and user-facing text are hardcoded English. | S | key-presence T1 + client |
| **Config GUI** (`GuiFactory`) | Change presets/toggles without editing a 30-key `.cfg`. | M | client |
| **NEI tooltip/overlay** | Show "unified → `main` entry" and owner rank on hover. | M | client |
| **Report pagination/formatting** | 100–200 chat lines today; page/batch them (pure paginator testable). | S | T1 + client |
| **JSON report export** | `/unidict report json` or a file under `logs/` for pack authors/CI diffing. | S | T1 + harness |
| **Dry-run mode** | Log what *would* be rewritten without mutating recipes — useful while adding GT/inputs. | S–M | T2 + harness |
| **Icon + optional-dep metadata** | `logoFile` is empty; `mcmod.info` says nothing about the optional integrations. | S | n/a |

### Tier 4 — API / ecosystem / verification

| Feature | What it adds | State | Effort |
|---|---|---|---|
| **`UniDictAPI` + helpers** | Public API so mods/scripts can query or extend the unified model (upstream's Forestry/Furnace/IE/TCon helpers). | deferred (#9) | M |
| **CraftTweaker/MineTweaker bridge** | Pack scripts can add equivalences/blacklist entries at load without a config edit — high value where MineTweaker is present. | not started | S–M |
| **Client-side T3 in CI** | The server-side oracle is in CI; NEI/GUI/tooltip paths still need a display (virtual display or Windows runner). | not started | M |
| **Harness extensions** | Synthetic `EntityItem` (drop listener), console-driven `/unidict report`, NEI hide-count assertions. | not started | S |
| **Publishing docs** | JitPack is configured; add a "use as a dependency" README section + versioning notes. | — | S |

**Suggested order:** GT5 spike/integration → `inputReplacement` → NEI live-entries + `why`/`list`/JSON → fuels (BB-4) → `customUnifiedResources` → UX/API items as appetite allows.

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

### 3. CoFH Core / TE runtime crash — CLOSED (not a UniDict defect)

- **Decision (2026-10-24):** the production modpack widens `World.collidingBoundingBoxes`; the access
  transformer kept under `src/main/resources/META-INF/` is **dev-only tooling** for a local full-stack
  client and is deliberately not tracked or published (see `CODE_REVIEW.md` BUG-2). Nothing to do for a
  release; only a local full-stack dev run needs the AT present.
- **History:** a full-stack (TE + CoFH Core) run used to die with `IllegalAccessError: … World.collidingBoundingBoxes from cofh.asmhooks.HooksCore` on the first item-entity tick — CoFH's ASM hook reads a field that the production pack (via GregTech/its own ATs) widens. Documented in `INTEGRATIONS.md` gotcha #2; no further action.

### 3b. Fixed defect (2026-10-23) — drop-time cross-wiring after `FMLModIdMappingEvent`
- **Symptom (field report, pack with Et Futurum + Artifice):** an EtF **Amethyst Cluster** drop came back as
  **aluminum/gold ore blocks** with the cluster's own count (2, more with Fortune); silk-touch drops were
  untouched. `UnifyDrops` was on (`unifyDrops=true`).
- **Root cause:** `ResourceHandler#individualStackAttributes` was keyed by
  `MetaKey.forItemAndDamage(registryId, damage)`. It is built once at POST_INIT and lives for the session,
  but Forge **renumbers item registry ids** on `FMLModIdMappingEvent` (this pack remaps 9,202 ids). Remapping
  shifts each mod's ids into another mod's old slots, so a stale lookup does not just miss — it hits
  **another item's** entry and returns that container's `getMainEntry(size)` (hence the preserved count).
  `modded_raw_ore`'s load-time id was the amethyst shard's remapped id, so the shard resolved to `oreAluminum`.
  `containerForOreName`'s OD fallback only ran on *misses*, so it never saw the false hits.
- **Fix:** the index is now `pure.IdentityDamageIndex` — keyed by the **`Item` instance (identity) + damage**,
  wildcard-aware, built from the container snapshots. Item identity survives a remap; ids do not. `UniResourceContainer.hashes`
  (and `getHashes()`) is gone, and the now-meaningless `ResourceHandler#exists(int)` (id-keyed) was removed.
  `MetaKey` keeps its role for one-shot key math inside a single pass (recipe/cumulative keys) — it must never
  key a map that outlives POST_INIT.
- **Verify:** T1 `IdentityDamageIndexTest` (identity isolation, damage keying, wildcard precedence, re-index).
  **T3 stage-2 harness** (`VerifyHarness.runPostRemapChecks`, hooked at `FMLServerStartedEvent` — *after* Forge
  rebakes ids): `resourceIndex coverage`, `resourceIndex no-cross-wiring` (index assignment must be one of the
  variant's live OD groups) and `resourceIndex no-spurious-rewrite` (an unmodeled registry item must come back by
  identity). Stage 1 ran at POST_INIT, i.e. before any remap, which is why nothing caught this.
  In-game: Amethyst Cluster and Artifice ores drop their own items with `unifyDrops=true`.
- **Follow-up (not a blocker):** the OD fallback can still resolve a stack that belongs to several modeled
  containers (arbitrary, as before); `UniOreDictionary.stackToName` is likewise id-keyed but unused in production.

### 4. Docs ↔ HEAD sync — DONE (2026-10-24)

- README, `STATUS.md`, `INTEGRATIONS.md` and this file were synced against HEAD: preset claims,
  missing integrations (TiC, Storage Drawers, drops), the metal list, the dev-only AT decision, the
  `rewritten=` delta semantics, and the corrected furnace verify line. `STATUS.md` was also re-encoded
  from mojibake to UTF-8.
- **Remaining at tag time:** a final grep for "pending"/"deferred" against the code, plus the T3 items
  still marked "to confirm" (Galacticraft compressor, Storage Drawers, TiC, NEI `hidden=N`).

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

### 13. Storage Drawers Compact Drawer
- **What:** Storage Drawers' Compact Drawer lists the same-mod block version of the metal used (TF copper ingot = TF copper nuget = TF copper block). We override Copper block to Et Futurum, so Compact Drawer should show that as the block
- **Notes:** Not very necessary but QoL

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
