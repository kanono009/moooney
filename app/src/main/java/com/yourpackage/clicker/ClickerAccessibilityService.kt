package com.yourpackage.clicker

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.GestureDescription

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
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
