# PopupSheet - the one popup primitive

Read this before building any new popup, bottom sheet, "sheet-like dialog", info page, or
overlay in this app. It exists because the app used to have four separate hand-rolled
implementations of "show a sheet over the screen" (`BottomSheetMenu`, `BottomSheetPage`,
`ZoomOutPopupContainer`, plus one-off `Dialog`/`AlertDialog`/`Surface` popups like the old
`RestrictionPopup` and `UpdateInfoDialog`), each with its own colors, corner radii, drag
physics, and dismiss behavior. If you are about to write `Dialog(onDismissRequest = ...)`,
`ModalBottomSheet(...)`, or a hand-rolled `Box` with a rounded top and a drag handle, stop -
use `PopupSheet` instead.

Engine lives in `app/src/main/kotlin/com/example/musicfy/ui/component/PopupSheet.kt`.

## The mental model

There is one `PopupSheetHost` at the root of the app (wired into `MainActivity.kt`). It wraps
the entire app UI, and when any popup becomes visible it:

1. Zooms the app content out slightly, rounds its corners, and dims it with a flat alpha scrim
   (the "receding screen" effect) - a plain `Color.Black.copy(alpha = ...)` box, deliberately
   **not** a blur. A render-node blur was tried first and reverted: capturing + blurring the
   whole app every frame is expensive, and `GlassKit`'s progressive multi-step blur (built for
   an ~86dp top bar strip) compounds opacity across its overlapping steps when stretched over a
   full screen until it reads as solid black instead of a translucent backdrop. If you're
   tempted to add blur back here, don't - profile it first and expect to hit the same wall.
2. Renders the active sheet anchored to the bottom, capped at ~86% of the screen height
   (`MaxSheetHeightFraction`) on phones - **never edge-to-edge**. A fullscreen version was also
   tried and reverted: it put the drag handle right under the status bar and left nothing of the
   "receded" app visible above it, which defeated the point of the zoom-out in the first place.
   Leave that gap. On tablets it's a floating centered card (`maxWidth >= 600.dp`) instead.

`MenuSheetSurface` (the player's own in-sheet menus - see "Player sub-sheets" below) is a
separate primitive and does **not** go through this host at all; it was tried once and reverted
because it made the whole screen darken behind the player for something that should stay local
to it. Don't reattach it.

The zoom/dim amount is not its own independent animation - `PopupSheetHost` reads the active
state's `presence` (the most-open frame's `1 - offset`) directly inside a graphicsLayer, every
frame. Because it's the *max* over the stack, it tracks a lone sheet's drag/entrance/exit exactly,
but pushing a second sheet, or dragging/popping the one in front, never un-zooms the app (an
earlier shared `liveOffset` reset to "closed" on every push, so the app zoomed back in and out -
that read as a new window opening). If you add a new way to move a frame, write its
`Frame.offset`; the host and the frames behind derive everything from that.

You never call `PopupSheetHost` yourself. You just call `.show { ... }` on one of three
composition-local states, same as before the unification:

- `LocalMenuState.current` - action menus (song menu, playlist menu, queue menu, player menu...).
- `LocalBottomSheetPageState.current` - "page"-like sheets pushed from a menu (media info, etc).
- `LocalZoomOutOverlayState.current` - app-level overlays not tied to a specific list screen
  (Donate, update prompts, onboarding flows).

Which one you pick barely matters mechanically (they're all literally `PopupSheetState` under
the hood - `MenuState`, `BottomSheetPageState`, `ZoomOutOverlayState` are type aliases of it).
Pick by what's semantically showing it: a list-row menu, a sheet pushed from a menu, or a
standalone app-level popup.

## How to show one

```kotlin
val menuState = LocalMenuState.current

menuState.show {
    // your content, as a ColumnScope - put a LazyColumn/Column of rows here like
    // every other menu in the app does. Don't wrap it in your own Box/background/corner
    // radius - PopupSheet already supplies the surface, the drag handle, and the padding.
    Material3MenuGroup(items = ...)
}
```

