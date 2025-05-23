package ani.himitsu.media.anime

import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TrackType
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.BottomSheetSubtitlesBinding
import ani.himitsu.databinding.ItemSubtitleTextBinding
import ani.himitsu.others.LanguageMapper.getTrackItem
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import eu.kanade.tachiyomi.util.system.getThemeColor
import java.util.Locale


@OptIn(UnstableApi::class)
class TrackGroupDialogFragment(
    private var instance: ExoplayerView,
    private var trackGroups: ArrayList<Tracks.Group>,
    private var type: @TrackType Int
) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSubtitlesBinding? = null
    private val binding by lazy { _binding!! }
    private var languages = mutableListOf<String>()

    init {
        languages = instance.audioLanguages
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSubtitlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (type == TRACK_TYPE_AUDIO) binding.selectionTitle.text = getString(R.string.audio_tracks)
        binding.subtitlesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.subtitlesRecycler.adapter = TrackGroupAdapter()
    }

    inner class TrackGroupAdapter : RecyclerView.Adapter<TrackGroupAdapter.StreamViewHolder>() {
        inner class StreamViewHolder(val binding: ItemSubtitleTextBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder =
            StreamViewHolder(
                ItemSubtitleTextBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        @OptIn(UnstableApi::class)
        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            val binding = holder.binding
            trackGroups[position].let { trackGroup ->
                val trackFormat = trackGroup.mediaTrackGroup.getFormat(0)
                val localeCode = if (trackGroup.mediaTrackGroup.type == TRACK_TYPE_AUDIO)
                    languages.getOrNull(position - (trackGroups.size - languages.size))
                else
                    null
                when (val language = trackFormat.language) {
                    null -> {
                        binding.subtitleTitle.text = localeCode?.let {
                            getTrackItem(it)
                                ?: "[${String.format(Locale.ROOT, "%02d", position)}] $it"
                        } ?: getString(
                            R.string.unknown_track, String.format(Locale.ROOT, "%02d", position)
                        )
                    }

                    "none" -> {
                        binding.subtitleTitle.text = getString(R.string.disabled_track)
                    }

                    "file" -> {
                        val document = DocumentFile.fromSingleUri(instance, Uri.parse(
                            "content:${trackFormat.id?.substringAfter("content:")}"
                        ))
                        binding.subtitleTitle.text =
                            getString(R.string.user_subtitle, document?.name)
                    }

                    "load" -> {
                        binding.subtitleTitle.text = getString(R.string.load_subtitle)
                    }

                    else -> {
                        binding.subtitleTitle.text = getTrackItem(language)
                            ?: if (language.length > 2
                                && !language.contains("-")
                                && !language.contains("_"))
                                "[${String.format(Locale.ROOT, "%02d", position)}] $language"
                            else
                                getString(R.string.unknown_track, language)
                    }
                }
                trackFormat.label?.let {
                    binding.subtitleLabel.text = it
                    binding.subtitleLabel.visibility = View.VISIBLE
                }
                if (trackGroup.isSelected) {
                    val selected = "✔ ${binding.subtitleTitle.text}"
                    binding.subtitleTitle.text = selected
                    with(binding.root.context) {
                        binding.root.setCardBackgroundColor(
                            getThemeColor(com.google.android.material.R.attr.colorPrimary)
                        )
                    }
                }
                binding.root.setOnClickListener {
                    dismiss()
                    instance.onSetTrackGroupOverride(trackGroup, type)
                }
                binding.root.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (trackFormat.language == "file") {
                        instance.onRemoveSubtitleFile(
                            "content:${trackFormat.id?.substringAfter("content:")}"
                        )
                        dismiss()
                        true
                    } else { false }
                }
            }
        }

        override fun getItemCount(): Int = trackGroups.size
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}
