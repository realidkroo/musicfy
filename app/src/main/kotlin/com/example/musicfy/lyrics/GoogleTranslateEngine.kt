// GoogleTranslateEngine.kt
// Translation that needs no API key and no account.
//
// The existing translation path requires an OpenRouter or DeepL key pasted into settings, which
// means the translate toggle does nothing at all until the user has gone and signed up for
// something. This uses the endpoint the Google Translate web widget itself calls
// (`client=gtx`), which is unauthenticated, so translation works out of the box.
//
// Caveats worth knowing, since they shape the code below:
//   - It is an undocumented endpoint. It can change shape or rate-limit without notice, so every
//     failure path here degrades to "no translation" rather than throwing into the UI.
//   - The response is a deeply nested JSON array, not an object. It is parsed positionally.
//   - Long requests get truncated, so lines are sent in batches rather than one giant blob.

package com.example.musicfy.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

object GoogleTranslateEngine {

    /**
     * Lines are joined with this before being sent, so one request covers many lines.
     *
     * A plain newline is not safe: the translator collapses and re-flows them, and the reply comes
     * back with a different number of lines than went out, which silently shifts every translation
     * onto the wrong lyric. This marker survives the round trip because it reads as ordinary
     * punctuation the translator has no reason to merge across.
     */
    private const val LineMarker = "\n@@@\n"

    /** Kept well under the point where the endpoint starts truncating. */
    private const val BatchCharBudget = 1_500

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    /**
     * Translates [lines] into [targetLanguage], preserving order and count.
     *
     * Returns null if anything at all goes wrong. The caller should treat that as "leave the
     * lyrics untranslated" — a partial or misaligned translation is worse than none, because it
     * puts the wrong words under the wrong line and the user cannot tell.
     */
    suspend fun translate(
        lines: List<String>,
        targetLanguage: String,
        sourceLanguage: String = "auto",
    ): List<String>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty() || targetLanguage.isBlank()) return@withContext null

        val out = ArrayList<String>(lines.size)
        var batch = ArrayList<String>()
        var budget = 0

        suspend fun flush(): Boolean {
            if (batch.isEmpty()) return true
            val translated = translateBatch(batch, targetLanguage, sourceLanguage) ?: return false
            out += translated
            batch = ArrayList()
            budget = 0
            return true
        }

        for (line in lines) {
            if (budget + line.length > BatchCharBudget && batch.isNotEmpty()) {
                if (!flush()) return@withContext null
            }
            batch.add(line)
            budget += line.length + LineMarker.length
        }
        if (!flush()) return@withContext null

        // Only hand back a result that lines up one-for-one with what went in. Anything else would
        // mis-attribute translations to the wrong lyrics.
        if (out.size == lines.size) out else null
    }

    private suspend fun translateBatch(
        lines: List<String>,
        targetLanguage: String,
        sourceLanguage: String,
    ): List<String>? {
        val payload = lines.joinToString(LineMarker)
        val body = runCatching {
            val response = client.get("https://translate.googleapis.com/translate_a/single") {
                parameter("client", "gtx")
                parameter("sl", sourceLanguage)
                parameter("tl", targetLanguage)
                parameter("dt", "t")
                parameter("q", payload)
            }
            if (response.status.value != 200) return null
            response.bodyAsText()
        }.getOrNull() ?: return null

        // Shape: [[["translated","original",...], ...], ...] — the pieces of the translation are
        // split arbitrarily, so they are concatenated back together before being re-split on the
        // marker rather than assumed to be one segment per line.
        val joined = runCatching {
            val root = json.parseToJsonElement(body).jsonArray
            val segments = root.getOrNull(0) as? JsonArray ?: return null
            buildString {
                for (segment in segments) {
                    val piece = (segment as? JsonArray)?.getOrNull(0) as? JsonPrimitive ?: continue
                    append(piece.content)
                }
            }
        }.getOrNull() ?: return null

        val parts = joined.split(Regex("\\s*@@@\\s*")).map { it.trim() }
        return if (parts.size == lines.size) parts else null
    }
}
