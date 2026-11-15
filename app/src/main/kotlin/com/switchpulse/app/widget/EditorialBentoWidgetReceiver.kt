package com.switchpulse.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.worker.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditorialBentoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EditorialBentoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CoroutineScope(Dispatchers.IO).launch {
            val cached = PreferencesStore(context).getCachedDeals()
            if (cached.isEmpty()) {
                SyncScheduler.triggerImmediate(context)
            }
        }
    }
}
