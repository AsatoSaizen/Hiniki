package ani.himitsu.settings.extension

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.FragmentExtensionsBinding
import ani.himitsu.databinding.ItemExtensionBinding
import ani.himitsu.others.LanguageMapper
import ani.himitsu.parsers.NovelSources
import ani.himitsu.parsers.ParserTestActivity
import ani.himitsu.parsers.novel.NovelExtension
import ani.himitsu.parsers.novel.NovelExtensionManager
import ani.himitsu.settings.SearchQueryHandler
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.util.Logger
import bit.himitsu.forceShowIcons
import bit.himitsu.os.Version
import bit.himitsu.search.ReverseSearchDialogFragment
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections

class InstalledNovelExtensionsFragment : Fragment(), SearchQueryHandler {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding by lazy { _binding!! }
    private lateinit var extensionsRecyclerView: RecyclerView
    private val skipIcons: Boolean = PrefManager.getVal(PrefName.SkipExtensionIcons)
    private val novelExtensionManager: NovelExtensionManager = Injekt.get()
    private val extensionsAdapter = NovelExtensionsAdapter(
        { _ ->
            Toast.makeText(requireContext(), "Source is not configurable", Toast.LENGTH_SHORT)
                .show()
        },
        { pkg ->
            if (!isAdded || !pkg.hasUpdate) return@NovelExtensionsAdapter
            val context = requireContext()  // Store context in a variable
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager  // Initialize NotificationManager once
            novelExtensionManager.updateExtension(pkg)
                .observeOn(AndroidSchedulers.mainThread())  // Observe on main thread
                .subscribe(
                    { installStep ->
                        val builder = NotificationCompat.Builder(
                            context,
                            Notifications.CHANNEL_DOWNLOADER_PROGRESS
                        )
                            .setSmallIcon(R.drawable.round_sync_24)
                            .setContentTitle(getString(R.string.updating_extension))
                            .setContentText(getString(R.string.installer_step, installStep))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                        notificationManager.notify(1, builder.build())
                    },
                    { error ->
                        Logger.log(error)  // Log the error
                        val builder = NotificationCompat.Builder(
                            context,
                            Notifications.CHANNEL_DOWNLOADER_ERROR
                        )
                            .setSmallIcon(R.drawable.round_info_outline_24)
                            .setContentTitle(getString(R.string.update_failed, error.message))
                            .setContentText(getString(R.string.error_message, error.message))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                        notificationManager.notify(1, builder.build())
                        snackString(getString(R.string.update_failed, error.message))
                    },
                    {
                        val builder = NotificationCompat.Builder(
                            context,
                            Notifications.CHANNEL_DOWNLOADER_PROGRESS
                        )
                            .setSmallIcon(R.drawable.ic_check)
                            .setContentTitle(getString(R.string.update_complete))
                            .setContentText(getString(R.string.extension_updated))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                        notificationManager.notify(1, builder.build())
                        snackString(getString(R.string.update_complete))
                    }
                )
        },
        { pkg ->
            novelExtensionManager.uninstallExtension(pkg.pkgName)
            // snackString(getString(R.string.extension_removed))
        },
        { extension ->
            ReverseSearchDialogFragment(extension).show(
                requireActivity().supportFragmentManager, null
            )
        },
        skipIcons
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        extensionsRecyclerView = binding.allExtensionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = extensionsAdapter
            FastScrollerBuilder(this).useMd2Style().build().setPadding(0, 0, 0, 0)
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val newList = extensionsAdapter.currentList.toMutableList()
                val fromPosition = viewHolder.absoluteAdapterPosition
                val toPosition = target.absoluteAdapterPosition
                if (fromPosition < toPosition) { //probably need to switch to a recyclerview adapter
                    for (i in fromPosition until toPosition) {
                        Collections.swap(newList, i, i + 1)
                    }
                } else {
                    for (i in fromPosition downTo toPosition + 1) {
                        Collections.swap(newList, i, i - 1)
                    }
                }
                extensionsAdapter.submitList(newList)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.elevation = 8f
                    viewHolder?.itemView?.translationZ = 8f
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                extensionsAdapter.updatePref()
                viewHolder.itemView.elevation = 0f
                viewHolder.itemView.translationZ = 0f
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(extensionsRecyclerView)


        lifecycleScope.launch {
            novelExtensionManager.installedExtensionsFlow.collect { extensions ->
                extensionsAdapter.updateData(sortToNovelSourcesList(extensions))
            }
        }
        return binding.root
    }

