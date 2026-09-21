package com.example.dataclear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundNotif()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
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

        // Drag korar jonno grip (upore, choto strip)
        val grip = TextView(this).apply {
            text = "⋮⋮"
            textSize = 14f
            setTextColor(Color.parseColor("#BDBDBD"))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }

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

        container.addView(grip, LinearLayout.LayoutParams(-1, -2))
        container.addView(clearBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        container.addView(openBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

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

        overlayView = container
        windowManager.addView(container, params)
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
    }
}
