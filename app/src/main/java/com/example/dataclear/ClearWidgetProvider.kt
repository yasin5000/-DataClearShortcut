package com.example.dataclear

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ClearWidgetProvider : AppWidgetProvider() {

    companion object {
        /** MainActivity theke call korbe jokhon selection change hoy, jate widget-o update hoy. */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClearWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                ClearWidgetProvider().onUpdate(context, mgr, ids)
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val pkg = prefs.getString("pkg", null)

        val label = if (pkg == null) {
            "Kono app select kora nai"
        } else {
            try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) {
                pkg
            }
        }

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_clear)
            views.setTextViewText(R.id.widget_app_name, label)

            val intent = Intent(context, QuickClearActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            // Proti widget id-r jonno alada request code, noyle sob widget ekই PendingIntent share kore fele
            val pi = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_clear_btn, pi)
            mgr.updateAppWidget(id, views)
        }
    }
}
