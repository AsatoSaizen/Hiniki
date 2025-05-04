package ani.himitsu.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import ani.himitsu.Himitsu
import ani.himitsu.R
import ani.himitsu.download.DownloadCompat.removeDownloadCompat
import ani.himitsu.download.DownloadCompat.removeMediaCompat
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.toast
import ani.himitsu.util.Logger
import com.anggrayudi.storage.callback.FolderCallback
import com.anggrayudi.storage.callback.ZipDecompressionCallback
import com.anggrayudi.storage.file.decompressZip
import com.anggrayudi.storage.file.deleteRecursively
import com.anggrayudi.storage.file.findFolder
import com.anggrayudi.storage.file.moveFolderTo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.xdrop.fuzzywuzzy.FuzzySearch
import tachiyomi.core.util.lang.withUIContext
import java.io.Serializable

class DownloadsManager(private val context: Context) {
    private val gson = Gson()
    private val downloadsList = loadDownloads().toMutableList()

    val mangaDownloadedTypes: List<DownloadedType>
        get() = downloadsList.filter { it.type == MediaType.MANGA }
    val animeDownloadedTypes: List<DownloadedType>
        get() = downloadsList.filter { it.type == MediaType.ANIME }
    val novelDownloadedTypes: List<DownloadedType>
        get() = downloadsList.filter { it.type == MediaType.NOVEL }

    private fun saveDownloads() {
        val jsonString = gson.toJson(downloadsList)
        PrefManager.setVal(PrefName.DownloadsKeys, jsonString)
    }

    private fun loadDownloads(): List<DownloadedType> {
        val jsonString = PrefManager.getVal(PrefName.DownloadsKeys, null as String?)
        return if (jsonString != null) {
            val type = object : TypeToken<List<DownloadedType>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            emptyList()
        }
    }

    fun addDownload(downloadedType: DownloadedType) {
        downloadsList.add(downloadedType)
        saveDownloads()
    }

    fun removeDownload(
        downloadedType: DownloadedType,
        toast: Boolean = true,
        onFinished: () -> Unit
    ) {
        removeDownloadCompat(context, downloadedType)
        downloadsList.remove(downloadedType)
        CoroutineScope(Dispatchers.IO).launch {
            removeDirectory(downloadedType, toast)
            withUIContext { onFinished() }
        }
        saveDownloads()
    }

    fun removeMedia(title: String, type: MediaType) {
        removeMediaCompat(context, title, type)
        val baseDirectory = getBaseDirectory(context, type)
        val directory = baseDirectory?.findFolder(title)
        if (directory?.exists() == true) {
            val deleted = directory.deleteRecursively(context, false)
            if (deleted) {
                toast(R.string.successfully_deleted)
            } else {
                toast(R.string.failed_to_delete_directory)
            }
        } else {
            toast(R.string.directory_not_found)
            cleanDownloads()
        }
        downloadsList.removeAll { it.titleName == title && it.type == type }
        saveDownloads()
    }

    private fun cleanDownloads() {
        cleanDownload(MediaType.MANGA)
        cleanDownload(MediaType.ANIME)
        cleanDownload(MediaType.NOVEL)
    }

    private fun cleanDownload(type: MediaType) {
        // remove all folders that are not in the downloads list
        val directory = getBaseDirectory(context, type)
        val downloadsSubLists = when (type) {
            MediaType.MANGA -> mangaDownloadedTypes
            MediaType.NOVEL -> novelDownloadedTypes
            else -> animeDownloadedTypes
        }
        if (directory?.exists() == true && directory.isDirectory) {
            val files = directory.listFiles()
            for (file in files) {
                if (!downloadsSubLists.any { it.titleName == file.name }) {
                    file.deleteRecursively(context, false)
                }
            }
        }
        // now remove all downloads that do not have a folder
        val iterator = downloadsList.iterator()
        while (iterator.hasNext()) {
            val download = iterator.next()
            val downloadDir = directory?.findFolder(download.titleName)
            if ((downloadDir?.exists() == false && download.type == type) || download.titleName.isBlank()) {
                iterator.remove()
            }
        }
    }

