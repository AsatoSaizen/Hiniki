package ani.himitsu.others.webview

import android.annotation.SuppressLint
import android.app.Application
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import ani.himitsu.R
import ani.himitsu.themes.ThemeManager
import bit.himitsu.os.Version
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.getSerializableExtraCompat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CookieCatcher : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()

        //get url from intent
        val url = intent.getStringExtra("url") ?: getString(R.string.cursed_yt)
        val headers: Map<String, String> =
            intent.getSerializableExtraCompat("headers") as? Map<String, String> ?: emptyMap()

        if (Version.isPie) {
            val process = Application.getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }
        setContentView(R.layout.activity_discord)

        val webView = findViewById<WebView>(R.id.discordWebview)

        val cookies: CookieManager? = Injekt.get<NetworkHelper>().cookieJar.manager
        cookies?.setAcceptThirdPartyCookies(webView, true)

        webView.apply {
            settings.javaScriptEnabled = true
            settings.databaseEnabled = true
            settings.domStorageEnabled = true
        }
        WebView.setWebContentsDebuggingEnabled(true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }

        webView.loadUrl(url, headers)
    }

}