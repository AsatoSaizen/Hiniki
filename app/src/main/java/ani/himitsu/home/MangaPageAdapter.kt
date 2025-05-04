package ani.himitsu.home

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.currContext
import ani.himitsu.databinding.ItemListContainerBinding
import ani.himitsu.databinding.ItemMangaPageBinding
import ani.himitsu.databinding.LayoutTrendingBinding
import ani.himitsu.loadImage
import ani.himitsu.media.GenreActivity
import ani.himitsu.media.MediaAdaptor
import ani.himitsu.media.MediaListViewActivity
import ani.himitsu.media.SearchActivity
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.reviews.ReviewPopupActivity
import ani.himitsu.notifications.NotificationActivity
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.setSlideIn
import ani.himitsu.setSlideUp
import ani.himitsu.settings.SettingsDialogFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.view.MediaPageTransformer
import bit.himitsu.content.toPx
import bit.himitsu.nio.Strings.getString
import bit.himitsu.widget.onCompletedAction
import bit.himitsu.withFlexibleMargin
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import eu.kanade.tachiyomi.util.system.getThemeColor

class MangaPageAdapter(val parent: MangaFragment): RecyclerView.Adapter<MangaPageAdapter.MangaPageViewHolder>() {
    val ready = MutableLiveData(false)
    lateinit var binding: ItemMangaPageBinding
    private lateinit var bindingListContainer: ItemListContainerBinding
    lateinit var trendingBinding: LayoutTrendingBinding
    private var trendHandler: Handler? = null
    private lateinit var trendRun: Runnable
    var trendingViewPager: ViewPager2? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaPageViewHolder {
        val binding =
            ItemMangaPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MangaPageViewHolder(binding)
    }

    private fun launchSearch() {
        trendingBinding.searchBarText.context.startActivity(
            Intent(trendingBinding.searchBarText.context, SearchActivity::class.java).apply {
                if (!trendingBinding.searchBarText.text.isNullOrBlank()) {
                    putExtra("query", trendingBinding.searchBarText.text?.toString())
                    putExtra("hideKeyboard", true)
                }
            }.putExtra("type", trendingBinding.searchBar.hint)
        )
        trendingBinding.searchBarText.setText(null)
        trendingBinding.searchBarText.clearFocus()
    }

    override fun onBindViewHolder(holder: MangaPageViewHolder, position: Int) {
        binding = holder.binding
        trendingBinding = LayoutTrendingBinding.bind(binding.root)
        trendingViewPager = trendingBinding.trendingViewPager

        val textInputLayout = holder.itemView.findViewById<TextInputLayout>(R.id.searchBar)
        val currentColor = textInputLayout.boxBackgroundColor
        val semiTransparentColor = (currentColor and 0x00FFFFFF) or 0xA8000000.toInt()
        textInputLayout.boxBackgroundColor = semiTransparentColor
        val materialCardView =
            holder.itemView.findViewById<MaterialCardView>(R.id.userAvatarContainer)
        materialCardView.setCardBackgroundColor(semiTransparentColor)
        val color = currContext().getThemeColor(android.R.attr.windowBackground)

        textInputLayout.boxBackgroundColor = (color and 0x00FFFFFF) or 0x28000000
        materialCardView.setCardBackgroundColor((color and 0x00FFFFFF) or 0x28000000)

        trendingBinding.titleContainer.updatePadding(top = statusBarHeight)

        trendingBinding.trendingContainer.withFlexibleMargin(
            holder.itemView.resources.configuration, toBottom = false
        ).updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = (-108f).toPx
        }

        updateAvatar()
        parent.setActiveNotificationCount()

        trendingBinding.searchBar.hint = "MANGA"
        trendingBinding.searchBarText.setOnEditorActionListener(onCompletedAction {
            launchSearch()
        })

        trendingBinding.searchBar.setEndIconOnClickListener {
            launchSearch()
        }

        val scale = 56.toPx / (trendingBinding.root.width - 90.toPx).toFloat()

        trendingBinding.searchBar.setEndIconOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            trendingBinding.searchBar.pivotX = 0f
            ObjectAnimator.ofFloat(trendingBinding.searchBar, "scaleX", 1f, scale).apply {
                duration = (250 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong()
                doOnEnd {
                    trendingBinding.searchBar.isVisible = false
                    trendingBinding.searchContainer.isVisible = true
                    trendingBinding.searchBuffer.isVisible = true
                    with (holder.itemView.context.getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager) {
                        hideSoftInputFromWindow(trendingBinding.searchBar.windowToken, 0)
                    }
                }
            }.start()
            true
        }

        trendingBinding.searchContainer.setOnClickListener {
            trendingBinding.searchBuffer.isVisible = false
            trendingBinding.searchBar.pivotX = 0f
            ObjectAnimator.ofFloat(trendingBinding.searchBar, "scaleX", scale, 1f).apply {
                duration = 500
                doOnStart { trendingBinding.searchContainer.isVisible = false }
                doOnEnd {
                    trendingBinding.searchBar.pivotX = trendingBinding.searchBar.width / 2f
                    trendingBinding.searchBarText.requestFocus()
                    with (holder.itemView.context.getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager) {
                        showSoftInput(trendingBinding.searchBarText, 0)
                    }
                }
            }.start()
            trendingBinding.searchBar.isVisible = true
        }

        if (PrefManager.getVal(PrefName.PersistSearch)) {
            trendingBinding.searchBuffer.isVisible = false
            trendingBinding.searchContainer.isVisible = false
            trendingBinding.searchBar.pivotX = trendingBinding.searchBar.width / 2f
            trendingBinding.searchBar.isVisible = true
        }

