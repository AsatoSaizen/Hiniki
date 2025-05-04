package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.databinding.ActivitySettingsMangaBinding
import ani.himitsu.download.DownloadsManager
import ani.himitsu.media.MediaType
import ani.himitsu.settings.CurrentNovelReaderSettings
import ani.himitsu.settings.CurrentReaderSettings
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.util.customAlertDialog
import bit.himitsu.os.Version
import bit.himitsu.widget.onCompletedAction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsMangaFragment : Fragment() {
    private lateinit var binding: ActivitySettingsMangaBinding
    private var defaultSettings = CurrentReaderSettings()
    private var defaultSettingsLN = CurrentNovelReaderSettings()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsMangaBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            mangaSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            // Default Manga

            val settingsMangaAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.default_chp_view,
                        cardDrawable = arrayOf(
                            R.drawable.round_view_list_24,
                            R.drawable.round_view_comfy_24
                        ),
                        onCardClick = arrayOf(
                            { PrefManager.setVal(PrefName.MangaDefaultView, 0) },
                            { PrefManager.setVal(PrefName.MangaDefaultView, 1) }
                        ),
                        selectedItem = PrefManager.getVal<Int>(PrefName.MangaDefaultView)
                    ),
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.layout,
                        cardDrawable = arrayOf(
                            R.drawable.ic_round_amp_stories_24,
                            R.drawable.round_view_array_24,
                            R.drawable.round_view_column_24
                        ),
                        onCardClick = arrayOf(
                            {
                                defaultSettings.layout = CurrentReaderSettings.Layouts[0]
                                    ?: CurrentReaderSettings.Layouts.CONTINUOUS
                                PrefManager.setVal(PrefName.LayoutReader, 0)
                            },
                            {
                                defaultSettings.layout = CurrentReaderSettings.Layouts[1]
                                    ?: CurrentReaderSettings.Layouts.CONTINUOUS
                                PrefManager.setVal(PrefName.LayoutReader, 1)
                            },
                            {
                                defaultSettings.layout = CurrentReaderSettings.Layouts[2]
                                    ?: CurrentReaderSettings.Layouts.CONTINUOUS
                                PrefManager.setVal(PrefName.LayoutReader, 2)
                            }
                        ),
                        selectedItem = defaultSettings.layout.ordinal,
                        stringArray = resources.getStringArray(R.array.manga_layouts),
                        defaultText =  resources.getStringArray(R.array.manga_layouts)
                            [defaultSettings.layout.ordinal]
                    ),
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.direction,
                        cardDrawable = arrayOf(
                            R.drawable.round_swipe_up_alt_24
                        ),
                        cardRotation = 90f * (defaultSettings.direction.ordinal),
                        onCardClick = arrayOf(
                            {
                                defaultSettings.direction =
                                    CurrentReaderSettings.Directions[defaultSettings.direction.ordinal + 1]
                                        ?: CurrentReaderSettings.Directions.TOP_TO_BOTTOM
                                val direction = mangaReaderRecyclerView.findViewHolderForAdapterPosition(2)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                direction.binding.settingsCardLayout.rotation = 90f * (defaultSettings.direction.ordinal)
                                direction.binding.settingsValue.text =
                                    resources.getStringArray(R.array.manga_directions)[defaultSettings.direction.ordinal]
                                PrefManager.setVal(PrefName.Direction, defaultSettings.direction.ordinal)
                            }
                        ),
                        defaultText =  resources.getStringArray(R.array.manga_directions)
                            [defaultSettings.direction.ordinal],
                    ),
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.dual_page,
                        descRes = R.string.dual_page_info,
                        cardDrawable = arrayOf(
                            R.drawable.round_close_24,
                            R.drawable.round_screen_rotation_24,
                            R.drawable.round_menu_book_24
                        ),
                        onCardClick = arrayOf(
                            {
                                defaultSettings.dualPageMode = CurrentReaderSettings.DualPageModes[0]
                                    ?: CurrentReaderSettings.DualPageModes.Automatic
                                val dualPage = mangaReaderRecyclerView.findViewHolderForAdapterPosition(3)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                dualPage.binding.settingsValue.text = defaultSettings.dualPageMode.toString()
                                PrefManager.setVal(PrefName.DualPageModeReader, 0)
                            },
                            {
                                defaultSettings.dualPageMode = CurrentReaderSettings.DualPageModes[1]
                                    ?: CurrentReaderSettings.DualPageModes.Automatic
                                val dualPage = mangaReaderRecyclerView.findViewHolderForAdapterPosition(3)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                dualPage.binding.settingsValue.text = defaultSettings.dualPageMode.toString()
                                PrefManager.setVal(PrefName.DualPageModeReader, 1)
                            },
                            {
                                defaultSettings.dualPageMode = CurrentReaderSettings.DualPageModes[2]
                                    ?: CurrentReaderSettings.DualPageModes.Automatic
                                val dualPage = mangaReaderRecyclerView.findViewHolderForAdapterPosition(3)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                dualPage.binding.settingsValue.text = defaultSettings.dualPageMode.toString()
                                PrefManager.setVal(PrefName.DualPageModeReader, 2)
                            }
                        ),
                        selectedItem = defaultSettings.dualPageMode.ordinal,
                        defaultText = defaultSettings.dualPageMode.toString()
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.source_info,
                        icon = R.drawable.round_info_outline_24,
                        pref = PrefName.ShowSource
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.auto_detect_webtoon,
                        descRes = R.string.auto_detect_webtoon_info,
                        icon = R.drawable.round_find_in_page_24,
                        pref = PrefName.AutoDetectWebtoon
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.over_scroll,
                        icon = R.drawable.round_swipe_vertical_24,
                        isChecked = defaultSettings.overScrollMode,
                        switch = { isChecked, _ ->
                            defaultSettings.overScrollMode = isChecked
                            PrefManager.setVal(PrefName.OverScrollMode, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.image_long_clicking,
                        icon = R.drawable.round_touch_app_24,
                        pref = PrefName.LongClickImage
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.true_colors,
                        descRes = R.string.true_colors_info,
                        icon = R.drawable.round_high_quality_24,
                        isChecked = defaultSettings.trueColors,
                        switch = { isChecked, _ ->
                            defaultSettings.trueColors = isChecked
                            PrefManager.setVal(PrefName.TrueColors, isChecked)
                        },
                        isEnabled = !defaultSettings.hardColors
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.hard_colors,
                        descRes = R.string.hard_colors_info,
                        icon = R.drawable.round_high_quality_24,
                        isChecked = defaultSettings.hardColors,
                        switch = { isChecked, _ ->
                            defaultSettings.hardColors = isChecked
                            PrefManager.setVal(PrefName.HardColors, isChecked)
                        },
                        isVisible = Version.isOreo,
                        itemsDisabled = arrayOf(8)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.photo_negative,
                        icon = R.drawable.round_invert_colors_24,
                        isChecked = defaultSettings.photoNegative,
                        switch = { isChecked, _ ->
                            defaultSettings.photoNegative = isChecked
                            PrefManager.setVal(PrefName.PhotoNegative, isChecked)
                        },
                        isEnabled = !defaultSettings.autoNegative
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.auto_negative,
                        icon = R.drawable.round_monochrome_photos_24,
                        isChecked = defaultSettings.autoNegative,
                        switch = { isChecked, _ ->
                            defaultSettings.autoNegative = isChecked
                            PrefManager.setVal(PrefName.AutoNegative, isChecked)
                        },
                        itemsDisabled = arrayOf(10)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.image_rotation,
                        icon = R.drawable.round_screen_rotation_alt_24,
                        isChecked = defaultSettings.rotation,
                        switch = { isChecked, _ ->
                            defaultSettings.rotation = isChecked
                            PrefManager.setVal(PrefName.Rotation, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.crop_borders,
                        icon = R.drawable.round_auto_awesome_24,
                        isChecked = defaultSettings.cropBorders,
                        switch = { isChecked, _ ->
                            defaultSettings.cropBorders = isChecked
                            PrefManager.setVal(PrefName.CropBorders, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.spaced_pages,
                        icon = R.drawable.round_space_bar_24,
                        isChecked = defaultSettings.padding,
                        switch = { isChecked, _ ->
                            defaultSettings.padding = isChecked
                            PrefManager.setVal(PrefName.Padding, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.page_turning,
                        icon = R.drawable.round_auto_stories_24,
                        isChecked = defaultSettings.pageTurn,
                        switch = { isChecked, _ ->
                            defaultSettings.pageTurn = isChecked
                            PrefManager.setVal(PrefName.PageTurn, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.hide_scroll_bar,
                        icon = R.drawable.round_no_scroll_bar,
                        isChecked = defaultSettings.hideScrollBar,
                        switch = { isChecked, _ ->
                            defaultSettings.hideScrollBar = isChecked
                            PrefManager.setVal(PrefName.HideScrollBar, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.hide_page_numbers,
                        icon = R.drawable.ic_page_numbering,
                        isChecked = defaultSettings.hidePageNumbers,
                        switch = { isChecked, _ ->
                            defaultSettings.hidePageNumbers = isChecked
                            PrefManager.setVal(PrefName.HidePageNumbers, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.horizontal_scroll_bar,
                        icon = R.drawable.round_straighten_24,
                        isChecked = defaultSettings.horizontalScrollBar,
                        switch = { isChecked, _ ->
                            defaultSettings.horizontalScrollBar = isChecked
                            PrefManager.setVal(PrefName.HorizontalScrollBar, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.keep_screen_on,
                        icon = R.drawable.round_screen_lock_portrait_24,
                        isChecked = defaultSettings.keepScreenOn,
                        switch = { isChecked, _ ->
                            defaultSettings.keepScreenOn = isChecked
                            PrefManager.setVal(PrefName.KeepScreenOn, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.volume_buttons,
                        icon = R.drawable.round_touch_app_24,
                        isChecked = defaultSettings.volumeButtons,
                        switch = { isChecked, _ ->
                            defaultSettings.volumeButtons = isChecked
                            PrefManager.setVal(PrefName.VolumeButtonsReader, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.wrap_images,
                        descRes = R.string.wrap_images_info,
                        icon = R.drawable.round_fullscreen_24,
                        isChecked = defaultSettings.wrapImages,
                        switch = { isChecked, _ ->
                            defaultSettings.wrapImages = isChecked
                            PrefManager.setVal(PrefName.WrapImages, isChecked)
                        }
                    )
                )
            )
            mangaReaderRecyclerView.apply {
                adapter = settingsMangaAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            // LN settings

            val settingsNovelAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.layout,
                        cardDrawable = arrayOf(
                            R.drawable.ic_round_amp_stories_24,
                            R.drawable.round_view_column_24
                        ),
                        onCardClick = arrayOf(
                            {
                                defaultSettingsLN.layout = CurrentNovelReaderSettings.Layouts[0]
                                    ?: CurrentNovelReaderSettings.Layouts.PAGED
                                val layout = novelReaderRecyclerView.findViewHolderForAdapterPosition(0)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                layout.binding.settingsValue.text = defaultSettingsLN.layout.string
                                PrefManager.setVal(PrefName.LayoutNovel, 0)
                            },
                            {
                                defaultSettingsLN.layout = CurrentNovelReaderSettings.Layouts[1]
                                    ?: CurrentNovelReaderSettings.Layouts.PAGED
                                val layout = novelReaderRecyclerView.findViewHolderForAdapterPosition(0)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                layout.binding.settingsValue.text = defaultSettingsLN.layout.string
                                PrefManager.setVal(PrefName.LayoutNovel, 1)
                            }
                        ),
                        selectedItem = defaultSettingsLN.layout.ordinal,
                        defaultText =  defaultSettingsLN.layout.string
                    ),
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.dual_page,
                        descRes = R.string.dual_page_info,
                        cardDrawable = arrayOf(
                            R.drawable.round_close_24,
                            R.drawable.round_screen_rotation_24,
                            R.drawable.round_menu_book_24
                        ),
                        onCardClick = arrayOf(
                            {
                                defaultSettingsLN.dualPageMode = CurrentReaderSettings.DualPageModes[0]
                                    ?: CurrentReaderSettings.DualPageModes.Automatic
                                val dualPage = novelReaderRecyclerView.findViewHolderForAdapterPosition(1)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                dualPage.binding.settingsValue.text = defaultSettingsLN.dualPageMode.toString()
                                PrefManager.setVal(PrefName.DualPageModeNovel, 0)
                            },
                            {
                                defaultSettingsLN.dualPageMode = CurrentReaderSettings.DualPageModes[1]
                                    ?: CurrentReaderSettings.DualPageModes.Automatic
                                val dualPage = novelReaderRecyclerView.findViewHolderForAdapterPosition(1)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                dualPage.binding.settingsValue.text = defaultSettingsLN.dualPageMode.toString()
                                PrefManager.setVal(PrefName.DualPageModeNovel, 1)
                            },
                            {
                                defaultSettingsLN.dualPageMode = CurrentReaderSettings.DualPageModes[2]
                                    ?: CurrentReaderSettings.DualPageModes.Automatic
                                val dualPage = novelReaderRecyclerView.findViewHolderForAdapterPosition(1)
                                        as SettingsAdapter.SettingsSelectorViewHolder
                                dualPage.binding.settingsValue.text = defaultSettingsLN.dualPageMode.toString()
                                PrefManager.setVal(PrefName.DualPageModeNovel, 2)
                            }
                        ),
                        selectedItem = defaultSettingsLN.dualPageMode.ordinal,
                        defaultText = defaultSettingsLN.dualPageMode.toString()
                    ),
                    Settings(
                        type = ViewType.COUNTER,
                        nameRes = R.string.line_height,
                        icon = R.drawable.round_text_fields_24,
                        value = defaultSettingsLN.lineHeight.toFloat(),
                        stepSize = 0.1f,
                        defaultValue = 1.4f,
                        onCount = { value ->
                            defaultSettingsLN.lineHeight = value
                            PrefManager.setVal(PrefName.LineHeight, value)
                        }
                    ),
                    Settings(
                        type = ViewType.COUNTER,
                        nameRes = R.string.margin,
                        icon = R.drawable.round_text_fields_24,
                        value = defaultSettingsLN.margin.toFloat(),
                        stepSize = 0.01f,
                        defaultValue = 0.66f,
                        onCount = { value ->
                            defaultSettingsLN.margin = value
                            PrefManager.setVal(PrefName.Margin, value)
                        }
                    ),
                    Settings(
                        type = ViewType.COUNTER,
                        nameRes = R.string.maximum_inline_size,
                        descRes = R.string.maximum_column_width,
                        icon = R.drawable.round_text_fields_24,
                        value = defaultSettingsLN.maxInlineSize.toFloat(),
                        stepSize = 10f,
                        defaultValue = 720f,
                        onCount = { value ->
                            defaultSettingsLN.maxInlineSize = value.toInt()
                            PrefManager.setVal(PrefName.MaxInlineSize, value.toInt())
                        }
                    ),
                    Settings(
                        type = ViewType.COUNTER,
                        nameRes = R.string.maximum_block_size,
                        descRes = R.string.maximum_height,
                        icon = R.drawable.round_text_fields_24,
                        value = defaultSettingsLN.maxBlockSize.toFloat(),
                        stepSize = 10f,
                        defaultValue = 720f,
                        onCount = { value ->
                            defaultSettingsLN.maxBlockSize = value.toInt()
                            PrefManager.setVal(PrefName.MaxBlockSize, value.toInt())
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.use_dark_theme,
                        icon = R.drawable.round_brightness_4_24,
                        isChecked = defaultSettingsLN.useDarkTheme,
                        switch = { isChecked, _ ->
                            defaultSettingsLN.useDarkTheme = isChecked
                            PrefManager.setVal(PrefName.UseDarkThemeNovel, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.use_oled_theme,
                        icon = R.drawable.round_brightness_4_24,
                        isChecked = defaultSettingsLN.useOledTheme,
                        switch = { isChecked, _ ->
                            defaultSettingsLN.useOledTheme = isChecked
                            PrefManager.setVal(PrefName.UseOledThemeNovel, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.keep_screen_on,
                        icon = R.drawable.round_screen_lock_portrait_24,
                        isChecked = defaultSettingsLN.keepScreenOn,
                        switch = { isChecked, _ ->
                            defaultSettingsLN.keepScreenOn = isChecked
                            PrefManager.setVal(PrefName.KeepScreenOnNovel, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.volume_buttons,
                        icon = R.drawable.round_touch_app_24,
                        isChecked = defaultSettingsLN.volumeButtons,
                        switch = { isChecked, _ ->
                            defaultSettingsLN.volumeButtons = isChecked
                            PrefManager.setVal(PrefName.VolumeButtonsNovel, isChecked)
                        }
                    )
                )
            )
            novelReaderRecyclerView.apply {
                adapter = settingsNovelAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings (
                        type = ViewType.HEADER,
                        nameRes = R.string.general
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.include_list,
                        descRes = R.string.include_list_desc,
                        icon = R.drawable.view_list_24,
                        isChecked = PrefManager.getVal(PrefName.IncludeMangaList),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.IncludeMangaList, isChecked)
                            Refresh.all()
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.show_system_bars,
                        icon = R.drawable.round_smart_button_24,
                        pref = PrefName.ShowSystemBars
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.comic_format,
                        descRes = R.string.comic_format_desc,
                        icon = R.drawable.ic_menu_book_24,
                        pref = PrefName.ComicBookFormat
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.purge_manga_downloads,
                        icon = R.drawable.round_delete_sweep_24,
                        onClick = {
                            settings.customAlertDialog().apply {
                                setMessage(R.string.purge_confirm, getString(R.string.manga))
                                setPositiveButton(R.string.yes, onClick = {
                                    val downloadsManager = Injekt.get<DownloadsManager>()
                                    downloadsManager.purgeDownloads(MediaType.MANGA)
                                })
                                setNegativeButton(R.string.no)
                                show()
                            }
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.purge_novel_downloads,
                        icon = R.drawable.round_delete_sweep_24,
                        onClick = {
                            settings.customAlertDialog().apply {
                                setMessage(R.string.purge_confirm, getString(R.string.novels))
                                setPositiveButton(R.string.yes) {
                                    val downloadsManager = Injekt.get<DownloadsManager>()
                                    downloadsManager.purgeDownloads(MediaType.NOVEL)
                                }
                                setNegativeButton(R.string.no)
                                show()
                            }
                        },
                        isDialog = true
                    ),
                    Settings (
                        type = ViewType.HEADER,
                        nameRes = R.string.tracking
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.ask_update_progress_manga,
                        descRes = R.string.ask_update_progress_info_chap,
                        icon = R.drawable.ic_anilist,
                        pref = PrefName.AskIndividualReader,
                        itemsDisabled = arrayOf(8)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.ask_update_progress_chapter_zero,
                        descRes = R.string.ask_update_progress_info_zero,
                        icon = R.drawable.round_assist_walker_24,
                        pref = PrefName.ChapterZeroReader,
                        isEnabled = !PrefManager.getVal<Boolean>(PrefName.AskIndividualReader)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.ask_update_progress_doujin,
                        icon = R.drawable.round_no_adult_content_24,
                        isChecked = PrefManager.getVal(PrefName.UpdateForHReader),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.UpdateForHReader, isChecked)
                            if (isChecked) toast(getString(R.string.very_bold))
                        }
                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            settings.model.getQuery().observe(viewLifecycleOwner) { query ->
                settingsMangaAdapter.getFilter()?.filter(query)
                settingsNovelAdapter.getFilter()?.filter(query)
                settingsAdapter.getFilter()?.filter(query)
            }
            binding.searchViewText.setText(settings.model.getQuery().value)
            binding.searchViewText.setOnEditorActionListener(onCompletedAction {
                with (requireContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager) {
                    hideSoftInputFromWindow(binding.searchViewText.windowToken, 0)
                }
                settings.model.setQuery(binding.searchViewText.text?.toString())
            })
            binding.searchView.setEndIconOnClickListener {
                settings.model.setQuery(binding.searchViewText.text?.toString())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.mangaReaderRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
            (binding.novelReaderRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
        }
    }
}