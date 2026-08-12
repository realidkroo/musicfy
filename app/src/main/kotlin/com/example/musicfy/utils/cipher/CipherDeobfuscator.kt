// cipherdeobfuscatorkt
// this thing is for cipher deobfuscator

package com.example.musicfy.utils.cipher

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object CipherDeobfuscator {
    private const val TAG = "musicfy_CipherDeobfusc"

    lateinit var appContext: Context
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private var cipherWebView: CipherWebView? = null
    private var currentPlayerHash: String? = null

    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        return try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Cipher deobfuscation failed, retrying with fresh JS: ${e.message}")
            // invalidate cache and retry once with fresh player js
            try {
                PlayerJsFetcher.invalidateCache()
                closeWebView()
                deobfuscateInternal(signatureCipher, videoId, isRetry = true)
            } catch (retryE: Exception) {
                Timber.tag(TAG).e(retryE, "Cipher deobfuscation retry also failed: ${retryE.message}")
                null
            }
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String, isRetry: Boolean): String? {
        // parse the signaturecipher query string
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"]
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"]

        if (obfuscatedSig == null || baseUrl == null) {
            Timber.tag(TAG).e("Could not parse signatureCipher params: s=${obfuscatedSig != null}, url=${baseUrl != null}")
            return null
        }

        Timber.tag(TAG).d("Deobfuscating cipher for $videoId: sig=${obfuscatedSig.take(20)}..., sp=$sigParam")

        val webView = getOrCreateWebView(forceRefresh = isRetry)
            ?: return null

        // deobfuscate signature
        val deobfuscatedSig = webView.deobfuscateSignature(obfuscatedSig)

        // build the url with deobfuscated signature
        val separator = if ("?" in baseUrl) "&" else "?"
        val finalUrl = "$baseUrl${separator}${sigParam}=${Uri.encode(deobfuscatedSig)}"

        Timber.tag(TAG).d("Custom cipher deobfuscation succeeded for $videoId")
        return finalUrl
    }

    // transform the 'n' parameter in a streaming url to avoid throttling/403 uses the
    suspend fun transformNParamInUrl(url: String): String {
        return try {
            transformNInternal(url)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "N-transform failed, returning original URL: ${e.message}")
            url
        }
    }

    private suspend fun transformNInternal(url: String): String {
        // extract the 'n' parameter value from the url
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            Timber.tag(TAG).d("No 'n' parameter found in URL, skipping transform")
            return url
        }
        val nValue = Uri.decode(nMatch.groupValues[1])
        Timber.tag(TAG).d("N-param found: $nValue")

        val webView = getOrCreateWebView(forceRefresh = false) ?: return url

        if (!webView.nFunctionAvailable) {
            Timber.tag(TAG).e("N-transform function was not discovered at init time")
            return url
        }

        val transformedN = webView.transformN(nValue)
        Timber.tag(TAG).d("N-param transformed: $nValue -> $transformedN")

        // replace n= parameter in url
        return url.replaceFirst(
            Regex("([?&])n=[^&]+"),
            "$1n=${Uri.encode(transformedN)}"
        )
    }

    private suspend fun getOrCreateWebView(forceRefresh: Boolean): CipherWebView? {
        if (!forceRefresh && cipherWebView != null) {
            return cipherWebView
        }

        // close existing webview if any
        if (cipherWebView != null) {
            closeWebView()
        }

        // fetch player js
        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            Timber.tag(TAG).e("Failed to get player JS")
            return null
        }
        val (playerJs, hash) = result

        // extract signature function info
        val sigInfo = FunctionNameExtractor.extractSigFunctionInfo(playerJs)

        if (sigInfo == null) {
            Timber.tag(TAG).e("Could not extract signature function info from player JS")
            return null
        }

        // extract n-transform function info (for throttle avoidance / 403 fix)
        val nFuncInfo = FunctionNameExtractor.extractNFunctionInfo(playerJs)
        if (nFuncInfo == null) {
            Timber.tag(TAG).e("Could not extract n-function info from player JS (will try brute-force)")
        }

        Timber.tag(TAG).d("Creating CipherWebView with sig=${sigInfo.name}, constantArg=${sigInfo.constantArg}, nFunc=${nFuncInfo?.name}[${nFuncInfo?.arrayIndex}]")

        // create webview — n-function is exported to window if found with
        val webView = CipherWebView.create(
            context = appContext,
            playerJs = playerJs,
            sigInfo = sigInfo,
            nFuncInfo = nFuncInfo,
        )

        cipherWebView = webView
        currentPlayerHash = hash
        return webView
    }

    private suspend fun closeWebView() {
        withContext(Dispatchers.Main) {
            cipherWebView?.close()
        }
        cipherWebView = null
        currentPlayerHash = null
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = Uri.decode(pair.substring(0, idx))
                val value = Uri.decode(pair.substring(idx + 1))
                result[key] = value
            }
        }
        return result
    }
}
