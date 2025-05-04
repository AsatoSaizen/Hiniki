package ani.himitsu

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.dantotsu.connections.comments.CommentsAPI
import ani.himitsu.aniyomi.anime.custom.AppModule
import ani.himitsu.aniyomi.anime.custom.PreferenceModule
import ani.himitsu.connections.discord.DiscordService
import ani.himitsu.notifications.TaskScheduler
import ani.himitsu.parsers.AnimeSources
import ani.himitsu.parsers.MangaSources
import ani.himitsu.parsers.NovelSources
import ani.himitsu.parsers.novel.NovelExtensionManager
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.settings.saving.internal.Location
import ani.himitsu.util.Logger
import bit.himitsu.cloudflare.Subtitles.getTypefaceList
import bit.himitsu.content.ScaledContext
import bit.himitsu.content.stopRunningService
import bit.himitsu.firebase.getDefaultFirebaseApp
import bit.himitsu.io.Debug
import bit.himitsu.webkit.ChromeIntegration
import com.google.android.material.color.DynamicColors
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.PrintWriter
import java.io.StringWriter

class Himitsu : MultiDexApplication() {
    private lateinit var animeExtensionManager: AnimeExtensionManager
    private lateinit var mangaExtensionManager: MangaExtensionManager
    private lateinit var novelExtensionManager: NovelExtensionManager
    private lateinit var downloadAddonManager: DownloadAddonManager

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    init {
        instance = this
    }

    val mFTActivityLifecycleCallbacks = FTActivityLifecycleCallbacks()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        Injekt.importModule(AppModule(this))
        Injekt.importModule(PreferenceModule(this))
        PrefManager.init(this)

        getDefaultFirebaseApp()

        Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, error: Throwable ->
            Firebase.crashlytics.recordException(error)
            StringWriter().apply { error.printStackTrace(PrintWriter(this)) }.toString().also {
                try { Debug.saveException(this, it) } catch (_: Exception) { }
            }
            try { Logger.uncaughtException(thread, error) } catch (_: Exception) { }
            try {
                DiscordService::class.java.stopRunningService(this)
            } catch (_: Exception) { }
//            android.os.Process.killProcess(android.os.Process.myPid())
//            kotlin.system.exitProcess(-1)
        }
        Logger.init(this)

        registerActivityLifecycleCallbacks(mFTActivityLifecycleCallbacks)

        ScaledContext(this).apply {
            if (PrefManager.getVal(PrefName.SmallVille))
                screen(PrefManager.getVal(PrefName.LoisLane))
            else
                restore()
        }.setTheme(R.style.Theme_Main)

        val uiModeManager: UiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isAndroidTV = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        val layouts = PrefManager.getVal<List<Boolean>>(PrefName.HomeLayout)
        if (layouts.size == 8) PrefManager.setVal(PrefName.HomeLayout, listOf(false).plus(layouts))

        val useMaterialYou: Boolean = PrefManager.getVal(PrefName.UseMaterialYou)
        if (useMaterialYou) { DynamicColors.applyToActivitiesIfAvailable(this) }

        initializeNetwork()

        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            Logger.log(e)
        }

        if (!LogcatLogger.isInstalled) {
            LogcatLogger.install(AndroidLogcatLogger(
                if (PrefManager.getVal(PrefName.Lightspeed))
                    LogPriority.ERROR
                else
                    LogPriority.DEBUG)
            )
        }

        installTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime

        launchIO {
            awaitAll(
                async(Dispatchers.IO) { loadAnimeExtensions() },
                async(Dispatchers.IO) { loadMangaExtensions() },
                async(Dispatchers.IO) { loadNovelExtensions() }
            )

            val useAlarmManager = PrefManager.getVal<Boolean>(PrefName.UseAlarmManager)
            val scheduler = TaskScheduler.create(this@Himitsu, useAlarmManager)
            try {
                scheduler.scheduleAllTasks(this@Himitsu)
            } catch (e: IllegalStateException) {
                Logger.log("Failed to schedule tasks")
                Logger.log(e)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            ChromeIntegration.hasCustomTabs = ChromeIntegration.bindCustomTabService(this@Himitsu)

            downloadAddonManager = Injekt.get()
            downloadAddonManager.init()

            if (PrefManager.getVal(PrefName.CommentsOptIn))
                CommentsAPI.fetchAuthToken(this@Himitsu)
        }

        launchIO { getTypefaceList() }
    }

    private suspend fun loadAnimeExtensions() {
        animeExtensionManager = Injekt.get()
        animeExtensionManager.findAvailableExtensions()
        AnimeSources.init(animeExtensionManager.installedExtensionsFlow)
    }

    private suspend fun loadMangaExtensions() {
        mangaExtensionManager = Injekt.get()
        mangaExtensionManager.findAvailableExtensions()
        MangaSources.init(mangaExtensionManager.installedExtensionsFlow)
    }

    private suspend fun loadNovelExtensions() {
        novelExtensionManager = Injekt.get()
        novelExtensionManager.findAvailableExtensions()
        NovelSources.init(novelExtensionManager.installedExtensionsFlow)
        novelExtensionManager.findAvailablePlugins()
    }

    fun clearObsoletePrefs() {
        PrefManager.removeKey(Location.Irrelevant, "ImageUrl")
        PrefManager.removeKey(Location.Player, "CursedSpeeds")
        PrefManager.removeKey(Location.Player, "DefaultSpeed")
        PrefManager.removeKey(Location.Player, "Outline")
        PrefManager.removeKey(Location.Player, "PlaybackSpeed")
        PrefManager.removeKey(Location.Player, "TimeStampsEnabled")
        PrefManager.removeKey(Location.Player, "UseProxyForTimeStamps")
        PrefManager.removeKey(Location.UI, "SmallView")
    }

    inner class FTActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        var currentActivity: Activity? = null
        override fun onActivityCreated(p0: Activity, p1: Bundle?) {}
        override fun onActivityStarted(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityResumed(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityPaused(p0: Activity) {}
        override fun onActivityStopped(p0: Activity) {}
        override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
        override fun onActivityDestroyed(p0: Activity) {}
    }

    companion object {
        /** Reference to the application instance.
         *
         * USE WITH EXTREME CAUTION!**/
        @JvmStatic
        lateinit var instance: Himitsu
            private set

        fun currentContext(): Context {
            return ScaledContext(
                instance.mFTActivityLifecycleCallbacks.currentActivity
                    ?: instance.applicationContext
            ).apply {
                if (PrefManager.getVal(PrefName.SmallVille))
                    screen(PrefManager.getVal(PrefName.LoisLane))
                else
                    restore()
            }
        }

        fun currentActivity(): Activity? {
            return instance.mFTActivityLifecycleCallbacks.currentActivity
        }

        val appName: String = this::class.java.declaringClass.simpleName
        var installTime: Long? = null

        var isAndroidTV = false
    }
}
