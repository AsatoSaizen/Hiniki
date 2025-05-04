package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.discord.Discord
import ani.himitsu.connections.mal.MAL
import ani.himitsu.databinding.ActivitySettingsMainBinding
import ani.himitsu.loadImage
import ani.himitsu.openLinkInBrowser
import ani.himitsu.openLinkInYouTube
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.settings.Page
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.containsQuery
import ani.himitsu.settings.extension.DiscordDialogFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.toast
import bit.himitsu.content.reboot
import bit.himitsu.update.MatagiUpdater
import bit.himitsu.webkit.ChromeIntegration
import bit.himitsu.webkit.setWebClickListeners
import bit.himitsu.widget.onCompletedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext


class SettingsMainFragment : Fragment() {
    lateinit var binding: ActivitySettingsMainBinding
    private var cursedCounter = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            settingsBack.setOnClickListener {
                settings.reboot()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.DROPDOWN,
                        nameRes = R.string.data_setting,
                        icon = R.drawable.round_electric_bolt_24,
                        adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.item_dropdown,
                            resources.getStringArray(R.array.quickSettings)
                        ),
                        defaultText = resources.getStringArray(R.array.quickSettings)[getDataIndex()],
                        onItemClick = { item ->
                            setDataDefaults(item)
                            settings.recreate()
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.anilist,
                        descRes = R.string.ani_setting_desc,
                        icon = R.drawable.ic_anilist,
                        onClick = {
                            ChromeIntegration.openStreamTab(
                                requireContext(), "https://anilist.co/settings"
                            )
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.ui_settings,
                        descRes = R.string.ui_settings_desc,
                        icon = R.drawable.round_auto_awesome_24,
                        onClick = {
                            settings.setFragment(Page.UI)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.theme,
                        descRes = R.string.theme_desc,
                        icon = R.drawable.ic_palette,
                        onClick = {
                            settings.setFragment(Page.THEME)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.common,
                        descRes = R.string.common_desc,
                        icon = R.drawable.ic_lightbulb_24,
                        onClick = {
                            settings.setFragment(Page.COMMON)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.anime,
                        descRes = R.string.anime_desc,
                        icon = R.drawable.round_movie_filter_24,
                        onClick = {
                            settings.setFragment(Page.ANIME)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.manga,
                        descRes = R.string.manga_desc,
                        icon = R.drawable.round_import_contacts_24,
                        onClick = {
                            settings.setFragment(Page.MANGA)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.extensions,
                        descRes = R.string.extensions_desc,
                        icon = R.drawable.ic_extension,
                        onClick = {
                            settings.setFragment(Page.EXTENSION)
                        },
                        hasTransition = true,
                        isVisible = !PrefManager.getVal<Boolean>(PrefName.ClientMode)
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.notifications,
                        descRes = R.string.notifications_desc,
                        icon = R.drawable.round_notifications_none_24,
                        onClick = {
                            settings.setFragment(Page.NOTIFICATION)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.addons,
                        descRes = R.string.addons_desc,
                        icon = R.drawable.round_sports_kabaddi_24,
                        onClick = {
                            settings.setFragment(Page.ADDON)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.system,
                        descRes = R.string.system_desc,
                        icon = R.drawable.round_admin_panel_settings_24,
                        onClick = {
                            settings.setFragment(Page.SYSTEM)
                        },
                        hasTransition = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.about,
                        descRes = R.string.about_desc,
                        icon = R.drawable.round_info_outline_24,
                        onClick = {
                            settings.setFragment(Page.ABOUT)
                        },
                        hasTransition = true
                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
            }

            fun filterSectionsByQuery(query: String?) {
                settingsAdapter.setItemVisibility(
                    1, // AniList
                    getString(R.string.anilist) == query
                )
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.uiResources.containsQuery(query)
                    withUIContext { settingsAdapter.setItemVisibility(2, isVisible) }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.themeResources.containsQuery(query)
                    withUIContext { settingsAdapter.setItemVisibility(3, isVisible) }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.commonResources.containsQuery(query)
                    withUIContext { settingsAdapter.setItemVisibility(4, isVisible) }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.animeResources.containsQuery(query)
                    withUIContext {
                        settingsAdapter.setItemVisibility(5, isVisible)
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.mangaResources.containsQuery(query)
                    withUIContext {
                        settingsAdapter.setItemVisibility(6, isVisible)
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.extensionResources.containsQuery(query)
                    withUIContext {
                        settingsAdapter.setItemVisibility(7, isVisible)
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.notificationResources.containsQuery(query)
                    withUIContext {
                        settingsAdapter.setItemVisibility(8, isVisible)
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.addonResources.containsQuery(query)
                    withUIContext {
                        settingsAdapter.setItemVisibility(9, isVisible)
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.systemResources.containsQuery(query)
                    withUIContext {
                        settingsAdapter.setItemVisibility(10, isVisible)
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val isVisible = settings.model.aboutResources.containsQuery(query)
                    withUIContext {
                        settingsAdapter.setItemVisibility(11, isVisible)
                    }
                }
            }

            settings.model.getQuery().observe(viewLifecycleOwner) { query ->
                filterSectionsByQuery(query)
                binding.searchViewText.setText(query)
            }
            binding.searchViewText.setOnEditorActionListener(onCompletedAction {
                with (requireContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager) {
                    hideSoftInputFromWindow(binding.searchViewText.windowToken, 0)
                }
                settings.model.setQuery(binding.searchViewText.text?.toString())
            })
            binding.searchView.setEndIconOnClickListener {
                settings.model.setQuery(binding.searchViewText.text?.toString())
            }

            binding.apply {
                fun reload() {
                    if (AniList.token != null) {
                        settingsAniListLogin.setText(R.string.logout)
                        settingsAniListLogin.setOnClickListener {
                            AniList.removeSavedToken()
                            settings.recreate()
                            reload()
                        }
                        settingsAniListUsername.visibility = View.VISIBLE
                        settingsAniListUsername.text = AniList.username
                        settingsAniListAvatar.loadImage(AniList.avatar)
                        settingsAniListAvatar.setWebClickListeners(
                            getString(
                                R.string.anilist_link,
                                PrefManager.getVal<String>(PrefName.AnilistUserName)
                            )
                        )

                        settingsMALLoginRequired.visibility = View.GONE
                        settingsMALLogin.visibility = View.VISIBLE
                        settingsMALUsername.visibility = View.VISIBLE

                        if (MAL.token != null) {
                            settingsMALLogin.setText(R.string.logout)
                            settingsMALLogin.setOnClickListener {
                                MAL.removeSavedToken()
                                settings.recreate()
                                reload()
                            }
                            settingsMALUsername.visibility = View.VISIBLE
                            settingsMALUsername.text = MAL.username
                            settingsMALAvatar.loadImage(MAL.avatar)
                            settingsMALAvatar.setWebClickListeners(
                                getString(R.string.myanilist_link, MAL.username)
                            )
                            settingsMalIcon.setWebClickListeners("https://myanimelist.net/editprofile.php")
                        } else {
                            settingsMALAvatar.setOnClickListener { MAL.loginIntent() }
                            settingsMALAvatar.setOnClickListener { MAL.loginIntent() }
                            settingsMalLoginContainer.setOnClickListener {
                                MAL.loginIntent()
                            }
                            settingsMALAvatar.setImageResource(R.drawable.ic_round_person_32)
                            settingsMALUsername.visibility = View.GONE
                            settingsMALLogin.setText(R.string.login)
                            settingsMalIcon.setWebClickListeners("https://myanimelist.net/")
                        }
                        settingsAniListIcon.setWebClickListeners("https://anilist.co/settings")
                    } else {
                        settingsAniListAvatar.setWebClickListeners(AniList.LOGIN_URL)
                        settingsAniListLogin.setWebClickListeners(AniList.LOGIN_URL)
                        settingsAniListLoginContainer.setWebClickListeners(AniList.LOGIN_URL)
                        settingsAniListAvatar.setImageResource(R.drawable.ic_round_person_32)
                        settingsAniListUsername.visibility = View.GONE
                        settingsAniListLogin.setText(R.string.login)
                        settingsMALLoginRequired.visibility = View.VISIBLE
                        settingsMALLogin.visibility = View.GONE
                        settingsMALUsername.visibility = View.GONE
                        settingsAniListIcon.setWebClickListeners("https://anilist.co/")
                    }


                    if (Discord.getSavedToken()) {
                        val id = PrefManager.getVal(PrefName.DiscordId, null as String?)
                        val avatar = PrefManager.getVal(PrefName.DiscordAvatar, null as String?)
                        val username = PrefManager.getVal(PrefName.DiscordUserName, null as String?)
                        if (id != null && avatar != null) {
                            settingsDiscordAvatar.loadImage(
                                "https://cdn.discordapp.com/avatars/$id/$avatar.png"
                            )
                            settingsDiscordAvatar.setWebClickListeners(
                                getString(R.string.discord_link, id)
                            )
                        }
                        settingsDiscordUsername.visibility = View.VISIBLE
                        settingsDiscordUsername.text =
                            username ?: Discord.token?.replace(Regex("."), "*")
                        settingsDiscordLogin.setText(R.string.logout)
                        settingsDiscordLogin.setOnClickListener {
                            Discord.removeSavedToken(settings) {
                                Discord.token = null
                                settings.recreate()
                                reload()
                            }
                        }

                        settingsImageSwitcher.visibility = View.VISIBLE
                        var initialStatus =
                            when (PrefManager.getVal<String>(PrefName.DiscordStatus)) {
                                "online" -> R.drawable.discord_status_online
                                "idle" -> R.drawable.discord_status_idle
                                "dnd" -> R.drawable.discord_status_dnd
                                "invisible" -> R.drawable.discord_status_invisible
                                else -> R.drawable.discord_status_online
                            }
                        settingsImageSwitcher.setImageResource(initialStatus)

                        val zoomInAnimation =
                            AnimationUtils.loadAnimation(settings, R.anim.bounce_zoom)
                        settingsImageSwitcher.setOnClickListener {
                            var status = "online"
                            initialStatus = when (initialStatus) {
                                R.drawable.discord_status_online -> {
                                    status = "idle"
                                    R.drawable.discord_status_idle
                                }

                                R.drawable.discord_status_idle -> {
                                    status = "dnd"
                                    R.drawable.discord_status_dnd
                                }

                                R.drawable.discord_status_dnd -> {
                                    status = "invisible"
                                    R.drawable.discord_status_invisible
                                }

                                R.drawable.discord_status_invisible -> {
                                    status = "online"
                                    R.drawable.discord_status_online
                                }

                                else -> R.drawable.discord_status_online
                            }

                            PrefManager.setVal(PrefName.DiscordStatus, status)
                            settingsImageSwitcher.setImageResource(initialStatus)
                            settingsImageSwitcher.startAnimation(zoomInAnimation)
                        }
                        settingsImageSwitcher.setOnLongClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            DiscordDialogFragment().show(settings.supportFragmentManager, "dialog")
                            true
                        }
                        settingsDiscordIcon.setOnClickListener {
                            DiscordDialogFragment().show(settings.supportFragmentManager, "dialog")
                        }
                        settingsDiscordIcon.setOnLongClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            openLinkInBrowser("https://discord.com/")
                            true
                        }
                    } else {
                        settingsDiscordAvatar.setOnClickListener {
                            Discord.warning(settings)
                                .show(settings.supportFragmentManager, "dialog")
                        }
                        settingsDiscordAvatar.setOnClickListener {
                            Discord.warning(settings)
                                .show(settings.supportFragmentManager, "dialog")
                        }
                        settingsDiscordLoginContainer.setOnClickListener {
                            Discord.warning(settings)
                                .show(settings.supportFragmentManager, "dialog")
                        }
                        settingsImageSwitcher.visibility = View.GONE
                        settingsDiscordAvatar.setImageResource(R.drawable.ic_round_person_32)
                        settingsDiscordUsername.visibility = View.GONE
                        settingsDiscordLogin.setText(R.string.login)
                        settingsDiscordIcon.setWebClickListeners("https://discord.com/")
                    }
                }
                reload()
            }

            binding.settingsLogo.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                lifecycleScope.launch(Dispatchers.IO) {
                    MatagiUpdater.check(settings, true)
                }
                true
            }

            (settingsLogo.drawable as Animatable).start()
            val array = resources.getStringArray(R.array.tips)

            settingsLogo.setSafeOnClickListener {
                cursedCounter++
                (binding.settingsLogo.drawable as Animatable).start()
                if (cursedCounter % 7 == 0) {
                    toast(R.string.you_cursed)
                    openLinkInYouTube(getString(R.string.cursed_yt))
                    // PrefManager.setVal(PrefName.ImageUrl, !PrefManager.getVal(PrefName.ImageUrl, false))
                } else {
                    snackString(array[(Math.random() * array.size).toInt()], settings)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.searchViewText.setText(
            (requireActivity() as SettingsActivity).model.getQuery().value
        )
    }

    fun getDataIndex(): Int {
        val affinity = PrefManager.getVal<Int>(PrefName.LoadingAffinity)
        val sources = PrefManager.getVal<Boolean>(PrefName.SearchSources)
        val socials = PrefManager.getVal<Boolean>(PrefName.SocialInMedia)
        val covers = PrefManager.getVal<Boolean>(PrefName.TrendingCovers)
        val youtube = PrefManager.getVal<Boolean>(PrefName.YouTubeBanners)

        return when {
            affinity == 0 && !sources && socials && covers && youtube -> 1 // Defaults
            affinity == 2 && !sources && !socials && !covers && !youtube -> 2 // Fast Loading
            affinity == -2 && sources && socials && covers && youtube -> 3 // More Content
            else -> 0 // Custom
        }
    }

    fun setDataDefaults(selected: Int) {
        when (selected) {
            1 -> { // Defaults
                PrefManager.setVal(PrefName.LoadingAffinity, 0)
                PrefManager.setVal(PrefName.SearchSources, false)
                PrefManager.setVal(PrefName.AnifyTimeout, 0.75f)
                PrefManager.setVal(PrefName.KitsuTimeout, 1f)
                PrefManager.setVal(PrefName.SocialInMedia, true)
                PrefManager.setVal(PrefName.YouTubeBanners, true)
            }
            2 -> { // Fast Loading
                PrefManager.setVal(PrefName.LoadingAffinity, 2)
                PrefManager.setVal(PrefName.SearchSources, false)
                PrefManager.setVal(PrefName.AnifyTimeout, 0.5f)
                PrefManager.setVal(PrefName.KitsuTimeout, 0.75f)
                PrefManager.setVal(PrefName.SocialInMedia, false)
                PrefManager.setVal(PrefName.YouTubeBanners, false)
            }
            3 -> { // More Content
                PrefManager.setVal(PrefName.LoadingAffinity, -2)
                PrefManager.setVal(PrefName.SearchSources, true)
                PrefManager.setVal(PrefName.AnifyTimeout, 1f)
                PrefManager.setVal(PrefName.KitsuTimeout, 1.25f)
                PrefManager.setVal(PrefName.SocialInMedia, true)
                PrefManager.setVal(PrefName.YouTubeBanners, true)
            }
            else -> { }
        }
    }
}