    fun moveDownloadsDir(
        context: Context,
        oldUri: Uri,
        newUri: Uri,
        finished: (Boolean, String) -> Unit
    ) {
        if (oldUri == newUri) {
            Logger.log("Source and destination are the same")
            finished(false, "Source and destination are the same")
            return
        }
        if (oldUri == Uri.EMPTY) {
            Logger.log("Old Uri is empty")
            finished(true, "Old Uri is empty")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val oldBase = DocumentFile.fromTreeUri(context, oldUri)
                    ?: throw Exception("Old base is null")
                val newBase = DocumentFile.fromTreeUri(context, newUri)
                    ?: throw Exception("New base is null")
                val oldFolder = oldBase.findFolder(Himitsu.appName)
                    ?: throw Exception("Base folder not found")
                oldFolder.moveFolderTo(context, newBase, false, Himitsu.appName,
                    object : FolderCallback() {
                    override fun onFailed(errorCode: ErrorCode) {
                        when (errorCode) {
                            ErrorCode.CANCELED -> finished(false, "Move canceled")
                            ErrorCode.CANNOT_CREATE_FILE_IN_TARGET -> finished(
                                false,
                                "Cannot create file in target"
                            )

                            ErrorCode.INVALID_TARGET_FOLDER -> finished(
                                true,
                                "Invalid target folder"
                            ) // seems to still work
                            ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH -> finished(
                                false,
                                "No space left on target path"
                            )

                            ErrorCode.UNKNOWN_IO_ERROR -> finished(false, "Unknown IO error")
                            ErrorCode.SOURCE_FOLDER_NOT_FOUND -> finished(
                                false,
                                "Source folder not found"
                            )

                            ErrorCode.STORAGE_PERMISSION_DENIED -> finished(
                                false,
                                "Storage permission denied"
                            )

                            ErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER -> finished(
                                false,
                                "Target folder cannot have same path with source folder"
                            )

                            else -> finished(false, "Failed to move downloads: $errorCode")
                        }
                        Logger.log("Failed to move downloads: $errorCode")
                        super.onFailed(errorCode)
                    }

                    override fun onCompleted(result: Result) {
                        finished(true, "Successfully moved downloads")
                        PrefManager.setVal(PrefName.DownloadsDir, result.folder.uri.toString())
                        super.onCompleted(result)
                    }
                })
            } catch (e: Exception) {
                Logger.log(e)
                Logger.log("oldUri: $oldUri, newUri: $newUri")
                finished(false, "Failed to move downloads: ${e.message}")
                return@launch
            }
        }
    }

    fun importMediaFile(mediaUri: Uri, targetUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                with (context.contentResolver) {
                    val downloads = DocumentFile.fromTreeUri(context, targetUri)
                        ?: throw Exception("Import destination is null")
                    val mimeType = getType(mediaUri) ?: throw Exception("Import MimeType is null")
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    val fileName = DocumentFile.fromSingleUri(context, mediaUri)?.name
                        ?: "media.${extension}"
                    downloads.createFile(mimeType, fileName)?.let { file ->
                        openInputStream(mediaUri)?.use { input ->
                            openOutputStream(file.uri)?.use { output ->
                                val buffer = ByteArray(1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                                if (extension == "cbz") {
                                    DocumentFile.fromSingleUri(context, file.uri)?.decompressZip(
                                        context,
                                        downloads,
                                        object: ZipDecompressionCallback<DocumentFile>() {
                                            override fun onCompleted(
                                                zipFile: DocumentFile,
                                                targetFolder: DocumentFile,
                                                decompressionInfo: DecompressionInfo
                                            ) {
                                                super.onCompleted(
                                                    zipFile,
                                                    targetFolder,
                                                    decompressionInfo
                                                )
                                                zipFile.delete()
                                            }
                                        }
                                    )
                                }
                                if (PrefManager.getVal<Boolean>(PrefName.DeleteOnImport))
                                    DocumentsContract.deleteDocument(this, mediaUri)
                                toast(R.string.media_imported)
                            } ?: throw Exception("Import OutputStream is null")
                        } ?: throw Exception("Import InputStream is null")
                    } ?: throw Exception("Import file creation null")
                }
            } catch (e: Exception) {
                Logger.log(e)
            }
        }
    }

    fun queryDownload(downloadedType: DownloadedType): Boolean {
        return downloadsList.contains(downloadedType)
    }

    fun queryDownload(title: String, chapter: String, type: MediaType? = null): Boolean {
        return if (type == null) {
            downloadsList.any { it.titleName == title && it.chapterName == chapter }
        } else {
            downloadsList.any { it.titleName == title && it.chapterName == chapter && it.type == type }
        }
    }

    private fun removeDirectory(downloadedType: DownloadedType, toast: Boolean) {
        val baseDirectory = getBaseDirectory(context, downloadedType.type)
        val directory =
            baseDirectory?.findFolder(downloadedType.titleName)
                ?.findFolder(downloadedType.chapterName)
        downloadsList.remove(downloadedType)
        // Check if the directory exists and delete it recursively
        if (directory?.exists() == true) {
            val deleted = directory.deleteRecursively(context, false)
            if (deleted) {
                if (toast) toast(R.string.successfully_deleted)
            } else {
                toast(R.string.failed_to_delete_directory)
            }
        } else {
            toast(R.string.directory_not_found)
        }
    }

    fun purgeDownloads(type: MediaType) {
        val directory = getBaseDirectory(context, type)
        if (directory?.exists() == true) {
            val deleted = directory.deleteRecursively(context, false)
            if (deleted) {
                toast(R.string.successfully_deleted)
            } else {
                toast(R.string.failed_to_delete_directory)
            }
        } else {
            snackString(R.string.directory_not_found)
        }

        downloadsList.removeAll { it.type == type }
        saveDownloads()
    }

    companion object {
        /**
         * Get and create a base directory for the given type
         * @param context the context
         * @param type the type of media
         * @return the base directory
         */
        fun getBaseDirectory(context: Context, type: MediaType?): DocumentFile? {
            val baseDirectory = Uri.parse(PrefManager.getVal<String>(PrefName.DownloadsDir))
            if (baseDirectory == Uri.EMPTY) return null
            var base = DocumentFile.fromTreeUri(context, baseDirectory) ?: return null
            base = base.findOrCreateFolder(Himitsu.appName, false) ?: return null
            return type?.text?.let { base.findOrCreateFolder(it, false) } ?: base
        }

        /**
         * Get and create a subdirectory for the given type
         * @param context the context
         * @param type the type of media
         * @param title the title of the media
         * @param chapter the chapter of the media
         * @return the subdirectory
         */
        fun getSubDirectory(
            context: Context,
            type: MediaType,
            overwrite: Boolean,
            title: String,
            chapter: String? = null
        ): DocumentFile? {
            val baseDirectory = getBaseDirectory(context, type) ?: return null
            return if (chapter != null) {
                baseDirectory.findOrCreateFolder(title, false)
                    ?.findOrCreateFolder(chapter, overwrite)
            } else {
                baseDirectory.findOrCreateFolder(title, overwrite)
            }
        }

        fun getDirSize(
            context: Context,
            type: MediaType,
            title: String,
            chapter: String? = null
        ): Long {
            val directory = getSubDirectory(context, type, false, title, chapter) ?: return 0
            var size = 0L
            directory.listFiles().forEach { size += it.length() }
            return size
        }

        fun addNoMedia(context: Context) {
            val downloadsDir = Uri.parse(PrefManager.getVal<String>(PrefName.DownloadsDir))
            if (downloadsDir == Uri.EMPTY) return
            val baseDirectory = DocumentFile.fromTreeUri(context, downloadsDir) ?: return
            if (baseDirectory.findFile(".nomedia") == null) {
                baseDirectory.createFile("application/octet-stream", ".nomedia")
                MediaScannerConnection.scanFile(
                    context, arrayOf(baseDirectory.uri.path), null, null
                )
            }
        }

        fun DocumentFile.findOrCreateFolder(
            name: String, overwrite: Boolean
        ): DocumentFile? {
            return if (overwrite) {
                findFolder(name.findValidName())?.delete()
                createDirectory(name.findValidName())
            } else {
                findFolder(name.findValidName()) ?: createDirectory(name.findValidName())
            }
        }

        private const val RATIO_THRESHOLD = 95
        fun Media.compareName(name: String): Boolean {
            val mainName = mainName().findValidName().lowercase()
            val ratio = FuzzySearch.ratio(mainName, name.lowercase())
            return ratio > RATIO_THRESHOLD
        }

        fun String.compareName(name: String): Boolean {
            val mainName = findValidName().lowercase()
            val compareName = name.findValidName().lowercase()
            val ratio = FuzzySearch.ratio(mainName, compareName)
            return ratio > RATIO_THRESHOLD
        }
    }
}

enum class DownloadManager {
    AddOn,
    OneDM,
    ADM,
    Browser;

    companion object {
        fun fromPref(): DownloadManager {
            return DownloadManager.entries[PrefManager.getVal<Int>(PrefName.DownloadManager)]
        }
    }
}

private const val RESERVED_CHARS = "|\\?*<\":>+[]/'"
fun String?.findValidName(): String {
    return this?.replace("/","_")?.filterNot { RESERVED_CHARS.contains(it) } ?: ""
}

data class DownloadedType(
    private val pTitle: String?,
    private val pChapter: String?,
    val type: MediaType,
    @Deprecated("use pTitle instead")
    private val title: String? = null,
    @Deprecated("use pChapter instead")
    private val chapter: String? = null
) : Serializable {
    val titleName: String
        get() = title ?: pTitle.findValidName()
    val chapterName: String
        get() = chapter ?: pChapter.findValidName()
}