    private fun sortToNovelSourcesList(inpt: List<NovelExtension.Installed>): List<NovelExtension.Installed> {
        val sourcesMap = inpt.associateBy { it.name }
        val orderedSources = NovelSources.pinnedNovelSources.mapNotNull { name ->
            sourcesMap[name]
        }
        return orderedSources + inpt.filter { !NovelSources.pinnedNovelSources.contains(it.name) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun updateContentBasedOnQuery(query: String?) {
        extensionsAdapter.filter(
            query,
            sortToNovelSourcesList(novelExtensionManager.installedExtensionsFlow.value)
        )
    }

    override fun notifyDataChanged() {}

    private class NovelExtensionsAdapter(
        private val onSettingsClicked: (NovelExtension.Installed) -> Unit,
        private val onUpdateClicked: (NovelExtension.Installed) -> Unit,
        private val onUninstallClicked: (NovelExtension.Installed) -> Unit,
        private val onSearchClicked: (NovelExtension.Installed) -> Unit,
        val skipIcons: Boolean
    ) : ListAdapter<NovelExtension.Installed, NovelExtensionsAdapter.ViewHolder>(
        DIFF_CALLBACK_INSTALLED
    ), PopupTextProvider {

        fun updateData(newExtensions: List<NovelExtension.Installed>) {
            submitList(newExtensions)
        }

        fun updatePref() {
            val map = currentList.map { it.name }
            PrefManager.setVal(PrefName.NovelSourcesOrder, map)
            NovelSources.pinnedNovelSources = map
            NovelSources.performReorderNovelSources()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemExtensionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding.root)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val extension = getItem(position)  // Use getItem() from ListAdapter
            val nsfw = ""
            val lang = LanguageMapper.mapLanguageCodeToName("all")
            holder.extensionNameTextView.text = extension.name
            val versionText = "$lang ${extension.versionName} $nsfw"
            holder.extensionVersionTextView.text = versionText
            if (!skipIcons) {
                holder.extensionIconImageView.setImageDrawable(extension.icon)
            }
            holder.closeTextView.isVisible = extension.hasUpdate
            holder.closeTextView.setOnClickListener {
                onUpdateClicked(extension)
            }

            val popup = if (Version.isLollipopMR)
                PopupMenu(holder.settingsImageView.context, holder.settingsImageView, Gravity.END, 0, R.style.MyPopup)
            else
                PopupMenu(holder.settingsImageView.context, holder.settingsImageView)
            popup.menuInflater.inflate(R.menu.extension_item_menu, popup.menu)
            popup.forceShowIcons()

            holder.settingsImageView.setOnClickListener {
                popup.show()
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.search -> {
                            onSearchClicked(extension)
                        }
                        R.id.settings -> {
                            onSettingsClicked(extension)
                        }
                        R.id.testing -> {
                            holder.settingsImageView.context.startActivity(
                                Intent(holder.settingsImageView.context, ParserTestActivity::class.java)
                            )
                        }
                        R.id.uninstall -> {
                            onUninstallClicked(extension)
                        }
                    }
                    true
                }
            }
        }

        fun filter(query: String?, currentList: List<NovelExtension.Installed>) {
            val filteredList = if (!query.isNullOrBlank()) {
                currentList.filter { it.name.lowercase().contains(query.lowercase()) }
            } else { currentList }
            if (filteredList != currentList) submitList(filteredList)
        }

        override fun getPopupText(view: View, position: Int) : CharSequence {
            return getItem(position).name[0].uppercase()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val extensionNameTextView: TextView = view.findViewById(R.id.extensionNameTextView)
            val extensionVersionTextView: TextView =
                view.findViewById(R.id.extensionVersionTextView)
            val settingsImageView: ImageView = view.findViewById(R.id.settingsImageView)
            val extensionIconImageView: ImageView = view.findViewById(R.id.extensionIconImageView)
            val closeTextView: ImageView = view.findViewById(R.id.closeTextView)
        }

        companion object {
            val DIFF_CALLBACK_INSTALLED =
                object : DiffUtil.ItemCallback<NovelExtension.Installed>() {
                override fun areItemsTheSame(
                    oldItem: NovelExtension.Installed,
                    newItem: NovelExtension.Installed
                ): Boolean {
                    return oldItem.pkgName == newItem.pkgName
                }

                override fun areContentsTheSame(
                    oldItem: NovelExtension.Installed,
                    newItem: NovelExtension.Installed
                ): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }
}