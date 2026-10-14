package dev.local.claudeusage

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * claude.ai 로그인 화면을 WebView로 띄우고, 로그인되면 sessionKey 쿠키를 가져온다.
 *
 * - Google 로그인: WebView 표시("; wv")를 뺀 User-Agent를 쓰고, 팝업 창을 처리한다.
 * - 로그인 후 페이지 전환이 없어도 잡을 수 있게 1초마다 쿠키를 확인한다.
 */
class LoginActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var container: FrameLayout
    private var popup: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    private val poller = object : Runnable {
        override fun run() {
            if (done) return
            tryCapture()
            handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 안내 + 수동 확인 버튼
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(Color.parseColor("#262624"))
        }
        bar.addView(TextView(this).apply {
            text = "Google 로그인이 막히면 '이메일로 계속하기'를 쓰세요.\n(Google 계정 메일로 받은 코드로도 로그인돼요)"
            setTextColor(Color.WHITE)
            textSize = 12f
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply {
            text = "완료"
            setOnClickListener {
                if (!tryCapture()) {
                    Toast.makeText(this@LoginActivity, "아직 로그인 정보가 없어요", Toast.LENGTH_SHORT).show()
                }
            }
        })
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        web = newWebView()
        container.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.loadUrl("https://claude.ai/login")
        handler.postDelayed(poller, 1000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // Google은 "; wv"가 붙은 WebView UA를 차단하므로 일반 Chrome UA로 보이게 한다
        settings.userAgentString = Browser.userAgent(this@LoginActivity)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                tryCapture()
            }
        }
        webChromeClient = object : WebChromeClient() {
            // Google 로그인 팝업을 같은 화면 위에 띄운다
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
            ): Boolean {
                closePopup()
                val child = newWebView()
                popup = child
                container.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                if (window == popup) {
                    closePopup()
                    // 팝업 로그인이 끝나면 원래 화면을 새로고침해 세션을 반영
                    web.reload()
                }
            }
        }
    }

    private fun closePopup() {
        popup?.let {
            container.removeView(it)
            it.destroy()
        }
        popup = null
    }

    /** sessionKey 쿠키가 있으면 저장하고 닫는다. 찾았으면 true */
    private fun tryCapture(): Boolean {
        if (done) return true
        val cm = CookieManager.getInstance()
        cm.flush()
        val cookies = cm.getCookie("https://claude.ai") ?: return false
        val key = cookies.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("sessionKey=") }
            ?.removePrefix("sessionKey=")
            ?.takeIf { it.isNotBlank() } ?: return false

        done = true
        handler.removeCallbacks(poller)
        Prefs.setSessionKey(this, key)
        UsageWorker.schedule(this)
        UsageWorker.refreshNow(this)
        Toast.makeText(this, "로그인 완료! 사용량을 불러옵니다", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            popup != null -> {
                if (popup!!.canGoBack()) popup!!.goBack() else closePopup()
            }
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        closePopup()
        web.destroy()
        super.onDestroy()
    }
}
