package ani.himitsu.media.anime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.himitsu.R
import ani.himitsu.copyToClipboard
import ani.himitsu.currActivity
import ani.himitsu.currContext
import ani.himitsu.databinding.BottomSheetSelectorBinding
import ani.himitsu.databinding.ItemStreamBinding
import ani.himitsu.databinding.ItemUrlBinding
import ani.himitsu.download.DownloadManager
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.video.Helper
import ani.himitsu.isTorrentLink
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.MediaType
import ani.himitsu.media.SubtitleDownloader
import ani.himitsu.media.cereal.Media
import ani.himitsu.openInGooglePlay
import ani.himitsu.others.Download.download
import ani.himitsu.parsers.Subtitle
import ani.himitsu.parsers.Video
import ani.himitsu.parsers.VideoExtractor
import ani.himitsu.parsers.VideoType
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.settings.Page
import ani.himitsu.settings.SILENT_EXIT
import ani.himitsu.settings.START_PAGE
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.tryWith
import ani.himitsu.util.Logger
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import bit.himitsu.TorrManager
import bit.himitsu.hideSystemBars
import bit.himitsu.os.Version
import bit.himitsu.setStatusTransparent
import bit.himitsu.torrServerLoad
import bit.himitsu.torrServerStart
import bit.himitsu.webkit.setWebClickListeners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.reflect.InvocationTargetException
import java.net.SocketException
import java.text.DecimalFormat

class SelectorDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding by lazy { _binding!! }
    val model: MediaDetailsViewModel by activityViewModels()
    private var scope: CoroutineScope = lifecycleScope
    private var media: Media? = null
    private var episode: Episode? = null
    private var prevEpisode: String? = null
    private var makeDefault = false
    private var selected: String? = null
    private var launch: Boolean = true
    private var isDownloadMenu: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selected = it.getString("server")
            prevEpisode = it.getString("prev")
            isDownloadMenu = it.getBoolean("isDownload")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        dialog?.window?.setStatusTransparent()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var loaded = false
        model.getMedia().observe(viewLifecycleOwner) { m ->
            media = m
            if (media != null && !loaded) {
                loaded = true
                val ep = media?.anime?.episodes?.get(media?.anime?.selectedEpisode)
                episode = ep
                if (ep != null) {
                    if (isDownloadMenu == true) {
                        binding.selectorMakeDefault.visibility = View.GONE
                    }

                    if (selected != null && isDownloadMenu == false) {
                        binding.selectorListContainer.visibility = View.GONE
                        binding.selectorAutoListContainer.visibility = View.VISIBLE
                        binding.selectorAutoText.text = selected
                        binding.selectorCancel.setOnClickListener {
                            media!!.selected!!.server = null
                            model.saveSelected(media!!.id, media!!.selected!!)
                            tryWith {
                                dismiss()
                            }
                        }
                        fun fail() {
                            toast(getString(R.string.auto_select_server_error))
                            binding.selectorCancel.performClick()
                        }

                        fun load() {
                            val size =
                                if (model.watchSources!!.isDownloadedSource(media!!.selected!!.sourceIndex)) {
                                    ep.extractors?.firstOrNull()?.videos?.size
                                } else {
                                    ep.extractors?.find { it.server.name == selected }?.videos?.size
                                }

                            if (size != null && size >= media!!.selected!!.video) {
                                media!!.anime!!.episodes?.get(media!!.anime!!.selectedEpisode!!)?.selectedExtractor =
                                    selected
                                media!!.anime!!.episodes?.get(media!!.anime!!.selectedEpisode!!)?.selectedVideo =
                                    media!!.selected!!.video
                                startExoplayer(media!!)
                            } else fail()
                        }

                        if (ep.extractors.isNullOrEmpty()) {
                            model.getEpisode().observe(this) {
                                if (it != null) {
                                    episode = it
                                    load()
                                }
                            }
                            scope.launch {
                                if (withIOContext {
                                        !model.loadEpisodeSingleVideo(ep, media!!.selected!!)
                                    }) fail()
                            }
                        } else load()
                    } else {
                        binding.selectorRecyclerView.adapter = null
                        binding.selectorProgressBar.visibility = View.VISIBLE
                        makeDefault = PrefManager.getVal(PrefName.MakeDefault)
                        binding.selectorMakeDefault.isChecked = makeDefault
                        binding.selectorMakeDefault.setOnClickListener {
                            makeDefault = binding.selectorMakeDefault.isChecked
                            PrefManager.setVal(PrefName.MakeDefault, makeDefault)
                        }
                        binding.selectorRecyclerView.layoutManager =
                            LinearLayoutManager(
                                requireActivity(),
                                LinearLayoutManager.VERTICAL,
                                false
                            )
                        val adapter = ExtractorAdapter()
                        binding.selectorRecyclerView.adapter = adapter
                        if (!ep.allStreams) {
                            ep.extractorCallback = {
                                scope.launch {
                                    adapter.add(it)
                                    if (model.watchSources!!.isDownloadedSource(media?.selected!!.sourceIndex)) {
                                        adapter.performClick(0)
                                    }
                                }
                            }
                            model.getEpisode().observe(this) {
                                if (it != null) {
                                    media!!.anime?.episodes?.set(
                                        media!!.anime?.selectedEpisode!!,
                                        ep
                                    )
                                }
                            }
                            scope.launch(Dispatchers.IO) {
                                model.loadEpisodeVideos(ep, media!!.selected!!.sourceIndex)
                                withUIContext {
                                    binding.selectorProgressBar.visibility = View.GONE
                                    if (adapter.itemCount == 0) {
                                        toast(getString(R.string.stream_selection_empty))
                                        launch = false
                                        dismiss()
                                    }
                                }
                            }
                        } else {
                            media!!.anime?.episodes?.set(media!!.anime?.selectedEpisode!!, ep)
                            adapter.addAll(ep.extractors)
                            if (ep.extractors?.size == 0) {
                                toast(getString(R.string.stream_selection_empty))
                                launch = false
                                dismiss()
                            }
                            if (model.watchSources!!.isDownloadedSource(media?.selected!!.sourceIndex)) {
                                adapter.performClick(0)
                            }
                            binding.selectorProgressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun exportMagnetIntent(episode: Episode, video: Video): Intent {
        val amnis = "com.amnis"
        return Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(amnis, "$amnis.gui.player.PlayerActivity")
            data = Uri.parse(video.file.url)
            putExtra("title", "${media?.name} - ${episode.title}")
            putExtra("position", 0)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("secure_uri", true)
            val headersArray = arrayOf<String>()
            video.file.headers.forEach {
                headersArray.plus(arrayOf(it.key, it.value))
            }
            putExtra("headers", headersArray)
        }
    }

    private val torrHandler = Handler(Looper.getMainLooper())
    private var retryCount = 5

    private fun resetDialog(autoPlay: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.selectorTitle.text = getString(R.string.server_selector)
            binding.selectorRecyclerView.isVisible = true
            binding.selectorMakeDefault.isVisible = autoPlay
            binding.selectorProgressBar.isVisible = false
        }
    }

    @OptIn(UnstableApi::class)
    private fun launchVideo(video: Video, autoPlay: Boolean) {
        try {
            runBlocking(Dispatchers.IO) {
                try {
                    torrServerLoad(video)
                    if (ExoplayerView.torrent == null) throw SocketException()
                    torrHandler.postDelayed({
                        if (autoPlay) {
                            startExoplayer()
                        } else {
                            binding.selectorTitle.text = getString(R.string.server_selector)
                            binding.selectorRecyclerView.isVisible = true
                            binding.selectorProgressBar.isVisible = false
                        }
                    }, 1000)
                } catch (_: SocketException) {
                    retryCount -= 1
                    if (retryCount > 0) {
                        torrHandler.postDelayed({ launchVideo(video, autoPlay) }, 1000)
                    } else {
                        resetDialog(autoPlay)
                        toast(R.string.media_import_failed)
                    }
                    return@runBlocking
                } catch (_: InvocationTargetException) {
                    resetDialog(autoPlay)
                    toast(R.string.server_failed)
                    return@runBlocking
                }
            }
        } catch (_: InterruptedException) {
            resetDialog(autoPlay)
        }
    }

    private fun parseMagnetLink(
        video: Video, ep: Episode, videoUrl: String, autoPlay: Boolean
    ): Boolean {
        if (videoUrl.isTorrentLink) {
            if (Version.isMarshmallow) {
                if (!PrefManager.getVal<Boolean>(PrefName.TorrServerEnabled)
                    && !TorrManager.isServiceRunning()
                ) {
                    requireContext().customAlertDialog().apply {
                        setTitle(R.string.server_disabled)
                        setMessage(R.string.enable_server_temp)
                        setPositiveButton(R.string.yes) {
                            torrServerStart()
                            binding.selectorTitle.text = getString(R.string.server_enabled)
                            binding.selectorRecyclerView.isVisible = false
                            binding.selectorMakeDefault.isVisible = false
                            binding.selectorProgressBar.isVisible = true
                            retryCount = 5
                            launchVideo(video, autoPlay)
                        }
                        setNegativeButton(R.string.no) { dismiss() }
                        show()
                    }
                    return true
                } else {
                    binding.selectorTitle.text = getString(R.string.server_enabled)
                    binding.selectorRecyclerView.isVisible = false
                    binding.selectorMakeDefault.isVisible = false
                    binding.selectorProgressBar.isVisible = true
                    retryCount = 5
                    launchVideo(video, autoPlay)
                    return autoPlay
                }
            } else {
                try {
                    startActivity(exportMagnetIntent(ep, video))
                } catch (_: ActivityNotFoundException) {
                    openInGooglePlay("com.amnis")
                }
                dismiss()
                return true
            }
        }
        return false
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun startExoplayer(media: Media) {
        prevEpisode = null

        episode?.let { ep ->
            val video = ep.extractors?.find {
                it.server.name == ep.selectedExtractor
            }?.videos?.getOrNull(ep.selectedVideo)
            val isExportedOrAutomatic = video?.file?.url?.let { videoUrl ->
                parseMagnetLink(video, ep, videoUrl, true)
            } == true
            if (isExportedOrAutomatic) return
        }
        stopAddingToList()
        if (isAdded) {
            val intent = Intent(requireContext(), ExoplayerView::class.java)
            ExoplayerView.media = media
            ExoplayerView.initialized = true
            startActivity(intent)
        }
        dismiss()
    }

    @OptIn(UnstableApi::class)
    fun startExoplayer() {
        stopAddingToList()
        if (isAdded) {
            val intent = Intent(requireContext(), ExoplayerView::class.java)
            ExoplayerView.media = media!!
            ExoplayerView.initialized = true
            startActivity(intent)
        }
        dismiss()
    }

    private fun stopAddingToList() {
        episode?.extractorCallback = null
        episode?.also {
            it.extractors = it.extractors?.toMutableList()
        }
    }

    private inner class ExtractorAdapter :
        RecyclerView.Adapter<ExtractorAdapter.StreamViewHolder>() {
        val links = mutableListOf<VideoExtractor>()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder =
            StreamViewHolder(
                ItemStreamBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            val extractor = links[position]
            holder.binding.streamName.text = "" // extractor.server.name
            holder.binding.streamName.visibility = View.GONE

            holder.binding.streamRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            holder.binding.streamRecyclerView.adapter = VideoAdapter(extractor)

        }

        override fun getItemCount(): Int = links.size

        fun add(videoExtractor: VideoExtractor) {
            if (videoExtractor.videos.isNotEmpty()) {
                links.add(videoExtractor)
                notifyItemInserted(links.size - 1)
            }
        }

        fun addAll(extractors: List<VideoExtractor>?) {
            links.addAll(extractors ?: return)
            notifyItemRangeInserted(0, extractors.size)
        }

        fun performClick(position: Int) {
            try {
                val extractor = links[position]
                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedExtractor =
                    extractor.server.name
                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedVideo = 0
                startExoplayer(media!!)
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        private inner class StreamViewHolder(val binding: ItemStreamBinding) :
            RecyclerView.ViewHolder(binding.root)
    }

    private inner class VideoAdapter(private val extractor: VideoExtractor) :
        RecyclerView.Adapter<VideoAdapter.UrlViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UrlViewHolder {
            return UrlViewHolder(
                ItemUrlBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: UrlViewHolder, position: Int) {
            val binding = holder.binding
            val video = extractor.videos[position]

            binding.urlShare.isVisible = isDownloadMenu != true && episode != null
                    && !video.file.url.isTorrentLink
            binding.urlShare.setWebClickListeners(video.file.url)

            binding.importDownload.isVisible = isDownloadMenu == true
            binding.urlDownload.isVisible = isDownloadMenu == true && episode != null
            val subtitles = extractor.subtitles
            if (subtitles.isNotEmpty()) {
                binding.urlSub.visibility = View.VISIBLE
                binding.urlSub.setOnClickListener {
                    val subtitleNames = subtitles.map { it.language }
                    var subtitleToDownload: Subtitle? = null
                    requireContext().customAlertDialog().apply {
                        setTitle(R.string.download_subtitle)
                        setSingleChoiceItems(subtitleNames.toTypedArray(), -1, false) { which ->
                            subtitleToDownload = subtitles[which]
                        }
                        setPositiveButton(R.string.download) {
                            if (subtitleToDownload != null) {
                                scope.launch {
                                    SubtitleDownloader.downloadSubtitle(
                                        requireContext(),
                                        subtitleToDownload.file.url,
                                        DownloadedType(
                                            media!!.mainName(),
                                            media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.number,
                                            MediaType.ANIME
                                        )
                                    )
                                }
                            } else {
                                toast(R.string.no_subs_available)
                            }
                        }
                        setNegativeButton(R.string.cancel)
                        show()
                    }
                }
            } else {
                binding.urlSub.visibility = View.GONE
            }

            binding.importDownload.setOnClickListener {
                model.onImportDownloadClick(
                    requireActivity(), media!!, media!!.anime!!.selectedEpisode!!, MediaType.ANIME
                )
                dismiss()
            }

            if (DownloadManager.fromPref() == DownloadManager.Browser) {
                binding.urlDownload.isVisible = binding.urlDownload.isVisible
                        && !video.file.url.isTorrentLink
                binding.urlDownload.setWebClickListeners(video.file.url) { dismiss() }
            } else {
                binding.urlDownload.setSafeOnClickListener {
                    media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedExtractor =
                        extractor.server.name
                    media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedVideo =
                        position
                    if (DownloadManager.fromPref() != DownloadManager.AddOn) {
                        binding.urlDownload.isVisible = episode != null
                        val subsFiles: ArrayList<String> = arrayListOf()
                        val subsNames: ArrayList<String> = arrayListOf()
                        subtitles.forEach {
                            subsFiles.add(it.file.url)
                            subsNames.add(it.language)
                        }
                        download(
                            requireActivity(),
                            media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!,
                            media!!.userPreferredName,
                            subsFiles,
                            subsNames
                        )
                    } else {
                        val downloadAddonManager: DownloadAddonManager = Injekt.get()
                        if (!downloadAddonManager.isAvailable()) {
                            requireContext().customAlertDialog().apply {
                                setTitle(R.string.download_addon_not_installed)
                                setMessage(R.string.would_you_like_to_install)
                                setPositiveButton(R.string.yes) {
                                    currContext().startActivity(
                                        Intent(currContext(), SettingsActivity::class.java)
                                            .putExtra(START_PAGE, Page.ADDON.name)
                                            .putExtra(SILENT_EXIT, true)
                                    )
                                }
                                setNegativeButton(R.string.no) {
                                    return@setNegativeButton
                                }
                                show()
                            }
                            dismiss()
                            return@setSafeOnClickListener
                        }
                        val subtitleNames = subtitles.map { it.language }
                        var selectedSubtitles: MutableList<Pair<String, String>> = mutableListOf()
                        var selectedAudioTracks: MutableList<Pair<String, String>> = mutableListOf()
                        val activity = currActivity() ?: requireActivity()
                        val episode =
                            media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!
                        val selectedVideo = if (extractor.videos.size > episode.selectedVideo)
                            extractor.videos[episode.selectedVideo]
                        else
                            null
                        val isExportedOrAutomatic = selectedVideo?.file?.url?.let { videoUrl ->
                            parseMagnetLink(video, episode, videoUrl, false)
                        } == true
                        if (isExportedOrAutomatic) return@setSafeOnClickListener
                        fun go() {
                            if (selectedVideo != null) {
                                Helper.startAnimeDownloadService(
                                    activity,
                                    media!!.mainName(),
                                    episode.number,
                                    selectedVideo,
                                    selectedSubtitles,
                                    selectedAudioTracks,
                                    media,
                                    episode.thumb?.url ?: media!!.banner ?: media!!.cover
                                )
                                broadcastDownloadStarted(episode.number, activity)
                            } else {
                                toast(R.string.no_video_selected)
                            }
                        }

                        fun checkAudioTracks() {
                            val audioTracks = extractor.audioTracks.map { it.lang }
                            if (audioTracks.isNotEmpty()) {
                                val audioNamesArray = audioTracks.toTypedArray()
                                val checkedItems = BooleanArray(audioNamesArray.size) { false }
                                requireContext().customAlertDialog().apply {
                                    setTitle(R.string.download_audio_tracks)
                                    setMultiChoiceItems(
                                        audioNamesArray,
                                        checkedItems,
                                        { which, isChecked ->
                                            val audioPair = Pair(
                                                extractor.audioTracks[which].url,
                                                extractor.audioTracks[which].lang
                                            )
                                            if (isChecked) {
                                                selectedAudioTracks.add(audioPair)
                                            } else {
                                                selectedAudioTracks.remove(audioPair)
                                            }
                                        },
                                        null
                                    )
                                    setPositiveButton(R.string.download) {
                                        dialog?.dismiss()
                                        go()
                                    }
                                    setNegativeButton(R.string.skip) {
                                        selectedAudioTracks = mutableListOf()
                                        go()
                                    }
                                    setNeutralButton(R.string.cancel) {
                                        selectedAudioTracks = mutableListOf()
                                    }
                                    show()
                                }
                            } else {
                                go()
                            }
                        }
                        if (subtitles.isNotEmpty()) {
                            val subtitleNamesArray = subtitleNames.toTypedArray()
                            val checkedItems = BooleanArray(subtitleNamesArray.size) { false }
                            requireContext().customAlertDialog().apply {
                                setTitle(R.string.download_subtitle)
                                setMultiChoiceItems(
                                    subtitleNamesArray,
                                    checkedItems,
                                    { which, isChecked ->
                                        val subtitlePair = Pair(
                                            subtitles[which].file.url,
                                            subtitles[which].language
                                        )
                                        if (isChecked) {
                                            selectedSubtitles.add(subtitlePair)
                                        } else {
                                            selectedSubtitles.remove(subtitlePair)
                                        }
                                    },
                                    null
                                )
                                setPositiveButton(R.string.download) {
                                    dialog?.dismiss()
                                    checkAudioTracks()
                                }
                                setNegativeButton(R.string.skip) {
                                    selectedSubtitles = mutableListOf()
                                    checkAudioTracks()
                                }
                                setNeutralButton(R.string.cancel) {
                                    selectedSubtitles = mutableListOf()
                                }
                                show()
                            }
                        } else {
                            checkAudioTracks()
                        }
                    }
                    dismiss()
                }
            }
            if (video.format == VideoType.CONTAINER) {
                binding.urlSize.isVisible = video.size != null
                // if video size is null or 0, show "Unknown Size" else show the size in MB
                val sizeText = getString(
                    R.string.mb_size, "${if (video.extraNote != null) " : " else ""}${
                        if (video.size == 0.0) getString(R.string.size_unknown) else DecimalFormat("#.##").format(
                            video.size ?: 0
                        )
                    }"
                )
                binding.urlSize.text = sizeText
            }
            binding.urlNote.visibility = View.VISIBLE
            binding.urlNote.text = video.format.name
            binding.urlQuality.text = extractor.server.name
        }

        private fun broadcastDownloadStarted(episodeNumber: String, activity: Activity) {
            val intent = Intent(AnimeWatchFragment.ACTION_DOWNLOAD_STARTED).apply {
                putExtra(AnimeWatchFragment.EXTRA_EPISODE_NUMBER, episodeNumber)
            }
            activity.sendBroadcast(intent)
        }

        override fun getItemCount(): Int = extractor.videos.size

        private inner class UrlViewHolder(val binding: ItemUrlBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.setSafeOnClickListener {
                    if (isDownloadMenu == true) {
                        binding.urlDownload.performClick()
                        return@setSafeOnClickListener
                    }
                    tryWith(true) {
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedExtractor =
                            extractor.server.name
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedVideo =
                            bindingAdapterPosition
                        if (makeDefault) {
                            media!!.selected!!.server = extractor.server.name
                            media!!.selected!!.video = bindingAdapterPosition
                            model.saveSelected(media!!.id, media!!.selected!!)
                        }
                        startExoplayer(media!!)
                    }
                }
                itemView.setOnLongClickListener {
                    val video = extractor.videos[bindingAdapterPosition]
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(video.file.url), "video/*")
                    }
                    copyToClipboard(video.file.url, true)
                    dismiss()
                    startActivity(Intent.createChooser(intent, "Open Video in :"))
                    true
                }
            }
        }
    }

    companion object {
        fun newInstance(
            server: String? = null,
            prev: String? = null,
            isDownload: Boolean
        ): SelectorDialogFragment =
            SelectorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("server", server)
                    putString("prev", prev)
                    putBoolean("isDownload", isDownload)
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {}

    @OptIn(UnstableApi::class)
    override fun onDismiss(dialog: DialogInterface) {
        if (launch == false) {
            activity?.hideSystemBars()
            model.epChanged.postValue(true)
            if (prevEpisode != null) {
                startActivity(Intent(requireContext(), ExoplayerView::class.java).apply {
                    action = "ani.himitsu.media.anime.EPISODE"
                    putExtra("episodeNumber", prevEpisode)
                })
                prevEpisode = null
            }
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
