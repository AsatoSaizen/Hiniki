package ani.himitsu.media

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import ani.himitsu.databinding.BottomSheetSourceSearchBinding
import ani.himitsu.media.anime.AnimeSourceAdapter
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.manga.MangaSourceAdapter
import ani.himitsu.parsers.AnimeSources
import ani.himitsu.parsers.BaseParser
import ani.himitsu.parsers.HAnimeSources
import ani.himitsu.parsers.HMangaSources
import ani.himitsu.parsers.MangaSources
import ani.himitsu.tryWithSuspend
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import bit.himitsu.content.dpToColumns
import bit.himitsu.widget.onCompletedActionText
import kotlinx.coroutines.launch
import tachiyomi.core.util.lang.withIOContext

class SourceSearchDialogFragment(val parser: BaseParser? = null) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSourceSearchBinding? = null
    private val binding by lazy { _binding!! }
    val model: MediaDetailsViewModel by activityViewModels()
    private var searched = false
    private var anime = true
    private var index: Int? = null
    private var media: Media? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSourceSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val scope = requireActivity().lifecycleScope
        val imm =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        model.getMedia().observe(viewLifecycleOwner) {
            media = it
            if (media != null) {
                binding.mediaListProgressBar.visibility = View.GONE
                binding.mediaListLayout.visibility = View.VISIBLE

                binding.searchRecyclerView.visibility = View.GONE
                binding.searchProgress.visibility = View.VISIBLE

                index = media!!.selected!!.sourceIndex

                anime = media!!.anime != null
                val source = when {
                    parser != null -> { parser }
                    anime -> { (if (media!!.isAdult) HAnimeSources else AnimeSources)[index!!] }
                    else -> { (if (media!!.isAdult) HMangaSources else MangaSources)[index!!] }
                }

                fun search() {
                    binding.searchBarText.clearFocus()
                    imm.hideSoftInputFromWindow(binding.searchBarText.windowToken, 0)
                    scope.launch {
                        model.responses.postValue(
                            withIOContext {
                                tryWithSuspend {
                                    source.search(binding.searchBarText.text.toString())
                                }
                            }
                        )
                    }
                }
                binding.searchSourceTitle.text = source.name
                binding.searchBarText.setText(media!!.mangaName())
                binding.searchBarText.setOnEditorActionListener(onCompletedActionText { search() })
                binding.searchBar.setEndIconOnClickListener { search() }
                if (!searched) search()
                searched = true
                model.responses.observe(viewLifecycleOwner) { results ->
                    results?.let { result ->
                        binding.searchRecyclerView.visibility = View.VISIBLE
                        binding.searchProgress.visibility = View.GONE
                        binding.searchRecyclerView.adapter =
                            if (anime)
                                AnimeSourceAdapter(result, model, index!!, media!!.id, this, scope)
                            else
                                MangaSourceAdapter(result, model, index!!, media!!.id, this, scope)
                        binding.searchRecyclerView.layoutManager = GridLayoutManager(
                            requireActivity(), 124.dpToColumns
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        model.responses.value = null
        super.dismiss()
    }
}