package com.example.dataclear

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Widget-er CLEAR button ei activity ke khole. Kono UI dekhায় na —
 * shudhu prefs theke last-selected pkg niye clear flow shuru kore
 * (ba accessibility off thakle setting-e pathiye) tarpor nijei bondho hoye jay.
 */
class QuickClearActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val pkg = prefs.getString("pkg", null)

        if (pkg == null) {
            Toast.makeText(this, "Age app-e dhuke ekta app select koro", Toast.LENGTH_LONG).show()
        } else {
            ClearHelper.autoClear(this, pkg)
        }
        finish()
    }
}
