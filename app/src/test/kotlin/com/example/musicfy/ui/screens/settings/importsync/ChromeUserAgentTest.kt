package com.example.musicfy.ui.screens.settings.importsync

import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeUserAgentTest {

    @Test
    fun webViewMarkersAreRemoved() {
        val webView = "Mozilla/5.0 (Linux; Android 13; 2201116SG Build/TP1A.220624.014; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/129.0.6668.100 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; 2201116SG Build/TP1A.220624.014) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/129.0.6668.100 Mobile Safari/537.36",
            chromeUserAgent(webView),
        )
    }

    @Test
    fun chromeAgentIsUnchanged() {
        val chrome = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        assertEquals(chrome, chromeUserAgent(chrome))
    }
}
