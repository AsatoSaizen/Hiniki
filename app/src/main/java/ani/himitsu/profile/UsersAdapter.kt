package ani.himitsu.profile

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.databinding.ItemFollowerBinding
import ani.himitsu.loadImage
import ani.himitsu.setAnimation


class UsersAdapter(private val user: ArrayList<User>) :
    RecyclerView.Adapter<UsersAdapter.UsersViewHolder>() {

    inner class UsersViewHolder(val binding: ItemFollowerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                binding.root.context.startActivity(
                    Intent(binding.root.context, ProfileActivity::class.java)
                        .putExtra("userId", user[bindingAdapterPosition].id)
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsersViewHolder {
        return UsersViewHolder(
            ItemFollowerBinding.inflate(
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
        b.profileBannerImage.loadImage(user.banner ?: user.pfp)
        b.profileUserName.text = user.name
    }

    override fun getItemCount(): Int = user.size
}
