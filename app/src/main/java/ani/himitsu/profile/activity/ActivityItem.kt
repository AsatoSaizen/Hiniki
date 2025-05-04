package ani.himitsu.profile.activity

import android.content.Intent
import android.view.View
import android.view.View.OnClickListener
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.blurImage
import ani.himitsu.buildMarkwon
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.Activity
import ani.himitsu.databinding.ItemActivityBinding
import ani.himitsu.loadImage
import ani.himitsu.openLinkInYouTube
import ani.himitsu.profile.User
import ani.himitsu.profile.UsersDialogFragment
import ani.himitsu.setAnimation
import ani.himitsu.toast
import ani.himitsu.util.AniMarkdown.getBasicAniHTML
import ani.himitsu.util.MarkdownCreatorActivity
import bit.himitsu.nio.string
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class ActivityItem(
    private val activity: Activity,
    val clickCallback: (Int, type: String) -> Unit,
    private val fragActivity: FragmentActivity
) : BindableItem<ItemActivityBinding>() {
    private lateinit var binding: ItemActivityBinding
    private lateinit var repliesAdapter: GroupieAdapter

    override fun bind(viewBinding: ItemActivityBinding, position: Int) {
        binding = viewBinding
        setAnimation(binding.root.context, binding.root)

        repliesAdapter = GroupieAdapter()
        binding.activityReplies.adapter = repliesAdapter
        binding.activityReplies.layoutManager = LinearLayoutManager(
            binding.root.context,
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.activityUserName.text = activity.user?.name ?: activity.messenger?.name
        binding.activityUserAvatar.loadImage(
            activity.user?.avatar?.medium ?: activity.messenger?.avatar?.medium
        )
        binding.activityTime.text = ActivityItemBuilder.getDateTime(activity.createdAt)
        val likeColor = ContextCompat.getColor(binding.root.context, R.color.youtube_red)
        val notLikeColor = ContextCompat.getColor(binding.root.context, R.color.bg_opp)
        binding.activityLike.setColorFilter(if (activity.isLiked == true) likeColor else notLikeColor)
        binding.commentTotalReplies.isVisible = activity.replyCount > 0
        binding.dot.isVisible = activity.replyCount > 0
        if (!activity.replies.isNullOrEmpty()) {
            val replies = activity.replies.map {
                ActivityReplyItem(it, fragActivity) { id, type ->
                    clickCallback(
                        id,
                        type
                    )
                }
            }
            val onReplyClickListener = OnClickListener {
                when (binding.activityReplies.visibility) {
                    View.GONE -> {
                        repliesAdapter.addAll(replies)
                        binding.activityReplies.visibility = View.VISIBLE
                        binding.commentTotalReplies.setText(R.string.hide_replies)
                    }

                    else -> {
                        repliesAdapter.clear()
                        binding.activityReplies.visibility = View.GONE
                        binding.commentTotalReplies.setText(R.string.view_replies)
                    }
                }
            }
            repliesAdapter.add(ActivityReplyItem(activity.replies.first(), fragActivity) { id, type ->
                clickCallback(
                    id,
                    type
                )
            })
            binding.activityReplies.visibility = View.VISIBLE
            if (activity.replies.size > 1) {
                binding.commentTotalReplies.setOnClickListener {
                    repliesAdapter.clear()
                    repliesAdapter.addAll(replies)
                    binding.commentTotalReplies.setText(R.string.hide_replies)
                    binding.commentTotalReplies.setOnClickListener(onReplyClickListener)
                }
            } else {
                binding.commentTotalReplies.setText(R.string.hide_replies)
                binding.commentTotalReplies.setOnClickListener(onReplyClickListener)
            }
        }
        if (activity.isLocked != true) {
            binding.commentReply.setOnClickListener {
                val context = binding.root.context
                context.startActivity(
                    Intent(context, MarkdownCreatorActivity::class.java)
                        .putExtra("type", MarkdownCreatorActivity.REPLY_ACTIVITY)
                        .putExtra("parentId", activity.id)
                )
            }
        } else {
            binding.commentReply.visibility = View.GONE
            binding.dot.visibility = View.GONE
        }
        val userList = arrayListOf<User>()
        activity.likes?.forEach { i ->
            userList.add(User(i.id, i.name.toString(), i.avatar?.medium, i.bannerImage))
        }
        binding.activityLikeContainer.setOnLongClickListener {
            UsersDialogFragment().apply {
                userList(userList)
                show(fragActivity.supportFragmentManager, "dialog")
            }
            true
        }
        binding.activityLikeCount.text = (activity.likeCount ?: 0).string
        binding.activityLikeContainer.setOnClickListener {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val res = AniList.mutation.toggleLike(activity.id, "ACTIVITY")
                withUIContext {
                    if (res != null) {
                        if (activity.isLiked == true) {
                            activity.likeCount = activity.likeCount?.minus(1)
                        } else {
                            activity.likeCount = activity.likeCount?.plus(1)
                        }
                        binding.activityLikeCount.text = (activity.likeCount ?: 0).string
                        activity.isLiked = !activity.isLiked!!
                        binding.activityLike.setColorFilter(if (activity.isLiked == true) likeColor else notLikeColor)

                    } else {
                        toast(R.string.like_failed)
                    }
                }
            }
        }
        when {
            activity.text?.contains("class='youtube'") == true -> {
                activity.text.substringAfter("class='youtube'").substringBefore(">")
            }
            activity.text?.contains("class=\"youtube\"") == true -> {
                activity.text.substringAfter("class=\"youtube\"").substringBefore(">")
            }
            else -> null
        }?.let { url ->
            binding.videoButtonYT.visibility = View.VISIBLE
            binding.videoButtonYT.setOnClickListener {
                openLinkInYouTube(url.substringAfter("id=").substring(1, url.length - 5))
            }
        }
        val context = binding.root.context
        when (activity.typename) {
            "ListActivity" -> {
                val cover = activity.media?.coverImage?.large
                val banner = activity.media?.bannerImage
                binding.activityContent.visibility = View.GONE
                binding.activityBannerContainer.visibility = View.VISIBLE
                binding.activityMediaName.text = activity.media?.title?.userPreferred
                val activityText = "${activity.user!!.name} ${activity.status} ${
                    activity.progress
                        ?: activity.media?.title?.userPreferred
                }"
                binding.activityText.text = activityText
                binding.activityCover.loadImage(cover)
                binding.activityBannerImage.blurImage(banner ?: cover)
                binding.activityAvatarContainer.setOnClickListener {
                    clickCallback(activity.userId ?: -1, "USER")
                }
                binding.activityUserName.setOnClickListener {
                    clickCallback(activity.userId ?: -1, "USER")
                }
                binding.activityCoverContainer.setOnClickListener {
                    clickCallback(activity.media?.id ?: -1, "MEDIA")
                }
                binding.activityMediaName.setOnClickListener {
                    clickCallback(activity.media?.id ?: -1, "MEDIA")
                }
            }

            "TextActivity" -> {
                binding.activityBannerContainer.visibility = View.GONE
                binding.activityContent.visibility = View.VISIBLE
                if (!(context as android.app.Activity).isDestroyed) {
                    buildMarkwon(context, false).setMarkdown(
                        binding.activityContent,
                        getBasicAniHTML(activity.text ?: "")
                    )
                }
                binding.activityAvatarContainer.setOnClickListener {
                    clickCallback(activity.userId ?: -1, "USER")
                }
                binding.activityUserName.setOnClickListener {
                    clickCallback(activity.userId ?: -1, "USER")
                }
            }

            "MessageActivity" -> {
                binding.activityBannerContainer.visibility = View.GONE
                binding.activityContent.visibility = View.VISIBLE
                if (!(context as android.app.Activity).isDestroyed) {
                    buildMarkwon(context, false).setMarkdown(
                        binding.activityContent,
                        getBasicAniHTML(activity.message ?: "")
                    )
                }
                binding.activityAvatarContainer.setOnClickListener {
                    clickCallback(activity.messengerId ?: -1, "USER")
                }
                binding.activityUserName.setOnClickListener {
                    clickCallback(activity.messengerId ?: -1, "USER")
                }
            }
        }
        binding.activityDelete.setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(
                fragActivity,
                R.style.MyDialog
            )
            builder.setTitle(R.string.activity_delete)
            builder.setPositiveButton(R.string.yes) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    AniList.mutation.deleteActivity(activity.id)
                    withUIContext {
                        fragActivity.recreate()
                    }
                }
            }
            builder.setNegativeButton(R.string.no) { _, _ ->
                // Do nothing
            }
            val dialog = builder.show()
            dialog.window?.setDimAmount(0.8f)
        }
        binding.activityDelete.isVisible = activity.userId == AniList.userid
    }

    override fun getLayout(): Int {
        return R.layout.item_activity
    }

    override fun initializeViewBinding(view: View): ItemActivityBinding {
        return ItemActivityBinding.bind(view)
    }

    override fun unbind(viewHolder: GroupieViewHolder<ItemActivityBinding>) {
        binding.videoButtonYT.visibility = View.GONE
        super.unbind(viewHolder)
    }
}