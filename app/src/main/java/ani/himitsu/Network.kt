package ani.himitsu

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.Uri
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import ani.himitsu.connections.anilist.UrlMedia
import ani.himitsu.others.SpoilerPlugin
import ani.himitsu.others.webview.CloudFlare
import ani.himitsu.others.webview.WebViewBottomDialog
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import com.lagradost.nicehttp.addGenericDns
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkHelper.Companion.defaultUserAgentProvider
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.TagHandlerNoOp
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import tachiyomi.core.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable
import java.io.StringWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

lateinit var defaultHeaders: Map<String, String>

lateinit var okHttpClient: OkHttpClient
lateinit var client: Requests

fun initializeNetwork() {

    val networkHelper = Injekt.get<NetworkHelper>()

    defaultHeaders = mapOf(
        "User-Agent" to
                defaultUserAgentProvider()
                    .format(Build.VERSION.RELEASE, Build.MODEL)
    )

    okHttpClient = networkHelper.client
    client = Requests(
        networkHelper.client,
        defaultHeaders,
        defaultCacheTime = 6,
        defaultCacheTimeUnit = TimeUnit.HOURS,
        responseParser = Mapper
    )
}

object Mapper : ResponseParser {

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @OptIn(InternalSerializationApi::class)
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return json.decodeFromString(kClass.serializer(), text)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return tryWith {
            parse(text, kClass)
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return json.encodeToString(obj)
    }

    inline fun <reified T> parse(text: String): T {
        return json.decodeFromString(text)
    }
}

fun logError(e: Throwable, post: Boolean = true, snackbar: Boolean = true) {
    val sw = StringWriter()
    val stackTrace: String = sw.toString()
    if (post) {
        if (snackbar)
            snackString(e.localizedMessage, null, stackTrace)
        else
            snackString(e.localizedMessage)
    }
    Logger.log(e)
}

fun <T> tryWith(post: Boolean = false, snackbar: Boolean = true, call: () -> T): T? {
    return try {
        call.invoke()
    } catch (e: Throwable) {
        logError(e, post, snackbar)
        null
    }
}

suspend fun <T> tryWithSuspend(
    post: Boolean = false,
    snackbar: Boolean = true,
    call: suspend () -> T
): T? {
    return try {
        call.invoke()
    } catch (e: Throwable) {
        logError(e, post, snackbar)
        null
    } catch (_: CancellationException) {
        null
    }
}

/**
 * A url, which can also have headers
 * **/
data class FileUrl(
    var url: String,
    var headers: Map<String, String> = mapOf()
) : Serializable {
    companion object {
        operator fun get(url: String?, headers: Map<String, String> = mapOf()): FileUrl? {
            return FileUrl(url ?: return null, headers)
        }

        private const val serialVersionUID = 1L
    }
}

suspend fun FileUrl.getFileSize(): Double? {
    return tryWithSuspend {
        client.head(url, headers, timeout = 1000).size?.toDouble()?.div(1024 * 1024)
    }
}

suspend fun String.getFileSize(): Double? {
    return FileUrl(this).getFileSize()
}

fun OkHttpClient.Builder.addGoogleDns() = (
        addGenericDns(
            "https://dns.google/dns-query",
            listOf(
                "8.8.4.4",
                "8.8.8.8"
            )
        ))

fun OkHttpClient.Builder.addCloudFlareDns() = (
        addGenericDns(
            "https://cloudflare-dns.com/dns-query",
            listOf(
                "1.1.1.1",
                "1.0.0.1",
                "2606:4700:4700::1111",
                "2606:4700:4700::1001"
            )
        ))

fun OkHttpClient.Builder.addAdGuardDns() = (
        addGenericDns(
            "https://dns.adguard.com/dns-query",
            listOf(
                // "Non-filtering"
                "94.140.14.140",
                "94.140.14.141",
            )
        ))

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun webViewInterface(webViewDialog: WebViewBottomDialog): Map<String, String>? {
    var map: Map<String, String>? = null

    val latch = CountDownLatch(1)
    webViewDialog.callback = {
        map = it
        latch.countDown()
    }
    val fragmentManager =
        (currContext() as FragmentActivity?)?.supportFragmentManager ?: return null
    webViewDialog.show(fragmentManager, "web-view")
    delay(0)
    latch.await(2, TimeUnit.MINUTES)
    return map
}

