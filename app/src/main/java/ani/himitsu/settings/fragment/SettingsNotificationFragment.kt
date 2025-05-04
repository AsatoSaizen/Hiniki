package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.notifications.comment.CommentNotificationWorker
import ani.himitsu.R
import ani.himitsu.connections.anilist.api.NotificationType
import ani.himitsu.databinding.ActivitySettingsNotificationsBinding
import ani.himitsu.notifications.TaskScheduler
import ani.himitsu.notifications.anilist.AniListNotificationWorker
import ani.himitsu.notifications.subscription.SubscriptionNotificationWorker
import ani.himitsu.openSettings
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.os.Version
import bit.himitsu.widget.onCompletedAction

class SettingsNotificationFragment : Fragment() {
    private lateinit var binding: ActivitySettingsNotificationsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            notificationSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            var curTime = PrefManager.getVal<Int>(PrefName.SubscriptionNotificationInterval)
            val timeNames = SubscriptionNotificationWorker.checkIntervals.map {
                val mins = it % 60
                val hours = it / 60
                if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
                else getString(R.string.do_not_update)
            }.toTypedArray()

            val aTimeNames = AniListNotificationWorker.checkIntervals.map { it.toInt() }
            val aItems = aTimeNames.map {
                val mins = it % 60
                val hours = it / 60
                if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
                else getString(R.string.do_not_update)
            }

