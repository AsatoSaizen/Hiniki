package ani.himitsu.profile

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.himitsu.R
import ani.himitsu.blurImage
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.Query
import ani.himitsu.databinding.ActivityProfileBinding
import ani.himitsu.databinding.ProfileAppBarBinding
import ani.himitsu.initActivity
import ani.himitsu.loadImage
import ani.himitsu.media.user.ListActivity
import ani.himitsu.openLinkInBrowser
import ani.himitsu.profile.activity.FeedFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import ani.himitsu.view.dialog.ImageViewDialog
import bit.himitsu.content.metrics
import bit.himitsu.nio.string
import bit.himitsu.setBaseline
import bit.himitsu.withFlexibleMargin
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.joery.animatedbottombar.AnimatedBottomBar
import tachiyomi.core.util.lang.withUIContext
import kotlin.math.abs


class ProfileActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {
    lateinit var binding: ActivityProfileBinding
    private lateinit var bindingProfileAppBar: ProfileAppBarBinding
    private var selected: Int = 0
    lateinit var navBar: AnimatedBottomBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val context = this
        navBar = binding.profileNavBar.apply {
            withFlexibleMargin(resources.configuration)
        }
        navBar.visibility = View.GONE
        binding.profileViewPager.isUserInputEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val userid = intent.getIntExtra("userId", -1)
            val username = intent.getStringExtra("username") ?: ""
            val respond =
                if (userid != -1) AniList.query.getUserProfile(userid) else
                    AniList.query.getUserProfile(username)
            val user = respond?.data?.user
            if (user == null) {
                toast("User not found")
                finish()
                return@launch
            }

            withUIContext {
                binding.profileViewPager.adapter =
                    ViewPagerAdapter(supportFragmentManager, lifecycle, user)
                navBar.visibility = View.VISIBLE
                navBar.setupWithViewPager2(binding.profileViewPager)
                navBar.onTabSelected = { selected = navBar.selectedIndex }
                binding.profileViewPager.setCurrentItem(selected, false)
                navBar.selectTabAt(selected, false)

                binding.profileViewPager.setBaseline(navBar)

                bindingProfileAppBar = ProfileAppBarBinding.bind(binding.root).apply {
                    binding.profileProgressBar.visibility = View.GONE
                    followButton.isGone =
                        user.id == AniList.userid || AniList.userid == null

                    fun followText(): String {
                        return getString(
                            when {
                                user.isFollowing && user.isFollower -> R.string.mutual
                                user.isFollowing -> R.string.unfollow
                                user.isFollower -> R.string.follows_you
                                else -> R.string.follow
                            }
                        )
                    }

                    followButton.text = followText()

                    followButton.setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val res = AniList.mutation.toggleFollow(user.id)
                            if (res?.data?.toggleFollow != null) {
                                withUIContext {
                                    toast(R.string.success)
                                    user.isFollowing = res.data.toggleFollow.isFollowing
                                    followButton.text = followText()
                                }
                            }
                        }
                    }
                    profileAppBar.visibility = View.VISIBLE
                    profileMenuButton.setOnClickListener {
                        openLinkInBrowser("https://anilist.co/user/${user.name}")
                    }

                    profileUserAvatar.loadImage(user.avatar?.medium)
                    profileUserAvatar.setOnLongClickListener {
                        ImageViewDialog.newInstance(
                            context,
                            getString(R.string.avatar, user.name),
                            user.avatar?.medium
                        )
                    }
                    profileUserName.text = user.name
                    val bannerAnimations: ImageView = if (PrefManager.getVal(PrefName.BannerAnimations))
                        profileBannerImage
                    else profileBannerImageNoKen

