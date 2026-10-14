package dev.local.claudeusage

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

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
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, pad / 2, 0, pad)
        }
        root.addView(status)

        btn("claude.ai 로그인") {
            startActivityForResult(Intent(this, LoginActivity::class.java), 1)
        }
        btn("세션 키 직접 입력") { askKey() }
        btn("지금 새로고침") {
            UsageWorker.refreshNow(this)
            status.text = "새로고침 요청함… 잠시 후 위젯을 확인하세요"
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
                "위젯을 탭하면 즉시 새로고침, 자동 갱신은 15분 간격입니다."
            textSize = 13f
            gravity = Gravity.START
        })
        setContentView(root)
        refreshStatus()
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refreshStatus()
    }

    private fun refreshStatus() {
        val s = Prefs.loadSnapshot(this)
        status.text = if (Prefs.sessionKey(this) == null) "로그인 안 됨"
        else buildString {
            append("로그인됨\n")
            s.fivePct?.let { append("5시간: ${it.toInt()}%  ${UsageWidget.resetLabel(s.fiveReset, true)}\n") }
            s.weekPct?.let { append("주간: ${it.toInt()}%  ${UsageWidget.resetLabel(s.weekReset, false)}\n") }
            s.error?.let { append("⚠ $it") }
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
                    UsageWorker.schedule(this)
                    UsageWorker.refreshNow(this)
                    status.text = "저장됨, 불러오는 중…"
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
