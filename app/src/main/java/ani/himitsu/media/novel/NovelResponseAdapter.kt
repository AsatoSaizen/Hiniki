package ani.himitsu.media.novel

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.R
import ani.himitsu.databinding.ItemNovelResponseBinding
import ani.himitsu.loadImage
import ani.himitsu.setAnimation
import ani.himitsu.toast
import ani.himitsu.util.Logger
import ani.himitsu.util.customAlertDialog
import bit.himitsu.nio.Strings.getString
import eu.kanade.tachiyomi.util.system.getThemeColor

class NovelResponseAdapter(
    val fragment: NovelReadFragment,
    val downloadCallback: DownloadCallback
) : RecyclerView.Adapter<NovelResponseAdapter.ViewHolder>() {
    val list: MutableList<ShowResponse> = mutableListOf()

    inner class ViewHolder(val binding: ItemNovelResponseBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val bind =
            ItemNovelResponseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(bind)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        if (list.size <= position) return
        val novel = list[position]
        setAnimation(fragment.requireContext(), holder.binding.root)
        binding.itemEpisodeImage.loadImage(novel.coverUrl, 400)

        val color = fragment.requireContext()
            .getThemeColor(com.google.android.material.R.attr.colorOnBackground)

        binding.itemEpisodeTitle.text = novel.name
        binding.itemEpisodeFiller.text =
            if (downloadCallback.downloadedCheck(novel)) {
                "Downloaded"
            } else {
                novel.extra?.get("0") ?: ""
            }
        if (binding.itemEpisodeFiller.text.contains("Downloading")) {
            binding.itemEpisodeFiller.setTextColor(
                ContextCompat.getColor(fragment.requireContext(), android.R.color.holo_blue_light)
            )
        } else if (binding.itemEpisodeFiller.text.contains("Downloaded")) {
            binding.itemEpisodeFiller.setTextColor(
                ContextCompat.getColor(fragment.requireContext(), android.R.color.holo_green_light)
            )
        } else {
            binding.itemEpisodeFiller.setTextColor(color)
        }
        binding.itemEpisodeDesc2.text = novel.extra?.get("1") ?: ""
        val desc = novel.extra?.get("2")
        binding.itemEpisodeDesc.isVisible = !desc.isNullOrBlank()
        binding.itemEpisodeDesc.text = desc ?: ""

        binding.importDownload.setOnClickListener {
            fragment.onImportDownloadClick(novel.name)
        }
        binding.importDownload.isVisible = !novel.link.startsWith("content://")

        binding.root.setOnClickListener {
            if (activeDownloads.contains(novel.link)) {
                return@setOnClickListener
            }
            if (downloadCallback.downloadedCheckWithStart(novel)) {
                return@setOnClickListener
            }
            if (novel.link.startsWith("content://")) {
                fragment.launchDownload(Uri.parse(novel.link))
                return@setOnClickListener
            }

            val bookDialog = BookDialog.newInstance(fragment.novelName, novel, fragment.source)

            bookDialog.setCallback(object : BookDialog.Callback {
                override fun onDownloadTriggered(link: String) {
                    downloadCallback.downloadTrigger(
                        NovelDownloadPackage(
                            link,
                            novel.coverUrl.url,
                            novel.name,
                            novel.link
                        )
                    )
                    bookDialog.dismiss()
                }
            })
            bookDialog.show(fragment.parentFragmentManager, "dialog")

        }

        binding.root.setOnLongClickListener {
            fragment.requireContext().customAlertDialog().apply {
                setTitle(getString(R.string.delete_item, novel.name))
                setMessage(getString(R.string.delete_content, novel.name))
                setPositiveButton(R.string.yes) {
                    downloadCallback.deleteDownload(novel)
                    deleteDownload(novel.link)
                    toast(getString(R.string.deleted_item, novel.name))
                    if (binding.itemEpisodeFiller.text.toString()
                            .contains("Download", ignoreCase = true)
                    ) {
                        binding.itemEpisodeFiller.text = ""
                    }
                }
                setNegativeButton(R.string.no) { }
                show()
            }
            true
        }
    }

    private val activeDownloads = mutableSetOf<String>()
    private val downloadedChapters = mutableSetOf<String>()

    fun startDownload(link: String) {
        activeDownloads.add(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Downloading: 0%")
            notifyItemChanged(position)
        }

    }

    fun stopDownload(link: String) {
        activeDownloads.remove(link)
        downloadedChapters.add(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Downloaded")
            notifyItemChanged(position)
        }
    }

    fun deleteDownload(link: String) {
        downloadedChapters.remove(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "")
            notifyItemChanged(position)
        }
    }

    fun purgeDownload(link: String) {
        activeDownloads.remove(link)
        downloadedChapters.remove(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Failed")
            notifyItemChanged(position)
        }
    }

    fun updateDownloadProgress(link: String, progress: Int) {
        if (!activeDownloads.contains(link)) {
            activeDownloads.add(link)
        }
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Downloading: $progress%")
            Logger.log("updateDownloadProgress: $progress, position: $position")
            notifyItemChanged(position)
        }
    }

    fun importDownload(novelName: String) {
        notifyItemChanged(list.indexOfFirst { it.name == novelName })
    }

    fun submitList(it: List<ShowResponse>) {
        val old = list.size
        list.clear()
        list.addAll(it)
        when {
            old == 0 -> {
                notifyItemRangeInserted(0, it.size)
            }
            it.size > old -> {
                notifyItemChanged(0, old)
                notifyItemRangeInserted(old, it.size - old)
            }
            old > it.size -> {
                notifyItemChanged(0, it.size)
                notifyItemRangeRemoved(it.size, old - it.size)
            }
            else -> {
                notifyItemRangeInserted(old, it.size)
            }
        }
    }
}

data class NovelDownloadPackage(
    val link: String,
    val coverUrl: String,
    val novelName: String,
    val originalLink: String
)