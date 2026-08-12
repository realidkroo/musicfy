// turntablewidgetreceiverkt
// the file functioned as turntable widget receiver

package com.example.musicfy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.example.musicfy.playback.MusicService

class TurntableWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // only trigger update through musicservice if it s already running
        // this prevents backgroundservicestartnotallowedexception on android 14+
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_TURNTABLE_WIDGET
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // service might be restricted in background
            }
        }
        // if service is not running widget shows default layout until user opens app
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TURNTABLE_PLAY_PAUSE, ACTION_TURNTABLE_NEXT, ACTION_TURNTABLE_PREVIOUS -> {
                // user interactions from widget buttons can start the service
                // android allows starting fgs from widget pendingintent clicks
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = when (intent.action) {
                        ACTION_TURNTABLE_PLAY_PAUSE -> MusicWidgetReceiver.ACTION_PLAY_PAUSE
                        ACTION_TURNTABLE_NEXT -> MusicWidgetReceiver.ACTION_NEXT
                        ACTION_TURNTABLE_PREVIOUS -> MusicWidgetReceiver.ACTION_PREVIOUS
                        else -> intent.action
                    }
                    putExtras(intent)
                }
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    // service might be restricted in background
                }
            }
        }
    }

    companion object {
        const val ACTION_TURNTABLE_PLAY_PAUSE = "com.example.musicfy.widget.TURNTABLE_PLAY_PAUSE"
        const val ACTION_TURNTABLE_NEXT = "com.example.musicfy.widget.TURNTABLE_NEXT"
        const val ACTION_TURNTABLE_PREVIOUS = "com.example.musicfy.widget.TURNTABLE_PREVIOUS"
        const val ACTION_UPDATE_TURNTABLE_WIDGET = "com.example.musicfy.widget.UPDATE_TURNTABLE_WIDGET"
    }
}
