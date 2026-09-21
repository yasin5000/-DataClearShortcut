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
import android.graphics.drawable.GradientDrawable
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

/**
 * Home screen-er upore vashman (floating) ekta premium-style choto toolbar.
 * Ekpashe choto arrow tab (‹ ›) diye pura panel show/hide kora jay,
 * ar ekta rounded semi-transparent shada panel e shob button thake.
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

    /** Demo design-er moto flat, rounded, left-aligned tool button banay. */
    private fun styledButton(labelText: String, textColor: Int = Color.parseColor("#1C1C1E")): Button {
        return Button(this).apply {
            text = labelText
            textSize = 12f
            isAllCaps = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(textColor)
            setPadding(dp(10), 0, dp(6), 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F5F7"))
                cornerRadius = dp(9).toFloat()
            }
            stateListAnimator = null
            elevation = 0f
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

        // Default: right edge, vertically centered (demo-r moto). Age kokhono
        // drag kore rakhle sheita mone thakbe.
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val dockedRight = prefs.getBoolean("toolbar_docked_right", true)
        val startCollapsedForX = prefs.getBoolean("toolbar_collapsed", false)
        val startWidth = if (startCollapsedForX) dp(22) else dp(22) + dp(130)
        val defaultX = if (dockedRight) screenW - startWidth else 0
        params.x = prefs.getInt("float_x", defaultX)
        params.y = prefs.getInt("float_y", screenH / 2 - dp(110))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Choto premium arrow tab - eta chaple pura panel show/hide hoy
        val toggleTab = TextView(this).apply {
            text = "‹"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EB141419"))
                cornerRadii = floatArrayOf(
                    dp(10).toFloat(), dp(10).toFloat(),
                    0f, 0f,
                    0f, 0f,
                    dp(10).toFloat(), dp(10).toFloat()
                )
            }
        }

        // Rounded, semi-transparent shada panel
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E0FFFFFF"))
                cornerRadius = dp(15).toFloat()
            }
        }

        // Drag korar jonno choto grip, panel-er upore
        val grip = TextView(this).apply {
            text = "⋮⋮"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }

        val clearBtn = styledButton("⌫  Clear Data", Color.parseColor("#D32F2F"))
        val openBtn = styledButton("↗  Open")
        val plusBtn = styledButton("＋  Add App")
        val resetBtn = styledButton("↺  Reset", Color.parseColor("#EF6C00"))

        val extras = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        extraContainer = extras

        panel.addView(grip, LinearLayout.LayoutParams(-1, -2))
        panel.addView(
            clearBtn,
            LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(2); bottomMargin = dp(2) }
        )
        panel.addView(
            openBtn,
            LinearLayout.LayoutParams(-1, dp(32)).apply { bottomMargin = dp(2) }
        )
        panel.addView(
            plusBtn,
            LinearLayout.LayoutParams(-1, dp(32)).apply { bottomMargin = dp(2) }
        )
        panel.addView(
            resetBtn,
            LinearLayout.LayoutParams(-1, dp(32)).apply { bottomMargin = dp(2) }
        )
        panel.addView(extras, LinearLayout.LayoutParams(-1, -2))

        val startCollapsed = prefs.getBoolean("toolbar_collapsed", false)
        panel.visibility = if (startCollapsed) View.GONE else View.VISIBLE
        toggleTab.text = if (startCollapsed) "›" else "‹"

        container.addView(toggleTab, LinearLayout.LayoutParams(dp(22), dp(40)))
        container.addView(panel, LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT))

        // Panel show/hide korar shomoy dock kora edge (left/right) e flush kore rakhe
        fun snapToEdge(collapsed: Boolean) {
            val docked = prefs.getBoolean("toolbar_docked_right", true)
            val w = if (collapsed) dp(22) else dp(22) + dp(130)
            params.x = if (docked) resources.displayMetrics.widthPixels - w else 0
            windowManager.updateViewLayout(container, params)
            prefs.edit().putInt("float_x", params.x).apply()
        }

        // Sudhu grip-e touch kore drag kora jabe
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
                    // Chere deoar por kachakachi edge-e (left ba right) flush kore snap kore fela
                    val curW = if (panel.visibility == View.GONE) dp(22) else dp(22) + dp(130)
                    val center = params.x + curW / 2
                    val dockRight = center >= resources.displayMetrics.widthPixels / 2
                    prefs.edit().putBoolean("toolbar_docked_right", dockRight).apply()
                    prefs.edit().putInt("float_y", params.y).apply()
                    snapToEdge(panel.visibility == View.GONE)
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
        resetBtn.setOnClickListener {
            ClearHelper.resetNetworkAuto(this)
        }
        toggleTab.setOnClickListener {
            val nowVisible = panel.visibility == View.VISIBLE
            panel.visibility = if (nowVisible) View.GONE else View.VISIBLE
            toggleTab.text = if (nowVisible) "›" else "‹"
            prefs.edit().putBoolean("toolbar_collapsed", nowVisible).apply()
            snapToEdge(nowVisible)
        }

        overlayView = container
        windowManager.addView(container, params)
        rebuildExtraButtons()
    }

    /**
     * "+" diye add kora extra app ba telegram username-gulor jonno
     * "Open N" / "Chat N" button notun kore banay, demo-r style-e.
     * Long-press korle remove hoye jabe.
     */
    private fun rebuildExtraButtons() {
        val extras = extraContainer ?: return
        extras.removeAllViews()

        val entries = loadExtraPkgs()
        var openCount = 0
        var chatCount = 0

        entries.forEach { entry ->
            val label: String
            val color: Int
            val isTg = entry.startsWith("tg:")

            if (isTg) {
                chatCount++
                label = "✈  Chat $chatCount"
                color = Color.parseColor("#0088CC")
            } else {
                openCount++
                label = "▸  Open $openCount"
                color = Color.parseColor("#6A1B9A")
            }

            val btn = styledButton(label, color).apply {
                setOnLongClickListener {
                    removeExtraPkg(entry)
                    true
                }
                if (isTg) {
                    val username = entry.removePrefix("tg:")
                    setOnClickListener { openTelegramChat(username) }
                } else {
                    setOnClickListener { ClearHelper.openApp(this@FloatingToolbarService, entry) }
                }
            }

            extras.addView(
                btn,
                LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(2) }
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
