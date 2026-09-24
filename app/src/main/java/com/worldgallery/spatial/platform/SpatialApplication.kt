package com.worldgallery.spatial.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.BuildConfig
import com.worldgallery.spatial.debug.DebugReceiver
import com.worldgallery.spatial.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // App-scoped state and the adb test harness exist before the first window opens.
        DebugReceiver.register(this, GalleryViewModel.get(this))
        launch(::mainApp)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) com.worldgallery.spatial.scene.Bitmaps.trim()
    }
}
