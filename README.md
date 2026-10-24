# 🧱 UniDict

**UniDict** is a Minecraft **1.7.10** mod that solves one of the most annoying problems in big modpacks: having **five different "copper ingots," three "iron ingots," and a chest full of metals that all look the same but won't stack together.**

If you've ever stood at a storage room full of nearly-identical ingots and wondered why they don't combine into one pile, this is the mod for you.

---

## What does it do?

In a large modpack, several mods each add their *own* version of the same thing — a copper ingot from Thermal Foundation, one from IC2, one from Immersive Engineering, and so on. They're all basically copper, but Minecraft treats them as completely different items.

**UniDict picks one "main" version of each metal** (and each thing made from it), and then quietly makes everything else in the game line up with that one version:

- ✅ **Machines** produce the main version instead of random duplicates.
- ✅ **The crafting table** makes the main version.
- ✅ **Chests and loot** will hand you the main version.
- ✅ **Items you drop on the ground** turn into the main version.
- ✅ **NEI item lists** hide the confusing duplicate entries, so you just see one. (An item that is itself the main version of *another* unified resource always stays visible.)

The result: one copper ingot, one tin ingot, one iron ingot... they all stack, they all work together, and you can stop organizing your base's duplicates into "the ones that won't merge."

### Which metals?
Out of the box UniDict unifies the usual suspects — **Iron, Gold, Copper, Tin, Silver, Lead, Nickel, Platinum, Aluminum/Aluminium, Ardite, Cobalt, Osmium, Mithril, Zinc, Invar, Steel, Bronze, Electrum, Brass, Titanium, Desh, Meteoric Iron, Tungsten, Uranium, and Magnesium** — and for each one it covers the whole family: **ore, dust, tiny dust, chunks, nuggets, ingots, blocks, plates, and gears.**

> You can pick and choose exactly which metals (and which forms of them) you want unified in the config file — see below.

---

## Which mods' machines are supported?

UniDict hooks straight into the machines of the mods below, so their outputs come out as the unified version automatically (each one only applies if that mod is installed):

| Mod                       | Machines that get unified output                                                                           |
|---------------------------|------------------------------------------------------------------------------------------------------------|
| **Vanilla**               | Furnace                                                                                                    |
| **Vanilla**               | The crafting table                                                                                         |
| **Vanilla**               | Dungeon / mineshaft / village / bonus chest loot                                                           |
| **Applied Energistics 2** | Grinder                                                                                                    |
| **IndustrialCraft 2**     | Macerator, Compressor, Extractor, Centrifuge, Metal Former, Block Cutter, Blast Furnace, Ore Washing Plant |
| **Immersive Engineering** | Arc Furnace, Blast Furnace, Crusher, Metal Press                                                           |
| **Ender IO**              | Alloy Smelter, SAG Mill                                                                                    |
| **Railcraft**             | Blast Furnace, Rock Crusher                                                                                |
| **Thermal Expansion**     | Redstone Furnace, Pulverizer, Induction Smelter                                                            |
| **Forestry**              | Carpenter, Squeezer, Centrifuge (plus a couple of handy bonus recipes — see below)                         |
| **Galacticraft**          | Ingot Compressor, Electric Ingot Compressor                                                                |
| **Tinkers' Construct**    | Smeltery Casting Table and Casting Basin (melt/alloy stages produce fluids, so they have no item to unify)  |
| **Storage Drawers**       | Compacting Drawers (the canonical block/ingot/nugget chains are seeded into the drawer tier registry)      |

### A few nice extras

- **Forestry crate recipes.** If Forestry already knows a crate for a metal, UniDict wires up the matching "crate it" / "uncrate it" carpenter recipes using your unified metal.
- **Bronze tool recycling.** Got broken bronze tools? With a unified bronze ingot, UniDict adds carpenter recipes to recycle them back into bronze ingots.
- **Galacticraft's electric / arc furnaces** reuse the vanilla furnace recipes, so they're already covered for free.
- **Dropped items** are converted on the ground too (only plain, untagged stacks — see below).

