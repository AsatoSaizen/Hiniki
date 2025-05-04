package ani.himitsu.notifications.subscription

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ani.himitsu.FileUrl
import ani.himitsu.R
import ani.himitsu.connections.anilist.UrlMedia
import ani.himitsu.hasNotificationPermission
import ani.himitsu.notifications.Task
import ani.himitsu.parsers.AnimeSources
import ani.himitsu.parsers.MangaSources
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_SUBSCRIPTION_CHECK
import eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_SUBSCRIPTION_CHECK_PROGRESS
import eu.kanade.tachiyomi.data.notification.Notifications.ID_SUBSCRIPTION_CHECK_PROGRESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import java.net.URL

class SubscriptionNotificationTask : Task {
    private var currentlyPerforming = false

    @SuppressLint("MissingPermission")
    override suspend fun execute(context: Context, withNotification: Boolean): Boolean {
        if (!currentlyPerforming) {
            try {
                withIOContext {
                    PrefManager.init(context)
                    currentlyPerforming = true
                    var timeout = 15_000L
                    do {
                        delay(1000)
                        timeout -= 1000
                    } while (timeout > 0 && !AnimeSources.isInitialized && !MangaSources.isInitialized)
                    Logger.log("SubscriptionNotificationTask: timeout: $timeout")
                    if (timeout <= 0) {
                        currentlyPerforming = false
                        return@withIOContext
                    }
                    val subscriptions = SubscriptionHelper.getSubscriptions()
                    var i = 0
                    val index = subscriptions.map { i++; it.key to i }.toMap()
                    val notificationManager = NotificationManagerCompat.from(context)

                    val progressEnabled: Boolean =
                        PrefManager.getVal(PrefName.SubscriptionCheckingNotifications)
                    val progressNotification = if (progressEnabled) getProgressNotification(
                        context,
                        subscriptions.size
                    ) else null
                    if (progressNotification != null && hasNotificationPermission(context)) {
                        notificationManager.notify(
                            ID_SUBSCRIPTION_CHECK_PROGRESS,
                            progressNotification.build()
                        )
                        //Seems like if the parent coroutine scope gets cancelled, the notification stays
                        //So adding this as a safeguard? dk if this will be useful
                        CoroutineScope(Dispatchers.Default).launch {
                            delay(5 * subscriptions.size * 1000L)
                            notificationManager.cancel(ID_SUBSCRIPTION_CHECK_PROGRESS)
                        }
                    }

                    fun progress(progress: Int, parser: String, media: String) {
                        if (progressNotification != null && hasNotificationPermission(context))
                            notificationManager.notify(
                                ID_SUBSCRIPTION_CHECK_PROGRESS,
                                progressNotification
                                    .setProgress(subscriptions.size, progress, false)
                                    .setContentText("$media on $parser")
                                    .build()
                            )
                    }

                    if (hasNotificationPermission(context)) {
                        subscriptions.toList().map {
                            val media = it.second
                            val text = if (media.isAnime) {
                                val parser =
                                    SubscriptionHelper.getAnimeParser(media.id)
                                progress(index[it.first]!!, parser.name, media.name)
                                SubscriptionHelper.getEpisode(
                                    parser,
                                    media
                                )?.let { ep ->
                                    ("${
                                        if (ep.title != ep.number && ep.title != null) {
                                            ep.title
                                        } else {
                                            (context.getString(R.string.episode) + ep.number)
                                        }
                                    }${
                                        if (ep.isFiller) " [Filler]" else ""
                                    } ") + context.getString(R.string.just_released) to (ep.thumbnail ?: media.cover?.let {
                                        FileUrl(it)
                                    })
                                }
                            } else {
                                val parser = SubscriptionHelper.getMangaParser(media.id)
                                progress(index[it.first]!!, parser.name, media.name)
                                SubscriptionHelper.getChapter(
                                    parser,
                                    media
                                )?.let { chap ->
                                    "${chap.number} ${context.getString(R.string.just_released)}" to media.cover?.let { image ->
                                        FileUrl(image)
                                    }
                                }
                            } ?: return@map
                            if (!addSubscriptionToStore(
                                    SubscriptionStore(
                                        media.name,
                                        text.first,
                                        media.id,
                                        media.cover,
                                        media.banner
                                    )
                                )) return@map
                            PrefManager.setVal(PrefName.UnreadCommentNotifications,
                                PrefManager.getVal<Int>(PrefName.UnreadCommentNotifications) + 1)
                            if (!withNotification) return@map
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) return@map
                            val notification = createNotification(
                                context.applicationContext,
                                media,
                                text.first,
                                text.second
                            )
                            NotificationManagerCompat.from(context)
                                .notify(
                                    CHANNEL_SUBSCRIPTION_CHECK,
                                    System.currentTimeMillis().toInt(),
                                    notification
                                )
                        }
                    }

                    if (progressNotification != null)
                        notificationManager.cancel(ID_SUBSCRIPTION_CHECK_PROGRESS)
                    currentlyPerforming = false
                }
                return true
            } catch (e: Exception) {
                Logger.log(e)
                return false
            }
        } else {
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun createNotification(
        context: Context,
        media: SubscriptionHelper.SubscribeMedia,
        text: String,
        thumbnail: FileUrl?
    ): android.app.Notification {
        val pendingIntent = getIntent(context, media.id)
        val icon =
            if (media.isAnime) R.drawable.round_movie_filter_24 else R.drawable.round_menu_book_24

        val builder = NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTION_CHECK)
            .setSmallIcon(icon)
            .setContentTitle(media.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (thumbnail != null) {
            getBitmapFromUrl(thumbnail.url)?.let {
                builder.setLargeIcon(Bitmap.createScaledBitmap(it, 128, 128, false))
            }
        }

        return builder.build()
    }

    private fun getProgressNotification(
        context: Context,
        size: Int
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTION_CHECK_PROGRESS).apply {
            setPriority(NotificationCompat.PRIORITY_DEFAULT)
            setSmallIcon(R.drawable.ic_himitsu_logo)
            setContentTitle(context.getString(R.string.checking_subscriptions_title))
            setProgress(size, 0, false)
            setOngoing(true)
            setAutoCancel(false)
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_himitsu_logo)?.let {
                setLargeIcon(Bitmap.createScaledBitmap(it, 128, 128, false))
            }
        }
    }

    private fun getBitmapFromUrl(url: String): Bitmap? {
        return try {
            URL(url).openStream().use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun getIntent(context: Context, mediaId: Int): PendingIntent {
        val notifyIntent = Intent(context, UrlMedia::class.java)
            .putExtra("media", mediaId)
            .setAction(mediaId.toString())
            .apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        return PendingIntent.getActivity(
            context, mediaId, notifyIntent,
            if (Version.isMarshmallow) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }
        )
    }

    private fun addSubscriptionToStore(notification: SubscriptionStore): Boolean {
        val notificationStore = PrefManager.getNullableVal<List<SubscriptionStore>>(
            PrefName.SubscriptionNotificationStore,
            null
        ) ?: listOf()
        val newStore = notificationStore.toMutableList()
        if (newStore.any {
                it.mediaId == notification.mediaId && it.content == notification.content
            }) return false
        if (newStore.size >= 100) {
            newStore.remove(newStore.minByOrNull { it.time })
        }
        newStore.add(notification)
        PrefManager.setVal(PrefName.SubscriptionNotificationStore, newStore)
        return true
    }
}