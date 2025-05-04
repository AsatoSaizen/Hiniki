package ani.himitsu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.databinding.BottomSheetRecyclerBinding
import ani.himitsu.notifications.subscription.SubscriptionHelper
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import com.xwray.groupie.GroupieAdapter

class SubscriptionsBottomDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetRecyclerBinding? = null
    private val binding by lazy { _binding!! }
    private val adapter: GroupieAdapter = GroupieAdapter()
    private var subscriptions: Map<Int, SubscriptionHelper.SubscribeMedia> = mapOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetRecyclerBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.repliesRecyclerView.adapter = adapter
        binding.repliesRecyclerView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.VERTICAL,
            false
        )
        val context = requireContext()
        binding.title.text = context.getString(R.string.subscriptions)
        binding.replyButton.visibility = View.GONE
        subscriptions.forEach { (id, media) ->
            adapter.add(SubscriptionItem(id, media, adapter))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(subscriptions: Map<Int, SubscriptionHelper.SubscribeMedia>): SubscriptionsBottomDialog {
            val dialog = SubscriptionsBottomDialog()
            dialog.subscriptions = subscriptions
            return dialog
        }
    }
}