// LyricsTranslationHelper.kt

package com.example.musicfy.lyrics

import android.content.Context
import com.example.musicfy.api.DeepLService
import com.example.musicfy.api.MistralService
import com.example.musicfy.api.OpenRouterService
import com.example.musicfy.api.OpenRouterStreamingService
import com.example.musicfy.constants.LanguageCodeToName
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private val _translationSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val translationSaved: SharedFlow<Unit> = _translationSaved.asSharedFlow()

    private var translationJob: Job? = null
    private var isCompositionActive = true

    private val translationCache = mutableMapOf<String, List<String>>()

    private fun getCacheKey(lyricsText: String, mode: String, language: String): String =
        "${lyricsText.hashCode()}_${mode}_$language"

    private fun tryParsePartialTranslation(content: String, expectedCount: Int): List<String> {
        val startIdx = content.indexOf('[')
        if (startIdx == -1) return emptyList()

        val result = mutableListOf<String>()
        var pos = startIdx + 1
        var inString = false
        var escaping = false
        val currentString = StringBuilder()

        while (pos < content.length && result.size < expectedCount) {
            val char = content[pos]

            when {
                escaping -> {
                    currentString.append(char)
                    escaping = false
                }
                char == '\\' && inString -> {
                    currentString.append(char)
                    escaping = true
                }
                char == '"' -> {
                    if (inString) {
                        result.add(currentString.toString())
                        currentString.clear()
                        inString = false
                    } else {
                        inString = true
                    }
                }
                inString -> {
                    currentString.append(char)
                }
                char == ']' -> {
                    break
                }
            }
            pos++
        }

        return result
    }

    fun getCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): List<String>? {
        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val key = getCacheKey(lyricsText, mode, language)
        return translationCache[key]
    }

    fun applyCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): Boolean {
        val cached = getCachedTranslations(lyrics, mode, language) ?: return false
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        if (cached.size >= nonEmptyEntries.size) {
            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                lyrics[originalIndex].translatedTextFlow.value = cached[idx]
            }
            return true
        }
        return false
    }

    /**
     * Whether translations should be on screen. This is the toggle the translate button flips.
     *
     * It used to be implicit: "on" meant "some line currently has a translation", and "off" only
     * blanked the lines. Nothing remembered the choice, so the stored translation reappeared on
     * the next database emission, a new song never translated, and changing the language had
     * nothing to re-run against.
     */
    private val _translationOn = MutableStateFlow(false)
    val translationOn: StateFlow<Boolean> = _translationOn.asStateFlow()

    private var lastSongId: String? = null

    /**
     * What a stored translation is filed under. Just the target when the source is detected
     * (as before, so existing stored translations still match), "source>target" when the user
     * picked the source, so picking one after a bad detection really translates again instead of
     * reloading the stored copy.
     */
    fun languageTag(sourceLanguage: String, targetLanguage: String): String =
        if (sourceLanguage.isBlank() || sourceLanguage == "auto") targetLanguage else "$sourceLanguage>$targetLanguage"

    fun triggerManualTranslation() {
        _translationOn.value = true
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _translationOn.value = false
        _hasActiveTranslations.value = false
        translationJob?.cancel()
        _status.value = TranslationStatus.Idle
        _clearTranslationsTrigger.tryEmit(Unit)
    }

    /**
     * Called when the playing song changes. Translation carries over to the next song only when
     * "translate this whole session" is on; otherwise each song starts untranslated.
     */
    fun onSongChanged(songId: String?, sessionWide: Boolean) {
        if (songId == null) return
        if (lastSongId != null && lastSongId != songId && !sessionWide) {
            _translationOn.value = false
            _hasActiveTranslations.value = false
        }
        lastSongId = songId
    }

    fun hasTranslations(lyricsEntity: LyricsEntity?): Boolean = !lyricsEntity?.translatedLyrics.isNullOrBlank()

    fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity =
        lyricsEntity.copy(
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = "",
        )

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    fun clearCache() {
        translationCache.clear()
    }

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun cancelTranslation() {
        isCompositionActive = false
        translationJob?.cancel()
        translationJob = null
    }

    /** @return true when a stored translation matching [targetLanguage] and [mode] was applied. */
    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String,
    ): Boolean {

        lyrics.forEach { it.translatedTextFlow.value = null }

        if (lyricsEntity?.translatedLyrics.isNullOrBlank()) {
            _hasActiveTranslations.value = false
            return false
        }
        if (lyricsEntity.translationLanguage != targetLanguage) {
            _hasActiveTranslations.value = false
            return false
        }
        if (lyricsEntity.translationMode != mode) {
            _hasActiveTranslations.value = false
            return false
        }

        val translatedLines = lyricsEntity.translatedLyrics.lines()
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
            if (idx < translatedLines.size) {
                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
            }
        }

        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val cacheKey = getCacheKey(lyricsText, mode, targetLanguage)
        translationCache[cacheKey] = translatedLines
        _hasActiveTranslations.value = true
        return true
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
        database: MusicDatabase? = null,
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating

        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launchTranslation {
            try {

                val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
                if (effectiveApiKey.isBlank()) {
                    _status.value = TranslationStatus.Error(context.getString(com.example.musicfy.R.string.ai_error_api_key_required))
                    return@launchTranslation
                }

                if (lyrics.isEmpty()) {
                    _status.value = TranslationStatus.Error(context.getString(com.example.musicfy.R.string.ai_error_no_lyrics))
                    return@launchTranslation
                }

                val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
                    if (entry.text.isNotBlank()) index to entry else null
                }

                if (nonEmptyEntries.isEmpty()) {
                    _status.value = TranslationStatus.Error(context.getString(com.example.musicfy.R.string.ai_error_lyrics_empty))
                    return@launchTranslation
                }

                val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }

                val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                val cachedTranslations = translationCache[cacheKey]
                if (cachedTranslations != null && cachedTranslations.size >= nonEmptyEntries.size) {

                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                        if (idx < cachedTranslations.size) {
                            lyrics[originalIndex].translatedTextFlow.value = cachedTranslations[idx]
                        }
                    }
                    _hasActiveTranslations.value = true
                    _status.value = TranslationStatus.Success

                    if (songId.isNotBlank() && database != null) {
                        try {
                            val currentLyrics = database.lyrics(songId).first()
                            if (currentLyrics != null && currentLyrics.translatedLyrics.isNullOrBlank()) {
                                database.query {
                                    upsert(
                                        currentLyrics.copy(
                                            translatedLyrics = cachedTranslations.joinToString("\n"),
                                            translationLanguage = targetLanguage,
                                            translationMode = mode,
                                        ),
                                    )
                                }
                                _translationSaved.tryEmit(Unit)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to persist cached translations to database")
                        }
                    }

                    delay(3000)
                    if (_status.value is TranslationStatus.Success && isCompositionActive) {
                        _status.value = TranslationStatus.Idle
                    }
                    return@launchTranslation
                }

                if (targetLanguage.isBlank()) {
                    _status.value = TranslationStatus.Error(context.getString(com.example.musicfy.R.string.ai_error_language_required))
                    return@launchTranslation
                }

                val fullLanguageName = LanguageCodeToName[targetLanguage]
                    ?: try {
                        Locale.forLanguageTag(targetLanguage).displayLanguage.takeIf { it.isNotBlank() && it != targetLanguage }
                    } catch (e: Exception) { null }
                    ?: targetLanguage

                val result = if (provider == "DeepL") {
                    Timber.d("Using DeepL for translation")
                    DeepLService.translate(
                        text = fullText,
                        targetLanguage = targetLanguage,
                        apiKey = deeplApiKey,
                        formality = deeplFormality,
                    )
                } else if (provider == "Mistral") {
                    Timber.d("Using Mistral for translation")
                    MistralService.translate(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        model = model,
                        mode = mode,
                    )
                } else if (useStreaming && provider != "Custom") {
                    Timber.d("Using streaming for translation with provider: $provider")
                    var translatedLines: List<String>? = null
                    var hasError = false
                    var errorMessage = ""
                    val contentAccumulator = StringBuilder()

                    OpenRouterStreamingService.streamTranslation(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    ).collect { chunk ->
                        Timber.v("Received streaming chunk: $chunk")
                        when (chunk) {
                            is OpenRouterStreamingService.StreamChunk.Content -> {
                                contentAccumulator.append(chunk.text)

                                val partialContent = contentAccumulator.toString()
                                val partialResult = tryParsePartialTranslation(partialContent, nonEmptyEntries.size)
                                if (partialResult.isNotEmpty()) {
                                    partialResult.forEachIndexed { idx, translation ->
                                        if (idx < nonEmptyEntries.size && translation.isNotBlank()) {
                                            val originalIndex = nonEmptyEntries[idx].first
                                            lyrics[originalIndex].translatedTextFlow.value = translation
                                        }
                                    }
                                    _status.value = TranslationStatus.Translating
                                }
                            }
                            is OpenRouterStreamingService.StreamChunk.Complete -> {
                                Timber.d("Streaming complete with ${chunk.translatedLines.size} lines")
                                translatedLines = chunk.translatedLines
                            }
                            is OpenRouterStreamingService.StreamChunk.Error -> {
                                Timber.e("Streaming error: ${chunk.message}")
                                hasError = true
                                errorMessage = chunk.message
                            }
                        }
                    }

                    Timber.d("Streaming collection complete. hasError=$hasError, translatedLines=${translatedLines?.size}")
                    if (hasError) {
                        Result.failure(Exception(errorMessage))
                    } else if (translatedLines != null) {
                        Result.success(translatedLines)
                    } else {
                        Result.failure(Exception("No translation received"))
                    }
                } else {
                    Timber.d("Using non-streaming for translation")
                    OpenRouterService.translate(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    )
                }

                result.onSuccess { translatedLines ->
                    if (!isCompositionActive) {
                        return@onSuccess
                    }

                    val cacheKey2 = getCacheKey(fullText, mode, targetLanguage)
                    translationCache[cacheKey2] = translatedLines

                    if (songId.isNotBlank() && database != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val currentLyrics = database.lyrics(songId).first()
                                if (currentLyrics != null) {
                                    database.query {
                                        upsert(
                                            currentLyrics.copy(
                                                translatedLyrics = translatedLines.joinToString("\n"),
                                                translationLanguage = targetLanguage,
                                                translationMode = mode,
                                            ),
                                        )
                                    }

                                    _translationSaved.tryEmit(Unit)
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to save translated lyrics to database")
                            }
                        }
                    }

                    val expectedCount = nonEmptyEntries.size

                    when {
                        translatedLines.size >= expectedCount -> {
                            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        translatedLines.size < expectedCount -> {
                            translatedLines.forEachIndexed { idx, translation ->
                                if (idx < nonEmptyEntries.size) {
                                    val originalIndex = nonEmptyEntries[idx].first
                                    lyrics[originalIndex].translatedTextFlow.value = translation
                                }
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        else -> {
                            _status.value = TranslationStatus.Error(context.getString(com.example.musicfy.R.string.ai_error_unexpected))
                        }
                    }

                    delay(3000)
                    if (_status.value is TranslationStatus.Success && isCompositionActive) {
                        _status.value = TranslationStatus.Idle
                    }
                }.onFailure { error ->
                    if (!isCompositionActive) {
                        return@onFailure
                    }

                    val errorMessage = error.message ?: context.getString(com.example.musicfy.R.string.ai_error_unknown)
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && isCompositionActive) {
                    val errorMessage = e.message ?: context.getString(com.example.musicfy.R.string.ai_error_translation_failed)
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            }
        }
    }

    /**
     * Translates via Google Translate, which needs no API key.
     *
     * This exists so the translate toggle does something for a user who has not pasted an
     * OpenRouter or DeepL key into settings — previously that user got silence. Callers should
     * prefer this whenever no AI key is configured.
     */
    fun translateWithGoogle(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        mode: String,
        sourceLanguage: String = "auto",
        scope: CoroutineScope,
        songId: String = "",
        database: MusicDatabase? = null,
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launchTranslation {
            val indexed = lyrics.mapIndexedNotNull { index, entry ->
                if (entry.text.isNotBlank()) index to entry else null
            }
            if (indexed.isEmpty()) {
                _status.value = TranslationStatus.Idle
                return@launchTranslation
            }

            val translated = GoogleTranslateEngine.translate(
                lines = indexed.map { it.second.text },
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage.ifBlank { "auto" },
            )
            if (translated == null) {
                _status.value = TranslationStatus.Error("Translation failed")
                return@launchTranslation
            }

            indexed.forEachIndexed { idx, (originalIndex, entry) ->
                // A line already in the target language comes back unchanged; showing it twice
                // only doubles the line.
                lyrics[originalIndex].translatedTextFlow.value = translated.getOrNull(idx)
                    ?.takeUnless { it.trim().equals(entry.text.trim(), ignoreCase = true) }
            }
            _hasActiveTranslations.value = true
            _status.value = TranslationStatus.Success

            val lyricsText = indexed.joinToString("\n") { it.second.text }
            translationCache[getCacheKey(lyricsText, mode, languageTag(sourceLanguage, targetLanguage))] = translated

            if (songId.isNotBlank() && database != null) {
                try {
                    val current = database.lyrics(songId).first()
                    if (current != null) {
                        database.query {
                            upsert(
                                current.copy(
                                    translatedLyrics = translated.joinToString("\n"),
                                    translationLanguage = languageTag(sourceLanguage, targetLanguage),
                                    translationMode = mode,
                                ),
                            )
                        }
                        _translationSaved.tryEmit(Unit)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to persist Google translations")
                }
            }
        }
    }

    /**
     * Launches a translation job that cannot leave the status stuck on Translating. Jobs run in
     * the lyrics screen's scope, so leaving the screen or switching language cancels them; before,
     * a cancelled job left "Translating…" up for good, which disabled the translate button.
     */
    private fun CoroutineScope.launchTranslation(block: suspend CoroutineScope.() -> Unit): Job {
        val job = launch(Dispatchers.IO, block = block)
        job.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException &&
                translationJob === job &&
                _status.value is TranslationStatus.Translating
            ) {
                _status.value = TranslationStatus.Idle
            }
        }
        return job
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}
