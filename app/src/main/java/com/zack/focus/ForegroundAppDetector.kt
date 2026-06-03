package com.zack.focus

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class ForegroundAppDetector(private val context: Context) {

    private var lastKnownForeground: String? = null

    /**
     * Returns the current foreground package. Caches the last known value so that
     * brief gaps in UsageEvents (when the foreground app hasn't changed recently)
     * still return the correct app rather than null.
     */
    fun getForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 3_000  // short window since we poll frequently

        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> lastKnownForeground = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    if (event.packageName == lastKnownForeground) lastKnownForeground = null
            }
        }

        return lastKnownForeground
    }
}
