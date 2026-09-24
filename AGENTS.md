# World Gallery — agent handoff

PICO Spatial SDK 6.1.9 app (Kotlin / Compose / Spatial ECS), package `com.worldgallery.spatial`, scaffolded with `pico-cli project create --template planar` and extended with a second Planar `WindowContainer` (art frame) and a Full `Stage` (immersive viewer). Source of truth for requirements: `/Users/bytedance/Documents/Codex/2026-09-11/artworks-canvas-3d-2d/outputs/World-Gallery-PRD.md` (v0.4) and the Figma file "World Gallery · PICO Spatial UI UX" (node 8-807 home, 8-1759 Full Space).

## Build / run

```bash
export JAVA_HOME=~/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk   # pico-cli app install hangs on this host
pico-cli app launch com.worldgallery.spatial
python3 deliverables/drive.py start:essentials wait=3 shot=viewer state      # debug channel + screenshots
```

## Key files

- `Main.kt` — `DefaultWindowContainer` + `WindowContainer("WorldGalleryFrame")` + `Stage("WorldGalleryImmersive")`.
- `app/GalleryViewModel.kt` — singleton app state shared by all three containers; `debug(cmd, arg)` is the adb harness.
- `ui/GalleryApp.kt` — main window root: TitleBar, TabBar, viewing Toolbar, info Subwindow, immersive hint Sheet, frame-replace AlertDialog.
- `ui/ViewerPage.kt` — painting zoom/pan surface and sculpture SpatialView (model in the planar window).
- `ui/FrameWindow.kt` — art frame window with lifecycle-driven slideshow pausing.
- `ui/ImmersiveStage.kt` + `scene/ArtworkEntities.kt` — Stage scene (sky sphere, floor, lights, textured painting plane or model with a material-opacity cross-fade between paintings, invisible prev/next pads, and an `AttachmentPanel` control strip: prev / next / back to gallery). Leaving Full Space restores the minimized windows *before* `closeStage()`; see release-notes.md (2026-09-22) for why nothing works after the Stage is gone., window minimize/restore around Full Space.
- `scene/ModelLoader.kt` — on-demand model download / side-load with verify + cache.
- `data/Catalog.kt` — published content only; `data/Store.kt` — SharedPreferences persistence.

## Verified on PICO Emulator 6.1 (2026-09-14)

Home, collection, viewer zoom + detail-limit hint, info subwindow, favorites, frame fixed / loop / reading pause, sculpture Load 3D state machine with a side-loaded USDZ, Full Space entry (dark scene + painting, windows minimized), next/zoom inside the Stage, exit back to Shared Space. See `deliverables/screenshots/` and `deliverables/release-notes.md`.

## Emulator findings worth knowing

- `openStage` returns `OpenStageResult.NotAllowed` unless this app's window holds focus (another app in front → denied). The toolbar path always runs from the focused window; the debug path needs the window in front first (`monkey -p com.worldgallery.spatial -c android.intent.category.LAUNCHER 1`, or `am force-stop` whatever holds focus).
- `SpatialNavigator.restoreWindowContainer` fails once the Stage is closing (ON_PAUSE/ON_STOP/dispose all return false on the emulator), so `GalleryViewModel.onImmersiveClosed()` reopens the main window with the package launch intent; that path is verified.
- The emulator's `adb` becomes very slow while a Stage renders or a large model loads; use generous timeouts (`deliverables/drive.py`) and avoid `pico-cli app install` (hangs) — plain `adb install -r` works.
- No network in the emulator: on-demand downloads fail with "unknown host"; side-load models into `files/models/` (`adb shell "cat /sdcard/Download/x.usdz | run-as com.worldgallery.spatial sh -c 'cat > files/models/venus.usdz'"`).

## Known gaps / next tasks

- Sketchfab (SMK) models cannot be downloaded without a signed-in account; place files under `files/models/<id>.glb`.
- The emulator has no network, so sculpture downloads were not exercised end to end; the loader was verified with a local `file://` source. (The Met "scanned version" candidate for Wheat Field was removed on 2026-09-22 at the user's request.)
- Hand / controller gestures (drag, pinch) were exercised through the debug channel only; verify on a device.
- Spatial anchors / multi-instance placement (P1/P2) are not implemented.
- Not yet verified: a 3D model inside the Full Space Stage (emulator hung after loading the 28 MB test model); the 2D painting path in the Stage is verified.
