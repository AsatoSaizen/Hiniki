package ani.himitsu.settings.paging

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.isGone
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.ItemExtensionBinding
import ani.himitsu.others.LanguageMapper
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.content.toPx
import com.bumptech.glide.Glide
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import tachiyomi.core.util.lang.withUIContext

@Suppress("UNCHECKED_CAST")
class AnimeExtensionsViewModelFactory(
    private val animeExtensionManager: AnimeExtensionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AnimeExtensionsViewModel(animeExtensionManager) as T
    }
}

class AnimeExtensionsViewModel(
    animeExtensionManager: AnimeExtensionManager
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private var currentPagingSource: AnimeExtensionPagingSource? = null
    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun invalidatePager() {
        currentPagingSource?.invalidate()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagerFlow: Flow<PagingData<AnimeExtension.Available>> = combine(
        animeExtensionManager.availableExtensionsFlow,
        animeExtensionManager.installedExtensionsFlow,
        searchQuery
    ) { available, installed, query ->
        Triple(available, installed, query)
    }.flatMapLatest { (available, installed, query) ->
        Pager(
            PagingConfig(
                pageSize = 20,
                initialLoadSize = available.size + installed.size,
                prefetchDistance = 10,
                enablePlaceholders = true
            )
        ) {
            AnimeExtensionPagingSource(available, installed, query). also {
                currentPagingSource = it
            }
        }.flow
    }.cachedIn(viewModelScope)
}

class AnimeExtensionPagingSource(
    private val availableExtensionsFlow: List<AnimeExtension.Available>,
    private val installedExtensionsFlow: List<AnimeExtension.Installed>,
    private val searchQuery: String
) : PagingSource<Int, AnimeExtension.Available>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeExtension.Available> {
        val position = params.key ?: 0
        val installedExtensions = installedExtensionsFlow.map { it.pkgName }.toSet()
        val availableExtensions =
            availableExtensionsFlow.filterNot { it.pkgName in installedExtensions }
        val query = searchQuery
        val isNsfwEnabled: Boolean = PrefManager.getVal(PrefName.NSFWExtension)

        val filteredExtensions = if (query.isEmpty()) {
            availableExtensions
        } else {
            availableExtensions.filter { it.name.contains(query, ignoreCase = true) }
        }
        val lang: String = PrefManager.getVal(PrefName.LangSort)
        val langFilter = if (lang != "all")
            filteredExtensions.filter { it.lang == lang }
        else
            filteredExtensions
        val filternfsw = if (isNsfwEnabled) langFilter else langFilter.filterNot { it.isNsfw }
        return try {
            val sublist = filternfsw.subList(
                fromIndex = position,
                toIndex = (position + params.loadSize).coerceAtMost(filternfsw.size)
            )
            LoadResult.Page(
                data = sublist,
                prevKey = if (position == 0) null else position - params.loadSize,
                nextKey = if (position + params.loadSize >= filternfsw.size) null else position + params.loadSize
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AnimeExtension.Available>): Int? {
        return null
    }
}

class AnimeExtensionAdapter(private val clickListener: OnAnimeInstallClickListener) :
    PagingDataAdapter<AnimeExtension.Available, AnimeExtensionAdapter.AnimeExtensionViewHolder>(
        DIFF_CALLBACK
    ), PopupTextProvider {

    private val skipIcons: Boolean = PrefManager.getVal(PrefName.SkipExtensionIcons)

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AnimeExtension.Available>() {
            override fun areItemsTheSame(
                oldItem: AnimeExtension.Available,
                newItem: AnimeExtension.Available
            ): Boolean {
                // Your logic here
                return oldItem.pkgName == newItem.pkgName
            }

            override fun areContentsTheSame(
                oldItem: AnimeExtension.Available,
                newItem: AnimeExtension.Available
            ): Boolean {
                // Your logic here
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimeExtensionViewHolder {
        val binding = ItemExtensionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ).apply {
            extensionPinImageView.isGone = true
            settingsImageView.isGone = true
            closeTextView.setImageResource(R.drawable.ic_download_24)
        }
        return AnimeExtensionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimeExtensionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getPopupText(view: View, position: Int) : CharSequence {
        return getItem(position)?.name[0]?.uppercase() ?: "?"
    }

    inner class AnimeExtensionViewHolder(private val binding: ItemExtensionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val job = Job()
        private val scope = CoroutineScope(Dispatchers.IO + job)

        init {
            binding.closeTextView.setOnClickListener {
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                getItem(bindingAdapterPosition)?.let { extension ->
                    clickListener.onInstallClick(extension)
                    binding.closeTextView.setImageResource(R.drawable.ic_sync)
                    scope.launch {
                        while (isActive) {
                            withUIContext {
                                binding.closeTextView.animate()
                                    .rotationBy(360f)
                                    .setDuration(1000)
                                    .setInterpolator(LinearInterpolator())
                                    .start()
                            }
                            delay(1000)
                        }
                    }
                }
            }
        }

        fun bind(extension: AnimeExtension.Available?) {
            if (extension == null) return
            if (!skipIcons) {
                Glide.with(binding.extensionIconImageView)
                    .load(extension.iconUrl).override(48.toPx)
                    .into(binding.extensionIconImageView)
            }
            val nsfw = if (extension.isNsfw) "(18+)" else ""
            val lang = LanguageMapper.mapLanguageCodeToName(extension.lang)
            binding.extensionNameTextView.text = extension.name
            val versionText = "$lang ${extension.versionName} $nsfw"
            binding.extensionVersionTextView.text = versionText
        }

        fun clear() {
            job.cancel() // Cancel the coroutine when the view is recycled
        }
    }

    override fun onViewRecycled(holder: AnimeExtensionViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }
}

interface OnAnimeInstallClickListener {
    fun onInstallClick(pkg: AnimeExtension.Available)
}