suspend fun webViewInterface(type: String, url: FileUrl): Map<String, String>? {
    val webViewDialog: WebViewBottomDialog = when (type) {
        "Cloudflare" -> CloudFlare.newInstance(url)
        else -> return null
    }
    return webViewInterface(webViewDialog)
}

suspend fun webViewInterface(type: String, url: String): Map<String, String>? {
    return webViewInterface(type, FileUrl(url))
}

/**
 * Builds the markwon instance with all the plugins
 * @return the markwon instance
 */
fun buildMarkwon(
    activity: Context,
    userInputContent: Boolean = true,
    fragment: Fragment? = null,
    anilist: Boolean = false
): Markwon {
    val glideContext = fragment?.let { Glide.with(it) } ?: Glide.with(activity)
    return Markwon.builder(activity)
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    ImageUtil.ImageType.entries.forEach {
                        if (link.endsWith(it.extension, true)) {
                            view.setOnClickListener {
                                openLinkInBrowser(link)
                            }
                            return@linkResolver
                        }
                    }
                    if (link.startsWith("https://anilist.co")) {
                        view.setOnClickListener {
                            view.context.startActivity(
                                Intent(view.context, UrlMedia::class.java).apply {
                                    data = Uri.parse(link)
                                }
                            )
                        }
                    } else {
                        view.setOnClickListener {
                            openLinkInBrowser(link)
                        }
                    }
                }
            }
        })
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(activity))
        .usePlugin(TaskListPlugin.create(activity))
        .usePlugin(SpoilerPlugin(anilist))
        .usePlugin(HtmlPlugin.create { plugin ->
            if (userInputContent) {
                plugin.addHandler(
                    TagHandlerNoOp.create("h1", "h2", "h3", "h4", "h5", "h6", "hr", "pre", "a")
                )
            }
        })
        .usePlugin(GlideImagesPlugin.create(object : GlideImagesPlugin.GlideStore {
            private val requestManager: RequestManager = glideContext.apply {
                addDefaultRequestListener(object : RequestListener<Any> {
                    override fun onResourceReady(
                        resource: Any,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<Any>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        if (resource is GifDrawable && !resource.isRunning) resource.start()
                        return false
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<Any>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Logger.log(e as Exception)
                        return false
                    }
                })
            }

            override fun load(drawable: AsyncDrawable): RequestBuilder<Drawable> {
                Logger.log("Loading image: ${drawable.destination}")
                return requestManager.load(drawable.destination)
            }

            override fun cancel(target: Target<*>) {
                Logger.log("Cancelling image load")
                requestManager.clear(target)
            }
        })).build()
}

val Context.isOnline: Boolean get() {
    with (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager) {
        return tryWith {
            if (Version.isMarshmallow) {
                val cap =
                    getNetworkCapabilities(activeNetwork)
                return@tryWith if (cap != null) {
                    when {
                        cap.hasTransport(TRANSPORT_BLUETOOTH) ||
                                cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                                cap.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) ||
                                cap.hasTransport(NetworkCapabilities.TRANSPORT_USB) ||
                                cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                                cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> true

                        else -> false
                    }
                } else false
            } else {
                @Suppress("DEPRECATION")
                return@tryWith activeNetworkInfo?.run {
                    type == ConnectivityManager.TYPE_BLUETOOTH ||
                            type == ConnectivityManager.TYPE_ETHERNET ||
                            type == ConnectivityManager.TYPE_MOBILE ||
                            type == ConnectivityManager.TYPE_MOBILE_DUN ||
                            type == ConnectivityManager.TYPE_MOBILE_HIPRI ||
                            type == ConnectivityManager.TYPE_WIFI ||
                            type == ConnectivityManager.TYPE_WIMAX ||
                            type == ConnectivityManager.TYPE_VPN
                } == true
            }
        } == true
    }
}

val Context.isOffline : Boolean get() {
    return !isOnline || PrefManager.getVal(PrefName.OfflineMode)
}