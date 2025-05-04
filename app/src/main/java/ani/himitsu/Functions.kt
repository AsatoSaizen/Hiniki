package ani.himitsu

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.math.MathUtils.clamp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import ani.himitsu.BuildConfig.APPLICATION_ID
import ani.himitsu.connections.anilist.getUserId
import ani.himitsu.media.cereal.Genre
import ani.himitsu.notifications.IncognitoNotificationClickReceiver
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.settings.saving.internal.PreferenceKeystore
import ani.himitsu.settings.saving.internal.PreferenceKeystore.Companion.generateSalt
import ani.himitsu.util.Logger
import ani.himitsu.view.dialog.CustomBottomDialog
import bit.himitsu.content.reboot
import bit.himitsu.content.toDp
import bit.himitsu.content.toPx
import bit.himitsu.isPhoneLandscape
import bit.himitsu.nio.Strings.getString
import bit.himitsu.os.Version
import bit.himitsu.setNavigationTheme
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.joery.animatedbottombar.AnimatedBottomBar
import tachiyomi.core.util.lang.withUIContext
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.util.TimeZone
import kotlin.collections.set
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow

var statusBarHeight = 0
var navBarHeight = 0

lateinit var bottomBar: AnimatedBottomBar
var selectedOption = 1

object Refresh {
    fun all() {
        for (i in activity) {
            activity[i.key]!!.postValue(true)
        }
    }

    val activity = mutableMapOf<Int, MutableLiveData<Boolean>>()
}

fun currContext(): Context {
    return Himitsu.currentContext()
}

fun currActivity(): Activity? {
    return Himitsu.currentActivity()
}

var loadMedia: Int? = null
var loadIsMAL = false

fun initActivity(a: Activity) {
    val window = a.window
    WindowCompat.setDecorFitsSystemWindows(window, true)
    val darkMode = PrefManager.getVal<Int>(PrefName.DarkMode)
    val immersiveMode: Boolean = PrefManager.getVal(PrefName.ImmersiveMode)
    darkMode.apply {
        AppCompatDelegate.setDefaultNightMode(
            when (this) {
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
    if (immersiveMode) {
        if (navBarHeight == 0) {
            ViewCompat.getRootWindowInsets(window.decorView)?.run {
                navBarHeight = if (a.resources.isPhoneLandscape())
                    getInsets(WindowInsetsCompat.Type.navigationBars()).right
                else
                    getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                if (Version.isLowerThan(Build.VERSION_CODES.M)) navBarHeight += 48.toPx
            }
        }
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).hide(WindowInsetsCompat.Type.statusBars())
        if (Version.isPie && statusBarHeight == 0 && !a.resources.isPhoneLandscape()) {
            window.decorView.rootWindowInsets?.displayCutout?.apply {
                if (boundingRects.isNotEmpty()) {
                    statusBarHeight = min(boundingRects[0].width(), boundingRects[0].height())
                }
            }
        }
    } else {
        if (statusBarHeight == 0) {
            ViewCompat.getRootWindowInsets(window.decorView)?.run {
                statusBarHeight = getInsets(WindowInsetsCompat.Type.statusBars()).top
                navBarHeight =
                    if (a.resources.isPhoneLandscape())
                        getInsets(WindowInsetsCompat.Type.navigationBars()).right
                    else
                        getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                if (Version.isLowerThan(Build.VERSION_CODES.M)) navBarHeight += 48.toPx
            }
        }
        if (a !is MainActivity) a.setNavigationTheme()
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

/**
 * Restarts the application from the launch intent and redirects to the specified activity
 */
fun Activity.restart(component: ComponentName? = null, extras: Bundle? = null) {
    val mainIntent = Intent.makeRestartActivityTask(
        packageManager.getLaunchIntentForPackage(this.packageName)!!.component
    )
    finishAndRemoveTask()
    try {
        startActivity(mainIntent.setComponent(component).apply {
            extras?.let { putExtras( it ) }
        })
    } catch (_: Exception) {
        startActivity(mainIntent.apply {
            extras?.let { putExtras( it ) }
        })
    }
}

suspend fun loadFragment(activity: FragmentActivity, response: () -> Unit) {
//    Anilist.userid = PrefManager.getNullableVal<String>(
//        PrefName.AnilistUserId, null
//    )?.toIntOrNull()
    try {
        getUserId(activity) { response.invoke() }
    } catch (ex: Exception) {
        withUIContext {
            CustomBottomDialog.newInstance().apply {
                title = activity.getString(R.string.anilist_broken_title)
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.anilist_broken)
                })

                setNegativeButton(activity.getString(R.string.reset)) {
                    activity.reboot()
                }

                setPositiveButton(activity.getString(R.string.close)) {
                    activity.finishAffinity()
                }

                setNeutralButton(activity.getString(R.string.offline_mode)) {
                    PrefManager.setVal(PrefName.OfflineMode, true)
                    activity.recreate()
                    dismiss()
                }

                show(activity.supportFragmentManager, "dialog")
            }
        }
        throw ex
    }
}

fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    if (lhs == rhs) {
        return 0
    }
    if (lhs.isEmpty()) {
        return rhs.length
    }
    if (rhs.isEmpty()) {
        return lhs.length
    }

    val lhsLength = lhs.length + 1
    val rhsLength = rhs.length + 1

    var cost = Array(lhsLength) { it }
    var newCost = Array(lhsLength) { 0 }

    for (i in 1 until rhsLength) {
        newCost[0] = i

        for (j in 1 until lhsLength) {
            val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1

            newCost[j] = min(min(costInsert, costDelete), costReplace)
        }

        val swap = cost
        cost = newCost
        newCost = swap
    }

    return cost[lhsLength - 1]
}

