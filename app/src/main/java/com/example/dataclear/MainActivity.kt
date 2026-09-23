package com.example.dataclear

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    data class AppItem(val label: String, val pkg: String, val icon: Drawable)

    companion object {
        private const val REQ_PICK_IMAGE = 501
    }

    private var allApps: List<AppItem> = emptyList()
    private var shown: List<AppItem> = emptyList()
    private var selectedPkg: String? = null

    private lateinit var adapter: AppAdapter
    private lateinit var banner: TextView
    private lateinit var selIcon: ImageView
    private lateinit var selName: TextView
    private lateinit var clearBtn: Button
    private lateinit var floatBtn: Button
    private lateinit var uploadBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(28), dp(12), dp(8))
        }

        // Accessibility status banner
        banner = TextView(this).apply {
            textSize = 13f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        // Upore: selected app + boro CLEAR button
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        val selRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        selIcon = ImageView(this)
        selName = TextView(this).apply {
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(dp(12), 0, 0, 0)
        }
        selRow.addView(selIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
        selRow.addView(selName, LinearLayout.LayoutParams(0, -2, 1f))

        clearBtn = Button(this).apply {
            text = "CLEAR DATA"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setOnClickListener {
                val pkg = selectedPkg
                if (pkg != null) ClearHelper.autoClear(this@MainActivity, pkg)
            }
        }
        floatBtn = Button(this).apply {
            text = "FLOATING TOOLBAR ON"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setOnClickListener { toggleFloatingToolbar() }
        }
        uploadBtn = Button(this).apply {
            text = "UPLOAD BUTTON ON koro"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00897B"))
            setOnClickListener { toggleUploadButton() }
        }

        card.addView(selRow, LinearLayout.LayoutParams(-1, -2))
        card.addView(
            clearBtn,
            LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) }
        )
        card.addView(
            floatBtn,
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
        )
        card.addView(
            uploadBtn,
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
        )

        val hintText = TextView(this).apply {
            text = "Niche theke app select koro (chepe rakhle App info khulbe)"
            textSize = 12f
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        val search = EditText(this).apply {
            hint = "App khujo..."
            setSingleLine()
        }
        val list = ListView(this)

        root.addView(banner, LinearLayout.LayoutParams(-1, -2))
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(hintText, LinearLayout.LayoutParams(-1, -2))
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        allApps = loadApps()
        shown = allApps
        selectedPkg = prefs.getString("pkg", null)
        adapter = AppAdapter()
        list.adapter = adapter
        refreshSelected()

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                shown = if (q.isEmpty()) allApps
                else allApps.filter { it.label.lowercase().contains(q) }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        updateBanner()
        updateFloatButton()
        updateUploadButton()
    }

    private fun updateFloatButton() {
        if (FloatingToolbarService.isRunning) {
            floatBtn.text = "FLOATING TOOLBAR OFF koro"
            floatBtn.setBackgroundColor(Color.parseColor("#C62828"))
        } else {
            floatBtn.text = "FLOATING TOOLBAR ON koro"
            floatBtn.setBackgroundColor(Color.parseColor("#2E7D32"))
        }
    }

    private fun updateUploadButton() {
        val on = prefs.getBoolean("upload_enabled", false)
        if (on) {
            uploadBtn.text = "UPLOAD BUTTON OFF koro (image: set kora ache)"
            uploadBtn.setBackgroundColor(Color.parseColor("#C62828"))
        } else {
            uploadBtn.text = "UPLOAD BUTTON ON koro"
            uploadBtn.setBackgroundColor(Color.parseColor("#00897B"))
        }
    }

    private fun toggleUploadButton() {
        val on = prefs.getBoolean("upload_enabled", false)
        if (on) {
            prefs.edit().putBoolean("upload_enabled", false).apply()
            updateUploadButton()
            Toast.makeText(this, "Floating toolbar theke Upload button shore gelo", Toast.LENGTH_SHORT).show()
            return
        }
        // On korar age ekta image select korte hobe
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, REQ_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(this, "Image picker khola gelo na", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val ok = saveUploadImage(uri)
        if (ok) {
            prefs.edit().putBoolean("upload_enabled", true).apply()
            Toast.makeText(
                this,
                "Image save hoyeche. Floating toolbar-e ekhon 'Upload' button dekhabe.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Image save kora gelo na", Toast.LENGTH_SHORT).show()
        }
        updateUploadButton()
    }

    // Select kora image-take public Downloads folder-e fixed naam diye copy kore,
    // jate AutoClearService file-picker-e oi naam khuje ber korte pare.
    private fun saveUploadImage(sourceUri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(sourceUri)?.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, AutoClearService.UPLOAD_FILE_NAME)
                        put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = contentResolver
                    // Age purono thakle muche notun kore likhbo
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Downloads.DISPLAY_NAME}=?",
                        arrayOf(AutoClearService.UPLOAD_FILE_NAME)
                    )
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return false
                    resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    val file = File(dir, AutoClearService.UPLOAD_FILE_NAME)
                    FileOutputStream(file).use { out -> input.copyTo(out) }
                }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun toggleFloatingToolbar() {
        if (FloatingToolbarService.isRunning) {
            stopService(Intent(this, FloatingToolbarService::class.java))
            updateFloatButton()
            return
        }
        if (selectedPkg == null) {
            Toast.makeText(this, "Age ekta app select koro", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "'Display over other apps' permission ta on koro, tarpor abar chapo",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        startService(Intent(this, FloatingToolbarService::class.java))
        updateFloatButton()
    }

    private fun updateBanner() {
        if (AutoClearService.instance != null) {
            banner.text = "Auto mode ON"
            banner.setBackgroundColor(Color.parseColor("#C8E6C9"))
        } else {
            banner.text = "Auto mode OFF. Ekhane chapo > 'Data Clear Auto' on koro."
            banner.setBackgroundColor(Color.parseColor("#FFCDD2"))
        }
        banner.setTextColor(Color.BLACK)
    }

    private fun select(item: AppItem) {
        selectedPkg = item.pkg
        prefs.edit().putString("pkg", item.pkg).apply()
        refreshSelected()
        adapter.notifyDataSetChanged()
        ClearWidgetProvider.updateAll(this)
    }

    private fun refreshSelected() {
        val item = allApps.firstOrNull { it.pkg == selectedPkg }
        if (item == null) {
            selIcon.setImageDrawable(null)
            selName.text = "Kono app select kora nai"
            clearBtn.isEnabled = false
            clearBtn.alpha = 0.5f
        } else {
            selIcon.setImageDrawable(item.icon)
            selName.text = item.label
            clearBtn.isEnabled = true
            clearBtn.alpha = 1f
        }
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .map { (pkg, ri) -> AppItem(ri.loadLabel(pm).toString(), pkg, ri.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(p: Int, convertView: View?, parent: ViewGroup): View {
            val item = shown[p]
            val isSel = item.pkg == selectedPkg
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                if (isSel) setBackgroundColor(Color.parseColor("#E3F2FD"))
            }
            val icon = ImageView(this@MainActivity).apply { setImageDrawable(item.icon) }
            row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

            val name = TextView(this@MainActivity).apply {
                text = item.label
                textSize = 16f
                setTextColor(Color.BLACK)
                setPadding(dp(12), 0, dp(8), 0)
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))

            if (isSel) {
                val tick = TextView(this@MainActivity).apply {
                    text = "✓ selected"
                    textSize = 13f
                    setTextColor(Color.parseColor("#1565C0"))
                    setPadding(0, 0, dp(8), 0)
                }
                row.addView(tick, LinearLayout.LayoutParams(-2, -2))
            }

            // Prottek row-e ekta "Open" button, chaplei oi app ta shorashori open hobe
            val openBtn = Button(this@MainActivity).apply {
                text = "Open"
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1565C0"))
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { ClearHelper.openApp(this@MainActivity, item.pkg) }
            }
            row.addView(
                openBtn,
                LinearLayout.LayoutParams(-2, dp(36))
            )

            row.setOnClickListener { select(item) }
            row.setOnLongClickListener { ClearHelper.openAppInfo(this@MainActivity, item.pkg); true }
            return row
        }
    }
}
