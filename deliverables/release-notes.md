# World Gallery · dev build 0.1 (2026-09-14)

Built with pico-cli 0.5.0, PICO Spatial SDK 6.1.9 (BOM), AGP 8.13.2, Kotlin 2.1.20; verified on PICO Emulator 6.1 (`Pico_Emulator_6_1`, PICO OS 6.1.0).

## What is in the build

- Main planar window (1134 × 748 dp): home with "continue viewing", featured collections, collection grid, single-work viewer, favorites, search, one information subwindow.
- Art frame window: fixed painting or slideshow with interval / order settings; pauses while reading, while minimized and during Full Space.
- Full Space viewer (Stage, Full style): dark gradient environment, floor, key/fill lights, one artwork (4096-px textured plane or loaded model); invisible tap pads left/right for previous/next; drag pans or rotates; pinch zooms; system Back exits.
- Content: 4 open-access paintings bundled; 3 sculpture entries load models on demand or from side-loaded files.
- Debug broadcast channel for emulator testing (`deliverables/drive.py`, `deliverables/dbg.sh`).

## Screenshots (deliverables/screenshots)

| File | State |
| --- | --- |
| 01-home | English home with TabBar |
| 02-viewer-bedroom · 03-viewer-zoom · 04-viewer-info | viewer, 225 % zoom with detail-limit hint, info subwindow |
| 05-viewer-wheat | next work, favorite on |
| 06/07-frame-* · 08-frame-info | frame fixed, loop (1 / 4 · every 5 min), reading pause |
| 09/10-sculpture-* | sculpture collection, Load 3D preview |
| 14-immersive-bedroom · 15-immersive-wheat · 16-immersive-zoom | Full Space |
| 17-back-shared | back in Shared Space |
| 19–21-sculpture-* | side-loaded test model attempt (chess.usdz stands in for the Venus scan); see note below |
| 22-home-zh | Chinese home |

## Fixes from the 2026-09-22 emulator session

Found by reading the code against the PRD and the 6.1.9 API docs, then confirmed on the emulator:

1. **Full Space rebuilt the artwork on every download tick.** `ImmersiveStage` used the whole model-state map as a `LaunchedEffect` key, so each `Downloading(received,total)` update tore down and re-created the scene's artwork. Now keyed on whether the current work's model is ready.
2. **A paused Stage was treated as a closed Stage.** `ON_PAUSE` / `ON_STOP` fire while the Stage is still open (headset doffed, system overlay); restoring there put the Shared-Space windows back inside Full Space and marked the exit handled. Only `onDispose` now counts as leaving.
3. **Window restore after Full Space is not possible through the navigator.** The 6.1.9 docs state `minimizeWindowContainer` and `restoreWindowContainer` "can only be used when there is a stage open", and there is no `Context` form of restore. Every call at Stage teardown returns false on the emulator. The app relaunches its task with the package launch intent instead, and `stageMinimize on|off` (debug channel) toggles the minimize for comparison.
4. **Full-resolution images were pinned forever.** `Bitmaps.imageCache` was an unbounded `HashMap<String, ImageBitmap>` outside the 160 MB LruCache budget (~53 MB per 4096 px painting). It is now a bounded LruCache, and `onTrimMemory` clears both caches.
5. **The debug broadcast receiver shipped in release builds.** It can open the Stage, open/close the frame window and wipe state, and was registered `RECEIVER_EXPORTED` unconditionally. Now gated on `BuildConfig.DEBUG`.
6. **Sculptures had no tap-to-open info in Shared Space** (PRD 5.5). Added `detectSpatialTapGesture` alongside the drag/scale recognizers, in its own `pointerInput` block.

Design-style verifier: 0 errors, 0 warnings. Unit tests pass. APK rebuilt.

## Round 2 (2026-09-22, user request): scan candidate removed, cross-fade, single-language labels, Full Space return path

