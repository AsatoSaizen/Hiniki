package ani.himitsu.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ani.himitsu.INCOGNITO_CHANNEL_ID
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName


class IncognitoNotificationClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {

        PrefManager.setVal(PrefName.Incognito, false)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOGNITO_CHANNEL_ID)

    }
}