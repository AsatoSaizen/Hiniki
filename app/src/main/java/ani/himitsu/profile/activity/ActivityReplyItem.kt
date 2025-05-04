package ani.himitsu.profile.activity

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ani.himitsu.R
import ani.himitsu.buildMarkwon
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.ActivityReply
import ani.himitsu.databinding.ItemActivityReplyBinding
import ani.himitsu.loadImage
import ani.himitsu.profile.User
import ani.himitsu.profile.UsersDialogFragment
import ani.himitsu.toast
import ani.himitsu.util.AniMarkdown.getBasicAniHTML
import bit.himitsu.nio.string
import com.xwray.groupie.viewbinding.BindableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class ActivityReplyItem(
    private val reply: ActivityReply,
    private val fragActivity: FragmentActivity,
    private val clickCallback: (Int, type: String) -> Unit,
) : BindableItem<ItemActivityReplyBinding>() {
    private lateinit var binding: ItemActivityReplyBinding

    override fun bind(viewBinding: ItemActivityReplyBinding, position: Int) {
        binding = viewBinding

        binding.activityUserAvatar.loadImage(reply.user.avatar?.medium)
        binding.activityUserName.text = reply.user.name
        binding.activityTime.text = ActivityItemBuilder.getDateTime(reply.createdAt)
        binding.activityLikeCount.text = reply.likeCount.string
        val likeColor = ContextCompat.getColor(binding.root.context, R.color.youtube_red)
        val notLikeColor = ContextCompat.getColor(binding.root.context, R.color.bg_opp)
        binding.activityLike.setColorFilter(if (reply.isLiked) likeColor else notLikeColor)
        val markwon = buildMarkwon(binding.root.context)
        markwon.setMarkdown(binding.activityContent, getBasicAniHTML(reply.text))
        val userList = arrayListOf<User>()
        reply.likes?.forEach { i ->
            userList.add(User(i.id, i.name.toString(), i.avatar?.medium, i.bannerImage))
        }
        binding.activityLikeContainer.setOnLongClickListener {
            UsersDialogFragment().apply {
                userList(userList)
                show(fragActivity.supportFragmentManager, "dialog")
            }
            true
        }
        binding.activityLikeContainer.setOnClickListener {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val res = AniList.mutation.toggleLike(reply.id, "ACTIVITY_REPLY")
                withUIContext {
                    if (res != null) {
                        if (reply.isLiked) {
                            reply.likeCount = reply.likeCount.minus(1)
                        } else {
                            reply.likeCount = reply.likeCount.plus(1)
                        }
                        binding.activityLikeCount.text = (reply.likeCount).string
                        reply.isLiked = !reply.isLiked
                        binding.activityLike.setColorFilter(if (reply.isLiked) likeColor else notLikeColor)

                    } else {
                        toast(R.string.like_failed)
                    }
                }
            }
        }

        binding.activityAvatarContainer.setOnClickListener {
            clickCallback(reply.userId, "USER")
        }
        binding.activityUserName.setOnClickListener {
            clickCallback(reply.userId, "USER")
        }
    }

    override fun getLayout(): Int {
        return R.layout.item_activity_reply
    }

    override fun initializeViewBinding(view: View): ItemActivityReplyBinding {
        return ItemActivityReplyBinding.bind(view)
    }
}