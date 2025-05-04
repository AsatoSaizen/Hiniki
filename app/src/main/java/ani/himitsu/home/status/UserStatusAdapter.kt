package ani.himitsu.home.status

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.databinding.ItemUserStatusBinding
import ani.himitsu.loadImage
import ani.himitsu.profile.ProfileActivity
import ani.himitsu.profile.User
import ani.himitsu.setAnimation
import ani.himitsu.settings.saving.PrefManager
import bit.himitsu.nio.Strings.getString

class UserStatusAdapter(private val user: ArrayList<User>) :
    RecyclerView.Adapter<UserStatusAdapter.UsersViewHolder>() {

    inner class UsersViewHolder(val binding: ItemUserStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                StatusActivity.user = user
                itemView.context.startActivity(
                    Intent(
                        itemView.context,
                        StatusActivity::class.java
                    ).putExtra("position", bindingAdapterPosition)
                )
            }
            itemView.setOnLongClickListener {
                itemView.context.startActivity(
                    Intent(
                        itemView.context,
                        ProfileActivity::class.java
                    ).putExtra("userId", user[bindingAdapterPosition].id)
                )
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsersViewHolder {
        return UsersViewHolder(
            ItemUserStatusBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: UsersViewHolder, position: Int) {
        val b = holder.binding
        setAnimation(b.root.context, b.root)
        val user = user[position]
        b.profileUserAvatar.loadImage(user.pfp)
        b.profileUserName.text = if (AniList.userid == user.id) getString(R.string.your_story) else user.name

        val watchedActivity = PrefManager.getCustomVal<Set<Int>>("activities", setOf())
        val booleanList = user.activity.map { watchedActivity.contains(it.id) }
        b.profileUserStatusIndicator.setParts(user.activity.size, booleanList, user.id == AniList.userid)
    }

    override fun getItemCount(): Int = user.size
}
