package ani.himitsu.media

import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.databinding.ActivityListBinding
import ani.himitsu.media.user.ListViewPagerAdapter
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.hideSystemBarsExtendView
import bit.himitsu.setNavigationTheme
import bit.himitsu.setStatusColor
import bit.himitsu.setStatusTheme
import bit.himitsu.teamup.DubReleaseDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import eu.kanade.tachiyomi.util.system.getThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext

class CalendarActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListBinding
    private val scope = lifecycleScope
    private var selectedTabIdx = 7
    private var hasInitialized = false
    private var dubFragment: DubReleaseDialogFragment? = null
    private val model: OtherDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityListBinding.inflate(layoutInflater)

        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryTextColor = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        val secondaryTextColor = getThemeColor(com.google.android.material.R.attr.colorOutline)

        window.setNavigationTheme()
        binding.listTabLayout.setBackgroundColor(primaryColor)
        binding.listAppBar.setBackgroundColor(primaryColor)
        binding.listTabLayout.setTabTextColors(secondaryTextColor, primaryTextColor)
        binding.listTabLayout.setSelectedTabIndicatorColor(primaryTextColor)
        if (!PrefManager.getVal<Boolean>(PrefName.ImmersiveMode)) {
            window.setStatusColor(R.color.nav_bg_inv)
            binding.root.fitsSystemWindows = true
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window.setStatusTheme()
            binding.root.fitsSystemWindows = false
            hideSystemBarsExtendView()
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }
        }
        setContentView(binding.root)

        binding.listBackButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.listTitle.setText(R.string.release_calendar)
        binding.random.visibility = View.GONE
        binding.search.visibility = View.GONE
        binding.filter.visibility = View.GONE
        binding.listSort.setImageResource(R.drawable.round_hearing_24)
        binding.listTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                this@CalendarActivity.selectedTabIdx = tab?.position ?: selectedTabIdx
                if (hasInitialized) tab?.position?.let {
                    binding.listSort.setOnClickListener { view ->
                        dubFragment = DubReleaseDialogFragment(
                            null, model.getCalendar().value?.keys?.elementAt(it)
                        ).apply {
                            show(supportFragmentManager, null)
                        }
                    }
                }
                if (!hasInitialized) hasInitialized = true
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        binding.listSort.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            DubReleaseDialogFragment(BottomSheetBehavior.STATE_EXPANDED)
                .show(supportFragmentManager, null)
            true
        }

        model.getCalendar().observe(this) {
            if (it != null) {
                binding.listProgressBar.visibility = View.GONE
                binding.listViewPager.adapter = ListViewPagerAdapter(it.size, this)
                val keys = it.keys.toList()
                val values = it.values.toList()
                val savedTab = this.selectedTabIdx
                TabLayoutMediator(binding.listTabLayout, binding.listViewPager) { tab, position ->
                    tab.text = "${keys[position]} (${values[position].size})"
                }.attach()
                binding.listViewPager.setCurrentItem(savedTab, false)
            }
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch {
                    withIOContext { model.loadCalendar() }
                    live.postValue(false)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
