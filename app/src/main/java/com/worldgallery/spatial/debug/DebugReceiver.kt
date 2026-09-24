package com.worldgallery.spatial.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.containers.closeWindowContainer
import com.pico.spatial.ui.platform.containers.openStage
import com.pico.spatial.ui.platform.containers.openWindowContainer
import com.worldgallery.spatial.app.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Test harness for the PICO Emulator, where spatial input cannot be scripted over adb:
 *
 *   adb shell am broadcast -a com.worldgallery.spatial.DEBUG --es cmd start --es arg essentials
 *   adb shell am broadcast -a com.worldgallery.spatial.DEBUG --es cmd next
 *   adb shell am broadcast -a com.worldgallery.spatial.DEBUG --es cmd immersive
 *
 * Every command goes through the same ViewModel paths as real input; see GalleryViewModel.debug().
 */
class DebugReceiver(private val vm: GalleryViewModel) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val arg = intent.getStringExtra("arg") ?: ""
        val app = context.applicationContext
        val result = when (cmd) {
            "immersive" -> {
                MainScope().launch(Dispatchers.Main) {
                    runCatching { app.openStage(GalleryViewModel.STAGE_ID, StageStyle.Full) }
                        .onSuccess { Log.i(TAG, "openStage → $it") }
                        .onFailure { Log.e(TAG, "openStage failed", it) }
                }
                "openStage requested"
            }
            "exitImmersive" -> {
                MainScope().launch(Dispatchers.Main) {
                    runCatching { closeStage() }.onFailure { Log.e(TAG, "closeStage failed", it) }
                }
                "closeStage requested"
            }
            "openFrame" -> { runCatching { app.openWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }.onFailure { Log.e(TAG, "openFrame failed", it) }; "openFrame requested" }
            "closeFrame" -> { runCatching { app.closeWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }.onFailure { Log.e(TAG, "closeFrame failed", it) }; "closeFrame requested" }
            else -> vm.debug(cmd, arg)
        }
        Log.i(TAG, "cmd=$cmd arg=$arg → $result")
    }

    companion object {
        private const val TAG = "WorldGallery.Dbg"
        const val ACTION = "com.worldgallery.spatial.DEBUG"
        fun register(context: Context, vm: GalleryViewModel): DebugReceiver {
            val r = DebugReceiver(vm)
            context.registerReceiver(r, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
            return r
        }
    }
}
