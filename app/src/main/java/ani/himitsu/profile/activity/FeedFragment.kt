package ani.himitsu.profile.activity

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
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.Activity
import ani.himitsu.databinding.FragmentFeedBinding
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.profile.ProfileActivity
import ani.himitsu.util.Logger
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.core.util.lang.withUIContext

class FeedFragment : Fragment() {
    private lateinit var binding: FragmentFeedBinding
    private var adapter: GroupieAdapter = GroupieAdapter()
    private var activityList: List<Activity> = emptyList()
    private lateinit var activity: androidx.activity.ComponentActivity
    private var page: Int = 1
    private var loadedFirstTime = false
    private var userId: Int? = null
    private var global: Boolean = false
    private var activityId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity()

        userId = arguments?.getInt("userId", -1)
        activityId = arguments?.getInt("activityId", -1) ?: -1
        if (userId == -1) userId = null
        global = arguments?.getBoolean("global", false) == true

        binding.listRecyclerView.adapter = adapter
        binding.listRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.listProgressBar.visibility = ViewGroup.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onResume() {
        super.onResume()
        if (this::binding.isInitialized) {
            // binding.root.requestLayout()
            val navBar = if (userId != null) {
                (activity as ProfileActivity).navBar
            } else {
                (activity as FeedActivity).navBar
            }
            if (!loadedFirstTime) {
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val nulledId = if (activityId == -1) null else activityId
                    val res = AniList.query.getFeed(userId, global, activityId = nulledId)
                    withUIContext {
                        res?.data?.page?.activities?.let { activities ->
                            activityList = activities
                            val filtered =
                                activityList.filterNot {  // filter out messages that are not directed to the user
                                    it.recipient?.id != null && it.recipient.id != AniList.userid
                                }
                            adapter.update(filtered.map {
                                ActivityItem(
                                    it,
                                    ::onActivityClick,
                                    requireActivity()
                                )
                            })
                        }
                        binding.listProgressBar.visibility = ViewGroup.GONE
                        val scrollView = binding.listRecyclerView

                        binding.listRecyclerView.setOnTouchListener { _, event ->
                            if (event?.action == MotionEvent.ACTION_UP) {
                                if (activityList.size % AniList.ITEMS_PER_PAGE != 0 && !global) {
                                    // toast("No more activities") fix spam?
                                    Logger.log("No more activities")
                                } else if (!scrollView.canScrollVertically(1)
                                    && !binding.feedRefresh.isVisible
                                    && adapter.itemCount != 0
                                    && (binding.listRecyclerView.layoutManager as LinearLayoutManager)
                                        .findLastVisibleItemPosition() == (adapter.itemCount - 1)
                                ) {
                                    page++
                                    binding.feedRefresh.visibility = ViewGroup.VISIBLE
                                    loadPage {
                                        binding.feedRefresh.visibility = ViewGroup.GONE
                                    }
                                }
                            }
                            false
                        }

                        binding.feedSwipeRefresh.setOnRefreshListener {
                            page = 1
                            adapter.clear()
                            activityList = emptyList()
                            loadPage()
                        }
                    }
                }
                loadedFirstTime = true
            }
        }
    }

    private fun loadPage(onFinish: () -> Unit = {}) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val newRes = AniList.query.getFeed(userId, global, page)
            withUIContext {
                newRes?.data?.page?.activities?.let { activities ->
                    activityList += activities
                    val filtered = activities.filterNot {
                        it.recipient?.id != null && it.recipient.id != AniList.userid
                    }
                    adapter.addAll(filtered.map {
                        ActivityItem(
                            it,
                            ::onActivityClick,
                            requireActivity()
                        )
                    })
                }
                binding.feedSwipeRefresh.isRefreshing = false
                onFinish()
            }
        }
    }

    private fun onActivityClick(id: Int, type: String) {
        when (type) {
            "USER" -> {
                activity.startActivity(
                    Intent(activity, ProfileActivity::class.java)
                        .putExtra("userId", id)
                )
            }

            "MEDIA" -> {
                activity.startActivity(
                    Intent(activity, MediaDetailsActivity::class.java)
                        .putExtra("mediaId", id)
                )
            }
        }
    }

    companion object {
        fun newInstance(userId: Int?, global: Boolean, activityId: Int): FeedFragment {
            val fragment = FeedFragment()
            val args = Bundle()
            args.putInt("userId", userId ?: -1)
            args.putBoolean("global", global)
            args.putInt("activityId", activityId)
            fragment.arguments = args
            return fragment
        }
    }
}