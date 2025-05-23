package ani.himitsu.media.novel.novelreader

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColor
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import ani.himitsu.R
import ani.himitsu.currContext
import ani.himitsu.databinding.ActivityMangaReaderBinding
import ani.himitsu.openInGooglePlay
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.settings.CurrentNovelReaderSettings
import ani.himitsu.settings.CurrentReaderSettings
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.themes.ThemeManager
import ani.himitsu.tryWith
import ani.himitsu.util.Logger
import ani.himitsu.view.GestureSlider
import ani.himitsu.view.NoPaddingArrayAdapter
import ani.himitsu.view.dialog.ImageViewDialog
import bit.himitsu.hideSystemBarsExtendView
import bit.himitsu.os.Version
import bit.himitsu.showSystemBarsRetractView
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.vipulog.ebookreader.Book
import com.vipulog.ebookreader.EbookReaderEventListener
import com.vipulog.ebookreader.ReaderError
import com.vipulog.ebookreader.ReaderFlow
import com.vipulog.ebookreader.ReaderTheme
import com.vipulog.ebookreader.RelocationInfo
import com.vipulog.ebookreader.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Timer
import java.util.TimerTask
import kotlin.math.min
import kotlin.properties.Delegates

class NovelReaderActivity : AppCompatActivity(), EbookReaderEventListener {
    private lateinit var binding: ActivityMangaReaderBinding
    private val scope = lifecycleScope

    private var notchHeight: Int? = null

    var loaded = false

    private lateinit var book: Book
    private lateinit var sanitizedBookId: String
    private lateinit var toc: List<TocItem>
    private var currentTheme: ReaderTheme? = null
    private var currentCfi: String? = null

    val themes = ArrayList<ReaderTheme>()

    var defaultSettings = CurrentNovelReaderSettings()

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


    @SuppressLint("WebViewApiAvailability")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager(this).applyTheme()

        super.onCreate(savedInstanceState)
        //check for supported webview
        val webViewVersion = if (Version.isOreo) {
            WebView.getCurrentWebViewPackage()?.versionName
        } else {
            WebViewCompat.getCurrentWebViewPackage(this)?.versionName
        }
        val firstVersion = webViewVersion?.split(".")?.firstOrNull()?.toIntOrNull()
        if (webViewVersion == null || firstVersion == null || firstVersion < 87) {
            val text = when {
                webViewVersion == null -> getString(R.string.webview_not_found)
                firstVersion == null -> getString(R.string.webview_not_found_version, webViewVersion)
                firstVersion < 87 -> getString(R.string.update_webview_version, firstVersion)
                else -> getString(R.string.update_webview)
            }
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()

            openInGooglePlay("com.google.android.webview")
            finish()
            return
        }

        resources.getStringArray(R.array.themes).forEachIndexed { index, name ->
            if (index == 0) return@forEachIndexed
            themes.add(ReaderTheme(
                name = name,
                lightFg = Color.parseColor("#000000"),
                lightBg = Color.parseColor(when (index) {
                    1 -> "#E7F6E7"
                    2 -> "#E4F0F9"
                    3 -> "#FDEDE6"
                    4 -> "#FDF5E6"
                    5 -> "#F2F2F2"
                    else -> null
                }),
                lightLink = Color.parseColor(when (index) {
                    1 -> "#008000"
                    2 -> "#007BFF"
                    3 -> "#FF5733"
                    4 -> "#FFA500"
                    5 -> "#800080"
                    else -> null
                }),
                darkFg = Color.parseColor(when (index) {
                    1 -> "#E7F6E7"
                    2 -> "#E4F0F9"
                    3 -> "#FDEDE6"
                    4 -> "#FDF5E6"
                    5 -> "#F2F2F2"
                    else -> null
                }),
                darkBg = Color.parseColor(when (index) {
                    1 -> "#084D08"
                    2 -> "#0A2E3E"
                    3 -> "#441517"
                    4 -> "#523B19"
                    5 -> "#000000"
                    else -> null
                }),
                darkLink = Color.parseColor(when (index) {
                    1 -> "#00B200"
                    2 -> "#00A5E4"
                    3 -> "#FF6B47"
                    4 -> "#FFBF00"
                    5 -> "#B300B3"
                    else -> null
                })
            ))
        }

