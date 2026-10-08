// PendingYouTubeLikes.kt

package com.example.musicfy.importer

import android.content.Context
import java.io.File

/**
 * Songs liked by an import that YouTube doesn't know about yet. YouTube sync treats "liked here but
 * not on YouTube" as "unliked elsewhere" and unlikes it locally, which would wipe every imported
 * like; sync checks this set first and pushes these up instead.
 */
object PendingYouTubeLikes {
    private const val FILE_NAME = "yt_pending_likes.txt"
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun read(context: Context): Set<String> = synchronized(lock) {
        runCatching {
            file(context).takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        }.getOrNull().orEmpty()
    }

    fun add(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) { write(context, read(context) + ids) }
    }

    fun remove(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) { write(context, read(context) - ids.toSet()) }
    }

    private fun write(context: Context, ids: Set<String>) {
        runCatching {
            val target = file(context)
            if (ids.isEmpty()) {
                target.delete()
                return
            }
            // write-then-rename so a crash mid-write never leaves half a list
            val temp = File(context.filesDir, "$FILE_NAME.tmp")
            temp.writeText(ids.joinToString("\n"))
            if (!temp.renameTo(target)) {
                target.writeText(ids.joinToString("\n"))
                temp.delete()
            }
        }
    }
}
