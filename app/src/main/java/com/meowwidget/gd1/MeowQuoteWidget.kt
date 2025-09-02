package com.meowwidget.gd1

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MeowQuoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // RemoteViews trỏ tới layout tối giản của widget
        val views = RemoteViews(context.packageName, R.layout.bocuc_meow)

        // Dòng chữ thử theo yêu cầu B2
        views.setTextViewText(
            R.id.widget_text,
            "Đôi khi ta phải lạc đường để tìm ra con đường đúng đắn nhất"
        )

        // Chạm widget -> mở MeowSettingsActivity
        val intent = Intent(context, MeowSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
        views.setOnClickPendingIntent(R.id.widget_text, pendingIntent)

        // Cập nhật cho tất cả các instance của widget
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
