/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.extension.installer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.annotation.CallSuper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.himitsu.media.AddonType
import ani.himitsu.media.MediaType
import ani.himitsu.media.Type
import ani.himitsu.parsers.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import uy.kohesive.injekt.injectLazy
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * Base implementation class for extension installer. To be used inside a foreground [Service].
 */
abstract class Installer(private val service: Service) {

    private val animeExtensionManager: AnimeExtensionManager by injectLazy()
    private val mangaExtensionManager: MangaExtensionManager by injectLazy()
    private val novelExtensionManager: NovelExtensionManager by injectLazy()
    private val downloadAddonManager: DownloadAddonManager by injectLazy()

    private var waitingInstall = AtomicReference<Entry>(null)
    private val queue = Collections.synchronizedList(mutableListOf<Entry>())

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 } ?: return
            cancelQueue(downloadId)
        }
    }

    /**
     * Installer readiness. If false, queue check will not run.
     *
     * @see checkQueue
     */
    abstract var ready: Boolean

    /**
     * Add an item to install queue.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     * @param uri Uri of APK to install
     */
    fun addToQueue(type: Type, downloadId: Long, uri: Uri) {
        queue.add(Entry(type, downloadId, uri))
        checkQueue()
    }

    /**
     * Proceeds to install the APK of this entry inside this method. Call [continueQueue]
     * when the install process for this entry is finished to continue the queue.
     *
     * @param entry The [Entry] of item to process
     * @see continueQueue
     */
    @CallSuper
    open fun processEntry(entry: Entry) {
        when (entry.type) {
            MediaType.ANIME -> animeExtensionManager.setInstalling(entry.downloadId)
            MediaType.MANGA -> mangaExtensionManager.setInstalling(entry.downloadId)
            MediaType.NOVEL -> novelExtensionManager.setInstalling(entry.downloadId)
            AddonType.DOWNLOAD -> downloadAddonManager.setInstalling(entry.downloadId)
        }
    }

    /**
     * Called before queue continues. Override this to handle when the removed entry is
     * currently being processed.
     *
     * @return true if this entry can be removed from queue.
     */
    open fun cancelEntry(entry: Entry): Boolean {
        return true
    }

    /**
     * Tells the queue to continue processing the next entry and updates the install step
     * of the completed entry ([waitingInstall]) to [ExtensionManager].
     *
     * @param resultStep new install step for the processed entry.
     * @see waitingInstall
     */
    fun continueQueue(resultStep: InstallStep) {
        val completedEntry = waitingInstall.getAndSet(null)
        if (completedEntry != null) {
            when (completedEntry.type) {
                MediaType.ANIME -> {
                    animeExtensionManager.updateInstallStep(completedEntry.downloadId, resultStep)
                }

                MediaType.MANGA -> {
                    mangaExtensionManager.updateInstallStep(completedEntry.downloadId, resultStep)
                }

                MediaType.NOVEL -> {
                    novelExtensionManager.updateInstallStep(completedEntry.downloadId, resultStep)
                }

                AddonType.DOWNLOAD -> {
                    downloadAddonManager.updateInstallStep(completedEntry.downloadId, resultStep)
                }
            }
            checkQueue()
        }
    }

    /**
     * Checks the queue. The provided service will be stopped if the queue is empty.
     * Will not be run when not ready.
     *
     * @see ready
     */
    private fun checkQueue() {
        if (!ready) {
            return
        }
        if (queue.isEmpty()) {
            service.stopSelf()
            return
        }
        val nextEntry = queue.first()
        if (waitingInstall.compareAndSet(null, nextEntry)) {
            // queue.removeFirst()
            queue.removeAt(0)
            processEntry(nextEntry)
        }
    }

    /**
     * Call this method when the provided service is destroyed.
     */
    @CallSuper
    open fun onDestroy() {
        LocalBroadcastManager.getInstance(service).unregisterReceiver(cancelReceiver)
        queue.forEach {
            when (it.type) {
                MediaType.ANIME -> {
                    animeExtensionManager.updateInstallStep(it.downloadId, InstallStep.Error)
                }

                MediaType.MANGA -> {
                    mangaExtensionManager.updateInstallStep(it.downloadId, InstallStep.Error)
                }

                MediaType.NOVEL -> {
                    novelExtensionManager.updateInstallStep(it.downloadId, InstallStep.Error)
                }

                AddonType.DOWNLOAD -> {
                    downloadAddonManager.updateInstallStep(it.downloadId, InstallStep.Error)
                }
            }
        }
        queue.clear()
        waitingInstall.set(null)
    }

    protected fun getActiveEntry(): Entry? = waitingInstall.get()

    /**
     * Cancels queue for the provided download ID if exists.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     */
    private fun cancelQueue(downloadId: Long) {
        val waitingInstall = this.waitingInstall.get()
        val toCancel = queue.find { it.downloadId == downloadId } ?: waitingInstall ?: return
        if (cancelEntry(toCancel)) {
            queue.remove(toCancel)
            if (waitingInstall == toCancel) {
                // Currently processing removed entry, continue queue
                this.waitingInstall.set(null)
                checkQueue()
            }
            when (toCancel.type) {
                MediaType.ANIME -> {
                    animeExtensionManager.updateInstallStep(downloadId, InstallStep.Idle)
                }

                MediaType.MANGA -> {
                    mangaExtensionManager.updateInstallStep(downloadId, InstallStep.Idle)
                }

                MediaType.NOVEL -> {
                    novelExtensionManager.updateInstallStep(downloadId, InstallStep.Idle)
                }
            }
        }
    }

    /**
     * Install item to queue.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     * @param uri Uri of APK to install
     */
    data class Entry(val type: Type, val downloadId: Long, val uri: Uri)

    init {
        val filter = IntentFilter(ACTION_CANCEL_QUEUE)
        LocalBroadcastManager.getInstance(service).registerReceiver(cancelReceiver, filter)
    }

    companion object {
        private const val ACTION_CANCEL_QUEUE = "Installer.action.CANCEL_QUEUE"
        private const val EXTRA_DOWNLOAD_ID = "Installer.extra.DOWNLOAD_ID"

        /**
         * Attempts to cancel the installation entry for the provided download ID.
         *
         * @param downloadId Download ID as known by [ExtensionManager]
         */
        fun cancelInstallQueue(context: Context, downloadId: Long) {
            val intent = Intent(ACTION_CANCEL_QUEUE)
            intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }
}