### The two independent modes the caller controls

```kotlin
state.show(
    locked = false,       // false (default): drag handle + swipe-to-close + tap-outside +
                           //   back button all dismiss it. true: none of those do - the only
                           //   way out is a button inside `content` or `buttonBar` calling
                           //   `state.dismiss()`. Use `locked = true` for anything that must
                           //   be a forced choice (irreversible action confirmation, a flow
                           //   that must finish before returning).
    buttonBar = null,      // null (default): no pinned bottom bar, content can scroll under
                           //   where a handle/next-gesture would be. Non-null: a single
                           //   @Composable pinned below the scrollable content, outside the
                           //   content's own padding - use this for a single primary action
                           //   like Donate's "Done" button, not for a list of menu rows
                           //   (those belong in `content` as a Material3MenuGroup).
    fullBleed = false,     // false (default): content gets the standard 20dp horizontal
                           //   padding, matching every menu/list sheet in the app. true: no
                           //   padding - only use this if your content draws its own full-width
                           //   background/chrome (see "fullBleed exception" below).
) { /* content: ColumnScope */ }

state.dismiss()
```

Everything else - the drag physics, the lock resistance, the zoom-out backdrop, the
phone-vs-tablet layout, the surface color, the content height cap - is the engine's job, not the
caller's. Don't reimplement any of it at the call site. Your content composable should also not
assume it fills the screen: it gets a bounded-height, horizontally-padded `ColumnScope` and
should size to its own content (wrap a plain `Column` in `Modifier.verticalScroll(rememberScrollState())`
if it might overflow that - `DonateSheet.kt`/`UpdateSheet.kt` both do this; a `LazyColumn`-based
menu doesn't need it, it scrolls on its own, and must **not** also be wrapped in `verticalScroll`
- nesting two scrollables of the same orientation crashes with an infinite-height measurement).

Either kind of scrollable also doubles as a second way to dismiss: `PopupSheet` attaches a
`NestedScrollConnection` to the content area (modeled on the one in `ui/player/menu/MenuSheet.kt`),
so once your `LazyColumn`/`verticalScroll` content is scrolled to the top and the user keeps
pulling down, that motion drives the sheet closed - not just the small drag handle. You don't
wire this up yourself; it falls out of using a normal scrollable for your content.

## Colors

The app's dark theme sets `colorScheme.surface = Color.Black` - literal pure black, not a gray.
Don't use it, or any other `colorScheme.*` token, for sheet chrome; that's the "why is this
popup pure black" bug this file exists to prevent a repeat of. `PopupSheet` instead reuses the
exact grays the player's own menu sheets already use (`ui/player/menu/MenuSheet.kt`), on purpose
- one visual language for every sheet in the app, player included:

- Sheet background (set by the engine, not by you): `MenuSurface` (`#0B0B0C`).
- Elevated rows/cards/buttons sitting directly on that sheet background (option rows, a pinned
  `buttonBar` button, link rows): `MenuRowSurface` (`#161619`), one step lighter so they
  read as raised. Both are public vals in `ui/player/menu/MenuSheet.kt` - import them, don't
  redeclare the hex.
- Small icon badges *nested inside* one of those cards (e.g. the app-icon chip inside Update's
  release row): `MenuSurface` again - one step back down, for contrast against the card around it.
- Text/icon color: don't pass `color =` at all unless you need a dimmer secondary tone - the
  ambient `LocalContentColor` (white-ish, set at the app root) already reads fine against both
  of the above.

Don't hardcode a new hex for sheet chrome, and don't reach for `MaterialTheme.colorScheme.*`
tokens for it either - both routes have produced an inconsistent-popup bug before. The one
legitimate exception is a fixed light "badge" behind a third-party brand mark (e.g. the
Ko-fi/BuyMeACoffee icons in `DonateSheet.kt`), which needs to stay legible regardless of app
theme - comment it when you do that, like `DonateSheet.kt` does.

