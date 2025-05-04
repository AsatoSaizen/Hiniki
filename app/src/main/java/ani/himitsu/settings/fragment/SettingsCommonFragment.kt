package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.connections.comments.CommentsAPI
import ani.himitsu.Himitsu
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.databinding.ActivitySettingsCommonBinding
import ani.himitsu.download.DownloadsManager
import ani.himitsu.media.cereal.Media
import ani.himitsu.notifications.subscription.SubscriptionHelper
import ani.himitsu.notifications.subscription.SubscriptionHelper.saveSubscription
import ani.himitsu.restart
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.SubscriptionsBottomDialog
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.toast
import ani.himitsu.util.customAlertDialog
import bit.himitsu.nio.unicode
import bit.himitsu.widget.onCompletedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsCommonFragment : Fragment() {
    private lateinit var binding: ActivitySettingsCommonBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsCommonBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity
        val component = ComponentName(settings.packageName, settings::class.qualifiedName!!)

        val managers = resources.getStringArray(R.array.downloadManagers)
        val downloadManagerDialog =
            AlertDialog.Builder(settings, R.style.MyDialog).setTitle(R.string.download_manager)
        var downloadManager: Int = PrefManager.getVal(PrefName.DownloadManager)

        binding.apply {
            commonSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.general
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.always_continue_content,
                        descRes = R.string.always_continue_content_desc,
                        icon = R.drawable.round_insert_page_break_24,
                        pref = PrefName.ContinueMedia
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.add_shortcuts,
                        descRes = R.string.add_shortcuts_desc,
                        icon = R.drawable.ic_app_shortcut_24,
                        isChecked = PrefManager.getVal(PrefName.UseShortcuts),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.UseShortcuts, isChecked)
                            settings.restart(component)
                        },
                        isVisible = !Himitsu.isAndroidTV
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.recentlyListOnly,
                        descRes = R.string.recentlyListOnly_desc,
                        icon = R.drawable.round_new_releases_24,
                        pref = PrefName.RecentlyListOnly
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.adult_only_content,
                        descRes = R.string.adult_only_content_desc,
                        icon = R.drawable.round_no_adult_content_24,
                        isChecked = PrefManager.getVal(PrefName.AdultOnly),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.AdultOnly, isChecked)
                            Refresh.all()
                        }
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.media
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.descending_items,
                        descRes = R.string.descending_items_desc,
                        icon = R.drawable.round_stairs_24,
                        pref = PrefName.DescendingItems
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.social_in_media,
                        icon = R.drawable.ic_emoji_people_24,
                        pref = PrefName.SocialInMedia
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.sync_history,
                        icon = R.drawable.round_cloud_sync_24,
                        pref = PrefName.SyncProgress,
                        isEnabled = AniList.userid != null
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.comments_api,
                        descRes = R.string.comments_api_desc,
                        icon = R.drawable.ic_round_comment_24,
                        isChecked = PrefManager.getVal(PrefName.CommentsOptIn),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.CommentsOptIn, isChecked)
                            CoroutineScope(Dispatchers.IO).launch {
                                if (isChecked) {
                                    CommentsAPI.fetchAuthToken(settings)
                                } else {
                                    CommentsAPI.logout()
                                }
                            }
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.search_source_list,
                        descRes = R.string.search_source_list_desc,
                        icon = R.drawable.round_blind_24,
                        pref = PrefName.SearchSources,
                        itemsEnabled = arrayOf(12)
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.subscriptions
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.view_subscriptions,
                        descRes = R.string.view_subscriptions_desc,
                        icon = R.drawable.round_search_24,
                        onClick = {
                            SubscriptionHelper.getSubscriptions().let {
                                if (it.isEmpty()) {
                                    snackString(R.string.subscriptions_empty)
                                } else {
                                    SubscriptionsBottomDialog.newInstance(it).show(
                                        settings.supportFragmentManager,
                                        "subscriptions"
                                    )
                                }
                            }
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.subscribe_lists,
                        descRes = R.string.subscribe_lists_desc,
                        icon = R.drawable.round_notifications_active_24,
                        onClick = {
                            subscribeCurrentLists()
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.clear_subscriptions,
                        icon = R.drawable.round_delete_sweep_24,
                        onClick = {
                            settings.customAlertDialog().apply {
                                setMessage(R.string.clear_subscriptions_confirm)
                                setPositiveButton(R.string.yes, onClick = {
                                    SubscriptionHelper.clearSubscriptions()
                                })
                                setNegativeButton(R.string.no)
                                show()
                            }
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.downloads
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.download_manager_select,
                        descRes = R.string.download_manager_select_desc,
                        icon = R.drawable.ic_download_24,
                        onClick = {
                            val dialog = downloadManagerDialog.setSingleChoiceItems(
                                managers, downloadManager
                            ) { dialog, count ->
                                downloadManager = count
                                PrefManager.setVal(PrefName.DownloadManager, downloadManager)
                                dialog.dismiss()
                            }.show()
                            dialog.window?.setDimAmount(0.8f)
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.delete_imported,
                        descRes = R.string.delete_imported_desc,
                        icon = R.drawable.round_source_24,
                        pref = PrefName.DeleteOnImport
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.change_download_location,
                        desc = PrefManager.getVal<String>(PrefName.DownloadsDir)
                            .substringAfter("/tree").unicode,
                        icon = R.drawable.round_drive_file_move_rtl_24,
                        onClick = {
                            settings.customAlertDialog().apply {
                                setTitle(R.string.change_download_location)
                                setMessage(R.string.download_location_msg)
                                setPositiveButton(R.string.yes) {
                                    val oldUri = PrefManager.getVal<String>(PrefName.DownloadsDir)
                                    settings.getLauncher()?.registerForCallback { newUri ->
                                        newUri?.let {
                                            toast(getString(R.string.please_wait))
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                Injekt.get<DownloadsManager>().moveDownloadsDir(
                                                    settings, Uri.parse(oldUri), Uri.parse(newUri)
                                                ) { finished, message ->
                                                    if (finished) {
                                                        lifecycleScope.launch(Dispatchers.Main) {
                                                            settings.recreate()
                                                        }
                                                    }
                                                    toast(message)
                                                }
                                            }
                                        } ?: toast(getString(R.string.error))
                                    }?.launch()
                                }
                                setNegativeButton(R.string.no)
                                show()
                            }
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.reset_download_location,
                        icon = R.drawable.round_rule_folder_24,
                        onClick =  {
                            settings.customAlertDialog().apply {
                                setTitle(R.string.reset_download_location)
                                setMessage(R.string.download_location_msg)
                                setPositiveButton(R.string.yes) {
                                    PrefManager.removeVal(PrefName.DownloadsDir)
                                    settings.getLauncher()?.registerForCallback { newUri ->
                                        newUri?.let {
                                            PrefManager.setVal(PrefName.DownloadsDir, newUri)
                                            lifecycleScope.launch(Dispatchers.Main) {
                                                settings.recreate()
                                            }
                                        } ?: toast(getString(R.string.error))
                                    }?.launch()
                                }
                                setNegativeButton(R.string.no)
                                show()
                            }
                        },
                        isDialog = true
                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            settings.model.getQuery().observe(viewLifecycleOwner) { query ->
                settingsAdapter.getFilter()?.filter(query)
            }
            binding.searchViewText.setText(settings.model.getQuery().value)
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
        }
    }

    private fun subscribeCurrentLists() {
        CoroutineScope(Dispatchers.IO).launch {
            val userList = arrayListOf<Any>().apply {
                AniList.query.initHomePage().let { list ->
                    list["currentAnime"]?.let { addAll(it) }
                    list["plannedAnime"]?.let { addAll(it) }
                    list["currentManga"]?.let { addAll(it) }
                    list["plannedManga"]?.let { addAll(it) }
                }
            }
            if (userList.isEmpty()) {
                snackString(R.string.no_current_items)
            } else {
                userList.forEach {
                    saveSubscription(it as Media, true)
                }
                snackString(R.string.current_subscribed)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
        }
    }
}