package ani.himitsu.media.manga

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.NumberPicker
import androidx.core.view.isVisible
import androidx.lifecycle.coroutineScope
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.connections.updateProgress
import ani.himitsu.currContext
import ani.himitsu.databinding.ItemChapterListBinding
import ani.himitsu.databinding.ItemEpisodeCompactBinding
import ani.himitsu.media.MediaNameAdapter
import ani.himitsu.media.cereal.Media
import ani.himitsu.setAnimation
import ani.himitsu.util.customAlertDialog
import bit.himitsu.nio.Strings.getString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MangaChapterAdapter(
    private var type: Int,
    private val media: Media,
    private val fragment: MangaReadFragment,
    var chapters: List<MangaChapter> = listOf(),
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateChapters(chapters: List<MangaChapter>) {
        this.chapters = chapters
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            1 -> ChapterCompactViewHolder(
                ItemEpisodeCompactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            0 -> ChapterListViewHolder(
                ItemChapterListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return type
    }

    override fun getItemCount(): Int = chapters.size

    inner class ChapterCompactViewHolder(val binding: ItemEpisodeCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                if (bindingAdapterPosition in 0 until chapters.size)
                    fragment.onMangaChapterClick(chapters[bindingAdapterPosition].number)
            }
        }
    }

    private val activeDownloads = mutableSetOf<String>()
    private val downloadedChapters = mutableSetOf<String>()

    fun importDownload(episodeNumber: String) {
        downloadedChapters.add(episodeNumber)
        chapters.indexOfFirst { it.number == episodeNumber }.let {
            if (it != -1) notifyItemChanged(it)
        }
    }

    fun startDownload(chapterNumber: String) {
        activeDownloads.add(chapterNumber)
        // Find the position of the chapter and notify only that item
        val position = chapters.indexOfFirst { it.number == chapterNumber }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    fun stopDownload(chapterNumber: String) {
        activeDownloads.remove(chapterNumber)
        downloadedChapters.add(chapterNumber)
        // Find the position of the chapter and notify only that item
        val position = chapters.indexOfFirst { it.number == chapterNumber }
        if (position != -1) {
            chapters[position].progress = "Downloaded"
            notifyItemChanged(position)
        }
    }

    fun deleteDownload(chapterNumber: String) {
        downloadedChapters.remove(chapterNumber)
        // Find the position of the chapter and notify only that item
        val position = chapters.indexOfFirst { it.number == chapterNumber }
        if (position != -1) {
            chapters[position].progress = ""
            notifyItemChanged(position)
        }
    }

    fun purgeDownload(chapterNumber: String) {
        activeDownloads.remove(chapterNumber)
        downloadedChapters.remove(chapterNumber)
        // Find the position of the chapter and notify only that item
        val position = chapters.indexOfFirst { it.number == chapterNumber }
        if (position != -1) {
            chapters[position].progress = ""
            notifyItemChanged(position)
        }
    }

    fun updateDownloadProgress(chapterNumber: String, progress: Int) {
        // Find the position of the chapter and notify only that item
        val position = chapters.indexOfFirst { it.number == chapterNumber }
        if (position != -1) {
            chapters[position].progress = "Downloading: ${progress}%"

            notifyItemChanged(position)
        }
    }

    fun downloadNChaptersFrom(position: Int, n: Int) {
        //download next n chapters
        if (position < 0 || position >= chapters.size) return
        for (i in 0..<n) {
            if (position + i < chapters.size) {
                val chapterNumber = chapters[position + i].number
                if (activeDownloads.contains(chapterNumber)) {
                    //do nothing
                    continue
                } else if (downloadedChapters.contains(chapterNumber)) {
                    //do nothing
                    continue
                } else {
                    fragment.onMangaChapterDownloadClick(chapterNumber)
                    startDownload(chapterNumber)
                }
            }
        }
    }

    inner class ChapterListViewHolder(val binding: ItemChapterListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val activeCoroutines = mutableSetOf<String>()
        private val typedValue1 = TypedValue()
        private val typedValue2 = TypedValue()
        fun bind(chapterNumber: String, progress: String?) {
            if (progress != null) {
                binding.itemChapterTitle.visibility = View.VISIBLE
                binding.itemChapterTitle.text = progress
            } else {
                binding.itemChapterTitle.visibility = View.GONE
                binding.itemChapterTitle.text = ""
            }
            if (activeDownloads.contains(chapterNumber)) {
                // Show spinner
                binding.itemDownload.setImageResource(R.drawable.ic_sync)
                startOrContinueRotation(chapterNumber) {
                    binding.itemDownload.rotation = 0f
                }
            } else if (downloadedChapters.contains(chapterNumber)) {
                // Show checkmark
                binding.itemDownload.setImageResource(R.drawable.ic_circle_check)
                binding.itemDownload.postDelayed({
                    binding.itemDownload.setImageResource(R.drawable.round_delete_24)
                    binding.itemDownload.rotation = 0f
                }, 1000)
            } else {
                // Show download icon
                binding.itemDownload.setImageResource(R.drawable.ic_download_24)
                binding.itemDownload.rotation = 0f
            }

        }

        private fun startOrContinueRotation(chapterNumber: String, resetRotation: () -> Unit) {
            if (!isRotationCoroutineRunningFor(chapterNumber)) {
                val scope = fragment.lifecycle.coroutineScope
                scope.launch {
                    // Add chapter number to active coroutines set
                    activeCoroutines.add(chapterNumber)
                    while (activeDownloads.contains(chapterNumber)) {
                        binding.itemDownload.animate().rotationBy(360f).setDuration(1000)
                            .setInterpolator(
                                LinearInterpolator()
                            ).start()
                        delay(1000)
                    }
                    // Remove chapter number from active coroutines set
                    activeCoroutines.remove(chapterNumber)
                    resetRotation()
                }
            }
        }

        private fun isRotationCoroutineRunningFor(chapterNumber: String): Boolean {
            return chapterNumber in activeCoroutines
        }

        init {
            val theme = currContext().theme
            theme?.resolveAttribute(
                com.google.android.material.R.attr.colorError,
                typedValue1,
                true
            )
            theme?.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary,
                typedValue2,
                true
            )
            itemView.setOnClickListener {
                if (bindingAdapterPosition in 0 until chapters.size)
                    fragment.onMangaChapterClick(chapters[bindingAdapterPosition].number)
            }
            binding.importDownload.setOnClickListener {
                if (bindingAdapterPosition in 0 until chapters.size)
                    fragment.onImportDownloadClick(chapters[bindingAdapterPosition].number)
            }
            binding.itemDownload.setOnClickListener {
                if (bindingAdapterPosition in 0 until chapters.size) {
                    val chapterNumber = chapters[bindingAdapterPosition].number
                    if (activeDownloads.contains(chapterNumber)) {
                        fragment.onMangaChapterStopDownloadClick(chapterNumber)
                        return@setOnClickListener
                    } else if (downloadedChapters.contains(chapterNumber)) {
                        it.context.customAlertDialog().apply {
                            setTitle(getString(R.string.delete_item, getString(R.string.chapter)))
                            setMessage(getString(R.string.delete_content, chapterNumber))
                            setPositiveButton(R.string.delete) {
                                fragment.onMangaChapterRemoveDownloadClick(chapterNumber)
                            }
                            setNegativeButton(R.string.cancel)
                            show()
                        }
                        return@setOnClickListener
                    } else {
                        fragment.onMangaChapterDownloadClick(chapterNumber)
                        startDownload(chapterNumber)
                    }
                }
            }
            binding.itemDownload.setOnLongClickListener {
                if (bindingAdapterPosition in 0 until chapters.size) {
                    val chapterNumber = chapters[bindingAdapterPosition].number
                    if (downloadedChapters.contains(chapterNumber)) {
                        fragment.onMangaChapterRemoveDownloadLongClick(chapterNumber)
                        return@setOnLongClickListener true
                    }
                }
                // Alert dialog asking for the number of chapters to download
                it.context.customAlertDialog().apply {
                    setTitle(R.string.multi_download_chapter)
                    setMessage(R.string.multi_download_chapter_count)
                    val input = NumberPicker(currContext())
                    input.minValue = 1
                    input.maxValue = itemCount - bindingAdapterPosition
                    input.value = 1
                    setCustomView(input)
                    setPositiveButton(R.string.ok) {
                        downloadNChaptersFrom(bindingAdapterPosition, input.value)
                    }
                    setNegativeButton(R.string.cancel)
                    show()
                }
                true
            }

        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChapterCompactViewHolder -> {
                val binding = holder.binding
                setAnimation(fragment.requireContext(), holder.binding.root)
                val ep = chapters[position]
                val parsedNumber = MediaNameAdapter.findChapterNumber(ep.number)?.toInt()
                binding.itemEpisodeNumber.text = parsedNumber?.toString() ?: ep.number
                if (media.userProgress != null) {
                    if ((MediaNameAdapter.findChapterNumber(ep.number)
                            ?: 9999f) <= media.userProgress!!.toFloat()
                    )
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                    else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeCont.setOnLongClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val chapter = MediaNameAdapter.findChapterNumber(ep.number).toString()
                            it.context.customAlertDialog().apply {
                                setMessage(R.string.confirm_ch_progress, chapter)
                                setPositiveButton(R.string.yes) { updateProgress(media, chapter) }
                                setNegativeButton(R.string.no)
                                show()
                            }
                            true
                        }
                    }
                }
            }

            is ChapterListViewHolder -> {
                val binding = holder.binding
                val ep = chapters[position]
                holder.bind(ep.number, ep.progress)
                setAnimation(fragment.requireContext(), holder.binding.root)
                binding.itemChapterNumber.text = ep.number

                binding.importDownload.isVisible = !activeDownloads.contains(ep.number)

                if (ep.date != null) {
                    binding.itemChapterDateLayout.visibility = View.VISIBLE
                    binding.itemChapterDate.text = formatDate(ep.date)
                }
                if (ep.scanlator != null) {
                    binding.itemChapterDateLayout.visibility = View.VISIBLE
                    binding.itemChapterScan.text = ep.scanlator.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(
                            Locale.ROOT
                        ) else it.toString()
                    }
                }
                if (formatDate(ep.date) == "" || ep.scanlator == null) {
                    binding.itemChapterDateDivider.visibility = View.GONE
                } else binding.itemChapterDateDivider.visibility = View.VISIBLE

                if (ep.progress.isNullOrEmpty()) {
                    binding.itemChapterTitle.visibility = View.GONE
                } else binding.itemChapterTitle.visibility = View.VISIBLE

                if (media.userProgress != null) {
                    if ((MediaNameAdapter.findChapterNumber(ep.number)
                            ?: 9999f) <= media.userProgress!!.toFloat()
                    ) {
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                        binding.itemEpisodeViewed.visibility = View.VISIBLE
                    } else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeViewed.visibility = View.GONE
                        binding.root.setOnLongClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val chapter = MediaNameAdapter.findChapterNumber(ep.number).toString()
                            it.context.customAlertDialog().apply {
                                setMessage(R.string.confirm_ch_progress, chapter)
                                setPositiveButton(R.string.yes) { updateProgress(media, chapter) }
                                setNegativeButton(R.string.no)
                                show()
                            }
                            true
                        }
                    }
                } else {
                    binding.itemEpisodeViewedCover.visibility = View.GONE
                    binding.itemEpisodeViewed.visibility = View.GONE
                }
            }
        }
    }

    fun updateType(t: Int) {
        type = t
    }

    private fun formatDate(timestamp: Long?): String {
        timestamp ?: return "" // Return empty string if timestamp is null

        val targetDate = Date(timestamp)

        if (targetDate < Date(946684800000L)) { // January 1, 2000 (who want dates before that?)
            return ""
        }

        val currentDate = Date()
        val difference = currentDate.time - targetDate.time

        return when (val daysDifference = difference / (1000 * 60 * 60 * 24)) {
            0L -> {
                val hoursDifference = difference / (1000 * 60 * 60)
                val minutesDifference = (difference / (1000 * 60)) % 60

                when {
                    hoursDifference > 0 -> "$hoursDifference hour${if (hoursDifference > 1) "s" else ""} ago"
                    minutesDifference > 0 -> "$minutesDifference minute${if (minutesDifference > 1) "s" else ""} ago"
                    else -> "Just now"
                }
            }

            1L -> "1 day ago"
            in 2..6 -> "$daysDifference days ago"
            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(targetDate)
        }
    }

}