Buttons in general should be compact - `~46dp` tall, not the `54-56dp` the pre-unification
popups used. "Thick" pinned buttons were a specific complaint; don't reintroduce it.

## Physics, not timed curves

Every bit of motion in `PopupSheet.kt` - entrance, dismiss, drag release - comes from one shared
`spring()` (`PopupSpring`), not `tween()`/easing curves. If you add new motion to this file,
reuse `PopupSpring` rather than introducing a `tween` - that inconsistency (some things springy,
some things eased) was a specific complaint, as was the spring itself initially being tuned too
soft/slow (fixed: `DampingRatioNoBouncy` + `StiffnessMedium` - crisp, no overshoot).

**While actively dragging, never write the position through a suspend call.** This file went
through two broken versions before landing here, both worth knowing about so they don't come
back:

1. A plain `scope.launch { animatable.snapTo(...) }` from inside `draggable`'s `onDelta` or a
   `NestedScrollConnection`'s `onPreScroll`/`onPostScroll`. A plain `launch` **posts** the
   coroutine to run on the dispatcher's next turn instead of now - those callbacks fire many
   times per frame while dragging, so the launches piled up and drained in bursts a frame later,
   which read as the sheet teleporting instead of tracking the finger.
2. The same call with `CoroutineStart.UNDISPATCHED` added (which runs synchronously up to the
   first real suspension point - the fix `ui/component/BottomSheet.kt`'s own main drag sheet
   uses, and a reasonable thing to reach for). Better, but `Animatable.snapTo` still enters the
   Animatable's internal mutex on every single call, and gesture callbacks can fire faster than
   that's free - this was still visibly not smooth.

The actual fix: **there is no `Animatable` on the hot path at all.** The live position
(`Frame.offset`, which each `PopupSheetFrame` owns and the host and the frames behind derive from) is a plain `mutableFloatStateOf`, written synchronously
(`frame.offset = newValue`, not `scope.launch { ... }`) from every drag/scroll callback - no
coroutine, no suspend call, no mutex, every single update applies immediately. A suspend call
(the top-level `animate(initialValue, targetValue, animationSpec) { value, _ -> ... }` helper)
only ever appears for a *settle/entrance transition* - one call when a drag releases, one when a
frame mounts - never once per pointer event. This is exactly `ui/player/menu/MenuSheet.kt`'s
(`MenuSheetSurface`) pattern, copied intentionally: `offsetPx` there is a plain
`mutableFloatStateOf` for the same reason. If you're writing new drag-driven motion anywhere in
this app, copy this shape, not an `Animatable` for the live value - regardless of
`CoroutineStart`, a suspend call in a hot gesture callback is the wrong tool.

The shared spring (`PopupSpring`) is deliberately unhurried (`DampingRatioNoBouncy` +
`StiffnessLow`) - it was tuned snappier at one point and that read as rushed; "smooth and a
little slow" is the target feel, not "fast." If you retune it, keep it slow-and-settled rather
than snappy.

## Continuing into another sheet: push the stack, the one behind recedes

`PopupSheetState` is a small stack (`show()` pushes, `dismiss()` pops). Each frame keeps **one
composition for its whole life** (one keyed slot per frame in `PopupSheetStack`): it is never
rebuilt as a separate "peek" copy when covered, and never remounted with a fresh slide-up entrance
when uncovered. Both of those were tried; they threw away the covered sheet's state (scroll,
in-flight downloads) and looked like a new window opening.

When a frame is covered, it **recedes in place**, matching the reference mock:

- It scales down to `RecedeScale` (0.9, about its top edge) - it never zooms in.
- It rises just enough that a `PeekHeight` (24dp) strip of its top edge - its handle - shows above
  the sheet in front, tinted from `MenuSurface` toward `MenuRowSurface` so the strip reads as a
  separate layer.
