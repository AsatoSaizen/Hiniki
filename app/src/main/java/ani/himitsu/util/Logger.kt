package ani.himitsu.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import ani.himitsu.BuildConfig
import ani.himitsu.R
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import bit.himitsu.io.Debug
import bit.himitsu.io.Debug.ISSUE_URL
import bit.himitsu.io.Memory
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.concurrent.Executors

object Logger {
    private const val TAG = "HimitsuLogger"
    var file: File? = null
    private val loggerExecutor = Executors.newSingleThreadExecutor()
    val disabled get() = PrefManager.getVal<Boolean>(PrefName.Lightspeed)

    fun init(context: Context) {
        try {
            if (!PrefManager.getVal<Boolean>(PrefName.LogToFile) || file != null) return
            val fileName = "${context.getString(R.string.git_issue_title, BuildConfig.COMMIT)}.txt"
            file = File(context.getExternalFilesDir(null), fileName)
            if (file?.exists() == true) {
                if (file!!.length() > 5 * 1024 * 1024) { // 5 MB
                    val oldFile = fileName.replace(".txt", ".${System.currentTimeMillis()}.txt")
                    file?.renameTo( File(context.getExternalFilesDir(null), oldFile))
                    file?.createNewFile()
                }
            } else {
                file?.createNewFile()
            }
            file?.appendText(getDeviceAndAppInfo())
        } catch (e: Exception) {
            e.printStackTrace()
            file = null
        }
    }

    fun shareLog(context: Context) {
        if (file == null) {
            toast(R.string.no_log)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.provider",
                            file!!
                        )
                    )
                    val text = context.getString(R.string.git_issue_title, BuildConfig.COMMIT)
                    putExtra(Intent.EXTRA_SUBJECT, text)
                    putExtra(Intent.EXTRA_TEXT, Debug.processLogcat())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.log_picker))
                )
            } catch (_: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUE_URL)))
                } catch (_: Exception) { }
            }
        }
    }

    fun log(message: String) {
        if (disabled) return
        val trace = Thread.currentThread().stackTrace[3]
        loggerExecutor.execute {
            Log.d(TAG, message)
            file?.let {
                val className = trace.className
                val methodName = trace.methodName
                val lineNumber = trace.lineNumber
                it.appendText("date/time: ${Date()} | $className.$methodName:$lineNumber\n")
                it.appendText("message: $message\n-\n")
            }
        }
    }

    fun log(level: Int, message: String, tag: String = TAG) {
        if (disabled) return
        val trace = Thread.currentThread().stackTrace[3]
        loggerExecutor.execute {
            Log.println(level, tag, message)
            file?.let {
                val className = trace.className
                val methodName = trace.methodName
                val lineNumber = trace.lineNumber
                it.appendText("date/time: ${Date()} | $className.$methodName:$lineNumber\n")
                it.appendText("message: $message\n-\n")
            }
        }
    }

    fun log(e: Exception) {
        if (disabled) return
        loggerExecutor.execute {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
            file?.let {
                it.appendText("---------------------------Exception---------------------------\n")
                it.appendText("className: ${e.cause?.javaClass?.name ?: e.javaClass.name}")
                it.appendText("date/time: ${Date()} |  ${e.message}\n")
                it.appendText("trace: ${e.stackTraceToString()}\n")
                it.appendText("---------------------------Exception---------------------------\n")
            }
        }
    }

    fun log(e: Throwable) {
        if (disabled) return
        loggerExecutor.execute {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
            file?.let {
                it.appendText("---------------------------Throwable---------------------------\n")
                it.appendText("className: ${e.cause?.javaClass?.name ?: e.javaClass.name}")
                it.appendText("date/time: ${Date()} |  ${e.message}\n")
                it.appendText("trace: ${e.stackTraceToString()}\n")
                it.appendText("---------------------------Throwable---------------------------\n")
            }
        }
    }

    fun uncaughtException(t: Thread?, e: Throwable) {
        if (disabled) return
        loggerExecutor.execute {
            file?.let {
                it.appendText("---------------------------Uncaught Exception---------------------------\n")
                t?.name?.let { name -> it.appendText("thread: ${name}\n") }
                it.appendText("date/time: ${Date()} |  ${e.message}\n")
                it.appendText("trace: ${e.stackTraceToString()}\n")
                it.appendText("---------------------------Uncaught Exception---------------------------\n")
            }
        }
    }

    fun clearLog() {
        file?.delete()
        file = null
    }

    private fun getDeviceAndAppInfo(): String {
        return buildString {
            append("--------------------------------\n")
            append("Date/time: ${Date()}\n")
            append("App hash: ${BuildConfig.COMMIT}\n")
            append("Device: ${Build.MODEL}\n")
            append("Memory: ${Memory.getDeviceRAM()} RAM")
            append("OS version: ${Build.VERSION.RELEASE}\n")
            append("SDK version: ${Build.VERSION.SDK_INT}\n")
            append("Manufacturer: ${Build.MANUFACTURER}\n")
            append("Brand: ${Build.BRAND}\n")
            append("Product: ${Build.PRODUCT}\n")
            append("Device: ${Build.DEVICE}\n")
            append("Hardware: ${Build.HARDWARE}\n")
            append("Type: ${Build.TYPE}\n")
            append("Tags: ${Build.TAGS}\n")
            append("Board: ${Build.BOARD}\n")
            append("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n")
            append("Supported 32 bit ABIs: ${Build.SUPPORTED_32_BIT_ABIS.joinToString()}\n")
            append("Supported 64 bit ABIs: ${Build.SUPPORTED_64_BIT_ABIS.joinToString()}\n")
            append("--------------------------------\n")
        }
    }
}