                    bannerAnimations.blurImage(user.bannerImage ?: user.avatar?.medium)
                    profileBannerImage.updateLayoutParams { height += statusBarHeight }
                    profileBannerImageNoKen.updateLayoutParams { height += statusBarHeight }
                    profileBannerGradient.updateLayoutParams { height += statusBarHeight }
                    profileOverlayContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
                    profileButtonContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }

                    profileBannerImage.setOnLongClickListener {
                        ImageViewDialog.newInstance(
                            context,
                            getString(R.string.banner, user.name),
                            user.bannerImage
                        )
                    }

                    mMaxScrollSize = profileAppBar.totalScrollRange
                    profileAppBar.addOnOffsetChangedListener(context)


                    profileFollowerCount.text = (respond.data.followerPage?.pageInfo?.total ?: 0).string
                    profileFollowerCountContainer.setOnClickListener {
                        context.startActivity(
                            Intent(context, FollowActivity::class.java)
                                .putExtra("title", getString(R.string.followers))
                                .putExtra("userId", user.id)
                        )
                    }
                    profileFollowingCount.text = (respond.data.followingPage?.pageInfo?.total ?: 0).string
                    profileFollowingCountContainer.setOnClickListener {
                        context.startActivity(
                            Intent(context, FollowActivity::class.java)
                                .putExtra("title", "Following")
                                .putExtra("userId", user.id)
                        )
                    }

                    profileAnimeCount.text = user.statistics.anime.count.string
                    profileAnimeCountContainer.setOnClickListener {
                        context.startActivity(
                            Intent(context, ListActivity::class.java)
                                .putExtra("anime", true)
                                .putExtra("userId", user.id)
                                .putExtra("username", user.name)
                        )
                    }

                    profileMangaCount.text = user.statistics.manga.count.string
                    profileMangaCountContainer.setOnClickListener {
                        context.startActivity(
                            Intent(context, ListActivity::class.java)
                                .putExtra("anime", false)
                                .putExtra("userId", user.id)
                                .putExtra("username", user.name)
                        )
                    }

                    profileCloseButton.setOnClickListener {
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
    }

    //Collapsing UI Stuff
    private var isCollapsed = false
    private val percent = 65
    private var mMaxScrollSize = 0
    private val screenWidth: Float by lazy { metrics.widthPixels.toFloat() }

    override fun onOffsetChanged(appBar: AppBarLayout, i: Int) {
        if (mMaxScrollSize == 0) mMaxScrollSize = appBar.totalScrollRange
        val percentage = abs(i) * 100 / mMaxScrollSize

        with(bindingProfileAppBar) {
            profileUserAvatarContainer.visibility =
                if (profileUserAvatarContainer.scaleX == 0f) View.GONE else View.VISIBLE
            val duration = (200 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong()
            if (percentage >= percent && !isCollapsed) {
                isCollapsed = true
                ObjectAnimator.ofFloat(profileUserDataContainer, "translationX", screenWidth)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileUserAvatarContainer, "translationX", screenWidth)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileButtonContainer, "translationX", screenWidth)
                    .setDuration(duration).start()
                profileBannerImage.pause()
            }
            if (percentage <= percent && isCollapsed) {
                isCollapsed = false
                ObjectAnimator.ofFloat(profileUserDataContainer, "translationX", 0f)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileUserAvatarContainer, "translationX", 0f)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileButtonContainer, "translationX", 0f)
                    .setDuration(duration).start()

                if (PrefManager.getVal(PrefName.BannerAnimations)) profileBannerImage.resume()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        navBar.apply {
            withFlexibleMargin(newConfig)
        }
        binding.profileViewPager.setBaseline(navBar, newConfig)
    }

    override fun onRestart() {
        super.onRestart()
        if (this::navBar.isInitialized) navBar.selectTabAt(selected)
    }

    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val user: Query.UserProfile
    ) :
        FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> ProfileFragment.newInstance(user)
            1 -> FeedFragment.newInstance(user.id, false, -1)
            2 -> StatsFragment.newInstance(user)
            else -> ProfileFragment.newInstance(user)
        }
    }
}