        binding = ActivityMangaReaderBinding.inflate(layoutInflater).apply {
            bookReader.isVisible = true
            mangaReaderSwipy.isGone = true
            edgeSwipeFramework.isGone = true
            mangaReaderPageNumber.isGone = true
            progress.isVisible = true
            mangaReaderSlider.run {
                valueFrom = 0f
                valueTo = 1f
            }
        }
        setContentView(binding.root)

        controllerDuration = (PrefManager.getVal<Float>(PrefName.AnimationSpeed) * 200).toLong()

        setupViews()
        setupBackPressedHandler()
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        scope.launch { binding.bookReader.openBook(intent.data!!) }
        binding.bookReader.setEbookReaderListener(this)

        binding.mangaReaderBack.setOnClickListener { finish() }
        binding.mangaReaderSettings.setSafeOnClickListener {
            NovelReaderSettingsDialogFragment.newInstance()
                .show(supportFragmentManager, NovelReaderSettingsDialogFragment.TAG)
        }

        val gestureDetector = GestureDetector(this, object : GestureSlider() {
            override fun onSingleClick(event: MotionEvent) {
                handleController()
            }
        })

        binding.bookReader.setOnTouchListener { _, event ->
            if (event != null) tryWith { gestureDetector.onTouchEvent(event) } == true
            else false
        }

        binding.mangaReaderNextChap.setOnClickListener { binding.mangaReaderNextChapter.performClick() }
        binding.mangaReaderNextChapter.setOnClickListener { binding.bookReader.next() }
        binding.mangaReaderPrevChap.setOnClickListener { binding.mangaReaderPreviousChapter.performClick() }
        binding.mangaReaderPreviousChapter.setOnClickListener { binding.bookReader.prev() }

