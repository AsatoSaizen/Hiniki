/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.extension.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import ani.himitsu.R
import ani.himitsu.media.AddonType
import ani.himitsu.media.MediaType
import ani.himitsu.media.Type
import ani.himitsu.util.Logger
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.installer.Installer
import eu.kanade.tachiyomi.extension.installer.PackageInstallerInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller.Companion.EXTRA_ADDON_TYPE
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller.Companion.EXTRA_DOWNLOAD_ID
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller.Companion.EXTRA_EXTENSION_TYPE
import eu.kanade.tachiyomi.util.system.getSerializableExtraCompat
import eu.kanade.tachiyomi.util.system.notificationBuilder

class ExtensionInstallService : Service() {

    private var installer: Installer? = null

    override fun onCreate() {
        val notification = notificationBuilder(Notifications.CHANNEL_EXTENSIONS_UPDATE) {
            setSmallIcon(R.drawable.ic_download_24)
            setAutoCancel(false)
            setOngoing(true)
            setShowWhen(false)
            setContentTitle(getString(R.string.extension_install_notice))
            setProgress(100, 100, true)
        }.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_EXTENSION_INSTALLER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifications.ID_EXTENSION_INSTALLER, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        val type = intent?.getSerializableExtraCompat<MediaType>(EXTRA_EXTENSION_TYPE)
            ?: intent?.getSerializableExtraCompat<AddonType>(EXTRA_ADDON_TYPE)
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1)?.takeIf { it != -1L }
        val installerUsed = intent?.getSerializableExtraCompat<BasePreferences.ExtensionInstaller>(
            EXTRA_INSTALLER
        )
        if (uri == null || type == null || id == null || installerUsed == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (installer == null) {
            installer = when (installerUsed) {
                BasePreferences.ExtensionInstaller.PACKAGEINSTALLER -> PackageInstallerInstaller(
                    this
                )

                else -> {
                    Logger.log("Not implemented for installer $installerUsed")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        installer!!.addToQueue(type, id, uri)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        installer?.onDestroy()
        installer = null
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_INSTALLER = "EXTRA_INSTALLER"

        fun getIntent(
            context: Context,
            type: Type,
            downloadId: Long,
            uri: Uri,
            installer: BasePreferences.ExtensionInstaller,
        ): Intent {
            return Intent(context, ExtensionInstallService::class.java)
                .setDataAndType(uri, ExtensionInstaller.APK_MIME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                .putExtra(EXTRA_INSTALLER, installer).apply {
                    if (type is MediaType) {
                        putExtra(EXTRA_EXTENSION_TYPE, type)
                    } else if (type is AddonType) {
                        putExtra(EXTRA_ADDON_TYPE, type)
                    }
                }
        }
    }
}
