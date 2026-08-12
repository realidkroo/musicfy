// metrolistwidgetmanagerkt
// what is this for you ask its for metrolist widget manager ofc

package com.example.musicfy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.widget.RemoteViews
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.example.musicfy.MainActivity
import com.example.musicfy.R
import com.example.musicfy.db.MusicDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicfyWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    private val imageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(false)
            .build()
    }

    // cache for album art to avoid reloading
    private var cachedArtworkUri: String? = null
    private var cachedAlbumArt: Bitmap? = null
    private var cachedCircularAlbumArt: Bitmap? = null

    suspend fun updateWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // use cached album art if uri hasn't changed otherwise load new one
        val albumArt: Bitmap?
        val circularAlbumArt: Bitmap?
        
        if (artworkUri != null && artworkUri == cachedArtworkUri && cachedAlbumArt != null) {
            albumArt = cachedAlbumArt
            circularAlbumArt = cachedCircularAlbumArt
        } else {
            albumArt = artworkUri?.let { loadAlbumArt(it, 300) }
            circularAlbumArt = albumArt?.let { getCircularBitmap(it) }
            // update cache
            cachedArtworkUri = artworkUri
            cachedAlbumArt = albumArt
            cachedCircularAlbumArt = circularAlbumArt
        }

        // update main music player widgets
        val componentName = ComponentName(context, MusicWidgetReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isNotEmpty()) {
            widgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = createRemoteViewsForSize(
                    options,
                    title,
                    artist,
                    albumArt,
                    isPlaying,
                    isLiked,
                    duration,
                    currentPosition
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        // update turntable widgets
        val turntableComponentName = ComponentName(context, TurntableWidgetReceiver::class.java)
        val turntableWidgetIds = appWidgetManager.getAppWidgetIds(turntableComponentName)
        if (turntableWidgetIds.isNotEmpty()) {
            val turntableViews = createTurntableRemoteViews(
                circularAlbumArt,
                isPlaying,
                isLiked
            )
            turntableWidgetIds.forEach { widgetId ->
                appWidgetManager.updateAppWidget(widgetId, turntableViews)
            }
        }
    }

    private fun createRemoteViewsForSize(
        options: Bundle,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long
    ): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        // determine widget size category
        // 2x2: approximately 110dp x 110dp (compact square)
        // 4x1: approximately 250dp x 40dp (wide single row)
        // full: approximately 250dp x 110dp (default)
        return when {
            minWidth < 180 && minHeight < 100 -> {
                // 2x2 compact - only play button with album art
                createCompactSquareRemoteViews(albumArt, isPlaying)
            }
            minWidth >= 180 && minHeight < 100 -> {
                // 4x1 wide - single row with album art song info like and play buttons
                createCompactWideRemoteViews(title, artist, albumArt, isPlaying, isLiked)
            }
            else -> {
                // full layout
                createRemoteViews(title, artist, albumArt, isPlaying, isLiked, duration, currentPosition)
            }
        }
    }

    private fun createRemoteViews(
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)

        // set song info
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist_name, artist)

        // set album art with rounded corners
        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_album_art, roundedAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_album_art, getRoundedDefaultIcon(48f))
        }

        // set play/pause icon
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        // set like icon - using nav style (purple) for main widget
        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_like_button, likeIcon)

        // set progress level
        if (duration > 0) {
            val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt()
            views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
        } else {
            views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
        }

        // set click intents
        views.setOnClickPendingIntent(R.id.widget_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent())

        return views
    }

    private suspend fun loadAlbumArt(artworkUri: String, size: Int = 200): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(size, size)
                    .allowHardware(false)
                    .crossfade(300)
                    .build()
                val result = imageLoader.execute(request)
                result.image?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        // ensure the bitmap is square for thumbnails
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        
        return output
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        
        // first crop to square
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
        
        // create circular output
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        
        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        return output
    }

    private fun createCompactSquareRemoteViews(
        albumArt: Bitmap?,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_square)

        // set album art with rounded corners
        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_compact_album_art, roundedAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_compact_album_art, getRoundedDefaultIcon(48f))
        }

        // set play/pause icon - using low style icons
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_compact_play_pause, playPauseIcon)

        // set click intents
        views.setOnClickPendingIntent(R.id.widget_compact_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_compact_play_container, getPlayPauseIntent())

        return views
    }

    private fun createCompactWideRemoteViews(
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)

        // set song info
        views.setTextViewText(R.id.widget_wide_song_title, title)
        views.setTextViewText(R.id.widget_wide_artist_name, artist)

        // set album art with rounded corners (48f to match 12dp at ~4x density for
        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_wide_album_art, roundedAlbumArt)
        } else {
            // create rounded default icon
            views.setImageViewBitmap(R.id.widget_wide_album_art, getRoundedDefaultIcon(48f))
        }

        // set play/pause icon - using low style icons
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_wide_play_pause, playPauseIcon)

        // set like icon - using navigation style (purple)
        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_wide_like_button, likeIcon)

        // set click intents
        views.setOnClickPendingIntent(R.id.widget_wide_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_wide_play_container, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_wide_like_button, getLikeIntent())

        return views
    }

    private fun createTurntableRemoteViews(
        circularAlbumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)

        // set circular album art - create circular default icon if no album art
        if (circularAlbumArt != null) {
            views.setImageViewBitmap(R.id.widget_turntable_album_art, circularAlbumArt)
        } else {
            // load and make the default icon circular
            views.setImageViewBitmap(R.id.widget_turntable_album_art, getCircularDefaultIcon())
        }

        // set play/pause icon - using secondary color icons for turntable
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_secondary else R.drawable.ic_widget_play_secondary
        views.setImageViewResource(R.id.widget_turntable_play_pause, playPauseIcon)

        // set click intents
        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_play_container, getTurntablePlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_prev_button, getTurntablePreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_next_button, getTurntableNextIntent())

        return views
    }
    
    private fun getCircularDefaultIcon(): Bitmap {
        // get the launcher icon and make it circular
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getCircularBitmap(bitmap)
    }
    
    private fun getRoundedDefaultIcon(cornerRadius: Float): Bitmap {
        // get the launcher icon and make it rounded
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getRoundedCornerBitmap(bitmap, cornerRadius)
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPlayPauseIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getLikeIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_LIKE
        }
        return PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePlayPauseIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntableNextIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePreviousIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS
        }
        return PendingIntent.getBroadcast(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
