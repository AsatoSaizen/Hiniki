package ani.himitsu.media.anime

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.FileUrl
import ani.himitsu.R
import ani.himitsu.connections.Status
import ani.himitsu.currActivity
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
import ani.himitsu.media.cereal.Media
import ani.himitsu.openLinkInYouTube
import ani.himitsu.openSettings
import ani.himitsu.others.LanguageMapper
import ani.himitsu.others.webview.CookieCatcher
import ani.himitsu.parsers.DynamicAnimeParser
import ani.himitsu.parsers.WatchSources
import ani.himitsu.setAnimeTimer
import ani.himitsu.settings.FAQActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.util.customAlertDialog
import bit.himitsu.content.metrics
import bit.himitsu.content.toPx
import bit.himitsu.firebase.FireSale
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.string
import bit.himitsu.webkit.ChromeIntegration
import bit.himitsu.webkit.setWebClickListeners
import com.google.android.material.chip.Chip
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_SUBSCRIPTION_CHECK
import eu.kanade.tachiyomi.util.system.WebViewUtil
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class AnimeWatchAdapter(
    private val media: Media,
    private val fragment: AnimeWatchFragment,
    private var watchSources: WatchSources
) : RecyclerView.Adapter<AnimeWatchAdapter.ViewHolder>() {
    var subscribe: MediaDetailsActivity.PopImageButton? = null
    private var _binding: ItemAnimeWatchBinding? = null

    private val uiScope = MainScope()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val bind = ItemAnimeWatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(bind)
    }

    private var nestedDialog: AlertDialog? = null

    @SuppressLint("NotifyDataSetChanged")
    fun updateSources(watchSources: WatchSources) {
        this.watchSources = watchSources
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        subscribeButton(false)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        _binding = binding

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
                playbackSpeedText.text = getString(R.string.import_episode)
                playbackSpeed.minValue = 1
                playbackSpeed.maxValue = (media.anime?.totalEpisodes ?: 1)
                playbackSpeed.value = 1
                playbackConfirm.text = getString(R.string.select_episode)
            }
            val dialog =  fragment.requireContext().customAlertDialog().apply {
                setCustomView(picker.root)
            }.show()
            picker.playbackConfirm.setOnClickListener {
                fragment.onImportDownloadClick(picker.playbackSpeed.value.string)
                dialog.dismiss()
            }
        }

        binding.animeSourceDubbed.isChecked = media.selected!!.preferDub
        binding.animeSourceDubbedText.text =
            if (media.selected!!.preferDub) currActivity()!!.getString(R.string.dubbed) else currActivity()!!.getString(
                R.string.subbed
            )

        // Prefer Dub
        var changing = false
        binding.animeSourceDubbed.setOnCheckedChangeListener { _, isChecked ->
            binding.animeSourceDubbedText.text =
                if (isChecked) currActivity()!!.getString(R.string.dubbed) else currActivity()!!.getString(
                    R.string.subbed
                )
            if (!changing) fragment.onDubClicked(isChecked)
        }

        val clientMode = PrefManager.getVal<Boolean>(PrefName.ClientMode)
        val noNetwork = !binding.root.context.isOnline
        val offline_ext = !PrefManager.getVal<Boolean>(PrefName.OfflineMode)
                || PrefManager.getVal<Boolean>(PrefName.OfflineExt)

        val buttons = binding.streamingButtons
        if ((PrefManager.getVal(PrefName.ShowYtButton) || clientMode) && buttons.childCount == 0) {
            media.externalLinks.youtube?.let { url ->
                val youtube = getStreamIcon(buttons, R.color.youtube_red, R.string.icon_youtube, url)
                youtube.setOnClickListener { openLinkInYouTube(url) }
                buttons.addView(youtube)
            }
            media.externalLinks.adn?.let { url ->
                buttons.addView(getStreamIcon(buttons, R.color.adn_blue, R.string.icon_adn, url))
            }
            media.externalLinks.hulu?.let { url ->
                buttons.addView(getStreamIcon(buttons, R.drawable.gradient_hulu, R.string.icon_hulu, url))
            }
            media.externalLinks.amazon?.let { url ->
                buttons.addView(getStreamIcon(buttons, R.color.prime_blue, R.string.icon_prime, url))
            }
            media.externalLinks.netflix?.let { url ->
                buttons.addView(getStreamIcon(buttons, R.color.netflix_black, R.string.icon_netflix, url))
            }
            media.externalLinks.disney?.let { url ->
                buttons.addView(getStreamIcon(buttons, R.color.disney_teal, R.string.icon_disney, url))
            }
            media.externalLinks.max?.let { url ->
                buttons.addView( getStreamIcon(buttons, R.color.max_dark_blue, R.string.icon_max, url))
            }
            media.externalLinks.tubi?.let { url ->
                buttons.addView(getStreamIcon(buttons, R.drawable.gradient_tubi, R.string.icon_tubi, url))
            }
            media.externalLinks.hidive?.let { url ->
                buttons.addView(getStreamIcon(buttons, R.color.hidive_blue, R.string.icon_hidive, url))
            }
            media.externalLinks.crunchy != null
            if (media.streamingEpisodes.isNotEmpty()) {
                binding.episodeRecyclerView.adapter = StreamingAdapter(media)
            }
            media.externalLinks.crunchy?.let { url ->
                val crunchyroll = getStreamIcon(buttons, R.color.crunchyroll_orange, R.string.icon_crunchyroll, url)
                crunchyroll.setOnClickListener(null)
                crunchyroll.setOnClickListener {
                    if (media.streamingEpisodes.isEmpty()
                        || (media.sequel != null && media.sequel!!.status != Status.UNRELEASED)) {
                        ChromeIntegration.openStreamDialog(fragment.requireContext(), url, media)
                        return@setOnClickListener
                    } else {
                        binding.episodeRecyclerView.isVisible =
                            !binding.episodeRecyclerView.isVisible
                    }
                }
                buttons.addView(crunchyroll)
                if (clientMode && media.streamingEpisodes.isNotEmpty() && media.sequel == null)
                    crunchyroll.performClick()
            }
            if (buttons.childCount > 0) {
                binding.streamContainer.isVisible = true
            }
        }

        binding.animeSourceNameContainer.isGone = noNetwork || !offline_ext
        clientMode.let {
            binding.animeSourceNameContainer.isEnabled = !it
            binding.animeSource.isEnabled = !it
        }
        binding.animeSourceSettings.isGone = noNetwork || !offline_ext
        binding.animeSourceSearch.isGone = noNetwork || !offline_ext
        binding.animeSourceTitle.isGone = noNetwork || !offline_ext

        // Source Selection
        var source = media.selected!!.sourceIndex.let {
            if (it >= watchSources.names.size) 0 else it
        }
        setLanguageList(media.selected!!.langIndex, source)
        if (watchSources.names.isNotEmpty() && source in 0 until watchSources.names.size) {
            binding.animeSource.setText(watchSources.names[source])
            watchSources[source].apply {
                this.selectDub = media.selected!!.preferDub
                binding.animeSourceTitle.text = showUserText
                showUserTextListener = { uiScope.launch { binding.animeSourceTitle.text = it } }
                binding.animeSourceDubbedCont.isVisible = isDubAvailableSeparately()
            }
        }

        // Wrong Title
        binding.animeSourceSearch.setOnClickListener {
            SourceSearchDialogFragment(watchSources[source]).show(
                fragment.requireActivity().supportFragmentManager,
                null
            )
        }

        binding.animeSource.setAdapter(
            ArrayAdapter(
                fragment.requireContext(),
                R.layout.item_dropdown,
                watchSources.names
            )
        )
        binding.animeSourceTitle.isSelected = true
        binding.animeSource.setOnItemClickListener { _, _, i, _ ->
            fragment.onSourceChange(i).apply {
                binding.animeSourceTitle.text = showUserText
                showUserTextListener = { uiScope.launch { binding.animeSourceTitle.text = it } }
                changing = true
                binding.animeSourceDubbed.isChecked = selectDub
                changing = false
                binding.animeSourceDubbedCont.isVisible = isDubAvailableSeparately()
                source = i
                setLanguageList(0, i)
                // Wrong Title
                binding.animeSourceSearch.setOnClickListener {
                    SourceSearchDialogFragment(watchSources[i]).show(
                        fragment.requireActivity().supportFragmentManager,
                        null
                    )
                }
            }
            fragment.loadEpisodes(i, false)
        }

        binding.animeSourceLanguage.setOnItemClickListener { _, _, i, _ ->
            // Check if 'extension' and 'selected' properties exist and are accessible
            (watchSources[source] as? DynamicAnimeParser)?.let { ext ->
                ext.sourceLanguage = i
                fragment.onLangChange(i)
                fragment.onSourceChange(media.selected!!.sourceIndex).apply {
                    binding.animeSourceTitle.text = showUserText
                    showUserTextListener =
                        { uiScope.launch { binding.animeSourceTitle.text = it } }
                    changing = true
                    binding.animeSourceDubbed.isChecked = selectDub
                    changing = false
                    binding.animeSourceDubbedCont.isVisible = isDubAvailableSeparately()
                    setLanguageList(i, source)
                }
                fragment.loadEpisodes(media.selected!!.sourceIndex, true)
            } ?: run { }
        }

        // settings
        binding.animeSourceSettings.setOnClickListener {
            (watchSources[source] as? DynamicAnimeParser)?.let { ext ->
                fragment.openSettings(ext.extension)
            }
        }

        // subscribe
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

        // Nested Button
        binding.animeNestedButton.setOnClickListener {
            val dialogView =
                LayoutInflater.from(fragment.requireContext()).inflate(R.layout.dialog_layout, null)
            val dialogBinding = DialogLayoutBinding.bind(dialogView)
            var refresh = false
            var run = false
            var reversed = media.selected!!.recyclerReversed
            var style =
                media.selected!!.recyclerStyle ?: PrefManager.getVal(PrefName.AnimeDefaultView)
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
            //Grids
            var selected = when (style) {
                0 -> dialogBinding.animeSourceList
                1 -> dialogBinding.animeSourceGrid
                2 -> dialogBinding.animeSourceCompact
                else -> dialogBinding.animeSourceList
            }
            when (style) {
                0 -> dialogBinding.layoutText.setText(R.string.list)
                1 -> dialogBinding.layoutText.setText(R.string.grid)
                2 -> dialogBinding.layoutText.setText(R.string.compact)
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
            dialogBinding.animeSourceGrid.setOnClickListener {
                selected(it as ImageButton)
                style = 1
                dialogBinding.layoutText.setText(R.string.grid)
                run = true
            }
            dialogBinding.animeSourceCompact.setOnClickListener {
                selected(it as ImageButton)
                style = 2
                dialogBinding.layoutText.setText(R.string.compact)
                run = true
            }
            dialogBinding.animeWebviewContainer.setOnClickListener {
                if (!WebViewUtil.supportsWebView(fragment.requireContext())) {
                    toast(R.string.webview_not_installed)
                }
                //start CookieCatcher activity
                if (watchSources.names.isNotEmpty() && source in 0 until watchSources.names.size) {
                    val sourceAHH = watchSources[source] as? DynamicAnimeParser
                    val sourceHttp =
                        sourceAHH?.extension?.sources?.firstOrNull() as? AnimeHttpSource
                    val url = sourceHttp?.baseUrl
                    url?.let {
                        refresh = true
                        val headersMap = try {
                            sourceHttp.headers.toMultimap()
                                .mapValues { it.value.getOrNull(0) ?: "" }
                        } catch (_: Exception) {
                            emptyMap()
                        }
                        fragment.requireContext().startActivity(
                            Intent(fragment.requireContext(), CookieCatcher::class.java)
                                .putExtra("url", url)
                                .putExtra("headers", headersMap as HashMap<String, String>)
                        )
                    }
                }
            }

            // hidden
            dialogBinding.animeScanlatorContainer.visibility = View.GONE
            dialogBinding.animeDownloadContainer.visibility = View.GONE

            nestedDialog = fragment.requireContext().customAlertDialog().apply {
                setTitle(R.string.options)
                    setCustomView(dialogView)
                    setPositiveButton(R.string.ok) {
                        if (run) fragment.onIconPressed(style, reversed)
                        if (refresh) fragment.loadEpisodes(source, true)
                    }
                    setNegativeButton(R.string.cancel) {
                        if (refresh) fragment.loadEpisodes(source, true)
                    }
                    setOnCancelListener {
                        if (refresh) fragment.loadEpisodes(source, true)
                    }
            }.show()
        }
        // Episode Handling
        // FireSale().getCurrent("${media.id}_current_ep", MediaType.ANIME) { handleEpisodes() }
        FireSale().getSeason(media.id, MediaType.ANIME) { handleEpisodes() }
    }

    private fun getStreamIcon(
        parent: LinearLayout, background: Int, imageRes: Int, url: String?
    ): ImageButton {
        val icon = LayoutInflater.from(fragment.context)
            .inflate(R.layout.streaming_button, parent, false) as ImageButton
        icon.isVisible = true
        icon.setBackgroundResource(background)
        icon.loadImage(getString(imageRes), 48.toPx)
        icon.setWebClickListeners(url, media)
        return icon
    }

    fun subscribeButton(enabled: Boolean) {
        subscribe?.enabled(enabled)
    }

    // Chips
    fun updateChips(limit: Int, names: Array<String>, arr: Array<Int>, selected: Int = 0) {
        val binding = _binding
        if (binding != null) {
            val screenWidth = metrics.widthPixels
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

                val chipText = "${names[limit * (position)]} - ${names[last]}"
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
                binding.animeSourceChipGroup.addView(chip)
                if (selected == position) {
                    selected()
                    select = chip
                }
            }
            if (select != null)
                binding.animeWatchChipScroll.apply {
                    post {
                        scrollTo((select.left - screenWidth / 2) + (select.width / 2), 0)
                    }
                }
        }
    }

    fun clearChips() {
        _binding?.animeSourceChipGroup?.removeAllViews()
    }

    fun handleEpisodes() {
        val binding = _binding
        if (binding != null) {
            if (media.anime?.episodes != null) {
                val clientMode = PrefManager.getVal<Boolean>(PrefName.ClientMode)
                val episodes = media.anime.episodes!!.keys.toTypedArray()
                val anilistEp = (media.userProgress ?: -1).let {
                    if (it > 0) it + 1 else it // Completed
                }
                val appEp = PrefManager.getCustomVal<String?>(
                    "${media.id}_current_ep", ""
                )?.toIntOrNull() ?: -1
                var continueEp = (if (anilistEp > appEp) anilistEp else appEp).toString()
                if (episodes.contains(continueEp)) {
                    binding.animeSourceContinue.visibility = View.VISIBLE
                    handleProgress(
                        binding.itemEpisodeProgressCont,
                        binding.itemEpisodeProgress,
                        binding.itemEpisodeProgressEmpty,
                        media.id,
                        continueEp
                    )
                    if ((binding.itemEpisodeProgress.layoutParams as LinearLayout.LayoutParams).weight > PrefManager.getVal<Float>(
                            PrefName.WatchPercentage
                        )
                    ) {
                        val e = episodes.indexOf(continueEp)
                        if (e != -1 && e + 1 < episodes.size) {
                            continueEp = episodes[e + 1]
                            handleProgress(
                                binding.itemEpisodeProgressCont,
                                binding.itemEpisodeProgress,
                                binding.itemEpisodeProgressEmpty,
                                media.id,
                                continueEp
                            )
                        }
                    }
                    val ep = media.anime.episodes!![continueEp]!!

                    val cleanedTitle = ep.title?.let { MediaNameAdapter.removeEpisodeNumber(it) }

                    binding.itemEpisodeImage.loadImage(
                        ep.thumb ?: FileUrl[media.banner ?: media.cover], 0
                    )
                    binding.itemEpisodeFillerView.isVisible = ep.filler

                    binding.animeSourceContinueText.text = getString(
                            R.string.continue_episode, ep.number, if (ep.filler)
                                getString(R.string.filler_tag)
                            else
                                "", cleanedTitle
                        )
                    binding.animeSourceContinue.setOnClickListener {
                        fragment.onEpisodeClick(continueEp)
                    }
                    if (fragment.continueEp) {
                        if (
                            (binding.itemEpisodeProgress.layoutParams as LinearLayout.LayoutParams)
                                .weight < PrefManager.getVal<Float>(PrefName.WatchPercentage)
                        ) {
                            binding.animeSourceContinue.performClick()
                            fragment.continueEp = false
                        }
                    }
                } else {
                    binding.animeSourceContinue.visibility = View.GONE
                }

                binding.animeSourceProgressBar.visibility = View.GONE

                val sourceFound = media.anime.episodes!!.isNotEmpty()
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
        val parser = watchSources[source] as? DynamicAnimeParser
        if (parser != null) {
            (watchSources[source] as? DynamicAnimeParser)?.let { ext ->
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
            media.setAnimeTimer(binding.animeSourceContainer)
        }
    }
}
