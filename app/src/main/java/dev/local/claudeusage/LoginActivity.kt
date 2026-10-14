package dev.local.claudeusage

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * claude.ai 로그인 화면을 WebView로 띄우고, 로그인되면 sessionKey 쿠키를 가져온다.
 * (Google 계정 로그인은 WebView에서 막히므로 이메일 로그인을 사용)
 */
class LoginActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                tryCapture()
            }
        }
        web.loadUrl("https://claude.ai/login")
    }

    private fun tryCapture() {
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return
        val key = cookies.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("sessionKey=") }
            ?.removePrefix("sessionKey=") ?: return
        if (key.isBlank()) return
        Prefs.setSessionKey(this, key)
        UsageWorker.schedule(this)
        UsageWorker.refreshNow(this)
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
