package ani.himitsu.home

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.bottomBar
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.AniListMangaViewModel
import ani.himitsu.databinding.FragmentAnimeBinding
import ani.himitsu.loadFragment
import ani.himitsu.media.MediaAdaptor
import ani.himitsu.media.ProgressAdapter
import ani.himitsu.media.ViewType
import ani.himitsu.media.cereal.SearchResults
import ani.himitsu.navBarHeight
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.toRoundImage
import ani.himitsu.toast
import bit.himitsu.content.toPx
import bit.himitsu.isOverlapping
import bit.himitsu.nio.string
import bit.himitsu.os.Version
import bit.himitsu.update.MatagiUpdater
import bit.himitsu.widget.FABulous
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MangaFragment : Fragment() {
    private var _binding: FragmentAnimeBinding? = null
    private val binding by lazy { _binding!! }
    private lateinit var mangaPageAdapter: MangaPageAdapter

    val model: AniListMangaViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView();_binding = null
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val scope = viewLifecycleOwner.lifecycleScope

        var height = statusBarHeight
        if (Version.isPie) {
            val displayCutout = activity?.window?.decorView?.rootWindowInsets?.displayCutout
            if (displayCutout != null) {
                if (displayCutout.boundingRects.isNotEmpty()) {
                    height = max(
                        statusBarHeight,
                        min(
                            displayCutout.boundingRects[0].width(),
                            displayCutout.boundingRects[0].height()
                        )
                    )
                }
            }
        }
        binding.animeRefresh.setSlingshotDistance(height + 128)
        binding.animeRefresh.setProgressViewEndTarget(false, height + 128)
        binding.animeRefresh.setOnRefreshListener {
            Refresh.activity[this.hashCode()]!!.postValue(true)
        }

        // TODO: Investigate hardcoded values
        binding.animePageRecyclerView.updatePaddingRelative(bottom = navBarHeight + 160.toPx)

        mangaPageAdapter = MangaPageAdapter(this)
        var loading = true
        if (model.notSet) {
            model.notSet = false
            model.searchResults = SearchResults(
                "MANGA",
                isAdult = false,
                onList = false,
                results = arrayListOf(),
                hasNextPage = true,
                sort = AniList.sortBy[1]
            )
        }
        val popularAdaptor = MediaAdaptor(ViewType.LARGE, model.searchResults.results, requireActivity())
        val progressAdaptor = ProgressAdapter(searched = model.searched)
        binding.animePageRecyclerView.adapter =
            ConcatAdapter(mangaPageAdapter, popularAdaptor, progressAdaptor)
        val layout = LinearLayoutManager(requireContext())
        binding.animePageRecyclerView.layoutManager = layout

        var visible = false
        fun animate() {
            val start = if (visible) 0f else 1f
            val end = if (!visible) 0f else 1f
            ObjectAnimator.ofFloat(binding.animePageScrollTop, "scaleX", start, end).apply {
                duration = 300
                interpolator = OvershootInterpolator(2f)
                start()
            }
            ObjectAnimator.ofFloat(binding.animePageScrollTop, "scaleY", start, end).apply {
                duration = 300
                interpolator = OvershootInterpolator(2f)
                start()
            }
        }

        binding.animePageScrollTop.setOnClickListener {
            binding.animePageRecyclerView.scrollToPosition(4)
            binding.animePageRecyclerView.smoothScrollToPosition(0)
        }

        binding.animePageRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                if (!v.canScrollVertically(1)) {
                    if (model.searchResults.hasNextPage && model.searchResults.results.isNotEmpty() && !loading) {
                        scope.launch(Dispatchers.IO) {
                            loading = true
                            model.loadNextPage(model.searchResults)
                        }
                    }
                }
                if (layout.findFirstVisibleItemPosition() > 1 && !visible) {
                    bottomBar.visibility = View.GONE
                    binding.animePageScrollTop.visibility = View.VISIBLE
                    visible = true
                    animate()
                }

                if (!v.canScrollVertically(-1) || (visible && layout.findFirstVisibleItemPosition() <= 1)) {
                    visible = false
                    animate()
                    scope.launch {
                        delay(300)
                        binding.animePageScrollTop.visibility = View.GONE
                        bottomBar.visibility = View.VISIBLE
                    }
                }

                super.onScrolled(v, dx, dy)
            }
        })

        mangaPageAdapter.ready.observe(viewLifecycleOwner) { i ->
            if (i) {
                binding.avatarFabulous.apply {
                    isVisible = PrefManager.getVal(PrefName.FloatingAvatar)
                    if (isVisible) {
                        setAnchor(mangaPageAdapter.trendingBinding.userAvatar)
                        toRoundImage(AniList.avatar, 52.toPx)
                        (behavior as FloatingActionButton.Behavior).isAutoHideEnabled = false

                        setDefaultPosition()

                        if (binding.avatarFabulous.isOverlapping(mangaPageAdapter.trendingBinding.userAvatar)) {
                            setBadgeDrawable(
                                AniList.unreadNotificationCount + MatagiUpdater.hasUpdate
                            )
                        }

                        val handler = Handler(Looper.getMainLooper())
                        val mRunnable = Runnable {
                            if (isOverlapping(mangaPageAdapter.trendingBinding.userAvatar)) {
                                setDefaultPosition()
                            }
                        }

                        setOnMoveListener(object : FABulous.OnViewMovedListener {
                            override fun onActionMove(x: Float, y: Float) {
                                handler.removeCallbacksAndMessages(mRunnable)
                                if (isOverlapping(mangaPageAdapter.trendingBinding.userAvatar)) {
                                    handler.postDelayed(mRunnable, 1000)
                                }
                                setActiveNotificationCount()
                            }
                        })
                        setOnClickListener {
                            mangaPageAdapter.trendingBinding.userAvatar.performClick()
                        }
                        setOnLongClickListener {
                            if (isOverlapping(mangaPageAdapter.trendingBinding.userAvatar)) {
                                mangaPageAdapter.trendingBinding.userAvatar.performLongClick()
                            } else {
                                false
                            }
                        }
                    }
                }
                model.getPopularNovel().observe(viewLifecycleOwner) {
                    if (it != null) {
                        mangaPageAdapter.updateNovel(MediaAdaptor(ViewType.COMPACT, it, requireActivity()), it)
                    }
                }
                model.getPopularManga().observe(viewLifecycleOwner) {
                    if (it != null) {
                        mangaPageAdapter.updateTrendingManga(MediaAdaptor(ViewType.COMPACT, it, requireActivity()), it)
                        if (it.isNotEmpty()) mangaPageAdapter.setReviewImageFromTrending(it[Random.nextInt(it.size)])
                    }
                }
                model.getPopularManhwa().observe(viewLifecycleOwner) {
                    if (it != null) {
                        mangaPageAdapter.updateTrendingManhwa(
                            MediaAdaptor(
                                ViewType.COMPACT,
                                it,
                                requireActivity()
                            ), it
                        )
                    }
                }
                model.getTopRated().observe(viewLifecycleOwner) {
                    if (it != null) {
                        mangaPageAdapter.updateTopRated(MediaAdaptor(ViewType.COMPACT, it, requireActivity()), it)
                    }
                }
                model.getMostFav().observe(viewLifecycleOwner) {
                    if (it != null) {
                        mangaPageAdapter.updateMostFav(MediaAdaptor(ViewType.COMPACT, it, requireActivity()), it)
                    }
                }
                if (mangaPageAdapter.trendingViewPager != null) {
                    mangaPageAdapter.updateHeight()
                    model.getTrending().observe(viewLifecycleOwner) {
                        if (it != null) {
                            mangaPageAdapter.updateTrending(
                                MediaAdaptor(
                                    if (PrefManager.getVal(PrefName.TrendingCovers))
                                        ViewType.SMALL_PAGE
                                    else
                                        ViewType.BASIC,
                                    it,
                                    requireActivity(),
                                    viewPager = mangaPageAdapter.trendingViewPager
                                )
                            )
                            mangaPageAdapter.updateAvatar()
                        }
                    }
                }
                binding.animePageScrollTop.translationY =  -(navBarHeight).toFloat() // nav hidden
                    // -(bottomBar.height + bottomBar.marginBottom + navBarHeight).toFloat()

            }
        }

        var oldIncludeList = true

        mangaPageAdapter.onIncludeListClick = { checked ->
            oldIncludeList = !checked
            loading = true
            model.searchResults.results.clear()
            popularAdaptor.notifyDataSetChanged()
            scope.launch(Dispatchers.IO) {
                model.loadPopular("MANGA", sort = AniList.sortBy[1], onList = checked)
            }
        }

        model.getPopular().observe(viewLifecycleOwner) {
            if (it != null) {
                if (oldIncludeList == (it.onList != false)) {
                    val prev = model.searchResults.results.size
                    model.searchResults.results.addAll(it.results)
                    popularAdaptor.notifyItemRangeInserted(prev, it.results.size)
                } else {
                    model.searchResults.results.addAll(it.results)
                    popularAdaptor.notifyDataSetChanged()
                    oldIncludeList = it.onList ?: true
                }
                model.searchResults.onList = it.onList
                model.searchResults.hasNextPage = it.hasNextPage
                model.searchResults.page = it.page
                if (it.hasNextPage)
                    progressAdaptor.bar?.visibility = View.VISIBLE
                else {
                    toast(getString(R.string.jobless_message))
                    progressAdaptor.bar?.visibility = View.GONE
                }
                loading = false
            }
        }

        fun load() = scope.launch(Dispatchers.Main) {
            mangaPageAdapter.updateAvatar()
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(false) }
        live.observe(viewLifecycleOwner) {
            if (it) {
                scope.launch(Dispatchers.IO) {
                    try {
                        loadFragment(requireActivity()) { load() }
                    } catch (_: Exception) { }
                    model.loaded = true
                    model.loadTrending()
                    model.loadAll()
                    model.loadPopular(
                        "MANGA", sort = AniList.sortBy[1], onList = PrefManager.getVal(
                            PrefName.PopularMangaList
                        )
                    )
                }
                live.postValue(false)
                _binding?.animeRefresh?.isRefreshing = false
            }
        }
    }

    fun setActiveNotificationCount() {
        val count = AniList.unreadNotificationCount + MatagiUpdater.hasUpdate
        if (binding.avatarFabulous.isOverlapping(mangaPageAdapter.trendingBinding.userAvatar)) {
            mangaPageAdapter.trendingBinding.notificationCount.isVisible = false
            binding.avatarFabulous.setBadgeDrawable(count)
        } else {
            mangaPageAdapter.trendingBinding.notificationCount.text = count.string
            mangaPageAdapter.trendingBinding.notificationCount.isVisible = count > 0
            binding.avatarFabulous.setBadgeDrawable(null)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!model.loaded) Refresh.activity[this.hashCode()]!!.postValue(true)
        //make sure mangaPageAdapter is initialized
        if (this::mangaPageAdapter.isInitialized && _binding != null) {
            if (mangaPageAdapter.trendingViewPager != null) {
                binding.root.requestApplyInsets()
                binding.avatarFabulous.setDefaultPosition()
                setActiveNotificationCount()
            }
        }
    }
}