1. **"Scanned version (technical candidate)" removed.** The Met scan of Wheat Field (`wheat-scan`), the viewer toggle, `scanMode`, the `scan` debug command and the catalog field are gone; sculptures are the only 3D content.
2. **Cross-fade when switching works.** `AssetImage` keeps the last decoded picture on screen while the next one decodes and cross-fades the two (`Crossfade`, 450 ms; a cut with "reduce motion"), so the viewer and the frame never show a blank mat between works — that blank was the flash. In Full Space the new painting plane fades in over the old one (`UnlitMaterial` `BlendingMode.TRANSPARENT` + opacity, same 450 ms); the old plane is destroyed after the fade.
3. **No mixed languages in one label.** The second-language title under the viewer title and in the info window is gone (`otherTitle` removed; the line now shows the museum only), and the Chinese UI no longer says "精选 Collection", "更换 Collection", "在 Gallery 观赏页", "Load 3D · 加载模型".
4. **A way back from Full Space.** The Stage now has a control strip under the work — previous / n of N / next / "Back to gallery" — built as a `SpatialView` `AttachmentPanel`, scaled ×2.4 (the panel renders at dp scale, ~0.3 m wide otherwise). Leaving is ordered: `restoreWindowContainer` for the gallery (and frame) window **while the Stage is still open**, then `closeStage()`. Verified on the emulator: two consecutive round-trips (`restore before closeStage → main=true`, window count unchanged, screenshots `r7-01/02-after-r*`), the frame round-trip (`main=true frame=true`, `r7-04-frame-back`), and the tutorial's close/reopen variant twice (`reopenMode context`, `gallery window back=true after ~1.0 s`).

   What does **not** work, measured, so it is not tried at teardown any more: after the Stage is closed the app is in the background — `restoreWindowContainer` returns false, `openWindowContainer` produces no window, and the package launch intent starts nothing (Android background-activity restriction; the same relaunch from the shell/launcher does work). A Stage the app did not close itself therefore leaves the windows hidden; the safety net closes the hidden windows first so the user's next tap on the app icon yields one gallery window on the same work, not a stacked second one (that stacking was observed: 24 → 67 → 99 window entries). A `BackHandler` is installed for the system Back gesture, but the emulator's `keyevent BACK` never reaches the Stage (nothing closes), so that path is unverified — physical-device check pending.

   Investigation note: every "second Stage entry dies" symptom seen while building this (all reopen modes, ~20 ms after hiding the windows) was a self-inflicted bug — the debug `leaveImmersive` counter stayed > 0 and re-fired `leaveStage()` in each new Stage composition. Requests are consumed once now.

## Art frame UI change (2026-09-22, user request)

The frame control bar dropped the playback transport and the maximize action, and gained a one-tap size control:

- Removed from the bar: previous / play-pause / next, and the expand ("maximize") button.
- Kept: artwork title and status, artwork info, frame settings.
- Added: `−` / `+` around the current size (50 / 70 / 100 / 130 / 160 %), persisted across sessions.
- Previous / pause / next moved to a new "Playback" row at the top of the frame settings panel, so loop control still exists (PRD 5.7) without sitting on a frame you are only looking at.

The size buttons drive the window itself, not the picture inside it: the container is declared `ContainerResizeType.ContentSize` and the content root carries `Modifier.windowConstraints(width, height)`, which the 6.1.9 docs describe as the way to "directly control the size of the WindowContainer". Verified on the emulator at 70 %, 100 % and 160 % — see `screenshots/f-01-frame-default.png`, `f-03-frame-largest.png`, `f-04-frame-small.png`, `f-05-frame-settings.png`.

## Sculpture / Load 3D verification note

The Load 3D state machine (download → verify → cache → Entity load) was exercised with a side-loaded 28 MB `chess.usdz` copied to `files/models/venus.usdz`. With the model parsed on `Dispatchers.IO` the entity loaded in ~19 s (`WorldGallery.Scene: model venus.usdz bounds=… scale=1.05`, no ANR); the earlier main-thread load took minutes and produced an "isn't responding" dialog, which is why the load was moved off the main thread. The emulator's viewpoint had drifted by then (another pre-installed app, `org.chuange.egokit`, kept relaunching and taking focus), so the sculpture screenshots do not show the window; verify the model visually on a device or a fresh emulator session.

## Not verified / limitations

- No network in the emulator: the Met GLB candidate download path was not run; a `file://` side-load exercised the same state machine.
- Sketchfab downloads need a signed-in account; SMK models are not bundled.
- Real hand/controller gestures and the system Back gesture in Full Space need a device check.
- A loaded 3D model inside Full Space was not captured: after the 28 MB test model was loaded and rotated, the emulator stopped processing the app's debug broadcasts (the same session had already produced an ANR and a runtime restart). Paintings in Full Space, prev/next and zoom there, and the exit path are verified.
