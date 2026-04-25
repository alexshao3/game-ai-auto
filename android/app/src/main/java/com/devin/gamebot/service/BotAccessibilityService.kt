package com.devin.gamebot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlin.random.Random

/**
 * Accessibility service that dispatches synthetic taps and swipes.
 *
 * Phase 0 surface area is intentionally small: a static [INSTANCE] reference
 * lets the app trigger gestures without complex IPC. We add small
 * randomized jitter and timing variation to avoid trivial bot fingerprints.
 */
class BotAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 0: we don't act on events. View-tree based detection won't work
        // for Unity games anyway. Reserved for popup-heuristic fallbacks.
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        INSTANCE = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        INSTANCE = null
        return super.onUnbind(intent)
    }

    fun tap(x: Float, y: Float, jitterPx: Float = 4f, durationMs: Long = 80L) {
        val jx = x + Random.nextFloat() * 2f * jitterPx - jitterPx
        val jy = y + Random.nextFloat() * 2f * jitterPx - jitterPx
        val path = Path().apply { moveTo(jx, jy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L) {
        val path = Path().apply {
            moveTo(x1, y1)
            // Slight curve to look more human-like
            val midX = (x1 + x2) / 2f + Random.nextFloat() * 20f - 10f
            val midY = (y1 + y2) / 2f + Random.nextFloat() * 20f - 10f
            quadTo(midX, midY, x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val TAG = "BotA11yService"

        @Volatile
        var INSTANCE: BotAccessibilityService? = null
            private set
    }
}
