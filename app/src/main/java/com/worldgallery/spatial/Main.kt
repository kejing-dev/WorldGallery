package com.worldgallery.spatial

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Immersion
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.platform.resize.CaptionBarType
import com.pico.spatial.ui.platform.resize.ContainerResizeRestriction
import com.pico.spatial.ui.platform.resize.ContainerResizeType
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.ui.FrameWindow
import com.worldgallery.spatial.ui.GalleryTheme
import com.worldgallery.spatial.ui.GalleryWindow
import com.worldgallery.spatial.ui.ImmersiveStage

/**
 * World Gallery: one app, three spatial containers (PRD 1 / 5.6 / 5.7).
 *
 * - [DefaultWindowContainer] (Planar, manifest id `WorldGalleryMain`): browsing + single-work viewing.
 * - [WindowContainer] `WorldGalleryFrame` (Planar, no glass): the art frame, opened by the user only.
 * - [Stage] `WorldGalleryImmersive` (Full, immersion pinned at 100): the no-UI Full Space viewer.
 *   Opening it switches the app to Full Space; closing it (system Back) returns to Shared Space.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            GalleryTheme { GalleryWindow() }
        }

        WindowContainer(
            id = GalleryViewModel.FRAME_WINDOW_ID,
            form = Form.Planar,
            // ContentSize honours BOTH the min and max of Modifier.windowConstraints, which is what lets the
            // frame's own "smaller / larger" buttons drive the window size from app state.
            resizeType = ContainerResizeType.ContentSize,
            defaultSize = WindowContainerSize(width = 1094.dp, height = 740.dp),
            defaultResizeRestriction = ContainerResizeRestriction.UniformResizable,
            defaultCaptionBarType = CaptionBarType.AutomaticHide,
            enableMaterialBackground = false,
        ) {
            GalleryTheme {
                val vm = GalleryViewModel.get(LocalContext.current)
                val (w, h) = vm.frameSize
                FrameWindow(Modifier.windowConstraints(width = w.dp, height = h.dp))
            }
        }

        Stage(
            id = GalleryViewModel.STAGE_ID,
            immersion = Immersion(default = 100, min = 100, max = 100),
        ) {
            GalleryTheme { ImmersiveStage() }
        }
    }
