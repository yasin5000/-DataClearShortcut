package com.example.dataclear

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Tinta automation job chalay:
 *  1) Settings > App info > Storage > Clear data > OK  (clear app data)
 *  2) Settings > (System/Additional settings) > Reset options > Reset network
 *     settings > Reset settings  (network reset — sheshe lock/PIN cheye jodi
 *     asheo, sheita user nijei diye confirm korbe)
 *  3) Kono app-er (jemon Chrome) file-picker e age theke bache rakha
 *     "DataClearUpload.jpg" khuje click kore image auto-select kore dey
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

        fun startNetworkReset() {
            instance?.beginNetworkReset()
        }

        fun startImageUpload() {
            instance?.beginImageUpload()
        }

        private const val MODE_CLEAR = 0
        private const val MODE_RESET_NETWORK = 1
        private const val MODE_UPLOAD_IMAGE = 2

        private const val STEP_STORAGE = 0
        private const val STEP_CLEAR = 1
        private const val STEP_CONFIRM = 2

        private const val RESET_STEP_ENTRY = 0
        private const val RESET_STEP_TARGET = 1

        private const val TIMEOUT_MS = 15_000L
        private const val RESET_TIMEOUT_MS = 90_000L
        private const val UPLOAD_TIMEOUT_MS = 30_000L
        private const val TICK_MS = 350L

        const val UPLOAD_FILE_NAME = "DataClearUpload.jpg"

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

        // Reset network flow-er final action ("Reset network settings" screen-er
        // nicher "Reset settings" button/link)
        private val RESET_FINAL_WORDS = listOf(
            "reset settings", "reset network settings", "reset wi-fi, mobile & bluetooth",
            "reset wi-fi, mobile and bluetooth", "সেটিংস রিসেট", "নেটওয়ার্ক রিসেট"
        )

        // Reset options menu-te dhukar entry ("Reset network settings" row)
        private val RESET_TARGET_WORDS = listOf(
            "reset network settings", "reset wi-fi, mobile & bluetooth",
            "reset wi-fi, mobile and bluetooth", "নেটওয়ার্ক সেটিংস রিসেট"
        )

        // "Reset options" / "Backup & reset" moto submenu
        private val RESET_OPTIONS_WORDS = listOf(
            "reset options", "backup & reset", "backup and reset",
            "রিসেট অপশন", "ব্যাকআপ ও রিসেট"
        )

        // Root Settings-e reset-er dike jawar entry point
        private val RESET_ENTRY_WORDS = listOf(
            "system", "additional settings", "general management",
            "system management", "about phone", "সিস্টেম", "অতিরিক্ত সেটিংস"
        )

        // File-picker-e "Files"/"Browse"/"Downloads" tab-e switch korar jonno
        private val UPLOAD_TAB_WORDS = listOf(
            "files", "browse", "browse other apps", "downloads", "download",
            "internal storage", "show roots", "show internal storage",
            "ফাইল", "ডাউনলোড"
        )

        // File select howar por "Select"/"Open"/"Done" moto confirm button
        private val UPLOAD_CONFIRM_WORDS = listOf(
            "select", "open", "done", "use this photo", "add", "attach", "ok",
            "নির্বাচন", "ওপেন"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pkg: String? = null
    private var mode = MODE_CLEAR
    private var step = STEP_STORAGE
    private var resetStep = RESET_STEP_ENTRY
    private var uploadFileClicked = false
    private var deadline = 0L
    private var visitedStorage = false
    private var jobActive = false

    private val tick = object : Runnable {
        override fun run() {
            if (!jobActive) return
            if (System.currentTimeMillis() > deadline) {
                val msg = when (mode) {
                    MODE_RESET_NETWORK -> "Hoy nai. Manually Settings > Reset options theke koro."
                    MODE_UPLOAD_IMAGE -> "Image auto-select hoy nai. Manually file-picker theke '$UPLOAD_FILE_NAME' select koro."
                    else -> "Hoy nai. Manually Storage > Clear data chapo."
                }
                finishJob(false, msg)
                return
            }
            val root = rootInActiveWindow
            // Kono package filter na kore je window e ase sheta i process kori,
            // karon kichu phone-e confirm dialog ta alada package (jemon MIUI
            // security center) theke ashe, "settings" word thake na.
            if (root != null) {
                when (mode) {
                    MODE_RESET_NETWORK -> processReset(root)
                    MODE_UPLOAD_IMAGE -> processUpload(root)
                    else -> process(root)
                }
            }
            if (jobActive) handler.postDelayed(this, TICK_MS)
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
        mode = MODE_CLEAR
        pkg = p
        jobActive = true
        step = STEP_STORAGE
        visitedStorage = false
        deadline = System.currentTimeMillis() + TIMEOUT_MS
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 700)
    }

    fun beginNetworkReset() {
        mode = MODE_RESET_NETWORK
        pkg = null
        jobActive = true
        resetStep = RESET_STEP_ENTRY
        deadline = System.currentTimeMillis() + RESET_TIMEOUT_MS
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 700)
    }

    fun beginImageUpload() {
        mode = MODE_UPLOAD_IMAGE
        pkg = null
        jobActive = true
        uploadFileClicked = false
        deadline = System.currentTimeMillis() + UPLOAD_TIMEOUT_MS
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 400)
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

    private fun processReset(root: AccessibilityNodeInfo) {
        // Shesh button ("Reset settings") pele shesh e chapo, screen jekhane thakuk na keno
        if (clickWords(root, RESET_FINAL_WORDS, exact = true)) {
            finishJob(true, "Reset shuru hoyeche. Lock/PIN cheye thakle nijei diye confirm koro.")
            return
        }

        // "Reset network settings" entry row (Reset options menu-te)
        if (clickWords(root, RESET_TARGET_WORDS, exact = false)) {
            resetStep = RESET_STEP_TARGET
            return
        }

        if (clickWords(root, RESET_OPTIONS_WORDS, exact = true)) {
            return
        }

        if (resetStep == RESET_STEP_ENTRY && clickWords(root, RESET_ENTRY_WORDS, exact = true)) {
            return
        }
    }

    private fun processUpload(root: AccessibilityNodeInfo) {
        if (uploadFileClicked) {
            if (clickWords(root, UPLOAD_CONFIRM_WORDS, exact = true)) {
                finishJob(true, "Image ta select hoye geche")
            }
            return
        }

        if (clickWords(root, listOf(UPLOAD_FILE_NAME), exact = true)) {
            uploadFileClicked = true
            return
        }

        clickWords(root, UPLOAD_TAB_WORDS, exact = false)
    }

    // Onek button/thumbnail-e (jemon image picker-er photo grid) visible "text"
    // thake na - label ta sudhu "content description"-e thake (screen-e dekha
    // jay na, khali accessibility-r jonno). Age sudhu n.text dekhto, tai oi
    // node-gulo shob shomoy skip hoye jeto - eijonyoi image select hoto na.
    private fun nodeLabel(n: AccessibilityNodeInfo): String? =
        (n.text?.toString() ?: n.contentDescription?.toString())?.trim()?.lowercase()

    private fun clickWords(root: AccessibilityNodeInfo, words: List<String>, exact: Boolean): Boolean {
        val lowerWords = words.map { it.lowercase() }
        for (n in findNodes(root, words)) {
            val t = nodeLabel(n) ?: continue
            // "exact" e-o full equality chara, "filename, image, 245 KB" moto
            // extra info jog kora thakle shetao match hobe (starts-with check).
            val matches = if (exact) {
                lowerWords.any { w -> t == w || t.startsWith("$w,") || t.startsWith("$w ") }
            } else {
                lowerWords.any { t.contains(it) }
            }
            if (!matches) continue
            val c = clickableAncestor(n) ?: continue
            if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun clickClearData(root: AccessibilityNodeInfo): Boolean {
        for (n in findNodes(root, CLEAR_WORDS)) {
            val t = nodeLabel(n) ?: continue
            if (t.contains("cache")) continue
            if (CLEAR_WORDS.none { t.contains(it.lowercase()) }) continue
            val c = clickableAncestor(n) ?: continue
            if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun clickStorageRow(root: AccessibilityNodeInfo): Boolean {
        for (n in findNodes(root, STORAGE_WORDS)) {
            val t = nodeLabel(n) ?: continue
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
            val t = nodeLabel(n) ?: continue
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
        val wasClearMode = mode == MODE_CLEAR
        pkg = null
        jobActive = false
        handler.removeCallbacks(tick)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        if (ok && wasClearMode) {
            // Settings theke back diye amader app-e phire jao (shudhu Clear Data flow-e)
            val backs = if (visitedStorage) 2 else 1
            for (i in 1..backs) {
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 500L * i)
            }
        }
    }
}
