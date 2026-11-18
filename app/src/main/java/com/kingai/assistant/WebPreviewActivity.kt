package com.kingai.assistant

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** پیش‌نمایش آفلاین HTML تولیدشده؛ دسترسی فایل و شبکه برای محتوای پیش‌نمایش بسته است. */
class WebPreviewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_HTML = "com.kingai.assistant.EXTRA_PREVIEW_HTML"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Neon.BLACK
        window.navigationBarColor = Neon.BLACK

        val html = intent.getStringExtra(EXTRA_HTML).orEmpty()
        if (html.isBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Neon.BLACK)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val title = TextView(this).apply {
            text = "پیش‌نمایش وب‌سایت"
            textSize = 17f
            setTextColor(Neon.GREEN)
            setPadding(0, dp(8), 0, dp(8))
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this).apply {
            text = "بستن"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { finish() }
        }
        header.addView(close)
        root.addView(header)

        val web = WebView(this)
        web.setBackgroundColor(Color.WHITE)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        web.loadDataWithBaseURL("https://king-ai.local/", html, "text/html", "UTF-8", null)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
