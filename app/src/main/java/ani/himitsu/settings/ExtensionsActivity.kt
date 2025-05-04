package ani.himitsu.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.himitsu.R
import ani.himitsu.currContext
import ani.himitsu.databinding.ActivityExtensionsBinding
import ani.himitsu.initActivity
import ani.himitsu.media.MediaType
import ani.himitsu.navBarHeight
import ani.himitsu.others.AndroidBug5497Workaround
import ani.himitsu.others.LanguageMapper
import ani.himitsu.parsers.novel.NovelExtensionManager
import ani.himitsu.settings.extension.AnimeExtensionsFragment
import ani.himitsu.settings.extension.InstalledAnimeExtensionsFragment
import ani.himitsu.settings.extension.InstalledMangaExtensionsFragment
import ani.himitsu.settings.extension.InstalledNovelExtensionsFragment
import ani.himitsu.settings.extension.MangaExtensionsFragment
import ani.himitsu.settings.extension.NovelExtensionsFragment
import ani.himitsu.settings.extension.NovelPluginsFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.view.dialog.CustomBottomDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

class ExtensionsActivity : AppCompatActivity() {
    lateinit var binding: ActivityExtensionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityExtensionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        AndroidBug5497Workaround.assistActivity(this) {
            if (it) {
                binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = statusBarHeight
                }
            } else {
                binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = statusBarHeight + navBarHeight
                }
            }
        }

        binding.listBackButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = statusBarHeight + navBarHeight
        }
        binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        binding.viewPager.offscreenPageLimit = 1

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 7

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> InstalledAnimeExtensionsFragment()
                    1 -> AnimeExtensionsFragment()
                    2 -> InstalledMangaExtensionsFragment()
                    3 -> MangaExtensionsFragment()
                    4 -> InstalledNovelExtensionsFragment()
                    5 -> NovelExtensionsFragment()
                    6 -> NovelPluginsFragment()
                    else -> AnimeExtensionsFragment()
                }
            }

        }

        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    binding.searchViewText.setText("")
                    binding.searchViewText.clearFocus()
                    binding.tabLayout.clearFocus()
                    binding.languageSelect.isVisible = tab.text?.contains(
                        getString(R.string.available_extensions, "")
                    ) == true
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                    binding.tabLayout.clearFocus()
                }

                override fun onTabReselected(tab: TabLayout.Tab) { }
            }
        )

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.installed_extensions, MediaType.ANIME.string)
                1 -> getString(R.string.available_extensions, MediaType.ANIME.string)
                2 -> getString(R.string.installed_extensions, MediaType.MANGA.string)
                3 -> getString(R.string.available_extensions, MediaType.MANGA.string)
                4 -> getString(R.string.installed_extensions, MediaType.NOVEL.string)
                5 -> getString(R.string.available_extensions, MediaType.NOVEL.string)
                6 -> getString(R.string.novel_plugins)
                else -> null
            }
        }.attach()

        binding.searchViewText.addTextChangedListener {
            val currentFragment = supportFragmentManager
                .findFragmentByTag("f${binding.viewPager.currentItem}")
            if (currentFragment is SearchQueryHandler) {
                currentFragment.updateContentBasedOnQuery(it?.toString()?.trim())
            }
        }

        binding.openSettingsButton.setOnClickListener {
            onChangeSettings.launch(Intent(this, SettingsActivity::class.java)
                .putExtra(START_PAGE, Page.EXTENSION.name)
                .putExtra(SILENT_EXIT, true)
            )
        }

        binding.languageSelect.setOnClickListener {
            val languageOptions = LanguageMapper.codeMap.values.toTypedArray()
            val builder = AlertDialog.Builder(currContext(), R.style.MyDialog)
            val listOrder: String = PrefManager.getVal(PrefName.LangSort)
            val index = LanguageMapper.codeMap.entries.indexOfFirst { it.key == listOrder }
            builder.setTitle(R.string.language)
            builder.setSingleChoiceItems(languageOptions, index) { dialog, i ->
                PrefManager.setVal(
                    PrefName.LangSort,
                    LanguageMapper.codeMap.keys.filterIndexed { index, _ -> index == i }.first()
                )
                val currentFragment =
                    supportFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}")
                if (currentFragment is SearchQueryHandler) {
                    currentFragment.notifyDataChanged()
                }
                dialog.dismiss()
            }
            val dialog = builder.show()
            dialog.window?.setDimAmount(0.8f)
        }

        if (!PrefManager.getVal<Boolean>(PrefName.ExtensionNotice)) {
            CustomBottomDialog.newInstance().apply {
                title = this@ExtensionsActivity.getString(R.string.extension_notice)

                addView(TextView(this@ExtensionsActivity).apply {
                    text = this@ExtensionsActivity.getString(R.string.extension_notice_desc)
                })

                setPositiveButton(this@ExtensionsActivity.getString(R.string.close)) {
                    PrefManager.setVal(PrefName.ExtensionNotice, true)
                    dismiss()
                }
                show(this@ExtensionsActivity.supportFragmentManager, "dialog")
            }
        }
    }

    private val onChangeSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: ActivityResult ->

    }

    override fun onUserLeaveHint() {
        CoroutineScope(Dispatchers.IO).launch {
            val animeExtensionManager: AnimeExtensionManager by injectLazy()
            animeExtensionManager.findAvailableExtensions()
        }
        CoroutineScope(Dispatchers.IO).launch {
            val mangaExtensionManager: MangaExtensionManager by injectLazy()
            mangaExtensionManager.findAvailableExtensions()
        }
        CoroutineScope(Dispatchers.IO).launch {
            val novelExtensionManager: NovelExtensionManager by injectLazy()
            novelExtensionManager.findAvailableExtensions()
            novelExtensionManager.findAvailablePlugins()
        }
        super.onUserLeaveHint()
    }
}

interface SearchQueryHandler {
    fun updateContentBasedOnQuery(query: String?)
    fun notifyDataChanged()
}
