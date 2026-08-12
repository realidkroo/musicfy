// playbacklogmanagerkt
// this thing is for playback log manager

package com.example.musicfy.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// log levels for playback diagnostics
enum class PlaybackLogLevel {
    INFO,
    WARNING,
    ERROR,
    DEBUG,
    BOT // special level for highlighting bot mitigation events
}

// log entry for the playback diagnostics
data class PlaybackLogEntry(
    val timestamp: String,
    val level: PlaybackLogLevel,
    val message: String,
    val details: String? = null
)

// singleton manager to hold the global state of playback logs this is used for
object PlaybackLogManager {
    private const val MAX_LOG_ENTRIES = 500
    
    private val _logs = MutableStateFlow<List<PlaybackLogEntry>>(emptyList())
    val logs: StateFlow<List<PlaybackLogEntry>> = _logs.asStateFlow()
    
    // add a new log entry
    fun log(level: PlaybackLogLevel, message: String, details: String? = null) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        val entry = PlaybackLogEntry(timestamp, level, message, details)
        
        // use a list to ensure thread safety during update
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(entry)
        
        // take last n entries
        _logs.value = if (currentLogs.size > MAX_LOG_ENTRIES) {
            currentLogs.takeLast(MAX_LOG_ENTRIES)
        } else {
            currentLogs
        }
    }
    
    // clear all logs
    fun clearLogs() {
        _logs.value = emptyList()
    }
}
