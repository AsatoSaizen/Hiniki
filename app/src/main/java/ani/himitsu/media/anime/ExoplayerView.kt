package ani.himitsu.media.anime

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.STREAM_MUSIC
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings.System
import android.util.AttributeSet
import android.util.Rational
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_B
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_N
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import androidx.core.math.MathUtils.clamp
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DEPRESSED
import androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
import androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
import androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
import androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import ani.himitsu.R
import ani.himitsu.brightnessConverter
import ani.himitsu.circularReveal
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.aniskip.AniSkip
import ani.himitsu.connections.aniskip.AniSkip.getType
import ani.himitsu.connections.discord.Discord
import ani.himitsu.connections.discord.DiscordService
import ani.himitsu.connections.discord.RPC
import ani.himitsu.connections.updateProgress
import ani.himitsu.currContext
import ani.himitsu.databinding.ActivityExoplayerBinding
import ani.himitsu.databinding.DialogPickerBinding
import ani.himitsu.defaultHeaders
import ani.himitsu.download.DownloadsManager.Companion.getSubDirectory
import ani.himitsu.download.video.Helper
import ani.himitsu.framecapture.AV_FrameCapture
import ani.himitsu.getCurrentBrightnessValue
import ani.himitsu.isOffline
import ani.himitsu.logError
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.MediaNameAdapter
import ani.himitsu.media.MediaType
import ani.himitsu.media.SubtitleDownloader
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.currentEpisode
import ani.himitsu.media.fullscreenInt
import ani.himitsu.media.maxValue
import ani.himitsu.media.progressDialog
import ani.himitsu.media.saveProgress
import ani.himitsu.media.speedValue
import ani.himitsu.media.subLanguage
import ani.himitsu.media.subtitles
import ani.himitsu.okHttpClient
import ani.himitsu.openInGooglePlay
import ani.himitsu.others.LanguageMapper
import ani.himitsu.others.ResettableTimer
import ani.himitsu.others.getSerialized
import ani.himitsu.parsers.AnimeSources
import ani.himitsu.parsers.HAnimeSources
import ani.himitsu.parsers.Subtitle
import ani.himitsu.parsers.SubtitleType
import ani.himitsu.parsers.Video
import ani.himitsu.parsers.VideoExtractor
import ani.himitsu.parsers.VideoType
import ani.himitsu.settings.PlayerSettingsActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import ani.himitsu.tryWithSuspend
import ani.himitsu.util.Logger
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.CustomCastButton
import ani.himitsu.view.GestureSlider
import ani.himitsu.view.NoPaddingArrayAdapter
import bit.himitsu.TorrManager.removeTorrent
import bit.himitsu.cloudflare.Subtitles.localizedFrom
import bit.himitsu.content.reboot
import bit.himitsu.content.stopRunningService
import bit.himitsu.content.toDp
import bit.himitsu.content.toPx
import bit.himitsu.firebase.FireSale
import bit.himitsu.hideSystemBars
import bit.himitsu.hideSystemBarsExtendView
import bit.himitsu.nio.roundTo
import bit.himitsu.nio.string
import bit.himitsu.os.Version
import bit.himitsu.cloudflare.Subtitles.getUserTypeface
import bit.himitsu.cloudflare.Subtitles.requestTypeface
import com.anggrayudi.storage.file.extension
import com.bumptech.glide.Glide
import com.github.rubensousa.previewseekbar.PreviewBar
import com.github.rubensousa.previewseekbar.PreviewBar.OnScrubListener
import com.github.rubensousa.previewseekbar.PreviewLoader
import com.github.rubensousa.previewseekbar.media3.PreviewTimeBar
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.common.collect.Iterables
import com.lagradost.nicehttp.ignoreAllSSLErrors
import eu.kanade.tachiyomi.data.torrentServer.model.Torrent
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@UnstableApi
@SuppressLint("ClickableViewAccessibility")
open class ExoplayerView : AppCompatActivity(), Player.Listener, SessionAvailabilityListener {

    private val resumeWindow = "resumeWindow"
    private val resumePosition = "resumePosition"
    private val playerFullscreen = "playerFullscreen"
    private val playerOnPlay = "playerOnPlay"
    private var disappeared: Boolean = false
    private var functionstarted: Boolean = false

    private lateinit var exoPlayer: ExoPlayer
    private var castPlayer: CastPlayer? = null
    private var castContext: CastContext? = null
    private var isCastApiAvailable = false
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var cacheFactory: CacheDataSource.Factory
    private lateinit var playbackParameters: PlaybackParameters
    private lateinit var mediaItem: MediaItem
    private var userSubtitles: MutableList<MediaItem.SubtitleConfiguration> = mutableListOf()
    private lateinit var mediaSource: MergingMediaSource
    private var mediaSession: MediaSession? = null

    private lateinit var binding: ActivityExoplayerBinding
    private lateinit var playerView: PlayerView
    private lateinit var exoPlay: ImageButton
    private lateinit var exoSource: ImageButton
    private lateinit var exoSettings: ImageButton
    private lateinit var exoSubtitle: ImageButton
    private lateinit var exoTranslate: ImageButton
    private lateinit var exoSubtitleView: SubtitleView
    private lateinit var exoNext: ImageButton
    private lateinit var exoPrev: ImageButton
    private lateinit var exoSkipOpEd: ImageButton
    private lateinit var exoBrightness: Slider
    private lateinit var exoVolume: Slider
    private lateinit var exoBrightnessCont: View
    private lateinit var exoVolumeCont: View
    private lateinit var exoSkip: View
    private lateinit var timeline: ExtendedTimeBar
    private lateinit var timeStampText: TextView
    private lateinit var animeTitle: TextView
    private lateinit var videoInfo: TextView
    private lateinit var episodeTitle: Spinner

    private var orientationListener: OrientationEventListener? = null

    private var downloadId: String? = null
    private var hasExtSubtitles = false

    companion object {
        var initialized = false
        lateinit var media: Media
        var discordRPC = Discord.getSavedToken() && !currContext().isOffline
        var torrent: Torrent? = null

        private val DEFAULT_MIN_BUFFER_MS =
            PrefManager.getVal<Float>(PrefName.MinBufferTime).toInt() * 1000
        private val DEFAULT_MAX_BUFFER_MS =
            PrefManager.getVal<Float>(PrefName.MaxBufferTime).toInt() * 1000
        private const val BUFFER_FOR_PLAYBACK_MS = 2500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000

        val playerSpeeds = arrayListOf<Float>().apply {
            for (i in 15..5000) {
                if (i % 5 == 0) add(i / 100f)
            }
        }.toTypedArray()
    }

    private lateinit var episode: Episode
    private lateinit var episodes: MutableMap<String, Episode>
    private lateinit var episodeTitleArr: ArrayList<String>
    private var currentEpisodeIndex = 0
    private var epChanging = false

    private var extractor: VideoExtractor? = null
    private var video: Video? = null
    private var subtitle: Subtitle? = null
    var audioLanguages = mutableListOf<String>()

    private var notchHeight: Int = 0
    private var currentWindow = 0
    private var playbackPosition: Long = 0L
    private var episodeLength: Float = 0f
    private var isFullscreen: Int = 0
    private var isInitialized = false
    private var isPlayerPlaying = true
    private var changingServer = false
    private var interacted = false

    private var pipEnabled = false
    private var aspectRatio = Rational(16, 9)

    private val handler = Handler(Looper.getMainLooper())
    val model: MediaDetailsViewModel by viewModels()
    var frameCapture: AV_FrameCapture? = null
    var retriever: MediaMetadataRetriever? = null

    private var isTimeStampsLoaded = false
    private var isSeeking = false
    private var isFastForwarding = false

    var rotation = 0

    private val isTorrent: Boolean get() = torrent != null
    private val subsEmbedded: Boolean get() = isTorrent || !hasExtSubtitles

    private val episodeNumber: String get() = media.anime?.selectedEpisode ?:
    if (this::episode.isInitialized) {
        episode.number
    } else {
         episodes.keys.elementAt(currentEpisodeIndex)
    }

    private var isUserLeaveHint = false