- The new sheet opens **at least as tall as the one it covers** (1:1 - `Frame.minHeightPx`,
  captured at push time). Its button bar stays pinned to the bottom and the extra space sits
  above it. This is what keeps the covered sheet's content hidden. The previous "handle-only peek"
  assumed the front sheet was always the taller one, so Donate (short) over the app-version sheet
  (tall) showed the whole upper half of the version sheet. Don't remove the 1:1 rule.
- Sheets further back fade out; only one ever peeks.

All of this is derived at draw time from the `offset` of the frame in front, so the covered sheet
tracks that sheet's entrance, exit and drag exactly, with no animation of its own to fall out of
step. Popping is the same motion in reverse, and the frame behind becomes interactive again
without a remount. Covered frames are inert (no drag/back/scroll). Tapping the peeking strip
dismisses the front sheet, the same as tapping the backdrop. The front sheet is a touch target over
its whole area, so taps on empty patches no longer fall through and close things.

`dismiss()` always animates the front frame closed *before* popping it, however it was triggered:
drag, fling, back, backdrop, peek tap, or a button calling `state.dismiss()`. `dismiss()` only
flags the frame (`closing`); the frame plays its own exit and removes itself. Don't mutate
`state.frames` from outside `PopupSheet.kt`.

To lock the current sheet temporarily (e.g. while a download runs), call `state.setLocked(true/false)`.
**Don't** re-`show()` the same content with `locked = true`: that pushes a duplicate frame with fresh
state (the old update-detail sheet did this and lost its download progress).

