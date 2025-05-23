package ani.himitsu.media.novel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.databinding.BottomSheetBookBinding
import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.others.getSerialized
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetBookBinding? = null
    private val binding by lazy { _binding!! }
    private val viewList = mutableListOf<View>()

    private val viewModel by activityViewModels<MediaDetailsViewModel>()

    private lateinit var novelName: String
    private lateinit var novel: ShowResponse
    private var source: Int = 0

    interface Callback {
        fun onDownloadTriggered(link: String)
    }

    private var callback: Callback? = null
    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        arguments?.let {
            novelName = it.getString("novelName")!!
            novel = it.getSerialized("novel")!!
            source = it.getInt("source")
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.bookRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewModel.book.observe(viewLifecycleOwner) {
            if (it != null) {
                binding.itemBookTitle.text = it.name
                binding.bookRecyclerView.adapter = UrlAdapter(it.links, it, novelName, callback)
                binding.progress.isGone = true
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadBook(novel, source)
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance(novelName: String, novel: ShowResponse, source: Int): BookDialog {
            val bundle = Bundle().apply {
                putString("novelName", novelName)
                putInt("source", source)
                putSerializable("novel", novel)
            }
            return BookDialog().apply {
                arguments = bundle
            }
        }
    }
}