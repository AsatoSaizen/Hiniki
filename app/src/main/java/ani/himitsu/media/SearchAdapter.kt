package ani.himitsu.media

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.HORIZONTAL
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.currContext
import ani.himitsu.databinding.ItemChipBinding
import ani.himitsu.databinding.ItemSearchHeaderBinding
import ani.himitsu.openLinkInBrowser
import ani.himitsu.others.imagesearch.ImageSearchActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.addViewObserver
import bit.himitsu.widget.onCompletedActionText
import com.google.android.material.checkbox.MaterialCheckBox.STATE_CHECKED
import com.google.android.material.checkbox.MaterialCheckBox.STATE_INDETERMINATE
import com.google.android.material.checkbox.MaterialCheckBox.STATE_UNCHECKED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchAdapter(private val activity: SearchActivity, private val type: String) :
    RecyclerView.Adapter<SearchAdapter.SearchHeaderViewHolder>() {
    private val itemViewType = 6969
    var search: Runnable? = null
    var requestFocus: Runnable? = null
    private var textWatcher: TextWatcher? = null
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    private lateinit var binding: ItemSearchHeaderBinding

    private fun updateFilterTextViewDrawable() {
        val filterDrawable = when (activity.result.sort) {
            AniList.sortBy[0] -> R.drawable.round_area_chart_24
            AniList.sortBy[1] -> R.drawable.round_filter_hdr_24
            AniList.sortBy[2] -> R.drawable.round_auto_graph_24
            AniList.sortBy[3] -> R.drawable.round_new_releases_24
            AniList.sortBy[4] -> R.drawable.round_filter_list_24
            AniList.sortBy[5] -> R.drawable.round_filter_list_24_reverse
            AniList.sortBy[6] -> R.drawable.round_assist_walker_24
            else -> R.drawable.round_filter_alt_24
        }
        binding.filterTextView.setCompoundDrawablesWithIntrinsicBounds(filterDrawable, 0, 0, 0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHeaderViewHolder {
        val binding =
            ItemSearchHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchHeaderViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: SearchHeaderViewHolder, position: Int) {
        binding = holder.binding

        searchHistoryAdapter = SearchHistoryAdapter(type) { binding.searchBarText.setText(it) }
        binding.searchHistoryList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.searchHistoryList.adapter = searchHistoryAdapter

        binding.clearSearHistory.isVisible = searchHistoryAdapter.itemCount > 0
        if (binding.clearSearHistory.isVisible) {
            binding.clearSearHistory.setOnClickListener {
                it.context.customAlertDialog().apply {
                    setMessage(R.string.clear_history_confirm)
                    setPositiveButton(R.string.yes) {
                        searchHistoryAdapter.clear()
                        binding.clearSearHistory.isVisible = false
                    }
                    setNegativeButton(R.string.no)
                    show()
                }
            }
        }

        binding.searchTypeValue.setText(when (type) {
            "ANIME" -> "\uD83D\uDCFD\uFE0F"
            "MANGA" -> "\uD83D\uDCD6"
            "USER" -> "\uD83D\uDC64"
            "STAFF" -> "\uD83D\uDE4B"
            "STUDIO" -> "\uD83C\uDFE9"
            "CHARACTER" -> "\uD83E\uDD84"
            else -> "\uD83D\uDCFD\uFE0F"
        })
        binding.searchTypeValue.setAdapter(
            ArrayAdapter(
                activity,
                R.layout.item_dropdown,
                activity.resources.getStringArray(R.array.search_type)
            )
        )
        binding.searchTypeLayout.addViewObserver {
            binding.searchTypeValue.dropDownWidth = binding.searchBarText.width / 2
        }
        binding.searchTypeValue.setOnClickListener { binding.searchTypeValue.showDropDown() }
        binding.searchTypeValue.setOnItemClickListener { _, _, i, _ ->
            activity.model.searchResults.apply {
                type = when (i) {
                    0 -> "ANIME"
                    1 -> "MANGA"
                    2 -> "USER"
                    3 -> "STAFF"
                    4 -> "STUDIO"
                    5 -> "CHARACTER"
                    else -> "ANIME"
                }
            }
            activity.search()
        }

        val imm: InputMethodManager =
            activity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager

        when (activity.style) {
            0 -> {
                binding.searchResultGrid.alpha = 1f
                binding.searchResultList.alpha = 0.33f
            }

            1 -> {
                binding.searchResultList.alpha = 1f
                binding.searchResultGrid.alpha = 0.33f
            }
        }

        binding.searchBar.hint = activity.result.type
        if (PrefManager.getVal(PrefName.Incognito)) {
            val startIconDrawableRes = R.drawable.ic_incognito_24
            val startIconDrawable: Drawable? =
                currContext().let { AppCompatResources.getDrawable(it, startIconDrawableRes) }
            binding.searchBar.startIconDrawable = startIconDrawable
        }

        var adult = activity.result.isAdult
        var listOnly = activity.result.onList

        binding.searchBarText.removeTextChangedListener(textWatcher)
        binding.searchBarText.setText(activity.result.search)

        binding.searchAdultCheck.isChecked = adult
        binding.searchList.isChecked = listOnly == true

        binding.searchChipRecycler.adapter = SearchChipAdapter(activity, this).also {
            activity.updateChips = { it.update() }
        }

        binding.searchChipRecycler.layoutManager =
            LinearLayoutManager(binding.root.context, HORIZONTAL, false)

        binding.searchFilter.setOnClickListener {
            SearchFilterBottomDialog.newInstance().show(activity.supportFragmentManager, "dialog")
        }
        binding.searchFilter.setOnLongClickListener {
            val popupMenu = PopupMenu(activity, binding.searchFilter)
            popupMenu.menuInflater.inflate(R.menu.sortby_filter_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.sort_by_score -> {
                        activity.result.sort = AniList.sortBy[0]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_popular -> {
                        activity.result.sort = AniList.sortBy[1]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_trending -> {
                        activity.result.sort = AniList.sortBy[2]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_recent -> {
                        activity.result.sort = AniList.sortBy[3]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_a_z -> {
                        activity.result.sort = AniList.sortBy[4]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_z_a -> {
                        activity.result.sort = AniList.sortBy[5]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_user_score -> {
                        activity.result.sort = AniList.sortBy[6]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }
                }
                true
            }
            popupMenu.show()
            true
        }
        binding.searchByImage.setOnClickListener {
            activity.startActivity(Intent(activity, ImageSearchActivity::class.java))
        }

        fun searchTitle() {
            activity.result.apply {
                search =
                    if (binding.searchBarText.text.toString() != "") binding.searchBarText.text.toString() else null
                onList = listOnly
                isAdult = adult
            }
            if (binding.searchBarText.text.toString().equals("hentai", true)) {
                openLinkInBrowser("https://www.youtube.com/watch?v=GgJrEOo0QoA")
            }
            activity.search()
        }

        textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.toString().isBlank()) {
                    activity.emptyMediaAdapter()
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        delay(200)
                        activity.runOnUiThread {
                            setHistoryVisibility(true)
                        }
                    }
                } else {
                    setHistoryVisibility(false)
                    searchTitle()
                }
            }
        }
        binding.searchBarText.addTextChangedListener(textWatcher)

        binding.searchBarText.setOnEditorActionListener (onCompletedActionText {
            searchTitle()
            binding.searchBarText.clearFocus()
            imm.hideSoftInputFromWindow(binding.searchBarText.windowToken, 0)
        })
        binding.searchBar.setEndIconOnClickListener { searchTitle() }

        binding.searchResultGrid.setOnClickListener {
            it.alpha = 1f
            binding.searchResultList.alpha = 0.33f
            activity.style = 0
            PrefManager.setVal(PrefName.SearchStyle, 0)
            activity.recycler()
        }
        binding.searchResultList.setOnClickListener {
            it.alpha = 1f
            binding.searchResultGrid.alpha = 0.33f
            activity.style = 1
            PrefManager.setVal(PrefName.SearchStyle, 1)
            activity.recycler()
        }

        if (AniList.adult) {
            binding.searchAdultCheck.visibility = View.VISIBLE
            binding.searchAdultCheck.isChecked = adult
            binding.searchAdultCheck.setOnCheckedChangeListener { _, b ->
                adult = b
                searchTitle()
            }
        } else binding.searchAdultCheck.visibility = View.GONE
        binding.searchList.apply {
            if (AniList.userid != null) {
                visibility = View.VISIBLE
                checkedState = when (listOnly) {
                    null -> STATE_UNCHECKED
                    true -> STATE_CHECKED
                    false -> STATE_INDETERMINATE
                }

                addOnCheckedStateChangedListener { _, state ->
                    listOnly = when (state) {
                        STATE_CHECKED -> true
                        STATE_INDETERMINATE -> false
                        STATE_UNCHECKED -> null
                        else -> null
                    }
                }

                setOnTouchListener { _, event ->
                    (event.actionMasked == MotionEvent.ACTION_DOWN).also {
                        if (it) checkedState = (checkedState + 1) % 3
                        searchTitle()
                    }
                }
            } else visibility = View.GONE
        }

        search = Runnable { searchTitle() }
        requestFocus = Runnable {
            binding.searchBarText.requestFocus()
        }
    }

    fun setHistoryVisibility(visible: Boolean) {
        if (visible) {
            binding.searchResultLayout.startAnimation(fadeOutAnimation())
            binding.searchHistoryList.startAnimation(fadeInAnimation())
            binding.searchResultLayout.visibility = View.GONE
            binding.searchHistoryList.visibility = View.VISIBLE
            binding.searchByImage.visibility = View.VISIBLE
        } else {
            if (binding.searchResultLayout.visibility != View.VISIBLE) {
                binding.searchResultLayout.startAnimation(fadeInAnimation())
                binding.searchHistoryList.startAnimation(fadeOutAnimation())
            }

            binding.searchResultLayout.visibility = View.VISIBLE
            binding.searchHistoryList.visibility = View.GONE
            binding.searchByImage.visibility = View.GONE
        }
    }

    private fun fadeInAnimation(): Animation {
        return AlphaAnimation(0f, 1f).apply {
            duration = 150
        }
    }

    private fun fadeOutAnimation(): Animation {
        return AlphaAnimation(1f, 0f).apply {
            duration = 150
        }
    }


    fun addHistory() {
        if (::searchHistoryAdapter.isInitialized &&
            binding.searchBarText.text.toString().isNotBlank()
        ) searchHistoryAdapter.add(binding.searchBarText.text.toString())
    }

    override fun getItemCount(): Int = 1

    inner class SearchHeaderViewHolder(val binding: ItemSearchHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return itemViewType
    }


    class SearchChipAdapter(
        val activity: SearchActivity,
        private val searchAdapter: SearchAdapter
    ) :
        RecyclerView.Adapter<SearchChipAdapter.SearchChipViewHolder>() {
        private var chips = activity.result.toChipList()

        inner class SearchChipViewHolder(val binding: ItemChipBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchChipViewHolder {
            val binding =
                ItemChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SearchChipViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SearchChipViewHolder, position: Int) {
            val chip = chips[position]
            holder.binding.root.apply {
                text = chip.text.replace("_", " ")
                setOnClickListener {
                    activity.result.removeChip(chip)
                    update()
                    activity.search()
                    searchAdapter.updateFilterTextViewDrawable()
                }
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun update() {
            chips = activity.result.toChipList()
            notifyDataSetChanged()
            searchAdapter.updateFilterTextViewDrawable()
        }

        override fun getItemCount(): Int = chips.size
    }
}

