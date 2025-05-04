package ani.himitsu.notifications.anilist

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ani.himitsu.MainActivity
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.notifications.Task
import ani.himitsu.profile.activity.ActivityItemBuilder
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext

class AniListNotificationTask : Task {
    override suspend fun execute(context: Context, withNotification: Boolean): Boolean {
        try {
            withIOContext {
                PrefManager.init(context) //make sure prefs are initialized
                val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
                if (userId.isNotEmpty()) {
                    AniList.getSavedToken()
                    val res = AniList.query.getNotifications(
                        userId.toInt(),
                        resetNotification = false,
                        isBackgroundTask = true
                    )
                    val unreadNotificationCount = res?.data?.user?.unreadNotificationCount ?: 0
                    if (unreadNotificationCount > 0) {
                        val unreadNotifications =
                            res?.data?.page?.notifications?.sortedBy { it.id }
                                ?.takeLast(unreadNotificationCount)
                        val lastId = PrefManager.getVal<Int>(PrefName.LastAnilistNotificationId)
                        val newNotifications = unreadNotifications?.filter { it.id > lastId }
                        val filteredTypes =
                            PrefManager.getVal<Set<String>>(PrefName.AnilistFilteredTypes)
                        if (!newNotifications.isNullOrEmpty()) {
                            PrefManager.setVal(
                                PrefName.LastAnilistNotificationId,
                                newNotifications.last().id
                            )
                        }
                        if (!withNotification) return@withIOContext
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) return@withIOContext
                        NotificationManagerCompat.from(context).apply {
                            val groups = arrayListOf<String>()
                            newNotifications?.forEach {
                                if (!filteredTypes.contains(it.notificationType)) {
                                    val content = ActivityItemBuilder.getContent(it).also { group ->
                                        groups.add(group)
                                    }
                                    notify(
                                        Notifications.CHANNEL_ANILIST,
                                        System.currentTimeMillis().toInt(),
                                        createNotification(context, content, it.id)
                                    )
                                }
                            }
                            if (groups.isNotEmpty()) {
                                groups.distinctBy { it }.forEachIndexed { index, group ->
                                    val count = groups.count { it == group }
                                    notify(
                                        index,
                                        NotificationCompat.Builder(
                                            context, Notifications.CHANNEL_ANILIST
                                        ).apply {
                                            setSmallIcon(R.drawable.ic_himitsu_logo)
                                            setContentTitle("$group ($count)")
                                            setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                            setAutoCancel(true)
                                            setGroup(group)
                                            setGroupSummary(true)
                                            BitmapFactory.decodeResource(
                                                context.resources,
                                                R.drawable.ic_himitsu_logo
                                            )?.let {
                                                setLargeIcon(
                                                    Bitmap.createScaledBitmap(it, 128, 128, false)
                                                )
                                            }
                                        }.build()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Logger.log("AniListNotificationTask: ${e.message}")
            Logger.log(e)
            return false
        }
    }

    private fun createNotification(
        context: Context,
        content: String,
        notificationId: Int? = null
    ): android.app.Notification {
        val title = context.getString(R.string.anilist_notification)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FRAGMENT_TO_LOAD", "NOTIFICATIONS")
            if (notificationId != null) {
                Logger.log("notificationId: $notificationId")
                putExtra("activityId", notificationId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, Notifications.CHANNEL_ANILIST).apply {
            setSmallIcon(R.drawable.ic_himitsu_logo)
            setContentTitle(title)
            setContentText(content)
            setPriority(NotificationCompat.PRIORITY_DEFAULT)
            setContentIntent(pendingIntent)
            setAutoCancel(true)
            setGroup(content)
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_himitsu_logo)?.let {
                setLargeIcon(Bitmap.createScaledBitmap(it, 128, 128, false))
            }
        }.build()
    }
}