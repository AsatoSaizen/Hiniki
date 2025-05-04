package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.databinding.ActivitySettingsThemeBinding
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.themes.ThemeManager
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import bit.himitsu.widget.onCompletedAction
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.color.SimpleColorDialog

class SettingsThemeFragment : Fragment(), SimpleDialog.OnDialogResultListener {
    private lateinit var binding: ActivitySettingsThemeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsThemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            themeSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.day_night,
                        cardDrawable = arrayOf(
                            R.drawable.round_brightness_7_24,
                            R.drawable.round_brightness_4_24,
                            R.drawable.round_brightness_auto_24
                        ),
                        onCardClick = arrayOf(
                            {
                                PrefManager.setVal(PrefName.DarkMode, 0)
                                requireActivity().recreate()
                            },
                            {
                                PrefManager.setVal(PrefName.DarkMode, 1)
                                val oledSwitch = settingsRecyclerView.findViewHolderForAdapterPosition(1)
                                        as SettingsAdapter.SettingsSwitchViewHolder
                                oledSwitch.binding.settingsButton.isChecked = false
                                PrefManager.setVal(PrefName.UseOLED, false)
                                requireActivity().recreate()
                            },
                            {
                                PrefManager.setVal(PrefName.DarkMode, 2)
                                requireActivity().recreate()
                            }
                        ),
                        selectedItem = PrefManager.getVal<Int>(PrefName.DarkMode)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.oled_theme_variant,
                        descRes = R.string.oled_theme_variant_desc,
                        icon = R.drawable.round_hdr_strong_24,
                        isChecked = PrefManager.getVal(PrefName.UseOLED),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.UseOLED, isChecked)
                            requireActivity().recreate()
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.use_material_you,
                        descRes = R.string.use_material_you_desc,
                        icon = R.drawable.round_wallpaper_24,
                        isChecked = PrefManager.getVal(PrefName.UseMaterialYou),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.UseMaterialYou, isChecked)
                            if (isChecked) PrefManager.setVal(PrefName.UseCustomTheme, false)
                            requireActivity().recreate()
                        },
                        isVisible = Version.isSnowCone
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.theme_use_media,
                        descRes = R.string.theme_use_media_desc,
                        icon = R.drawable.round_attractions_24,
                        isChecked = PrefManager.getVal(PrefName.UseSourceTheme),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.UseSourceTheme, isChecked)
                            requireActivity().recreate()
                        },
                        isVisible = Version.isSnowCone
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.theme_use_profile,
                        descRes = R.string.theme_use_profile_desc,
                        icon = R.drawable.ic_colorize_24,
                        isChecked = PrefManager.getVal(PrefName.UseProfileTheme),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.UseProfileTheme, isChecked)
                            requireActivity().recreate()
                        },
                        isVisible = Version.isSnowCone
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.use_custom_theme,
                        descRes = R.string.use_custom_theme_desc,
                        icon = R.drawable.ic_format_color_fill_24,
                        isChecked = PrefManager.getVal(PrefName.UseCustomTheme),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.UseCustomTheme, isChecked)
                            if (isChecked) PrefManager.setVal(PrefName.UseMaterialYou, false)
                            requireActivity().recreate()
                        },
                        isVisible = Version.isSnowCone
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.color_picker,
                        descRes = R.string.color_picker_desc,
                        icon = R.drawable.ic_palette,
                        onClick = {
                            val originalColor: Int = PrefManager.getVal(PrefName.CustomThemeInt)

                            class CustomColorDialog : SimpleColorDialog() {
                                override fun onPositiveButtonClick() {
                                    super.onPositiveButtonClick()
                                    requireActivity().recreate()
                                }
                            }

                            val tag = "colorPicker"
                            CustomColorDialog().title(R.string.custom_theme)
                                .colorPreset(originalColor)
                                .colors(settings, SimpleColorDialog.MATERIAL_COLOR_PALLET)
                                .allowCustom(true).showOutline(0x46000000).gridNumColumn(5)
                                .choiceMode(SimpleColorDialog.SINGLE_CHOICE).neg()
                                .show(settings, tag)
                        },
                        isVisible = Version.isSnowCone,
                        isDialog = true
                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
            }

            settings.model.getQuery().observe(viewLifecycleOwner) { query ->
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

            val prefTheme: String = PrefManager.getVal(PrefName.Theme)
            val themeText = prefTheme.substring(0, 1) + prefTheme.substring(1).lowercase()
            binding.themeSwitcher.setText(themeText)
            themeSwitcher.setOnItemClickListener { _, _, i, _ ->
                PrefManager.setVal(PrefName.Theme, ThemeManager.Companion.Theme.entries[i].theme)
                themeSwitcher.clearFocus()
                requireActivity().recreate()
            }
            themePicker.children.forEachIndexed { index, view ->
                view.setOnClickListener {
                    val theme = ThemeManager.Companion.Theme.entries[index].theme
                    PrefManager.setVal(PrefName.Theme, theme)
                    requireActivity().recreate()
                    val themeName = theme.substring(0, 1) + theme.substring(1).lowercase()
                    binding.themeSwitcher.setText(themeName)
                }
            }
        }
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (which == SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE) {
            if (dialogTag == "colorPicker") {
                val color = extras.getInt(SimpleColorDialog.COLOR)
                PrefManager.setVal(PrefName.CustomThemeInt, color)
                Logger.log("Custom Theme: $color")
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        binding.themeSwitcher.setAdapter(ArrayAdapter(
            requireContext(),
            R.layout.item_dropdown,
            ThemeManager.Companion.Theme.entries.map {
                it.theme.substring(0, 1) + it.theme.substring(1).lowercase()
            }
        ))
        binding.themePicker.requestFocus()

        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
        }
    }
}