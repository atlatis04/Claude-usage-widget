package dev.local.claudeusage

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import kotlin.coroutines.resume

object Browser {
    /** WebView 표시를 뺀 일반 Chrome User-Agent (로그인 화면과 API 요청에 같은 값 사용) */
    fun userAgent(ctx: Context): String =
        WebSettings.getDefaultUserAgent(ctx)
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+ "), "")

    /** WebView 쿠키 저장소에 있는 claude.ai 쿠키 전체 (sessionKey + Cloudflare 쿠키) */
    fun cookies(): String? = runCatching {
        CookieManager.getInstance().getCookie("https://claude.ai")
    }.getOrNull()

    /** 직접 입력한 세션 키를 WebView 쿠키에도 넣어 둔다 */
    fun putSessionCookie(key: String) {
        Handler(Looper.getMainLooper()).post {
            CookieManager.getInstance().apply {
                setCookie("https://claude.ai", "sessionKey=$key; Domain=.claude.ai; Path=/; Secure")
                flush()
            }
        }
    }

    /**
     * 화면에 보이지 않는 WebView로 URL을 열어 본문(JSON)을 읽는다.
     * Cloudflare 확인 페이지가 나오면 브라우저처럼 자동 통과한 뒤 JSON이 뜰 때까지 기다린다.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchText(ctx: Context, url: String, timeoutMs: Long = 30_000): String =
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val main = Handler(Looper.getMainLooper())
                var web: WebView? = null
                var finished = false

                fun finish(text: String) {
                    if (finished) return
                    finished = true
                    web?.apply { stopLoading(); destroy() }
                    web = null
                    if (cont.isActive) cont.resume(text)
                }

                cont.invokeOnCancellation {
                    main.post {
                        if (!finished) {
                            finished = true
                            web?.apply { stopLoading(); destroy() }
                            web = null
                        }
                    }
                }

                main.post {
                    val wv = WebView(ctx.applicationContext)
                    web = wv
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.userAgentString = userAgent(ctx)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, u: String) {
                            view.evaluateJavascript(
                                "(function(){return document.body ? document.body.innerText : ''})()"
                            ) { raw ->
                                val text = runCatching { JSONArray("[$raw]").getString(0) }
                                    .getOrDefault("").trim()
                                if (text.startsWith("{") || text.startsWith("[")) finish(text)
                                // 아니면 Cloudflare 확인 중 → 다음 페이지 로드를 기다림
                            }
                        }
                    }
                    wv.loadUrl(url)
                }
            }
        }
}
