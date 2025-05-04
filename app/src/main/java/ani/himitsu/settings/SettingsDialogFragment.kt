package ani.himitsu.settings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.MainActivity
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.databinding.BottomSheetSettingsBinding
import ani.himitsu.databinding.ItemAppUpdateBinding
import ani.himitsu.download.anime.OfflineAnimeFragment
import ani.himitsu.download.manga.OfflineMangaFragment
import ani.himitsu.home.AnimeFragment
import ani.himitsu.home.HomeFragment
import ani.himitsu.home.LoginFragment
import ani.himitsu.home.MangaFragment
import ani.himitsu.home.NoInternet
import ani.himitsu.incognitoNotification
import ani.himitsu.loadImage
import ani.himitsu.notifications.NotificationActivity
import ani.himitsu.offline.OfflineFragment
import ani.himitsu.profile.ProfileActivity
import ani.himitsu.profile.activity.FeedActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import bit.himitsu.content.reboot
import bit.himitsu.content.toPx
import bit.himitsu.search.ReverseSearchDialogFragment
import bit.himitsu.setNavigationTheme
import bit.himitsu.update.MatagiUpdater
import bit.himitsu.webkit.ChromeIntegration
import bit.himitsu.webkit.setWebClickListeners
import bit.himitsu.widget.onCompletedActionText
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import java.util.Timer
import kotlin.concurrent.schedule

class SettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSettingsBinding? = null
    private val binding by lazy { _binding!! }

    private lateinit var pageType: PageType
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageType = arguments?.getSerializableCompat("pageType") ?: PageType.HOME
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.run {
            setNavigationTheme()
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        }
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        MatagiUpdater.notifyOnUpdate(this, ItemAppUpdateBinding.bind(binding.root))

        val offline = PrefManager.getVal<Boolean>(PrefName.OfflineMode)
        val offline_ext = !offline || PrefManager.getVal<Boolean>(PrefName.OfflineExt)
        val clientMode = PrefManager.getVal<Boolean>(PrefName.ClientMode)
        if (offline) {
            binding.settingsAccount.isVisible = false
            binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = 16.toPx
            }
        } else {
            val notificationIcon = if (AniList.unreadNotificationCount > 0) {
                R.drawable.round_notifications_active_24
            } else {
                R.drawable.round_notifications_none_24
            }
            binding.settingsNotification.setImageResource(notificationIcon)

            if (AniList.token != null) {
                binding.settingsLogin.setText(R.string.logout)
                binding.settingsLogin.setOnClickListener {
                    requireContext().customAlertDialog().apply {
                        setTitle(R.string.logout)
                        setMessage(R.string.logout_confirm)
                        setPositiveButton(R.string.yes) {
                            AniList.removeSavedToken()
                            this@SettingsDialogFragment.dismiss()
                            requireContext().reboot()
                        }
                        setNegativeButton(R.string.no)
                        show()
                    }
                }
                binding.settingsUsername.text = AniList.username
                binding.settingsUserAvatar.loadImage(AniList.avatar)
                binding.settingsUserAvatar.setOnClickListener {
                    requireContext().startActivity(
                        Intent(requireContext(), ProfileActivity::class.java)
                            .putExtra("userId", AniList.userid)
                    )
                }
            } else {
                binding.settingsUserAvatar.setWebClickListeners(AniList.LOGIN_URL) { dismiss() }
                binding.settingsLogin.setWebClickListeners(AniList.LOGIN_URL) { dismiss() }
                binding.settingsLoginContainer.setWebClickListeners(AniList.LOGIN_URL) { dismiss() }
                binding.settingsUsername.visibility = View.GONE
                binding.settingsLogin.setText(R.string.login)
            }

            val count = AniList.unreadNotificationCount
            binding.settingsNotificationCount.isVisible = count > 0
            binding.settingsNotificationCount.text = "$count"
        }

        binding.settingsNotification.setOnClickListener {
            startActivity(Intent(activity, NotificationActivity::class.java))
            dismiss()
        }

        binding.settingsNotification.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startActivity(Intent(activity, FeedActivity::class.java))
            dismiss()
            true
        }

        fun search(query: Editable?) {
            if (query.isNullOrBlank()) return
            ReverseSearchDialogFragment(query.toString()).show(
                requireActivity().supportFragmentManager, null
            )
            dismiss()
        }

        binding.searchView.setEndIconOnClickListener {
            search(binding.searchViewText.text)
        }
        binding.searchView.isVisible = offline_ext && !clientMode

        binding.searchViewText.setOnEditorActionListener(onCompletedActionText {
            search(binding.searchViewText.text)
        })

        val settingsAdapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.offline_mode,
                    icon = R.drawable.ic_download_24,
                    isChecked = PrefManager.getVal(PrefName.OfflineMode),
                    switch = { isChecked, _ ->
                        Timer().schedule(300) {
                            PrefManager.setVal(PrefName.OfflineMode, isChecked)
                            startActivity(
                                when (pageType) {
                                    PageType.MANGA -> {
                                        Intent(activity, NoInternet::class.java).apply {
                                            putExtra(
                                                "FRAGMENT_CLASS_NAME",
                                                OfflineMangaFragment::class.java.name
                                            )
                                        }
                                    }

                                    PageType.ANIME -> {
                                        Intent(activity, NoInternet::class.java).apply {
                                            putExtra(
                                                "FRAGMENT_CLASS_NAME",
                                                OfflineAnimeFragment::class.java.name
                                            )
                                        }
                                    }

                                    PageType.HOME -> {
                                        Intent(activity, NoInternet::class.java).apply {
                                            putExtra("FRAGMENT_CLASS_NAME", OfflineFragment::class.java.name)
                                        }
                                    }

                                    PageType.OfflineMANGA -> {
                                        Intent(activity, MainActivity::class.java).apply {
                                            putExtra("FRAGMENT_CLASS_NAME", MangaFragment::class.java.name)
                                        }
                                    }

                                    PageType.OfflineHOME -> {
                                        Intent(activity, MainActivity::class.java).apply {
                                            putExtra(
                                                "FRAGMENT_CLASS_NAME",
                                                if (AniList.token != null)
                                                    HomeFragment::class.java.name
                                                else
                                                    LoginFragment::class.java.name
                                            )
                                        }
                                    }

                                    PageType.OfflineANIME -> {
                                        Intent(activity, MainActivity::class.java).apply {
                                            putExtra("FRAGMENT_CLASS_NAME", AnimeFragment::class.java.name)
                                        }
                                    }
                                }
                            )
                            dismiss()
                        }
                    }
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.incognito_mode,
                    icon = R.drawable.ic_incognito_24,
                    isChecked = PrefManager.getVal(PrefName.Incognito),
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.Incognito, isChecked)
                        incognitoNotification(requireContext())
                    },
                    isVisible = false
                ),
                Settings(
                    type = ViewType.SWITCH,
                    nameRes = R.string.client_mode,
                    icon = R.drawable.round_security_24,
                    isChecked = PrefManager.getVal(PrefName.Incognito),
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.ClientMode, isChecked)
                        binding.searchView.isVisible = !isChecked
                        (binding.settingsRecyclerView.adapter as SettingsAdapter)
                            .setItemVisibility(5, offline_ext && !isChecked)
                    },
                    isVisible = false
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.ani_forum,
                    icon = R.drawable.round_forum_24,
                    onClick = {
                        ChromeIntegration.openStreamTab(
                            requireContext(),
                            "https://anilist.co/forum/overview"
                        )
                        dismiss()
                    },
                    onLongClick = {
                        ChromeIntegration.openStreamDialog(
                            requireContext(),
                            "https://anilist.co/forum/overview"
                        )
                    },
                    isDialog = true,
                    isVisible = PrefManager.getVal(PrefName.ShowForumButton)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.anibrain,
                    icon = R.drawable.ic_anibrain,
                    onClick = {
                        ChromeIntegration.openStreamTab(
                            requireContext(),
                            "https://anime.ameo.dev/user/${AniList.username}/recommendations?source=anilist"
                        )
                        dismiss()
                    },
                    onLongClick = {
                        ChromeIntegration.openStreamDialog(
                            requireContext(),
                            "https://anime.ameo.dev/user/${AniList.username}/recommendations?source=anilist"
                        )
                    },
                    isDialog = true,
                    isVisible = PrefManager.getVal(PrefName.ShowAnibrainButton)
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.extension_settings,
                    icon = R.drawable.ic_extension,
                    onClick = {
                        startActivity(Intent(activity, ExtensionsActivity::class.java))
                        dismiss()
                    },
                    isActivity = true,
                    isVisible = offline_ext && !clientMode
                ),
                Settings(
                    type = ViewType.BUTTON,
                    nameRes = R.string.settings,
                    icon = R.drawable.round_settings_24,
                    onClick = {
                        startActivity(Intent(activity, SettingsActivity::class.java))
                        dismiss()
                    },
                    isActivity = true
                )
            )
        )
        binding.settingsRecyclerView.adapter = settingsAdapter
        binding.settingsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
        }

        binding.settingsUserAvatar.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            settingsAdapter.toggleItemVisibility(1)
            settingsAdapter.toggleItemVisibility(2)
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        enum class PageType {
            MANGA, ANIME, HOME, OfflineMANGA, OfflineANIME, OfflineHOME
        }

        fun newInstance(pageType: PageType): SettingsDialogFragment {
            val fragment = SettingsDialogFragment()
            val args = Bundle()
            args.putSerializable("pageType", pageType)
            fragment.arguments = args
            return fragment
        }
    }
}
