package com.example.dataclear

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Floating toolbar-er "+" button theke khola hoy.
 * Ekta app list dekhay, user select korle sheta "extra open apps" list e
 * add hoye jay ebong FloatingToolbarService ke jaanaye dey (broadcast diye)
 * jate toolbar-e notun "Open N" button toiri hoy.
 */
class AppPickerActivity : Activity() {

    companion object {
        const val ACTION_EXTRA_APPS_CHANGED = "com.example.dataclear.ACTION_EXTRA_APPS_CHANGED"
        const val PREF_EXTRA_PKGS = "extra_pkgs"
    }

    data class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private var allApps: List<AppItem> = emptyList()
    private var shown: List<AppItem> = emptyList()
    private lateinit var adapter: AppAdapter

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(24), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Toolbar-e add korar jonno app select koro"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        val search = EditText(this).apply {
            hint = "App khujo..."
            setSingleLine()
        }
        val list = ListView(this)

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        allApps = loadApps()
        shown = allApps
        adapter = AppAdapter()
        list.adapter = adapter

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

    private fun addToExtras(pkg: String) {
        val current = (prefs.getString(PREF_EXTRA_PKGS, "") ?: "")
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableList()

        if (current.contains(pkg)) {
            Toast.makeText(this, "Ei app age theke add kora ache", Toast.LENGTH_SHORT).show()
            return
        }

        current.add(pkg)
        prefs.edit().putString(PREF_EXTRA_PKGS, current.joinToString(",")).apply()

        sendBroadcast(Intent(ACTION_EXTRA_APPS_CHANGED).setPackage(packageName))

        Toast.makeText(this, "Toolbar-e add hoye geche", Toast.LENGTH_SHORT).show()
        finish()
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
            val row = LinearLayout(this@AppPickerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            val icon = ImageView(this@AppPickerActivity).apply { setImageDrawable(item.icon) }
            row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

            val name = TextView(this@AppPickerActivity).apply {
                text = item.label
                textSize = 16f
                setTextColor(Color.BLACK)
                setPadding(dp(12), 0, dp(8), 0)
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.setOnClickListener {
                if (isTelegram(item.pkg)) {
                    startActivity(Intent(this@AppPickerActivity, TelegramUsernameActivity::class.java))
                    finish()
                } else {
                    addToExtras(item.pkg)
                }
            }
            return row
        }
    }

    private fun isTelegram(pkg: String) = pkg.contains("telegram", ignoreCase = true)
}

