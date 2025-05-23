package ani.dantotsu.addons

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import ani.himitsu.Mapper
import ani.himitsu.R
import ani.himitsu.client
import ani.himitsu.logError
import ani.himitsu.openLinkInBrowser
import ani.himitsu.settings.InstallerSteps
import ani.himitsu.toast
import ani.himitsu.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import rx.android.schedulers.AndroidSchedulers
import java.text.SimpleDateFormat
import java.util.Locale

object AddonDownloader {
    private suspend fun check(repo: String): Pair<String, String> {
        return try {
            val res = client.get("https://api.github.com/repos/$repo/releases")
                .parsed<JsonArray>().map {
                    Mapper.json.decodeFromJsonElement<GithubResponse>(it)
                }
            val r = res.maxByOrNull {
                it.timeStamp()
            } ?: throw Exception("No Pre Release Found")
            val v = r.tagName.substringAfter("v", "")
            val md = r.body ?: ""
            val version = v.ifEmpty { throw Exception("Weird Version : ${r.tagName}") }

            Logger.log("Git Version for $repo: $version")
            Pair(md, version)
        } catch (e: Exception) {
            Logger.log("Error checking for update")
            Logger.log(e)
            Pair("", "")
        }
    }

    suspend fun hasUpdate(repo: String, currentVersion: String): Boolean {
        val (_, version) = check(repo)
        return compareVersion(version, currentVersion)
    }

    suspend fun update(
        activity: Activity,
        manager: AddonManager<*>,
        repo: String,
        currentVersion: String
    ) {
        val (_, version) = check(repo)
        if (!compareVersion(version, currentVersion)) {
            toast(activity.getString(R.string.no_update_found))
            return
        }
        MainScope().launch(Dispatchers.IO) {
            try {
                client.get("https://api.github.com/repos/$repo/releases/tags/v$version")
                    .parsed<GithubResponse>().assets?.run {
                        find { it.browserDownloadURL.contains(getCurrentABI()) }
                            ?: find { it.browserDownloadURL.contains("universal") }
                            ?: first { it.browserDownloadURL.endsWith(".apk") }
                    }?.browserDownloadURL?.apply {
                        val notificationManager =
                            activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val installerSteps = InstallerSteps(notificationManager, activity)
                        manager.install(this)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                { installStep -> installerSteps.onInstallStep(installStep) {} },
                                { error -> installerSteps.onError(error) {} },
                                { installerSteps.onComplete {} }
                            )
                    } ?: openLinkInBrowser("https://github.com/repos/$repo/releases/tag/v$version")
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    /**
     * Returns the ABI that the app is most likely running on.
     * @return The primary ABI for the device.
     */
    private fun getCurrentABI(): String {
        return if (Build.SUPPORTED_ABIS.isNotEmpty()) {
            Build.SUPPORTED_ABIS[0]
        } else "Unknown"
    }

    private fun compareVersion(newVersion: String, oldVersion: String): Boolean {
        fun toDouble(list: List<String>): Double {
            return try {
                list.mapIndexed { i: Int, s: String ->
                    when (i) {
                        0 -> s.toDouble() * 100
                        1 -> s.toDouble() * 10
                        2 -> s.toDouble()
                        else -> s.toDoubleOrNull() ?: 0.0
                    }
                }.sum()
            } catch (e: NumberFormatException) {
                0.0
            }
        }

        val new = toDouble(newVersion.split("."))
        val curr = toDouble(oldVersion.split("."))
        return new > curr
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    @Serializable
    data class GithubResponse(
        @SerialName("html_url")
        val htmlUrl: String,
        @SerialName("tag_name")
        val tagName: String,
        val prerelease: Boolean,
        @SerialName("created_at")
        val createdAt: String,
        val body: String? = null,
        val assets: List<Asset>? = null
    ) {
        @Serializable
        data class Asset(
            @SerialName("browser_download_url")
            val browserDownloadURL: String
        )

        fun timeStamp(): Long {
            return dateFormat.parse(createdAt)!!.time
        }
    }
}