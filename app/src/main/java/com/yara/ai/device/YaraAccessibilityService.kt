package com.yara.ai.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class YaraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tapAt(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null)
    }

    fun clickText(needle: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNode(root, needle.lowercase()) ?: return false
        val rect = Rect(); target.getBoundsInScreen(rect)
        return if (target.isClickable) { target.performAction(AccessibilityNodeInfo.ACTION_CLICK); true }
        else { tapAt(rect.centerX().toFloat(), rect.centerY().toFloat()); true }
    }

    private fun findNode(node: AccessibilityNodeInfo, needle: String): AccessibilityNodeInfo? {
        val txt = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        if (txt.lowercase().contains(needle)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { findNode(it, needle)?.let { r -> return r } }
        return null
    }

    fun openApp(appName: String): Boolean {
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage(resolvePackage(pm, appName)) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent); return true
    }

    private fun resolvePackage(pm: PackageManager, name: String): String {
        val lower = name.lowercase()
        val map = mapOf(
            "whatsapp" to "com.whatsapp", "instagram" to "com.instagram.android",
            "youtube" to "com.google.android.youtube", "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps", "chrome" to "com.android.chrome",
            "camera" to "com.android.camera2", "spotify" to "com.spotify.music",
            "phone" to "com.android.dialer", "messages" to "com.google.android.apps.messaging"
        )
        map[lower]?.let { return it }
        for (p in pm.getInstalledApplications(0)) {
            val label = pm.getApplicationLabel(p).toString().lowercase()
            if (label.contains(lower) || p.packageName.lowercase().contains(lower)) return p.packageName
        }
        return lower
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder(); collectText(root, sb); return sb.toString().take(2000)
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it, sb) }
    }

    override fun onDestroy() { instance = null; super.onDestroy() }

    companion object { @Volatile var instance: YaraAccessibilityService? = null; private set }
}
