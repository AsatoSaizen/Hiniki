package ani.himitsu.profile.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.himitsu.R
import ani.himitsu.databinding.ActivityFeedBinding
import ani.himitsu.initActivity
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.util.MarkdownCreatorActivity
import bit.himitsu.setBaseline
import bit.himitsu.withFlexibleMargin
import nl.joery.animatedbottombar.AnimatedBottomBar


class FeedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeedBinding
    private var selected: Int = 0
    lateinit var navBar: AnimatedBottomBar
    private val fabHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navBar = binding.feedNavBar.apply {
            withFlexibleMargin(resources.configuration)
        }
        binding.feedViewPager.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin += statusBarHeight
        }
        binding.feedViewPager.setBaseline(navBar)
        val personalTab = navBar.createTab(R.drawable.ic_round_person_32, getString(R.string.follow))
        val globalTab = navBar.createTab(R.drawable.ic_globe_24, getString(R.string.global))
        navBar.addTab(personalTab)
        navBar.addTab(globalTab)
        binding.listTitle.text = getString(R.string.activities)
        binding.listToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        val activityId = intent.getIntExtra("activityId", -1)
        binding.feedViewPager.adapter =
            ViewPagerAdapter(supportFragmentManager, lifecycle, activityId)
        binding.feedViewPager.isUserInputEnabled = false
        navBar.setupWithViewPager2(binding.feedViewPager)
        navBar.onTabSelected = { selected = navBar.selectedIndex }
        binding.feedViewPager.setCurrentItem(selected, false)
        navBar.selectTabAt(selected, false)

        binding.listBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.activityFAB.setOnClickListener {
            startActivity(
                Intent(this, MarkdownCreatorActivity::class.java).apply {
                        putExtra("type", MarkdownCreatorActivity.ACTIVITY)
                }
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        navBar.apply {
            withFlexibleMargin(newConfig)
        }
        binding.feedViewPager.setBaseline(navBar, newConfig)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        fabHandler.removeCallbacksAndMessages(null)
        binding.activityFAB.visibility = View.VISIBLE
        fabHandler.postDelayed({
            if (binding.activityFAB.isVisible) binding.activityFAB.visibility = View.GONE
        }, 5000)
    }

    override fun onRestart() {
        super.onRestart()
        if (this::navBar.isInitialized) navBar.selectTabAt(selected)
    }

    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val activityId: Int
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> FeedFragment.newInstance(null, false, activityId)
                else -> FeedFragment.newInstance(null, true, -1)
            }
        }
    }
}
