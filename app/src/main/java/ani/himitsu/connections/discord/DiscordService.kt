/*
 * https://discord.com/developers/docs/events/gateway-events
 */

package ani.himitsu.connections.discord

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ani.himitsu.MainActivity
import ani.himitsu.R
import ani.himitsu.connections.discord.serializers.Presence
import ani.himitsu.connections.discord.serializers.User
import ani.himitsu.isOnline
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class DiscordService : Service() {
    private var heartbeat: Int = 0
    private var sequence: Int? = null
    private var sessionId: String = ""
    private var resume = false
    private lateinit var webSocket: WebSocket
    private lateinit var heartbeatThread: Thread
    private lateinit var client: OkHttpClient
    private lateinit var wakeLock: PowerManager.WakeLock
    private val shouldLog = false
    var presenceStore = ""

    val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun onCreate() {
        super.onCreate()

        log("Service onCreate()")
        val powerManager = baseContext.getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "discordRPC:backgroundPresence"
        )
        // log("WakeLock Acquired")
        if (Version.isOreo) {
            val serviceChannel = NotificationChannel(
                RPC.CHANNEL_NAME,
                "Discord Presence Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val actionDisconnect = PendingIntent.getService(
            this, 0, Intent(this, DiscordService::class.java).apply {
                action = "ani.himitsu.discord.STOP_SERVICE"
            }, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, RPC.CHANNEL_NAME)
            .setSmallIcon(R.drawable.ic_himitsu_logo)
            .setContentTitle(getString(R.string.rpc_notification))
            .setContentText(getString(R.string.rpc_notification_text))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.round_weekend_24, getString(R.string.disconnect), actionDisconnect)
        BitmapFactory.decodeResource(resources, R.drawable.ic_himitsu_logo)?.let {
            builder.setLargeIcon(Bitmap.createScaledBitmap(
                it, 128, 128, false
            ))
        }
        startForeground(1, builder.build())
        log("Foreground service started, notification shown")
        client = OkHttpClient().apply {
            newWebSocket(
                Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build(),
                DiscordWebSocketListener()
            )
            dispatcher.executorService.shutdown()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // log("Service onStartCommand()")
        if (intent != null) {
            if (intent.hasExtra(RPC.INTENT_EXTRA)) {
                if (wakeLock.isHeld) wakeLock.release()
                val duration = intent.getLongExtra(RPC.DURATION_EXTRA, 30 * 60 * 1000L /*30 minutes*/)
                wakeLock.acquire(duration)
                log("Service onStartCommand() setPresence")
                intent.getStringExtra(RPC.INTENT_EXTRA)?.let {
                    if (this::webSocket.isInitialized) webSocket.send(it)
                    presenceStore = it
                }
            } else {
                log("Service onStartCommand() no presence")
                // kill the client
                onDisconnect()
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    fun onDisconnect() {
        if (Version.isNougat)
            stopForeground(STOP_FOREGROUND_REMOVE)
        else
            @Suppress("DEPRECATION") stopForeground(true)
        RPC.isEnabled = false
        log("Service disconnected")
        if (wakeLock.isHeld) wakeLock.release()
        if (this::webSocket.isInitialized) {
            setPresence(
                json.encodeToString(Presence.Response(3, Presence(status = "offline")))
            )
            webSocket.close(1000, "Closed by user")
        }
        client = OkHttpClient().apply {
            dispatcher.executorService.shutdown()
        }
        if (::heartbeatThread.isInitialized && !heartbeatThread.isInterrupted) {
            heartbeatThread.interrupt()
        }
    }

    override fun onDestroy() {
        onDisconnect()
        super.onDestroy()
        // saveLogToFile()
    }

    fun saveProfile(response: String) {
        val user = json.decodeFromString<User.Response>(response).d.user
        log("User data: $user")
        PrefManager.setVal(PrefName.DiscordUserName, user.username)
        PrefManager.setVal(PrefName.DiscordId, user.id)
        PrefManager.setVal(PrefName.DiscordAvatar, user.avatar)
    }

    override fun onBind(p0: Intent?): IBinder? = null

    inner class DiscordWebSocketListener : WebSocketListener() {

        private var retryAttempts = 0
        private val maxRetryAttempts = 10
        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            this@DiscordService.webSocket = webSocket
            log("WebSocket: Opened")
        }

        /* #receive-events */
        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            val json = JsonParser.parseString(text).asJsonObject
            log("WebSocket: Received op code ${json.get("op")}")
            when (json.get("op").asInt) {
                0 -> { // Ready
                    if (json.has("s")) {
                        log("WebSocket: Sequence ${json.get("s")} Received")
                        sequence = json.get("s").asInt
                    }
                    if (json.get("t").asString != "READY") return
                    saveProfile(text)
                    log(text)
                    sessionId = json.get("d").asJsonObject.get("session_id").asString
                    log("WebSocket: SessionID ${json.get("d").asJsonObject.get("session_id")} Received")
                    if (presenceStore.isNotEmpty()) setPresence(presenceStore)
                    sendBroadcast(Intent("ServiceToConnectButton"))
                }

                1 -> { // Heartbeat
                    log("WebSocket: Received Heartbeat request, sending heartbeat")
                    heartbeatThread.interrupt()
                    heartbeatSend(webSocket, sequence)
                    heartbeatThread = Thread(HeartbeatRunnable())
                    heartbeatThread.start()
                }

                7 -> { // Reconnect
                    resume = true
                    log("WebSocket: Requested to Restart, restarting")
                    webSocket.close(1000, "Requested to Restart by the server")
                    client = OkHttpClient().apply {
                        newWebSocket(
                            Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build(),
                            DiscordWebSocketListener()
                        )
                        dispatcher.executorService.shutdown()
                    }
                }

                9 -> { // Invalid Session
                    log("WebSocket: Invalid Session, restarting")
                    webSocket.close(1000, "Invalid Session")
                    Thread.sleep(5000)
                    client = OkHttpClient().apply {
                        newWebSocket(
                            Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build(),
                            DiscordWebSocketListener()
                        )
                        dispatcher.executorService.shutdown()
                    }
                }

                10 -> { // Hello
                    heartbeat = json.get("d").asJsonObject.get("heartbeat_interval").asInt
                    heartbeatThread = Thread(HeartbeatRunnable())
                    heartbeatThread.start()
                    if (resume) {
                        log("WebSocket: Resuming because server requested")
                        resume()
                        resume = false
                    } else {
                        identify(webSocket)
                        log("WebSocket: Identified")
                    }
                }

                11 -> { // Heartbeat ACK
                    log("WebSocket: Heartbeat ACKed")
                    heartbeatThread = Thread(HeartbeatRunnable())
                    heartbeatThread.start()
                }
            }
        }

        /* #identify */
        private fun identify(webSocket: WebSocket) {
            val properties = JsonObject()
            properties.addProperty("os", "linux")
            properties.addProperty("browser", "unknown")
            properties.addProperty("device", "unknown")
            val d = JsonObject()
            d.addProperty("token", getToken())
            d.addProperty("intents", 0)
            d.add("properties", properties)
            val payload = JsonObject()
            payload.addProperty("op", 2)
            payload.add("d", d)
            webSocket.send(payload.toString())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            if (!baseContext.isOnline) {
                log("WebSocket: Error, onFailure() reason: No Internet")
                errorNotification("Could not set the presence", "No Internet")
                return
            } else {
                retryAttempts++
                if (retryAttempts >= maxRetryAttempts) {
                    log("WebSocket: Error, onFailure() reason: Max Retry Attempts")
                    errorNotification("Timeout setting presence", "Max Retry Attempts")
                    return
                }
            }
            t.message?.let { Logger.log("onFailure() $it") }
            log("WebSocket: Error, onFailure() reason: ${t.message}")
            client = OkHttpClient().apply {
                newWebSocket(
                    Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build(),
                    DiscordWebSocketListener()
                )
                dispatcher.executorService.shutdown()
            }
            if (::heartbeatThread.isInitialized && !heartbeatThread.isInterrupted) {
                heartbeatThread.interrupt()
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            Logger.log("onClosing() $code $reason")
            if (::heartbeatThread.isInitialized && !heartbeatThread.isInterrupted) {
                heartbeatThread.interrupt()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            Logger.log("onClosed() $code $reason")
            if (code >= 4000) {
                log("WebSocket: Error, code: $code reason: $reason")
                client = OkHttpClient().apply {
                    newWebSocket(
                        Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build(),
                        DiscordWebSocketListener()
                    )
                    dispatcher.executorService.shutdown()
                }
                return
            }
        }
    }

    fun getToken(): String {
        val token = PrefManager.getNullableVal<String>(PrefName.DiscordToken, null)
        return if (token == null) {
            log("WebSocket: Token not found")
            errorNotification("Could not set the presence", "token not found")
            ""
        } else {
            token
        }
    }

    fun heartbeatSend(webSocket: WebSocket, seq: Int?) {
        val json = JsonObject()
        json.addProperty("op", 1)
        json.addProperty("d", seq)
        webSocket.send(json.toString())
    }

    private fun errorNotification(title: String, text: String) {
        val intent = Intent(this@DiscordService, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent =
            PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this@DiscordService, RPC.CHANNEL_NAME)
            .setSmallIcon(R.drawable.ic_himitsu_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(2, builder.build())
        log("Error Notified")
    }

    @Suppress("unused")
    fun saveSimpleTestPresence() {
        val file = File(baseContext.cacheDir, "payload")
        // fill with test payload
        val payload = JsonObject()
        payload.addProperty("op", 3)
        payload.add("d", JsonObject().apply {
            addProperty("status", "dnd")
            addProperty("afk", false)
            add("activities", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("name", "Test")
                    addProperty("type", 0)
                })
            })
        })
        file.writeText(payload.toString())
        log("WebSocket: Simple Test Presence Saved")
    }

    fun setPresence(string: String) {
        log("WebSocket: Sending Presence payload")
        log(string)
        webSocket.send(string)
    }

    fun log(string: String) {
        if (shouldLog) Logger.log(string)
    }

    fun resume() {
        log("Sending Resume payload")
        val d = JsonObject()
        d.addProperty("token", getToken())
        d.addProperty("session_id", sessionId)
        d.addProperty("seq", sequence)
        val json = JsonObject()
        json.addProperty("op", 6)
        json.add("d", d)
        log(json.toString())
        webSocket.send(json.toString())
    }

    inner class HeartbeatRunnable : Runnable {
        override fun run() {
            try {
                Thread.sleep(heartbeat.toLong())
                heartbeatSend(webSocket, sequence)
                log("WebSocket: Heartbeat Sent")
            } catch (_: InterruptedException) {
            }
        }
    }

    companion object {
        fun isRunning(): Boolean {
            Injekt.get<Application>().run {
                with(getSystemService(ACTIVITY_SERVICE) as ActivityManager) {
                    @Suppress("DEPRECATION") // We only need our services
                    getRunningServices(Int.MAX_VALUE).forEach {
                        if (DiscordService::class.java.name.equals(it.service.className)) {
                            return true
                        }
                    }
                }
            }
            return false
        }
    }
}