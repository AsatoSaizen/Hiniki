package ani.himitsu.connections.discord

import android.content.Context
import android.content.Intent
import android.widget.TextView
import ani.himitsu.R
import ani.himitsu.connections.discord.RPC.Asset
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.tryWith
import ani.himitsu.view.dialog.CustomBottomDialog
import eu.kanade.tachiyomi.network.awaitResume
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object Discord {

    var token: String? = null
    var userid: String? = null
    var avatar: String? = null

    fun getSavedToken(): Boolean {
        token = PrefManager.getNullableVal<String>(PrefName.DiscordToken, null)
        return token != null
    }

    fun saveToken(token: String) {
        PrefManager.setVal(PrefName.DiscordToken, token)
    }

    fun removeSavedToken(context: Context, callback: () -> Unit) {
        PrefManager.removeVal(PrefName.DiscordToken)

        tryWith(true) {
            val dir = File(context.filesDir?.parentFile, "app_webview")
            if (dir.deleteRecursively())
                toast(context.getString(R.string.discord_logout_success))
        }

        callback.invoke()
    }

    fun warning(context: Context) = CustomBottomDialog().apply {
        title = context.getString(R.string.warning)
        val md = context.getString(R.string.discord_warning)
        addView(TextView(context).apply {
            val markWon =
                Markwon.builder(context).usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
            markWon.setMarkdown(this, md)
        })

        setNegativeButton(context.getString(R.string.cancel)) {
            dismiss()
        }

        setPositiveButton(context.getString(R.string.login)) {
            dismiss()
            loginIntent(context)
        }
    }

    private fun loginIntent(context: Context) {
        val intent = Intent(context, Login::class.java)
        context.startActivity(intent)
    }

    enum class MODE {
        HIMITSU,
        ANILIST,
        NOTHING
    }

    private const val TIMEOUT = 10L

    val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
    }

    suspend fun String.discordUri(): String? = token?.let { token ->
        if (this.startsWith("mp:")) return this
        val request = Request.Builder()
            .url("https://discord.com/api/v9/applications/${application_Id}/external-assets")
            .header("Authorization", token)
            .post("{\"urls\":[\"$this\"]}".toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            val res = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build().newCall(request).awaitResume()
            json.decodeFromString<List<Asset>>(res.body.string())
                .firstOrNull()?.externalAssetPath?.let { "mp:$it" }
        }.getOrNull()
    }

    const val application_Id = "1243752355502227517"

    const val himitsu_small: String =
        "mp:external/Hb3tbwkuWo6-CxOCTV4QtWTFB0ibQmOfpfTm40NrOqM/https/cdn.discordapp.com/app-icons/1243752355502227517/521614c3e2484797248c45e7438fea4e.png"
}