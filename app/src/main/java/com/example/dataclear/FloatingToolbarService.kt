package com.example.dataclear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Home screen-er upore vashman (floating) ekta choto toolbar dekhay,
 * jate bar bar app khule kaj korte na hoy.
 *
 * Duita button:
 *  - Clear : MainActivity theke selected app-er DATA clear kore (auto-clear flow)
 *  - Open  : Selected app-take shorashori open kore dey
 *
 * Upore ekta grip (::) diye toolbar-take drag kore jekhane khushi rakha jay.
 */
class FloatingToolbarService : Service() {

    companion object {
        @Volatile
        var isRunning = false
        private const val CHANNEL_ID = "floating_toolbar"
        private const val NOTIF_ID = 101
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private var extraContainer: LinearLayout? = null
    private var contentContainer: LinearLayout? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    private fun loadExtraPkgs(): List<String> =
        (prefs.getString(AppPickerActivity.PREF_EXTRA_PKGS, "") ?: "")
            .split(",")
            .filter { it.isNotBlank() }

    private val extraAppsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            rebuildExtraButtons()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundNotif()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
        val filter = IntentFilter(AppPickerActivity.ACTION_EXTRA_APPS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(extraAppsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(extraAppsReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Floating Toolbar", NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }

        val notif: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Floating toolbar chalu ache")
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Floating toolbar chalu ache")
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun addOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.getInt("float_x", 0)
        params.y = prefs.getInt("float_y", dp(200))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.parseColor("#DD212121"))
        }

        // Drag korar jonno grip (upore, choto strip) + minimize/expand toggle
        val grip = TextView(this).apply {
            text = "⋮⋮"
            textSize = 14f
            setTextColor(Color.parseColor("#BDBDBD"))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }
        val toggleBtn = TextView(this).apply {
            text = "▾"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(4))
        }
        val gripRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        gripRow.addView(grip, LinearLayout.LayoutParams(0, -2, 1f))
        gripRow.addView(toggleBtn, LinearLayout.LayoutParams(-2, -2))

        val clearBtn = Button(this).apply {
            text = "Clear"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        val openBtn = Button(this).apply {
            text = "Open"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1565C0"))
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        val plusBtn = Button(this).apply {
            text = "+"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }

        // Open button + "+" button ek row e paashapashi
        val openRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        openRow.addView(
            openBtn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        openRow.addView(
            plusBtn,
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) }
        )

        // Extra select kora app-gulor jonno "Open 1", "Open 2"... button ekhane boshbe
        val extras = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        extraContainer = extras

        // Clear/Open/+/extras shob eki jaygay - eta minimize korle hide hoye jabe
        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentContainer.addView(clearBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        contentContainer.addView(openRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        contentContainer.addView(extras, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        this.contentContainer = contentContainer

        val startCollapsed = prefs.getBoolean("toolbar_collapsed", false)
        contentContainer.visibility = if (startCollapsed) View.GONE else View.VISIBLE
        toggleBtn.text = if (startCollapsed) "▸" else "▾"

        container.addView(gripRow, LinearLayout.LayoutParams(-1, -2))
        container.addView(contentContainer, LinearLayout.LayoutParams(-1, -2))

        // Sudhu grip-e touch kore drag kora jabe, button-e shudhu click kaj korbe
        var downRawX = 0f
        var downRawY = 0f
        var downParamX = 0
        var downParamY = 0

        grip.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamX = params.x
                    downParamY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    params.x = downParamX + dx
                    params.y = downParamY + dy
                    windowManager.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("float_x", params.x).putInt("float_y", params.y).apply()
                    true
                }
                else -> false
            }
        }

        clearBtn.setOnClickListener {
            val pkg = prefs.getString("pkg", null)
            if (pkg != null) ClearHelper.autoClear(this, pkg)
        }
        openBtn.setOnClickListener {
            val pkg = prefs.getString("pkg", null)
            if (pkg != null) ClearHelper.openApp(this, pkg)
        }
        plusBtn.setOnClickListener {
            startActivity(
                Intent(this, AppPickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        toggleBtn.setOnClickListener {
            val nowCollapsed = contentContainer.visibility == View.VISIBLE
            contentContainer.visibility = if (nowCollapsed) View.GONE else View.VISIBLE
            toggleBtn.text = if (nowCollapsed) "▸" else "▾"
            prefs.edit().putBoolean("toolbar_collapsed", nowCollapsed).apply()
            windowManager.updateViewLayout(container, params)
        }

        overlayView = container
        windowManager.addView(container, params)
        rebuildExtraButtons()
    }

    /**
     * "+" diye add kora extra app ba telegram username-gulor jonno
     * "Open N" / "Chat N" button notun kore banay. Long-press korle
     * remove hoye jabe.
     */
    private fun rebuildExtraButtons() {
        val extras = extraContainer ?: return
        extras.removeAllViews()

        val entries = loadExtraPkgs()
        var openCount = 0
        var chatCount = 0

        entries.forEach { entry ->
            val btn = Button(this).apply {
                textSize = 12f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnLongClickListener {
                    removeExtraPkg(entry)
                    true
                }
            }

            if (entry.startsWith("tg:")) {
                chatCount++
                val username = entry.removePrefix("tg:")
                btn.text = "Chat $chatCount"
                btn.setBackgroundColor(Color.parseColor("#0088CC"))
                btn.setOnClickListener { openTelegramChat(username) }
            } else {
                openCount++
                btn.text = "Open $openCount"
                btn.setBackgroundColor(Color.parseColor("#6A1B9A"))
                btn.setOnClickListener { ClearHelper.openApp(this@FloatingToolbarService, entry) }
            }

            extras.addView(
                btn,
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
            )
        }

        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun openTelegramChat(username: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Telegram app deep-link handle na korle, browser/t.me fallback
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$username"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun removeExtraPkg(pkg: String) {
        val remaining = loadExtraPkgs().filter { it != pkg }
        prefs.edit().putString(AppPickerActivity.PREF_EXTRA_PKGS, remaining.joinToString(",")).apply()
        Toast.makeText(this, "Toolbar theke remove kora hoyeche", Toast.LENGTH_SHORT).show()
        rebuildExtraButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // view already gone
            }
        }
        overlayView = null
        try {
            unregisterReceiver(extraAppsReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
    }
}
