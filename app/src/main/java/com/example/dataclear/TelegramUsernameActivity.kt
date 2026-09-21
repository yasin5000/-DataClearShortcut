package com.example.dataclear

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Telegram-er jonno username diye specific chat/channel/group
 * floating toolbar e "Chat N" button hisebe add korar screen.
 * Ekbar e ekadhik username "Add" kore add kora jay.
 */
class TelegramUsernameActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private lateinit var addedList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Telegram username diye chat add koro"
            textSize = 16f
            setTextColor(Color.BLACK)
        }
        val hintLabel = TextView(this).apply {
            text = "@ chara shudhu username likho, jemon: jasin_shopan"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(4), 0, dp(12))
        }

        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val usernameInput = EditText(this).apply {
            hint = "username"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        val addBtn = Button(this).apply {
            text = "Add"
            isAllCaps = false
        }
        inputRow.addView(usernameInput, LinearLayout.LayoutParams(0, -2, 1f))
        inputRow.addView(addBtn, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })

        addedList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }

        val doneBtn = Button(this).apply {
            text = "Done"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(hintLabel, LinearLayout.LayoutParams(-1, -2))
        root.addView(inputRow, LinearLayout.LayoutParams(-1, -2))
        root.addView(addedList, LinearLayout.LayoutParams(-1, -2))
        root.addView(
            doneBtn,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
        )
        setContentView(root)

        refreshAddedList()

        addBtn.setOnClickListener {
            val raw = usernameInput.text.toString().trim().removePrefix("@")
            if (raw.isEmpty()) {
                Toast.makeText(this, "Username likho age", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addUsername(raw)
            usernameInput.text.clear()
        }
    }

    private fun addUsername(username: String) {
        val current = (prefs.getString(AppPickerActivity.PREF_EXTRA_PKGS, "") ?: "")
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableList()

        val entry = "tg:$username"
        if (current.contains(entry)) {
            Toast.makeText(this, "Ei username age theke add kora ache", Toast.LENGTH_SHORT).show()
            return
        }
        current.add(entry)
        prefs.edit().putString(AppPickerActivity.PREF_EXTRA_PKGS, current.joinToString(",")).apply()
        sendBroadcast(Intent(AppPickerActivity.ACTION_EXTRA_APPS_CHANGED).setPackage(packageName))
        Toast.makeText(this, "@$username add hoye geche", Toast.LENGTH_SHORT).show()
        refreshAddedList()
    }

    private fun refreshAddedList() {
        addedList.removeAllViews()
        val entries = (prefs.getString(AppPickerActivity.PREF_EXTRA_PKGS, "") ?: "")
            .split(",")
            .filter { it.startsWith("tg:") }
            .map { it.removePrefix("tg:") }

        entries.forEach { username ->
            val row = TextView(this).apply {
                text = "✓ @$username"
                textSize = 14f
                setTextColor(Color.parseColor("#2E7D32"))
                setPadding(0, dp(4), 0, dp(4))
            }
            addedList.addView(row)
        }
    }
}
