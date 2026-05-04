package com.cnanfc.xcanplayer

import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class ImmersiveModeHelper(private val window: Window, private val decorView: View) {
    init {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    fun applyImmersiveMode(isImmersive: Boolean) {
        val controller = WindowCompat.getInsetsController(window, decorView)
        if (isImmersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.statusBarColor = Color.parseColor("#B3000000")
            window.navigationBarColor = Color.parseColor("#B3000000")
        }
    }
}