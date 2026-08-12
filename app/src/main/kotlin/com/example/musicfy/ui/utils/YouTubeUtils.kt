// youtubeutilskt
// the file functioned as you tube utils

package com.example.musicfy.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    // iytimgcom handling (youtube video thumbnails)
    // these urls use a filename-based quality system:
    // we use maxresdefault (1280x720) for the large player thumbnail (width >=
    // and hqdefault (480x360) for lists and grids to ensure extremely fast
    if (this.contains("i.ytimg.com")) {
        val targetQuality = if (width != null && width >= 1200) "maxresdefault.jpg" else "hqdefault.jpg"
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg"),
            targetQuality
        )
    }

    // googleusercontentcom handling (includes lh3-lh6 yt3 etc)
    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val w = width ?: 0
        val h = height ?: width ?: 0
        // reverting to l90-rj (jpeg) for better compatibility while keeping high
        return "$baseUrl=w$w-h$h-p-l90-rj"
    }

    // yt3ggphtcom handling (avatars)
    if (this.contains("yt3.ggpht.com")) {
        // correctly strip any existing size parameter (=s or -s) before appending
        val baseUrl = this.split("=")[0].split("-s")[0]
        return "$baseUrl=s${width ?: height}"
    }

    // fallback for other lh3-style urls that might not have =w yet
    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val w = width ?: 0
        val h = height ?: width ?: 0
        return "${this.split("=")[0]}=w$w-h$h-p-l90-rj"
    }

    return this
}
