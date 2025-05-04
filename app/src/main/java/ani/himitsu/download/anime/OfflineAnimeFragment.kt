package ani.himitsu.download.anime


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LayoutAnimationController
import android.widget.AbsListView
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import ani.himitsu.R
import ani.himitsu.bottomBar
import ani.himitsu.currContext
import ani.himitsu.databinding.FragmentOfflinePageBinding
import ani.himitsu.download.DownloadCompat.loadMediaCompat
import ani.himitsu.download.DownloadCompat.loadOfflineAnimeModelCompat
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.download.DownloadsManager.Companion.compareName
import ani.himitsu.download.findValidName
import ani.himitsu.initActivity
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.settings.SettingsDialogFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.util.Logger
import ani.himitsu.util.customAlertDialog
import bit.himitsu.nio.totalEpisodeText
import com.anggrayudi.storage.file.openInputStream
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.util.system.getThemeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable

class OfflineAnimeFragment : Fragment(), OfflineAnimeSearchListener {

    private val downloadManager = Injekt.get<DownloadsManager>()
    private var downloads: List<OfflineAnimeModel> = listOf()
    private lateinit var gridView: GridView
    private lateinit var adapter: OfflineAnimeAdapter
    private lateinit var total: TextView
    private var downloadsJob: Job = Job()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = FragmentOfflinePageBinding.inflate(inflater, container, false)

        view.offlineMangaSearchBar.hint = "Anime"
        val currentColor = view.offlineMangaSearchBar.boxBackgroundColor
        val semiTransparentColor = (currentColor and 0x00FFFFFF) or 0xA8000000.toInt()
        view.offlineMangaSearchBar.boxBackgroundColor = semiTransparentColor
        view.offlineMangaAvatarContainer.setCardBackgroundColor(semiTransparentColor)
        val color = requireContext().getThemeColor(android.R.attr.windowBackground)

        view.offlineMangaUserAvatar.setSafeOnClickListener {
            val dialogFragment =
                SettingsDialogFragment.newInstance(SettingsDialogFragment.Companion.PageType.OfflineANIME)
            dialogFragment.show((it.context as AppCompatActivity).supportFragmentManager, "dialog")
        }
        if (!(PrefManager.getVal(PrefName.ImmersiveMode) as Boolean)) {
            view.root.fitsSystemWindows = true
        }

        view.offlineMangaSearchBar.boxBackgroundColor = (color and 0x00FFFFFF) or 0x28000000
        view.offlineMangaAvatarContainer.setCardBackgroundColor((color and 0x00FFFFFF) or 0x28000000)

        view.animeSearchBarText.addTextChangedListener { onSearchQuery(it.toString()) }
        var style: Int = PrefManager.getVal(PrefName.OfflineView)
        var selected = when (style) {
            0 -> view.downloadedList
            1 -> view.downloadedGrid
            else -> view.downloadedList
        }
        selected.alpha = 1f

        fun selected(it: ImageView) {
            selected.alpha = 0.33f
            selected = it
            selected.alpha = 1f
        }

        view.downloadedList.setOnClickListener {
            selected(it as ImageView)
            style = 0
            PrefManager.setVal(PrefName.OfflineView, style)
            gridView.visibility = View.GONE
            gridView = view.gridView
            adapter.notifyNewGrid()
            grid()
        }

        view.downloadedGrid.setOnClickListener {
            selected(it as ImageView)
            style = 1
            PrefManager.setVal(PrefName.OfflineView, style)
            gridView.visibility = View.GONE
            gridView = view.gridView1
            adapter.notifyNewGrid()
            grid()
        }

