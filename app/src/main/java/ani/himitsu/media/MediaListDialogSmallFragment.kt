package ani.himitsu.media

import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.mal.MAL
import ani.himitsu.databinding.BottomSheetMediaListSmallBinding
import ani.himitsu.media.cereal.Media
import ani.himitsu.others.getSerialized
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.snackString
import ani.himitsu.toast
import ani.himitsu.util.InputFilterMinMax
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import java.io.Serializable


class MediaListDialogSmallFragment : BottomSheetDialogFragment() {

    private lateinit var media: Media

    companion object {
        fun newInstance(m: Media): MediaListDialogSmallFragment =
            MediaListDialogSmallFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("media", m as Serializable)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            media = it.getSerialized("media")!!
        }
    }

    private var _binding: BottomSheetMediaListSmallBinding? = null
    private val binding by lazy { _binding!! }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMediaListSmallBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val scope = viewLifecycleOwner.lifecycleScope
        binding.mediaListDelete.setOnClickListener {
            // TODO: Offer to clear settings
            var id = media.userListId
            viewLifecycleOwner.lifecycleScope.launch {
                withIOContext {
                    if (id != null) {
                        try {
                            AniList.mutation.deleteList(id!!)
                            MAL.query.deleteList(media.anime != null, media.idMAL)
                        } catch (e: Exception) {
                            withUIContext {
                                snackString(getString(R.string.delete_fail_reason, e.message))
                            }
                            return@withIOContext
                        }
                    } else {
                        with (AniList.query.userMediaDetails(media)) {
                            userListId?.let { listId ->
                                id = listId
                                AniList.mutation.deleteList(listId)
                                MAL.query.deleteList(anime != null, idMAL)
                            }
                        }
                    }
                }
                withUIContext {
                    if (id != null) {
                        Refresh.all()
                        toast(getString(R.string.deleted_from_list))
                        dismissAllowingStateLoss()
                    } else {
                        toast(getString(R.string.no_list_id))
                    }
                }
            }
        }

        binding.mediaListProgressBar.visibility = View.GONE
        binding.mediaListLayout.visibility = View.VISIBLE
        val statuses: Array<String> = resources.getStringArray(R.array.status)
        val statusStrings =
            if (media.manga == null)
                resources.getStringArray(R.array.status_anime)
            else
                resources.getStringArray(R.array.status_manga)
        val userStatus =
            if (media.userStatus != null) statusStrings[statuses.indexOf(media.userStatus)] else statusStrings[0]

        binding.mediaListStatus.setText(userStatus)
        binding.mediaListStatus.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.item_dropdown,
                statusStrings
            )
        )

        binding.mediaListProgress.setText(if (media.userProgress != null) media.userProgress.toString() else "")
        val total: Int? = media.anime?.let { anime ->
            anime.totalEpisodes?.let { total ->
                binding.mediaListProgress.filters =
                    arrayOf(
                        InputFilterMinMax(0.0, total.toDouble(), binding.mediaListStatus),
                        LengthFilter(total.toString().length)
                    )
                total
            }
        } ?: media.manga?.let { manga ->
            manga.totalChapters?.let { total ->
                binding.mediaListProgress.filters =
                    arrayOf(
                        InputFilterMinMax(0.0, total.toDouble(), binding.mediaListStatus),
                        LengthFilter(total.toString().length)
                    )
                total
            }
        }

        binding.mediaListProgressLayout.suffixText = " / ${total ?: '?'}"
        binding.mediaListProgressLayout.suffixTextView.updateLayoutParams {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.mediaListProgressLayout.suffixTextView.gravity = Gravity.CENTER

        binding.mediaListVolumesLayout.isVisible = media.manga != null
        binding.mediaListVolumes.setText(if (media.userVolumes != null) media.userVolumes.toString() else "")

        binding.mediaListScore.setText(
            if (media.userScore != 0) media.userScore.div(
                10.0
            ).toString() else ""
        )
        binding.mediaListScore.filters =
            arrayOf(InputFilterMinMax(1.0, 10.0), LengthFilter(10.0.toString().length))
        binding.mediaListScoreLayout.suffixTextView.updateLayoutParams {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.mediaListScoreLayout.suffixTextView.gravity = Gravity.CENTER

        binding.mediaListIncrement.setOnClickListener {
            if (binding.mediaListStatus.text.toString() == statusStrings[0])
                binding.mediaListStatus.setText(statusStrings[1], false)
            val init =
                if (binding.mediaListProgress.text.toString() != "")
                    binding.mediaListProgress.text.toString().toInt()
                else
                    0
            if (init < (total ?: 5000)) {
                val progressText = "${init + 1}"
                binding.mediaListProgress.setText(progressText)
            }
            if (media.anime?.nextAiringEpisode != total) return@setOnClickListener
            if (init + 1 == (total ?: 5000)) {
                binding.mediaListStatus.setText(statusStrings[2], false)
            }
        }

        binding.mediaListVolumesIncrement.setOnClickListener {
            if (binding.mediaListStatus.text.toString() == statusStrings[0])
                binding.mediaListStatus.setText(statusStrings[1], false)
            val init =
                if (binding.mediaListVolumes.text.toString() != "")
                    binding.mediaListVolumes.text.toString().toInt()
                else
                    0
            val progressText = "${init + 1}"
            binding.mediaListVolumes.setText(progressText)
        }

        binding.mediaListPrivate.isChecked = media.isListPrivate
        binding.mediaListPrivate.setOnCheckedChangeListener { _, checked ->
            media.isListPrivate = checked
        }
        val removeList = PrefManager.getCustomVal("removeList", setOf<Int>())
        var remove: Boolean? = null
        binding.mediaListShow.isChecked = media.id in removeList
        binding.mediaListShow.setOnCheckedChangeListener { _, checked ->
            remove = checked
        }
        binding.mediaListSave.setOnClickListener {
            scope.launch {
                withIOContext {
                    val progress = _binding?.mediaListProgress?.text.toString().toIntOrNull()
                    val progressVolumes = _binding?.mediaListVolumes?.text.toString().toIntOrNull()
                    val score = (_binding?.mediaListScore?.text.toString().toDoubleOrNull()
                        ?.times(10))?.toInt()
                    val status =
                        statuses[statusStrings.indexOf(_binding?.mediaListStatus?.text.toString())]
                    AniList.mutation.editList(
                        media.id,
                        progress,
                        progressVolumes,
                        score,
                        null,
                        null,
                        status,
                        media.isListPrivate
                    )
                    MAL.query.editList(
                        media.idMAL,
                        media.anime != null,
                        progress,
                        score,
                        status
                    )
                }
                remove?.let {
                    if (it) {
                        PrefManager.setCustomVal("removeList", removeList.plus(media.id))
                    } else {
                        PrefManager.setCustomVal("removeList", removeList.minus(media.id))
                    }
                }
                Refresh.all()
                toast(getString(R.string.list_updated))
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