            val cTimeNames = CommentNotificationWorker.checkIntervals.map { it.toInt() }
            val cItems = cTimeNames.map {
                val mins = it % 60
                val hours = it / 60
                if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
                else getString(R.string.do_not_update)
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.notifications
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.notification_page,
                        descRes = R.string.notification_page_desc,
                        icon = R.drawable.ic_round_sort_24,
                        onClick = {
                            val dialog = AlertDialog.Builder(settings, R.style.MyDialog)
                                .setTitle(R.string.notification_page).apply {
                                    setSingleChoiceItems(
                                        resources.getStringArray(R.array.notification_type),
                                        PrefManager.getVal(PrefName.NotificationPage),
                                        DialogInterface.OnClickListener { dialog, which ->
                                            PrefManager.setVal(PrefName.NotificationPage, which)
                                            dialog.dismiss()
                                        }
                                    )
                                }.show()
                            dialog.window?.setDimAmount(0.8f)
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.anilist_notification_filters,
                        descRes = R.string.anilist_notification_filters_desc,
                        icon = R.drawable.ic_anilist,
                        onClick = {
                            val types = NotificationType.entries.map { it.name }
                            val filteredTypes =
                                PrefManager.getVal<Set<String>>(PrefName.AnilistFilteredTypes)
                                    .toMutableSet()
                            val selected = types.map { filteredTypes.contains(it) }.toBooleanArray()
                            val dialog = AlertDialog.Builder(settings, R.style.MyDialog)
                                .setTitle(R.string.anilist_notification_filters)
                                .setMultiChoiceItems(
                                    types.toTypedArray(),
                                    selected
                                ) { _, which, isChecked ->
                                    val type = types[which]
                                    if (isChecked) {
                                        filteredTypes.add(type)
                                    } else {
                                        filteredTypes.remove(type)
                                    }
                                    PrefManager.setVal(PrefName.AnilistFilteredTypes, filteredTypes)
                                }.show()
                            dialog.window?.setDimAmount(0.8f)
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.notification_for_checking_subscriptions,
                        descRes = R.string.notification_for_checking_subscriptions_desc,
                        icon = R.drawable.round_smart_button_24,
                        isChecked = PrefManager.getVal(PrefName.SubscriptionCheckingNotifications),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(
                                PrefName.SubscriptionCheckingNotifications,
                                isChecked
                            )
                        },
                        onLongClick = {
                            openSettings(settings, null)
                        }
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.frequency
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        name = getString(
                            R.string.subscriptions_checking_time_s,
                            timeNames[curTime]
                        ),
                        descRes = R.string.subscriptions_info,
                        icon = R.drawable.round_notifications_none_24,
                        onClick = {
                            val speedDialog = AlertDialog.Builder(settings, R.style.MyDialog)
                                .setTitle(R.string.subscriptions_checking_time)
                            val dialog =
                                speedDialog.setSingleChoiceItems(timeNames, curTime) { dialog, i ->
                                    curTime = i
                                    it.settingsTitle.text =
                                        getString(
                                            R.string.subscriptions_checking_time_s,
                                            timeNames[i]
                                        )
                                    PrefManager.setVal(
                                        PrefName.SubscriptionNotificationInterval,
                                        curTime
                                    )
                                    dialog.dismiss()
                                    TaskScheduler.create(
                                        settings, PrefManager.getVal(PrefName.UseAlarmManager)
                                    ).scheduleAllTasks(settings)
                                }.show()
                            dialog.window?.setDimAmount(0.8f)
                        },
                        onLongClick = {
                            TaskScheduler.create(
                                settings, PrefManager.getVal(PrefName.UseAlarmManager)
                            ).scheduleAllTasks(settings)
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        name = getString(
                            R.string.anilist_notifications_checking_time,
                            aItems[PrefManager.getVal(PrefName.AnilistNotificationInterval)]
                        ),
                        descRes = R.string.anilist_notifications_checking_time_desc,
                        icon = R.drawable.round_notifications_none_24,
                        onClick = {
                            val selected =
                                PrefManager.getVal<Int>(PrefName.AnilistNotificationInterval)
                            val dialog = AlertDialog.Builder(settings, R.style.MyDialog)
                                .setTitle(R.string.subscriptions_checking_time)
                                .setSingleChoiceItems(
                                    aItems.toTypedArray(),
                                    selected
                                ) { dialog, i ->
                                    PrefManager.setVal(PrefName.AnilistNotificationInterval, i)
                                    it.settingsTitle.text =
                                        getString(
                                            R.string.anilist_notifications_checking_time,
                                            aItems[i]
                                        )
                                    dialog.dismiss()
                                    TaskScheduler.create(
                                        settings, PrefManager.getVal(PrefName.UseAlarmManager)
                                    ).scheduleAllTasks(settings)
                                }.create()
                            dialog.window?.setDimAmount(0.8f)
                            dialog.show()
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        name = getString(
                            R.string.comment_notification_checking_time,
                            cItems[PrefManager.getVal(PrefName.CommentNotificationInterval)]
                        ),
                        descRes = R.string.comment_notification_checking_time_desc,
                        icon = R.drawable.round_notifications_none_24,
                        onClick = {
                            val selected =
                                PrefManager.getVal<Int>(PrefName.CommentNotificationInterval)
                            val dialog = AlertDialog.Builder(settings, R.style.MyDialog)
                                .setTitle(R.string.subscriptions_checking_time)
                                .setSingleChoiceItems(
                                    cItems.toTypedArray(),
                                    selected
                                ) { dialog, i ->
                                    PrefManager.setVal(PrefName.CommentNotificationInterval, i)
                                    it.settingsTitle.text =
                                        getString(
                                            R.string.comment_notification_checking_time,
                                            cItems[i]
                                        )
                                    dialog.dismiss()
                                    TaskScheduler.create(
                                        settings, PrefManager.getVal(PrefName.UseAlarmManager)
                                    ).scheduleAllTasks(settings)
                                }.create()
                            dialog.window?.setDimAmount(0.8f)
                            dialog.show()
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.use_alarm_manager_reliable,
                        descRes = R.string.use_alarm_manager_reliable_desc,
                        icon = R.drawable.ic_anilist,
                        isChecked = PrefManager.getVal(PrefName.UseAlarmManager),
                        switch = { isChecked, view ->
                            if (isChecked) {
                                val alertDialog = AlertDialog.Builder(settings, R.style.MyDialog)
                                    .setTitle(R.string.use_alarm_manager)
                                    .setMessage(R.string.use_alarm_manager_confirm)
                                    .setPositiveButton(R.string.use) { dialog, _ ->
                                        PrefManager.setVal(PrefName.UseAlarmManager, true)
                                        if (Version.isSnowCone) {
                                            if (!(settings.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) {
                                                val intent =
                                                    Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM")
                                                startActivity(intent)
                                                view.settingsButton.isChecked = true
                                            }
                                        }
                                        dialog.dismiss()
                                    }.setNegativeButton(R.string.cancel) { dialog, _ ->
                                        view.settingsButton.isChecked = false
                                        PrefManager.setVal(PrefName.UseAlarmManager, false)

                                        dialog.dismiss()
                                    }.create()
                                alertDialog.window?.setDimAmount(0.8f)
                                alertDialog.show()
                            } else {
                                PrefManager.setVal(PrefName.UseAlarmManager, false)
                                TaskScheduler.create(settings, true).cancelAllTasks()
                                TaskScheduler.create(settings, false)
                                    .scheduleAllTasks(settings)
                            }
                        }
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

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
        }
    }
}