

package ani.himitsu.settings

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Filter
import android.widget.Filterable
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.databinding.ItemSelectorCardviewBinding
import ani.himitsu.databinding.ItemSettingsBinding
import ani.himitsu.databinding.ItemSettingsCounterBinding
import ani.himitsu.databinding.ItemSettingsDropdownBinding
import ani.himitsu.databinding.ItemSettingsEntryBinding
import ani.himitsu.databinding.ItemSettingsHeaderBinding
import ani.himitsu.databinding.ItemSettingsProgressBinding
import ani.himitsu.databinding.ItemSettingsSelectorBinding
import ani.himitsu.databinding.ItemSettingsSliderBinding
import ani.himitsu.databinding.ItemSettingsSwitchBinding
import ani.himitsu.setAnimation
import ani.himitsu.settings.saving.PrefManager
import bit.himitsu.content.toPx
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.string
import bit.himitsu.widget.onCompletedAction
import java.util.Locale

class SettingsAdapter(private val settings: ArrayList<Settings>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {
    inner class SettingsViewHolder(val binding: ItemSettingsBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsSwitchViewHolder(val binding: ItemSettingsSwitchBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsSliderViewHolder(val binding: ItemSettingsSliderBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsCounterViewHolder(val binding: ItemSettingsCounterBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsEntryViewHolder(val binding: ItemSettingsEntryBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsDropdownViewHolder(val binding: ItemSettingsDropdownBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsSelectorViewHolder(val binding: ItemSettingsSelectorBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsProgressViewHolder(val binding: ItemSettingsProgressBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingsHeaderViewHolder(val binding: ItemSettingsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    private var filteredData: ArrayList<Settings> = arrayListOf()
    private var filter: SettingsFilter? = null

    init {
        filteredData = settings
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ViewType.BUTTON.ordinal -> SettingsViewHolder(
                ItemSettingsBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.SWITCH.ordinal -> SettingsSwitchViewHolder(
                ItemSettingsSwitchBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.SLIDER.ordinal -> SettingsSliderViewHolder(
                ItemSettingsSliderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.COUNTER.ordinal -> SettingsCounterViewHolder(
                ItemSettingsCounterBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.EDITTEXT.ordinal -> SettingsEntryViewHolder(
                ItemSettingsEntryBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.DROPDOWN.ordinal -> SettingsDropdownViewHolder(
                ItemSettingsDropdownBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.SELECTOR.ordinal -> SettingsSelectorViewHolder(
                ItemSettingsSelectorBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.PROGRESS.ordinal -> SettingsProgressViewHolder(
                ItemSettingsProgressBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            ViewType.HEADER.ordinal -> SettingsHeaderViewHolder(
                ItemSettingsHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            else -> SettingsViewHolder(
                ItemSettingsBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val setting = filteredData[position]
        when (setting.type) {
            ViewType.BUTTON -> {
                val b = (holder as SettingsViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsTitle.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name
                b.settingsTitle.isVisible = !b.settingsTitle.text.isNullOrBlank()
                b.settingsDesc.text = setting.descRes?.let {
                    b.root.context.getString(it)
                } ?: setting.desc
                b.settingsDesc.isVisible = !b.settingsDesc.text.isNullOrBlank()
                b.settingsIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        b.root.context, setting.icon
                    )
                )
                b.settingsLayout.setOnClickListener { view ->
                    setting.onClick?.invoke(b)
                }
                b.settingsLayout.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    setting.onLongClick?.invoke()
                    true
                }
                b.settingsLayout.isVisible = setting.isVisible
                b.settingsLayout.isEnabled = setting.isEnabled
                b.settingsIconRight.isVisible = setting.isActivity
                        || setting.isDialog || setting.hasTransition
                b.attachView.isVisible = setting.attach != null
                setting.attach?.invoke(b)
            }

            ViewType.SWITCH -> {
                val b = (holder as SettingsSwitchViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsButton.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name
                b.settingsDesc.text = setting.descRes?.let {
                    b.root.context.getString(it)
                } ?: setting.desc
                b.settingsDesc.isVisible = !b.settingsDesc.text.isNullOrBlank()
                b.settingsIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        b.root.context, setting.icon
                    )
                )
                b.settingsButton.isChecked = setting.pref?.let {
                        PrefManager.getVal<Any>(it).takeIf { it is Boolean } as? Boolean == true
                } ?: setting.isChecked
                b.settingsButton.setOnCheckedChangeListener { view, isChecked ->
                    setting.switch?.invoke(isChecked, b) ?: setting.pref?.let {
                        PrefManager.setVal(it, isChecked)
                    }
                    view.postDelayed({
                        setting.itemsEnabled.forEach {
                            setItemEnabled(it, isChecked)
                        }
                        setting.itemsDisabled.forEach {
                            setItemEnabled(it, !isChecked)
                        }
                        setting.itemsShown.forEach {
                            setItemVisibility(it, isChecked)
                        }
                    }, 250)
                }
                b.settingsLayout.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    setting.onLongClick?.invoke()
                    true
                }
                b.settingsLayout.isVisible = setting.isVisible
                b.settingsLayout.isEnabled = setting.isEnabled
                b.settingsButton.isEnabled = setting.isEnabled
                setting.attachToSwitch?.invoke(b)
            }

            ViewType.SLIDER -> {
                val b = (holder as SettingsSliderViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsButton.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name
                b.settingsDesc.text = setting.descRes?.let {
                    b.root.context.getString(it)
                } ?: setting.desc
                b.settingsDesc.isVisible = !b.settingsDesc.text.isNullOrBlank()
                b.settingsIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        b.root.context, setting.icon
                    )
                )
                b.settingSlider.valueFrom = setting.valueFrom
                b.settingSlider.valueTo = setting.valueTo
                b.settingSlider.stepSize = setting.stepSize
                clamp(setting.pref?.let {
                    PrefManager.getVal<Any>(it).takeIf { it is Float } as Float
                } ?: setting.value, setting.valueFrom, setting.valueTo).let { clamped ->
                    b.settingSlider.value = clamped
                }
                if (setting.labelLeft != null || setting.labelRight != null) {
                    b.settingsLabelLayout.isVisible = true
                    b.settingsLabelLeft.text = setting.labelLeft
                    b.settingsLabelRight.text = setting.labelRight
                }
                b.settingSlider.addOnChangeListener { view, value, fromUser ->
                    if (fromUser) clamp(value, setting.valueFrom, setting.valueTo).let { clamped ->
                        setting.slider?.invoke(clamped, b) ?: setting.pref?.let {
                            PrefManager.setVal(it, clamped)
                        }
                    }
                }
                b.settingsLayout.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    setting.onLongClick?.invoke()
                    true
                }
                b.settingsLayout.isVisible = setting.isVisible
                b.settingsLayout.isEnabled = setting.isEnabled
                b.settingSlider.isEnabled = setting.isEnabled
                setting.attachToSlider?.invoke(b)
            }

            ViewType.COUNTER -> {
                val b = (holder as SettingsCounterViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsButton.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name
                b.settingsDesc.text = setting.descRes?.let {
                    b.root.context.getString(it)
                } ?: setting.desc
                b.settingsDesc.isVisible = !b.settingsDesc.text.isNullOrBlank()
                b.settingsIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        b.root.context, setting.icon
                    )
                )
                b.settingsValue.setText(setting.value.string)
                b.settingsMinus.setOnClickListener {
                    b.settingsValue.text.toString().toFloatOrNull()?.let {
                        val newValue = it - setting.stepSize
                        b.settingsValue.setText(newValue.string)
                        setting.onCount?.invoke(newValue)
                    }
                }
                b.settingsPlus.setOnClickListener {
                    b.settingsValue.text.toString().toFloatOrNull()?.let {
                        val newValue = it + setting.stepSize
                        b.settingsValue.setText(newValue.string)
                        setting.onCount?.invoke(newValue)
                    }
                }
                b.settingsValue.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        setting.onCount?.invoke(
                            b.settingsValue.text.toString().toFloatOrNull() ?: setting.defaultValue
                        )
                    }
                }
                b.settingsLayout.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    setting.onLongClick?.invoke()
                    true
                }
                b.settingsLayout.isVisible = setting.isVisible
                b.settingsLayout.isEnabled = setting.isEnabled
                b.settingsPlus.isEnabled = setting.isEnabled
                b.settingsMinus.isEnabled = setting.isEnabled
                b.settingsValue.isEnabled = setting.isEnabled
            }

            ViewType.EDITTEXT -> {
                val b = (holder as SettingsEntryViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsButton.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name
                b.settingsDesc.text = setting.descRes?.let {
                    b.root.context.getString(it)
                } ?: setting.desc
                b.settingsDesc.isVisible = !b.settingsDesc.text.isNullOrBlank()
                b.settingsIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        b.root.context, setting.icon
                    )
                )
                b.settingsValue.setText(setting.defaultText)
                b.settingsValue.setOnEditorActionListener(onCompletedAction {
                    setting.onTextChange?.invoke(b.settingsValue, b.settingsValue.text?.toString())
                })
                b.settingsLayout.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    setting.onLongClick?.invoke()
                    true
                }
                b.settingsLayout.isVisible = setting.isVisible
                b.settingsLayout.isEnabled = setting.isEnabled
                b.settingsValue.isEnabled = setting.isEnabled
            }

            ViewType.DROPDOWN -> {
                val b = (holder as SettingsDropdownViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsButton.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name

                b.settingsInputLayout.startIconDrawable = ContextCompat.getDrawable(
                    b.root.context, setting.icon
                )

                b.settingsValue.setAdapter(setting.adapter)
                b.settingsValue.setText(setting.defaultText, false)
                b.settingsValue.setOnItemClickListener { _, _, item, _ ->
                    setting.onItemClick?.invoke(item)
                    b.settingsValue.clearFocus()
                }


                b.settingsLayout.isVisible = setting.isVisible
                b.settingsLayout.isEnabled = setting.isEnabled
                b.settingsInputLayout.isEnabled = setting.isEnabled
            }

            ViewType.SELECTOR -> {
                val b = (holder as SettingsSelectorViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsButton.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name
                b.settingsDesc.text = setting.descRes?.let {
                    b.root.context.getString(it)
                } ?: setting.desc
                b.settingsDesc.isVisible = !b.settingsDesc.text.isNullOrBlank()

                b.settingsValue.text = setting.defaultText
                b.settingsValue.isVisible = !b.settingsValue.text.isNullOrBlank()

                val views = arrayListOf<ItemSelectorCardviewBinding>()
                b.settingsCardLayout.removeAllViews()
                setting.onCardClick.forEachIndexed { index, onClick ->
                    views.add(
                        ItemSelectorCardviewBinding.inflate(
                            LayoutInflater.from(b.settingsCardLayout.context),
                            b.settingsCardLayout,
                            true
                        ).apply {
                            settingsCard.alpha = if (setting.onCardClick.size == 1
                                || index == setting.selectedItem) 1f else 0.33f
                            settingsCard.setImageDrawable(ContextCompat.getDrawable(
                                b.root.context, setting.cardDrawable[index]
                            ))
                            settingsCard.setOnClickListener {
                                onClick?.invoke()
                                if (views.size > 1) {
                                    views.forEachIndexed { i, v ->
                                        v.settingsCard.alpha = if (i == index) 1f else 0.33f
                                    }
                                    if (!setting.stringArray.isNullOrEmpty()) {
                                        b.settingsValue.text = setting.stringArray[index].toString()
                                    }
                                }
                            }
                        }
                    )
                }
                b.settingsCardLayout.rotation = setting.cardRotation

                b.settingsLayout.isVisible = setting.isVisible
                b.settingsLayout.isEnabled = setting.isEnabled
                views.forEach { it.settingsCard.isEnabled = setting.isEnabled }
            }

            ViewType.PROGRESS -> {
                val b = (holder as SettingsProgressViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsLayout.isVisible = setting.isVisible
            }

            ViewType.HEADER -> {
                val b = (holder as SettingsHeaderViewHolder).binding
                setAnimation(b.root.context, b.root)

                b.settingsLayout.updateLayoutParams<MarginLayoutParams> {
                    topMargin = if (position != 0 && settings[position - 1].desc.ifBlank {
                        settings[position - 1].descRes
                    } != null)
                        32.toPx
                    else
                        16.toPx
                }

                b.settingsTitle.text = setting.nameRes?.let {
                    b.root.context.getString(it)
                } ?: setting.name
                b.settingsTitle.isVisible = !b.settingsTitle.text.isNullOrBlank()
            }
        }
    }

    fun setItemEnabled(absolutePosition: Int, isEnabled: Boolean) {
        settings[absolutePosition].isEnabled = isEnabled
        notifyItemChanged(absolutePosition)
    }

    fun setItemVisibility(absolutePosition: Int, isVisible: Boolean) {
        settings[absolutePosition].isVisible = isVisible
        notifyItemChanged(absolutePosition)
    }

    fun toggleItemVisibility(absolutePosition: Int) {
        settings[absolutePosition].isVisible = !settings[absolutePosition].isVisible
        notifyItemChanged(absolutePosition)
    }

    override fun getItemCount(): Int = filteredData.size

    override fun getItemViewType(position: Int): Int {
        return filteredData[position].type.ordinal
    }

    override fun getFilter(): Filter? {
        if (null == filter)
            filter = SettingsFilter()
        return filter as SettingsFilter
    }

    inner class SettingsFilter : Filter() {
        private fun Settings.containsQuery(query: String) : Boolean {
            return (name.isNotBlank() && name.lowercase(Locale.getDefault()).contains(query))
                    || nameRes?.let { getString(it).lowercase(Locale.getDefault()).contains(query) } == true
                    || (desc.isNotBlank() && desc.lowercase(Locale.getDefault()).contains(query))
                    || descRes?.let { getString(it).lowercase(Locale.getDefault()).contains(query) } == true
        }

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim { it <= ' ' } ?: ""
            val filterResults = FilterResults()
            if (query.isEmpty()) {
                filterResults.count = settings.size
                filterResults.values = settings
                return filterResults
            }
            val tempList: List<Settings> = settings.filter {
                it.type == ViewType.HEADER || it.containsQuery(query.lowercase(Locale.getDefault()))
            }
            filterResults.count = tempList.size
            filterResults.values = tempList
            return filterResults
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
            if (filteredData === filterResults.values) return
            filterResults.values?.let {
                @Suppress("UNCHECKED_CAST") filteredData = it as ArrayList<Settings>
            }
            notifyDataSetChanged()
        }
    }
}