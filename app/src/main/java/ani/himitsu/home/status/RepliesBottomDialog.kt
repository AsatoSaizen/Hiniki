package ani.himitsu.home.status

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.ActivityReply
import ani.himitsu.databinding.BottomSheetRecyclerBinding
import ani.himitsu.profile.ProfileActivity
import ani.himitsu.profile.activity.ActivityReplyItem
import ani.himitsu.toast
import ani.himitsu.util.MarkdownCreatorActivity
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class RepliesBottomDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetRecyclerBinding? = null
    private val binding by lazy { _binding!! }
    private val adapter: GroupieAdapter = GroupieAdapter()
    private val replies: MutableList<ActivityReply> = mutableListOf()
    private var activityId: Int = -1

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
        binding.replyButton.setOnClickListener {
            context.startActivity(
                Intent(context, MarkdownCreatorActivity::class.java)
                    .putExtra("type", MarkdownCreatorActivity.REPLY_ACTIVITY)
                    .putExtra("parentId", activityId)
            )
        }
        activityId = requireArguments().getInt("activityId")
        lifecycleScope.launch(Dispatchers.IO) {
            loading(true)
            val response = AniList.query.getReplies(activityId)
            loading(false)
            if (response != null) {
                replies.clear()
                replies.addAll(response.data.page.activityReplies)
                withUIContext {
                    adapter.update(
                        replies.map {
                            ActivityReplyItem(
                                it,
                                requireActivity(),
                                clickCallback = { int, _ ->
                                    onClick(int)
                                }
                            )
                        }
                    )
                }
            } else {
                toast(R.string.load_replies_failed)
            }
        }
    }

    private fun onClick(int: Int) {
        requireContext().startActivity(
            Intent(requireContext(), ProfileActivity::class.java).putExtra("userId", int)
        )
    }

    private suspend fun loading(load: Boolean) = withUIContext {
        binding.repliesRefresh.isVisible = load
        binding.repliesRecyclerView.isVisible = !load
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(activityId: Int): RepliesBottomDialog {
            return RepliesBottomDialog().apply {
                arguments = Bundle().apply {
                    putInt("activityId", activityId)
                }
            }
        }
    }
}