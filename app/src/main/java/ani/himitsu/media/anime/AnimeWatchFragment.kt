package ani.himitsu.media.anime

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.Lazier
import ani.himitsu.R
import ani.himitsu.asyncMap
import ani.himitsu.databinding.FragmentAnimeWatchBinding
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.download.DownloadsManager.Companion.compareName
import ani.himitsu.download.DownloadsManager.Companion.getSubDirectory
import ani.himitsu.download.anime.AnimeDownloaderService
import ani.himitsu.download.findValidName
import ani.himitsu.isOnline
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.notifications.subscription.SubscriptionHelper
import ani.himitsu.notifications.subscription.SubscriptionHelper.saveSubscription
import ani.himitsu.others.LanguageMapper
import ani.himitsu.parsers.AnimeParser
import ani.himitsu.parsers.AnimeSources
import ani.himitsu.parsers.BaseParser
import ani.himitsu.parsers.HAnimeSources
import ani.himitsu.parsers.WatchSources
import ani.himitsu.settings.extension.AnimeSourcePreferencesFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.util.Logger
import ani.himitsu.util.StoragePermissions.Companion.accessAlertDialog
import ani.himitsu.util.StoragePermissions.Companion.hasDirAccess
import bit.himitsu.content.dpToColumns
import bit.himitsu.firebase.FireSale
import bit.himitsu.media.buildEpisodeMeta
import bit.himitsu.setNavigationTheme
import com.anggrayudi.storage.file.extension
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.measureTime

class AnimeWatchFragment : Fragment() {
    private var _binding: FragmentAnimeWatchBinding? = null
    private val binding by lazy { _binding!! }
    private val model: MediaDetailsViewModel by activityViewModels()

    private lateinit var media: Media

    private var start = 0
    private var end: Int? = null
    private var style: Int? = null
    private var reverse = false

    private lateinit var headerAdapter: AnimeWatchAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

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
                return if (position == 0)
                    maxGridSize
                else
                    when (episodeAdapter.getItemViewType(position)) {
                        1 -> 2
                        2 -> 1
                        else -> maxGridSize
                    }
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
                    //binding.ScrollTop.translationY = -navBarHeight.toFloat()
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
                media.selected = model.loadSelected(media).apply {
                    recyclerReversed = PrefManager.getVal(PrefName.DescendingItems)
                }

                subscribed = SubscriptionHelper.getSubscriptions().containsKey(media.id)

                style = media.selected!!.recyclerStyle
                reverse = media.selected!!.recyclerReversed

                progress = View.GONE
                binding.mediaInfoProgressBar.visibility = progress

                if (!loaded) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        model.watchSources = if (media.isAdult) HAnimeSources else AnimeSources

                        val clientMode = PrefManager.getVal<Boolean>(PrefName.ClientMode)
                        val noNetwork = !binding.root.context.isOnline
                        val offline_ext = !PrefManager.getVal<Boolean>(PrefName.OfflineMode)
                                || PrefManager.getVal<Boolean>(PrefName.OfflineExt)

                        val target = arguments?.getString("extension")
                            ?: if (clientMode) "Jellyfin" else null

                        val targetSources = target?.let { name ->
                            model.watchSources?.list?.filter { source -> source.name == name }
                        }
                        media.selected!!.sourceIndex = when {
                            !targetSources.isNullOrEmpty() -> {
                                model.watchSources!!.names.indexOf(target)
                            }
                            noNetwork || !offline_ext -> {
                                model.watchSources!!.list.lastIndex
                            }
                            else -> {
                                media.selected!!.sourceIndex
                            }
                        }

                        withUIContext {
                            headerAdapter = AnimeWatchAdapter(
                                it, this@AnimeWatchFragment, model.watchSources!!
                            )
                            episodeAdapter = EpisodeAdapter(
                                style ?: PrefManager.getVal(PrefName.AnimeDefaultView),
                                media,
                                this@AnimeWatchFragment
                            )
                            model.episodeAdapter = episodeAdapter

                            binding.animeSourceRecycler.adapter = ConcatAdapter(headerAdapter, episodeAdapter)

                            headerAdapter.updateSources(object : WatchSources() {
                                override val list = targetSources
                                    ?: if (clientMode || noNetwork || !offline_ext)
                                        model.watchSources?.list?.last()?.let { listOf(it) }
                                            ?: listOf()
                                    else
                                        listOf()
                            })
                        }

