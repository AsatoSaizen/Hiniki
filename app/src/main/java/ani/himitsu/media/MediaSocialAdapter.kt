package ani.himitsu.media

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.ItemFollowerGridBinding
import ani.himitsu.loadImage
import ani.himitsu.profile.ProfileActivity
import ani.himitsu.profile.User
import ani.himitsu.setAnimation
import ani.himitsu.view.dialog.ImageViewDialog
import bit.himitsu.nio.italic
import bit.himitsu.nio.string

class MediaSocialAdapter(
    val user: ArrayList<User>,
    val type: String,
    val activity: FragmentActivity
) : RecyclerView.Adapter<MediaSocialAdapter.FollowerGridViewHolder>() {

    inner class FollowerGridViewHolder(val binding: ItemFollowerGridBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowerGridViewHolder {
        return FollowerGridViewHolder(
            ItemFollowerGridBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }


    override fun onBindViewHolder(holder: FollowerGridViewHolder, position: Int) {
        holder.binding.apply {
            val user = user[position]
            val score = user.score?.div(10.0) ?: 0.0
            setAnimation(root.context, root)
            profileUserName.text = user.name
            profileInfo.apply {
                text = when (user.status) {
                    "CURRENT" -> if (type == "ANIME")
                        this.context.getString(R.string.watching)
                    else this.context.getString(R.string.reading)
                    else -> user.status ?: ""
                }.italic
                visibility = View.VISIBLE
            }
            profileCompactUserProgress.text = user.progress.toString()
            profileCompactScore.text = score.string
            " | ${user.totalEpisodes ?: "~"}".also { profileCompactTotal.text = it }
            profileUserAvatar.loadImage(user.pfp)

            val scoreDrawable = if (score == 0.0) R.drawable.score else R.drawable.user_score
            profileCompactScoreBG.apply {
                visibility = View.VISIBLE
                background = ContextCompat.getDrawable(root.context, scoreDrawable)
            }

            profileCompactProgressContainer.visibility = View.VISIBLE

            profileUserAvatar.setOnClickListener {
                root.context.startActivity(
                    Intent(root.context, ProfileActivity::class.java)
                        .putExtra("userId", user.id)
                )
            }
            profileUserAvatarContainer.setOnLongClickListener {
                ImageViewDialog.newInstance(
                    activity,
                    activity.getString(R.string.avatar, user.name),
                    user.pfp
                )
            }
        }
    }

    override fun getItemCount(): Int = user.size
}