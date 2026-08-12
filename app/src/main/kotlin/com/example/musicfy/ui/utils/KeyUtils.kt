// keyutilskt
// the file functioned as key utils

package com.example.musicfy.ui.utils

import java.util.concurrent.atomic.AtomicLong

// utility object for generating unique keys in lazycolumn/lazyrow to prevent
object KeyUtils {
    private val counter = AtomicLong(0)
    
    // generates a unique key by combining a base identifier with a unique counter
    fun generateUniqueKey(baseId: String, prefix: String = ""): String {
        val uniqueId = counter.incrementAndGet()
        return if (prefix.isNotEmpty()) {
            "${prefix}_${baseId}_$uniqueId"
        } else {
            "${baseId}_$uniqueId"
        }
    }
    
    // generates a unique key for items in a list with their index useful for
    fun generateIndexedKey(baseId: String, index: Int, prefix: String = ""): String {
        val uniqueId = counter.incrementAndGet()
        return if (prefix.isNotEmpty()) {
            "${prefix}_${baseId}_${index}_$uniqueId"
        } else {
            "${baseId}_${index}_$uniqueId"
        }
    }
    
    // generates a timestamp-based unique key for dynamic content useful for content
    fun generateTimestampKey(baseId: String, prefix: String = ""): String {
        val timestamp = System.currentTimeMillis()
        val uniqueId = counter.incrementAndGet()
        return if (prefix.isNotEmpty()) {
            "${prefix}_${baseId}_${timestamp}_$uniqueId"
        } else {
            "${baseId}_${timestamp}_$uniqueId"
        }
    }
}
