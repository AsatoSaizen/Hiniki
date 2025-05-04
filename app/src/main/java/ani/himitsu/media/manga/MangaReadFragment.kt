package ani.himitsu.media.manga

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.Lazier
import ani.himitsu.R
import ani.himitsu.asyncMap
import ani.himitsu.databinding.FragmentAnimeWatchBinding
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.download.DownloadsManager.Companion.compareName
import ani.himitsu.download.DownloadsManager.Companion.getSubDirectory
import ani.himitsu.download.manga.MangaDownloaderService
import ani.himitsu.download.manga.MangaServiceDataSingleton
import ani.himitsu.isOnline
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.MediaNameAdapter
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.manga.mangareader.ChapterLoaderDialog
import ani.himitsu.notifications.subscription.SubscriptionHelper
import ani.himitsu.notifications.subscription.SubscriptionHelper.saveSubscription
import ani.himitsu.others.LanguageMapper
import ani.himitsu.parsers.BaseParser
import ani.himitsu.parsers.DynamicMangaParser
import ani.himitsu.parsers.HMangaSources
import ani.himitsu.parsers.MangaParser
import ani.himitsu.parsers.MangaReadSources
import ani.himitsu.parsers.MangaSources
import ani.himitsu.settings.extension.MangaSourcePreferencesFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.util.StoragePermissions.Companion.accessAlertDialog
import ani.himitsu.util.StoragePermissions.Companion.hasDirAccess
import ani.himitsu.util.customAlertDialog
import bit.himitsu.content.dpToColumns
import bit.himitsu.firebase.FireSale
import bit.himitsu.os.Version
import bit.himitsu.setNavigationTheme
import com.anggrayudi.storage.callback.ZipCompressionCallback
import com.anggrayudi.storage.file.compressToZip
import com.google.android.material.appbar.AppBarLayout
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.measureTime

open class MangaReadFragment : Fragment(), ScanlatorSelectionListener {
    private var _binding: FragmentAnimeWatchBinding? = null
    private val binding by lazy { _binding!! }
    private val model: MediaDetailsViewModel by activityViewModels()

    private lateinit var media: Media

    private var start = 0
    private var end: Int? = null
    private var style: Int? = null
    private var reverse = false

    private lateinit var headerAdapter: MangaReadAdapter
    private lateinit var chapterAdapter: MangaChapterAdapter

    val downloadManager = Injekt.get<DownloadsManager>()

    private var progress = View.VISIBLE

