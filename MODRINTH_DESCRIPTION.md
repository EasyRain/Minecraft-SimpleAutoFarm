# Simple Auto Farm

**A small Minecraft mod that adds a fully automatic farming machine, FE generators and an upgrade system.**

Put seeds in, supply FE power, and the machine automatically grows and harvests crops — the products land in the output slots below. No redstone, no external automation required.

Supported versions: **Minecraft 1.21.1 (NeoForge 21.1.x)** and **Minecraft 26.1.2 (NeoForge 26.1.2.x)**.

---

## Features

### Simple Auto Farm

- 🌱 **One-block automation** — 9 input slots (one seed each, used as markers and never consumed) + 27 output slots (extract only).
- 🧺 **Universal crop detection** — vanilla crops (wheat, beetroot, carrot, potato, ...), nether wart, saplings, bamboo, sugar cane, mushrooms, and many modded plants (AE2 / ExtendedAE / GeOre budding blocks, Mystical Agriculture crops, ...).
- ⚡ **FE powered** — consumes FE per planted crop per tick; growth pauses automatically when energy runs out.
- 📊 **Shared progress bar** — all planted crops share one growth progress; when mature, everything is harvested at once.
- 📤 **Auto-eject** — optional; pushes products into adjacent containers.
- 🎒 **Keep contents on break** — mining the machine drops it with its inventory and energy preserved (in `block_entity_data`), restored when placed again.

### Generators

- 🔥 **Simple Generator** — burns any vanilla furnace fuel to produce FE and auto-pushes it to adjacent machines. Has 2 upgrade slots.
- 🔥 **Simple Generator Pro**:
  - **10×** base production / storage / output / push rate compared to the normal generator.
  - Runs on **lava** — right-click with a lava bucket (or a fluid container from other mods) to fill it; item fuels in the fuel slot are auto-converted into lava.
  - Lava is consumed by amount (1 mB = 1 second); the GUI shows a lava bar and a flame icon.

### Upgrades

- ⚡ **Speed** (T1–T4) — faster farm batches, more generator output (and higher fuel use).
- 🔧 **Efficiency** (T1–T4) — lower farm energy use, higher energy input/cache, lower generator fuel use.
- 📦 **Yield** (T1–T4) — more farm products per harvest, higher output stack limit.
- 🌟 **Creative Upgrade** (no recipe, creative tab only) — overrides every other upgrade:
  farm produces every 1 second with `Integer.MAX_VALUE` output/stack and needs no energy;
  generators produce/cache/output/push `Integer.MAX_VALUE` FE/t with no fuel.

## Installation

1. Install the matching [NeoForge](https://neoforged.net/) version.
2. Drop the `simpleautofarm` jar into the `mods` folder.

## Notes

- Mod id: `simpleautofarm`.
- All machines require a pickaxe to mine; a bare hand drops nothing.
- Both Chinese (zh_cn) and English (en_us) translations are included.

## License

MIT
