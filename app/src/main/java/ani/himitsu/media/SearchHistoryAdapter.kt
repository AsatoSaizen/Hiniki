package ani.himitsu.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.ItemSearchHistoryBinding
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefManager.asLiveStringSet
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.settings.saving.SharedPreferenceStringSetLiveData

class SearchHistoryAdapter(type: String, private val searchClicked: (String) -> Unit) :
    ListAdapter<String, SearchHistoryAdapter.SearchHistoryViewHolder>(
        DIFF_CALLBACK_INSTALLED
    ) {
    private var searchHistoryLiveData: SharedPreferenceStringSetLiveData? = null
    private var searchHistory: MutableSet<String>? = null
    private var historyType: PrefName = when (type.lowercase()) {
        "anime" -> PrefName.AnimeSearchHistory
        "manga" -> PrefName.MangaSearchHistory
        else -> throw IllegalArgumentException("Invalid type")
    }

    init {
        searchHistoryLiveData =
            PrefManager.getLiveVal(historyType, mutableSetOf<String>()).asLiveStringSet()
        searchHistoryLiveData?.observeForever {
            searchHistory = it.toMutableSet()
            submitList(searchHistory?.toList())
        }
    }

    fun clear() {
        searchHistory?.clear()
        PrefManager.setVal(historyType, setOf<String>())
        submitList(searchHistory?.toList())
    }

    fun remove(item: String) {
        searchHistory?.remove(item)
        PrefManager.setVal(historyType, searchHistory)
        submitList(searchHistory?.toList())
    }

    fun add(item: String) {
        if (searchHistory?.contains(item) == true || item.isBlank()) return
        if (PrefManager.getVal(PrefName.Incognito)) return
        searchHistory?.add(item)
        submitList(searchHistory?.toList())
        PrefManager.setVal(historyType, searchHistory)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return SearchHistoryViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: SearchHistoryViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        holder.binding.searchHistoryTextView.text = item
        holder.binding.closeTextView.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition >= itemCount || currentPosition < 0) return@setOnClickListener
            remove(getItem(currentPosition))
        }
        holder.binding.searchHistoryTextView.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition >= itemCount || currentPosition < 0) return@setOnClickListener
            searchClicked(getItem(currentPosition))
        }
    }

    inner class SearchHistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val binding = ItemSearchHistoryBinding.bind(view)
    }

    companion object {
        val DIFF_CALLBACK_INSTALLED = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(
                oldItem: String,
                newItem: String
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: String,
                newItem: String
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