        trendingBinding.userAvatar.setSafeOnClickListener {
            val dialogFragment =
                SettingsDialogFragment.newInstance(SettingsDialogFragment.Companion.PageType.MANGA)
            dialogFragment.show((it.context as AppCompatActivity).supportFragmentManager, "dialog")
        }
        trendingBinding.userAvatar.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.context.startActivity(
                Intent(view.context, NotificationActivity::class.java)
            )
            true
        }

        bindingListContainer = ItemListContainerBinding.bind(binding.root).apply {
            leftButtonImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/manga/banner/105778-wk5qQ7zAaTGl.jpg")
            rightButtonText.text = getString(R.string.top_score)
            rightButtonImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/manga/banner/30002-3TuoSMl20fUX.jpg")

            leftListButton.setOnClickListener {
                it.context.startActivity(
                    Intent(it.context, GenreActivity::class.java).putExtra("type", "MANGA")
                )
            }
            rightListButton.setOnClickListener {
                it.context.startActivity(
                    Intent(it.context, SearchActivity::class.java)
                        .putExtra("type", "MANGA")
                        .putExtra("sortBy", AniList.sortBy[0])
                        .putExtra("hideKeyboard", true)
                )
            }
            reviewButtonText.text = getString(R.string.review_type, "MANGA")
            reviewButton.setOnClickListener {
                it.context.startActivity(
                    Intent(it.context, ReviewPopupActivity::class.java).putExtra("type", "MANGA")
                )
            }
            //reviewButtonImage.loadImage()
        }

        binding.mangaIncludeList.isVisible = AniList.userid != null

        binding.mangaIncludeList.isChecked = PrefManager.getVal(PrefName.PopularMangaList)

        binding.mangaIncludeList.setOnCheckedChangeListener { _, isChecked ->
            onIncludeListClick.invoke(isChecked)
            PrefManager.setVal(PrefName.PopularMangaList, isChecked)
        }
        if (ready.value == false)
            ready.postValue(true)
    }

    lateinit var onIncludeListClick: ((Boolean) -> Unit)

    override fun getItemCount(): Int = 1

    fun updateHeight() {
        trendingViewPager!!.updateLayoutParams { height += statusBarHeight }
    }

    fun updateTrending(adaptor: MediaAdaptor) {
        trendingBinding.trendingProgressBar.visibility = View.GONE
        trendingBinding.trendingViewPager.adapter = adaptor
        trendingBinding.trendingViewPager.offscreenPageLimit = 3
        trendingBinding.trendingViewPager.getChildAt(0).overScrollMode =
            RecyclerView.OVER_SCROLL_NEVER
        trendingBinding.trendingViewPager.setPageTransformer(MediaPageTransformer())
        trendHandler = Handler(Looper.getMainLooper())
        trendRun = Runnable {
            trendingBinding.trendingViewPager.currentItem += 1
        }
        trendingBinding.trendingViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    trendHandler?.removeCallbacks(trendRun)
                    if (PrefManager.getVal(PrefName.TrendingScroller))
                        trendHandler!!.postDelayed(trendRun, 4000)
                }
            }
        )

        trendingBinding.trendingViewPager.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
        trendingBinding.titleContainer.startAnimation(setSlideUp())
        bindingListContainer.listContainer.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)

    }

    fun setReviewImageFromTrending(media: Media) {
        bindingListContainer.reviewButtonImage.loadImage(media.cover)
    }

    fun updateTrendingManga(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaTrendingMangaRecyclerView,
                mangaTrendingMangaProgressBar,
                mangaTrendingManga,
                mangaTrendingMangaMore,
                getString(R.string.trending_manga),
                media
            )
        }
    }

    fun updateTrendingManhwa(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaTrendingManhwaRecyclerView,
                mangaTrendingManhwaProgressBar,
                mangaTrendingManhwa,
                mangaTrendingManhwaMore,
                getString(R.string.trending_manhwa),
                media
            )
        }
    }

    fun updateNovel(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaNovelRecyclerView,
                mangaNovelProgressBar,
                mangaNovel,
                mangaNovelMore,
                getString(R.string.trending_novel),
                media
            )
        }
    }

    fun updateTopRated(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaTopRatedRecyclerView,
                mangaTopRatedProgressBar,
                mangaTopRated,
                mangaTopRatedMore,
                getString(R.string.top_rated),
                media
            )
        }
    }

    fun updateMostFav(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                mangaMostFavRecyclerView,
                mangaMostFavProgressBar,
                mangaMostFav,
                mangaMostFavMore,
                getString(R.string.most_favourite),
                media
            )
            mangaPopular.visibility = View.VISIBLE
            mangaPopular.startAnimation(setSlideUp())
        }
    }

    fun init(
        adaptor: MediaAdaptor,
        recyclerView: RecyclerView,
        progress: View,
        title: View ,
        more: View ,
        string: String,
        media : MutableList<Media>
    ) {
        progress.visibility = View.GONE
        recyclerView.adapter = adaptor
        recyclerView.layoutManager =
            LinearLayoutManager(
                recyclerView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        more.setOnClickListener {
            it.context.startActivity(
                Intent(it.context, MediaListViewActivity::class.java)
                    .putExtra("title", string)
                    .putExtra("media",  media as ArrayList<Media>)
            )
        }
        recyclerView.visibility = View.VISIBLE
        title.visibility = View.VISIBLE
        more.visibility = View.VISIBLE
        title.startAnimation(setSlideUp())
        more.startAnimation(setSlideUp())
        recyclerView.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
    }

    fun updateAvatar() {
        if (AniList.avatar != null && ready.value == true) {
            trendingBinding.userAvatar.loadImage(AniList.avatar, 52.toPx)
            trendingBinding.userAvatar.imageTintList = null
        }
    }

    inner class MangaPageViewHolder(val binding: ItemMangaPageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
