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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.FuzzyDate
import ani.himitsu.connections.mal.MAL
import ani.himitsu.databinding.BottomSheetMediaListBinding
import ani.himitsu.media.cereal.Media
import ani.himitsu.navBarHeight
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.toast
import ani.himitsu.tryWith
import ani.himitsu.util.InputFilterMinMax
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import ani.himitsu.view.dialog.DatePickerFragment
import bit.himitsu.nio.string
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext


class MediaListDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMediaListBinding? = null
    private val binding by lazy { _binding!! }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMediaListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mediaListContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        var media: Media?
        val model: MediaDetailsViewModel by activityViewModels()
        val scope = viewLifecycleOwner.lifecycleScope

        model.getMedia().observe(this) { it ->
            media = it
            if (media != null) {
                binding.mediaListProgressBar.visibility = View.GONE
                binding.mediaListLayout.visibility = View.VISIBLE

                val statuses: Array<String> = resources.getStringArray(R.array.status)
                val statusStrings =
                    if (media?.manga == null)
                        resources.getStringArray(R.array.status_anime)
                    else
                        resources.getStringArray(R.array.status_manga)
                val userStatus = if (media!!.userStatus != null)
                    statusStrings[statuses.indexOf(media!!.userStatus)]
                else
                    statusStrings[0]

                binding.mediaListStatus.setText(userStatus)
                binding.mediaListStatus.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        R.layout.item_dropdown,
                        statusStrings
                    )
                )

                binding.mediaListProgress.setText(if (media!!.userProgress != null) media!!.userProgress.toString() else "")
                val total: Int? = media!!.anime?.let { anime ->
                    anime.totalEpisodes?.let { total ->
                        binding.mediaListProgress.filters =
                            arrayOf(
                                InputFilterMinMax(0.0, total.toDouble(), binding.mediaListStatus),
                                LengthFilter(total.toString().length)
                            )
                        total
                    }
                } ?: media!!.manga?.let { manga ->
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

                binding.mediaListVolumesLayout.isVisible = media!!.manga != null
                binding.mediaListVolumes.setText(if (media.userVolumes != null) media.userVolumes.toString() else "")

                binding.mediaListScore.setText(
                    if (media!!.userScore != 0) media!!.userScore.div(
                        10.0
                    ).toString() else ""
                )
                binding.mediaListScore.filters =
                    arrayOf(InputFilterMinMax(1.0, 10.0), LengthFilter(10.0.toString().length))
                binding.mediaListScoreLayout.suffixTextView.updateLayoutParams {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                binding.mediaListScoreLayout.suffixTextView.gravity = Gravity.CENTER

                val start = DatePickerFragment(requireActivity(), media!!.userStartedAt)
                val end = DatePickerFragment(requireActivity(), media!!.userCompletedAt)
                binding.mediaListStart.setText(media!!.userStartedAt.toStringOrEmpty())
                binding.mediaListStart.setOnClickListener {
                    tryWith(false) {
                        if (!start.dialog.isShowing) start.dialog.show()
                    }
                }
                binding.mediaListStart.setOnFocusChangeListener { _, b ->
                    tryWith(false) {
                        if (b && !start.dialog.isShowing) start.dialog.show()
                    }
                }
                binding.mediaListEnd.setText(media!!.userCompletedAt.toStringOrEmpty())
                binding.mediaListEnd.setOnClickListener {
                    tryWith(false) {
                        if (!end.dialog.isShowing) end.dialog.show()
                    }
                }
                binding.mediaListEnd.setOnFocusChangeListener { _, b ->
                    tryWith(false) {
                        if (b && !end.dialog.isShowing) end.dialog.show()
                    }
                }
                start.dialog.setOnDismissListener {
                    _binding?.mediaListStart?.setText(start.date.toStringOrEmpty())
                }
                end.dialog.setOnDismissListener {
                    _binding?.mediaListEnd?.setText(end.date.toStringOrEmpty())
                }

                fun onComplete() {
                    binding.mediaListProgress.setText(total.toString())
                    if (start.date.year == null) {
                        start.date = FuzzyDate().getToday()
                        binding.mediaListStart.setText(start.date.toString())
                    }
                    end.date = FuzzyDate().getToday()
                    binding.mediaListEnd.setText(end.date.toString())
                }

                var startBackupDate: FuzzyDate? = null
                var endBackupDate: FuzzyDate? = null
                var progressBackup: String? = null
                binding.mediaListStatus.setOnItemClickListener { _, _, i, _ ->
                    if (i == 2 && total != null) {
                        startBackupDate = start.date
                        endBackupDate = end.date
                        progressBackup = binding.mediaListProgress.text.toString()
                        onComplete()
                    } else {
                        if (!progressBackup.isNullOrBlank())
                            binding.mediaListProgress.setText(progressBackup)
                        if (startBackupDate != null) {
                            binding.mediaListStart.setText(startBackupDate.toString())
                            start.date = startBackupDate
                        }
                        if (endBackupDate != null) {
                            binding.mediaListEnd.setText(endBackupDate.toString())
                            end.date = endBackupDate
                        }
                    }
                }

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
                    if (media?.anime?.nextAiringEpisode != total) return@setOnClickListener
                    if (init + 1 == (total ?: 5000)) {
                        binding.mediaListStatus.setText(statusStrings[2], false)
                        onComplete()
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

                binding.mediaListPrivate.isChecked = media?.isListPrivate == true
                binding.mediaListPrivate.setOnCheckedChangeListener { _, checked ->
                    media?.isListPrivate = checked
                }
                val removeList = PrefManager.getCustomVal("removeList", setOf<Int>())
                var remove: Boolean? = null
                binding.mediaListShow.isChecked = media?.id in removeList
                binding.mediaListShow.setOnCheckedChangeListener { _, checked ->
                    remove = checked
                }
                media?.userRepeat?.apply {
                    binding.mediaListRewatch.setText(this.string)
                }

                media?.notes?.apply {
                    binding.mediaListNotes.setText(this)
                }

                if (media?.inCustomListsOf?.isEmpty() != false)
                    binding.mediaListAddCustomList.apply {
                        (parent as? ViewGroup)?.removeView(this)
                    }

                media?.inCustomListsOf?.forEach {
                    MaterialSwitch(requireContext()).apply {
                        isChecked = it.value
                        text = it.key
                        setOnCheckedChangeListener { _, isChecked ->
                            media?.inCustomListsOf?.put(it.key, isChecked)
                        }
                        binding.mediaListCustomListContainer.addView(this)
                    }
                }

                binding.mediaListSave.setOnClickListener {
                    scope.launch {
                        withIOContext {
                            if (media != null) {
                                val progress =
                                    _binding?.mediaListProgress?.text.toString().toIntOrNull()
                                val progressVolumes =
                                    _binding?.mediaListVolumes?.text.toString().toIntOrNull()
                                val score =
                                    (_binding?.mediaListScore?.text.toString().toDoubleOrNull()
                                        ?.times(10))?.toInt()
                                val status =
                                    statuses[statusStrings.indexOf(_binding?.mediaListStatus?.text.toString())]
                                val rewatch =
                                    _binding?.mediaListRewatch?.text?.toString()?.toIntOrNull()
                                val notes = _binding?.mediaListNotes?.text?.toString()
                                val startD = start.date
                                val endD = end.date
                                AniList.mutation.editList(
                                    media!!.id,
                                    progress,
                                    progressVolumes,
                                    score,
                                    rewatch,
                                    notes,
                                    status,
                                    media?.isListPrivate == true,
                                    startD,
                                    endD,
                                    media?.inCustomListsOf?.mapNotNull { if (it.value) it.key else null }
                                )
                                MAL.query.editList(
                                    media!!.idMAL,
                                    media!!.anime != null,
                                    progress,
                                    score,
                                    status,
                                    rewatch,
                                    startD,
                                    endD
                                )
                            }
                        }
                        media?.id?.let { item ->
                            remove?.let {
                                if (it) {
                                    PrefManager.setCustomVal("removeList", removeList.plus(item))
                                } else {
                                    PrefManager.setCustomVal("removeList", removeList.minus(item))
                                }
                            }
                        }
                        Refresh.all()
                        toast(getString(R.string.list_updated))
                        dismissAllowingStateLoss()
                    }
                }

                binding.mediaListDelete.setOnClickListener {
                    // TODO: Offer to clear settings
                    var id = media!!.userListId
                    scope.launch {
                        withIOContext {
                            if (id != null) {
                                AniList.mutation.deleteList(id!!)
                                MAL.query.deleteList(media?.anime != null, media?.idMAL)
                            } else {
                                with(AniList.query.userMediaDetails(media!!)) {
                                    userListId?.let { listId ->
                                        id = listId
                                        AniList.mutation.deleteList(listId)
                                        MAL.query.deleteList(anime != null, idMAL)
                                    }
                                }
                            }
                        }
                        PrefManager.setCustomVal("removeList", removeList.minus(media?.id))
                    }
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
