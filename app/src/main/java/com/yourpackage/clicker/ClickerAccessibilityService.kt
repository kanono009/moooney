package com.yourpackage.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ClickerAccessibilityService : AccessibilityService() {
    companion object {
        var instance: ClickerAccessibilityService? = null
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
    fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        // FIX: 100ms stroke — long enough for apps to register as a real press
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("CLICKER", "Tap completed at $x, $y")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d("CLICKER", "Tap CANCELLED at $x, $y")
            }
        }, null)
        Log.d("CLICKER", "dispatchGesture queued: $dispatched")
    }
}
