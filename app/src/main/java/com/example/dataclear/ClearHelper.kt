package com.example.dataclear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * MainActivity ar QuickClearActivity (widget theke asha) — dujon-ei
 * ekhan theke "clear" flow ta call kore, jate logic duibar likhte na hoy.
 */
object ClearHelper {

    fun autoClear(context: Context, pkg: String) {
        if (AutoClearService.instance == null) {
            Toast.makeText(context, "Age Accessibility-te 'Data Clear Auto' on koro", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        AutoClearService.start(pkg)
        openAppInfo(context, pkg)
    }

    fun openAppInfo(context: Context, pkg: String) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Settings khola gelo na", Toast.LENGTH_SHORT).show()
        }
    }

    // Selected app-take shorashori open kore dey (launcher intent diye)
    fun openApp(context: Context, pkg: String) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, "App ta open kora gelo na", Toast.LENGTH_SHORT).show()
        }
    }
}
