package ani.himitsu.media.novel

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.DialogPickerBinding
import ani.himitsu.databinding.ItemNovelHeaderBinding
import ani.himitsu.media.cereal.Media
import ani.himitsu.parsers.NovelReadSources
import ani.himitsu.util.customAlertDialog
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.string
import bit.himitsu.widget.onCompletedActionText
import kotlin.math.min

class NovelReadAdapter(
    private val media: Media,
    private val fragment: NovelReadFragment,
    private val novelReadSources: NovelReadSources
) : RecyclerView.Adapter<NovelReadAdapter.ViewHolder>() {

    var progress: View? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemNovelHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        progress = binding.progress.root
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        progress = binding.progress.root

        fun search(): Boolean {
            binding.searchBarText.clearFocus()
            with (fragment.requireContext().getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager) {
                hideSoftInputFromWindow(binding.searchBarText.windowToken, 0)
            }

            val query = binding.searchBarText.text.toString()
            fragment.source = min(media.selected!!.sourceIndex, novelReadSources.names.size - 1)

            fragment.search(query, save = true)
            return true
        }

        if (novelReadSources.names.isNotEmpty()) {
            val source = min(media.selected!!.sourceIndex, novelReadSources.names.size - 1)
            binding.animeSource.setText(novelReadSources.names[source], false)
        }
        binding.animeSource.setAdapter(
            ArrayAdapter(
                fragment.requireContext(),
                R.layout.item_dropdown,
                novelReadSources.names
            )
        )
        binding.animeSource.setOnItemClickListener { _, _, i, _ ->
            fragment.onSourceChange(i)
        }

        binding.searchBarText.setText(fragment.searchQuery)
        binding.searchBarText.setOnEditorActionListener(onCompletedActionText { search() })
        binding.searchBar.setEndIconOnClickListener { search() }
        binding.searchBar.setEndIconOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val picker = DialogPickerBinding.inflate(fragment.layoutInflater).apply {
                playbackSpeedText.text = getString(R.string.import_volume)
                playbackSpeed.minValue = 1
                playbackSpeed.maxValue = 100 // How do we know?
                playbackSpeed.value = 1
                playbackConfirm.text = getString(R.string.select_volume)
            }
            val dialog = fragment.requireContext().customAlertDialog().apply {
                setCustomView(picker.root)
            }.show()
            picker.playbackConfirm.setOnClickListener {
                fragment.onImportDownloadClick(picker.playbackSpeed.value.string)
                dialog.dismiss()
            }
            true
        }
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(val binding: ItemNovelHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)
}