fun List<ShowResponse>.sortByTitle(string: String): List<ShowResponse> {
    val list = this.toMutableList()
    list.sortByTitle(string)
    return list
}

fun MutableList<ShowResponse>.sortByTitle(string: String) {
    val temp: MutableMap<Int, Int> = mutableMapOf()
    for (i in 0 until this.size) {
        temp[i] = levenshtein(string.lowercase(), this[i].name.lowercase())
    }
    val c = temp.toList().sortedBy { (_, value) -> value }.toMap()
    val a = ArrayList(c.keys.toList().subList(0, min(this.size, 25)))
    val b = c.values.toList().subList(0, min(this.size, 25))
    for (i in b.indices.reversed()) {
        if (b[i] > 18 && i < a.size) a.removeAt(i)
    }
    val temp2 = this.toMutableList()
    this.clear()
    for (i in a.indices) {
        this.add(temp2[a[i]])
    }
}

class SafeClickListener(
    private var defaultInterval: Int = 1000,
    private val onSafeCLick: (View) -> Unit
) : View.OnClickListener {

    private var lastTimeClicked: Long = 0

    override fun onClick(v: View) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return
        }
        lastTimeClicked = SystemClock.elapsedRealtime()
        onSafeCLick(v)
    }
}

fun View.setSafeOnClickListener(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

fun savePrefsToDownloads(
    title: String,
    serialized: String,
    context: Activity,
    password: CharArray? = null
) {
    FileProvider.getUriForFile(
        context,
        "$APPLICATION_ID.provider",
        if (password != null && Version.isMarshmallow) {
            savePrefs(
                serialized,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                title,
                context,
                password
            ) ?: return
        } else {
            savePrefs(
                serialized,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                title,
                context
            ) ?: return
        }
    )
}

fun savePrefs(serialized: String, path: String, title: String, context: Context): File? {
    var file = File(path, "$title.ani")
    var counter = 1
    while (file.exists()) {
        file = File(path, "${title}_${counter}.ani")
        counter++
    }

    return try {
        file.writeText(serialized)
        scanFile(file, context)
        toast(context.getString(R.string.saved_to_path, file.absolutePath))
        file
    } catch (e: Exception) {
        snackString("Failed to save settings: ${e.localizedMessage}")
        null
    }
}

@RequiresApi(Build.VERSION_CODES.M)
fun savePrefs(
    serialized: String,
    path: String,
    title: String,
    context: Context,
    password: CharArray
): File? {
    var file = File(path, "$title.sani")
    var counter = 1
    while (file.exists()) {
        file = File(path, "${title}_${counter}.sani")
        counter++
    }

    val salt = generateSalt()

    return try {
        val encryptedData = PreferenceKeystore.encryptWithPassword(password, serialized, salt)

        // Combine salt and encrypted data
        val dataToSave = salt + encryptedData

        file.writeBytes(dataToSave)
        scanFile(file, context)
        toast(context.getString(R.string.saved_to_path, file.absolutePath))
        file
    } catch (e: Exception) {
        snackString("Failed to save settings: ${e.localizedMessage}")
        null
    }
}

fun saveImage(image: Bitmap, folder: File, imageFileName: String): File? {
    val imageFile = File(folder, "$imageFileName.png")
    return try {
        FileOutputStream(imageFile).use {
            image.compress(Bitmap.CompressFormat.PNG, 0, it)
        }
        scanFile(imageFile, currContext())
        toast(String.format(getString(R.string.saved_to_path, folder.path)))
        imageFile
    } catch (e: Exception) {
        snackString("Failed to save image: ${e.localizedMessage}")
        null
    }
}

fun saveImageToDownloads(title: String, bitmap: Bitmap, context: Activity) {
    FileProvider.getUriForFile(
        context,
        "$APPLICATION_ID.provider",
        saveImage(
            bitmap,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            title
        ) ?: return
    )
}

fun shareImage(title: String, bitmap: Bitmap, context: Context) {

    val imageFile = saveImage(bitmap, context.externalCacheDir ?: context.cacheDir, title) ?: return
    val contentUri = FileProvider.getUriForFile(context, "$APPLICATION_ID.provider", imageFile)

    context.startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, title)
            putExtra(Intent.EXTRA_STREAM, contentUri)
        }, "Share $title"))
}

