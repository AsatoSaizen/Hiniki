package ani.himitsu.media.reviews

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.Query
import ani.himitsu.databinding.ActivityFollowBinding
import ani.himitsu.util.MarkdownCreatorActivity
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class ReviewFragment : Fragment() {
    private var _binding: ActivityFollowBinding? = null
    private val binding by lazy { _binding!! }
    val adapter = GroupieAdapter()
    private val reviews = mutableListOf<Query.Review>()
    var mediaId = 0
    private var currentPage: Int = 1
    private var hasNextPage: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityFollowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView();_binding = null
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        
        mediaId = arguments?.getInt("mediaId", -1) ?: -1
        if (mediaId == -1) return

//        try {
//            arguments?.getSerializableCompat<ArrayList<Review>>("reviews")?.let {
//                reviews.addAll(it)
//            }
//        } catch (_: Exception) { }

        binding.listToolbar.visibility = View.GONE
        binding.reviewFAB.visibility = View.VISIBLE
        binding.reviewFAB.setOnClickListener {
            requireContext().startActivity(
                Intent(requireContext(), MarkdownCreatorActivity::class.java)
                    .putExtra("type", MarkdownCreatorActivity.REVIEW)
                    .putExtra("mediaId", mediaId)
            )
        }

        val mediaTitle = arguments?.getString("title")
        binding.emptyRecyclerText.text = getString(R.string.reviews_empty, mediaTitle)
        binding.listRecyclerView.adapter = adapter
        binding.listRecyclerView.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.listProgressBar.visibility = View.VISIBLE

        binding.followSwipeRefresh.setOnRefreshListener {
            reviews.clear()
            appendList(reviews)
            binding.followSwipeRefresh.isRefreshing = false
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val response = AniList.query.getReviews(mediaId)?.data?.page
            withUIContext {
                binding.listProgressBar.visibility = View.GONE
                binding.listRecyclerView.setOnTouchListener { _, event ->
                    if (event?.action == MotionEvent.ACTION_UP) {
                        if (hasNextPage
                            && !binding.listRecyclerView.canScrollVertically(1)
                            && !binding.followRefresh.isVisible
                            && adapter.itemCount != 0
                            && (binding.listRecyclerView.layoutManager as LinearLayoutManager)
                                .findLastVisibleItemPosition() == (adapter.itemCount - 1)
                        ) {
                            binding.followRefresh.visibility = ViewGroup.VISIBLE
                            loadPage(++currentPage) {
                                binding.followRefresh.visibility = ViewGroup.GONE
                            }
                        }
                    }
                    false
                }
            }
            currentPage = response?.pageInfo?.currentPage ?: 1
            hasNextPage = response?.pageInfo?.hasNextPage == true
            response?.reviews?.let {
                reviews.addAll(it)
                appendList(it)
            }
            withUIContext {
                binding.emptyRecyclerText.isVisible = reviews.isEmpty()
            }
        }
    }

    private fun loadPage(page: Int, callback: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = AniList.query.getReviews(mediaId, page)?.data?.page
            currentPage = response?.pageInfo?.currentPage ?: 1
            hasNextPage = response?.pageInfo?.hasNextPage == true
            response?.reviews?.let {
                reviews.addAll(it)
                appendList(it)
            }
            withUIContext {
                binding.emptyRecyclerText.isVisible = reviews.isEmpty()
                callback()
            }
        }
    }

    private fun appendList(reviews: List<Query.Review>) {
        lifecycleScope.launch(Dispatchers.Main) {
            reviews.forEach { adapter.add(ReviewItem(it, ::onUserClick)) }
        }
    }

    private fun onUserClick(userId: Int) {
        reviews.find { it.id == userId }?.let { review ->
            startActivity(Intent(requireContext(), ReviewViewActivity::class.java)
                .putExtra("review", review))
        }
    }
}