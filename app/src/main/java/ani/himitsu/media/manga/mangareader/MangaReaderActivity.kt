package ani.himitsu.media.manga.mangareader

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_PAGE_DOWN
import android.view.KeyEvent.KEYCODE_PAGE_UP
import android.view.KeyEvent.KEYCODE_VOLUME_DOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.CheckBox
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.math.MathUtils.clamp
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.discord.DiscordService
import ani.himitsu.connections.discord.RPC
import ani.himitsu.connections.updateProgress
import ani.himitsu.currContext
import ani.himitsu.databinding.ActivityMangaReaderBinding
import ani.himitsu.isOffline
import ani.himitsu.logError
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.MediaNameAdapter
import ani.himitsu.media.MediaType
import ani.himitsu.media.anime.ExoplayerView
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.cereal.MediaSingleton
import ani.himitsu.media.currentChapter
import ani.himitsu.media.currentSettings
import ani.himitsu.media.manga.MangaCache
import ani.himitsu.media.manga.MangaChapter
import ani.himitsu.media.maxValue
import ani.himitsu.media.progressDialog
import ani.himitsu.media.saveProgress
import ani.himitsu.parsers.HMangaSources
import ani.himitsu.parsers.MangaImage
import ani.himitsu.parsers.MangaSources
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.settings.CurrentReaderSettings
import ani.himitsu.settings.CurrentReaderSettings.Directions
import ani.himitsu.settings.CurrentReaderSettings.DualPageModes
import ani.himitsu.settings.CurrentReaderSettings.Layouts
import ani.himitsu.settings.bottomTopPaged
import ani.himitsu.settings.directionHorz
import ani.himitsu.settings.directionRLBT
import ani.himitsu.settings.directionVert
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import ani.himitsu.tryWith
import ani.himitsu.util.Logger
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.GestureSlider
import ani.himitsu.view.NoPaddingArrayAdapter
import ani.himitsu.view.dialog.ImageViewDialog
import bit.himitsu.content.metrics
import bit.himitsu.content.stopRunningService
import bit.himitsu.content.toDp
import bit.himitsu.content.toPx
import bit.himitsu.firebase.FireSale
import bit.himitsu.hideSystemBarsExtendView
import bit.himitsu.nio.string
import bit.himitsu.os.Version
import bit.himitsu.showSystemBarsRetractView
import bit.himitsu.viewpager.BookFlipPageTransformer2
import com.alexvasilkov.gestures.views.GestureFrameLayout
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Timer
import java.util.TimerTask
import kotlin.math.min
import kotlin.properties.Delegates

class MangaReaderActivity : AppCompatActivity() {
    private val mangaCache = Injekt.get<MangaCache>()

    private lateinit var binding: ActivityMangaReaderBinding
    private val model: MediaDetailsViewModel by viewModels()
    private val scope = lifecycleScope

    var defaultSettings = CurrentReaderSettings()

    private lateinit var media: Media
    private lateinit var chapter: MangaChapter
    private lateinit var chapters: MutableMap<String, MangaChapter>
    private lateinit var chaptersArr: List<String>
    private lateinit var chaptersTitleArr: ArrayList<String>
    private var currentChapterIndex = 0

    private var isContVisible = false

    private var maxChapterPage = 0L
    private var currentChapterPage = 0L

    private var notchHeight: Int? = null

    private var imageAdapter: BaseImageAdapter? = null

    var sliding = false
    var isAnimating = false

    private val chapterNumber: Float
        get() = MediaNameAdapter.findChapterNumber(media.manga!!.selectedChapter!!)
            ?: (currentChapterIndex + 1).toFloat()

