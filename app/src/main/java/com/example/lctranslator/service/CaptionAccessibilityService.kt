package com.example.lctranslator.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lctranslator.Prefs

class CaptionAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_NEW_CAPTION = "com.example.lctranslator.NEW_CAPTION"
        const val EXTRA_CAPTION = "caption"
        private val CAPTION_ID_HINTS = listOf(
            "caption_text", "captionText", "subtitle_text", "live_caption_text"
        )
    }

    private var lastText = ""
    private var lastSentAt = 0L
    private val debounceMs = 700L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            packageNames = arrayOf(Prefs.captionPkg(this@CaptionAccessibilityService))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastSentAt < debounceMs) return
        val text = findCaptionText(rootInActiveWindow) ?: return
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed == lastText) return
        lastText = trimmed
        lastSentAt = now
        sendBroadcast(Intent(ACTION_NEW_CAPTION).apply {
            putExtra(EXTRA_CAPTION, trimmed)
            setPackage(packageName)
        })
    }

    private fun findCaptionText(node: AccessibilityNodeInfo?): String? {
        node ?: return null
        val id = node.viewIdResourceName ?: ""
        if (CAPTION_ID_HINTS.any { id.contains(it, ignoreCase = true) }) {
            val t = node.text?.toString()
            if (!t.isNullOrBlank()) return t
        }
        for (i in 0 until node.childCount) {
            val found = findCaptionText(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {}
}
