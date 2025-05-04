package ani.himitsu.media.manga.mangareader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.connections.discord.Discord
import ani.himitsu.databinding.BottomSheetCurrentReaderSettingsBinding
import ani.himitsu.settings.CurrentReaderSettings
import ani.himitsu.settings.CurrentReaderSettings.Directions
import ani.himitsu.settings.CurrentReaderSettings.DualPageModes
import ani.himitsu.settings.CurrentReaderSettings.Layouts
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import bit.himitsu.os.Version

class ReaderSettingsDialogFragment(val isWebtoon: Boolean) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCurrentReaderSettingsBinding? = null
    private val binding by lazy { _binding!! }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCurrentReaderSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MangaReaderActivity
        val settings = activity.defaultSettings

        binding.mangaReaderRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
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
                            settings.layout = Layouts[0] ?: Layouts.CONTINUOUS
                            activity.applySettings()
                            binding.mangaReaderRecyclerView.adapter?.notifyItemChanged(9)
                        },
                        {
                            settings.layout = Layouts[1] ?: Layouts.CONTINUOUS
                            activity.applySettings()
                            binding.mangaReaderRecyclerView.adapter?.notifyItemChanged(9)
                        },
                        {
                            settings.layout = Layouts[2] ?: Layouts.CONTINUOUS
                            activity.applySettings()
                            binding.mangaReaderRecyclerView.adapter?.notifyItemChanged(9)
                        }
                    ),
                    selectedItem = settings.layout.ordinal,
                    stringArray = resources.getStringArray(R.array.manga_layouts),
                    defaultText =  resources.getStringArray(R.array.manga_layouts)
                        [settings.layout.ordinal],
                    isVisible = !isWebtoon
                ),
                Settings(
                    type = ViewType.SELECTOR,
                    nameRes = R.string.direction,
                    cardRotation = 90f * (settings.direction.ordinal),
                    cardDrawable = arrayOf(
                        R.drawable.round_swipe_up_alt_24
                    ),
                    onCardClick = arrayOf(
                        {
                            settings.direction = Directions[settings.direction.ordinal + 1]
                                    ?: Directions.TOP_TO_BOTTOM
                            val direction = binding.mangaReaderRecyclerView.findViewHolderForAdapterPosition(1)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            direction.binding.settingsCardLayout.rotation = 90f * (settings.direction.ordinal)
                            direction.binding.settingsValue.text =
                                resources.getStringArray(R.array.manga_directions)[settings.direction.ordinal]
                            activity.applySettings()
                        }
                    ),
                    defaultText =  resources.getStringArray(R.array.manga_directions)
                        [settings.direction.ordinal],
                    isVisible = !isWebtoon
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
                            settings.dualPageMode = DualPageModes[0] ?: DualPageModes.Automatic
                            val dualPage = binding.mangaReaderRecyclerView.findViewHolderForAdapterPosition(2)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            dualPage.binding.settingsValue.text = settings.dualPageMode.toString()
                            activity.applySettings()
                        },
                        {
                            settings.dualPageMode = DualPageModes[1] ?: DualPageModes.Automatic
                            val dualPage = binding.mangaReaderRecyclerView.findViewHolderForAdapterPosition(2)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            dualPage.binding.settingsValue.text = settings.dualPageMode.toString()
                            activity.applySettings()
                        },
                        {
                            settings.dualPageMode = DualPageModes[2] ?: DualPageModes.Automatic
                            val dualPage = binding.mangaReaderRecyclerView.findViewHolderForAdapterPosition(2)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            dualPage.binding.settingsValue.text = settings.dualPageMode.toString()
                            activity.applySettings()
                        }
                    ),
                    selectedItem = settings.dualPageMode.ordinal,
                    defaultText = settings.dualPageMode.toString(),
                    isVisible = !isWebtoon
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.discord_rpc,
                    icon = R.drawable.ic_discord,
                    isChecked = settings.discordRPC,
                    switch = { isChecked, _ ->
                        settings.discordRPC = isChecked
                        activity.applySettings()
                    },
                    isVisible = Discord.getSavedToken()
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.over_scroll,
                    icon = R.drawable.round_swipe_vertical_24,
                    isChecked = settings.overScrollMode,
                    switch = { isChecked, _ ->
                        settings.overScrollMode = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.true_colors,
                    descRes = R.string.true_colors_info,
                    icon = R.drawable.round_high_quality_24,
                    isChecked = settings.trueColors,
                    switch = { isChecked, _ ->
                        settings.trueColors = isChecked
                        activity.applySettings()
                    },
                    isEnabled = !settings.hardColors
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.hard_colors,
                    descRes = R.string.hard_colors_info,
                    icon = R.drawable.round_high_quality_24,
                    isChecked = settings.hardColors,
                    switch = { isChecked, _ ->
                        settings.hardColors = isChecked
                        activity.applySettings()
                    },
                    isVisible = Version.isOreo,
                    itemsDisabled = arrayOf(5)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.photo_negative,
                    icon = R.drawable.round_invert_colors_24,
                    isChecked = settings.photoNegative,
                    switch = { isChecked, _ ->
                        settings.photoNegative = isChecked
                        activity.applySettings()
                    },
                    isEnabled = !settings.autoNegative
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.auto_negative,
                    icon = R.drawable.round_monochrome_photos_24,
                    isChecked = settings.autoNegative,
                    switch = { isChecked, _ ->
                        settings.autoNegative = isChecked
                        activity.applySettings()
                    },
                    itemsDisabled = arrayOf(7)
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.image_rotation,
                    icon = R.drawable.round_screen_rotation_alt_24,
                    isChecked = settings.rotation,
                    switch = { isChecked, _ ->
                        settings.rotation = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.crop_borders,
                    icon = R.drawable.round_auto_awesome_24,
                    isChecked = settings.cropBorders,
                    switch = { isChecked, _ ->
                        settings.cropBorders = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.spaced_pages,
                    icon = R.drawable.round_space_bar_24,
                    isChecked = settings.padding,
                    switch = { isChecked, _ ->
                        settings.padding = isChecked
                        activity.applySettings()
                    },
                    isEnabled = settings.layout.ordinal != 0
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.hide_scroll_bar,
                    icon = R.drawable.round_no_scroll_bar,
                    isChecked = settings.hideScrollBar,
                    switch = { isChecked, _ ->
                        settings.hideScrollBar = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.hide_page_numbers,
                    icon = R.drawable.ic_page_numbering,
                    isChecked = settings.hidePageNumbers,
                    switch = { isChecked, _ ->
                        settings.hidePageNumbers = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.horizontal_scroll_bar,
                    icon = R.drawable.round_straighten_24,
                    isChecked = settings.horizontalScrollBar,
                    switch = { isChecked, _ ->
                        settings.horizontalScrollBar = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.keep_screen_on,
                    icon = R.drawable.round_screen_lock_portrait_24,
                    isChecked = settings.keepScreenOn,
                    switch = { isChecked, _ ->
                        settings.keepScreenOn = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.volume_buttons,
                    icon = R.drawable.round_touch_app_24,
                    isChecked = settings.volumeButtons,
                    switch = { isChecked, _ ->
                        settings.volumeButtons = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.wrap_images,
                    descRes = R.string.wrap_images_info,
                    icon = R.drawable.round_fullscreen_24,
                    isChecked = settings.wrapImages,
                    switch = { isChecked, _ ->
                        settings.wrapImages = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.reader_reset,
                    icon = R.drawable.round_settings_24,
                    onClick = {
                        activity.customAlertDialog().apply {
                            setTitle(R.string.reader_reset_confirm)
                            setPositiveButton(R.string.ok) {
                                restoreDefaults(settings)
                            }
                            setNegativeButton(R.string.cancel) { }
                            show()
                        }
                    },
                    isDialog = true
                )
            )
        )
        binding.mangaReaderRecyclerView.apply {
            layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        }
    }

    private fun restoreDefaults(settings: CurrentReaderSettings) {
        settings.direction = Directions[PrefManager.getVal(PrefName.Direction)]
            ?: Directions.TOP_TO_BOTTOM
        settings.layout = Layouts[PrefManager.getVal(PrefName.LayoutReader)]
            ?: Layouts.CONTINUOUS
        settings.dualPageMode = DualPageModes[PrefManager.getVal(PrefName.DualPageModeReader)]
            ?: DualPageModes.Automatic
        settings.overScrollMode= PrefManager.getVal(PrefName.OverScrollMode)
        settings.trueColors = PrefManager.getVal(PrefName.TrueColors)
        settings.rotation = PrefManager.getVal(PrefName.Rotation)
        settings.padding = PrefManager.getVal(PrefName.Padding)
        settings.pageTurn = PrefManager.getVal(PrefName.PageTurn)
        settings.hideScrollBar = PrefManager.getVal(PrefName.HideScrollBar)
        settings.hidePageNumbers = PrefManager.getVal(PrefName.HidePageNumbers)
        settings.horizontalScrollBar = PrefManager.getVal(PrefName.HorizontalScrollBar)
        settings.keepScreenOn = PrefManager.getVal(PrefName.KeepScreenOn)
        settings.volumeButtons = PrefManager.getVal(PrefName.VolumeButtonsReader)
        settings.wrapImages = PrefManager.getVal(PrefName.WrapImages)
        settings.cropBorders = PrefManager.getVal(PrefName.CropBorders)
        settings.cropBorderThreshold= PrefManager.getVal(PrefName.CropBorderThreshold)
        (requireActivity() as MangaReaderActivity).applySettings()
        dialog?.dismiss()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance(isWebtoon: Boolean) = ReaderSettingsDialogFragment(isWebtoon)
    }
}