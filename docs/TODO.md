# TODO — FurnaceBoard

> Tasks ordered by priority. Never reorder without Muhofy's approval.
> Status: `[ ]` = todo, `[x]` = done, `[~]` = in progress, `[!]` = blocked

---

## 🏗️ PHASE 1 — Foundation

- [x] Initialize Fabric mod project (fabricmc.net/develop/template)
- [x] Configure `gradle.properties`:
  - `minecraft_version=1.21.11`
  - `yarn_mappings=1.21.11+build.4`
  - `loader_version=0.18.1`
  - `fabric_version=0.140.2+1.21.11`
  - `loom_version=1.14`
  - `cloth_config_version=21.11.153+fabric`
  - `java_version=21`
- [x] Configure `build.gradle` (Loom 1.14, Java 21)
- [x] Configure `settings.gradle`
- [x] Configure `fabric.mod.json`:
  - mod ID: `furnaceboard`
  - environment: `client`
  - entrypoints: client only
- [x] Set up package structure per MEMORYBANK
- [x] Create `FurnaceBoardMod.java` entry point
- [x] Create `FurnaceBoardLogger.java`
- [x] Verify project builds cleanly: `./gradlew build`

---

## 📦 PHASE 2 — Data Layer

- [x] Verify Yarn 1.21.11+build.4 field names for `AbstractFurnaceBlockEntity`:
  - `cookTime` — ticks elapsed on current item
  - `cookTimeTotal` — total ticks needed for current item
  - `burnTime` — fuel ticks remaining
  - **Do NOT assume these names — check Yarn javadoc first**
- [x] Implement `FurnaceState.java` (enum: SMELTING, DONE, NO_FUEL, EMPTY)
- [x] Implement `FurnaceRecord.java` (full contract per SYSINSTRUCTIONS, NBT serializable)
- [x] Implement `FurnaceBoardWorldData.java`:
  - [x] `Map<BlockPos, FurnaceRecord>` storage
  - [x] Load from `.minecraft/saves/<world>/furnaceboard.dat`
  - [x] Save to same file
  - [x] Prune records not updated in 3 real days
- [x] Validate: data saves and loads correctly across game restarts

---

## ⚙️ PHASE 3 — Core Tracking

- [x] Investigate: how to read `BlockPos` + furnace fields from open furnace screen client-side
  - Option A: `ScreenEvents` (FAPI) → cast handler → read block entity
  - Option B: Mixin into `AbstractFurnaceScreen` — flag as high-risk
  - Verify `ScreenEvents` fires for `AbstractFurnaceScreen` in FAPI `0.140.2+1.21.11`
- [ ] Implement `FurnaceTrackerManager.java`:
  - [ ] Hook furnace screen open → read BlockPos + initial state → create/update `FurnaceRecord`
  - [ ] Hook `ClientTickEvents` → recalculate ETA every 20 ticks for all tracked furnaces
  - [ ] Detect state transitions (e.g. SMELTING → DONE)
  - [ ] On transition to DONE → fire event to `FurnaceNotifier`
  - [ ] Remove stale records (not updated in 3 real days)
  - [ ] Persist changes to `FurnaceBoardWorldData`
- [ ] Implement `FurnaceBoardKeybinds.java`:
  - [ ] Register dashboard keybind (default: `F`) via `KeyBindingHelper` (FAPI)
  - [ ] Verify `KeyBindingHelper` exists in FAPI `0.140.2+1.21.11`

---

## 🔔 PHASE 4 — Notification System

- [ ] Decide notification sound: vanilla `SoundEvents.BLOCK_NOTE_BLOCK_PLING` or custom
  - Verify `SoundEvents` field name in Yarn 1.21.11+build.4 before use
- [ ] Implement `FurnaceNotifier.java`:
  - [ ] Show vanilla toast notification (title: item name, body: position)
  - [ ] Play sound via `MinecraftClient.getInstance().getSoundManager()`
  - [ ] Fire once per furnace per DONE transition — not repeatedly
  - [ ] Respect config: notification toggle, sound toggle, sound volume

---

## 🖥️ PHASE 5 — UI

- [ ] Implement `FurnaceBoardHudWidget.java`:
  - [ ] Always visible (toggleable via keybind or dashboard button)
  - [ ] Shows: active furnace count + next completion ETA
  - [ ] Position: configurable via config (top-left / top-right / bottom-left / bottom-right)
  - [ ] Format ETA as MM:SS
  - [ ] Color: green if smelting, gold if any done, red if any no-fuel

- [ ] Implement `FurnaceBoardScreen.java`:
  - [ ] Opens on keybind press (only when not already in a screen)
  - [ ] Header: "FurnaceBoard" title
  - [ ] Per furnace row:
    - [ ] Position (formatted as "X, Y, Z")
    - [ ] Item icon + name + count
    - [ ] Progress bar (color coded per state)
    - [ ] ETA or state label (✅ Done / ❌ No Fuel / — Empty)
  - [ ] [Toggle HUD] button
  - [ ] [Close] button + ESC
  - [ ] Scroll if more than 6 furnaces

---

## ⚙️ PHASE 6 — Config & Polish

- [ ] Implement `FurnaceBoardConfig.java` (Cloth Config):
  - [ ] Toggle: enable/disable notifications
  - [ ] Toggle: enable/disable sound
  - [ ] Setting: notification sound volume (0.0–1.0)
  - [ ] Toggle: show HUD widget by default
  - [ ] Setting: HUD position (top-left / top-right / bottom-left / bottom-right)
  - [ ] Setting: stale data pruning days (default: 3)
- [ ] `en_us.json` — all translatable strings, zero hardcoded text in Java
- [ ] Register config screen via ModMenu
- [ ] `PosFormatter.java` — format BlockPos as clean "X, Y, Z" string

---

## 🚀 PHASE 7 — Release

- [ ] Full clean test: Fabric 1.21.11, no other mods
- [ ] Test with ModMenu + Cloth Config installed
- [ ] Test all 3 furnace types (Furnace, Blast Furnace, Smoker)
- [ ] Test notification fires exactly once per completion
- [ ] Test HUD toggle
- [ ] Test data persistence across world reloads
- [ ] Test stale data pruning
- [ ] Write Modrinth description (EN)
- [ ] Create mod icon (512x512 PNG)
- [ ] Record screenshots (dashboard + HUD + notification)
- [ ] Publish to Modrinth
- [ ] Publish to CurseForge

---

## 🐛 KNOWN ISSUES

_None — project not started._

---

## 💡 BACKLOG (v2+)

- Server-side mode: track ALL furnaces in world, not just opened ones
- Multi-furnace ETA sorted list (next to finish first)
- Click furnace in dashboard → teleport/waypoint to it (optional, toggleable)
- Integration with Xaero's Minimap: show furnace icons on map
- Notify when furnace runs out of fuel mid-smelt
- Support for modded furnaces (via tag system)