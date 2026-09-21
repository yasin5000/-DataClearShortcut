package com.example.dataclear

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Settings > App info > Storage > Clear data > OK
 * ei steps-gulo automatic chape. Shudhu MainActivity theke start() call hole kaj kore.
 *
 * Note: button-er lekha bhasha/phone company bhede alada hoy. Kaj na korle
 * nicher word list-e tomar phone-er lekha add koro (ba phone English-e rakho).
 */
class AutoClearService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClearService? = null

        fun start(pkg: String) {
            instance?.begin(pkg)
        }

        private const val STEP_STORAGE = 0
        private const val STEP_CLEAR = 1
        private const val STEP_CONFIRM = 2

        private const val TIMEOUT_MS = 15_000L
        private const val TICK_MS = 350L

        // "Clear data" button
        private val CLEAR_WORDS = listOf(
            "clear data", "clear storage", "clear all data",
            "ডেটা মুছুন", "ডেটা মুছে", "স্টোরেজ মুছুন", "সব ডেটা মুছুন"
        )

        // "Storage & cache" row
        private val STORAGE_WORDS = listOf("storage", "স্টোরেজ")

        // Confirm dialog button (exact match)
        private val CONFIRM_WORDS = listOf(
            "ok", "delete", "clear", "yes", "confirm",
            "ঠিক আছে", "মুছুন", "হ্যাঁ"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pkg: String? = null
    private var step = STEP_STORAGE
    private var deadline = 0L
    private var visitedStorage = false

    private val tick = object : Runnable {
        override fun run() {
            if (pkg == null) return
            if (System.currentTimeMillis() > deadline) {
                finishJob(false, "Hoy nai. Manually Storage > Clear data chapo.")
                return
            }
            val root = rootInActiveWindow
            // Kono package filter na kore je window e ase sheta i process kori,
            // karon kichu phone-e ei confirm dialog ta alada package (jemon MIUI
            // security center) theke ashe, "settings" word thake na.
            if (root != null) {
                process(root)
            }
            if (pkg != null) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun begin(p: String) {
        pkg = p
        step = STEP_STORAGE
        visitedStorage = false
        deadline = System.currentTimeMillis() + TIMEOUT_MS
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 700)
    }

    private fun isSettingsWindow(p: String?): Boolean {
        if (p == null) return false
        return p.contains("settings") || p.contains("packageinstaller") ||
            p.contains("permissioncontroller")
    }

    private fun process(root: AccessibilityNodeInfo) {
        if (step == STEP_CONFIRM) {
            if (clickConfirm(root)) {
                finishJob(true, "Data clear hoyeche")
            }
            return
        }

        // Clear data button dekha gele sheta age chapo (Storage page ba purono Android)
        if (clickClearData(root)) {
            step = STEP_CONFIRM
            return
        }

        if (step == STEP_STORAGE && clickStorageRow(root)) {
            step = STEP_CLEAR
            visitedStorage = true
        }
    }

    private fun clickClearData(root: AccessibilityNodeInfo): Boolean {
        for (n in findNodes(root, CLEAR_WORDS)) {
            val t = n.text?.toString()?.lowercase() ?: continue
            if (t.contains("cache")) continue
            if (CLEAR_WORDS.none { t.contains(it.lowercase()) }) continue
            val c = clickableAncestor(n) ?: continue
            if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun clickStorageRow(root: AccessibilityNodeInfo): Boolean {
        for (n in findNodes(root, STORAGE_WORDS)) {
            val t = n.text?.toString()?.lowercase() ?: continue
            if (t.contains("clear") || t.contains("মুছ")) continue
            val c = clickableAncestor(n) ?: continue
            if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun clickConfirm(root: AccessibilityNodeInfo): Boolean {
        val b1 = root.findAccessibilityNodeInfosByViewId("android:id/button1")
            ?.firstOrNull { it.isEnabled }
        if (b1 != null) return b1.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        for (n in findNodes(root, CONFIRM_WORDS)) {
            val t = n.text?.toString()?.trim()?.lowercase() ?: continue
            if (t !in CONFIRM_WORDS) continue
            val c = clickableAncestor(n) ?: continue
            if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun findNodes(root: AccessibilityNodeInfo, words: List<String>): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        for (w in words) {
            root.findAccessibilityNodeInfosByText(w)?.let { out.addAll(it) }
        }
        return out
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = n
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun finishJob(ok: Boolean, msg: String) {
        pkg = null
        handler.removeCallbacks(tick)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        if (ok) {
            // Settings theke back diye amader app-e phire jao
            val backs = if (visitedStorage) 2 else 1
            for (i in 1..backs) {
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 500L * i)
            }
        }
    }
}
