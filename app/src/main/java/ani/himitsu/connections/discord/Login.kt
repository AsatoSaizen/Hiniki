package ani.himitsu.connections.discord

import android.annotation.SuppressLint
import android.app.Application.getProcessName
import android.content.ComponentName
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import ani.himitsu.R
import ani.himitsu.connections.discord.Discord.saveToken
import ani.himitsu.restart
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.themes.ThemeManager
import bit.himitsu.os.Version

class Login : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        if (Version.isPie) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }
        setContentView(R.layout.activity_discord)

        val webView = findViewById<WebView>(R.id.discordWebview)

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
                if (request?.url.toString() != "https://discord.com/login") {
                    view?.isInvisible = true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Check if the URL is the one expected after a successful login
                if (url?.toString() != "https://discord.com/login") {
                    // Delay the script execution to ensure the page is fully loaded
                    view?.evaluateJavascript(
                        """
                    (function() {
                        const wreq = (webpackChunkdiscord_app.push([[''],{},e=>{m=[];for(let c in e.c)m.push(e.c[c])}]),m).find(m=>m?.exports?.default?.getToken!==void 0).exports.default.getToken();
                        return wreq;
                    })()
                """.trimIndent()
                    ) { result ->
                        login(result.trim('"'))
                    }
                }
            }
        }

        webView.loadUrl("https://discord.com/login")
    }

    private fun login(token: String) {
        if (token.isEmpty() || token == "null") {
            Toast.makeText(this, getString(R.string.token_retrieve_failed), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        saveToken(token)
        restart(ComponentName(packageName, SettingsActivity::class.qualifiedName!!))
    }

}
