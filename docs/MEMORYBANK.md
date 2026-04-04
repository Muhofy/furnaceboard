# MEMORYBANK — FurnaceBoard

> Single source of truth for all project decisions, architecture, and progress.
> Update this file after every major decision or milestone.

---

## PROJECT IDENTITY

| Field | Value |
|---|---|
| Mod Name | FurnaceBoard |
| Mod ID | `furnaceboard` |
| Description | Track all your furnaces at a glance. Get notified when smelting is done. |
| Platform | Fabric |
| Minecraft Version | `1.21.11` |
| Mappings | Yarn `1.21.11+build.4` |
| Java Version | `21` |
| License | MIT |
| Modrinth Slug | `furnaceboard` (TBD) |
| Side | Client-side only (v1) |

---

## ONE-LINE PITCH

> *"See all your furnaces at once. Get notified the moment smelting is done."*

---

## VERIFIED DEPENDENCY VERSIONS

| Dependency | Version | Source |
|---|---|---|
| Minecraft | `1.21.11` | Mojang |
| Yarn Mappings | `1.21.11+build.4` | maven.fabricmc.net |
| Fabric Loader | `0.18.1` | fabricmc.net |
| Fabric API | `0.140.2+1.21.11` | Modrinth |
| Fabric Loom | `1.14` | fabricmc.net |
| Cloth Config | `21.11.153+fabric` | Modrinth |

---

## CORE FEATURES (v1)

### 1. Furnace Tracking
- Furnace recorded when player opens its screen
- State updated every 20 ticks (1 second)
- Tracks: position, dimension, input item, progress, ETA, fuel state

### 2. Dashboard Screen
- Opens via keybind (default: `F`)
- Lists all tracked furnaces with:
  - World position
  - Item being smelted + count
  - Progress bar
  - ETA (time remaining)
  - State indicator (Smelting / Done / No Fuel / Empty)
- Toggle HUD button
- Close button + ESC

### 3. HUD Widget
- Always visible (toggleable)
- Shows: active furnace count + next completion ETA
- Compact, corner of screen
- Position configurable via Cloth Config

### 4. Completion Notification
- Toast notification on screen
- Sound effect (vanilla ding or custom)
- Fires once per furnace per completion
- Shows which item finished + position

### 5. Supports All Furnace Types
- Furnace
- Blast Furnace
- Smoker

---

## ARCHITECTURE DECISIONS

| Decision | Choice | Reason |
|---|---|---|
| Side | Client-only (v1) | No server mod needed, simpler, wider compat |
| Tracking trigger | Player opens furnace screen | Only track furnaces player interacts with |
| State update | Every 20 ticks via ClientTickEvents | Balance accuracy vs performance |
| Notification | Toast + sound | Non-intrusive, familiar vanilla pattern |
| Dashboard trigger | Keybind (default: F) | Fast access |
| HUD | Always-on widget, toggleable | Player choice |
| Data storage | World NBT (`furnaceboard.dat`) | Per-world isolation |
| Stale data pruning | Remove after 3 real days not seen | Prevent data bloat |
| ETA formula | `(cookTimeTotal - cookTime) / 20` seconds | Standard tick-to-second conversion |
| Furnace types | AbstractFurnaceBlockEntity subtypes | Covers all 3 vanilla furnace types |

---

## PROJECT STRUCTURE

```
furnaceboard/
├── src/main/java/dev/muhofy/furnaceboard/
│   ├── FurnaceBoardMod.java                      # Entry point (client)
│   ├── tracker/
│   │   └── FurnaceTrackerManager.java            # Records + updates furnace states
│   ├── data/
│   │   ├── FurnaceRecord.java                    # Single furnace snapshot
│   │   ├── FurnaceState.java                     # Enum: SMELTING/DONE/NO_FUEL/EMPTY
│   │   └── FurnaceBoardWorldData.java            # Root NBT container, saved to disk
│   ├── notification/
│   │   └── FurnaceNotifier.java                  # Toast + sound on DONE transition
│   ├── ui/
│   │   ├── FurnaceBoardScreen.java               # Dashboard screen (keybind)
│   │   └── FurnaceBoardHudWidget.java            # Compact HUD overlay
│   └── util/
│       ├── FurnaceBoardConfig.java               # Cloth Config integration
│       ├── FurnaceBoardLogger.java               # Shared logger
│       ├── FurnaceBoardKeybinds.java             # Keybind registration
│       └── PosFormatter.java                     # BlockPos → readable string
├── src/main/resources/
│   ├── fabric.mod.json
│   └── assets/furnaceboard/
│       └── lang/
│           └── en_us.json
├── gradle.properties
├── build.gradle
├── settings.gradle
├── TODO.md
├── MEMORYBANK.md
└── SYSINSTRUCTIONS.md
```

---

## DATA MODEL

```
FurnaceBoardWorldData
└── Map<BlockPos, FurnaceRecord>
    └── FurnaceRecord
        ├── pos: BlockPos
        ├── dimension: RegistryKey<World>
        ├── inputItem: Identifier          // nullable
        ├── inputCount: int
        ├── cookTimeTotal: int             // ticks
        ├── cookTime: int                  // current progress ticks
        ├── burnTime: int                  // fuel remaining ticks
        ├── state: FurnaceState
        └── lastUpdated: long              // System.currentTimeMillis()
```

---

## OPEN QUESTIONS

- [ ] Exact Yarn 1.21.11 field names for `AbstractFurnaceBlockEntity`: `cookTime`, `cookTimeTotal`, `burnTime` — **must verify in Yarn javadoc before use**
- [ ] Does `ScreenEvents` (FAPI) fire for furnace screen open in 1.21.11? Needs verification
- [ ] How to read `BlockPos` from open furnace screen client-side? Via `ScreenHandler` or `BlockEntity`? Needs investigation
- [ ] What sound to use for notification — vanilla `BLOCK_NOTE_BLOCK_PLING` or custom? Decide before Phase 4
- [ ] Should ETA show seconds or MM:SS format? (MM:SS more readable)

---

## MILESTONES

- [ ] Project scaffolding + verified gradle.properties
- [ ] `FurnaceRecord`, `FurnaceState`, `FurnaceBoardWorldData` data models
- [ ] `FurnaceTrackerManager` — record on screen open + tick update
- [ ] `FurnaceNotifier` — toast + sound on DONE
- [ ] `FurnaceBoardScreen` — dashboard UI
- [ ] `FurnaceBoardHudWidget` — compact HUD
- [ ] `FurnaceBoardConfig` — Cloth Config screen
- [ ] `en_us.json` — all translatable strings
- [ ] Full test: single player 1.21.11
- [ ] Modrinth release

---

## CHANGELOG

| Date | Change |
|---|---|
| 2026-04-03 | Project created — FurnaceBoard concept finalized |
| 2026-04-03 | Docs created (SYSINSTRUCTIONS, MEMORYBANK, TODO) |