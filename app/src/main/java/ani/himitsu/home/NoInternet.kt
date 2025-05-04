package ani.himitsu.home

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.himitsu.R
import ani.himitsu.databinding.ActivityNoInternetBinding
import ani.himitsu.download.anime.OfflineAnimeFragment
import ani.himitsu.download.manga.OfflineMangaFragment
import ani.himitsu.initActivity
import ani.himitsu.navBarHeight
import ani.himitsu.offline.OfflineFragment
import ani.himitsu.selectedOption
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import bit.himitsu.os.Version
import nl.joery.animatedbottombar.AnimatedBottomBar

class NoInternet : AppCompatActivity() {
    private lateinit var binding: ActivityNoInternetBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()

        binding = ActivityNoInternetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomBar = findViewById<AnimatedBottomBar>(R.id.navbar)
        if (Version.isNougat) {
            val backgroundDrawable = bottomBar.background as GradientDrawable
            val currentColor = backgroundDrawable.color?.defaultColor ?: 0
            val semiTransparentColor = (currentColor and 0x00FFFFFF) or 0xE8000000.toInt()
            backgroundDrawable.setColor(semiTransparentColor)
            bottomBar.background = backgroundDrawable
        }
        bottomBar.background = ContextCompat.getDrawable(this, R.drawable.bottom_nav_gray)


        var doubleBackToExitPressedOnce = false
        onBackPressedDispatcher.addCallback(this) {
            if (doubleBackToExitPressedOnce) {
                finishAffinity()
            }
            doubleBackToExitPressedOnce = true
            toast(this@NoInternet.getString(R.string.back_to_exit))
            Handler(Looper.getMainLooper()).postDelayed(
                { doubleBackToExitPressedOnce = false },
                2000
            )
        }

        binding.root.doOnAttach {
            initActivity(this)
            selectedOption = PrefManager.getVal(PrefName.DefaultStartUpTab)

            binding.includedNavbar.navbarContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarHeight
            }
        }
        val navbar = binding.includedNavbar.navbar
        ani.himitsu.bottomBar = navbar
        navbar.visibility = View.VISIBLE
        val mainViewPager = binding.viewpager
        mainViewPager.isUserInputEnabled = false
        mainViewPager.adapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
        // mainViewPager.setPageTransformer(ZoomOutPageTransformer())
        navbar.setupWithViewPager2(mainViewPager)
        navbar.onTabSelected = { selectedOption = navbar.selectedIndex }
        mainViewPager.setCurrentItem(selectedOption, false)
        navbar.selectTabAt(selectedOption, false)

        // supportFragmentManager.beginTransaction().replace(binding.fragmentContainer.id, OfflineFragment()).commit()

    }

    private class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
        FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OfflineAnimeFragment()
                2 -> OfflineMangaFragment()
                else -> OfflineFragment()
            }
        }
    }
}