        gridView =
            if (style == 0) view.gridView else view.gridView1
        total = view.total
        grid()
        return view.root
    }

    @OptIn(UnstableApi::class)
    private fun grid() {
        gridView.visibility = View.VISIBLE
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 300 // animations  pog
        gridView.layoutAnimation = LayoutAnimationController(fadeIn)
        adapter = OfflineAnimeAdapter(requireContext(), downloads, this)
        getDownloads()
        gridView.adapter = adapter
        gridView.scheduleLayoutAnimation()
        total.text = if (gridView.count > 0) "Anime (${gridView.count})" else "Empty List"
        gridView.setOnItemClickListener { _, _, position, _ ->
            // Get the OfflineAnimeModel that was clicked
            val item = adapter.getItem(position) as OfflineAnimeModel
            val media =
                downloadManager.animeDownloadedTypes.firstOrNull { it.titleName.compareName(item.title) }
            media?.let {
                lifecycleScope.launch {
                    val mediaModel = getMedia(it)
                    if (mediaModel == null) {
                        snackString("Error loading media.json")
                        return@launch
                    }
                    requireActivity().startActivity(
                        Intent(requireContext(), MediaDetailsActivity::class.java)
                            .putExtra("media", mediaModel as Serializable)
                            .putExtra("download", true)
                    )
                }
            } ?: run {
                snackString("no media found")
            }
        }
        gridView.setOnItemLongClickListener { _, _, position, _ ->
            // Get the OfflineAnimeModel that was clicked
            val item = adapter.getItem(position) as OfflineAnimeModel
            val type: MediaType = MediaType.ANIME

            // Alert dialog to confirm deletion
            requireContext().customAlertDialog().apply {
                setTitle(getString(R.string.delete_item, item.title))
                setMessage(getString(R.string.delete_content, item.title))
                setPositiveButton(R.string.yes) {
                    downloadManager.removeMedia(item.title, type)
                    val mediaIds = PrefManager.getAnimeDownloadPreferences().all?.filter {
                        it.key.contains(item.title)
                    }?.values.orEmpty()
                    if (mediaIds.isEmpty()) {
                        snackString("No media found")  // if this happens, terrible things have happened
                    }
                    getDownloads()
                }
                setNegativeButton(R.string.no)
                show()
            }
            true
        }
    }

    override fun onSearchQuery(query: String) {
        adapter.onSearchQuery(query)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scrollTop = view.findViewById<CardView>(R.id.mangaPageScrollTop)
        scrollTop.setOnClickListener {
            gridView.smoothScrollToPositionFromTop(0, 0)
        }

        // Assuming 'scrollTop' is a view that you want to hide/show
        scrollTop.visibility = View.GONE

        gridView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
            }

            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                val first = view.getChildAt(0)
                val visibility = first != null && first.top < 0
                scrollTop.translationY = -(bottomBar.height + bottomBar.marginBottom).toFloat()
                scrollTop.isVisible = visibility
            }
        })
        initActivity(requireActivity())

    }

    override fun onResume() {
        super.onResume()
        getDownloads()
    }

    override fun onPause() {
        super.onPause()
        downloads = listOf()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloads = listOf()
    }

    override fun onStop() {
        super.onStop()
        downloads = listOf()
    }

    private fun getDownloads() {
        downloads = listOf()
        if (downloadsJob.isActive) {
            downloadsJob.cancel()
        }
        downloadsJob = Job()
        CoroutineScope(Dispatchers.IO + downloadsJob).launch {
            val animeTitles = downloadManager.animeDownloadedTypes.map {
                it.titleName.findValidName()
            }.distinct()
            val newAnimeDownloads = mutableListOf<OfflineAnimeModel>()
            for (title in animeTitles) {
                val tDownloads = downloadManager.animeDownloadedTypes.filter {
                    it.titleName.findValidName() == title
                }
                val download = tDownloads.firstOrNull() ?: continue
                val offlineAnimeModel = loadOfflineAnimeModel(download)
                if (offlineAnimeModel.title == "unknown") offlineAnimeModel.title = title
                newAnimeDownloads += offlineAnimeModel
            }
            downloads = newAnimeDownloads
            withUIContext {
                adapter.setItems(downloads)
                total.text = if (gridView.count > 0) "Anime (${gridView.count})" else "Empty List"
                adapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * Load media.json file from the directory and convert it to Media class
     * @param downloadedType DownloadedType object
     * @return Media object
     */
    private fun getMedia(downloadedType: DownloadedType): Media? {
        return try {
            val directory = DownloadsManager.getSubDirectory(
                context ?: currContext(), downloadedType.type,
                false, downloadedType.titleName
            )
            val gson = GsonBuilder()
                .registerTypeAdapter(SChapter::class.java, InstanceCreator<SChapter> {
                    SChapterImpl() // Provide an instance of SChapterImpl
                })
                .registerTypeAdapter(SAnime::class.java, InstanceCreator<SAnime> {
                    SAnimeImpl() // Provide an instance of SAnimeImpl
                })
                .registerTypeAdapter(SEpisode::class.java, InstanceCreator<SEpisode> {
                    SEpisodeImpl() // Provide an instance of SEpisodeImpl
                })
                .create()
            val media = directory?.findFile("media.json")
                ?: return loadMediaCompat(downloadedType)
            val mediaJson =
                media.openInputStream(context ?: currContext())?.use {
                    it.bufferedReader().use { it.readText() }
                } ?: return null
            gson.fromJson(mediaJson, Media::class.java)
        } catch (e: Exception) {
            Logger.log("Error loading media.json: ${e.message}")
            Logger.log(e)
            null
        }
    }

    /**
     * Load OfflineAnimeModel from the directory
     * @param downloadedType DownloadedType object
     * @return OfflineAnimeModel object
     */
    private fun loadOfflineAnimeModel(downloadedType: DownloadedType): OfflineAnimeModel {
        val type = downloadedType.type.text
        try {
            val directory = DownloadsManager.getSubDirectory(
                context ?: currContext(), downloadedType.type,
                false, downloadedType.titleName
            )
            val mediaModel = getMedia(downloadedType)!!
            val cover = directory?.findFile("cover.jpg")
            val coverUri: Uri? = if (cover?.exists() == true) {
                cover.uri
            } else null
            val banner = directory?.findFile("banner.jpg")
            val bannerUri: Uri? = if (banner?.exists() == true) {
                banner.uri
            } else null
            if (coverUri == null && bannerUri == null) throw Exception("No cover or banner found, probably compat")
            val title = mediaModel.mainName()
            val score = ((if (mediaModel.userScore == 0) (mediaModel.meanScore
                ?: 0) else mediaModel.userScore) / 10.0).toString()
            val isOngoing = mediaModel.status == getString(R.string.status_releasing)
            val isUserScored = mediaModel.userScore != 0
            val watchedEpisodes = (mediaModel.userProgress ?: "~").toString()
            val chapters = " Chapters"
            val totalEpisodesList = "${mediaModel.anime?.totalEpisodes?.takeIf {
                it >= (mediaModel.anime.nextAiringEpisode ?: 0)
            } ?: mediaModel.anime?.nextAiringEpisode ?: "??"}"

            return OfflineAnimeModel(
                title,
                score,
                mediaModel.anime?.totalEpisodeText.toString(),
                totalEpisodesList,
                watchedEpisodes,
                type,
                chapters,
                isOngoing,
                isUserScored,
                coverUri,
                bannerUri
            )
        } catch (e: Exception) {
            return try {
                loadOfflineAnimeModelCompat(downloadedType)
            } catch (e: Exception) {
                Logger.log("Error loading media.json: ${e.message}")
                Logger.log(e)
                OfflineAnimeModel(
                    downloadedType.titleName,
                    "0",
                    "??",
                    "??",
                    "??",
                    downloadedType.type.text,
                    downloadedType.chapterName,
                    isOngoing = false,
                    isUserScored = false,
                    null,
                    null
                )
            }
        }
    }
}

interface OfflineAnimeSearchListener {
    fun onSearchQuery(query: String)
}