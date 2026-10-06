# ⚡ Final Step: Wire New Zero-Lag Modular Player System

## Deep Root Cause Analysis

### Why Performance Didn't Change Yet
The Phase 1-6 component files were created successfully, but `MainActivity.kt` and `Player.kt` were still executing the **old 195KB monolithic `Player.kt` file** (~4,700 lines).

### Root Causes of Lag in Old `Player.kt`:
1. **Root-Level 20Hz Position Polling**: The position getter was executed directly inside `BottomSheetPlayer` 20 times per second, forcing the **entire 4,700-line Compose tree** to recompose 20 times/second.
2. **20+ Root `collectAsState()` Calls**: Reading player state, network status, queue, lyrics, and volume at the top-level scope triggered complete recompositions whenever *any* state changed.
3. **Unscoped Recomposition**: Controls, background shaders, song info, thumbnails, and lyrics all shared a single composition scope.

---

## 🎯 The Fix: Wire `BottomSheetPlayer` to `FullPlayer` & `MiniPlayer`

We will update `BottomSheetPlayer` in `Player.kt` to delegate rendering directly to our new, modular, `@Immutable` composables:

```
BottomSheetPlayer (Orchestrator)
 ├── MiniPlayer (Collapsed scope - reads track & transport only)
 └── FullPlayer (Expanded scope - uses () -> Long position provider)
      ├── PlayerBackground (Draw-phase gradient & AGSL shader)
      ├── Thumbnail (LazyHorizontalGrid snap carousel + ExoPlayer pool)
      ├── SongInfo (Title marquee & artist avatars)
      ├── PlayerSlider (Draw-phase canvas rendering — zero recompositions)
      ├── PlayerControls (Press-scale spring physics: 0.86x, 0.54f / 720f)
      ├── ActionButtons (Action icons)
      └── PlayerBottomCards (Visual card stack: 104dp & 132dp, karaoke highlight)
```

---

## Technical Action Plan

### 1. `Player.kt` Wiring
- Replace `BottomSheetPlayer` inner content with `BottomSheet` hosting `MiniPlayer` (collapsed) and `FullPlayer` (expanded).
- Supply `positionProvider = { playerConnection.player.currentPosition }` so position reads happen **only in the draw phase of `PlayerSlider`**, removing 20Hz root recomposition completely.
- Scope `trackInfo` and `transportState` collections so `MiniPlayer` recomposes independently.

### 2. Move Monolith to `old/`
- Archive old monolithic `Player.kt` implementation into `ui/player/old/PlayerOld.kt` to ensure clean build separation.

### 3. Verification & Benchmark
- Run `./gradlew compileUniversalFossDebugKotlin` to verify zero compile errors.
- Verify smooth 60fps scrolling, bottom sheet dragging, and slider drag without jank.

---

## Visual Contract Guarantee
- **MiniPlayer**: `64.dp` height, `48.dp` cover art, `15.sp` bold title, `13.sp` artist, `36.dp` controls.
- **FullPlayer**: Full width, exact `74.dp` transport buttons, `28.dp` slider, `150.dp` bottom cards stack, all 6 background styles.
- **Physics**: Press scale `0.86x` (`dampingRatio = 0.54f, stiffness = 720f`), track height spring (`0.72f, 520f`).
