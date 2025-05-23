package ani.himitsu.media.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import ani.himitsu.databinding.FragmentListBinding
import ani.himitsu.media.CalendarActivity
import ani.himitsu.media.MediaAdaptor
import ani.himitsu.media.OtherDetailsViewModel
import ani.himitsu.media.ViewType
import ani.himitsu.media.cereal.Media
import bit.himitsu.content.dpToColumns

class ListFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding by lazy { _binding!! }
    private var pos: Int? = null
    private var grid: Boolean? = null
    private var list: MutableList<Media>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pos = it.getInt("list")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun update() {
            if (grid != null && list != null) {
                val adapter = MediaAdaptor(
                    if (grid!!) ViewType.COMPACT else ViewType.LARGE,
                    list!!,
                    requireActivity(),
                    true
                )
                binding.listRecyclerView.layoutManager = GridLayoutManager(
                    requireContext(), if (grid!!) 120.dpToColumns else 1
                )
                binding.listRecyclerView.adapter = adapter
            }
        }

        if (activity is CalendarActivity) {
            val model: OtherDetailsViewModel by activityViewModels()
            model.getCalendar().observe(viewLifecycleOwner) {
                if (it != null) {
                    list = it.values.toList().getOrNull(pos!!)
                    update()
                }
            }
            grid = true
        } else {
            val model: ListViewModel by activityViewModels()
            model.getLists().observe(viewLifecycleOwner) {
                if (it != null) {
                    list = it.values.toList().getOrNull(pos!!)
                    update()
                }
            }
            model.grid.observe(viewLifecycleOwner) {
                grid = it
                update()
            }
        }
    }

    fun randomOptionClick() {
        val adapter = binding.listRecyclerView.adapter as MediaAdaptor
        adapter.randomOptionClick()
    }

    companion object {
        fun newInstance(pos: Int): ListFragment =
            ListFragment().apply {
                arguments = Bundle().apply {
                    putInt("list", pos)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}