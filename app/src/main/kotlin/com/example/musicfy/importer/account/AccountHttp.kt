// AccountHttp.kt

package com.example.musicfy.importer.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal object AccountHttp {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }

    class Response(val status: Int, val body: String, val retryAfterSeconds: Long?)

    suspend fun get(url: String, headers: Map<String, String>, service: String): Response = try {
        val response = client.get(url) { headers.forEach { (name, value) -> header(name, value) } }
        Response(response.status.value, response.bodyAsText(), response.headers["Retry-After"]?.trim()?.toLongOrNull())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AccountImportException("Couldn't reach $service. Check your connection.")
    }

    fun parse(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    /**
     * A library read: waits out rate limits, and turns failures into messages a user can act on.
     * Returns null for a 404 when [notFoundAsNull], which some services use for an empty list.
     */
    suspend fun getJson(
        url: String,
        headers: Map<String, String>,
        service: String,
        notFoundAsNull: Boolean = false,
    ): JsonObject? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = get(url, headers, service)
            when {
                response.status in 200..299 ->
                    return parse(response.body) ?: throw AccountImportException("$service sent something we couldn't read.")
                response.status == 429 || response.status == 502 || response.status == 503 -> {
                    val waitSeconds = (response.retryAfterSeconds ?: (2L shl attempt)).coerceIn(1, 30)
                    delay(waitSeconds * 1000)
                }
                response.status == 401 ->
                    throw AccountImportException("$service signed you out. Sign in again.", signedOut = true)
                response.status == 404 && notFoundAsNull -> return null
                response.status == 403 ->
                    throw AccountImportException("$service won't share this one (403).")
                else -> throw AccountImportException("$service answered with an error (${response.status}).")
            }
        }
        throw AccountImportException("$service is busy right now. Try again in a minute.")
    }

    private const val MAX_ATTEMPTS = 5
}
