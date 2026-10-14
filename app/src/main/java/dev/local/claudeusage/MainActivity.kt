package dev.local.claudeusage

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private lateinit var loginState: TextView
    private lateinit var status: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        root.addView(TextView(this).apply {
            text = "Claude 사용량 위젯"
            textSize = 22f
        })
        loginState = TextView(this).apply {
            textSize = 17f
            setPadding(0, pad / 2, 0, 0)
        }
        root.addView(loginState)
        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, pad / 3, 0, pad)
        }
        root.addView(status)

        btn("로그인 상태 확인") { checkLogin() }
        btn("claude.ai 로그인") {
            startActivityForResult(Intent(this, LoginActivity::class.java), 1)
        }
        btn("세션 키 직접 입력") { askKey() }
        btn("지금 새로고침") {
            UsageWorker.refreshNow(this)
            status.text = "새로고침 중… 잠시 후 위젯을 확인하세요"
        }
        btn("로그아웃") {
            Prefs.clear(this)
            CookieManager.getInstance().removeAllCookies(null)
            UsageWorker.cancel(this)
            UsageWidget.renderAll(this)
            refreshStatus()
        }
        root.addView(TextView(this).apply {
            text = "\n위젯 추가: 홈 화면(DeX 바탕화면) 빈 곳 길게 누르기 → 위젯 → 'Claude 사용량'\n" +
                "위젯의 ⟳ 버튼 = 즉시 새로고침, 위젯 본문 탭 = 이 화면 열기\n" +
                "자동 갱신은 15분 간격입니다."
            textSize = 13f
        })
        setContentView(root)
        refreshStatus()
    }

    override fun onResume() { super.onResume(); refreshStatus() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refreshStatus()
        if (resultCode == RESULT_OK) checkLogin()
    }

    private fun refreshStatus() {
        val (label, color) = when (Prefs.authState(this)) {
            AuthState.NONE -> "● 로그인 안 됨" to "#9E9E9E"
            AuthState.EXPIRED -> "● 로그인 만료 — 다시 로그인하세요" to "#E5534B"
            AuthState.OK -> "● 로그인됨" + (Prefs.account(this)?.let { " ($it)" } ?: "") to "#2E9E4F"
        }
        loginState.text = label
        loginState.setTextColor(Color.parseColor(color))

        val s = Prefs.loadSnapshot(this)
        status.text = buildString {
            s.fivePct?.let { append("5시간: ${it.toInt()}%  ${UsageWidget.resetLabel(s.fiveReset, true)}\n") }
            s.weekPct?.let { append("주간: ${it.toInt()}%  ${UsageWidget.resetLabel(s.weekReset, false)}\n") }
            s.error?.let { append("⚠ $it") }
        }
    }

    /** 실제로 claude.ai에 요청해 로그인 상태를 확인 */
    private fun checkLogin() {
        if (Prefs.sessionKey(this) == null) {
            refreshStatus(); return
        }
        loginState.text = "● 확인 중…"
        loginState.setTextColor(Color.GRAY)
        scope.launch {
            try {
                val name = UsageApi.checkLogin(this@MainActivity)
                Prefs.setAuthState(this@MainActivity, AuthState.OK)
                refreshStatus()
                loginState.text = "● 로그인됨 ($name)"
                UsageWorker.refreshNow(this@MainActivity)
            } catch (e: AuthException) {
                Prefs.setAuthState(this@MainActivity, AuthState.EXPIRED)
                refreshStatus()
            } catch (e: Exception) {
                refreshStatus()
                status.append("\n⚠ 확인 실패: ${e.message ?: e.javaClass.simpleName}")
            }
            UsageWidget.renderAll(this@MainActivity)
        }
    }

    /** PC 브라우저 개발자도구 > 쿠키에서 sessionKey 값을 복사해 붙여넣는 대안 */
    private fun askKey() {
        val input = EditText(this).apply {
            hint = "sk-ant-sid01-…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("sessionKey 입력")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val k = input.text.toString().trim().removePrefix("sessionKey=")
                if (k.isNotEmpty()) {
                    Prefs.setSessionKey(this, k)
                    Browser.putSessionCookie(k)
                    UsageWorker.schedule(this)
                    checkLogin()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
