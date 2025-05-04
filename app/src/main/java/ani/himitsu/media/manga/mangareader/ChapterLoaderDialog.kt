package ani.himitsu.media.manga.mangareader

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import ani.himitsu.R
import ani.himitsu.currActivity
import ani.himitsu.databinding.BottomSheetSelectorBinding
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.cereal.MediaSingleton
import ani.himitsu.media.manga.MangaChapter
import ani.himitsu.others.getSerialized
import ani.himitsu.tryWith
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import bit.himitsu.setNavigationTheme
import bit.himitsu.setStatusTransparent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable

class ChapterLoaderDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding by lazy { _binding!! }

    val model: MediaDetailsViewModel by activityViewModels()

    private val launch: Boolean by lazy { arguments?.getBoolean("launch", false) == true }
    private val chp: MangaChapter by lazy { arguments?.getSerialized("next")!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var loaded = false
        binding.selectorAutoListContainer.visibility = View.VISIBLE
        binding.selectorListContainer.visibility = View.GONE

        binding.autoSelectorTitle.text = getString(R.string.loading_chap_number, chp.number)
        binding.selectorCancel.setOnClickListener {
            dismiss()
        }

        model.getMedia().observe(viewLifecycleOwner) { m ->
            if (m != null && !loaded) {
                loaded = true
                binding.selectorAutoText.text = chp.title
                lifecycleScope.launch(Dispatchers.IO) {
                    if (model.loadMangaChapterImages(
                            chp,
                            m.selected!!
                        )
                    ) {
                        val activity = currActivity()
                        activity?.runOnUiThread {
                            tryWith { dismiss() }
                            if (launch) {
                                MediaSingleton.media = m
                                val intent = Intent(
                                    activity,
                                    MangaReaderActivity::class.java
                                )//.putExtra("media", m as Serializable)
                                activity.startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        dialog?.window?.let {
            it.setStatusTransparent()
            it.setNavigationTheme()
        }
        return binding.root
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance(next: MangaChapter, launch: Boolean = false) = ChapterLoaderDialog().apply {
            arguments = bundleOf("next" to next as Serializable, "launch" to launch)
        }
    }
}