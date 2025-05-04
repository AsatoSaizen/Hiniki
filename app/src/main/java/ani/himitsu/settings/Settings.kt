package ani.himitsu.settings

import android.widget.ArrayAdapter
import ani.himitsu.R
import ani.himitsu.databinding.ItemSettingsBinding
import ani.himitsu.databinding.ItemSettingsSliderBinding
import ani.himitsu.databinding.ItemSettingsSwitchBinding
import ani.himitsu.settings.saving.PrefName
import com.google.android.material.textfield.TextInputEditText

data class Settings(
    val type: ViewType,
    val name: String = "",
    val nameRes: Int? = null,
    val desc: String = "",
    val descRes: Int? = null,
    val icon: Int = R.drawable.round_settings_24,
    val cardRotation: Float = 0f,
    val cardDrawable: Array<Int> = arrayOf(),
    val labelLeft: String? = null,
    val labelRight: String? = null,
    val pref: PrefName? = null,
    val adapter: ArrayAdapter<String>? = null,
    val onClick: ((ItemSettingsBinding) -> Unit)? = null,
    val onWebClick: ((ItemSettingsBinding) -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
    val switch: ((isChecked: Boolean, view: ItemSettingsSwitchBinding) -> Unit)? = null,
    val slider: ((value: Float, view: ItemSettingsSliderBinding) -> Unit)? = null,
    val onCount: ((value: Float) -> Unit)? = null,
    val onTextChange: ((view: TextInputEditText, value: String?) -> Unit)? = null,
    val onItemClick: ((value: Int) -> Unit)? = null,
    val onCardClick: Array<(() -> Unit)?> = arrayOf(),
    val attach: ((ItemSettingsBinding) -> Unit)? = null,
    val attachToSwitch: ((ItemSettingsSwitchBinding) -> Unit)? = null,
    val attachToSlider: ((ItemSettingsSliderBinding) -> Unit)? = null,
    var isChecked: Boolean = false,
    val valueFrom: Float = 0f,
    val valueTo: Float = 0f,
    val stepSize: Float = 0f,
    val value: Float = valueFrom,
    val defaultValue: Float = 0f,
    val defaultText: String? = null,
    val stringArray: Array<String>? = null,
    val selectedItem: Int = 0,
    val isActivity: Boolean = false,
    val isDialog: Boolean = false,
    val hasTransition: Boolean = false,
    var isEnabled: Boolean = true,
    var isVisible: Boolean = true,
    val itemsEnabled: Array<Int> = arrayOf(),
    val itemsDisabled: Array<Int> = arrayOf(),
    val itemsShown: Array<Int> = arrayOf()
)
enum class ViewType {
    BUTTON,
    SWITCH,
    SLIDER,
    COUNTER,
    EDITTEXT,
    DROPDOWN,
    SELECTOR,
    PROGRESS,
    HEADER
}

enum class Page {
    MAIN,
    UI,
    THEME,
    COMMON,
    ANIME,
    MANGA,
    EXTENSION,
    ADDON,
    NOTIFICATION,
    SYSTEM,
    ABOUT
}

const val START_PAGE = "fragment"
const val SILENT_EXIT = "silentExit"