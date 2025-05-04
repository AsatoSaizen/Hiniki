package ani.himitsu.media.novel.novelreader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.databinding.BottomSheetCurrentNovelReaderSettingsBinding
import ani.himitsu.settings.CurrentNovelReaderSettings
import ani.himitsu.settings.CurrentReaderSettings
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.view.NoPaddingArrayAdapter
import ani.himitsu.view.addViewObserver
import ani.himitsu.view.dialog.BottomSheetDialogFragment

class NovelReaderSettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCurrentNovelReaderSettingsBinding? = null
    private val binding by lazy { _binding!! }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCurrentNovelReaderSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as NovelReaderActivity
        val settings = activity.defaultSettings
        val themeLabels = activity.themes.map { it.name }
        binding.themeSelect.adapter =
            NoPaddingArrayAdapter(activity, R.layout.item_dropdown, themeLabels)
        binding.themeLayout.addViewObserver { binding.themeSelect.dropDownWidth = it.width / 2 }
        binding.themeSelect.setSelection(themeLabels.indexOfFirst { it == settings.currentThemeName })
        binding.themeSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                settings.currentThemeName = themeLabels[position]
                activity.applySettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.novelReaderRecyclerView.adapter = SettingsAdapter(
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
                            settings.layout = CurrentNovelReaderSettings.Layouts[0]
                                ?: CurrentNovelReaderSettings.Layouts.PAGED
                            val layout = binding.novelReaderRecyclerView.findViewHolderForAdapterPosition(0)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            layout.binding.settingsValue.text = settings.layout.string
                            activity.applySettings()
                        },
                        {
                            settings.layout = CurrentNovelReaderSettings.Layouts[1]
                                ?: CurrentNovelReaderSettings.Layouts.PAGED
                            val layout = binding.novelReaderRecyclerView.findViewHolderForAdapterPosition(0)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            layout.binding.settingsValue.text = settings.layout.string
                            activity.applySettings()
                        }
                    ),
                    selectedItem = settings.layout.ordinal,
                    defaultText =  settings.layout.string
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
                            settings.dualPageMode = CurrentReaderSettings.DualPageModes[0]
                                ?: CurrentReaderSettings.DualPageModes.Automatic
                            val dualPage = binding.novelReaderRecyclerView.findViewHolderForAdapterPosition(1)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            dualPage.binding.settingsValue.text = settings.dualPageMode.toString()
                            activity.applySettings()
                        },
                        {
                            settings.dualPageMode = CurrentReaderSettings.DualPageModes[1]
                                ?: CurrentReaderSettings.DualPageModes.Automatic
                            val dualPage = binding.novelReaderRecyclerView.findViewHolderForAdapterPosition(1)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            dualPage.binding.settingsValue.text = settings.dualPageMode.toString()
                            activity.applySettings()
                        },
                        {
                            settings.dualPageMode = CurrentReaderSettings.DualPageModes[2]
                                ?: CurrentReaderSettings.DualPageModes.Automatic
                            val dualPage = binding.novelReaderRecyclerView.findViewHolderForAdapterPosition(1)
                                    as SettingsAdapter.SettingsSelectorViewHolder
                            dualPage.binding.settingsValue.text = settings.dualPageMode.toString()
                            activity.applySettings()
                        }
                    ),
                    selectedItem = settings.dualPageMode.ordinal,
                    defaultText = settings.dualPageMode.toString()
                ),
                Settings(
                    type = ViewType.COUNTER,
                    nameRes = R.string.line_height,
                    value = settings.lineHeight.toFloat(),
                    stepSize = 0.1f,
                    defaultValue = 1.4f,
                    onCount = { value ->
                        settings.lineHeight = value
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.COUNTER,
                    nameRes = R.string.margin,
                    value = settings.margin.toFloat(),
                    stepSize = 0.01f,
                    defaultValue = 0.66f,
                    onCount = { value ->
                        settings.margin = value
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.COUNTER,
                    nameRes = R.string.maximum_inline_size,
                    descRes = R.string.maximum_column_width,
                    value = settings.maxInlineSize.toFloat(),
                    stepSize = 10f,
                    defaultValue = 720f,
                    onCount = { value ->
                        settings.maxInlineSize = value.toInt()
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.COUNTER,
                    nameRes = R.string.maximum_block_size,
                    descRes = R.string.maximum_height,
                    value = settings.maxBlockSize.toFloat(),
                    stepSize = 10f,
                    defaultValue = 720f,
                    onCount = { value ->
                        settings.maxBlockSize = value.toInt()
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.use_dark_theme,
                    icon = R.drawable.round_brightness_4_24,
                    isChecked = settings.useDarkTheme,
                    switch = { isChecked, _ ->
                        settings.useDarkTheme = isChecked
                        activity.applySettings()
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.use_oled_theme,
                    icon = R.drawable.round_brightness_4_24,
                    isChecked = settings.useOledTheme,
                    switch = { isChecked, _ ->
                        settings.useOledTheme = isChecked
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
                )
            )
        )
        binding.novelReaderRecyclerView.apply {
            layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)
        }
    }


    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }


    companion object {
        fun newInstance() = NovelReaderSettingsDialogFragment()
        const val TAG = "NovelReaderSettingsDialogFragment"
    }
}