package com.ihy2ln.weaverse.core.manga

import android.content.Context
import android.content.ContextWrapper
import android.webkit.CookieManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.feature.storyboard.GalleryAccountSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class GalleryAccountDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = UUID.randomUUID().toString()
    private val isolated = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$name-$suffix", mode)
    }
    private fun manager() = GalleryAccountManager(isolated, SecureKeyStore(isolated))

    @Test fun sourceOptInAndLoginAreSeparateAndPersist() {
        val account = manager()
        try {
            compose.setContent { MaterialTheme { GalleryAccountSettings(account) {} } }
            compose.onNodeWithText("Log in on website").assertIsNotEnabled()
            compose.onAllNodes(isToggleable())[0].performClick()
            compose.onNodeWithText("Log in on website").assertIsEnabled()
            compose.onAllNodes(isToggleable())[1].assertIsNotEnabled()
            compose.runOnIdle {
                assertTrue(manager().state.value.enabled)
                assertFalse(manager().state.value.sessionSaved)
                assertFalse(manager().state.value.restricted)
            }
        } finally { cleanup() }
    }

    @Test fun fixtureSessionIsScopedAndLogoutPreservesOtherSourceCookies() {
        val account = manager()
        try {
            compose.runOnUiThread {
                val cookies = CookieManager.getInstance()
                cookies.setCookie("https://e-hentai.org/", "ipb_member_id=123; Path=/; Secure")
                cookies.setCookie("https://e-hentai.org/", "ipb_pass_hash=fixture-only-not-a-real-session; Path=/; Secure")
                cookies.setCookie("https://example.test/", "other_source=keep; Path=/; Secure")
                cookies.flush()
                account.enable(true)
                assertTrue(account.saveWebSession())
                account.enableRestricted(true)
                assertTrue(manager().state.value.sessionSaved)
                assertTrue(manager().state.value.restricted)
                assertNotNull(account.cookieHeader("https://exhentai.org/"))
                assertNull(account.cookieHeader("https://example.test/"))
                assertNull(account.cookieHeader("http://e-hentai.org/"))
                account.logout()
                assertFalse(account.state.value.sessionSaved)
                assertFalse(manager().state.value.restricted)
                assertFalse(GallerySessionPolicy.loggedIn(cookies.getCookie("https://e-hentai.org/")))
                assertTrue(cookies.getCookie("https://example.test/").contains("other_source=keep"))
                cookies.setCookie("https://example.test/", "other_source=; Path=/; Max-Age=0; Secure")
            }
        } finally { cleanup() }
    }

    private fun cleanup() {
        context.deleteSharedPreferences("gallery-account-options-$suffix")
        context.deleteSharedPreferences("weaverse_secrets-$suffix")
    }
}
