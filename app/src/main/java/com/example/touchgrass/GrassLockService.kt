package com.example.touchgrass

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GrassLockService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Don't lock our own app, system UI, launchers, or the camera
            if (packageName == this.packageName || 
                packageName == "com.android.systemui" || 
                packageName == "com.google.android.apps.nexuslauncher" ||
                packageName.contains("launcher") ||
                packageName.contains("camera")) {
                return
            }

            val prefs = getSharedPreferences("touch_grass_prefs", Context.MODE_PRIVATE)
            val isLocked = prefs.getBoolean("is_locked", true)

            if (isLocked) {
                // Redirect to MainActivity
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {}
}