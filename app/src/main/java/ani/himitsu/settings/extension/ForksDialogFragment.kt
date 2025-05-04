package ani.himitsu.settings.extension

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.connections.github.Forks
import ani.himitsu.databinding.BottomSheetDevelopersBinding
import ani.himitsu.settings.DevelopersAdapter
import ani.himitsu.view.dialog.BottomSheetDialogFragment

class ForksDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDevelopersBinding? = null
    private val binding by lazy { _binding!! }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDevelopersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.devsTitle.setText(R.string.forks)
        binding.devsRecyclerView.adapter = DevelopersAdapter(Forks().getForks())
        binding.devsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}