`UpdateSheet.kt` is the reference: "Donate me!" calls `popup.showDonateSheet()` and "Update" calls
`popup.showUpdateDetailSheet(...)`, both pushing over the version sheet. Donate's "Done" and the
detail sheet's back row call `dismiss()` to pop back to it. Use this pattern (`fun
PopupSheetState.showWhatever(...)` extensions) whenever one popup leads to another, rather than
giving the second screen its own state.

## The `fullBleed` exception

Exactly one call site uses `fullBleed = true` today: the Monochrome onboarding flow
(`AdvancedAudioSettingsScreen.kt` -> `MonochromeOnboardingSheet.kt`). It was explicitly left
out of this migration - it draws its own rounded-top card, pill handle, and background, and
should keep looking exactly as it does. Don't use this as precedent to opt new popups out of
the shared chrome; if you think you need `fullBleed`, ask first instead of defaulting to it.

## Player sub-sheets (`MenuSheetSurface`)

`ui/player/menu/*` (`MenuSheetSurface` and its six callers: `PlayerActionMenu` - the chevron
"other menu", `PlaybackSpeedSheet`, `SleepTimerSheet`, `DeviceOutputSheet`, `LyricsToolSheets`,
plus whatever else gets added there) is a **separate, intentionally separate** sheet primitive
for sheets shown on top of the already-expanded, already-full-screen player. It is not folded
into `PopupSheet` and does not touch `PopupSheetHost` in any way - its drag physics (half/full
detent, resistance when locked, reveal-driven cross-fade) are well-tuned and specific to sitting
over the player, and it keeps its own local backdrop (`Color.Black @ 0.6 alpha`), scoped to just
the player.

A version of this file briefly had `MenuSheetSurface` register with `PopupSheetHost` so the app
root would also dim/recede behind it. That was reverted - these sheets are mounted *inside* the
player's own composable tree, which is itself inside the content `PopupSheetHost` zooms, so
there was no way to dim/recede "the rest of the app" without it reading as "the whole screen
darkens" the moment you open a player menu, which is not what anyone wants. If you're tempted to
reconnect the two, don't - it was tried and explicitly asked to be undone.

**They do get the same stacking, natively.** `MenuSheet.kt` has its own `MenuSheetStack`
(`rememberMenuSheetStack()` + `LocalMenuSheetStack`), provided by `PlayerActionMenu` around itself and
every sheet it opens. A sheet opened over another (action menu -> "Change device output" / speed /
sleep timer / a lyrics tool -> the language picker) gives the same result as `PopupSheet`. The
covered sheet scales to 0.9, rises until a 32dp strip with its handle peeks out, and gets the row-gray
tint. The new sheet opens at least as tall as the visible part of the one it covers (1:1), and it
skips its own black scrim so the peek isn't blacked out. Sheets two deep fade out. The peek is
32dp, not 24, because this sheet's handle pill sits lower. A new player sheet that can open another
must be composed inside that provider.

Inside a `MenuSheetSurface`'s content, a button that finishes the sheet's job ("Save", "Set timer",
picking a language) must call `LocalMenuSheetClose.current()`, **not** the `onDismiss` it was handed.
`onDismiss` drops the sheet from composition on the spot, with no slide-out. When that happens the
stack only animates the sheet behind back into place as a fallback.

On the lyrics page there's no options button in the header: the lyrics tools are reached through
the chevron menu's "Other menu" item, which opens this menu with `fromLyrics = true` whenever the
lyrics are up. The lyrics-page chevron is the same control as the song-info row's, gliding between
the two spots with the lyrics transition, with its own rect (`lyricsChevronRect`) for anchoring the menu.

Hoisting these sheets to true root-level `PopupSheetState`s (so they'd get the same recede/stack
treatment as Donate/the update sheet) would need restructuring how the player mounts them
entirely, not a one-line registration call. Possible future work; don't attempt it as a drive-by.

## Plain dialogs

- **Plain dialogs** (`DefaultDialog`/`ActionPromptDialog`/`AlertDialog`/`ListDialog` in
  `ui/component/Dialog.kt`) - centered modal dialogs are a different UI pattern from a bottom
  sheet and were out of scope for this pass. If you find yourself hand-rolling a centered
  `Dialog + Surface` card (the way the old `RestrictionPopup`/`UpdateInfoDialog` did), use
  `DefaultDialog`/`ActionPromptDialog` from `Dialog.kt` instead of a new one - that file is
  still the canonical place for centered dialogs.

## What was removed, not migrated

The "Reset app data?" confirmation existed as three independent copies (`GeneralSettingsScreen`,
`SettingsScreen`, `OtherSettingsScreen`) and the "this feature is under development" restriction
popup (`RestrictionPopup.kt`, gating two placeholder "Interconnectivity" cards in
`SettingsScreen.kt`) was never going anywhere - both were removed outright rather than unified.
If either comes back, build it once, as a `PopupSheet`, not as a new copy.

## Checklist before adding a new popup

1. Is this a bottom sheet / overlay? Use `PopupSheet` via `LocalMenuState` /
   `LocalBottomSheetPageState` / `LocalZoomOutOverlayState`, never a new `ModalBottomSheet` or
   hand-rolled `Box` with a drag handle.
2. Is this a centered modal (confirm/cancel, a short prompt)? Use `DefaultDialog` /
   `ActionPromptDialog` from `Dialog.kt`, never a new `Dialog { Surface { ... } }`.
3. Decide `locked` and `buttonBar` for your case; leave `fullBleed` false unless you have a
   reason as strong as the onboarding flow's.
4. Use `MenuSurface`/`MenuRowSurface` from `ui/player/menu/MenuSheet.kt`. No `MaterialTheme.colorScheme.*`
   tokens and no new hardcoded hex for sheet chrome.
5. Does your popup lead to another one? Push it on the same state (`popup.showX()` from inside the
   content) and go back with `dismiss()` - never re-`show()` the previous screen, and never
   re-`show()` the same content just to toggle `locked` (use `setLocked`). Don't touch the stack
   geometry (`RecedeScale`, `PeekHeight`, the 1:1 min-height rule) - it's matched to the user's
   reference mock and was confirmed on device.
6. If what you're building doesn't fit either primitive, that's a signal to extend
   `PopupSheet`/`Dialog.kt`, not to start a fifth implementation - update this file when you do.
