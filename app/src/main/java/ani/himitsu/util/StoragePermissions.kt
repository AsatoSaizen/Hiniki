package ani.himitsu.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ani.himitsu.R
import ani.himitsu.download.DownloadsManager
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast

class StoragePermissions {
    companion object {
        fun downloadsPermission(activity: AppCompatActivity): Boolean {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) return true
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )

            val requiredPermissions = permissions.filterNot {
                ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            return if (requiredPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity,
                    requiredPermissions,
                    DOWNLOADS_PERMISSION_REQUEST_CODE
                )
                false
            } else {
                true
            }
        }

        fun hasDirAccess(context: Context, path: String): Boolean {
            val uri = pathToUri(path)
            return context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }

        }

        fun hasDirAccess(context: Context, uri: Uri): Boolean {
            return context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        }

        fun hasDirAccess(context: Context): Boolean {
            val path = PrefManager.getVal<String>(PrefName.DownloadsDir)
            return hasDirAccess(context, path)
        }

        fun AppCompatActivity.accessAlertDialog(
            launcher: LauncherWrapper,
            force: Boolean = false,
            complete: (String?) -> Unit
        ) {
            if (hasDirAccess(this) && !force) {
                complete(this.toString())
                return
            }
            customAlertDialog().apply {
                setTitle(R.string.dir_access)
                setMessage(R.string.dir_access_msg)
                setPositiveButton(R.string.ok) {
                    launcher.registerForCallback(complete).launch()
                }
                setNegativeButton(R.string.cancel) {
                    complete(null)
                }
                show()
            }
        }

        private fun pathToUri(path: String): Uri {
            return Uri.parse(path)
        }

        private const val DOWNLOADS_PERMISSION_REQUEST_CODE = 100
    }
}

class LauncherWrapper(
    activity: AppCompatActivity,
    contract: ActivityResultContracts.OpenDocumentTree
) {
    private var launcher: ActivityResultLauncher<Uri?>
    var complete: (String?) -> Unit = {}

    init {
        launcher = activity.registerForActivityResult(contract) { uri ->
            if (uri != null) {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                if (StoragePermissions.hasDirAccess(activity, uri)) {
                    DownloadsManager.addNoMedia(activity)
                    complete(uri.toString())
                } else {
                    toast(activity.getString(R.string.dir_error))
                    complete(null)
                }
            } else {
                toast(activity.getString(R.string.dir_error))
                complete(null)
            }
        }
    }

    fun registerForCallback(callback: (String?) -> Unit) : LauncherWrapper {
        complete = callback
        return this
    }

    fun launch() {
        launcher.launch(null)
    }
}

class DocumentWrapper(
    activity: AppCompatActivity,
    contract: ActivityResultContracts.OpenMultipleDocuments
) {
    private var launcher: ActivityResultLauncher<Array<String>>
    var complete: (List<Uri>?) -> Unit = {}

    init {
        launcher = activity.registerForActivityResult(contract) { uris ->
            uris.forEach {
                activity.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            complete(uris)
        }
    }

    fun registerForCallback(callback: (List<Uri>?) -> Unit) : DocumentWrapper {
        complete = callback
        return this
    }

    fun launch(mimeTypes: Array<String>) {
        launcher.launch(mimeTypes)
    }
}