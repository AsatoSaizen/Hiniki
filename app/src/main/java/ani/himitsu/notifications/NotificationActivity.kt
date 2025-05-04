package ani.himitsu.notifications

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.notifications.comment.CommentStore
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.Notification
import ani.himitsu.connections.anilist.api.NotificationType
import ani.himitsu.connections.anilist.api.NotificationType.Companion.fromFormattedString
import ani.himitsu.connections.anilist.api.User
import ani.himitsu.connections.anilist.api.UserAvatar
import ani.himitsu.currContext
import ani.himitsu.databinding.ActivityFollowBinding
import ani.himitsu.databinding.CustomDialogLayoutBinding
import ani.himitsu.initActivity
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.notifications.subscription.SubscriptionStore
import ani.himitsu.profile.ProfileActivity
import ani.himitsu.profile.activity.FeedActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.util.Logger
import bit.himitsu.withFlexibleMargin
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class NotificationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFollowBinding
    private lateinit var commentStore: List<CommentStore>
    private lateinit var subscriptionStore: List<SubscriptionStore>
    private var adapter: GroupieAdapter = GroupieAdapter()
    private var notificationList: List<Notification> = emptyList()
    private val filters = arrayListOf<String>()
    private var currentPage: Int = 1
    private var hasNextPage: Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        filters.addAll(PrefManager.getVal<Set<String>>(PrefName.NotificationFilters))
        binding = ActivityFollowBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.listTitle.text = getString(R.string.notifications)
        binding.listToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        adapter.setHasStableIds(true)
        binding.listRecyclerView.adapter = adapter
        binding.listRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.followerGrid.visibility = ViewGroup.GONE
        binding.followerList.visibility = ViewGroup.GONE
        binding.listBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.listProgressBar.visibility = ViewGroup.VISIBLE
        binding.followFilterView.visibility = View.VISIBLE
        binding.activityFeedView.visibility = View.VISIBLE
        commentStore = PrefManager.getNullableVal<List<CommentStore>>(
            PrefName.CommentNotificationStore, null
        ) ?: listOf()
        subscriptionStore = PrefManager.getNullableVal<List<SubscriptionStore>>(
            PrefName.SubscriptionNotificationStore, null
        ) ?: listOf()

        binding.followFilterButton.setOnClickListener {
            val dialogView = CustomDialogLayoutBinding.inflate(layoutInflater)
            dialogView.dialogHeading.text = getString(R.string.filter)
            fun getToggleImageResource(container: ViewGroup): Int {
                var allChecked = true
                var allUnchecked = true

                for (i in 0 until container.childCount) {
                    val checkBox = container.getChildAt(i) as CheckBox
                    if (!checkBox.isChecked) {
                        allChecked = false
                    } else {
                        allUnchecked = false
                    }
                }
                return when {
                    allChecked -> R.drawable.untick_all_boxes
                    allUnchecked -> R.drawable.tick_all_boxes
                    else -> R.drawable.invert_all_boxes
                }
            }
            NotificationType.entries.forEach { notificationType ->
                val checkBox = CheckBox(currContext())
                checkBox.text = notificationType.toFormattedString()
                checkBox.isChecked = !filters.contains(notificationType.value.fromFormattedString())
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        filters.remove(notificationType.value.fromFormattedString())
                    } else {
                        filters.add(notificationType.value.fromFormattedString())
                    }
                    dialogView.toggleButton.setImageResource(getToggleImageResource(dialogView.checkboxContainer))
                }
                dialogView.checkboxContainer.addView(checkBox)
            }
            dialogView.toggleButton.setImageResource(getToggleImageResource(dialogView.checkboxContainer))
            dialogView.toggleButton.setOnClickListener {
                dialogView.checkboxContainer.children.forEach {
                    val checkBox = it as CheckBox
                    checkBox.isChecked = !checkBox.isChecked
                }
                dialogView.toggleButton.setImageResource(getToggleImageResource(dialogView.checkboxContainer))
            }
            val alertD = AlertDialog.Builder(this, R.style.MyDialog)
            alertD.setView(dialogView.root)
            alertD.setPositiveButton(R.string.ok) { _, _ ->
                PrefManager.setVal(PrefName.NotificationFilters, filters.toSet())
                filterByType(binding.notificationNavBar.selectedTab?.id)
            }
            alertD.setNegativeButton(R.string.cancel) { _, _ -> }
            val dialog = alertD.show()
            dialog.window?.setDimAmount(0.8f)
        }

        binding.activityFeedButton.setOnClickListener {
            startActivity(Intent(this, FeedActivity::class.java))
        }

        val activityId = intent.getIntExtra("activityId", -1)
        lifecycleScope.launch {
            loadPage(activityId) {
                binding.listProgressBar.visibility = ViewGroup.GONE
            }
            withUIContext {
                binding.listProgressBar.visibility = ViewGroup.GONE
                binding.listRecyclerView.setOnTouchListener { _, event ->
                    if (event?.action == MotionEvent.ACTION_UP) {
                        if (hasNextPage
                            && !binding.listRecyclerView.canScrollVertically(1)
                            && !binding.followRefresh.isVisible
                            && adapter.itemCount != 0
                            && (binding.listRecyclerView.layoutManager as LinearLayoutManager)
                                .findLastVisibleItemPosition() == (adapter.itemCount - 1)
                        ) {
                            binding.followRefresh.visibility = ViewGroup.VISIBLE
                            loadPage(-1) {
                                binding.followRefresh.visibility = ViewGroup.GONE
                            }
                        }
                    }
                    false
                }

                binding.followSwipeRefresh.setOnRefreshListener {
                    currentPage = 1
                    hasNextPage = true
                    adapter.clear()
                    notificationList = emptyList()
                    loadPage(-1) {
                        binding.followSwipeRefresh.isRefreshing = false
                    }
                }
            }
        }

        binding.notificationNavBar.visibility = View.VISIBLE
        binding.notificationNavBar.withFlexibleMargin(resources.configuration)
        binding.notificationNavBar.selectTabAt(PrefManager.getVal(PrefName.NotificationPage))
        binding.notificationNavBar.onTabSelected = { filterByType(it.id) }
    }

    private val media = listOf(
        NotificationType.AIRING.value,
        NotificationType.RELATED_MEDIA_ADDITION.value,
        NotificationType.MEDIA_DATA_CHANGE.value,
        NotificationType.MEDIA_MERGE.value,
        NotificationType.MEDIA_DELETION.value,
        NotificationType.SUBSCRIPTION.value
    )
    private val posts = listOf(
        NotificationType.ACTIVITY_MESSAGE.value,
        NotificationType.ACTIVITY_REPLY.value,
        NotificationType.ACTIVITY_MENTION.value,
        NotificationType.ACTIVITY_REPLY_SUBSCRIBED.value,
        NotificationType.COMMENT_REPLY.value,
        NotificationType.THREAD_COMMENT_MENTION.value,
        NotificationType.THREAD_SUBSCRIBED.value,
        NotificationType.THREAD_COMMENT_REPLY.value
    )
    private val user = listOf(
        NotificationType.FOLLOWING.value,
        NotificationType.ACTIVITY_LIKE.value,
        NotificationType.ACTIVITY_REPLY_LIKE.value,
        NotificationType.THREAD_LIKE.value,
        NotificationType.THREAD_COMMENT_LIKE.value
    )

    private fun getUncategorized(): List<String> {
        val newList = arrayListOf<String>()
        NotificationType.entries.filterNot { filters.contains(it.value) }.forEach {
            newList.add(it.value)
        }
        return newList
    }

    private fun enableTabByContent(tabId: Int, items: List<String>) {
        val hasContent = notificationList.any { items.contains(it.notificationType) }
        binding.notificationNavBar.tabs.find { it.id == tabId }?.enabled = hasContent
    }

    private fun filterByType(id: Int?) {
        val newNotifications = when (id) {
            R.id.notificationsMedia -> media
            R.id.notificationsPosts -> posts
            R.id.notificationsUser -> user
            else -> null
        }.let { list ->
            val filter = list?.minus(filters.toSet()) ?: getUncategorized()
            notificationList.filter { notification ->
                filter.contains(notification.notificationType)
            }
        }

        adapter.clear()
        adapter.addAll(newNotifications.map {
            NotificationItem(
                it,
                ::onNotificationClick,
                ::onNotificationLongClick
            )
        })
    }

    private fun loadPage(activityId: Int, onFinish: () -> Unit = {}) {
        lifecycleScope.launch(Dispatchers.IO) {
            val resetNotification = activityId == -1
            val res = AniList.query.getNotifications(
                AniList.userid ?: PrefManager.getVal<String>(PrefName.AnilistUserId).toIntOrNull() ?: 0,
                currentPage,
                resetNotification = resetNotification
            )
            val newNotifications: MutableList<Notification> = mutableListOf()
            res?.data?.page?.notifications?.let { notifications ->
                Logger.log("Notifications: $notifications")
                newNotifications += if (activityId != -1) {
                    notifications.filter { it.id == activityId }
                } else {
                    notifications
                }.toMutableList()
            }
            if (activityId == -1) {
                val furthestTime = newNotifications.minOfOrNull { it.createdAt } ?: 0
                commentStore.forEach {
                    if ((it.time > furthestTime * 1000L || !hasNextPage) && notificationList.none { notification ->
                            notification.commentId == it.commentId && notification.createdAt == (it.time / 1000L).toInt()
                        }) {
                        val notification = Notification(
                            it.type.toString(),
                            System.currentTimeMillis().toInt(),
                            commentId = it.commentId,
                            notificationType = it.type.toString(),
                            mediaId = it.mediaId,
                            context = it.title + "\n" + it.content,
                            createdAt = (it.time / 1000L).toInt(),
                        )
                        if (!notificationList.contains(notification))
                            newNotifications += notification
                    }
                }
                subscriptionStore.forEach {
                    if ((it.time > furthestTime * 1000L || !hasNextPage)) {
                        val notification = Notification(
                            it.type,
                            System.currentTimeMillis().toInt(),
                            commentId = it.mediaId,
                            mediaId = it.mediaId,
                            notificationType = it.type,
                            context = it.title + "\n" + it.content,
                            createdAt = (it.time / 1000L).toInt(),
                            user = User(
                                it.mediaId,
                                it.title,
                                UserAvatar(it.cover, it.cover),
                                it.banner,
                                null, null, null, null, null
                            ),
                        )
                        if (!notificationList.contains(notification))
                            newNotifications += notification
                    }
                }
                newNotifications.sortByDescending { it.createdAt }
            }

            withUIContext {
                notificationList += newNotifications
                enableTabByContent(R.id.notificationsMedia, media)
                enableTabByContent(R.id.notificationsPosts, posts)
                enableTabByContent(R.id.notificationsUser, user)
                currentPage = res?.data?.page?.pageInfo?.currentPage?.plus(1) ?: 1
                hasNextPage = res?.data?.page?.pageInfo?.hasNextPage == true
                filterByType(binding.notificationNavBar.selectedTab?.id)
                binding.followSwipeRefresh.isRefreshing = false
                onFinish()
            }
        }
    }

    private fun onNotificationLongClick(notification: Notification) {
        when (notification.notificationType) {
            NotificationType.COMMENT_REPLY.value -> {
                PrefManager.setVal(
                    PrefName.CommentNotificationStore,
                    PrefManager.getNullableVal<List<CommentStore>>(
                        PrefName.CommentNotificationStore,
                        null
                    )?.minus(notification)
                )
            }
            NotificationType.SUBSCRIPTION.value -> {
                PrefManager.setVal(
                    PrefName.SubscriptionNotificationStore,
                    PrefManager.getNullableVal<List<SubscriptionStore>>(
                        PrefName.SubscriptionNotificationStore,
                        null
                    )?.minus(notification)
                )
            }
            else -> {}
        }
        notificationList -= notification
        filterByType(binding.notificationNavBar.selectedTab?.id)
    }

    private fun onNotificationClick(id: Int, optional: Int?, type: NotificationClickType) {
        when (type) {
            NotificationClickType.USER -> {
                startActivity(
                    Intent(this, ProfileActivity::class.java)
                        .putExtra("userId", id)
                )
            }

            NotificationClickType.MEDIA -> {
                startActivity(
                    Intent(this, MediaDetailsActivity::class.java)
                        .putExtra("mediaId", id)
                )
            }

            NotificationClickType.ACTIVITY -> {
                startActivity(
                    Intent(this, FeedActivity::class.java)
                        .putExtra("activityId", id)
                )
            }

            NotificationClickType.COMMENT -> {
                startActivity(
                   Intent(this, MediaDetailsActivity::class.java)
                        .putExtra("FRAGMENT_TO_LOAD", "COMMENTS")
                        .putExtra("mediaId", id)
                        .putExtra("commentId", optional ?: -1)
                )

            }

            NotificationClickType.UNDEFINED -> {
                // Do nothing
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.notificationNavBar.withFlexibleMargin(newConfig)
    }

    override fun onResume() {
        super.onResume()
        commentStore = PrefManager.getNullableVal<List<CommentStore>>(
            PrefName.CommentNotificationStore, null
        ) ?: listOf()
        subscriptionStore = PrefManager.getNullableVal<List<SubscriptionStore>>(
            PrefName.SubscriptionNotificationStore, null
        ) ?: listOf()
    }

    companion object {
        enum class NotificationClickType {
            USER, MEDIA, ACTIVITY, COMMENT, UNDEFINED
        }
    }
}