package ani.himitsu.media.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.R
import ani.himitsu.currContext
import ani.himitsu.databinding.FragmentAnimeWatchBinding
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.download.novel.NovelDownloaderService
import ani.himitsu.download.novel.NovelServiceDataSingleton
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.novel.novelreader.NovelReaderActivity
import ani.himitsu.navBarHeight
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.util.Logger
import ani.himitsu.util.StoragePermissions
import ani.himitsu.util.StoragePermissions.Companion.accessAlertDialog
import com.anggrayudi.storage.file.extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelReadFragment : Fragment(), DownloadCallback {

    private var _binding: FragmentAnimeWatchBinding? = null
    private val binding by lazy { _binding!! }
    private val model: MediaDetailsViewModel by activityViewModels()

    private lateinit var media: Media
    var source = 0
    lateinit var novelName: String

    private lateinit var headerAdapter: NovelReadAdapter
    private lateinit var novelResponseAdapter: NovelResponseAdapter
    private var progress = View.VISIBLE

    private var continueEp: Boolean = false
    var loaded = false

    override fun downloadTrigger(novelDownloadPackage: NovelDownloadPackage) {
        Logger.log("novel link: ${novelDownloadPackage.link}")
        activity?.let {
            fun continueDownload() {
                val downloadTask = NovelDownloaderService.DownloadTask(
                    title = media.mainName(),
                    chapter = novelDownloadPackage.novelName,
                    downloadLink = novelDownloadPackage.link,
                    originalLink = novelDownloadPackage.originalLink,
                    sourceMedia = media,
                    coverUrl = novelDownloadPackage.coverUrl,
                    retries = 2,
                )
                NovelServiceDataSingleton.downloadQueue.offer(downloadTask)
                CoroutineScope(Dispatchers.IO).launch {
                    if (!NovelServiceDataSingleton.isServiceRunning) {
                        val intent = Intent(context, NovelDownloaderService::class.java)
                        withUIContext {
                            ContextCompat.startForegroundService(requireContext(), intent)
                        }
                        NovelServiceDataSingleton.isServiceRunning = true
                    }
                }
            }
            if (!StoragePermissions.hasDirAccess(it)) {
                (it as MediaDetailsActivity).accessAlertDialog(it.launcher) { pathUri ->
                    if (pathUri != null) {
                        PrefManager.setVal(PrefName.DownloadsDir, pathUri)
                        continueDownload()
                    } else {
                        toast(R.string.download_permission_required)
                    }
                }
            } else {
                continueDownload()
            }
        }
    }

    override fun downloadedCheckWithStart(novel: ShowResponse): Boolean {
        val downloadsManager = Injekt.get<DownloadsManager>()
        if (downloadsManager.queryDownload(
                DownloadedType(
                    media.mainName(),
                    novel.name,
                    MediaType.NOVEL
                )
            )
        ) {
            try {
                val directory = DownloadsManager.getSubDirectory(
                    context ?: currContext(),
                    MediaType.NOVEL,
                    false,
                    media.mainName(),
                    novel.name
                )
                directory?.listFiles()?.find { it.extension == "epub" }?.let { file ->
                    val intent = Intent(context, NovelReaderActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        setDataAndType(file.uri, "application/epub+zip")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    startActivity(intent)
                } ?: return false
                return true
            } catch (e: Exception) {
                Logger.log(e)
                return false
            }
        } else {
            return false
        }
    }

    override fun downloadedCheck(novel: ShowResponse): Boolean {
        val downloadsManager = Injekt.get<DownloadsManager>()
        return downloadsManager.queryDownload(
            DownloadedType(
                media.mainName(),
                novel.name,
                MediaType.NOVEL
            )
        )
    }

    override fun deleteDownload(novel: ShowResponse) {
        val downloadsManager = Injekt.get<DownloadsManager>()
        downloadsManager.removeDownload(
            DownloadedType(
                media.mainName(),
                novel.name,
                MediaType.NOVEL
            )
        ) {}
    }

    fun onImportDownloadClick(novelName: String) {
        model.onImportDownloadClick(requireActivity(), media, novelName, MediaType.NOVEL)
    }

    private val downloadStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!this@NovelReadFragment::novelResponseAdapter.isInitialized) return
            when (intent.action) {
                ACTION_DOWNLOAD_STARTED -> {
                    val link = intent.getStringExtra(EXTRA_NOVEL_LINK)
                    link?.let {
                        novelResponseAdapter.startDownload(it)
                    }
                }

                ACTION_DOWNLOAD_FINISHED -> {
                    val link = intent.getStringExtra(EXTRA_NOVEL_LINK)
                    link?.let {
                        novelResponseAdapter.stopDownload(it)
                    }
                }

                ACTION_DOWNLOAD_FAILED -> {
                    val link = intent.getStringExtra(EXTRA_NOVEL_LINK)
                    link?.let {
                        novelResponseAdapter.purgeDownload(it)
                    }
                }

                ACTION_DOWNLOAD_PROGRESS -> {
                    val link = intent.getStringExtra(EXTRA_NOVEL_LINK)
                    val progress = intent.getIntExtra("progress", 0)
                    link?.let {
                        novelResponseAdapter.updateDownloadProgress(it, progress)
                    }
                }
            }
        }
    }

    var response: List<ShowResponse>? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_DOWNLOAD_STARTED)
            addAction(ACTION_DOWNLOAD_FINISHED)
            addAction(ACTION_DOWNLOAD_FAILED)
            addAction(ACTION_DOWNLOAD_PROGRESS)
        }

        ContextCompat.registerReceiver(
            requireContext(),
            downloadStatusReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        binding.animeSourceRecycler.updatePadding(
            bottom = binding.animeSourceRecycler.paddingBottom + navBarHeight
        )

        binding.animeSourceRecycler.layoutManager = LinearLayoutManager(requireContext())
        model.scrolledToTop.observe(viewLifecycleOwner) {
            if (it) binding.animeSourceRecycler.scrollToPosition(0)
        }

        continueEp = model.continueMedia == true
        model.getMedia().observe(viewLifecycleOwner) {
            if (it != null) {
                media = it
                novelName = media.userPreferredName
                progress = View.GONE
                binding.mediaInfoProgressBar.visibility = progress
                if (!loaded) {
                    val sel = media.selected
                    searchQuery = sel?.server ?: media.name ?: media.nameRomaji
                    headerAdapter = NovelReadAdapter(media, this, model.novelSources)
                    novelResponseAdapter = NovelResponseAdapter(this, this)  // probably a better way to do this b̶u̶t̶ i̶t̶ w̶o̶r̶k̶s̶
                    model.novelResponseAdapter = novelResponseAdapter
                    binding.animeSourceRecycler.adapter =
                        ConcatAdapter(headerAdapter, novelResponseAdapter)
                    loaded = true
                    search(searchQuery, sel?.sourceIndex ?: this.source, auto = sel?.server == null)
                }
            }
        }
        model.novelResponses.observe(viewLifecycleOwner) {
            if (it != null) {
                response = it
                novelResponseAdapter.submitList(it)
                headerAdapter.progress?.visibility = View.GONE
            }
            searching = false
        }
    }

    fun launchDownload(fileUri: Uri) {
        val intent = Intent(context, NovelReaderActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(fileUri, "application/epub+zip")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
    }

    fun getDownloads() {
        val novels = arrayListOf<ShowResponse>()
        DownloadsManager.getSubDirectory(
            context ?: currContext(),
            MediaType.NOVEL,
            false,
            media.mainName()
        )?.listFiles()?.forEach {
            if (it.isDirectory) {
                val directory = DownloadsManager.getSubDirectory(
                    context ?: currContext(),
                    MediaType.NOVEL,
                    false,
                    media.mainName(),
                    it.name
                )
                directory?.listFiles()?.find { it.extension == "epub" }?.let { file ->
                    novels.add(
                        ShowResponse(
                            file.name?.removeSuffix(".epub") ?: it.name!!,
                            file.uri.toString(),
                            directory.findFile("cover.jpg")?.uri?.toString()
                                ?: it.findFile("cover.jpg")?.uri?.toString()
                                ?: ""
                        )
                    )
                }
            }
        }
        novelResponseAdapter.submitList(novels)
    }

    lateinit var searchQuery: String
    private var searching = false
    fun search(query: String, source: Int = this.source, save: Boolean = false, auto: Boolean = false) {
        if (model.novelSources.list[source].name == "Downloaded") {
            getDownloads()
            return
        }
        if (!searching) {
            searchQuery = query
            headerAdapter.progress?.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                if (auto || query == "")
                    model.autoSearchNovels(media)
                else
                    model.searchNovels(query, source)
            }
            searching = true
            if (save) {
                val selected = model.loadSelected(media)
                selected.server = query
                model.saveSelected(media.id, selected)
            }
        }
    }

    fun onSourceChange(i: Int) {
        val selected = model.loadSelected(media)
        selected.sourceIndex = i
        source = i
        selected.server = null
        model.saveSelected(media.id, selected)
        media.selected = selected
        search(searchQuery, source, true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAnimeWatchBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onDestroy() {
        model.mangaReadSources?.flushText()
        requireContext().unregisterReceiver(downloadStatusReceiver)
        super.onDestroy()
    }

    private var state: Parcelable? = null
    override fun onResume() {
        super.onResume()
        binding.mediaInfoProgressBar.visibility = progress
        binding.animeSourceRecycler.layoutManager?.onRestoreInstanceState(state)
    }

    override fun onPause() {
        super.onPause()
        state = binding.animeSourceRecycler.layoutManager?.onSaveInstanceState()
    }

    companion object {
        const val ACTION_DOWNLOAD_STARTED = "ani.dantotsu.ACTION_DOWNLOAD_STARTED"
        const val ACTION_DOWNLOAD_FINISHED = "ani.dantotsu.ACTION_DOWNLOAD_FINISHED"
        const val ACTION_DOWNLOAD_FAILED = "ani.dantotsu.ACTION_DOWNLOAD_FAILED"
        const val ACTION_DOWNLOAD_PROGRESS = "ani.dantotsu.ACTION_DOWNLOAD_PROGRESS"
        const val EXTRA_NOVEL_LINK = "extra_novel_link"
    }
}

interface DownloadCallback {
    fun downloadTrigger(novelDownloadPackage: NovelDownloadPackage)
    fun downloadedCheck(novel: ShowResponse): Boolean
    fun downloadedCheckWithStart(novel: ShowResponse): Boolean
    fun deleteDownload(novel: ShowResponse)
}