                        if (!targetSources.isNullOrEmpty() || clientMode || noNetwork || !offline_ext) {
                            loadEpisodeList()
                            loaded = true
                            return@launch
                        }

                        getPreferredSources()
                        loaded = true
                    }
                } else { reload() }
            }
        }
        model.getEpisodes().observe(viewLifecycleOwner) { loadedEpisodes ->
            if (loadedEpisodes != null) {
                if (!this::headerAdapter.isInitialized) {
                    headerAdapter = AnimeWatchAdapter(
                        media, this@AnimeWatchFragment, model.watchSources!!
                    )
                    episodeAdapter = EpisodeAdapter(
                        style ?: PrefManager.getVal(PrefName.AnimeDefaultView),
                        media,
                        this@AnimeWatchFragment
                    )
                    model.episodeAdapter = episodeAdapter
                    binding.animeSourceRecycler.adapter =
                        ConcatAdapter(headerAdapter, episodeAdapter)
                    if (loaded) getPreferredSources()
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val episodes = loadedEpisodes[media.selected!!.sourceIndex]

                    if (binding.root.context.isOnline
                        && PrefManager.getVal<Boolean>(PrefName.EpisodeSources)
                    ) {
                        awaitAll(
                            async { model.loadKitsuEpisodes(media) },
                            async { model.loadAnifyEpisodes(media.id) },
                            async { model.loadFillerEpisodes(media) }
                        )
                    }
                    episodes?.map { source -> async { media.buildEpisodeMeta(source) } }?.awaitAll()
                    media.anime?.episodes = episodes

                    // CHIP GROUP
                    val total = episodes?.size ?: 0
                    val divisions = total.toDouble() / 10
                    start = 0
                    end = null
                    val limit = when {
                        (divisions < 25) -> 25
                        (divisions < 50) -> 50
                        else -> 100
                    }
                    withUIContext {
                        headerAdapter.clearChips()
                        if (total > limit) {
                            val arr = media.anime!!.episodes!!.keys.toTypedArray()
                            val stored = ceil(total.toDouble() / limit).toInt()
                            val position = clamp(media.selected!!.chip, 0, stored - 1)
                            val last =
                                (if (position + 1 == stored) total else limit * (position + 1)) - 1
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
        }

        model.getKitsuEpisodes().observe(viewLifecycleOwner) { i ->
            if (i != null) { media.anime?.kitsuEpisodes = i }
        }

        model.getAnifyEpisodes().observe(viewLifecycleOwner) { i ->
            if (i != null) { media.anime?.anifyEpisodes = i }
        }

        model.getFillerEpisodes().observe(viewLifecycleOwner) { i ->
            if (i != null) { media.anime?.fillerEpisodes = i }
        }
    }

    fun onSourceChange(i: Int): AnimeParser {
        if (this::headerAdapter.isInitialized) headerAdapter.subscribeButton(false)
        val selected = model.loadSelected(media)
        model.watchSources?.get(selected.sourceIndex)?.showUserTextListener = null
        selected.sourceIndex = i
        selected.server = null
        model.saveSelected(media.id, selected)
        media.selected = selected
        return model.watchSources?.get(i)!!
    }

    fun onLangChange(i: Int) {
        val selected = model.loadSelected(media)
        selected.langIndex = i
        model.saveSelected(media.id, selected)
        media.selected = selected
    }

    fun onDubClicked(checked: Boolean) {
        val selected = model.loadSelected(media)
        model.watchSources?.get(selected.sourceIndex)?.selectDub = checked
        selected.preferDub = checked
        model.saveSelected(media.id, selected)
        media.selected = selected
        lifecycleScope.launch(Dispatchers.IO) {
            model.forceLoadEpisode(
                media,
                selected.sourceIndex
            )
        }
    }

    fun loadSearchEpisodes(i: Int, result: ShowResponse) {
        lifecycleScope.launch(Dispatchers.IO) { model.overrideEpisodes(i, result, media.id) }
    }

    fun loadEpisodes(i: Int, invalidate: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) { model.loadEpisodes(media, i, invalidate) }
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

    fun openSettings(pkg: AnimeExtension.Installed) {
        val changeUIVisibility: (Boolean) -> Unit = { show ->
            if (isAdded && requireActivity() is MediaDetailsActivity) {
                val activity = requireActivity() as MediaDetailsActivity
                activity.binding.mediaAppBar.isVisible = show
                activity.binding.mediaViewPager.isVisible = show
                activity.binding.mediaCover.isVisible = show
                activity.binding.mediaClose.isVisible = show
                activity.navBar.isVisible = show
                activity.binding.fragmentExtensionsContainer.isGone = show
            }
        }
        var itemSelected = false
        val allSettings = pkg.sources.filterIsInstance<ConfigurableAnimeSource>()
        if (allSettings.isNotEmpty()) {
            var selectedSetting = allSettings[0]
            if (allSettings.size > 1) {
                val names = allSettings.map {
                    LanguageMapper.getExtensionItem(it)
                }.toTypedArray()
                val dialog = AlertDialog.Builder(requireContext(), R.style.MyDialog)
                    .setTitle(R.string.select_source)
                    .setSingleChoiceItems(names, -1) { dialog, which ->
                        selectedSetting = allSettings[which]
                        itemSelected = true
                        dialog.dismiss()

                        // Move the fragment transaction here
                        requireActivity().runOnUiThread {
                            val fragment =
                                AnimeSourcePreferencesFragment().getInstance(selectedSetting.id) {
                                    changeUIVisibility(true)
                                    loadEpisodes(media.selected!!.sourceIndex, true)
                                }
                            parentFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                                .replace(R.id.fragmentExtensionsContainer, fragment)
                                .addToBackStack(null)
                                .commit()
                        }
                    }
                    .setOnDismissListener {
                        if (!itemSelected) {
                            changeUIVisibility(true)
                        }
                    }
                    .show()
                dialog.window?.setDimAmount(0.8f)
            } else {
                // If there's only one setting, proceed with the fragment transaction
                requireActivity().runOnUiThread {
                    val fragment =
                        AnimeSourcePreferencesFragment().getInstance(selectedSetting.id) {
                            changeUIVisibility(true)
                            loadEpisodes(media.selected!!.sourceIndex, true)
                        }
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                        .replace(R.id.fragmentExtensionsContainer, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            }

            changeUIVisibility(false)
        } else {
            toast(R.string.source_no_settings)
        }
    }

    fun onEpisodeClick(i: String) {
        model.continueMedia = false
        model.saveSelected(media.id, media.selected!!)
        if (isAdded) {
            model.onEpisodeClick(media, i, requireActivity().supportFragmentManager)
        }
    }

    fun onImportDownloadClick(episodeNumber: String) {
        model.onImportDownloadClick(requireActivity(), media, episodeNumber, MediaType.ANIME)
    }

    fun onAnimeEpisodeDownloadClick(i: String) {
        activity?.let {
            if (!hasDirAccess(it)) {
                (it as MediaDetailsActivity).accessAlertDialog(it.launcher) { pathUri ->
                    if (pathUri != null) {
                        PrefManager.setVal(PrefName.DownloadsDir, pathUri)
                        model.onEpisodeClick(
                            media,
                            i,
                            requireActivity().supportFragmentManager,
                            isDownload = true
                        )
                    } else {
                        toast(getString(R.string.download_permission_required))
                    }
                }
            } else {
                model.onEpisodeClick(
                    media,
                    i,
                    requireActivity().supportFragmentManager,
                    isDownload = true
                )
            }
        }
    }

    fun onAnimeEpisodeStopDownloadClick(i: String) {
        val cancelIntent = Intent().apply {
            action = AnimeDownloaderService.ACTION_CANCEL_DOWNLOAD
            putExtra(
                AnimeDownloaderService.EXTRA_TASK_NAME,
                AnimeDownloaderService.AnimeDownloadTask.getTaskName(media.mainName(), i)
            )
        }
        requireContext().sendBroadcast(cancelIntent)

        // Remove the download from the manager and update the UI
        downloadManager.removeDownload(
            DownloadedType(media.mainName(), i, MediaType.ANIME)
        ) {
            episodeAdapter.purgeDownload(i)
        }
    }

    @OptIn(UnstableApi::class)
    fun onAnimeEpisodeRemoveDownloadClick(i: String) {
        downloadManager.removeDownload(
            DownloadedType(media.mainName(), i, MediaType.ANIME)
        ) {
            val taskName = AnimeDownloaderService.AnimeDownloadTask.getTaskName(media.mainName(), i)
            PrefManager.getAnimeDownloadPreferences().edit().remove(taskName).apply()
            episodeAdapter.deleteDownload(i)
        }
    }

    @kotlin.OptIn(DelicateCoroutinesApi::class)
    fun fixDownload(i: String) {
        toast(R.string.running_fixes)
        launchIO {
            try {
                val context = context ?: throw Exception("Context is null")
                val directory =
                    getSubDirectory(context, MediaType.ANIME, false, media.mainName(), i)
                        ?: throw Exception("Directory is null")
                val files = directory.listFiles()
                val videoFiles = files.filter { it.extension == "mp4" || it.extension == "mkv" }
                if (videoFiles.size != 1) {
                    val biggest =
                        videoFiles.filter { it.length() > 1000 }.maxByOrNull { it.length() }
                            ?: throw Exception("No video files found")
                    val newName =
                        AnimeDownloaderService.AnimeDownloadTask.getTaskName(media.mainName(), i)
                            .findValidName() + "." + biggest.extension
                    videoFiles.forEach {
                        if (it != biggest) it.delete()
                    }
                    if (newName != biggest.name) biggest.renameTo(newName)
                    toast(context.getString(R.string.success) + " (1)")
                } else {
                    val tempFile =
                        directory.createFile("video/x-matroska", "temp.mkv")
                            ?: throw Exception("Temp file is null")
                    val ffExtension = Injekt.get<DownloadAddonManager>().extension?.extension!!
                    val tempPath = ffExtension.setDownloadPath(context, tempFile.uri)
                    val videoPath = ffExtension.getReadPath(context, videoFiles[0].uri)

                    val id = ffExtension.customFFMpeg("1", listOf(videoPath, tempPath)) { log ->
                        Logger.log(log)
                    }
                    val timeOut = System.currentTimeMillis() + 1000 * 60 * 10
                    while (ffExtension.getState(id) != "COMPLETED") {
                        if (ffExtension.getState(id) == "FAILED") {
                            Logger.log("Failed to fix download")
                            ffExtension.getStackTrace(id)?.let {
                                Logger.log(it)
                            }
                            toast(R.string.failed_to_fix)
                            return@launchIO
                        }
                        if (System.currentTimeMillis() > timeOut) {
                            Logger.log("Failed to fix download: Timeout")
                            toast(R.string.failed_to_fix)
                            return@launchIO
                        }
                    }
                    if (ffExtension.hadError(id)) {
                        Logger.log("Failed to fix download: ${ffExtension.getStackTrace(id)}")
                        toast(R.string.failed_to_fix)
                        return@launchIO
                    }
                    val name = videoFiles[0].name
                    if (videoFiles[0].delete().not()) {
                        toast(R.string.delete_fail)
                        return@launchIO
                    }
                    tempFile.renameTo(name!!)
                    toast(context.getString(R.string.success) + " (2)")
                }
            } catch (e: Exception) {
                toast(getString(R.string.error_msg, e.message))
                Logger.log(e)
            }
        }
    }

    private val downloadStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!this@AnimeWatchFragment::episodeAdapter.isInitialized) return
            when (intent.action) {
                ACTION_DOWNLOAD_STARTED -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_EPISODE_NUMBER)
                    chapterNumber?.let { episodeAdapter.startDownload(it) }
                }

                ACTION_DOWNLOAD_FINISHED -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_EPISODE_NUMBER)
                    chapterNumber?.let { episodeAdapter.stopDownload(it) }
                }

                ACTION_DOWNLOAD_FAILED -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_EPISODE_NUMBER)
                    chapterNumber?.let { episodeAdapter.purgeDownload(it) }
                }

                ACTION_DOWNLOAD_PROGRESS -> {
                    val chapterNumber = intent.getStringExtra(EXTRA_EPISODE_NUMBER)
                    val progress = intent.getIntExtra("progress", 0)
                    chapterNumber?.let { episodeAdapter.updateDownloadProgress(it, progress) }
                }
            }
        }
    }

    private fun reload() {
        val selected = model.loadSelected(media)

        // Find latest episode for subscription
        selected.latest = media.anime?.episodes?.values?.maxOfOrNull {
            it.number.toFloatOrNull() ?: 0f
        } ?: 0f
        selected.latest = media.userProgress?.toFloat()?.takeIf {
            selected.latest < it
        } ?: selected.latest

        model.saveSelected(media.id, selected)
        FireSale().getSeason(media.id, MediaType.ANIME) {
            headerAdapter.handleEpisodes()

            episodeAdapter.updateType(style ?: PrefManager.getVal(PrefName.AnimeDefaultView))
            episodeAdapter.updateEpisodes(media.anime!!.episodes?.let { episodes ->
                val end = end?.takeIf { it < episodes.size } ?: (episodes.size - 1)
                episodes.values.toList().slice(start..end).let {
                    if (reverse) it.reversed() else it
                }
            } ?: listOf())
        }

        for (download in downloadManager.animeDownloadedTypes) {
            if (media.compareName(download.titleName)) {
                episodeAdapter.stopDownload(download.chapterName)
            }
        }
    }

    override fun onDestroy() {
        model.watchSources?.flushText()
        super.onDestroy()
        try {
            requireContext().unregisterReceiver(downloadStatusReceiver)
        } catch (_: IllegalArgumentException) { }
    }

    fun reloadSource() {
        lifecycleScope.launch(Dispatchers.IO) {
            onSourceChange(media.selected!!.sourceIndex)
            loadEpisodeList()
        }
    }

    fun loadEpisodeList(result: ShowResponse? = null) {
        result?.let { loadSearchEpisodes(media.selected!!.sourceIndex, it) }
            ?: loadEpisodes(media.selected!!.sourceIndex, false)
    }

    private fun getPreferredSources() {
        if (PrefManager.getVal(PrefName.SearchSources)
            && !PrefManager.getVal<Boolean>(PrefName.OfflineMode)
            && !model.watchSources?.list.isNullOrEmpty()) {
            if (sortedSources.isNotEmpty() && sortedSources[media] != null) {
                model.watchSources = sortedSources[media]
                model.watchSources?.let {
                    lifecycleScope.launch(Dispatchers.IO) {
                        withUIContext {
                            headerAdapter.updateSources(it)
                        }
                        onSourceChange(media.selected!!.sourceIndex)
                        loadEpisodeList(sortedResults[it.list[media.selected!!.sourceIndex].name])
                    }
                }
                return
            }
            sortedResults.clear()
            val sources : ArrayList<SourceSearch> = arrayListOf()
            val download = model.watchSources?.list?.last()

            model.watchSources?.list?.asyncMap { source ->
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
            model.watchSources = object: WatchSources() {
                override val list = sources.sortedBy { it.timeout }.map { it.parser }
            }
            sortedSources[media] = model.watchSources
            model.watchSources?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    withUIContext {
                        headerAdapter.updateSources(it)
                    }
                    onSourceChange(0)
                    loadEpisodeList(sortedResults[it.list[0].name])
                }
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                withUIContext {
                    headerAdapter.updateSources(model.watchSources!!)
                }
                loadEpisodeList()
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
        const val EXTRA_EPISODE_NUMBER = "extra_episode_number"

        private var sortedSources : MutableMap<Media, WatchSources?> = mutableMapOf()
        private var sortedResults : MutableMap<String, ShowResponse> = mutableMapOf()
    }
}
