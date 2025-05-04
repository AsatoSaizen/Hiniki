package ani.himitsu.settings

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.connections.discord.Discord
import ani.himitsu.databinding.ActivityPlayerSettingsBinding
import ani.himitsu.initActivity
import ani.himitsu.media.anime.ExoplayerView
import ani.himitsu.media.cereal.Media
import ani.himitsu.others.LanguageMapper
import ani.himitsu.others.getSerialized
import ani.himitsu.parsers.Subtitle
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.Xpandable
import bit.himitsu.cloudflare.Subtitles.fontCollection
import bit.himitsu.cloudflare.Subtitles.getUserTypeface
import bit.himitsu.cloudflare.Subtitles.requestTypeface
import bit.himitsu.content.toPx
import bit.himitsu.nio.string
import bit.himitsu.os.Version
import bit.himitsu.widget.onCompletedActionText
import bit.himitsu.withFlexibleMargin
import kotlin.Int
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PlayerSettingsActivity : AppCompatActivity() {
    lateinit var binding: ActivityPlayerSettingsBinding
    private val player = "player_settings"

    var media: Media? = null
    var subtitle: Subtitle? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityPlayerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        try {
            media = intent.getSerialized("media")
            subtitle = intent.getSerialized("subtitle")
        } catch (e: Exception) {
            toast(e.toString())
        }

        binding.playerSettingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        binding.playerSettingsContainer.withFlexibleMargin(resources.configuration)

        binding.playerSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Behaviour
        var speedName = "${PrefManager.getVal<Float>(PrefName.ExoPlayerSpeed)}x"
        binding.playerSettingsSpeed.text = getString(R.string.default_playback_speed, speedName)
        binding.playerSettingsSpeed.setOnClickListener {
            val sliderView = binding.behaviorRecyclerView.findViewHolderForAdapterPosition(0)
                    as SettingsAdapter.SettingsSliderViewHolder
            sliderView.binding.settingSlider.value = 1.0f
            PrefManager.setVal(PrefName.ExoPlayerSpeed, sliderView.binding.settingSlider.value)
            binding.playerSettingsSpeed.text = getString(R.string.default_playback_speed,
                "${sliderView.binding.settingSlider.value}x") // Use the slider to verify
        }

        binding.behaviorRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.playback_speed,
                    icon = R.drawable.round_timeline_24,
                    valueFrom = 0.25f,
                    valueTo = 50f,
                    stepSize = 0.05f,
                    value = PrefManager.getVal<Float>(PrefName.ExoPlayerSpeed),
                    slider = { value, _ ->
                        PrefManager.setVal(PrefName.ExoPlayerSpeed, value)
                        binding.playerSettingsSpeed.text =
                            getString(R.string.default_playback_speed, "${value}x")
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.show_rotate_button,
                    icon = R.drawable.round_screen_rotation_alt_24,
                    pref = PrefName.RotationPlayer,
                    isVisible = android.provider.Settings.System.getInt(
                        contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0
                    ) != 1
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.resize_mode_button,
                    icon = R.drawable.round_fullscreen_24,
                    onClick = {
                        val resizeModes = resources.getStringArray(R.array.resizeModes)
                        customAlertDialog().apply {
                            setTitle(R.string.default_resize_mode)
                            setSingleChoiceItems(resizeModes, PrefManager.getVal<Int>(PrefName.Resize)) {
                                PrefManager.setVal(PrefName.Resize, it)
                            }
                            show()
                        }
                    },
                    isDialog = true
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.always_continue,
                    icon = R.drawable.round_play_circle_24,
                    pref = PrefName.AlwaysContinue
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.pause_video_focus,
                    icon = R.drawable.round_pause_24,
                    pref = PrefName.FocusPause
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.picture_in_picture,
                    icon = R.drawable.round_picture_in_picture_alt_24,
                    pref = PrefName.Pip,
                    isVisible = Version.isNougat
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.scrub_preview,
                    icon = R.drawable.round_filter_frames_24,
                    pref = PrefName.ScrubPreview,
                    itemsEnabled = arrayOf(6, 7)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.scrub_opengl,
                    icon = R.drawable.ic_preview_24,
                    pref = PrefName.ScrubAccelerated,
                    isEnabled = PrefManager.getVal(PrefName.ScrubPreview)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.show_cast_button,
                    icon = R.drawable.round_cast_24,
                    pref = PrefName.Cast,
                    itemsEnabled = arrayOf(9)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.try_internal_cast_experimental,
                    icon = R.drawable.cast_warning,
                    pref = PrefName.UseInternalCast,
                    isEnabled = PrefManager.getVal(PrefName.Cast)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.gestures,
                    icon = R.drawable.round_swipe_vertical_24,
                    pref = PrefName.Gestures
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.gesture_play_speed,
                    icon = R.drawable.round_fast_forward_24,
                    pref = PrefName.FastForward,
                    itemsEnabled = arrayOf(12, 13)
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.gesture_play_start,
                    icon = R.drawable.round_timeline_24,
                    pref = PrefName.FastModifier,
                    valueFrom = 0.25f,
                    valueTo = 2f,
                    stepSize = 0.05f,
                    isEnabled = PrefManager.getVal(PrefName.FastForward)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.gesture_play_lock,
                    icon = R.drawable.round_timelapse_24,
                    pref = PrefName.LockForward,
                    isEnabled = PrefManager.getVal(PrefName.FastForward)
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.seek_time,
                    descRes = R.string.seek_time_info,
                    icon = R.drawable.round_fast_rewind_24,
                    valueFrom = 5f,
                    valueTo = 45f,
                    stepSize = 5f,
                    value = PrefManager.getVal<Int>(PrefName.SeekTime).toFloat(),
                    slider = { value, _ ->
                        PrefManager.setVal(PrefName.SeekTime, value.roundToInt())
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.double_tap,
                    descRes = R.string.double_tap_info,
                    icon = R.drawable.round_touch_app_24,
                    pref = PrefName.DoubleTap
                )
            )
        )
        binding.behaviorRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerSettingsActivity, LinearLayoutManager.VERTICAL, false)
        }

        binding.exoSkipTime.setText(PrefManager.getVal<Int>(PrefName.SkipTime).string)
        binding.exoSkipTime.setOnEditorActionListener(onCompletedActionText {
            binding.exoSkipTime.clearFocus()
        })
        binding.exoSkipTime.addTextChangedListener {
            val time = binding.exoSkipTime.text.toString().toIntOrNull()
            if (time != null) {
                PrefManager.setVal(PrefName.SkipTime, time)
            }
        }

        // Segments
        binding.segmentsRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.show_skip_time_stamp_button,
                    icon = R.drawable.round_fast_forward_24,
                    pref = PrefName.ShowTimeStampButton,
                    itemsEnabled = arrayOf(2, 3)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.auto_hide_time_stamps,
                    descRes = R.string.hide_skip_button,
                    icon = R.drawable.round_art_track_24,
                    pref = PrefName.AutoHideTimeStamps,
                    isEnabled = PrefManager.getVal(PrefName.ShowTimeStampButton)
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.hide_skip_timeout,
                    icon = R.drawable.round_timelapse_24,
                    valueFrom = 5f,
                    valueTo = 30f,
                    stepSize = 5f,
                    value = PrefManager.getVal<Int>(PrefName.StampButtonTimeout).toFloat(),
                    slider = { value, _ ->
                        PrefManager.setVal(PrefName.StampButtonTimeout, value.roundToInt())
                    },
                    isEnabled = PrefManager.getVal(PrefName.ShowTimeStampButton)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.auto_skip_op_ed,
                    icon = R.drawable.round_play_disabled_24,
                    pref = PrefName.AutoSkipOPED
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.auto_skip_recap,
                    icon = R.drawable.round_play_disabled_24,
                    pref = PrefName.AutoSkipRecap
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.auto_skip_fillers,
                    descRes = R.string.auto_skip_fillers_info,
                    icon = R.drawable.round_play_disabled_24,
                    pref = PrefName.AutoSkipFiller
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.auto_play_next_episode,
                    descRes = R.string.auto_play_next_episode_info,
                    icon = R.drawable.round_skip_next_24,
                    pref = PrefName.AutoPlay
                )
            )
        )
        binding.segmentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerSettingsActivity, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)
        }

        binding.subtitlesRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.subtitle_toggle,
                    descRes = R.string.sub_color_info,
                    icon = R.drawable.round_subtitles_24,
                    pref = PrefName.Subtitles,
                    itemsShown = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.sub_translate,
                    icon = R.drawable.round_g_translate_24,
                    pref = PrefName.SubTranslate,
                    isVisible = PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.subtitle_langauge,
                    icon = R.drawable.round_translate_24,
                    onClick = {
                        customAlertDialog().apply {
                            setTitle(R.string.subtitle_langauge)
                            setSingleChoiceItems(
                                LanguageMapper.codeMap.values.toTypedArray(),
                                PrefManager.getVal(PrefName.SubLanguage)
                            ) {
                                PrefManager.setVal(PrefName.SubLanguage, it)
                            }
                            show()
                        }
                    },
                    isDialog = true,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.subtitle_defaults,
                    descRes = R.string.subtitle_defaults_desc,
                    icon = R.drawable.round_subtitles_24,
                    pref = PrefName.SubDefaults,
                    isVisible = PrefManager.getVal(PrefName.Subtitles),
                    itemsDisabled = arrayOf(10, 12)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.primary_sub_color_select,
                    icon = R.drawable.round_color_lens_24,
                    onClick = {
                        val colorsPrimary = resources.getStringArray(R.array.subPrimaryColor)
                        customAlertDialog().apply {
                            setTitle(R.string.primary_sub_color)
                            setSingleChoiceItems(
                                colorsPrimary,
                                PrefManager.getVal(PrefName.PrimaryColor)
                            ) {
                                PrefManager.setVal(PrefName.PrimaryColor, it)
                                updateSubPreview()
                            }
                            show()
                        }
                    },
                    isDialog = true,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.secondary_sub_outline_type_select,
                    icon = R.drawable.round_chat_bubble_outline_24,
                    onClick = {
                        val typesOutline = resources.getStringArray(R.array.outlineType)
                        customAlertDialog().apply {
                            setTitle(R.string.outline_type)
                            setSingleChoiceItems(
                                typesOutline,
                                PrefManager.getVal(PrefName.SubOutline)
                            ) {
                                PrefManager.setVal(PrefName.SubOutline, it)
                                updateSubPreview()
                            }
                            show()
                        }
                    },
                    isDialog = true,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.secondary_sub_color_select,
                    icon = R.drawable.round_color_lens_24,
                    onClick = {
                        val colorsSecondary = resources.getStringArray(R.array.subPrimaryColor).dropLast(1).toTypedArray()
                        customAlertDialog().apply {
                            setTitle(R.string.outline_sub_color)
                            setSingleChoiceItems(
                                colorsSecondary,
                                PrefManager.getVal(PrefName.SecondaryColor)
                            ) {
                                PrefManager.setVal(PrefName.SecondaryColor, it)
                                updateSubPreview()
                            }
                            show()
                        }
                    },
                    isDialog = true,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.sub_background_color_select,
                    icon = R.drawable.round_color_lens_24,
                    onClick = {
                        val colorsSubBackground = resources.getStringArray(R.array.subtitleColor)
                        customAlertDialog().apply {
                            setTitle(R.string.sub_background_color_select)
                            setSingleChoiceItems(
                                colorsSubBackground,
                                PrefManager.getVal(PrefName.SubBackground)
                            ) {
                                PrefManager.setVal(PrefName.SubBackground, it)
                                updateSubPreview()
                            }
                            show()
                        }
                    },
                    isDialog = true,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.sub_window_color_select,
                    descRes = R.string.sub_window_color_info,
                    icon = R.drawable.round_fullscreen_24,
                    onClick = {
                        val colorsSubWindow = resources.getStringArray(R.array.subtitleColor)
                        customAlertDialog().apply {
                            setTitle(R.string.sub_window_color_select)
                            setSingleChoiceItems(
                                colorsSubWindow,
                                PrefManager.getVal(PrefName.SubWindow)
                            ) {
                                PrefManager.setVal(PrefName.SubWindow, it)
                                updateSubPreview()
                            }
                            show()
                        }
                    },
                    isDialog = true,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.sub_alpha,
                    icon = R.drawable.round_disabled_visible_24,
                    valueFrom = 0f,
                    valueTo = 1f,
                    stepSize = 0.1f,
                    value = 1f - PrefManager.getVal<Float>(PrefName.SubAlpha),
                    slider = { value, _ ->
                        PrefManager.setVal(PrefName.SubAlpha, 1f - value)
                        updateSubPreview()
                    },
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.sub_padding_ratio,
                    descRes = R.string.sub_padding_ratio_desc,
                    icon = R.drawable.round_margin_24,
                    pref = PrefName.BottomPaddingRatio,
                    valueFrom = 0f,
                    valueTo = 1f,
                    stepSize = 0.1f,
                    isEnabled = !PrefManager.getVal<Boolean>(PrefName.SubDefaults),
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.sub_padding,
                    descRes = R.string.sub_padding_desc,
                    icon = R.drawable.round_margin_24,
                    pref = PrefName.SubBottomPadding,
                    valueFrom = 0f,
                    valueTo = 20f,
                    stepSize = 1f,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.sub_font_select,
                    icon = R.drawable.ic_format_text_24,
                    onClick = {
                        val fonts = resources.getStringArray(R.array.subtitleFont) +
                                fontCollection.keys.toTypedArray()

                        customAlertDialog().apply {
                            var font = PrefManager.getVal<Int>(PrefName.Font)
                            var name = PrefManager.getVal<String>(PrefName.Typeface)
                            val titleView = TextView(this@PlayerSettingsActivity).apply {
                                setText(R.string.subtitle_font)
                                requestTypeface(font, name) {
                                    typeface = getUserTypeface(font, name, ResourcesCompat.getFont(
                                        this@PlayerSettingsActivity,
                                        R.font.poppins_semi_bold
                                    ))
                                }
                                gravity = Gravity.CENTER
                                textSize = 16.toPx.toFloat()
                            }
                            setCustomTitle(titleView)
                            setSingleChoiceItems(fonts, font, false) { value ->
                                requestTypeface(value, fonts[value]) {
                                    getUserTypeface(value, fonts[value])?.let {
                                        titleView.typeface = it
                                        titleView.invalidate()
                                    }
                                }
                                font = value
                                name = fonts[value]
                            }
                            setPositiveButton(R.string.select_typeface) {
                                requestTypeface(font, name) {
                                    getUserTypeface(font, name)?.let {
                                        PrefManager.setVal(PrefName.Font, font)
                                        PrefManager.setVal(PrefName.Typeface, name)
                                        updateSubPreview()
                                    } ?: toast(R.string.font_invalid)
                                }
                            }
                            setNegativeButton(R.string.cancel)
                            show()
                        }
                    },
                    isDialog = true,
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                ),
                Settings(
                    type = ViewType.EDITTEXT,
                    nameRes = R.string.subtitle_font_size,
                    icon = R.drawable.round_text_fields_24,
                    defaultText = PrefManager.getVal<Int>(PrefName.FontSize).string,
                    onTextChange = { _, value ->
                        PrefManager.setVal(PrefName.FontSize, value?.toInt())
                        updateSubPreview()
                    },
                    isEnabled = !PrefManager.getVal<Boolean>(PrefName.SubDefaults),
                    isVisible =  PrefManager.getVal(PrefName.Subtitles)
                )
            )
        )
        binding.subtitlesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerSettingsActivity, LinearLayoutManager.VERTICAL, false)
        }

        binding.subtitleTest.addOnChangeListener(object : Xpandable.OnChangeListener {
            override fun onExpand() {
                updateSubPreview()
            }

            override fun onRetract() {}
        })
        updateSubPreview()

        // Tracking
        binding.trackingRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.ask_update_progress_anime,
                    descRes = R.string.ask_update_progress_info_ep,
                    icon = R.drawable.ic_anilist,
                    pref = PrefName.AskIndividualPlayer,
                    itemsDisabled = arrayOf(1)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.ask_update_progress_episode_zero,
                    descRes = R.string.ask_update_progress_info_zero,
                    icon = R.drawable.round_assist_walker_24,
                    pref = PrefName.ChapterZeroPlayer,
                    isEnabled = !PrefManager.getVal<Boolean>(PrefName.AskIndividualPlayer)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.ask_update_progress_hentai,
                    icon = R.drawable.round_no_adult_content_24,
                    isChecked = PrefManager.getVal(PrefName.UpdateForHPlayer),
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.UpdateForHPlayer, isChecked)
                        if (isChecked) toast(getString(R.string.very_bold))
                    }
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.watch_complete_percentage,
                    descRes = R.string.watch_complete_percentage_info,
                    icon = R.drawable.round_sync_24,
                    valueFrom = 0f,
                    valueTo = 100f,
                    stepSize = 1f,
                    value = (PrefManager.getVal<Float>(PrefName.WatchPercentage) * 100).roundToInt().toFloat(),
                    slider = { value, _ ->
                        PrefManager.setVal(PrefName.WatchPercentage, value / 100)
                    }
                )
            )
        )
        binding.trackingRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerSettingsActivity, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)
        }

        binding.advancedRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.min_buffer,
                    icon = R.drawable.round_video_stable_24,
                    valueFrom = 10f,
                    valueTo = 120f,
                    stepSize = 10f,
                    value = PrefManager.getVal<Float>(PrefName.MinBufferTime),
                    slider = { value, view ->
                        val max = min(value, PrefManager.getVal<Float>(PrefName.MaxBufferTime))
                        PrefManager.setVal(PrefName.MinBufferTime, max)
                        view.settingSlider.value = max
                    }
                ),
                Settings(
                    type = ViewType.SLIDER,
                    nameRes = R.string.max_buffer,
                    icon = R.drawable.round_video_stable_24,
                    valueFrom = 10f,
                    valueTo = 120.0f,
                    stepSize = 10f,
                    value = PrefManager.getVal<Float>(PrefName.MaxBufferTime),
                    slider = { value, view ->
                        val min = max(value, PrefManager.getVal<Float>(PrefName.MinBufferTime))
                        PrefManager.setVal(PrefName.MaxBufferTime, min)
                        view.settingSlider.value = min
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.exceed_cap,
                    descRes = R.string.exceed_cap_desc,
                    icon = R.drawable.round_airline_seat_recline_extra_24,
                    pref = PrefName.SettingsExceedCap
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.exo_analytics,
                    descRes = R.string.exo_analytics_desc,
                    icon = R.drawable.round_bug_report_24,
                    pref = PrefName.ExoAnalytics
                )
            )
        )
        binding.advancedRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerSettingsActivity, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)
        }
        binding.advancedLayout.isVisible = media == null

        // Other

        binding.discordRPC.isVisible = Discord.getSavedToken() && media != null
        binding.discordRPC.isChecked = ExoplayerView.discordRPC
        binding.discordRPC.setOnCheckedChangeListener { _, isChecked ->
            ExoplayerView.discordRPC = isChecked
        }
    }

    private fun updateSubPreview() {
        binding.subtitleTestWindow.run {
            alpha = PrefManager.getVal(PrefName.SubAlpha)
            setBackgroundColor(
                when (PrefManager.getVal<Int>(PrefName.SubWindow)) {
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
            )
        }
        binding.subtitleTestText.run {
            textSize = (PrefManager.getVal<Int>(PrefName.FontSize) * 0.5).toPx.toFloat()
            typeface = getUserTypeface(
                PrefManager.getVal<Int>(PrefName.Font),
                PrefManager.getVal<String>(PrefName.Typeface),
                ResourcesCompat.getFont(this.context, R.font.poppins_semi_bold)
            )
            setTextColor(
                when (PrefManager.getVal<Int>(PrefName.PrimaryColor)) {
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
            )
            setStrokeWidth(0F)
            setShadowWidth(0F)
            when (PrefManager.getVal<Int>(PrefName.SubOutline)) {
                0 -> {
                    // None
                }
                1 -> {
                    // Outline
                    setStrokeWidth(1F)
                    setStrokeColor(
                        when (PrefManager.getVal<Int>(PrefName.SecondaryColor)) {
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
                    )
                }
                2 -> {
                    // Drop Shadow
                    setShadowWidth(2F)
                    setShadowColor(
                        when (PrefManager.getVal<Int>(PrefName.SecondaryColor)) {
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
                    )
                }
                3 -> {
                    // Raised

                }
                4 -> {
                    // Depressed

                }
            }
            setBackgroundColor(
                when (PrefManager.getVal<Int>(PrefName.SubBackground)) {
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
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.playerSettingsContainer.withFlexibleMargin(newConfig)
    }
}