        binding.mangaReaderSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
            }

            override fun onStopTrackingTouch(slider: Slider) {
                binding.bookReader.gotoFraction(slider.value.toDouble())
            }
        })

        onVolumeUp = { binding.mangaReaderNextChapter.performClick() }

        onVolumeDown = { binding.mangaReaderPreviousChapter.performClick() }
    }

    private fun setupBackPressedHandler() {
        var doubleBackToExitPressedOnce = false
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bookReader.canGoBack()) {
                    binding.bookReader.goBack()
                } else {
                    if (doubleBackToExitPressedOnce) {
                        finish()
                    }
                    doubleBackToExitPressedOnce = true
                    snackString(this@NovelReaderActivity.getString(R.string.back_to_exit)).apply {
                        this?.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                super.onDismissed(transientBottomBar, event)
                                doubleBackToExitPressedOnce = false
                            }
                        })
                    }
                }
            }
        })
    }


    override fun onBookLoadFailed(error: ReaderError) {
        snackString(error.message)
        finish()
    }


    override fun onBookLoaded(book: Book) {
        this.book = book
        val bookId = book.identifier!!
        toc = book.toc

        val illegalCharsRegex = Regex("[^a-zA-Z0-9._-]")
        sanitizedBookId = bookId.replace(illegalCharsRegex, "_")

        binding.mangaReaderTitle.text = book.title
        binding.mangaReaderSource.text = book.author?.joinToString(", ")

        val tocLabels = book.toc.map { it.label ?: "" }
        binding.mangaReaderChapterSelect.adapter =
            NoPaddingArrayAdapter(this, R.layout.item_dropdown, tocLabels)
        binding.mangaReaderChapterSelect.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    binding.bookReader.goto(book.toc[position].href)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.bookReader.getAppearance {
            currentTheme = it
            themes.add(0, it)
            defaultSettings =
                loadReaderSettings("${sanitizedBookId}_current_settings") ?: defaultSettings
            applySettings()
        }

        val cfi = PrefManager.getNullableCustomVal(
            "${sanitizedBookId}_progress",
            null,
            String::class.java
        )

        cfi?.let { binding.bookReader.goto(it) }
        binding.progress.visibility = View.GONE
        loaded = true
    }


    override fun onProgressChanged(info: RelocationInfo) {
        currentCfi = info.cfi
        binding.mangaReaderSlider.value = info.fraction.toFloat()
        val pos = info.tocItem?.let { item -> toc.indexOfFirst { it == item } }
        if (pos != null) binding.mangaReaderChapterSelect.setSelection(pos)
        PrefManager.setCustomVal("${sanitizedBookId}_progress", info.cfi)
    }


    override fun onImageSelected(base64String: String) {
        scope.launch(Dispatchers.IO) {
            val base64Data = base64String.substringAfter(",")
            val imageBytes: ByteArray = Base64.decode(base64Data, Base64.DEFAULT)
            val imageFile = File(cacheDir, "/images/ln.jpg")

            imageFile.parentFile?.mkdirs()
            imageFile.createNewFile()

            FileOutputStream(imageFile).use { outputStream -> outputStream.write(imageBytes) }

            ImageViewDialog.newInstance(
                this@NovelReaderActivity,
                book.title,
                imageFile.toUri().toString()
            )
        }
    }

    override fun onTextSelectionModeChange(mode: Boolean) {
        // TODO: Show ui for adding annotations and notes
    }

    private var onVolumeUp: (() -> Unit)? = null
    private var onVolumeDown: (() -> Unit)? = null
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                    if (!defaultSettings.volumeButtons)
                        return false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    onVolumeUp?.invoke()
                    true
                } else false
            }

            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                    if (!defaultSettings.volumeButtons)
                        return false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    onVolumeDown?.invoke()
                    true
                } else false
            }

            else -> {
                super.dispatchKeyEvent(event)
            }
        }
    }

    fun applySettings() {
        saveReaderSettings("${sanitizedBookId}_current_settings", defaultSettings)
        setSystemBarVisibility()

        if (defaultSettings.useOledTheme) {
            themes.forEach { theme ->
                theme.darkBg = Color.parseColor("#000000")
            }
        }
        currentTheme =
            themes.first { it.name.equals(defaultSettings.currentThemeName, ignoreCase = true) }

        when (defaultSettings.layout) {
            CurrentNovelReaderSettings.Layouts.PAGED -> {
                currentTheme?.flow = ReaderFlow.PAGINATED
            }

            CurrentNovelReaderSettings.Layouts.SCROLLED -> {
                currentTheme?.flow = ReaderFlow.SCROLLED
            }
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        when (defaultSettings.dualPageMode) {
            CurrentReaderSettings.DualPageModes.No -> currentTheme?.maxColumnCount = 1
            CurrentReaderSettings.DualPageModes.Automatic -> currentTheme?.maxColumnCount = 2
            CurrentReaderSettings.DualPageModes.Force -> requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        currentTheme?.lineHeight = defaultSettings.lineHeight
        currentTheme?.gap = defaultSettings.margin
        currentTheme?.maxInlineSize = defaultSettings.maxInlineSize
        currentTheme?.maxBlockSize = defaultSettings.maxBlockSize
        currentTheme?.useDark = defaultSettings.useDarkTheme

        currentTheme?.let { binding.bookReader.setAppearance(it) }

        if (defaultSettings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }


    // region Handle Controls
    private var isContVisible = false
    private var isAnimating = false
    private var goneTimer = Timer()
    private var controllerDuration by Delegates.notNull<Long>()
    private val overshoot = OvershootInterpolator(1.4f)

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

    fun handleController(shouldShow: Boolean? = null) {
        if (!loaded) return

        setSystemBarVisibility()

        shouldShow?.apply { isContVisible = !this }
        if (isContVisible) {
            isContVisible = false
            if (!isAnimating) {
                isAnimating = true
                ObjectAnimator.ofFloat(binding.mangaReaderCont, "alpha", 1f, 0f)
                    .setDuration(controllerDuration).start()
                ObjectAnimator.ofFloat(binding.mangaReaderBottomCont, "translationY", 0f, 128f)
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
            ObjectAnimator.ofFloat(binding.mangaReaderBottomCont, "translationY", 128f, 0f)
                .apply { interpolator = overshoot;duration = controllerDuration;start() }
        }
    }
    // endregion Handle Controls

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

    override fun onException(exception: Throwable) {
        Logger.log(exception)
    }
}