---

## It's safe — nothing gets deleted

Don't worry, UniDict is **non-destructive**. It never removes items from the game, never deletes recipes, and never destroys anything in your chests.

It works by **rewriting outputs** — pointing a machine/recipe/loot-table at the *main* version of an item instead of a duplicate. All the other versions still exist in the game; they're just not what machines hand you anymore.

Enchanted, tagged, or otherwise "special" items are **never touched** when you drop them — only plain, clean items get unified on the ground.

---

## Your item lists just got cleaner (NEI)

With the default settings, UniDict hides the duplicate "non-main" variants of unified resources in **Not Enough Items**, so you see one clear entry instead of a wall of visually-identical ingots. You can fine-tune this in the config:

- Exempt a whole **kind** from hiding (e.g. keep every mod's version of `ore` visible).
- Exempt a specific **mod** from hiding.
- Protect specific **items** so they always show up.

Only the *non-main* variants are hidden: an item that is the main version of some other unified resource is always kept visible, even if it loses to another variant elsewhere.

---

## Wondering what won? Run `/unidict report`

Everything UniDict does is transparent. Type this in the chat:

```
/unidict report
```

...and you'll see, for every unified resource, **which item won the "main" spot** and how many variants were merged, plus a per-machine count of the outputs that were actually rewritten. Too much text? Filter it down to one resource:

```
/unidict report ingotCopper
```

Handy for when you want to know exactly what "copper" is going to be in this pack.

---

## Tuning it to your liking (config)

UniDict writes a normal config file to your `config` folder. There are three ready-made **presets** to pick from:

- **`minimal`** — vanilla-safe only (furnace + chest loot). The lightest touch.
- **`standard`** *(default)* — all supported integrations on, full metal list. Most packs want this.
- **`max-compat`** — standard, plus the extra diagnostics (kind debug logging). The `inputReplacement` key is reserved for a future release and currently has no effect.

Beyond the preset you can individually toggle each mod integration, choose which metals and which forms (`ore`, `dust`, `ingot`, `block`, ...) to unify, set the **owner priority order** (which mod gets to be the "main" version), and protect specific items or resource tags from being unified.

> Tip: There's a `protectedOreDictionaryNames` option. By default, it protects `raw` metals, so raw-metal processing stages (like Et Futurum's oxidizable copper) stay as their own thing instead of being morphed into a regular ore block.

Your settings are **never wiped** when the mod updates — UniDict only ever adds new config keys, it won't clear out what you already changed.

---

## Compatibility

|               |                                     |
|---------------|-------------------------------------|
| **Minecraft** | 1.7.10                              |
| **Forge**     | 10.13.4.1614                        |
| **Framework** | Built with the GTNH build toolchain |

UniDict is a small client-*and*-server-aware mod. You can drop it into a pack that doesn't have any of the optional mods — the vanilla furnace, crafting table, and chest-loot support work on their own, and the mod-specific integrations simply activate only when their mod is present.

---

## Developer docs

Verification: `./gradlew test` (unit + seam tests) and `./gradlew verifyServer` (headless dedicated-server
run of the `[unidict-verify]` oracle — no display needed).

- [`docs/INTEGRATIONS.md`](docs/INTEGRATIONS.md) — every machine/loot integration, its load stage, seam and verify line.
- [`docs/PLAN.md`](docs/PLAN.md) — implementation plan and milestone gates.
- [`docs/STATUS.md`](docs/STATUS.md) — per-milestone tracker.
- [`docs/TestPlan.md`](docs/TestPlan.md) — the T1/T2/T3 verification doctrine.
- [`docs/TODO.md`](docs/TODO.md) — forward-looking backlog.
- [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md) — severity-ranked review findings and what has been fixed.

---

## Credits & License

UniDict is maintained by **MrFuzzihead** as a rebuild of the original **UniDict by WanionCane** — credit to WanionCane for the original concept.

The source code is licensed under the **MIT License**. See [`LICENSE`](LICENSE) for details.

---

*Happy unifying — may all your ingots finally stack.*