    private fun checkNotch() {
        if (notchHeight != 0) {
            val orientation = resources.configuration.orientation
            playerView.findViewById<View>(R.id.exo_controller_margin)
                .updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        marginStart = notchHeight
                        marginEnd = notchHeight
                        topMargin = 0
                    } else {
                        topMargin = notchHeight
                        marginStart = 0
                        marginEnd = 0
                    }
                }
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_buffering).translationY =
                (if (orientation == Configuration.ORIENTATION_LANDSCAPE) 0 else (notchHeight + 8.toPx)).toDp
            exoBrightnessCont.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd =
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) notchHeight else 0
            }
            exoVolumeCont.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart =
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) notchHeight else 0
            }
        }
    }

    override fun onAttachedToWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, windowInsets ->
            windowInsets.also {
                it.displayCutout?.let { notch ->
                    if (notch.boundingRects.isNotEmpty()) {
                        notchHeight = min(
                            notch.boundingRects[0].width(),
                            notch.boundingRects[0].height()
                        )
                        checkNotch()
                    }
                }
            }
        }
        super.onAttachedToWindow()
    }

    private fun setSubtitleStyle(playerView: PlayerView) {
        val useDefaults = PrefManager.getVal<Boolean>(PrefName.SubDefaults)

        playerView.subtitleView?.let { subtitles ->
            subtitles.setApplyEmbeddedStyles(useDefaults)
            subtitles.setApplyEmbeddedFontSizes(useDefaults)

            val primaryColor = when (PrefManager.getVal<Int>(PrefName.PrimaryColor)) {
                0 -> Color.BLACK
                1 -> Color.DKGRAY
                2 -> Color.GRAY
                3 -> Color.LTGRAY
                4 -> Color.WHITE
                5 -> Color.RED
                6 -> Color.YELLOW
                7 -> Color.GREEN
                8 -> Color.CYAN
                9 -> Color.BLUE
                10 -> Color.MAGENTA
                11 -> Color.TRANSPARENT
                else -> Color.WHITE
            }
            val secondaryColor = when (PrefManager.getVal<Int>(PrefName.SecondaryColor)) {
                0 -> Color.BLACK
                1 -> Color.DKGRAY
                2 -> Color.GRAY
                3 -> Color.LTGRAY
                4 -> Color.WHITE
                5 -> Color.RED
                6 -> Color.YELLOW
                7 -> Color.GREEN
                8 -> Color.CYAN
                9 -> Color.BLUE
                10 -> Color.MAGENTA
                11 -> Color.TRANSPARENT
                else -> Color.BLACK
            }
            val outline = when (PrefManager.getVal<Int>(PrefName.SubOutline)) {
                0 -> EDGE_TYPE_NONE
                1 -> EDGE_TYPE_OUTLINE
                2 -> EDGE_TYPE_DROP_SHADOW
                3 -> EDGE_TYPE_RAISED
                4 -> EDGE_TYPE_DEPRESSED // Shine
                else -> EDGE_TYPE_OUTLINE
            }
            val subBackground = when (PrefManager.getVal<Int>(PrefName.SubBackground)) {
                0 -> Color.TRANSPARENT
                1 -> Color.BLACK
                2 -> Color.DKGRAY
                3 -> Color.GRAY
                4 -> Color.LTGRAY
                5 -> Color.WHITE
                6 -> Color.RED
                7 -> Color.YELLOW
                8 -> Color.GREEN
                9 -> Color.CYAN
                10 -> Color.BLUE
                11 -> Color.MAGENTA
                else -> Color.TRANSPARENT
            }
            val subWindow = when (PrefManager.getVal<Int>(PrefName.SubWindow)) {
                0 -> Color.TRANSPARENT
                1 -> Color.BLACK
                2 -> Color.DKGRAY
                3 -> Color.GRAY
                4 -> Color.LTGRAY
                5 -> Color.WHITE
                6 -> Color.RED
                7 -> Color.YELLOW
                8 -> Color.GREEN
                9 -> Color.CYAN
                10 -> Color.BLUE
                11 -> Color.MAGENTA
                else -> Color.TRANSPARENT
            }
            val typeface = getUserTypeface(
                PrefManager.getVal<Int>(PrefName.Font),
                PrefManager.getVal<String>(PrefName.Typeface),
                ResourcesCompat.getFont(this, R.font.poppins_semi_bold)
            )

            subtitles.setStyle(
                CaptionStyleCompat(
                    primaryColor,
                    subBackground,
                    subWindow,
                    outline,
                    secondaryColor,
                    typeface
                )
            )

            subtitles.alpha = PrefManager.getVal(PrefName.SubAlpha)
            subtitles.setBottomPaddingFraction(
                PrefManager.getVal<Float>(PrefName.BottomPaddingRatio)
            )
            val fontSize = PrefManager.getVal<Int>(PrefName.FontSize).toFloat()
            subtitles.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)

            subtitles.setApplyEmbeddedFontSizes(useDefaults)
            subtitles.setApplyEmbeddedStyles(useDefaults)

            subtitles.setPadding(
                subtitles.paddingLeft,
                subtitles.paddingTop,
                subtitles.paddingRight,
                PrefManager.getVal<Float>(PrefName.SubBottomPadding).toPx
            )
        }
    }

    /**
     * Updating the layout depending on type and state of device
     */
    private fun updateCurrentLayout(newLayoutInfo: WindowLayoutInfo) {
        if (!PrefManager.getVal<Boolean>(PrefName.UseFoldable)) return
        val controller = playerView.findViewById<FrameLayout>(R.id.exo_controller)
        val isFolding = (newLayoutInfo.displayFeatures.find {
            it is FoldingFeature
        } as? FoldingFeature)?.let {
            if (it.isSeparating) {
                if (it.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                    playerView.layoutParams.height = it.bounds.top - 24.toPx // Crease
                    controller.layoutParams.height = it.bounds.bottom - 24.toPx // Crease
                }
            }
            it.isSeparating
        } == true
        if (!isFolding) {
            playerView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            controller.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.root.requestLayout()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityExoplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Initialize
        isCastApiAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        try {
            castContext =
                CastContext.getSharedInstance(this, Executors.newSingleThreadExecutor()).result
            castPlayer = CastPlayer(castContext!!)
            castPlayer!!.setSessionAvailabilityListener(this)
        } catch (_: Exception) {
            isCastApiAvailable = false
        }

        hideSystemBarsExtendView()

        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@ExoplayerView)
                    .windowLayoutInfo(this@ExoplayerView)
                    .collect { updateCurrentLayout(it) }
            }
        }

        onBackPressedDispatcher.addCallback(this) { playerTeardown() }

        playerView = findViewById(R.id.player_view)
        exoPlay = playerView.findViewById(androidx.media3.ui.R.id.exo_play)
        exoSource = playerView.findViewById(R.id.exo_source)
        exoSettings = playerView.findViewById(R.id.exo_settings)
        exoSubtitle = playerView.findViewById(R.id.exo_sub)
        exoTranslate = playerView.findViewById(R.id.exo_translate)
        exoSubtitleView = playerView.findViewById(androidx.media3.ui.R.id.exo_subtitles)
        exoBrightness = playerView.findViewById(R.id.exo_brightness)
        exoVolume = playerView.findViewById(R.id.exo_volume)
        exoBrightnessCont = playerView.findViewById(R.id.exo_brightness_cont)
        exoVolumeCont = playerView.findViewById(R.id.exo_volume_cont)
        exoSkipOpEd = playerView.findViewById(R.id.exo_skip_op_ed)
        exoSkip = playerView.findViewById(R.id.exo_skip)
        timeline = playerView.findViewById(androidx.media3.ui.R.id.exo_progress)
        timeStampText = playerView.findViewById(R.id.exo_time_stamp_text)

        animeTitle = playerView.findViewById(R.id.exo_anime_title)
        episodeTitle = playerView.findViewById(R.id.exo_ep_sel)

        playerView.controllerShowTimeoutMs = 5000

        val audioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager

        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus({ focus ->
            when (focus) {
                AUDIOFOCUS_LOSS_TRANSIENT, AUDIOFOCUS_LOSS -> if (isInitialized) exoPlayer.pause()
            }
        }, AUDIO_CONTENT_TYPE_MOVIE, AUDIOFOCUS_GAIN)

        if (System.getInt(contentResolver, System.ACCELEROMETER_ROTATION, 0) != 1) {
            val exoRotate = playerView.findViewById<ImageButton>(R.id.exo_rotate)
            if (PrefManager.getVal(PrefName.RotationPlayer)) {
                orientationListener =
                    object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
                        override fun onOrientationChanged(orientation: Int) {
                            when (orientation) {
                                in 45..135 -> {
                                    if (rotation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                                        exoRotate.visibility = View.VISIBLE
                                    }
                                    rotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                }

                                in 225..315 -> {
                                    if (rotation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                        exoRotate.visibility = View.VISIBLE
                                    }
                                    rotation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }

                                in 315..360, in 0..45 -> {
                                    if (rotation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                        exoRotate.visibility = View.VISIBLE
                                    }
                                    rotation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }
                        }
                    }
                orientationListener?.enable()
            }

            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            exoRotate.setOnClickListener {
                requestedOrientation = rotation
                it.visibility = View.GONE
            }
        }

        requestTypeface(
            PrefManager.getVal<Int>(PrefName.Font),
            PrefManager.getVal<String>(PrefName.Typeface)
        ) {
            setSubtitleStyle(playerView)
        }

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(resumeWindow)
            playbackPosition = savedInstanceState.getLong(resumePosition)
            isFullscreen = savedInstanceState.getInt(playerFullscreen)
            isPlayerPlaying = savedInstanceState.getBoolean(playerOnPlay)
        }

        // Back Button
        playerView.findViewById<ImageButton>(R.id.exo_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // TimeStamps
        model.timeStamps.observe(this) { timestamps ->
            if (!isTimeStampsLoaded && timestamps.isNullOrEmpty()) {
                timeStampText.text = getString(R.string.aniskip_failed)
            }
            isTimeStampsLoaded = true
            exoSkipOpEd.visibility = timestamps?.let {
                val adGroups = it.flatMap {
                    listOf(
                        it.interval.startTime.toLong() * 1000L,
                        it.interval.endTime.toLong() * 1000L
                    )
                }.toLongArray()
                val playedAdGroups = it.flatMap {
                    listOf(false, false)
                }.toBooleanArray()
                playerView.setExtraAdGroupMarkers(adGroups, playedAdGroups)
                View.VISIBLE
            } ?: View.GONE
        }

        exoSkipOpEd.alpha = if (PrefManager.getVal(PrefName.AutoSkipOPED)) 1f else 0.3f
        exoSkipOpEd.setOnClickListener {
            if (PrefManager.getVal(PrefName.AutoSkipOPED)) {
                toast(getString(R.string.disabled_auto_skip))
                PrefManager.setVal(PrefName.AutoSkipOPED, false)
            } else {
                toast(getString(R.string.auto_skip))
                PrefManager.setVal(PrefName.AutoSkipOPED, true)
            }
            exoSkipOpEd.alpha = if (PrefManager.getVal(PrefName.AutoSkipOPED)) 1f else 0.3f
        }

        // Play Pause
        exoPlay.setOnClickListener {
            if (isInitialized) {
                isPlayerPlaying = exoPlayer.isPlaying
                (exoPlay.drawable as Animatable?)?.start()
                if (isPlayerPlaying || castPlayer?.isPlaying == true) {
                    Glide.with(exoPlay).load(R.drawable.anim_play_to_pause).into(exoPlay)
                    exoPlayer.pause()
                    castPlayer?.pause()
                } else {
                    if (castPlayer?.isPlaying == false && castPlayer?.currentMediaItem != null) {
                        Glide.with(exoPlay).load(R.drawable.anim_pause_to_play).into(exoPlay)
                        castPlayer?.play()
                    } else if (!isPlayerPlaying) {
                        Glide.with(exoPlay).load(R.drawable.anim_pause_to_play).into(exoPlay)
                        exoPlayer.play()
                    }
                }
            }
        }

        // Picture-in-picture
        if (Version.isNougat) {
            val exoPip = playerView.findViewById<ImageButton>(R.id.exo_pip)
            pipEnabled = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    && PrefManager.getVal(PrefName.Pip)
            if (pipEnabled) {
                if (Version.isOreo) {
                    setPictureInPictureParams(getPictureInPictureBuilder().build())
                }
                exoPip.visibility = View.VISIBLE
                exoPip.setOnClickListener {
                    enterPipMode()
                }
            } else exoPip.visibility = View.GONE
        }

        // Lock Button
        var locked = false
        val container = playerView.findViewById<View>(R.id.exo_controller_cont)
        val screen = playerView.findViewById<View>(R.id.exo_black_screen)
        val lockButton = playerView.findViewById<ImageButton>(R.id.exo_unlock)

        timeline.addOnScrubListener(object : OnScrubListener {
            override fun onScrubStart(previewBar: PreviewBar?) {
                exoPlayer.playWhenReady = false
            }

            override fun onScrubMove(previewBar: PreviewBar?, progress: Int, fromUser: Boolean) {
                exoPlayer.playWhenReady = false
            }

            override fun onScrubStop(previewBar: PreviewBar?) { }

        })
        setPreviewLoader()

        lockButton.setOnClickListener {
            val button = it as ImageButton
            locked = !locked
            button.setImageResource(
                if (locked) R.drawable.round_lock_24 else R.drawable.round_lock_open_24
            )
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(Color.WHITE))
            screen.isVisible = !locked
            container.isVisible = !locked
            timeline.setForceDisabled(locked)
        }

        // Skip Time Button
        var skipTime = PrefManager.getVal<Int>(PrefName.SkipTime)
        if (skipTime > 0) {
            exoSkip.findViewById<TextView>(R.id.exo_skip_time).text = skipTime.string
            exoSkip.setOnClickListener {
                if (isInitialized)
                    exoPlayer.seekTo(exoPlayer.currentPosition + skipTime * 1000)
            }
            exoSkip.setOnLongClickListener {
                val dialog = Dialog(this, R.style.MyDialog)
                dialog.setContentView(R.layout.item_seekbar_dialog)
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (skipTime <= 120) {
                    dialog.findViewById<Slider>(R.id.seekbar).value = skipTime.toFloat()
                } else {
                    dialog.findViewById<Slider>(R.id.seekbar).value = 120f
                }
                dialog.findViewById<Slider>(R.id.seekbar).addOnChangeListener { _, value, _ ->
                    skipTime = value.toInt()
                    //saveData(player, settings)
                    PrefManager.setVal(PrefName.SkipTime, skipTime)
                    playerView.findViewById<TextView>(R.id.exo_skip_time).text = skipTime.string
                    dialog.findViewById<TextView>(R.id.seekbar_value).text = skipTime.string
                }
                dialog.findViewById<Slider>(R.id.seekbar)
                    .addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                        override fun onStartTrackingTouch(slider: Slider) {}
                        override fun onStopTrackingTouch(slider: Slider) {
                            dialog.dismiss()
                        }
                    })
                dialog.findViewById<TextView>(R.id.seekbar_title).text = getString(R.string.skip_time)
                dialog.findViewById<TextView>(R.id.seekbar_value).text = skipTime.string
                @Suppress("DEPRECATION")
                dialog.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                dialog.show()
                true
            }
        } else {
            exoSkip.visibility = View.GONE
        }

        val gestureSpeed = (300 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong()
        // Player UI Visibility Handler
        val brightnessRunnable = Runnable {
            if (exoBrightnessCont.alpha == 1f)
                lifecycleScope.launch {
                    ObjectAnimator.ofFloat(exoBrightnessCont, "alpha", 1f, 0f)
                        .setDuration(gestureSpeed).start()
                    delay(gestureSpeed)
                    exoBrightnessCont.visibility = View.GONE
                    checkNotch()
                }
        }
        val volumeRunnable = Runnable {
            if (exoVolumeCont.alpha == 1f)
                lifecycleScope.launch {
                    ObjectAnimator.ofFloat(exoVolumeCont, "alpha", 1f, 0f)
                        .setDuration(gestureSpeed).start()
                    delay(gestureSpeed)
                    exoVolumeCont.visibility = View.GONE
                    checkNotch()
                }
        }
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val skipTimeButton = playerView.findViewById<MaterialCardView>(R.id.exo_skip_timestamp)
            if (visibility == View.GONE) {
                hideSystemBars()
                brightnessRunnable.run()
                volumeRunnable.run()
                if (PrefManager.getVal(PrefName.ShowTimeStampButton) && timeStampHiddden) {
                    skipTimeButton.visibility = View.GONE
                    exoSkip.isVisible = PrefManager.getVal<Int>(PrefName.SkipTime) > 0
                }
            } else {
                if (PrefManager.getVal(PrefName.ShowTimeStampButton) && timeStampHiddden) {
                    skipTimeButton.visibility = View.VISIBLE
                    exoSkip.visibility = View.GONE
                }
            }
        })
        val overshoot = AnimationUtils.loadInterpolator(this, R.anim.over_shoot)
        val controllerDuration = (300 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong()
        fun handleController() {
            if (if (Version.isNougat) !isInPictureInPictureMode else true) {
                if (playerView.isControllerFullyVisible) {
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_controller),
                        "alpha",
                        1f,
                        0f
                    ).setDuration(controllerDuration).start()
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_bottom_cont),
                        "translationY",
                        0f,
                        128f
                    ).apply { interpolator = overshoot;duration = controllerDuration;start() }
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_timeline_cont),
                        "translationY",
                        0f,
                        128f
                    ).apply { interpolator = overshoot;duration = controllerDuration;start() }
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_top_cont),
                        "translationY",
                        0f,
                        -128f
                    ).apply { interpolator = overshoot;duration = controllerDuration;start() }
                    playerView.postDelayed({ playerView.hideController() }, controllerDuration)
                } else {
                    checkNotch()
                    playerView.showController()
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_controller),
                        "alpha",
                        0f,
                        1f
                    )
                        .setDuration(controllerDuration).start()
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_bottom_cont),
                        "translationY",
                        128f,
                        0f
                    ).apply { interpolator = overshoot;duration = controllerDuration;start() }
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_timeline_cont),
                        "translationY",
                        128f,
                        0f
                    ).apply { interpolator = overshoot;duration = controllerDuration;start() }
                    ObjectAnimator.ofFloat(
                        playerView.findViewById(R.id.exo_top_cont),
                        "translationY",
                        -128f,
                        0f
                    ).apply { interpolator = overshoot;duration = controllerDuration;start() }
                }
            }
        }

        playerView.findViewById<View>(R.id.exo_full_area).setOnClickListener {
            handleController()
        }

        val rewindText = playerView.findViewById<TextView>(R.id.exo_fast_rewind_anim)
        val forwardText = playerView.findViewById<TextView>(R.id.exo_fast_forward_anim)
        val fastForwardCard = playerView.findViewById<View>(R.id.exo_fast_forward)
        val fastRewindCard = playerView.findViewById<View>(R.id.exo_fast_rewind)

        // Seeking
        val seekTimerF = ResettableTimer()
        val seekTimerR = ResettableTimer()
        var seekTimesF = 0
        var seekTimesR = 0

        fun seek(forward: Boolean, event: MotionEvent? = null) {
            val seekTime = PrefManager.getVal<Int>(PrefName.SeekTime)
            val (card, text) = if (forward) {
                val text = "+${seekTime * ++seekTimesF}"
                forwardText.text = text
                handler.post { exoPlayer.seekTo(exoPlayer.currentPosition + seekTime * 1000) }
                fastForwardCard to forwardText
            } else {
                val text = "-${seekTime * ++seekTimesR}"
                rewindText.text = text
                handler.post { exoPlayer.seekTo(exoPlayer.currentPosition - seekTime * 1000) }
                fastRewindCard to rewindText
            }

            //region Double Tap Animation
            val showCardAnim = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).setDuration(300)
            val showTextAnim = ObjectAnimator.ofFloat(text, "alpha", 0f, 1f).setDuration(150)

            fun startAnim() {
                showTextAnim.start()

                (text.compoundDrawables[1] as Animatable).apply {
                    if (!isRunning) start()
                }

                if (!isSeeking && event != null) {
                    playerView.hideController()
                    card.circularReveal(event.x.toInt(), event.y.toInt(), !forward, 800)
                    showCardAnim.start()
                }
            }

            fun stopAnim() {
                handler.post {
                    showCardAnim.cancel()
                    showTextAnim.cancel()
                    ObjectAnimator.ofFloat(card, "alpha", card.alpha, 0f).setDuration(150).start()
                    ObjectAnimator.ofFloat(text, "alpha", 1f, 0f).setDuration(150).start()
                }
            }
            //endregion

            startAnim()

            isSeeking = true

            if (forward) {
                seekTimerR.reset(object : TimerTask() {
                    override fun run() {
                        isSeeking = false
                        stopAnim()
                        seekTimesF = 0
                    }
                }, 850)
            } else {
                seekTimerF.reset(object : TimerTask() {
                    override fun run() {
                        isSeeking = false
                        stopAnim()
                        seekTimesR = 0
                    }
                }, 850)
            }
        }

        if (!PrefManager.getVal<Boolean>(PrefName.DoubleTap)) {
            playerView.findViewById<View>(R.id.exo_fast_forward_button_cont).visibility =
                View.VISIBLE
            playerView.findViewById<View>(R.id.exo_fast_rewind_button_cont).visibility =
                View.VISIBLE
            playerView.findViewById<ImageButton>(R.id.exo_fast_forward_button).setOnClickListener {
                if (isInitialized) {
                    seek(true)
                }
            }
            playerView.findViewById<ImageButton>(R.id.exo_fast_rewind_button).setOnClickListener {
                if (isInitialized) {
                    seek(false)
                }
            }
        }

        keyMap[KEYCODE_DPAD_RIGHT] = { seek(true) }
        keyMap[KEYCODE_DPAD_LEFT] = { seek(false) }

        // Screen Gestures
        fun doubleTap(forward: Boolean, event: MotionEvent) {
            if (!locked && isInitialized && PrefManager.getVal(PrefName.DoubleTap)) {
                seek(forward, event)
            }
        }

        if (PrefManager.getVal(PrefName.Gestures)) {
            val exoBrightnessIcon = playerView.findViewById<ImageView>(R.id.exo_brightness_icon)
            fun setBrightnessIconByValue(brightness: Float) {
                when (brightness) {
                    in 0F..3F -> {
                        exoBrightnessIcon.setImageResource(R.drawable.round_brightness_low_24)
                    }
                    in 3.000001F..7F -> {
                        exoBrightnessIcon.setImageResource(R.drawable.round_brightness_medium_24)
                    }
                    else -> {
                        exoBrightnessIcon.setImageResource(R.drawable.round_brightness_7_24)
                    }
                }
            }

            // Brightness
            var brightnessTimer = Timer()
            exoBrightnessCont.visibility = View.GONE

            fun brightnessHide() {
                brightnessTimer.cancel()
                brightnessTimer.purge()
                val timerTask: TimerTask = object : TimerTask() {
                    override fun run() {
                        handler.post(brightnessRunnable)
                    }
                }
                brightnessTimer = Timer()
                brightnessTimer.schedule(timerTask, 3000)
            }
            exoBrightness.value = (getCurrentBrightnessValue(this) * 10f)
            setBrightnessIconByValue(exoBrightness.value)

            exoBrightness.addOnChangeListener { _, value, _ ->
                val lp = window.attributes
                lp.screenBrightness = brightnessConverter((value.takeIf {
                    !it.isNaN()
                } ?: 0f) / 10, false)
                setBrightnessIconByValue(value)
                window.attributes = lp
                brightnessHide()
            }

            val exoVolumeIcon = playerView.findViewById<ImageView>(R.id.exo_volume_icon)
            fun setAudioIconByVolume(volume: Float) {
                when (volume) {
                    0F -> {
                        exoVolumeIcon.setImageResource(R.drawable.round_volume_off_24)
                    }
                    in 0.000001F..3F -> {
                        exoVolumeIcon.setImageResource(R.drawable.round_volume_mute_24)
                    }
                    in 3.000001F..<7F -> {
                        exoVolumeIcon.setImageResource(R.drawable.round_volume_down_24)
                    }
                    else -> {
                        exoVolumeIcon.setImageResource(R.drawable.round_volume_up_24)
                    }
                }
            }

            // Volume
            var volumeTimer = Timer()
            exoVolumeCont.visibility = View.GONE

            val volumeMax = audioManager.getStreamMaxVolume(STREAM_MUSIC)
            exoVolume.value = audioManager.getStreamVolume(STREAM_MUSIC).toFloat() / volumeMax * 10
            setAudioIconByVolume(exoVolume.value)
            fun volumeHide() {
                volumeTimer.cancel()
                volumeTimer.purge()
                val timerTask: TimerTask = object : TimerTask() {
                    override fun run() {
                        handler.post(volumeRunnable)
                    }
                }
                volumeTimer = Timer()
                volumeTimer.schedule(timerTask, 3000)
            }
            exoVolume.addOnChangeListener { _, value, _ ->
                val volume = ((value.takeIf { !it.isNaN() } ?: 0f) / 10 * volumeMax).roundToInt()
                setAudioIconByVolume(value)
                audioManager.setStreamVolume(STREAM_MUSIC, volume, 0)
                volumeHide()
            }
        }

        val fastForwardText = playerView.findViewById<TextView>(R.id.exo_fast_forward_text)
        var fastModifier = PrefManager.getVal<Float>(PrefName.FastModifier)
        val fastForward = PrefManager.getVal<Boolean>(PrefName.FastForward)
        val lockForward = PrefManager.getVal<Boolean>(PrefName.LockForward)
        fun fastForward() {
            isFastForwarding = true
            exoPlayer.setPlaybackSpeed(exoPlayer.playbackParameters.speed * fastModifier)
            fastForwardText.visibility = View.VISIBLE
            val speedText = "${exoPlayer.playbackParameters.speed}x"
            fastForwardText.text = speedText
        }

        fun editFastForward(movement: Float) {
            // Convert drag movement to a percentage relative to the speed and round to nearest 0.5
            val modifier = (((fastModifier - movement / 100).roundTo(2) * 20).roundToInt() / 20F)
            if (isFastForwarding && !lockForward && abs(modifier - fastModifier) > 0) {
                exoPlayer.setPlaybackSpeed(exoPlayer.playbackParameters.speed / fastModifier)
                fastModifier = clamp(modifier, 0.25f, 2f)
                exoPlayer.setPlaybackSpeed(exoPlayer.playbackParameters.speed * fastModifier)
                val speedText = "${exoPlayer.playbackParameters.speed}x"
                fastForwardText.text = speedText
            }
        }

        fun stopFastForward() {
            if (isFastForwarding) {
                isFastForwarding = false
                exoPlayer.setPlaybackSpeed(exoPlayer.playbackParameters.speed / fastModifier)
                fastForwardText.visibility = View.GONE
            }
        }

        var initForwardX = 0f
        fun detectFastFForwardGesture(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN) initForwardX = event.x
            if (event.action == MotionEvent.ACTION_UP) {
                initForwardX = 0f
                stopFastForward()
            }
            if (event.action == MotionEvent.ACTION_MOVE && isFastForwarding) {
                editFastForward(initForwardX - event.x)
            }
        }

        // FastRewind (Left Panel)
        val fastRewindDetector = GestureDetector(this, object : GestureSlider() {
            override fun onLongClick(event: MotionEvent) {
                if (fastForward) fastForward()
            }

            override fun onDoubleClick(event: MotionEvent) {
                doubleTap(false, event)
            }

            override fun onScrollYClick(y: Float) {
                if (!locked && PrefManager.getVal(PrefName.Gestures)) {
                    val adjustedY = exoBrightness.value + y / 100
                    exoBrightness.value = clamp(adjustedY.roundTo(2), 0f, 10f)
                    if (exoBrightnessCont.visibility != View.VISIBLE) {
                        exoBrightnessCont.visibility = View.VISIBLE
                    }
                    exoBrightnessCont.alpha = 1f
                }
            }

            override fun onScrollXClick(x: Float) { }

            override fun onSingleClick(event: MotionEvent) =
                if (isSeeking) doubleTap(false, event) else handleController()
        })
        val rewindArea = playerView.findViewById<View>(R.id.exo_rewind_area)
        rewindArea.isClickable = true
        rewindArea.setOnTouchListener { v, event ->
            fastRewindDetector.onTouchEvent(event)
            detectFastFForwardGesture(event)
            v.performClick()
            true
        }

        // FastForward (Right Panel)
        val fastForwardDetector = GestureDetector(this, object : GestureSlider() {
            override fun onLongClick(event: MotionEvent) {
                if (fastForward) fastForward()
            }

            override fun onDoubleClick(event: MotionEvent) {
                doubleTap(true, event)
            }

            override fun onScrollYClick(y: Float) {
                if (!locked && PrefManager.getVal(PrefName.Gestures)) {
                    val adjustedY = exoVolume.value + y / 100
                    exoVolume.value = clamp(adjustedY.roundTo(2), 0f, 10f)
                    if (exoVolumeCont.visibility != View.VISIBLE) {
                        exoVolumeCont.visibility = View.VISIBLE
                    }
                    exoVolumeCont.alpha = 1f
                }
            }

            override fun onScrollXClick(x: Float) { }

            override fun onSingleClick(event: MotionEvent) =
                if (isSeeking) doubleTap(true, event) else handleController()
        })
        val forwardArea = playerView.findViewById<View>(R.id.exo_forward_area)
        forwardArea.isClickable = true
        forwardArea.setOnTouchListener { v, event ->
            fastForwardDetector.onTouchEvent(event)
            detectFastFForwardGesture(event)
            v.performClick()
            true
        }

        // Handle Media
        if (!initialized) return reboot()
        model.setMedia(media)
        title = media.userPreferredName
        episodes = media.anime?.episodes ?: return reboot()

        videoInfo = playerView.findViewById(R.id.exo_video_info)

        model.watchSources = if (media.isAdult) HAnimeSources else AnimeSources

        model.epChanged.observe(this) { epChanging = !it }

        // Anime Title
        animeTitle.text = media.userPreferredName

        currentEpisodeIndex = episodes.keys.indexOf(media.anime!!.selectedEpisode!!)

        episodeTitleArr = arrayListOf()
        episodes.forEach {
            val episode = it.value
            val cleanedTitle = MediaNameAdapter.removeEpisodeNumberCompletely(episode.title ?: "")
            episodeTitleArr.add("Episode ${episode.number}${
                if (episode.filler) " [Filler]" else ""
            }${if (cleanedTitle.isNotBlank() && cleanedTitle != "null") ": $cleanedTitle" else ""}")
        }

        // Episode Change
        fun change(index: Int) {
            if (isInitialized) {
                val epKeys = episodes.keys.toList()
                changingServer = false
                setEpisodeProgress()
                exoPlayer.seekTo(0)
                exoPlayer.stop()
                val prev = epKeys[currentEpisodeIndex]
                isTimeStampsLoaded = false
                episodeLength = 0f
                media.anime!!.selectedEpisode = epKeys[index]
                model.setMedia(media)
                model.epChanged.postValue(false)
                model.setEpisode(episodes.values.elementAt(index), "change")
                model.onEpisodeClick(
                    media, media.anime!!.selectedEpisode!!, this.supportFragmentManager, prev
                )
            }
        }

        // Episode Selector
        episodeTitle.adapter = NoPaddingArrayAdapter(this, R.layout.item_dropdown, episodeTitleArr)
        episodeTitle.setSelection(currentEpisodeIndex)
        episodeTitle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                if (position != currentEpisodeIndex) {
                    disappeared = false
                    functionstarted = false
                    change(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Next Episode
        exoNext = playerView.findViewById(R.id.exo_next_ep)
        exoNext.setOnClickListener {
            if (isInitialized) {
                nextEpisode { i ->
                    updateAniProgress {
                        disappeared = false
                        functionstarted = false
                        change(currentEpisodeIndex + i)
                    }
                }
            }
        }
        // Prev Episode
        exoPrev = playerView.findViewById(R.id.exo_prev_ep)
        exoPrev.setOnClickListener {
            if (currentEpisodeIndex > 0) {
                disappeared = false
                change(currentEpisodeIndex - 1)
            } else
                snackString(getString(R.string.first_episode))
        }

        model.getEpisode().observe(this) { ep ->
            hideSystemBars()
            if (ep != null && !epChanging) {
                episode = ep
                FireSale().getProgress("${media.id}_${ep.number}", MediaType.ANIME) {
                    currentEpisodeIndex = episodes.keys.indexOf(ep.number)
                    media.selected = model.loadSelected(media)
                    model.setMedia(media)
                    episodeTitle.setSelection(currentEpisodeIndex)
                    if (isInitialized) releasePlayer()
                    playbackPosition = PrefManager.getCustomVal("${media.id}_${ep.number}", 0)
                    initPlayer()
                    preloading = false
                    updateProgress()
                }
            }
        }

        // FullScreen
        isFullscreen = PrefManager.getCustomVal(media.fullscreenInt, isFullscreen)
        playerView.resizeMode = when (isFullscreen) {
            0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        playerView.findViewById<ImageButton>(R.id.exo_screen).setOnClickListener {
            if (isFullscreen < 2) isFullscreen += 1 else isFullscreen = 0
            playerView.resizeMode = when (isFullscreen) {
                0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            snackString(
                when (isFullscreen) {
                    0 -> "Original"
                    1 -> "Zoom"
                    2 -> "Stretch"
                    else -> "Original"
                }
            )
            PrefManager.setCustomVal(media.fullscreenInt, isFullscreen)
        }

        // Cast
        if (PrefManager.getVal(PrefName.Cast)) {
            playerView.findViewById<CustomCastButton>(R.id.exo_cast).apply {
                visibility = View.VISIBLE
                if (PrefManager.getVal(PrefName.UseInternalCast)) {
                    try {
                        CastButtonFactory.setUpMediaRouteButton(context, this)
                        dialogFactory = CustomCastThemeFactory()
                    } catch (_: Exception) {
                        isCastApiAvailable = false
                    }
                } else {
                    setCastCallback { cast() }
                }
            }
        }

        // Settings
        exoSettings.setOnClickListener {
            setEpisodeProgress()
            val intent = Intent(this, PlayerSettingsActivity::class.java).apply {
                putExtra("media", media)
                putExtra("subtitle", subtitle)
            }
            exoPlayer.pause()
            onChangeSettings.launch(intent)
        }

        // Speed
        val speedsName = playerSpeeds.map { "${it}x" }.toTypedArray()
        //var curSpeed = loadData(media.speedValue, this) ?: settings.defaultSpeed
        var curSpeed = PrefManager.getCustomVal(
            media.speedValue,
            playerSpeeds.indexOf(PrefManager.getVal<Float>(PrefName.ExoPlayerSpeed))
        )

        playbackParameters = PlaybackParameters(playerSpeeds[curSpeed])
        var speed: Float
        playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_playback_speed).setOnClickListener {
            val picker = DialogPickerBinding.inflate(layoutInflater).apply {
                playbackSpeed.minValue = 0
                playbackSpeed.maxValue = speedsName.size - 1
                playbackSpeed.displayedValues = speedsName
                playbackSpeed.value = curSpeed
            }
            val dialog = customAlertDialog().apply {
                setCustomView(picker.root)
                setOnCancelListener { hideSystemBars() }
            }.show()
            picker.playbackConfirm.setOnClickListener {
                val i = picker.playbackSpeed.value
                if (isInitialized) {
                    PrefManager.setCustomVal(media.speedValue, i)
                    speed = playerSpeeds[i]
                    curSpeed = i
                    playbackParameters = PlaybackParameters(speed)
                    exoPlayer.playbackParameters = playbackParameters
                }
                hideSystemBars()
                dialog.dismiss()
            }
        }

        if (PrefManager.getVal(PrefName.AutoPlay)) {
            var touchTimer = Timer()
            fun touched() {
                interacted = true
                touchTimer.apply {
                    cancel()
                    purge()
                }
                touchTimer = Timer()
                touchTimer.schedule(object : TimerTask() {
                    override fun run() {
                        interacted = false
                    }
                }, 1000 * 60 * 60)
            }
            playerView.findViewById<View>(R.id.exo_touch_view).setOnTouchListener { _, _ ->
                touched()
                false
            }
        }

        isFullscreen = PrefManager.getVal(PrefName.Resize)
        playerView.resizeMode = when (isFullscreen) {
            0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        preloading = false
        updateAniProgress {
            model.setEpisode(episodes[media.anime!!.selectedEpisode!!], "invoke")
        }

        // Start the recursive Fun
        updateTimeStamp()
    }

    private fun setDiscordPresence() {
        if (!discordRPC || PrefManager.getVal(PrefName.Incognito)) {
            DiscordService::class.java.stopRunningService(this)
            return
        }
        val buttons = RPC.getAnimeButtons(media)
        val time = Calendar.getInstance().timeInMillis
        lifecycleScope.launch(Dispatchers.Main) {
            startService(Intent(this@ExoplayerView, DiscordService::class.java).apply {
                putExtra(RPC.INTENT_EXTRA, RPC.createPresence(
                    RPC.Companion.RPCData(
                        type = RPC.Type.WATCHING,
                        activityName = media.userPreferredName,
                        details = episode.title?.takeIf { it.isNotEmpty() }
                            ?: getString(R.string.episode_num, episode.number),
                        state = "Episode : ${episode.number}/${media.anime?.totalEpisodes ?: "??"}",
                        largeImage = media.cover?.let {
                            RPC.Link(media.userPreferredName, it)
                        },
                        startTimestamp = if (exoPlayer.isPlaying)
                            time - exoPlayer.currentPosition
                        else
                            null,
                        stopTimestamp = if (exoPlayer.isPlaying)
                            time + exoPlayer.duration
                        else
                            null,
                        buttons = buttons
                    )
                ))
                putExtra(RPC.DURATION_EXTRA, exoPlayer.duration)
            })
        }
    }

    private fun startDiscordService() {
        if (DiscordService.isRunning()) return
        if (discordRPC && !PrefManager.getVal<Boolean>(PrefName.Incognito)) {
            RPC.isEnabled = true
            setDiscordPresence()
        }
    }

    private fun initPlayer() {
        checkNotch()

        torrent?.active_peers?.let {
            if (it < 2) toast(R.string.peer_count_low)
        }

        if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
            FireSale().setCurrent(media.currentEpisode, episodeNumber, MediaType.ANIME)
        }
        PrefManager.setCustomVal(media.currentEpisode, episodeNumber)

        @Suppress("UNCHECKED_CAST")
        val list = (PrefManager.getNullableCustomVal(
            "continueAnimeList",
            listOf<Int>(),
            List::class.java
        ) as List<Int>).toMutableList()
        if (list.contains(media.id)) list.remove(media.id)
        list.add(media.id)
        PrefManager.setCustomVal("continueAnimeList", list)

        lifecycleScope.launch(Dispatchers.IO) {
            extractor?.onVideoStopped(video)
        }

        val ext = episode.extractors?.find { it.server.name == episode.selectedExtractor } ?: return
        extractor = ext
        video = ext.videos.getOrNull(episode.selectedVideo) ?: return

        val languagePref = LanguageMapper.codeMap.entries.filterIndexed { index, _ ->
            index == PrefManager.getVal(PrefName.SubLanguage)
        }.first()
        subtitle = intent.getSerialized("subtitle")
            ?: when (val subLang: String? =
                PrefManager.getNullableCustomVal(media.subLanguage, null, String::class.java)) {
                null -> {
                    when (episode.selectedSubtitle) {
                        null -> null
                        -1 -> ext.subtitles.find { it.language.contains(languagePref.key, ignoreCase = true)
                                || it.language.contains(languagePref.value, ignoreCase = true) }
                        else -> ext.subtitles.getOrNull(episode.selectedSubtitle!!)
                    }
                }

                "None", "none" -> ext.subtitles.let { null }
                else -> ext.subtitles.find { it.language == subLang }
            }

        // Subtitles
        hasExtSubtitles = ext.subtitles.isNotEmpty()
        val sub: MutableList<MediaItem.SubtitleConfiguration> =
            emptyList<MediaItem.SubtitleConfiguration>().toMutableList()
        ext.subtitles.forEach { subtitle ->
            val subtitleUrl = if (subsEmbedded) video!!.file.url else subtitle.file.url
            //var localFile: String? = null
            if (subtitle.type == SubtitleType.UNKNOWN) {
                runBlocking(Dispatchers.IO) {
                    val type = SubtitleDownloader.loadSubtitleType(subtitleUrl)
                    val fileUri = Uri.parse(subtitleUrl)
                    sub += MediaItem.SubtitleConfiguration
                        .Builder(fileUri)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setMimeType(SubtitleType.toMimeType(type))
                        .setId("Extractor")
                        .setLanguage(subtitle.language)
                        .build()
                }
                println("Unknown Subtitle: $sub")
            } else {
                sub += MediaItem.SubtitleConfiguration
                    .Builder(Uri.parse(subtitleUrl))
                    .setSelectionFlags(C.SELECTION_FLAG_FORCED)
                    .setMimeType(SubtitleType.toMimeType(subtitle.type))
                    .setId("Extractor")
                    .setLanguage(subtitle.language)
                    .build()
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            ext.onVideoPlayed(video)
        }

        cacheFactory = CacheDataSource.Factory().apply {
            setCache(VideoCache.getInstance(this@ExoplayerView))
            if (ext.server.offline) {
                setUpstreamDataSourceFactory(DefaultDataSource.Factory(this@ExoplayerView))
            } else {
                val httpClient = okHttpClient.newBuilder().apply {
                    ignoreAllSSLErrors()
                    followRedirects(true)
                    followSslRedirects(true)
                }.build()
                val dataSourceFactory = DataSource.Factory {
                    val dataSource: HttpDataSource =
                        OkHttpDataSource.Factory(httpClient).createDataSource()
                    defaultHeaders.forEach {
                        dataSource.setRequestProperty(it.key, it.value)
                    }
                    video?.file?.headers?.forEach {
                        dataSource.setRequestProperty(it.key, it.value)
                    }
                    dataSource
                }
                setUpstreamDataSourceFactory(dataSourceFactory)
            }
            setCacheWriteDataSinkFactory(null)
            setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

        val mimeType = when (video?.format) {
            VideoType.M3U8 -> MimeTypes.APPLICATION_M3U8
            VideoType.DASH -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.APPLICATION_MP4
        }

        val downloadedMediaItem = if (ext.server.offline) {
            val titleName = ext.server.name.split("/").first()
            val episodeName = ext.server.name.split("/").last()
            downloadId = PrefManager.getAnimeDownloadPreferences()
                .getString("$titleName - $episodeName", null) ?:
                    PrefManager.getAnimeDownloadPreferences()
                        .getString(ext.server.name, null)
            val exoItem = if (downloadId != null) {
                Helper.downloadManager(this).downloadIndex
                    .getDownload(downloadId!!)?.request?.toMediaItem()
            } else null
            if (exoItem != null) {
                exoItem
            } else {
                val directory = getSubDirectory(this, MediaType.ANIME, false, titleName, episodeName)
                if (directory != null) {
                    println(directory.listFiles())
                    val docFile = directory.listFiles().firstOrNull {
                        it.name?.endsWith(".mp4") == true || it.name?.endsWith(".mkv") == true
                    }
                    if (docFile != null) {
                        val downloadedMimeType = when (docFile.extension) {
                            "mp4" -> MimeTypes.APPLICATION_MP4
                            "mkv" -> MimeTypes.APPLICATION_MATROSKA
                            else -> MimeTypes.APPLICATION_MP4
                        }
                        MediaItem.Builder().setUri(docFile.uri)
                            .setMimeType(downloadedMimeType).build()
                    } else {
                        snackString(getString(R.string.file_not_found))
                        null
                    }
                } else {
                    snackString(getString(R.string.directory_not_found))
                    null
                }
            }
        } else null

        mediaItem = if (downloadedMediaItem == null) {
            val builder = MediaItem.Builder().setUri(video!!.file.url).setMimeType(mimeType)
            Logger.log("Video MimeType: $mimeType\nVideo URL: ${video!!.file.url}")
            builder.setSubtitleConfigurations(sub)
            builder.build()
        } else {
            if (sub.isNotEmpty()) {
                downloadedMediaItem.buildUpon().apply {
                    setSubtitleConfigurations(listOf(
                        sub[0].buildUpon().setLanguage(Locale.getDefault().language).build()
                    ))
                    episode.selectedSubtitle = 0
                }.build()
            } else {
                downloadedMediaItem
            }
        }

        mediaItem.buildUpon().setDrmConfiguration(
            MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setForceSessionsForAudioAndVideoTracks(true)
                .build()
        )

        val audioMediaItem = mutableListOf<MediaItem>()
        audioLanguages.clear()
        ext.audioTracks.forEach { track ->
            audioLanguages.add(LanguageMapper.mapNativeToCode(track.lang) ?: track.lang)
            audioMediaItem.add(
                MediaItem.Builder()
                    .setUri(track.url)
                    .setMimeType(MimeTypes.AUDIO_UNKNOWN)
                    .setTag(track.lang)
                    .setSubtitleConfigurations(sub)
                    .build()
            )
        }

        val audioSources = audioMediaItem.map { mediaItem ->
            if (video?.format == VideoType.M3U8) {
                HlsMediaSource.Factory(cacheFactory).apply {
                    experimentalParseSubtitlesDuringExtraction(!hasExtSubtitles)
                    setAllowChunklessPreparation(hasExtSubtitles)
                }.createMediaSource(mediaItem)
            } else {
                DefaultMediaSourceFactory(cacheFactory)
                    .experimentalParseSubtitlesDuringExtraction(!hasExtSubtitles)
                    .createMediaSource(mediaItem)

            }
        }.toTypedArray()
        val videoMediaSource = DefaultMediaSourceFactory(cacheFactory)
            .experimentalParseSubtitlesDuringExtraction(!hasExtSubtitles)
            .createMediaSource(mediaItem)
        mediaSource = MergingMediaSource(videoMediaSource, *audioSources)

        PrefManager.getCustomVal<Set<String>>(
            media.subtitles(episode.number), setOf()
        ).forEach { userSubtitles.add(importSubtitle(Uri.parse(it), true)) }
        if (userSubtitles.isNotEmpty()) {
            mediaSource = MergingMediaSource(DefaultMediaSourceFactory(this)
                .experimentalParseSubtitlesDuringExtraction(!hasExtSubtitles)
                .createMediaSource(
                    mediaItem.buildUpon().setSubtitleConfigurations(userSubtitles).build()
                )
            )
        }

        // Source
        exoSource.setOnClickListener { sourceClick() }

        // Quality Track
        trackSelector = DefaultTrackSelector(this)
        val parameters = trackSelector.buildUponParameters()
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .setAllowVideoNonSeamlessAdaptiveness(true)
            .setSelectUndeterminedTextLanguage(true)
            .setAllowAudioMixedMimeTypeAdaptiveness(true)
            .setAllowMultipleAdaptiveSelections(true)
            .setPreferredTextLanguage(subtitle?.language ?: Locale.getDefault().language)
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .setRendererDisabled(TRACK_TYPE_VIDEO, false)
            .setRendererDisabled(TRACK_TYPE_AUDIO, false)
            .setRendererDisabled(TRACK_TYPE_TEXT, false)
            .setMaxVideoSize(1, 1)
        // .setOverrideForType(TrackSelectionOverride(trackSelector, TRACK_TYPE_VIDEO))
        if (PrefManager.getVal(PrefName.SettingsPreferDub))
            parameters.setPreferredAudioLanguage(Locale.getDefault().language)
        if (PrefManager.getVal(PrefName.SettingsExceedCap))
            parameters.setExceedRendererCapabilitiesIfNecessary(true)
        trackSelector.setParameters(parameters)

        if (playbackPosition != 0L && !changingServer
            && !PrefManager.getVal<Boolean>(PrefName.AlwaysContinue)) {
            val time = String.format(Locale.ROOT,
                "%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(playbackPosition),
                TimeUnit.MILLISECONDS.toMinutes(playbackPosition) - TimeUnit.HOURS.toMinutes(
                    TimeUnit.MILLISECONDS.toHours(
                        playbackPosition
                    )
                ),
                TimeUnit.MILLISECONDS.toSeconds(playbackPosition) - TimeUnit.MINUTES.toSeconds(
                    TimeUnit.MILLISECONDS.toMinutes(
                        playbackPosition
                    )
                )
            )
            customAlertDialog().apply {
                setTitle(getString(R.string.continue_from, time))
                setCancelable(false)
                setPositiveButton(getString(R.string.yes)) {
                    buildExoplayer()
                }
                setNegativeButton(getString(R.string.no)) {
                    playbackPosition = 0L
                    buildExoplayer()
                }
                show()
            }
        } else buildExoplayer()
    }

    private fun buildExoplayer() {
        exoSubtitle.isEnabled = false
        // Player
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(1000 * 60 * 2, true)
            .setBufferDurationsMs(
                DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        hideSystemBars()
        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(
                object : NextRenderersFactory(this@ExoplayerView)  {
                    override fun buildTextRenderers(
                        context: Context,
                        output: TextOutput,
                        outputLooper: Looper,
                        extensionRendererMode: Int,
                        out: java.util.ArrayList<Renderer>
                    ) {
                        super.buildTextRenderers(
                            context, output, outputLooper, extensionRendererMode, out
                        )
                        (Iterables.getLast(out) as TextRenderer)
                            .experimentalSetLegacyDecodingEnabled(true)
                    }
                })
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .buildWithAssSupport(this, AssRenderType.OPEN_GL).apply {
                playWhenReady = true
                this.playbackParameters = this@ExoplayerView.playbackParameters
                setMediaSource(mediaSource)
                prepare()
                PrefManager.getCustomVal(
                    media.maxValue(episodeNumber),
                    Long.MAX_VALUE
                ).takeIf { it != Long.MAX_VALUE }?.let {
                    if (it <= playbackPosition) playbackPosition = max(0, it - 5)
                }
                seekTo(playbackPosition)
            }
        playerView.player = exoPlayer

        startDiscordService()

        try {
            val rightNow = Calendar.getInstance()
            mediaSession = MediaSession.Builder(this, exoPlayer)
                .setId(rightNow.timeInMillis.toString())
                .build()
        } catch (e: Exception) {
            toast(e.toString())
        }

        onSetScrubPreviews()

        exoPlayer.addListener(this)
        if (PrefManager.getVal<Boolean>(PrefName.ExoAnalytics)
            && !PrefManager.getVal<Boolean>(PrefName.Lightspeed)) {
            exoPlayer.addAnalyticsListener(EventLogger())
        }
        isInitialized = true
        exoSubtitle.isEnabled = true

        exoTranslate.alpha = if (PrefManager.getVal(PrefName.SubTranslate)) 1f else 0.3f
        exoTranslate.setOnClickListener {
            val languages = LanguageMapper.codeMap.values.toTypedArray()
            val picker = DialogPickerBinding.inflate(layoutInflater).apply {
                playbackSpeedText.isVisible = false
                playbackSwitch.isVisible = true
                playbackSwitch.isChecked = PrefManager.getVal<Boolean>(PrefName.SubTranslate)
                playbackSwitch.setOnCheckedChangeListener { _, isEnabled ->
                    PrefManager.setVal(PrefName.SubTranslate, isEnabled)
                    exoTranslate.alpha = if (isEnabled) 1f else 0.3f
                }
                playbackSpeed.minValue = 0
                playbackSpeed.maxValue = languages.size - 1
                playbackSpeed.displayedValues = languages
                playbackSpeed.value = PrefManager.getVal(PrefName.SubLanguage)
                playbackConfirm.text = getString(R.string.set_language)
            }
            val dialog = customAlertDialog().apply {
                setCustomView(picker.root)
                setOnCancelListener { hideSystemBars() }
            }.show()
            picker.playbackConfirm.setOnClickListener {
                val i = picker.playbackSpeed.value
                if (isInitialized) {
                    PrefManager.setVal(PrefName.SubLanguage, i)
                }
                hideSystemBars()
                dialog.dismiss()
            }
        }

        if (PrefManager.getVal<Boolean>(PrefName.Subtitles)) {
            exoTranslate.isVisible = true
        } else {
            exoTranslate.isVisible = false
            onSetTrackGroupOverride(dummyTrack, TRACK_TYPE_TEXT)
        }
    }

    private fun onSetScrubPreviews() {
        CoroutineScope(Dispatchers.IO).launch {
            val isAvailable = PrefManager.getVal(PrefName.ScrubAccelerated) && try {
                frameCapture = AV_FrameCapture().apply {
                    setDataSource(video?.file?.url)
                    setTargetSize(176.toPx, 96.toPx)
                    init()
                }
                true
            } catch (_: Exception) {
                false
            } || try {
                retriever = MediaMetadataRetriever().apply {
                    setDataSource(video?.file?.url, video?.file?.headers)
                }
                true
            } catch (_: Exception) {
                false
            }
            timeline.setPreviewEnabled(PrefManager.getVal(PrefName.ScrubPreview) && isAvailable)
        }
    }

    private fun setPreviewLoader() {
        val previewImage = findViewById<ImageView>(R.id.previewImage)
        timeline.setPreviewLoader(object: PreviewLoader {
            override fun loadPreview(currentPosition: Long, max: Long) {
                previewImage.drawable?.let { (it as BitmapDrawable).bitmap.recycle() }
                if ((currentPosition / 1000).toInt() == (timeline.currentFrame / 1000).toInt())
                    return
                timeline.currentFrame = currentPosition
                val keyFrame = currentPosition.toDuration(DurationUnit.MILLISECONDS)
                previewImage.setImageDrawable(null)
                previewImage.setImageBitmap(frameCapture?.getFrameAtTime(keyFrame.inWholeMicroseconds)
                    ?: retriever?.getFrameAtTime(keyFrame.inWholeMicroseconds)?.let { bitmap ->
                        bitmap.scale(176.toPx, 96.toPx, false).apply { bitmap.recycle() }
                    })
            }
        })
    }

    private fun releasePlayer() {
        isPlayerPlaying = exoPlayer.playWhenReady
        playbackPosition = exoPlayer.currentPosition
        isInitialized = false
        disappeared = false
        functionstarted = false
        retriever?.close()
        try { if (!exoPlayer.isReleased) exoPlayer.release() } catch (_: Exception) { }
        VideoCache.release()
        mediaSession?.release()
        DiscordService::class.java.stopRunningService(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (isInitialized) {
            outState.putInt(resumeWindow, exoPlayer.currentMediaItemIndex)
            outState.putLong(resumePosition, exoPlayer.currentPosition)
        }
        outState.putInt(playerFullscreen, isFullscreen)
        outState.putBoolean(playerOnPlay, isPlayerPlaying)
        super.onSaveInstanceState(outState)
    }

    private fun sourceClick() {
        changingServer = true

        media.selected!!.server = null
        setEpisodeProgress()
        model.saveSelected(media.id, media.selected!!)
        model.onEpisodeClick(media, episode.number, this.supportFragmentManager)
    }

    override fun onStart() {
        super.onStart()
        if (this::episode.isInitialized) startDiscordService()
    }

    override fun onPause() {
        super.onPause()
        orientationListener?.disable()
        if (isInitialized) {
            if (castPlayer?.isPlaying == false) playerView.player?.pause()
            if (exoPlayer.currentPosition > 5000) setEpisodeProgress()
        }
    }

    override fun onResume() {
        super.onResume()
        isUserLeaveHint = false
        orientationListener?.enable()
        hideSystemBars()
        if (isInitialized) {
            playerView.onResume()
            playerView.useController = true
        }
    }

    override fun onStop() {
        if (castPlayer?.isPlaying == false) playerView.player?.pause()
        isUserLeaveHint = true
        if (isInitialized) setEpisodeProgress()
        DiscordService::class.java.stopRunningService(this)
        super.onStop()
    }

    override fun onDestroy() {
        playerTeardown()
        super.onDestroy()
    }

    private var wasPlaying = false
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        binding.playerView.isInvisible = PrefManager.getVal(PrefName.SecureLock)
                && !hasFocus && (Version.isNougat && !isInPictureInPictureMode)
        if (PrefManager.getVal(PrefName.FocusPause) && !epChanging) {
            if (isInitialized && !hasFocus) wasPlaying = exoPlayer.isPlaying
            if (hasFocus) {
                if (isInitialized && wasPlaying) exoPlayer.play()
            } else {
                if (isInitialized) exoPlayer.pause()

            }
        }
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isBuffering) {
            isPlayerPlaying = isPlaying
            playerView.keepScreenOn = isPlaying
            if (!isUserLeaveHint) setDiscordPresence()
            (exoPlay.drawable as Animatable?)?.start()
            if (!this.isDestroyed) Glide.with(this)
                .load(if (isPlaying) R.drawable.anim_play_to_pause else R.drawable.anim_pause_to_play)
                .into(exoPlay)
        }
    }

    override fun onRenderedFirstFrame() {
        super.onRenderedFirstFrame()
        if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
            FireSale().setMax(
                media.maxValue(episodeNumber),
                exoPlayer.duration,
                MediaType.ANIME
            )
        }
        PrefManager.setCustomVal(
            media.maxValue(episodeNumber),
            exoPlayer.duration
        )
        val height = (exoPlayer.videoFormat ?: return).height
        val width = (exoPlayer.videoFormat ?: return).width

        aspectRatio = Rational(width, height)

        videoInfo.text = getString(R.string.video_quality, height)

        if (exoPlayer.duration < playbackPosition)
            exoPlayer.seekTo(0)

        // if playbackPosition is within 95% of the episode length, reset it to 0
        if (playbackPosition > exoPlayer.duration.toFloat() * 0.95) {
            playbackPosition = 0L
            exoPlayer.seekTo(playbackPosition)
        }

        if (!isTimeStampsLoaded && segmentsEnabled) {
            val duration = exoPlayer.duration / 1000
            lifecycleScope.launch(Dispatchers.IO) {
                // AnimeSkip.queryTimestamps(media.mainName())
                model.loadTimeStamps(
                    media.idMAL,
                    episodeNumber.trim().toIntOrNull() ?: (currentEpisodeIndex + 1),
                    duration
                )
            }
        }
    }

    // Link Preloading
    private var preloading = false
    private fun updateProgress() {
        if (isInitialized) {
            if (exoPlayer.currentPosition.toFloat() / exoPlayer.duration >
                PrefManager.getVal<Float>(PrefName.WatchPercentage)) {
                preloading = true
                nextEpisode(false) { i ->
                    val ep = episodes.values.elementAt(currentEpisodeIndex + i)
                    val selected = media.selected ?: return@nextEpisode
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (media.selected!!.server != null)
                            model.loadEpisodeSingleVideo(ep, selected, false)
                        else
                            model.loadEpisodeVideos(ep, selected.sourceIndex, false)
                    }
                }
            }
        }
        if (!preloading) handler.postDelayed({
            updateProgress()
        }, 2500)
    }

    // TimeStamp Updating
    private var currentTimeStamp: AniSkip.Stamp? = null
    private var skippedTimeStamps: MutableList<AniSkip.Stamp> = mutableListOf()
    private var timeStampHiddden = false
    private fun updateTimeStamp() {
        val skipTimeButton = playerView.findViewById<MaterialCardView>(R.id.exo_skip_timestamp)
        val skipTimeText = skipTimeButton.findViewById<TextView>(R.id.exo_skip_timestamp_text)
        var timer: CountDownTimer? = null
        fun cancelTimer() {
            timer?.cancel()
            timer = null
        }
        fun noTimeStamp() : String {
            disappeared = false
            functionstarted = false
            skipTimeButton.visibility = View.GONE
            exoSkip.isVisible = PrefManager.getVal<Int>(PrefName.SkipTime) > 0
            timeStampHiddden = false
            return if (timeStampText.text == getString(R.string.aniskip_failed))
                getString(R.string.aniskip_failed)
            else ""
        }
        if (isInitialized && segmentsEnabled) {
            val playerCurrentTime = exoPlayer.currentPosition / 1000
            currentTimeStamp = model.timeStamps.value?.find { timestamp ->
                timestamp.interval.startTime < playerCurrentTime
                        && playerCurrentTime < (timestamp.interval.endTime - 1)
            }

            val newTimeStamp = currentTimeStamp
            val timeout = (PrefManager.getVal<Int>(PrefName.StampButtonTimeout) * 1000).toLong()
            timer = object : CountDownTimer(timeout, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (newTimeStamp == null) {
                        skipTimeButton.visibility = View.GONE
                        exoSkip.isVisible = PrefManager.getVal<Int>(PrefName.SkipTime) > 0
                        disappeared = false
                        functionstarted = false
                        cancelTimer()
                    }
                }

                override fun onFinish() {
                    skipTimeButton.visibility = View.GONE
                    exoSkip.isVisible = PrefManager.getVal<Int>(PrefName.SkipTime) > 0
                    disappeared = true
                    functionstarted = false
                    cancelTimer()
                }
            }
            timeStampText.text = newTimeStamp?.let { timestamp ->
                fun disappearSkip() {
                    functionstarted = true
                    skipTimeButton.visibility = View.VISIBLE
                    exoSkip.visibility = View.GONE
                    skipTimeText.text = timestamp.skipType.getType(this)
                    skipTimeButton.setOnClickListener {
                        exoPlayer.seekTo((timestamp.interval.endTime * 1000).toLong())
                        timeStampHiddden = false
                    }
                    timer?.start()
                }
                when {
                    PrefManager.getVal(PrefName.AutoSkipOPED)
                            && (timestamp.skipType == "op" || timestamp.skipType == "ed") -> {
                            // && !skippedTimeStamps.contains(timestamp) -> {
                        exoPlayer.seekTo((timestamp.interval.endTime * 1000).toLong())
                        timeStampHiddden = false
                        skippedTimeStamps.add(timestamp)
                    }

                    PrefManager.getVal(PrefName.AutoSkipRecap)
                            && timestamp.skipType == "recap" -> {
                            // && !skippedTimeStamps.contains(timestamp) -> {
                        exoPlayer.seekTo((timestamp.interval.endTime * 1000).toLong())
                        timeStampHiddden = false
                        skippedTimeStamps.add(timestamp)
                    }

                    PrefManager.getVal(PrefName.ShowTimeStampButton) -> {
                        val autoHide = PrefManager.getVal<Boolean>(PrefName.AutoHideTimeStamps)
                        if (autoHide && !functionstarted && !disappeared) {
                            disappearSkip()
                            timeStampHiddden = true
                        } else if (!autoHide) {
                            skipTimeButton.visibility = View.VISIBLE
                            exoSkip.visibility = View.GONE
                            skipTimeText.text = timestamp.skipType.getType(this)
                            skipTimeButton.setOnClickListener {
                                exoPlayer.seekTo((timestamp.interval.endTime * 1000).toLong())
                                timeStampHiddden = false
                            }
                        }
                    }
                }
                timestamp.skipType.getType(this)
            } ?: noTimeStamp()
        } else {
            cancelTimer()
            noTimeStamp()
        }
        handler.postDelayed({
            updateTimeStamp()
        }, 500)
    }

    fun onSetTrackGroupOverride(trackGroup: Tracks.Group, type: @C.TrackType Int, index: Int = 0) {
        val format = trackGroup.mediaTrackGroup.getFormat(0)
        if (format.language == "load") {
            showSubtitleDialog()
            return
        }
        PrefManager.setCustomVal(media.subLanguage, format.language)
        val isDisabled = format.language == "none"
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(TRACK_TYPE_TEXT, isDisabled)
            .setOverrideForType(
                TrackSelectionOverride(trackGroup.mediaTrackGroup, index)
            )
            .setSelectUndeterminedTextLanguage(format.containerMimeType == getString(R.string.mimetype_cea))
            .build()
        if (type == TRACK_TYPE_TEXT) setSubtitleStyle(playerView)
    }

    private val dummyTrack = Tracks.Group(
        TrackGroup("Dummy Track", Format.Builder().apply { setLanguage("none") }.build()),
        true,
        intArrayOf(1),
        booleanArrayOf(false)
    )

    private val loaderTrack = Tracks.Group(
        TrackGroup("Sideload", Format.Builder().apply { setLanguage("load") }.build()),
        true,
        intArrayOf(1),
        booleanArrayOf(false)
    )

    override fun onTracksChanged(tracks: Tracks) {
        val audioTracks: ArrayList<Tracks.Group> = arrayListOf()
        val subTracks: ArrayList<Tracks.Group> = arrayListOf(dummyTrack)
        tracks.groups.forEach {
            when (it.mediaTrackGroup.type) {
                TRACK_TYPE_AUDIO -> {
                    if (it.isSupported(true)) audioTracks.add(it)
                }

                TRACK_TYPE_TEXT -> {
                    if (it.isSupported(true)) subTracks.add(it)
                }
            }
        }
        subTracks.add(loaderTrack)
        val exoAudioTrack = playerView.findViewById<ImageButton>(R.id.exo_audio)
        exoAudioTrack.isVisible = audioTracks.size > 1
        exoAudioTrack.setOnClickListener {
            TrackGroupDialogFragment(this, audioTracks, TRACK_TYPE_AUDIO)
                .show(supportFragmentManager, "dialog")
        }
        exoSubtitle.setOnClickListener {
            TrackGroupDialogFragment(this, subTracks, TRACK_TYPE_TEXT)
                .show(supportFragmentManager, "dialog")
        }
    }

    private fun extensionMimeType(extension: String): String {
        return when (extension) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ass" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_UNKNOWN
        }
    }

    private fun importSubtitle(uri: Uri, isFile: Boolean): MediaItem.SubtitleConfiguration {
        val file = if (isFile) DocumentFile.fromSingleUri(this, uri) else null
        val mimeType = if (isFile) {
            Logger.log("Sideload MimeType: ${contentResolver.getType(uri)}")
            when (contentResolver.getType(uri)) {
                MimeTypes.TEXT_VTT -> MimeTypes.TEXT_VTT
                MimeTypes.APPLICATION_TTML -> MimeTypes.APPLICATION_TTML
                MimeTypes.APPLICATION_SUBRIP -> MimeTypes.APPLICATION_SUBRIP
                MimeTypes.TEXT_SSA -> MimeTypes.TEXT_SSA
                getString(R.string.mimetype_binary) -> MimeTypes.TEXT_SSA
                else ->
                    file?.let { doc ->
                        Logger.log("Sideload Extension: ${doc.extension.lowercase()}")
                        extensionMimeType(doc.extension.lowercase())
                    } ?: MimeTypes.TEXT_UNKNOWN
            }
        } else {
            Logger.log("Sideload Extension: ${uri.toString().substringAfterLast(".")}")
            extensionMimeType(uri.toString().substringAfterLast("."))
        }
        return MediaItem.SubtitleConfiguration
            .Builder(uri)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .setMimeType(mimeType)
            .setLanguage("file")
            .setId(uri.toString())
            .build()
    }

    private fun buildSubtitleSource(mediaItem: MediaItem) {
        mediaSource = if (userSubtitles.isNotEmpty()) {
            MergingMediaSource(DefaultMediaSourceFactory(this).createMediaSource(mediaItem))
        } else {
            MergingMediaSource(DefaultMediaSourceFactory(cacheFactory).createMediaSource(mediaItem))
        }
        buildExoplayer()
    }

    fun onRemoveSubtitleFile(uriString: String?) {
        customAlertDialog().apply {
            setTitle(R.string.remove_subtitle)
            setMessage(DocumentFile.fromSingleUri(this@ExoplayerView, Uri.parse(uriString))?.name)
            setPositiveButton(R.string.delete) {
                val subtitlePref = media.subtitles(episode.number)
                val subs = PrefManager.getCustomVal<Set<String>>(
                    subtitlePref, setOf()
                ).minus(uriString)
                PrefManager.setCustomVal(subtitlePref, subs)
                userSubtitles.remove(userSubtitles.find { it.uri == Uri.parse(uriString) })
                if (exoPlayer.currentPosition > 5000) { setEpisodeProgress() }
                model.setEpisode(episode, "Subtitle")
            }
            setNegativeButton(R.string.cancel) { }
            show()
        }
    }

    private val onImportSubtitle = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { documents: List<Uri> ->
        userSubtitles.forEach {
            it.buildUpon().setSelectionFlags(C.SELECTION_FLAG_FORCED).build()
        }
        val subtitlePref = media.subtitles(episode.number)
        documents.forEach { uri ->
            if (userSubtitles.any { it.uri == uri }) {
                toast(R.string.duplicate_sub)
                return@forEach
            }
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val subs = PrefManager.getCustomVal<Set<String>>(
                subtitlePref, setOf()
            ).plus(uri.toString())
            PrefManager.setCustomVal(subtitlePref, subs)
            userSubtitles.add(importSubtitle(uri, true))
        }
        if (userSubtitles.isNotEmpty()) {
            buildSubtitleSource(
                mediaItem.buildUpon().setSubtitleConfigurations(userSubtitles).build()
            )
        }
    }

    private fun showSubtitleDialog() {
//        val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
//        val editText = dialogView.userAgentTextBox.apply {
//            hint = getString(R.string.subtitle_url)
//        }
//        customAlertDialog().apply {
//            setTitle(R.string.subtitle_url)
//            setCustomView(dialogView.root)
//            setPosButton(R.string.save) {
//                if (!editText.text.isNullOrBlank()) {
//                    val subs = mediaItem.localConfiguration?.subtitleConfigurations?.toMutableList()
//                        ?: mutableListOf<MediaItem.SubtitleConfiguration>()
//                    val uri = Uri.parse(editText.text.toString())
//                    if (subs.any { it.uri == uri }) {
//                        snackString(R.string.duplicate_sub)
//                        return@setPosButton
//                    }
//                    subs.forEach {
//                        it.buildUpon().setSelectionFlags(C.SELECTION_FLAG_FORCED).build()
//                    }
//                    subs.add(importSubtitle(uri, false))
//                    buildSubtitleSource(
//                        mediaItem.buildUpon().setSubtitleConfigurations(subs).build()
//                    )
//                }
//            }
//            setNeutralButton(R.string.import_file) {
                try {
                    onImportSubtitle.launch(
                        arrayOf(
                            MimeTypes.TEXT_VTT,
                            MimeTypes.APPLICATION_TTML,
                            MimeTypes.APPLICATION_SUBRIP,
                            MimeTypes.TEXT_SSA,
                            getString(R.string.mimetype_binary)
                        )
                    )
                } catch (_: ActivityNotFoundException) {
                    snackString(R.string.no_intent_apps)
                }
//            }
//            setNegButton(R.string.cancel)
//            show()
//        }
    }

    private val onChangeSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: ActivityResult ->
        if (discordRPC)
            startDiscordService()
        else
            DiscordService::class.java.stopRunningService(this)
        onSetTrackGroupOverride(dummyTrack, TRACK_TYPE_TEXT)
        if (PrefManager.getVal(PrefName.Subtitles)) {
            exoPlayer.currentTracks.groups.filter {
                it.mediaTrackGroup.type == TRACK_TYPE_TEXT
            } .forEach { trackGroup ->
                if (trackGroup.isSelected) {
                    onSetTrackGroupOverride(trackGroup, TRACK_TYPE_TEXT)
                }
            }
            exoTranslate.isVisible = true
        } else {
            exoTranslate.isVisible = false
        }
        onSetScrubPreviews()
        setPreviewLoader()
        if (isInitialized) exoPlayer.play()
    }

    override fun onPlayerError(error: PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                toast(getString(R.string.exo_source_exception, error.message))
                isPlayerPlaying = true
                sourceClick()
            }

            PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                toast(getString(R.string.exo_decoding_failed, error.message))
                sourceClick()
            }

            else -> {
                toast(
                    getString(
                        R.string.exo_player_error,
                        error.errorCode,
                        error.errorCodeName,
                        error.message
                    )
                )
                Logger.log(error)
            }
        }
    }

    private var isBuffering = true
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == ExoPlayer.STATE_READY) {
            exoPlayer.play()
            if (episodeLength == 0f) {
                episodeLength = exoPlayer.duration.toFloat()
            }
        }
        isBuffering = playbackState == Player.STATE_BUFFERING
        if (playbackState == Player.STATE_ENDED && PrefManager.getVal(PrefName.AutoPlay)) {
            if (interacted) exoNext.performClick()
            else toast(getString(R.string.autoplay_cancelled))
        }
        super.onPlaybackStateChanged(playbackState)
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
            setOnCancelListener { hideSystemBars() }
            setCancelable(false)
            setPositiveButton(getString(R.string.yes)) {
                PrefManager.setCustomVal(media.saveProgress, true)
                updateProgress(media, episodeNumber)
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
        val showProgressDialog = PrefManager.getVal<Boolean>(PrefName.AskIndividualPlayer)
                && PrefManager.getCustomVal(media.progressDialog, true)
        val autoSave = !showProgressDialog && PrefManager.getCustomVal(media.saveProgress, true)
        val episode0 = episodeNumber.toFloatOrNull()?.let {
            currentEpisodeIndex == 0 && it.minus(1).toInt() == 0
                    && PrefManager.getVal(PrefName.ChapterZeroPlayer)
        } == true
        val episodeEnd = this::exoPlayer.isInitialized && exoPlayer.duration > 0
                && exoPlayer.currentPosition / exoPlayer.duration >
                PrefManager.getVal<Float>(PrefName.WatchPercentage)

        if (!PrefManager.getVal<Boolean>(PrefName.Incognito)
            && AniList.userid != null
            && if (media.isAdult) PrefManager.getVal(PrefName.UpdateForHPlayer) else true) {
            when {
                episodeEnd && autoSave -> {
                    updateProgress(media, if (episode0) "0" else episodeNumber)
                    runnable.run()
                }
                episode0 && autoSave -> {
                    updateProgress(media, "0")
                    runnable.run()
                }
                episodeEnd && showProgressDialog -> { progress(runnable) }
                else -> { runnable.run() }
            }
        } else {
            runnable.run()
        }
    }

    private fun nextEpisode(toast: Boolean = true, runnable: ((Int) -> Unit)) {
        var isFiller = true
        var i = 1
        while (isFiller) {
            if (episodes.size > currentEpisodeIndex + i) {
                isFiller =
                    if (PrefManager.getVal(PrefName.AutoSkipFiller))
                        episodes.values.elementAt(currentEpisodeIndex + i).filler == true
                    else
                        false
                if (!isFiller) runnable.invoke(i)
                i++
            } else {
                if (toast) toast(getString(R.string.no_next_episode))
                isFiller = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCues(cueGroup: CueGroup) {
        super.onCues(cueGroup)
        if (!PrefManager.getVal<Boolean>(PrefName.SubTranslate)) return
        var lang = PrefManager.getNullableCustomVal(media.subLanguage, null, String::class.java)
            ?: return
        playerView.subtitleView?.let { subtitles ->
            subtitles.setCues(null)
            lifecycleScope.launch(Dispatchers.IO) { subtitles.setCues(cueGroup.localizedFrom(lang)) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "ani.himitsu.media.anime.EPISODE") {
            media.anime?.selectedEpisode = intent.getStringExtra("episodeNumber")
            currentEpisodeIndex = episodes.keys.indexOf(media.anime?.selectedEpisode)
            episodeTitle.setSelection(currentEpisodeIndex)
            if (isInitialized) releasePlayer()
            playbackPosition = PrefManager.getCustomVal("${media.id}_${episodeNumber}", 0)
            initPlayer()
        } else {
            playerTeardown()
            startActivity(Intent(intent))
        }
    }

    private fun playerTeardown() {
        isUserLeaveHint = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        CoroutineScope(Dispatchers.IO).launch {
            tryWithSuspend(true) {
                extractor?.onVideoStopped(video)
            }
        }

        if (isInitialized) exoPlayer.pause() // For slow interactions
        updateAniProgress {
            if (isInitialized) {
                setEpisodeProgress()
                exoPlayer.stop()
                releasePlayer()
            }

            torrent?.hash?.let {
                runBlocking(Dispatchers.IO) { removeTorrent(it) }
                torrent = null
            }

            finishAndRemoveTask()
        }
    }

    fun setEpisodeProgress() {
        val episodePref = "${media.id}_${episodeNumber}"
        if (PrefManager.getVal<Boolean>(PrefName.SyncProgress)) {
            FireSale().setProgress(episodePref, exoPlayer.currentPosition, MediaType.ANIME)
        }
        PrefManager.setCustomVal(episodePref, exoPlayer.currentPosition)
    }

    // Enter PiP Mode
    @Suppress("DEPRECATION")
    private fun enterPipMode() {
        wasPlaying = isPlayerPlaying
        if (!pipEnabled) return
        try {
            if (Version.isOreo) {
                enterPictureInPictureMode(getPictureInPictureBuilder().build())
            } else if (Version.isNougat) {
                enterPictureInPictureMode()
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getPictureInPictureBuilder(): PictureInPictureParams.Builder {
        return PictureInPictureParams.Builder().also {
            setPictureInPictureParams(it.setAspectRatio(aspectRatio).build())
        }
    }

    private fun onPiPChanged(isInPictureInPictureMode: Boolean) {
        playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationListener?.disable()
        } else {
            orientationListener?.enable()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            var fontSize = PrefManager.getVal<Int>(PrefName.FontSize).toFloat()
            if (isInPictureInPictureMode) fontSize /= 3.toFloat()
            playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        }
        if (lifecycle.currentState == Lifecycle.State.CREATED && !isInPictureInPictureMode) {
            playerTeardown()
        } else {
            if (wasPlaying) exoPlayer.play()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        @Suppress("DEPRECATION")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        onPiPChanged(isInPictureInPictureMode)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        onPiPChanged(isInPictureInPictureMode)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        onPiPChanged(isInPictureInPictureMode)
    }

    private val keyMap: MutableMap<Int, (() -> Unit)?> = mutableMapOf(
        KEYCODE_DPAD_RIGHT to null,
        KEYCODE_DPAD_LEFT to null,
        KEYCODE_SPACE to { exoPlay.performClick() },
        KEYCODE_N to { exoNext.performClick() },
        KEYCODE_B to { exoPrev.performClick() }
    )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return if (keyMap.containsKey(event.keyCode)) {
            (event.action == ACTION_UP).also {
                if (isInitialized && it) keyMap[event.keyCode]?.invoke()
            }
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    // Cast
    private fun cast() {
        val videoURL = video?.file?.url ?: return
        val subtitleUrl = if (subsEmbedded) videoURL else subtitle!!.file.url
        val shareVideo = Intent(Intent.ACTION_VIEW)
        shareVideo.setDataAndType(Uri.parse(videoURL), "video/*")
        shareVideo.setPackage("com.instantbits.cast.webvideo")
        if (subtitle != null) shareVideo.putExtra("subtitle", subtitleUrl)
        shareVideo.putExtra(
            "title",
            media.userPreferredName + " : Ep " + episodeTitleArr[currentEpisodeIndex]
        )
        shareVideo.putExtra("poster", episode.thumb?.url ?: media.cover)
        val headers = Bundle()
        defaultHeaders.forEach {
            headers.putString(it.key, it.value)
        }
        video?.file?.headers?.forEach {
            headers.putString(it.key, it.value)
        }
        shareVideo.putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
        shareVideo.putExtra("secure_uri", true)
        try {
            startActivity(shareVideo)
        } catch (_: ActivityNotFoundException) {
            openInGooglePlay("com.instantbits.cast.webvideo")
        }
    }

    private fun startCastPlayer() {
        if (!isCastApiAvailable) {
            snackString(getString(R.string.cast_api_not_available))
            return
        }
        //make sure mediaItem is initialized and castPlayer is not null
        if (!this::mediaItem.isInitialized || castPlayer == null) return
        castPlayer?.setMediaItem(mediaItem)
        castPlayer?.prepare()
        playerView.player = castPlayer
        exoPlayer.stop()
        castPlayer?.addListener(object : Player.Listener {
            //if the player is paused changed, we want to update the UI
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                if (playWhenReady) {
                    (exoPlay.drawable as Animatable?)?.start()
                    Glide.with(this@ExoplayerView)
                        .load(R.drawable.anim_play_to_pause)
                        .into(exoPlay)
                } else {
                    (exoPlay.drawable as Animatable?)?.start()
                    Glide.with(this@ExoplayerView)
                        .load(R.drawable.anim_pause_to_play)
                        .into(exoPlay)
                }
            }
        })
    }

    private fun startExoPlayer() {
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        playerView.player = exoPlayer
        castPlayer?.stop()
    }

    override fun onCastSessionAvailable() {
        if (isCastApiAvailable && !this.isDestroyed) {
            startCastPlayer()
        }
    }

    override fun onCastSessionUnavailable() {
        startExoPlayer()
    }

    @SuppressLint("ViewConstructor")
    class ExtendedTimeBar(
        context: Context,
        attrs: AttributeSet?
    ) : PreviewTimeBar(context, attrs) {
        private var enabled = false
        private var forceDisabled = false
        var currentFrame = 0L

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
            super.setEnabled(!forceDisabled && this.enabled)
        }

        fun setForceDisabled(forceDisabled: Boolean) {
            this.forceDisabled = forceDisabled
            isEnabled = enabled
        }
    }

    private val segmentsEnabled : Boolean get() {
        return PrefManager.getVal(PrefName.ShowTimeStampButton)
                || PrefManager.getVal(PrefName.AutoSkipOPED)
                || PrefManager.getVal(PrefName.AutoSkipRecap)
    }
}