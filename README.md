# 🧱 UniDict

[![](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)
[![](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Build and test](https://github.com/MrFuzzihead/UniDict-Legacy/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/MrFuzzihead/UniDict-Legacy/actions/workflows/build-and-test.yml)
[![Verify server](https://github.com/MrFuzzihead/UniDict-Legacy/actions/workflows/verify-server.yml/badge.svg)](https://github.com/MrFuzzihead/UniDict-Legacy/actions/workflows/verify-server.yml)
[![Test harness](https://github.com/MrFuzzihead/UniDict-Legacy/actions/workflows/test-harness.yml/badge.svg)](https://github.com/MrFuzzihead/UniDict-Legacy/actions/workflows/test-harness.yml)

**UniDict** is a Minecraft **1.7.10** mod that fixes one of the most annoying problems in large modpacks: five different
"copper ingots," three "iron ingots," and a chest full of metals that all look the same but refuse to stack.

It picks one **canonical "main" version** of each metal and each thing made from it, then quietly points the rest of
the game at that version. The duplicates still exist; they just stop getting in your way.

## What it does

| Surface | What UniDict does |
|---|---|
| **Machines and recipes** | Rewrites outputs so machines produce the canonical item instead of a random duplicate. |
| **Crafting** | Makes the crafting table return the canonical result. |
| **Chests and loot** | Rewrites dungeon, mineshaft, village, and bonus-chest loot to the canonical item. |
| **Dropped items** | Converts plain, untagged drops on the ground to the canonical item. |
| **NEI** | Hides non-canonical duplicate entries so you see one clear version of each resource. An item that is canonical for *another* resource always stays visible. |
| **Storage Drawers** | Seeds the canonical block/ingot/nugget chains into the Compacting Drawer registry. |

Out of the box, UniDict unifies **Iron, Gold, Copper, Tin, Silver, Lead, Nickel, Platinum, Aluminum/Aluminium, Ardite,
Cobalt, Osmium, Mithril, Zinc, Invar, Steel, Bronze, Electrum, Brass, Titanium, Desh, Meteoric Iron, Tungsten, Uranium,
and Magnesium**, covering the whole family: **ore, dust, tiny dust, chunks, nuggets, ingots, blocks, plates, and gears.**

You can choose exactly which metals and forms to unify in the config.

## Supported mods

Each integration activates only when that mod is installed. A vanilla-only install still gets furnace, crafting-table,
chest-loot, and drop support, plus NEI hiding when NEI is present.

| Mod | Machines / surfaces with unified output |
|---|---|
| **Vanilla** | Furnace, crafting table, dungeon/mineshaft/village/bonus-chest loot |
| **Applied Energistics 2** | Grinder |
| **IndustrialCraft 2** | Macerator, Compressor, Extractor, Centrifuge, Metal Former, Block Cutter, Blast Furnace, Ore Washing Plant |
| **Immersive Engineering** | Arc Furnace, Blast Furnace, Crusher, Metal Press |
| **Ender IO** | Alloy Smelter, SAG Mill |
| **Railcraft** | Blast Furnace, Rock Crusher |
| **Thermal Expansion** | Redstone Furnace, Pulverizer, Induction Smelter |
| **Forestry** | Carpenter, Squeezer, Centrifuge (plus optional crate and bronze-recycling recipes) |
| **Galacticraft** | Ingot Compressor, Electric Ingot Compressor |
| **Tinkers' Construct** | Smeltery Casting Table and Casting Basin |
| **Storage Drawers** | Compacting Drawer tier registry |

### A few nice extras

- **Forestry crate recipes.** If Forestry already has a crate for a metal, UniDict wires up the matching "crate it" /
  "uncrate it" carpenter recipes using the unified metal.
- **Bronze tool recycling.** Broken bronze tools can be recycled back into bronze ingots through Forestry's carpenter.
- **Galacticraft electric/arc furnaces** reuse the vanilla furnace recipes, so they are already covered.
- **Ground drops** are converted too, but only plain, untagged stacks; enchanted or NBT-bearing items are left alone.

## Requirements

| Dependency | Version | Notes |
|---|---|---|
| Minecraft | **1.7.10** | |
| Minecraft Forge | **10.13.4.1614** | |
| Java | **8** | Runtime/toolchain target; modern launchers and GTNH packs may supply a newer JVM. |
| Optional mods | Any of the supported mods above | Install only what your pack uses. |

UniDict is client- and server-aware. It never removes items, deletes recipes, or mutates Forge's Ore Dictionary.

## Installation and configuration

Drop the jar into your `mods` folder. The config file is generated at `config/unidict.cfg` on first launch.

Three ready-made presets are available:

| Preset | What it enables |
|---|---|
| `minimal` | The lightest touch: vanilla-safe furnace and chest-loot support, with NEI hiding off. |
| `standard` | **Default.** All supported integrations and the full metal list. |
| `max-compat` | Standard plus extra diagnostics and compatibility options. |

Beyond the preset, you can:

- Toggle each mod integration individually.
- Choose which metals and which forms (`ore`, `dust`, `ingot`, `block`, ...) to unify.
- Set the **owner priority order**, which decides which mod wins the "main" slot.
- Protect specific items or Ore Dictionary names from unification.

`protectedOreDictionaryNames` defaults to protecting `raw` metals, so raw-metal processing stages stay as their own
thing instead of being morphed into a regular ore block. The `inputReplacement` key is parsed for forward compatibility
but currently has no effect.

Your settings are never wiped on update; UniDict only adds new config keys.

## Commands

| Command | What it does |
|---|---|
| `/unidict report` | Prints the full unification report. |
| `/unidict report <resource>` | Filters the report to one resource, e.g. `/unidict report ingotCopper`. |
| `/unidict report <resource> <page>` | Shows a specific page; the report is paged at 20 lines. |

The report shows the canonical entry, variant count, owners, and per-machine rewrite counts. It is also emitted by the
headless server verifier as `[unidict-verify]` lines.

## Building

Everything is handled by the GTNH build toolchain:

```sh
./gradlew build              # compile, tests, format checks, coverage gate
./gradlew test               # JVM/seam tests only
./gradlew jacocoTestReport   # HTML coverage -> build/reports/jacoco/test/html
./gradlew gameTestServer     # dedicated-server in-game assertions
./gradlew verifyServer       # headless [unidict-verify] oracle
./gradlew clientTest         # client smoke test (use Xvfb on headless Linux)
```

For client development:

```sh
./gradlew runClient
./gradlew runServer
```

For a lighter local runtime, `-PunidictDevLight` keeps the heavy integrations on the compile classpath only, avoiding
CoFH Core on the runtime path. Full-stack client runs need the dev-only access transformer or the production pack's
equivalent field widening; server verification does not.

## Testing

| Command | What it runs |
|---|---|
| `./gradlew test` | **242** JUnit 5/Mockito tests, 0 failures |
| `./gradlew check` | The above plus Spotless, Checkstyle, the coverage floor, and game-test compilation |
| `./gradlew gameTestServer` | Boots a real dedicated server and runs **23/23** in-game assertions |
| `./gradlew verifyServer` | **300 stage-1 + 5 stage-2** headless checks, 0 failures |
| `./gradlew clientTest` | Client smoke boot; use Xvfb on headless Linux |

The current JaCoCo baseline is **44.8% line / 40.6% branch**, with a **44% / 40%** floor. CI runs the build,
the headless verifier, and the test harness on pull requests and pushes to `main`.

## Documentation

- [`docs/TODO.md`](docs/TODO.md#suggested-direction-authoritative) — forward-looking backlog and the authoritative
  suggested feature direction.
- [`docs/INTEGRATIONS.md`](docs/INTEGRATIONS.md) — every machine/loot integration, its load stage, seam, and verify line.
- [`docs/TestPlan.md`](docs/TestPlan.md) — the T1/T2/T3 verification doctrine.
- [`docs/PLAN.md`](docs/PLAN.md) — implementation plan and milestone gates.
- [`docs/STATUS.md`](docs/STATUS.md) — per-milestone tracker.
- [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md) — historical review findings and fix batches.
- [`docs/CODEBASE_ANALYSIS_2026-11-10.md`](docs/CODEBASE_ANALYSIS_2026-11-10.md) — independent follow-up analysis,
  dependency audit, and closed optimization list.

## Contributing

Bug reports and pull requests are welcome. Before starting a feature, check the
[suggested direction in `docs/TODO.md`](docs/TODO.md#suggested-direction-authoritative) and read
[`docs/TestPlan.md`](docs/TestPlan.md) for the testing tiers. Structural changes should also account for the historical
findings in [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md).

If a bug looks like it belongs to Forge or an integrated mod rather than UniDict, please report it to that project.

## License

UniDict is licensed under the **MIT License**. See [`LICENSE`](LICENSE).

## Credits

UniDict is maintained by **MrFuzzihead** as a rebuild of the original **UniDict by WanionCane**. Credit to WanionCane
for the original concept, and to the GTNH ecosystem for the 1.7.10 tooling and pack environment.

---

*Happy unifying — may all your ingots finally stack.*