    override fun onAttachedToWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, windowInsets ->
            windowInsets.also {
                it.displayCutout?.let { notch ->
                    if (notch.boundingRects.isNotEmpty()) {
                        notchHeight = min(
                            notch.boundingRects[0].width(),
                            notch.boundingRects[0].height()
                        )
                    }
                }
                setSystemBarVisibility()
            }
        }
        super.onAttachedToWindow()
    }

    private fun setSystemBarVisibility() {
        if (PrefManager.getVal(PrefName.ShowSystemBars))
            showSystemBarsRetractView()
        else {
            hideSystemBarsExtendView()
            binding.mangaReaderTopLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin =
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        notchHeight ?: return
                    else 0
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        applySettings()
        super.onConfigurationChanged(newConfig)
    }

    private fun startDiscordService() {
        if (DiscordService.isRunning()) return
        val context = this
        if (!defaultSettings.discordRPC || PrefManager.getVal(PrefName.Incognito)) return
        lifecycleScope.launch {
            val buttons = RPC.getMangaButtons(media)
            val presence = RPC.createPresence(
                RPC.Companion.RPCData(
                    type = RPC.Type.WATCHING,
                    activityName = media.userPreferredName,
                    details = chapter.title?.takeIf { it.isNotEmpty() }
                        ?: getString(R.string.chapter_num, chapter.number),
                    state = "${chapter.number}/${media.manga?.totalChapters ?: "??"}",
                    largeImage = media.cover?.let { cover ->
                        RPC.Link(media.userPreferredName, cover)
                    },
                    buttons = buttons
                )
            )
            RPC.isEnabled = true
            startService(Intent(context, DiscordService::class.java).apply {
                putExtra(RPC.INTENT_EXTRA, presence)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityMangaReaderBinding.inflate(layoutInflater).apply {
            mangaReaderSlider.run {
                valueFrom = 1f
                valueTo = 100f
                stepSize = 1f
                value = 1f
            }
        }
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) {
            if (!this@MangaReaderActivity::media.isInitialized) {
                finish()
                return@addCallback
            }
            updateAniProgress { finish() }
        }

        binding.mangaReaderBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        controllerDuration = (PrefManager.getVal<Float>(PrefName.AnimationSpeed) * 200).toLong()

        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MangaReaderActivity)
                    .windowLayoutInfo(this@MangaReaderActivity)
                    .collect { updateCurrentLayout(it) }
            }
        }

        media = if (model.getMedia().value == null)
            try {
                //(intent.getSerialized("media")) ?: return
                MediaSingleton.media ?: return
            } catch (e: Exception) {
                logError(e)
                return
            } finally {
                MediaSingleton.media = null
            }
        else model.getMedia().value ?: return
        model.setMedia(media)

        defaultSettings = loadReaderSettings(media.currentSettings) ?: defaultSettings

        var pageSliderTimer = Timer()
        fun pageSliderHide() {
            pageSliderTimer.cancel()
            pageSliderTimer.purge()
            val timerTask: TimerTask = object : TimerTask() {
                override fun run() {
                    binding.mangaReaderCont.post {
                        sliding = false
                        handleController(false)
                    }
                }
            }
            pageSliderTimer = Timer()
            pageSliderTimer.schedule(timerTask, 3000)
        }

        binding.mangaReaderSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                sliding = true
                if (defaultSettings.layout != Layouts.PAGED)
                    binding.mangaReaderRecycler.scrollToPosition((value.toInt() - 1) / (dualPage { 2 }
                        ?: 1))
                else
                    if (defaultSettings.direction == Directions.BOTTOM_TO_TOP) {
                        binding.mangaReaderPager.currentItem =
                            (maxChapterPage.toInt() - value.toInt()) / (dualPage { 2 } ?: 1)
                    } else {
                        binding.mangaReaderPager.currentItem =
                            (value.toInt() - 1) / (dualPage { 2 } ?: 1)
                    }
                pageSliderHide()
            }
        }

        chapters = media.manga?.chapters ?: return
        chapter = chapters[media.manga!!.selectedChapter] ?: return

        model.mangaReadSources = if (media.isAdult) HMangaSources else MangaSources
        binding.mangaReaderSource.isVisible = PrefManager.getVal(PrefName.ShowSource)
        if (model.mangaReadSources!!.names.isEmpty()) {
            //try to reload sources
            try {
                val mangaSources = MangaSources
                val scope = lifecycleScope
                scope.launch(Dispatchers.IO) {
                    mangaSources.init(
                        Injekt.get<MangaExtensionManager>().installedExtensionsFlow
                    )
                }
                model.mangaReadSources = mangaSources
            } catch (e: Exception) {
                logError(e)
            }
        }
        //check that index is not out of bounds (crash fix)
        if (media.selected!!.sourceIndex >= model.mangaReadSources!!.names.size) {
            media.selected!!.sourceIndex = 0
        }
        binding.mangaReaderSource.text =
            model.mangaReadSources!!.names[media.selected!!.sourceIndex]

        binding.mangaReaderTitle.text = media.userPreferredName

        chaptersArr = chapters.keys.toList()
        currentChapterIndex = chaptersArr.indexOf(media.manga!!.selectedChapter)

        chaptersTitleArr = arrayListOf()
        chapters.forEach {
            val chapter = it.value
            chaptersTitleArr.add("${if (!chapter.title.isNullOrEmpty() && chapter.title != "null") "" else "Chapter "}${chapter.number}${if (!chapter.title.isNullOrEmpty() && chapter.title != "null") " : " + chapter.title else ""}")
        }

        scope.launch(Dispatchers.IO) {
            model.loadMangaChapterImages(
                chapter,
                media.selected!!
            )
        }

        // Chapter Change
        fun change(index: Int) {
            mangaCache.clear()
            if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
                FireSale().setProgress(
                    "${media.id}_${chaptersArr[currentChapterIndex]}", currentChapterPage, MediaType.MANGA
                )
            }
            PrefManager.setCustomVal(
                "${media.id}_${chaptersArr[currentChapterIndex]}", currentChapterPage
            )
            ChapterLoaderDialog.newInstance(chapters[chaptersArr[index]]!!)
                .show(supportFragmentManager, "dialog")
        }

        // ChapterSelector
        binding.mangaReaderChapterSelect.adapter =
            NoPaddingArrayAdapter(this, R.layout.item_dropdown, chaptersTitleArr)
        binding.mangaReaderChapterSelect.setSelection(currentChapterIndex)
        binding.mangaReaderChapterSelect.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {
                    if (position != currentChapterIndex) change(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        binding.mangaReaderSettings.setSafeOnClickListener {
            ReaderSettingsDialogFragment.newInstance(
                PrefManager.getVal(PrefName.AutoDetectWebtoon) && media.countryOfOrigin != "JP"
            ).show(supportFragmentManager, "settings")
        }

        // Next Chapter
        binding.mangaReaderNextChap.setOnClickListener {
            binding.mangaReaderNextChapter.performClick()
        }
        binding.mangaReaderNextChapter.setOnClickListener {
            if (defaultSettings.directionRLBT) {
                if (currentChapterIndex > 0)
                    change(currentChapterIndex - 1)
                else
                    snackString(getString(R.string.first_chapter))
            } else {
                if (chaptersArr.size > currentChapterIndex + 1)
                    updateAniProgress { change(currentChapterIndex + 1) }
                else
                    snackString(getString(R.string.next_chapter_not_found))
            }
        }
        // Prev Chapter
        binding.mangaReaderPrevChap.setOnClickListener {
            binding.mangaReaderPreviousChapter.performClick()
        }
        binding.mangaReaderPreviousChapter.setOnClickListener {
            if (defaultSettings.directionRLBT) {
                updateAniProgress {
                    if (chaptersArr.size > currentChapterIndex + 1)
                        change(currentChapterIndex + 1)
                    else
                        snackString(getString(R.string.next_chapter_not_found))
                }
            } else {
                if (currentChapterIndex > 0)
                    change(currentChapterIndex - 1)
                else
                    snackString(getString(R.string.first_chapter))
            }
        }

        model.getMangaChapter().observe(this) { chap ->
            if (chap != null) {
                chapter = chap
                if (PrefManager.getVal(PrefName.AutoDetectWebtoon) && media.countryOfOrigin != "JP") {
                    defaultSettings = defaultSettings.apply {
                        layout = Layouts.CONTINUOUS
                        direction = Directions.TOP_TO_BOTTOM
                        dualPageMode = DualPageModes.No
                        padding = false
                    }
                }
                FireSale().getProgress("${media.id}_${chapter.number}", MediaType.MANGA) {
                    media.manga!!.selectedChapter = chapter.number
                    media.selected = model.loadSelected(media)
                    if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
                        FireSale().setCurrent(media.currentChapter, chap.number, MediaType.MANGA)
                    }
                    PrefManager.setCustomVal(media.currentChapter, chap.number)
                    currentChapterIndex = chaptersArr.indexOf(chap.number)
                    binding.mangaReaderChapterSelect.setSelection(currentChapterIndex)
                    if (defaultSettings.directionRLBT) {
                        binding.mangaReaderNextChap.text =
                            chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: ""
                        binding.mangaReaderPrevChap.text =
                            chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: ""
                    } else {
                        binding.mangaReaderNextChap.text =
                            chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: ""
                        binding.mangaReaderPrevChap.text =
                            chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: ""
                    }
                    applySettings()
                }
            }
        }
    }

    private val snapHelper = PagerSnapHelper()

    fun <T> dualPage(callback: () -> T): T? {
        return when (defaultSettings.dualPageMode) {
            DualPageModes.No -> null
            DualPageModes.Automatic -> {
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (defaultSettings.pageTurn)
                        binding.mangaReaderPager.setPageTransformer(null)
                    callback.invoke()
                } else {
                    if (defaultSettings.pageTurn)
                        binding.mangaReaderPager.setPageTransformer(BookFlipPageTransformer2())
                    null
                }
            }
            DualPageModes.Force -> {
                callback.invoke()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun applySettings() {
        saveReaderSettings(media.currentSettings, defaultSettings)
        setSystemBarVisibility()

        if (defaultSettings.discordRPC)
            startDiscordService()
        else
            DiscordService::class.java.stopRunningService(this)

        // true colors
        SubsamplingScaleImageView.setPreferredBitmapConfig(
            when {
                Version.isOreo && defaultSettings.hardColors -> Bitmap.Config.HARDWARE
                defaultSettings.trueColors -> Bitmap.Config.ARGB_8888
                else -> Bitmap.Config.RGB_565
            }
        )

        //keep screen On
        if (defaultSettings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.mangaReaderPager.unregisterOnPageChangeCallback(pageChangeCallback)

        currentChapterPage = PrefManager.getCustomVal("${media.id}_${chapter.number}", 1L)

        val chapImages = chapter.images().let {
            if (defaultSettings.bottomTopPaged) it.reversed() else it
        }

        maxChapterPage = 0
        if (chapImages.isNotEmpty()) {
            maxChapterPage = chapImages.size.toLong()
            if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
                FireSale().setMax(media.maxValue(chapter.number), maxChapterPage, MediaType.MANGA)
            }
            PrefManager.setCustomVal(media.maxValue(chapter.number), maxChapterPage)

            imageAdapter =
                dualPage { DualPageAdapter(this, chapter) } ?: ImageAdapter(this, chapter)

            if (chapImages.size > 1) {
                binding.mangaReaderSlider.apply {
                    visibility = View.VISIBLE
                    valueTo = maxChapterPage.toFloat()
                    value = currentChapterPage.toFloat().coerceIn(valueFrom, valueTo)
                }
            } else {
                binding.mangaReaderSlider.visibility = View.GONE
            }
            binding.mangaReaderPageNumber.text =
                if (defaultSettings.hidePageNumbers) "" else "${currentChapterPage}/$maxChapterPage"

        }

        val currentPage = if (defaultSettings.bottomTopPaged) {
            maxChapterPage - currentChapterPage + 1
        } else {
            currentChapterPage
        }.toInt()

        if (defaultSettings.directionVert) {
            binding.mangaReaderSwipy.vertical = true
            if (defaultSettings.direction == Directions.TOP_TO_BOTTOM) {
                binding.mangaReaderNextChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: ""
                binding.mangaReaderPrevChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: ""
                binding.BottomSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1)
                    ?: getString(R.string.no_chapter)
                binding.TopSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1)
                    ?: getString(R.string.no_chapter)
                binding.mangaReaderSwipy.onTopSwiped = {
                    binding.mangaReaderPreviousChapter.performClick()
                }
                binding.mangaReaderSwipy.onBottomSwiped = {
                    binding.mangaReaderNextChapter.performClick()
                }
            } else {
                binding.mangaReaderNextChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: ""
                binding.mangaReaderPrevChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: ""
                binding.BottomSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1)
                    ?: getString(R.string.no_chapter)
                binding.TopSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1)
                    ?: getString(R.string.no_chapter)
                binding.mangaReaderSwipy.onTopSwiped = {
                    binding.mangaReaderPreviousChapter.performClick()
                }
                binding.mangaReaderSwipy.onBottomSwiped = {
                    binding.mangaReaderNextChapter.performClick()
                }
            }
            binding.mangaReaderSwipy.topBeingSwiped = { value ->
                binding.TopSwipeContainer.apply {
                    alpha = value
                    translationY = -height.toDp * (1 - min(value, 1f))
                }
            }
            binding.mangaReaderSwipy.bottomBeingSwiped = { value ->
                binding.BottomSwipeContainer.apply {
                    alpha = value
                    translationY = height.toDp * (1 - min(value, 1f))
                }
            }
        } else {
            binding.mangaReaderSwipy.vertical = false
            if (defaultSettings.direction == Directions.RIGHT_TO_LEFT) {
                binding.mangaReaderNextChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: ""
                binding.mangaReaderPrevChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: ""
                binding.LeftSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1)
                    ?: getString(R.string.no_chapter)
                binding.RightSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1)
                    ?: getString(R.string.no_chapter)
            } else {
                binding.mangaReaderNextChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: ""
                binding.mangaReaderPrevChap.text =
                    chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: ""
                binding.LeftSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1)
                    ?: getString(R.string.no_chapter)
                binding.RightSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1)
                    ?: getString(R.string.no_chapter)
            }
            binding.mangaReaderSwipy.onLeftSwiped = {
                binding.mangaReaderPreviousChapter.performClick()
            }
            binding.mangaReaderSwipy.leftBeingSwiped = { value ->
                binding.LeftSwipeContainer.apply {
                    alpha = value
                    translationX = -width.toDp * (1 - min(value, 1f))
                }
            }
            binding.mangaReaderSwipy.onRightSwiped = {
                binding.mangaReaderNextChapter.performClick()
            }
            binding.mangaReaderSwipy.rightBeingSwiped = { value ->
                binding.RightSwipeContainer.apply {
                    alpha = value
                    translationX = width.toDp * (1 - min(value, 1f))
                }
            }
        }

        if (defaultSettings.layout != Layouts.PAGED) {

            binding.mangaReaderRecyclerContainer.visibility = View.VISIBLE
            binding.mangaReaderRecyclerContainer.controller.settings.isRotationEnabled =
                defaultSettings.rotation

            val detector = GestureDetector(this, object : GestureSlider() {
                override fun onLongPress(e: MotionEvent) {
                    if (binding.mangaReaderRecycler.findChildViewUnder(e.x, e.y).let { child ->
                            child ?: return@let false
                            val pos = binding.mangaReaderRecycler.getChildAdapterPosition(child)
                            val callback: (ImageViewDialog) -> Unit = { dialog ->
                                lifecycleScope.launch {
                                    imageAdapter?.loadImage(
                                        pos,
                                        child as GestureFrameLayout
                                    )
                                }
                                binding.mangaReaderRecycler.performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS
                                )
                                dialog.dismiss()
                            }
                            dualPage {
                                val page =
                                    chapter.dualPages().getOrNull(pos) ?: return@dualPage false
                                val nextPage = page.second
                                if (defaultSettings.direction != Directions.LEFT_TO_RIGHT && nextPage != null)
                                    onImageLongClicked(pos * 2, nextPage, page.first, callback)
                                else
                                    onImageLongClicked(pos * 2, page.first, nextPage, callback)
                            } ?: onImageLongClicked(
                                pos,
                                chapImages.getOrNull(pos) ?: return@let false,
                                null,
                                callback
                            )
                        }
                    ) binding.mangaReaderRecycler.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    super.onLongPress(e)
                }

                override fun onSingleClick(event: MotionEvent) {
                    handleController()
                }
            })

            val manager = PreloadLinearLayoutManager(
                this,
                if (defaultSettings.directionVert)
                    RecyclerView.VERTICAL
                else
                    RecyclerView.HORIZONTAL,
                defaultSettings.directionRLBT
            ).apply {
                preloadItemCount = imageAdapter?.itemCount?.let { if (it > 4) 4 else it } ?: 2
                setStackFromEnd(defaultSettings.direction == Directions.BOTTOM_TO_TOP)
            }

            binding.mangaReaderPager.visibility = View.GONE

            binding.mangaReaderRecycler.apply {
                clearOnScrollListeners()
                binding.mangaReaderSwipy.child = this
                adapter = imageAdapter
                layoutManager = manager
                setOnTouchListener { _, event ->
                    if (event != null)
                        tryWith { detector.onTouchEvent(event) } == true
                    else false
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                            if ((defaultSettings.directionVert && (!v.canScrollVertically(-1) || !v.canScrollVertically(1)))
                                || (defaultSettings.directionHorz && (!v.canScrollHorizontally(-1) || !v.canScrollHorizontally(1)))) {
                                handleController(true)
                            } else handleController(false)
                        updatePageNumber(
                            manager.findLastVisibleItemPosition().toLong() * (dualPage { 2 }
                                ?: 1) + 1)
                        super.onScrolled(v, dx, dy)
                    }
                })
                if (defaultSettings.directionVert)
                    updatePadding(0, 128.toPx, 0, 128.toPx)
                else
                    updatePadding(128.toPx, 0, 128.toPx, 0)

                snapHelper.attachToRecyclerView(
                    if (defaultSettings.layout == Layouts.CONTINUOUS_PAGED)
                        this
                    else
                        null
                )

                onVolumeUp = {
                    if (defaultSettings.directionVert)
                        smoothScrollBy(0, -500)
                    else
                        smoothScrollBy(-500, 0)
                }

                onVolumeDown = {
                    if (defaultSettings.directionVert)
                        smoothScrollBy(0, 500)
                    else
                        smoothScrollBy(500, 0)
                }

                scrollToPosition(currentPage / (dualPage { 2 } ?: 1) - 1)
            }
        } else {
            binding.mangaReaderRecyclerContainer.visibility = View.GONE
            binding.mangaReaderPager.apply {
                binding.mangaReaderSwipy.child = this
                visibility = View.VISIBLE
                if (defaultSettings.pageTurn) setPageTransformer(BookFlipPageTransformer2())
                adapter = imageAdapter
                layoutDirection =
                    if (defaultSettings.directionRLBT) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                orientation =
                    if (defaultSettings.directionHorz)
                        ViewPager2.ORIENTATION_HORIZONTAL
                    else ViewPager2.ORIENTATION_VERTICAL
                registerOnPageChangeCallback(pageChangeCallback)
                offscreenPageLimit = 5

                setCurrentItem(currentPage / (dualPage { 2 } ?: 1) - 1, false)
            }
            onVolumeUp = {
                binding.mangaReaderPager.currentItem -= 1
            }
            onVolumeDown = {
                binding.mangaReaderPager.currentItem += 1
            }
        }
    }

    private var onVolumeUp: (() -> Unit)? = null
    private var onVolumeDown: (() -> Unit)? = null
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KEYCODE_VOLUME_UP, KEYCODE_DPAD_UP, KEYCODE_PAGE_UP -> {
                if (event.keyCode == KEYCODE_VOLUME_UP)
                    if (!defaultSettings.volumeButtons)
                        return false
                if (event.action == ACTION_DOWN) {
                    onVolumeUp?.invoke()
                    true
                } else false
            }

            KEYCODE_VOLUME_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_PAGE_DOWN -> {
                if (event.keyCode == KEYCODE_VOLUME_DOWN)
                    if (!defaultSettings.volumeButtons)
                        return false
                if (event.action == ACTION_DOWN) {
                    onVolumeDown?.invoke()
                    true
                } else false
            }

            else -> {
                super.dispatchKeyEvent(event)
            }
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updatePageNumber(position.toLong() * (dualPage { 2 } ?: 1) + 1)
            handleController(position == 0 || position + 1 >= maxChapterPage)
            super.onPageSelected(position)
        }
    }

    private val overshoot = OvershootInterpolator(1.4f)
    private var controllerDuration by Delegates.notNull<Long>()
    private var goneTimer = Timer()
    fun gone() {
        goneTimer.cancel()
        goneTimer.purge()
        val timerTask: TimerTask = object : TimerTask() {
            override fun run() {
                if (!isContVisible) binding.mangaReaderCont.post {
                    binding.mangaReaderCont.visibility = View.GONE
                    isAnimating = false
                }
            }
        }
        goneTimer = Timer()
        goneTimer.schedule(timerTask, controllerDuration)
    }

    enum class PressPos {
        LEFT, RIGHT, CENTER
    }

    fun handleController(shouldShow: Boolean? = null, event: MotionEvent? = null) {
        var pressLocation = PressPos.CENTER
        if (!sliding) {
            if (event != null && defaultSettings.layout == Layouts.PAGED) {
                if (event.action != MotionEvent.ACTION_UP) return
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                val screenWidth = metrics.widthPixels
                //if in the 1st 1/5th of the screen width, left and lower than 1/5th of the screen height, left
                if (screenWidth / 5 in x + 1..<y) {
                    pressLocation = if (defaultSettings.direction == Directions.RIGHT_TO_LEFT) {
                        PressPos.RIGHT
                    } else {
                        PressPos.LEFT
                    }
                }
                //if in the last 1/5th of the screen width, right and lower than 1/5th of the screen height, right
                else if (x > screenWidth - screenWidth / 5 && y > screenWidth / 5) {
                    pressLocation = if (defaultSettings.direction == Directions.RIGHT_TO_LEFT) {
                        PressPos.LEFT
                    } else {
                        PressPos.RIGHT
                    }
                }
            }

            // if pressLocation is left or right go to previous or next page (paged mode only)
            if (pressLocation == PressPos.LEFT) {

                if (binding.mangaReaderPager.currentItem > 0) {
                    //if  the current images zoomed in, go back to normal before going to previous page
                    if (imageAdapter?.isZoomed() == true) {
                        imageAdapter?.setZoom(1f)
                    }
                    binding.mangaReaderPager.currentItem -= 1
                    return
                }

            } else if (pressLocation == PressPos.RIGHT) {
                if (binding.mangaReaderPager.currentItem < maxChapterPage - 1) {
                    //if  the current images zoomed in, go back to normal before going to next page
                    if (imageAdapter?.isZoomed() == true) {
                        imageAdapter?.setZoom(1f)
                    }
                    //if right to left, go to previous page
                    binding.mangaReaderPager.currentItem += 1
                    return
                }
            }

            // Hide the scrollbar completely
            if (defaultSettings.hideScrollBar) {
                binding.mangaReaderSliderContainer.visibility = View.GONE
            } else {
                if (defaultSettings.horizontalScrollBar) {
                    binding.mangaReaderSliderContainer.updateLayoutParams {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                    }

                    binding.mangaReaderSlider.apply {
                        updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                        rotation = 0f
                    }

                } else {
                    binding.mangaReaderSliderContainer.updateLayoutParams {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                        width = 48.toPx
                    }

                    binding.mangaReaderSlider.apply {
                        updateLayoutParams {
                            width = binding.mangaReaderSliderContainer.height - 16.toPx
                        }
                        rotation = 90f
                    }
                }
                binding.mangaReaderSliderContainer.visibility = View.VISIBLE
            }
            //horizontal scrollbar
            if (defaultSettings.horizontalScrollBar) {
                binding.mangaReaderSliderContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                }

                binding.mangaReaderSlider.apply {
                    updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    rotation = 0f
                }

            } else {
                binding.mangaReaderSliderContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    width = 48.toPx
                }

                binding.mangaReaderSlider.apply {
                    updateLayoutParams {
                        width = binding.mangaReaderSliderContainer.height - 16.toPx
                    }
                    rotation = 90f
                }
            }
            binding.mangaReaderSlider.layoutDirection =
                if (defaultSettings.directionRLBT)
                    View.LAYOUT_DIRECTION_RTL
                else
                    View.LAYOUT_DIRECTION_LTR
            shouldShow?.apply { isContVisible = !this }
            if (isContVisible) {
                isContVisible = false
                if (!isAnimating) {
                    isAnimating = true
                    ObjectAnimator.ofFloat(binding.mangaReaderCont, "alpha", 1f, 0f)
                        .setDuration(controllerDuration).start()
                    ObjectAnimator.ofFloat(
                        binding.mangaReaderBottomLayout,
                        "translationY",
                        0f,
                        128f
                    )
                        .apply { interpolator = overshoot;duration = controllerDuration;start() }
                    ObjectAnimator.ofFloat(binding.mangaReaderTopLayout, "translationY", 0f, -128f)
                        .apply { interpolator = overshoot;duration = controllerDuration;start() }
                }
                gone()
            } else {
                isContVisible = true
                binding.mangaReaderCont.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(binding.mangaReaderCont, "alpha", 0f, 1f)
                    .setDuration(controllerDuration).start()
                ObjectAnimator.ofFloat(binding.mangaReaderTopLayout, "translationY", -128f, 0f)
                    .apply { interpolator = overshoot;duration = controllerDuration;start() }
                ObjectAnimator.ofFloat(binding.mangaReaderBottomLayout, "translationY", 128f, 0f)
                    .apply { interpolator = overshoot;duration = controllerDuration;start() }
            }
        }
    }

    private var loading = false
    fun updatePageNumber(pageNumber: Long) {
        var page = pageNumber
        if (defaultSettings.bottomTopPaged) {
            page = maxChapterPage - pageNumber + 1
        }
        if (currentChapterPage != page) {
            currentChapterPage = page
            if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
                FireSale().setProgress("${media.id}_${chapter.number}", page, MediaType.MANGA)
            }
            PrefManager.setCustomVal("${media.id}_${chapter.number}", page)
            binding.mangaReaderPageNumber.text =
                if (defaultSettings.hidePageNumbers) "" else "${currentChapterPage}/$maxChapterPage"
            if (!sliding) binding.mangaReaderSlider.apply {
                value = clamp(currentChapterPage.toFloat(), 1f, valueTo)
            }
        }
        if (maxChapterPage - currentChapterPage <= 1 && !loading)
            scope.launch(Dispatchers.IO) {
                loading = true
                model.loadMangaChapterImages(
                    chapters[chaptersArr.getOrNull(currentChapterIndex + 1) ?: return@launch]!!,
                    media.selected!!,
                    false
                )
                loading = false
            }
    }

    private fun progress(runnable: Runnable) {
        val dialogView = layoutInflater.inflate(R.layout.item_custom_dialog, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.dialog_checkbox)
        checkbox.text = getString(R.string.dont_ask_again, media.userPreferredName)
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(media.progressDialog, !isChecked)
        }
        customAlertDialog().apply {
            setTitle(getString(R.string.update_media_progress, media.userPreferredName))
            setCustomView(dialogView)
            setOnCancelListener { setSystemBarVisibility() }
            setCancelable(false)
            setPositiveButton(getString(R.string.yes)) {
                PrefManager.setCustomVal(media.saveProgress, true)
                updateProgress(media, chapterNumber.toString())
                runnable.run()
            }
            setNegativeButton(getString(R.string.no)) {
                PrefManager.setCustomVal(media.saveProgress, false)
                toast(getString(R.string.reset_auto_update))
                runnable.run()
            }
            show()
        }
    }

    private fun updateAniProgress(runnable: Runnable) {
        val showProgressDialog = PrefManager.getVal<Boolean>(PrefName.AskIndividualReader)
                && PrefManager.getCustomVal(media.progressDialog, true)
        val autoSave = !showProgressDialog && PrefManager.getCustomVal(media.saveProgress, true)
        val current = MediaNameAdapter.findChapterNumber(media.manga!!.selectedChapter!!)
            ?: chapterNumber
        val chapter0 = currentChapterIndex == 0 && current.minus(1).toInt() == 0
                && PrefManager.getVal(PrefName.ChapterZeroPlayer)
        val chapterEnd = chapter.images().isNotEmpty()
                && chapter.images().size - currentChapterPage <= 1

        if (!PrefManager.getVal<Boolean>(PrefName.Incognito)
            && AniList.userid != null
            && if (media.isAdult) PrefManager.getVal(PrefName.UpdateForHReader) else true) {
            when {
                chapterEnd && autoSave -> {
                    updateProgress(media, current.string)
                    runnable.run()
                }
                chapter0 && autoSave -> {
                    updateProgress(media, "0")
                    runnable.run()
                }
                chapterEnd && showProgressDialog -> { progress(runnable) }
                else -> { runnable.run() }
            }
        } else {
            runnable.run()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> loadReaderSettings(
        fileName: String,
        context: Context? = null,
        toast: Boolean = true
    ): T? {
        val a = context ?: currContext()
        try {
            if (a.fileList() != null)
                if (fileName in a.fileList()) {
                    val fileIS: FileInputStream = a.openFileInput(fileName)
                    val objIS = ObjectInputStream(fileIS)
                    val data = objIS.readObject() as T
                    objIS.close()
                    fileIS.close()
                    return data
                }
        } catch (_: Exception) {
            if (toast) snackString(a.getString(R.string.error_loading_data, fileName))
            //try to delete the file
            try {
                a.deleteFile(fileName)
            } catch (e: Exception) {
                Logger.log("Failed to delete file $fileName")
                Logger.log(e)
            }
        }
        return null
    }

    private fun saveReaderSettings(fileName: String, data: Any?, context: Context? = null) {
        tryWith {
            val a = context ?: currContext()
            val fos: FileOutputStream = a.openFileOutput(fileName, MODE_PRIVATE)
            val os = ObjectOutputStream(fos)
            os.writeObject(data)
            os.close()
            fos.close()
        }
    }

    fun getTransformation(mangaImage: MangaImage): BitmapTransformation? {
        return model.loadTransformation(mangaImage, media.selected!!.sourceIndex)
    }

    /**
     * Updating the layout depending on type and state of device
     */
    private fun updateCurrentLayout(newLayoutInfo: WindowLayoutInfo) {
        if (!PrefManager.getVal<Boolean>(PrefName.UseFoldable)) return
        val isFolding = (newLayoutInfo.displayFeatures.find {
            it is FoldingFeature
        } as? FoldingFeature)?.let {
            if (it.isSeparating) {
                if (it.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                    binding.mangaReaderPager.layoutParams.height = it.bounds.top - 24.toPx // Crease
                    binding.mangaReaderRecyclerContainer.layoutParams.height =
                        it.bounds.top - 24.toPx // Crease
                    binding.mangaReaderCont.layoutParams.height =
                        it.bounds.bottom - 24.toPx // Crease
                    binding.edgeSwipeFramework.layoutParams.height =
                        it.bounds.bottom - 24.toPx // Crease
                }
            }
            it.isSeparating
        } == true
        if (!isFolding) {
            binding.mangaReaderPager.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            binding.mangaReaderRecyclerContainer.layoutParams.height =
                ViewGroup.LayoutParams.MATCH_PARENT
            binding.mangaReaderCont.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            binding.edgeSwipeFramework.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        }
        binding.mangaReaderPager.requestLayout()
        binding.mangaReaderRecyclerContainer.requestLayout()
        binding.mangaReaderCont.requestLayout()
    }

    fun onImageLongClicked(
        pos: Int,
        img1: MangaImage,
        img2: MangaImage?,
        callback: ((ImageViewDialog) -> Unit)? = null
    ): Boolean {
//        if (!defaultSettings.longClickImage) return false
        if (!PrefManager.getVal<Boolean>(PrefName.LongClickImage)) return false
        var chapter = chaptersTitleArr.getOrNull(currentChapterIndex)?.replace(" : ", " - ") ?: ""
        if (chapter.isNotBlank() && chapter.substringAfterLast(" - ") == chapter.substringBeforeLast(
                " - "
            )
        )
            chapter = chapter.substringAfterLast(" - ")
        val title =
            "Page ${pos + 1}${img2?.let { "-${pos + 2}" } ?: ""} [${media.userPreferredName}] (${chapter})"

        ImageViewDialog.newInstance(title, img1.url, true, img2?.url).apply {
            val transforms1 = mutableListOf<BitmapTransformation>()
            val parserTransformation1 = getTransformation(img1)
            if (parserTransformation1 != null) transforms1.add(parserTransformation1)
            val transforms2 = mutableListOf<BitmapTransformation>()
            if (img2 != null) {
                val parserTransformation2 = getTransformation(img2)
                if (parserTransformation2 != null) transforms2.add(parserTransformation2)
            }
            val threshold = defaultSettings.cropBorderThreshold
            if (defaultSettings.cropBorders) {
                transforms1.add(RemoveBordersTransformation(true, threshold))
                transforms1.add(RemoveBordersTransformation(false, threshold))
                if (img2 != null) {
                    transforms2.add(RemoveBordersTransformation(true, threshold))
                    transforms2.add(RemoveBordersTransformation(false, threshold))
                }
            }
            trans1 = transforms1.ifEmpty { null }
            trans2 = transforms2.ifEmpty { null }
            onReloadPressed = callback
            show(supportFragmentManager, "image")
        }
        return true
    }

    private fun saveReadProgress() {
        if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
            FireSale().setCurrent(media.currentChapter, chapterNumber.toString(), MediaType.MANGA)
            FireSale().setProgress("${media.id}_${chapterNumber}", currentChapterPage, MediaType.MANGA)
        }
        PrefManager.setCustomVal(media.currentChapter, chapterNumber)
        PrefManager.setCustomVal("${media.id}_${chapterNumber}", currentChapterPage)
    }

    override fun onStart() {
        super.onStart()
        defaultSettings = CurrentReaderSettings().apply {
            discordRPC = discordRPC && !isOffline
        }
        startDiscordService()
    }

    override fun onPause() {
        super.onPause()
        saveReadProgress()
    }

    override fun onStop() {
        DiscordService::class.java.stopRunningService(this)
        super.onStop()
    }

    override fun onDestroy() {
        DiscordService::class.java.stopRunningService(this)
        saveReadProgress()
        mangaCache.clear()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        binding.mangaReaderFrame.isInvisible = PrefManager.getVal(PrefName.SecureLock) && !hasFocus
    }
}
