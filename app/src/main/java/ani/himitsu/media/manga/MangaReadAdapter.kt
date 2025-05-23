package ani.himitsu.media.manga

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.currContext
import ani.himitsu.databinding.DialogLayoutBinding
import ani.himitsu.databinding.DialogPickerBinding
import ani.himitsu.databinding.ItemAnimeWatchBinding
import ani.himitsu.databinding.ItemChipBinding
import ani.himitsu.isOnline
import ani.himitsu.loadImage
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.media.MediaNameAdapter
import ani.himitsu.media.MediaType
import ani.himitsu.media.SourceSearchDialogFragment
import ani.himitsu.media.anime.handleProgress
import ani.himitsu.media.cereal.Media
import ani.himitsu.openSettings
import ani.himitsu.others.LanguageMapper
import ani.himitsu.others.webview.CookieCatcher
import ani.himitsu.parsers.DynamicMangaParser
import ani.himitsu.parsers.MangaReadSources
import ani.himitsu.setMangaTimer
import ani.himitsu.settings.FAQActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.util.customAlertDialog
import bit.himitsu.content.metrics
import bit.himitsu.firebase.FireSale
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.string
import com.google.android.material.chip.Chip
import eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_SUBSCRIPTION_CHECK
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.WebViewUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MangaReadAdapter(
    private val media: Media,
    private val fragment: MangaReadFragment,
    private var mangaReadSources: MangaReadSources
) : RecyclerView.Adapter<MangaReadAdapter.ViewHolder>() {
    var subscribe: MediaDetailsActivity.PopImageButton? = null
    private var _binding: ItemAnimeWatchBinding? = null
    val hiddenScanlators = mutableListOf<String>()
    var scanlatorSelectionListener: ScanlatorSelectionListener? = null
    var options = listOf<String>()

    private val uiScope = MainScope()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val bind = ItemAnimeWatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(bind)
    }

    private var nestedDialog: AlertDialog? = null

    @SuppressLint("NotifyDataSetChanged")
    fun updateSources(mangaReadSources: MangaReadSources) {
        this.mangaReadSources = mangaReadSources
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        subscribeButton(false)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        _binding = binding
        binding.sourceTitle.setText(R.string.chaps)

        binding.faqbutton.setOnClickListener {
            fragment.requireContext().startActivity(
                Intent(fragment.requireContext(), FAQActivity::class.java)
            )
        }

        binding.reloadButton.setOnClickListener {
            fragment.reloadSource()
        }

        binding.animeImportEpisodes.setOnClickListener {
            val picker = DialogPickerBinding.inflate(fragment.layoutInflater).apply {
                playbackSpeedText.text = getString(R.string.import_chapter)
                playbackSpeed.minValue = 1
                playbackSpeed.maxValue = (media.manga?.totalChapters ?: 1)
                playbackSpeed.value = 1
                playbackConfirm.text = getString(R.string.select_chapter)
            }
            val dialog =  fragment.requireContext().customAlertDialog().apply {
                setCustomView(picker.root)
            }.show()
            picker.playbackConfirm.setOnClickListener {
                fragment.onImportDownloadClick(picker.playbackSpeed.value.string)
                dialog.dismiss()
            }
        }

        val noNetwork = !binding.root.context.isOnline
        val offline_ext = !PrefManager.getVal<Boolean>(PrefName.OfflineMode)
                || PrefManager.getVal<Boolean>(PrefName.OfflineExt)

        binding.animeSourceNameContainer.isGone = noNetwork || !offline_ext
        PrefManager.getVal<Boolean>(PrefName.ClientMode).let {
            binding.animeSourceNameContainer.isEnabled = !it
            binding.animeSource.isEnabled = !it
        }
        binding.animeSourceSettings.isGone = noNetwork || !offline_ext
        binding.animeSourceSearch.isGone = noNetwork || !offline_ext
        binding.animeSourceTitle.isGone = noNetwork || !offline_ext
        // Source Selection
        var source = media.selected!!.sourceIndex.let {
            if (it >= mangaReadSources.names.size) 0 else it
        }
        setLanguageList(media.selected!!.langIndex, source)
        if (mangaReadSources.names.isNotEmpty() && source in 0 until mangaReadSources.names.size) {
            binding.animeSource.setText(mangaReadSources.names[source])
            mangaReadSources[source].apply {
                binding.animeSourceTitle.text = showUserText
                showUserTextListener = { uiScope.launch { binding.animeSourceTitle.text = it } }
            }
        }

        // Wrong Title
        binding.animeSourceSearch.setOnClickListener {
            SourceSearchDialogFragment(mangaReadSources[source]).show(
                fragment.requireActivity().supportFragmentManager,
                null
            )
        }

        media.selected?.scanlators?.let {
            hiddenScanlators.addAll(it)
        }
        binding.animeSource.setAdapter(
            ArrayAdapter(
                fragment.requireContext(),
                R.layout.item_dropdown,
                mangaReadSources.names
            )
        )
        binding.animeSourceTitle.isSelected = true
        binding.animeSource.setOnItemClickListener { _, _, i, _ ->
            fragment.onSourceChange(i).apply {
                binding.animeSourceTitle.text = showUserText
                showUserTextListener = { uiScope.launch { binding.animeSourceTitle.text = it } }
                source = i
                setLanguageList(0, i)
                // Wrong Title
                binding.animeSourceSearch.setOnClickListener {
                    SourceSearchDialogFragment(mangaReadSources[i]).show(
                        fragment.requireActivity().supportFragmentManager,
                        null
                    )
                }
            }
            //invalidate if it's the last source
            val invalidate = i == mangaReadSources.names.size - 1
            fragment.loadChapters(i, invalidate)
        }

        binding.animeSourceLanguage.setOnItemClickListener { _, _, i, _ ->
            // Check if 'extension' and 'selected' properties exist and are accessible
            (mangaReadSources[source] as? DynamicMangaParser)?.let { ext ->
                ext.sourceLanguage = i
                fragment.onLangChange(i, ext.saveName)
                fragment.onSourceChange(media.selected!!.sourceIndex).apply {
                    binding.animeSourceTitle.text = showUserText
                    showUserTextListener =
                        { uiScope.launch { binding.animeSourceTitle.text = it } }
                    setLanguageList(i, source)
                }
                fragment.loadChapters(media.selected!!.sourceIndex, true)
            } ?: run {
            }
        }

        // settings
        binding.animeSourceSettings.setOnClickListener {
            (mangaReadSources[source] as? DynamicMangaParser)?.let { ext ->
                fragment.openSettings(ext.extension)
            }
        }

        // Grids
        subscribe = MediaDetailsActivity.PopImageButton(
            fragment.lifecycleScope,
            binding.animeSourceSubscribe,
            R.drawable.round_notifications_active_24,
            R.drawable.round_notifications_none_24,
            R.color.bg_opp,
            R.color.violet_400,
            fragment.subscribed,
            true
        ) {
            fragment.onNotificationPressed(it, binding.animeSource.text.toString())
        }

        binding.animeSourceSubscribe.setOnLongClickListener {
            openSettings(fragment.requireContext(), CHANNEL_SUBSCRIPTION_CHECK)
        }

        binding.animeNestedButton.setOnClickListener {
            val dialogView =
                LayoutInflater.from(fragment.requireContext()).inflate(R.layout.dialog_layout, null)
            val dialogBinding = DialogLayoutBinding.bind(dialogView)
            var refresh = false
            var run = false
            var reversed = media.selected!!.recyclerReversed
            var style =
                media.selected!!.recyclerStyle ?: PrefManager.getVal(PrefName.MangaDefaultView)
            dialogBinding.animeSourceTop.rotation = if (reversed) -90f else 90f
            dialogBinding.sortText.text = getString(
                if (reversed) R.string.descending else R.string.ascending
            )
            dialogBinding.animeSourceTop.setOnClickListener {
                reversed = !reversed
                dialogBinding.animeSourceTop.rotation = if (reversed) -90f else 90f
                dialogBinding.sortText.text = getString(
                    if (reversed) R.string.descending else R.string.ascending
                )
                run = true
            }

            // Grids
            dialogBinding.animeSourceGrid.visibility = View.GONE
            var selected = when (style) {
                0 -> dialogBinding.animeSourceList
                1 -> dialogBinding.animeSourceCompact
                else -> dialogBinding.animeSourceList
            }
            when (style) {
                0 -> dialogBinding.layoutText.setText(R.string.list)
                1 -> dialogBinding.layoutText.setText(R.string.compact)
                else -> dialogBinding.animeSourceList
            }
            selected.alpha = 1f
            fun selected(it: ImageButton) {
                selected.alpha = 0.33f
                selected = it
                selected.alpha = 1f
            }
            dialogBinding.animeSourceList.setOnClickListener {
                selected(it as ImageButton)
                style = 0
                dialogBinding.layoutText.setText(R.string.list)
                run = true
            }
            dialogBinding.animeSourceCompact.setOnClickListener {
                selected(it as ImageButton)
                style = 1
                dialogBinding.layoutText.setText(R.string.compact)
                run = true
            }
            dialogBinding.animeWebviewContainer.setOnClickListener {
                if (!WebViewUtil.supportsWebView(fragment.requireContext())) {
                    toast(R.string.webview_not_installed)
                }
                //start CookieCatcher activity
                if (mangaReadSources.names.isNotEmpty() && source in 0 until mangaReadSources.names.size) {
                    val sourceAHH = mangaReadSources[source] as? DynamicMangaParser
                    val sourceHttp = sourceAHH?.extension?.sources?.firstOrNull() as? HttpSource
                    val url = sourceHttp?.baseUrl
                    url?.let {
                        refresh = true
                        fragment.requireContext().startActivity(
                            Intent(fragment.requireContext(), CookieCatcher::class.java)
                                .putExtra("url", url)
                        )
                    }
                }
            }

            // Multi download
            dialogBinding.downloadNo.text = "0"
            dialogBinding.animeDownloadTop.setOnClickListener {
                //Alert dialog asking for the number of chapters to download
                val alertDialog = AlertDialog.Builder(currContext(), R.style.MyDialog)
                alertDialog.setTitle(getString(R.string.multi_chapter_download))
                alertDialog.setMessage(getString(R.string.multi_chapter_download_desc))
                val input = NumberPicker(currContext())
                input.minValue = 1
                input.maxValue = 20
                input.value = 1
                alertDialog.setView(input)
                alertDialog.setPositiveButton(R.string.ok) { _, _ ->
                    dialogBinding.downloadNo.text = input.value.string
                }
                alertDialog.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
                val dialog = alertDialog.show()
                dialog.window?.setDimAmount(0.8f)
            }

            // Scanlator
            dialogBinding.animeScanlatorContainer.isVisible = options.count() > 1
            dialogBinding.scanlatorNo.text = options.count().string
            dialogBinding.animeScanlatorTop.setOnClickListener {
                val dialogView2 =
                    LayoutInflater.from(currContext()).inflate(R.layout.custom_dialog_layout, null)
                val checkboxContainer =
                    dialogView2.findViewById<LinearLayout>(R.id.checkboxContainer)
                val tickAllButton = dialogView2.findViewById<ImageButton>(R.id.toggleButton)

                // Function to get the right image resource for the toggle button
                fun getToggleImageResource(container: ViewGroup): Int {
                    var allChecked = true
                    var allUnchecked = true

                    for (i in 0 until container.childCount) {
                        val checkBox = container.getChildAt(i) as CheckBox
                        if (!checkBox.isChecked) {
                            allChecked = false
                        } else {
                            allUnchecked = false
                        }
                    }
                    return when {
                        allChecked -> R.drawable.untick_all_boxes
                        allUnchecked -> R.drawable.tick_all_boxes
                        else -> R.drawable.invert_all_boxes
                    }
                }

                // Dynamically add checkboxes
                options.forEach { option ->
                    val checkBox = CheckBox(currContext()).apply {
                        text = option
                        setOnCheckedChangeListener { _, _ ->
                            // Update image resource when you change a checkbox
                            tickAllButton.setImageResource(getToggleImageResource(checkboxContainer))
                        }
                    }

                    // Set checked if its already selected
                    if (media.selected!!.scanlators != null) {
                        checkBox.isChecked = media.selected!!.scanlators?.contains(option) != true
                        scanlatorSelectionListener?.onScanlatorsSelected()
                    } else {
                        checkBox.isChecked = true
                    }
                    checkboxContainer.addView(checkBox)
                }

                // Create AlertDialog
                val dialog = AlertDialog.Builder(currContext(), R.style.MyDialog)
                    .setView(dialogView2)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        hiddenScanlators.clear()
                        for (i in 0 until checkboxContainer.childCount) {
                            val checkBox = checkboxContainer.getChildAt(i) as CheckBox
                            if (!checkBox.isChecked) {
                                hiddenScanlators.add(checkBox.text.toString())
                            }
                        }
                        fragment.onScanlatorChange(hiddenScanlators)
                        scanlatorSelectionListener?.onScanlatorsSelected()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                dialog.window?.setDimAmount(0.8f)

                // Standard image resource
                tickAllButton.setImageResource(getToggleImageResource(checkboxContainer))

                // Listens to ticked checkboxes and changes image resource accordingly
                tickAllButton.setOnClickListener {
                    // Toggle checkboxes
                    for (i in 0 until checkboxContainer.childCount) {
                        val checkBox = checkboxContainer.getChildAt(i) as CheckBox
                        checkBox.isChecked = !checkBox.isChecked
                    }

                    // Update image resource
                    tickAllButton.setImageResource(getToggleImageResource(checkboxContainer))
                }
            }

            nestedDialog = fragment.requireContext().customAlertDialog().apply {
                setTitle(R.string.options)
                setCustomView(dialogView)
                setPositiveButton(R.string.ok) {
                    if (run) fragment.onIconPressed(style, reversed)
                    if (dialogBinding.downloadNo.text != "0") {
                        fragment.multiDownload(dialogBinding.downloadNo.text.toString().toInt())
                    }
                    if (refresh) fragment.loadChapters(source, true)
                }
                setNegativeButton(R.string.cancel) {
                    if (refresh) fragment.loadChapters(source, true)
                }
                setOnCancelListener {
                    if (refresh) fragment.loadChapters(source, true)
                }
            }.show()
        }
        // Chapter Handling
        FireSale().getCurrent("${media.id}_current_chp", MediaType.MANGA) { handleChapters() }
        // FireSale().queryProgress(media.id, MediaType.MANGA) { handleChapters() }
    }

    fun subscribeButton(enabled: Boolean) {
        subscribe?.enabled(enabled)
    }

    // Chips
    fun updateChips(limit: Int, names: Array<String>, arr: Array<Int>, selected: Int = 0) {
        val binding = _binding
        if (binding != null) {
            val screenWidth = metrics.widthPixels
            val descending = PrefManager.getVal<Boolean>(PrefName.DescendingItems)
            var select: Chip? = null
            for (position in arr.indices) {
                val last = (if (position + 1 == arr.size) names.size else limit * (position + 1)) - 1
                val chip =
                    ItemChipBinding.inflate(
                        LayoutInflater.from(fragment.context),
                        binding.animeSourceChipGroup,
                        false
                    ).root
                chip.isCheckable = true
                fun selected() {
                    chip.isChecked = true
                    binding.animeWatchChipScroll.smoothScrollTo(
                        (chip.left - screenWidth / 2) + (chip.width / 2),
                        0
                    )
                }

                val startChapter = MediaNameAdapter.findChapterNumber(names[limit * (position)])
                val endChapter = MediaNameAdapter.findChapterNumber(names[last])
                val startChapterString = if (startChapter != null) {
                    "Ch.$startChapter"
                } else {
                    names[limit * (position)]
                }
                val endChapterString = if (endChapter != null) {
                    "Ch.$endChapter"
                } else {
                    names[last]
                }
                // chip.text = "${names[limit * (position)]} - ${names[last]}"
                val chipText = if (descending)
                    "$endChapterString - $startChapterString"
                else
                    "$startChapterString - $endChapterString"
                chip.text = chipText
                chip.setTextColor(
                    ContextCompat.getColorStateList(
                        fragment.requireContext(),
                        R.color.chip_text_color
                    )
                )

                chip.setOnClickListener {
                    selected()
                    fragment.onChipClicked(position, limit * (position), last)
                }
                if (descending)
                    binding.animeSourceChipGroup.addView(chip, 0)
                else
                    binding.animeSourceChipGroup.addView(chip)
                if (selected == position) {
                    selected()
                    select = chip
                }
            }
            if (select != null)
                binding.animeWatchChipScroll.apply {
                    post {
                        scrollTo(
                            (select.left - screenWidth / 2) + (select.width / 2),
                            0
                        )
                    }
                }
        }
    }

    fun clearChips() {
        _binding?.animeSourceChipGroup?.removeAllViews()
    }

    fun handleChapters() {
        val binding = _binding
        if (binding != null) {
            if (media.manga?.chapters != null) {
                val clientMode = PrefManager.getVal<Boolean>(PrefName.ClientMode)
                val chapters = media.manga.chapters!!.keys.toTypedArray()
                val anilistEp = (media.userProgress ?: -1).let {
                    if (it > 0) it + 1 else it // Completed
                }
                val appEp = PrefManager.getNullableCustomVal(
                    "${media.id}_current_chp",
                    null,
                    String::class.java
                )?.let {
                    MediaNameAdapter.findChapterNumber(it)?.toInt()
                } ?: -1
                var continueEp = (if (anilistEp > appEp) anilistEp else appEp).toString()
                val filteredChapters = chapters.filter { chapterKey ->
                    val chapter = media.manga.chapters!![chapterKey]!!
                    chapter.scanlator !in hiddenScanlators
                }
                val formattedChapters = filteredChapters.map {
                    MediaNameAdapter.findChapterNumber(it)?.toInt()?.toString()
                }
                if (formattedChapters.contains(continueEp)) {
                    continueEp = chapters[formattedChapters.indexOf(continueEp)]
                    binding.animeSourceContinue.visibility = View.VISIBLE
                    FireSale().getProgress("${media.id}_${continueEp}", MediaType.MANGA) {
                        handleProgress(
                            binding.itemEpisodeProgressCont,
                            binding.itemEpisodeProgress,
                            binding.itemEpisodeProgressEmpty,
                            media.id,
                            continueEp
                        )
                    }
                    if ((binding.itemEpisodeProgress.layoutParams as LinearLayout.LayoutParams).weight > 0.8f) {
                        val e = chapters.indexOf(continueEp)
                        if (e != -1 && e + 1 < chapters.size) {
                            continueEp = chapters[e + 1]
                        }
                    }
                    val ep = media.manga.chapters!![continueEp]!!
                    binding.itemEpisodeImage.loadImage(media.banner ?: media.cover)
                    binding.animeSourceContinueText.text = getString(
                        R.string.continue_chapter,
                        ep.number,
                        if (!ep.title.isNullOrEmpty()) ep.title else ""
                    )
                    binding.animeSourceContinue.setOnClickListener {
                        fragment.onMangaChapterClick(continueEp)
                    }
                    if (fragment.continueEp) {
                        if ((binding.itemEpisodeProgress.layoutParams as LinearLayout.LayoutParams).weight < 0.8f) {
                            binding.animeSourceContinue.performClick()
                            fragment.continueEp = false
                        }
                    }
                } else {
                    binding.animeSourceContinue.visibility = View.GONE
                }
                binding.animeSourceProgressBar.visibility = View.GONE

                val sourceFound = media.manga.chapters!!.isNotEmpty()
                binding.animeSourceNotFound.isGone = sourceFound || clientMode
                binding.faqbutton.isGone = sourceFound || clientMode
                binding.reloadButton.isGone = sourceFound || clientMode
            } else {
                binding.animeSourceContinue.visibility = View.GONE
                binding.animeSourceNotFound.visibility = View.GONE
                binding.faqbutton.visibility = View.GONE
                binding.reloadButton.visibility = View.GONE
                clearChips()
                binding.animeSourceProgressBar.visibility = View.VISIBLE
            }
        }
    }

    private fun setLanguageList(lang: Int, source: Int) {
        val binding = _binding
        val parser = mangaReadSources[source] as? DynamicMangaParser
        if (parser != null) {
            (mangaReadSources[source] as? DynamicMangaParser)?.let { ext ->
                ext.sourceLanguage = lang
            }
            try {
                binding?.animeSourceLanguage?.setText(
                    LanguageMapper.getExtensionItem(parser.extension.sources[lang])
                )
            } catch (_: IndexOutOfBoundsException) {
                binding?.animeSourceLanguage?.setText(
                    parser.extension.sources.firstOrNull()?.let {
                        LanguageMapper.getExtensionItem(it)
                    } ?: "Unknown"
                )
            }
            ArrayAdapter(
                fragment.requireContext(),
                R.layout.item_dropdown,
                parser.extension.sources.map { LanguageMapper.getExtensionItem(it) }
            ).let {
                binding?.animeSourceLanguageContainer?.isVisible = it.count > 1
                binding?.animeSourceLanguage?.setAdapter(it)
            }
        }
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(val binding: ItemAnimeWatchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            media.setMangaTimer(binding.animeSourceContainer)
        }
    }
}

interface ScanlatorSelectionListener {
    fun onScanlatorsSelected()
}
