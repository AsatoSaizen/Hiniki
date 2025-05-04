package ani.himitsu.settings.extension

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.FragmentExtensionsBinding
import ani.himitsu.databinding.ItemExtensionBinding
import ani.himitsu.others.LanguageMapper
import ani.himitsu.parsers.novel.NovelExtensionManager
import ani.himitsu.parsers.novel.NovelPlugin
import ani.himitsu.settings.SearchQueryHandler
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.content.toPx
import bit.himitsu.webkit.ChromeIntegration
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelPluginsFragment : Fragment(), SearchQueryHandler {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding by lazy { _binding!! }
    private lateinit var pluginsRecyclerView: RecyclerView
    private val skipIcons: Boolean = PrefManager.getVal(PrefName.SkipExtensionIcons)
    private val novelExtensionManager: NovelExtensionManager = Injekt.get()
    private val pluginsAdapter = NovelPluginsAdapter(
        { plugin ->
            if (isAdded) {  // Check if the fragment is currently added to its activity
                ChromeIntegration.openPluginTab(
                    pluginsRecyclerView.context, plugin.sources[0].baseUrl
                )
            }
        },
        skipIcons
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        pluginsRecyclerView = binding.allExtensionsRecyclerView.apply {
            isNestedScrollingEnabled = false
            adapter = pluginsAdapter
            layoutManager = LinearLayoutManager(context)
            FastScrollerBuilder(this).useMd2Style().build().setPadding(0, 0, 0, 0)
        }


        lifecycleScope.launch {
            novelExtensionManager.availablePluginsFlow.collect { plugins ->
                pluginsAdapter.updateData(plugins)
            }
        }

        return binding.root
    }

    override fun updateContentBasedOnQuery(query: String?) {
        pluginsAdapter.filter(
            query,
            novelExtensionManager.availablePluginsFlow.value
        )
    }

    override fun notifyDataChanged() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class NovelPluginsAdapter(
        private val onViewerClicked: (NovelPlugin.Available) -> Unit,
        val skipIcons: Boolean
    ) : ListAdapter<NovelPlugin.Available, NovelPluginsAdapter.ViewHolder>(
        DIFF_CALLBACK
    )/* , PopupTextProvider */ {

        fun updateData(newPlugins: List<NovelPlugin.Available>) {
            submitList(newPlugins)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemExtensionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            ).apply {
                extensionPinImageView.isGone = true
                settingsImageView.isGone = true
                closeTextView.setImageResource(R.drawable.ic_globe_24)
            }
            return ViewHolder(binding.root)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val plugin = getItem(position)
            if (plugin != null) {
                val lang = LanguageMapper.mapLanguageCodeToName(plugin.lang)
                holder.extensionNameTextView.text = plugin.name
                val text = "$lang ${plugin.versionName}"
                holder.extensionVersionTextView.text = text
                if (!skipIcons) {
                    Glide.with(holder.itemView.context)
                        .load(plugin.iconUrl).override(48.toPx)
                        .into(holder.extensionIconImageView)
                }
                holder.closeTextView.setOnClickListener {
                    onViewerClicked(plugin)
                }
            }
        }

        fun filter(query: String?, currentList: List<NovelPlugin.Available>) {
            val filteredList = if (!query.isNullOrBlank()) {
                currentList.filter { it.name.lowercase().contains(query.lowercase()) }
            } else { currentList }
            if (filteredList != currentList) submitList(filteredList)
        }

//        override fun getPopupText(view: View, position: Int) : CharSequence {
//            return getItem(position).name[0].uppercase()
//        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val extensionNameTextView: TextView = view.findViewById(R.id.extensionNameTextView)
            val extensionVersionTextView: TextView =
                view.findViewById(R.id.extensionVersionTextView)
            val extensionIconImageView: ImageView = view.findViewById(R.id.extensionIconImageView)
            val closeTextView: ImageView = view.findViewById(R.id.closeTextView)
        }

        companion object {
            val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NovelPlugin.Available>() {
                override fun areItemsTheSame(
                    oldItem: NovelPlugin.Available,
                    newItem: NovelPlugin.Available
                ): Boolean {
                    return oldItem.pkgName == newItem.pkgName
                }

                override fun areContentsTheSame(
                    oldItem: NovelPlugin.Available,
                    newItem: NovelPlugin.Available
                ): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }
}