private fun scanFile(path: File, context: Context) {
    MediaScannerConnection.scanFile(context, arrayOf(path.canonicalPath), null) { _, _ -> }
}

fun copyToClipboard(string: String, toast: Boolean = true) {
    val context = currContext()
    val clipboard = getSystemService(context, ClipboardManager::class.java)
    val clip = ClipData.newPlainText("label", string)
    clipboard?.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        if (toast) snackString(context.getString(R.string.copied_text, string))
    }
}

fun MutableMap<String, Genre>.checkId(id: Int): Boolean {
    this.forEach { if (it.value.id == id) return false }
    return true
}

fun getCurrentBrightnessValue(context: Context): Float {
    fun getMax(): Int {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val fields: Array<Field> = powerManager.javaClass.declaredFields
        for (field in fields) {
            if (field.name.equals("BRIGHTNESS_ON")) {
                field.isAccessible = true
                return try {
                    field.get(powerManager)?.toString()?.toInt() ?: 255
                } catch (_: IllegalAccessException) {
                    255
                }
            }
        }
        return 255
    }

    fun getCur(): Float {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            127
        ).toFloat()
    }

    return brightnessConverter(getCur() / getMax(), true)
}

fun brightnessConverter(it: Float, fromLog: Boolean) =
    clamp(
        if (Version.isPie)
            if (fromLog) log2((it * 256f)) * 12.5f / 100f else 2f.pow(it * 100f / 12.5f) / 256f
        else it, 0.001f, 1f
    )


fun checkCountry(context: Context): Boolean {
    val telMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return when (telMgr.simState) {
        TelephonyManager.SIM_STATE_ABSENT -> {
            val tz = TimeZone.getDefault().id
            tz.equals("Asia/Kolkata", ignoreCase = true)
        }

        TelephonyManager.SIM_STATE_READY -> {
            val countryCodeValue = telMgr.networkCountryIso
            countryCodeValue.equals("in", ignoreCase = true)
        }

        else -> false
    }
}

const val INCOGNITO_CHANNEL_ID = 26

@SuppressLint("LaunchActivityFromNotification")
fun incognitoNotification(context: Context) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
    if (incognito) {
        val intent = Intent(context, IncognitoNotificationClickReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_INCOGNITO_MODE)
            .setSmallIcon(R.drawable.ic_incognito_24)
            .setContentTitle("Incognito Mode")
            .setContentText("Disable Incognito Mode")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
        notificationManager.notify(INCOGNITO_CHANNEL_ID, builder.build())
    } else {
        notificationManager.cancel(INCOGNITO_CHANNEL_ID)
    }
}

fun hasNotificationPermission(context: Context): Boolean {
    return if (Version.isTiramisu) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

fun openSettings(context: Context, channelId: String?): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startActivity(
            Intent(
                if (channelId != null) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                else Settings.ACTION_APP_NOTIFICATION_SETTINGS
            ).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        )
        true
    } else false
}

fun String.findBetween(a: String, b: String): String? {
    val string = substringAfter(a, "").substringBefore(b, "")
    return string.ifEmpty { null }
}

fun toast(string: String?) {
    if (string.isNullOrBlank()) return
    Logger.log(string)
    MainScope().launch {
        Toast.makeText(currActivity()?.application ?: return@launch, string, Toast.LENGTH_SHORT)
            .show()
    }
}

fun toast(res: Int) {
    toast(getString(res))
}

fun snackString(s: String?, activity: Activity? = null, clipboard: String? = null): Snackbar? {
    try { //I have no idea why this sometimes crashes for some people...
        if (s.isNullOrBlank()) return null
        (activity ?: currActivity())?.apply {
            val snackBar = Snackbar.make(
                window.decorView.findViewById(android.R.id.content),
                s,
                Snackbar.LENGTH_SHORT
            )
            runOnUiThread {
                snackBar.view.apply {
                    updateLayoutParams<FrameLayout.LayoutParams> {
                        gravity = (Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    translationY = -(navBarHeight.toDp + 32f)
                    translationZ = 32f
                    updatePadding(16f.toPx, right = 16f.toPx)
                    setOnClickListener {
                        snackBar.dismiss()
                    }
                    setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        copyToClipboard(clipboard ?: s, false)
                        true
                    }
                }
                snackBar.show()
            }
            return snackBar
        }
        Logger.log(s)
    } catch (e: Exception) {
        Logger.log(e)
    }
    return null
}

fun snackString(r: Int, activity: Activity? = null, clipboard: String? = null): Snackbar? {
    return snackString(getString(r), activity, clipboard)
}