    var continueEp: Boolean = false
    var loaded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAnimeWatchBinding.inflate(inflater, container, false)
        return _binding?.root
    }

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

        var maxGridSize = 100.dpToColumns
        maxGridSize = max(4, maxGridSize - (maxGridSize % 2))
        val gridLayoutManager = GridLayoutManager(requireContext(), maxGridSize)

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position == 0 || chapterAdapter.getItemViewType(position) != 1)
                    maxGridSize
                else
                    1
            }
        }

        binding.animeSourceRecycler.layoutManager = gridLayoutManager

        binding.ScrollTop.setOnClickListener {
            binding.animeSourceRecycler.scrollToPosition(10)
            binding.animeSourceRecycler.smoothScrollToPosition(0)
        }
        binding.animeSourceRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val position = gridLayoutManager.findFirstVisibleItemPosition()
                if (position > 2) {
                    binding.ScrollTop.visibility = View.VISIBLE
                } else {
                    binding.ScrollTop.visibility = View.GONE
                }
            }
        })
        model.scrolledToTop.observe(viewLifecycleOwner) {
            if (it) binding.animeSourceRecycler.scrollToPosition(0)
        }

        continueEp = model.continueMedia == true
        model.getMedia().observe(viewLifecycleOwner) {
            if (it != null) {
                media = it
                progress = View.GONE
                binding.mediaInfoProgressBar.visibility = progress

                if (media.format == "MANGA" || media.format == "ONE SHOT") {
                    media.selected = model.loadSelected(media).apply {
                        recyclerReversed = PrefManager.getVal(PrefName.DescendingItems)
                    }

                    subscribed = SubscriptionHelper.getSubscriptions().containsKey(media.id)

                    style = media.selected!!.recyclerStyle
                    reverse = media.selected!!.recyclerReversed

                    if (!loaded) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            model.mangaReadSources = if (media.isAdult) HMangaSources else MangaSources

                            val clientMode = PrefManager.getVal<Boolean>(PrefName.ClientMode)
                            val noNetwork = !binding.root.context.isOnline
                            val offline_ext = !PrefManager.getVal<Boolean>(PrefName.OfflineMode)
                                    || PrefManager.getVal<Boolean>(PrefName.OfflineExt)
                            
                            val target = arguments?.getString("extension")
                                ?: if (clientMode) "Suwayomi" else null

                            val targetSources = target?.let { name ->
                                model.mangaReadSources?.list?.filter { source -> source.name == name }
                            }
                            media.selected!!.sourceIndex = when {
                                !targetSources.isNullOrEmpty() -> {
                                    model.mangaReadSources!!.names.indexOf(target)
                                }
                                noNetwork || !offline_ext -> {
                                    model.mangaReadSources!!.list.lastIndex
                                }
                                else -> {
                                    media.selected!!.sourceIndex
                                }
                            }

                            withUIContext {
                                headerAdapter = MangaReadAdapter(
                                    it, this@MangaReadFragment, model.mangaReadSources!!
                                ).apply { scanlatorSelectionListener = this@MangaReadFragment }
                                chapterAdapter = MangaChapterAdapter(
                                    style ?: PrefManager.getVal(PrefName.MangaDefaultView),
                                    media,
                                    this@MangaReadFragment
                                )
                                model.chapterAdapter = chapterAdapter

                                for (download in downloadManager.mangaDownloadedTypes) {
                                    if (media.compareName(download.titleName)) {
                                        chapterAdapter.stopDownload(download.chapterName)
                                    }
                                }

                                binding.animeSourceRecycler.adapter = ConcatAdapter(headerAdapter, chapterAdapter)

                                headerAdapter.updateSources(object: MangaReadSources() {
                                    override val list = targetSources
                                        ?: if (clientMode || noNetwork || !offline_ext)
                                            model.mangaReadSources?.list?.last()?.let { listOf(it) }
                                                ?: listOf()
                                        else listOf()
                                })
                            }

                            if (!targetSources.isNullOrEmpty() || clientMode || noNetwork || !offline_ext) {
                                loadChapters(media.selected!!.sourceIndex, false)
                                loaded = true
                                return@launch
                            }

                            getPreferredSources()
                            loaded = true
                        }
                    } else { reload() }
                } else {
                    binding.animeNotSupported.visibility = View.VISIBLE
                    binding.animeNotSupported.text =
                        getString(R.string.not_supported, media.format ?: "")
                }
            }
        }

        model.getMangaChapters().observe(viewLifecycleOwner) { _ -> updateChapters() }
    }

    override fun onScanlatorsSelected() {
        updateChapters()
    }

    fun multiDownload(n: Int) {
        // get last viewed chapter
        val selected = media.userProgress
        val chapters = media.manga?.chapters?.values?.toList()
        // filter by selected language
        val progressChapterIndex = (chapters?.indexOfFirst {
            MediaNameAdapter.findChapterNumber(it.number)?.toInt() == selected
        } ?: 0) + 1

        if (progressChapterIndex < 0 || n < 1 || chapters == null) return

        // Calculate the end index
        val endIndex = minOf(progressChapterIndex + n, chapters.size)

        // make sure there are enough chapters
        val chaptersToDownload = chapters.subList(progressChapterIndex, endIndex)


        for (chapter in chaptersToDownload) {
            onMangaChapterDownloadClick(chapter.title!!)
        }
    }


    private fun updateChapters() {
        val loadedChapters = model.getMangaChapters().value
        if (loadedChapters != null) {
            if (!this::headerAdapter.isInitialized) {
                headerAdapter = MangaReadAdapter(
                    media, this@MangaReadFragment, model.mangaReadSources!!
                ).apply { scanlatorSelectionListener = this@MangaReadFragment }
                chapterAdapter = MangaChapterAdapter(
                    style ?: PrefManager.getVal(PrefName.MangaDefaultView),
                    media,
                    this@MangaReadFragment
                )
                model.chapterAdapter = chapterAdapter
                binding.animeSourceRecycler.adapter =
                    ConcatAdapter(headerAdapter, chapterAdapter)
                if (loaded) getPreferredSources()
            }
            val chapters = loadedChapters[media.selected!!.sourceIndex]
            if (chapters != null) {
                headerAdapter.options = getScanlators(chapters)
                val filteredChapters = chapters.filterNot { (_, chapter) ->
                    chapter.scanlator in headerAdapter.hiddenScanlators
                }

                media.manga?.chapters = filteredChapters.toMutableMap()

                // CHIP GROUP
                val total = filteredChapters.size
                val divisions = total.toDouble() / 10
                start = 0
                end = null
                val limit = when {
                    (divisions < 25) -> 25
                    (divisions < 50) -> 50
                    else -> 100
                }
                headerAdapter.clearChips()
                if (total > limit) {
                    val arr = filteredChapters.keys.toTypedArray()
                    val stored = ceil(total.toDouble() / limit).toInt()
                    val position = clamp(media.selected!!.chip, 0, stored - 1)
                    val last = (if (position + 1 == stored) total else limit * (position + 1)) - 1
                    start = limit * (position)
                    end = last
                    headerAdapter.updateChips(
                        limit,
                        arr,
                        (1..stored).toList().toTypedArray(),
                        position
                    )
                }

                headerAdapter.subscribeButton(true)
                reload()
            }
        }
    }

    private fun getScanlators(chap: MutableMap<String, MangaChapter>?): List<String> {
        val scanlators = mutableListOf<String>()
        if (chap != null) {
            val chapters = chap.values
            for (chapter in chapters) {
                scanlators.add(chapter.scanlator ?: "??")
            }
        }
        return scanlators.distinct()
    }

    fun onSourceChange(i: Int): MangaParser {
        if (this::headerAdapter.isInitialized) headerAdapter.subscribeButton(false)
        media.manga?.chapters = null
        reload()
        val selected = model.loadSelected(media)
        model.mangaReadSources?.get(selected.sourceIndex)?.showUserTextListener = null
        selected.sourceIndex = i
        selected.server = null
        model.saveSelected(media.id, selected)
        media.selected = selected
        return model.mangaReadSources?.get(i)!!
    }

    fun onLangChange(i: Int, saveName: String) {
        val selected = model.loadSelected(media)
        selected.langIndex = i
        model.saveSelected(media.id, selected)
        media.selected = selected
        PrefManager.removeCustomVal("${saveName}_${media.id}")
    }

    fun onScanlatorChange(list: List<String>) {
        val selected = model.loadSelected(media)
        selected.scanlators = list
        model.saveSelected(media.id, selected)
        media.selected = selected
    }

    fun loadSearchChapters(i: Int, result: ShowResponse?) {
        lifecycleScope.launch(Dispatchers.IO) {
            result?.let { model.overrideMangaChapters(i, it, media.id) }
                ?: model.loadMangaChapters(media, i, false)
        }
    }

    fun loadChapters(i: Int, invalidate: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) { model.loadMangaChapters(media, i, invalidate) }
    }

    fun onIconPressed(viewType: Int, rev: Boolean) {
        style = viewType
        reverse = rev
        media.selected!!.recyclerStyle = style
        media.selected!!.recyclerReversed = reverse
        model.saveSelected(media.id, media.selected!!)
        reload()
    }

    fun onChipClicked(i: Int, s: Int, e: Int) {
        media.selected!!.chip = i
        start = s
        end = e
        model.saveSelected(media.id, media.selected!!)
        reload()
    }

    var subscribed = false
    fun onNotificationPressed(subscribed: Boolean, source: String) {
        this.subscribed = subscribed
        saveSubscription(media, subscribed)
        toast(
            if (subscribed)
                getString(R.string.subscribed_notification, source)
            else
                getString(R.string.unsubscribed_notification)
        )
    }

    fun openSettings(pkg: MangaExtension.Installed) {
        val changeUIVisibility: (Boolean) -> Unit = { show ->
            val activity = activity
            if (activity is MediaDetailsActivity && isAdded) {
                activity.findViewById<AppBarLayout>(R.id.mediaAppBar).isVisible = show
                activity.findViewById<ViewPager2>(R.id.mediaViewPager).isVisible = show
                activity.findViewById<CardView>(R.id.mediaCover).isVisible = show
                activity.findViewById<CardView>(R.id.mediaClose).isVisible = show
                activity.navBar.isVisible = show
                activity.findViewById<FrameLayout>(R.id.fragmentExtensionsContainer).isGone = show
            }
        }
        var itemSelected = false
        val allSettings = pkg.sources.filterIsInstance<ConfigurableSource>()
        if (allSettings.isNotEmpty()) {
            var selectedSetting = allSettings[0]
            if (allSettings.size > 1) {
                val names =
                    allSettings.map { LanguageMapper.mapLanguageCodeToName(it.lang) }.toTypedArray()
                requireContext().customAlertDialog().apply {
                    setTitle(R.string.select_source)
                    setSingleChoiceItems(names, -1) { which ->
                        selectedSetting = allSettings[which]
                        itemSelected = true

                        // Move the fragment transaction here
                        val fragment =
                            MangaSourcePreferencesFragment().getInstance(selectedSetting.id) {
                                changeUIVisibility(true)
                                loadChapters(media.selected!!.sourceIndex, true)
                            }
                        parentFragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                            .replace(R.id.fragmentExtensionsContainer, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                    setOnDismissListener { if (!itemSelected) changeUIVisibility(true) }
                    show()
                }
            } else {
                // If there's only one setting, proceed with the fragment transaction
                val fragment = MangaSourcePreferencesFragment().getInstance(selectedSetting.id) {
                    changeUIVisibility(true)
                    loadChapters(media.selected!!.sourceIndex, true)
                }
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                    .replace(R.id.fragmentExtensionsContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }

            changeUIVisibility(false)
        } else {
            Toast.makeText(requireContext(), "Source is not configurable", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun onMangaChapterClick(i: String) {
        model.continueMedia = false
        media.manga?.chapters?.get(i)?.let {
            media.manga?.selectedChapter = i
            model.saveSelected(media.id, media.selected!!)
            ChapterLoaderDialog.newInstance(it, true)
                .show(requireActivity().supportFragmentManager, "dialog")
        }
    }

    fun onImportDownloadClick(chapterNumber: String) {
        model.onImportDownloadClick(requireActivity(), media, chapterNumber, MediaType.MANGA)
    }

    fun onMangaChapterDownloadClick(i: String) {
        activity?.let {
            if (!isNotificationPermissionGranted()) {
                if (Version.isTiramisu) {
                    ActivityCompat.requestPermissions(
                        it,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1
                    )
                }
            }
            fun continueDownload() {
                model.continueMedia = false
                media.manga?.chapters?.get(i)?.let { chapter ->
                    val parser =
                        model.mangaReadSources?.get(media.selected!!.sourceIndex) as? DynamicMangaParser
                    parser?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            val images = parser.imageList(chapter.sChapter)

                            // Create a download task
                            val downloadTask = MangaDownloaderService.DownloadTask(
                                title = media.mainName(),
                                chapter = chapter.title!!,
                                imageData = images,
                                sourceMedia = media,
                                retries = 2,
                                simultaneousDownloads = 2
                            )

                            MangaServiceDataSingleton.downloadQueue.offer(downloadTask)

                            // If the service is not already running, start it
                            if (!MangaServiceDataSingleton.isServiceRunning) {
                                val intent = Intent(context, MangaDownloaderService::class.java)
                                withUIContext {
                                    ContextCompat.startForegroundService(requireContext(), intent)
                                }
                                MangaServiceDataSingleton.isServiceRunning = true
                            }

                            // Inform the adapter that the download has started
                            withUIContext {
                                chapterAdapter.startDownload(i)
                            }
                        }
                    }
                }
            }
            if (!hasDirAccess(it)) {
                (it as MediaDetailsActivity).accessAlertDialog(it.launcher) { pathUri ->
                    if (pathUri != null) {
                        PrefManager.setVal(PrefName.DownloadsDir, pathUri)
                        continueDownload()
                    } else {
                        toast(getString(R.string.download_permission_required))
                    }
                }
            } else {
                continueDownload()
            }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Version.isTiramisu) {
            return ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }


    fun onMangaChapterRemoveDownloadClick(i: String) {
        downloadManager.removeDownload(
            DownloadedType(
                media.mainName(),
                i,
                MediaType.MANGA
            )
        ) {
            chapterAdapter.deleteDownload(i)
        }
    }

    fun onMangaChapterStopDownloadClick(i: String) {
        val cancelIntent = Intent().apply {
            action = MangaDownloaderService.ACTION_CANCEL_DOWNLOAD
            putExtra(MangaDownloaderService.EXTRA_CHAPTER, i)
        }
        requireContext().sendBroadcast(cancelIntent)

        // Remove the download from the manager and update the UI
        downloadManager.removeDownload(
            DownloadedType(
                media.mainName(),
                i,
                MediaType.MANGA
            )
        ) {
            chapterAdapter.purgeDownload(i)
        }
    }

    fun createComicArchive(context: Context, chapterNumber: String) {
        CoroutineScope(Dispatchers.IO).launch {
            getSubDirectory(
                context,
                MediaType.MANGA,
                false,
                media.mainName()
            )?.let { targetUri ->
                val folder = targetUri.findFile(chapterNumber)
                targetUri.createFile(
                    context.getString(R.string.mimetype_cbz),
                    "$chapterNumber.cbz"
                )?.let { zip ->
                    folder?.listFiles()?.toList()?.compressToZip(
                        context,
                        zip,
                        deleteSourceWhenComplete = false,
                        object: ZipCompressionCallback<DocumentFile>() {
                            override fun onCompleted(
                                zipFile: DocumentFile,
                                bytesCompressed: Long,
                                totalFilesCompressed: Int,
                                compressionRate: Float
                            ) {
                                super.onCompleted(
                                    zipFile,
                                    bytesCompressed,
                                    totalFilesCompressed,
                                    compressionRate
                                )
//                                folder.listFiles().forEach { img ->
//                                    img.delete()
//                                }
//                                folder.delete()
                                CoroutineScope(Dispatchers.Main).launch {
                                    onMangaChapterRemoveDownloadClick(chapterNumber)
                                }
                                toast(getString(R.string.cbz_created, chapterNumber))
                            }
                        }
                    )
                } ?: toast(R.string.file_not_create)
            } ?: toast(R.string.file_not_found)
        }
    }

    fun onMangaChapterRemoveDownloadLongClick(chapterNumber: String) {
        createComicArchive(requireContext(), chapterNumber)
    }

    private val downloadStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!this@MangaReadFragment::chapterAdapter.isInitialized) return
            when (intent.action) {
                ACTION_DOWNLOAD_STARTED -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_CHAPTER_NUMBER)
                    chapterNumber?.let { chapterAdapter.startDownload(it) }
                }

                ACTION_DOWNLOAD_FINISHED -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_CHAPTER_NUMBER)
                    chapterNumber?.let {
                        chapterAdapter.stopDownload(it)
                        if (PrefManager.getVal(PrefName.ComicBookFormat)) {
                            createComicArchive(context, it)
                        }
                    }
                }

                ACTION_DOWNLOAD_FAILED -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_CHAPTER_NUMBER)
                    chapterNumber?.let { chapterAdapter.purgeDownload(it) }
                }

                ACTION_DOWNLOAD_PROGRESS -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_CHAPTER_NUMBER)
                    val progress = intent.getIntExtra("progress", 0)
                    chapterNumber?.let { chapterAdapter.updateDownloadProgress(it, progress) }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reload() {
        val selected = model.loadSelected(media)

        // Find latest chapter for subscription
        selected.latest = media.manga?.chapters?.values?.maxOfOrNull {
            it.number.toFloatOrNull() ?: 0f
        } ?: 0f
        selected.latest = media.userProgress?.toFloat()?.takeIf {
            selected.latest < it
        } ?: selected.latest

        model.saveSelected(media.id, selected)
        FireSale().getCurrent("${media.id}_current_chp", MediaType.MANGA) {
            headerAdapter.handleChapters()

            chapterAdapter.updateType(style ?: PrefManager.getVal(PrefName.MangaDefaultView))
            chapterAdapter.updateChapters(media.manga!!.chapters?.let { chapters ->
                val end = end?.takeIf { it < chapters.size } ?: (chapters.size - 1)
                chapters.values.toList().slice(start..end).let {
                    if (reverse) it.reversed() else it
                }
            } ?: listOf())
        }
    }

    override fun onDestroy() {
        model.mangaReadSources?.flushText()
        super.onDestroy()
        requireContext().unregisterReceiver(downloadStatusReceiver)
    }

    fun reloadSource() {
        lifecycleScope.launch(Dispatchers.IO) {
            onSourceChange(media.selected!!.sourceIndex)
            loadChapters(media.selected!!.sourceIndex, true)
        }
    }

    private fun getPreferredSources() {
        if (PrefManager.getVal(PrefName.SearchSources)
            && !PrefManager.getVal<Boolean>(PrefName.OfflineMode)
            && !model.mangaReadSources?.list.isNullOrEmpty()) {
            if (sortedSources.isNotEmpty() && sortedSources[media] != null) {
                model.mangaReadSources = sortedSources[media]
                model.mangaReadSources?.let {
                    lifecycleScope.launch(Dispatchers.IO) {
                        withUIContext {
                            headerAdapter.updateSources(it)
                        }
                        onSourceChange(media.selected!!.sourceIndex)
                        loadSearchChapters(
                            media.selected!!.sourceIndex,
                            sortedResults[it.list[media.selected!!.sourceIndex].name]
                        )
                    }
                }
                return
            }
            sortedResults.clear()
            val sources : ArrayList<SourceSearch> = arrayListOf()
            val download = model.mangaReadSources?.list?.last()

            model.mangaReadSources?.list?.asyncMap { source ->
                val searchTime =  try {
                    withTimeout(1000L) { measureTime {
                        source.get.value?.autoSearch(media)?.also { res ->
                            sortedResults.put(source.name, res)
                        } ?: throw Exception()
                    }.inWholeMilliseconds }
                } catch (_: Exception) { 1250L }
                sources.add(SourceSearch(searchTime, source))
            }
            download?.let { dl ->
                sources.remove(sources.find { it.parser.name == dl.name })
                sources.add(SourceSearch(1500L, dl))
            }
            model.mangaReadSources = object: MangaReadSources() {
                override val list = sources.sortedBy { it.timeout }.map { it.parser }
            }
            sortedSources[media] = model.mangaReadSources
            model.mangaReadSources?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    withUIContext {
                        headerAdapter.updateSources(it)
                    }
                    onSourceChange(0)
                    loadSearchChapters(0, sortedResults[it.list[0].name])
                }
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                withUIContext {
                    headerAdapter.updateSources(model.mangaReadSources!!)
                }
                loadChapters(media.selected!!.sourceIndex, false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mediaInfoProgressBar.visibility = progress
        requireActivity().setNavigationTheme()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    data class SourceSearch(
        val timeout: Long,
        val parser: Lazier<BaseParser>
    )

    companion object {
        const val ACTION_DOWNLOAD_STARTED = "ani.dantotsu.ACTION_DOWNLOAD_STARTED"
        const val ACTION_DOWNLOAD_FINISHED = "ani.dantotsu.ACTION_DOWNLOAD_FINISHED"
        const val ACTION_DOWNLOAD_FAILED = "ani.dantotsu.ACTION_DOWNLOAD_FAILED"
        const val ACTION_DOWNLOAD_PROGRESS = "ani.dantotsu.ACTION_DOWNLOAD_PROGRESS"
        const val EXTRA_CHAPTER_NUMBER = "extra_chapter_number"

        private var sortedSources : MutableMap<Media, MangaReadSources?> = mutableMapOf()
        private var sortedResults : MutableMap<String, ShowResponse> = mutableMapOf()
    }
}