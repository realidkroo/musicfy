// UtilExt.kt
// this thing is part of util ext

package com.example.musicfy.extensions

fun <T> tryOrNull(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        null
    }
