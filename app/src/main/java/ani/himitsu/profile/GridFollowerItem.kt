package ani.himitsu.profile

import android.view.View
import ani.himitsu.R
import ani.himitsu.databinding.ItemFollowerGridBinding
import ani.himitsu.loadImage
import com.xwray.groupie.viewbinding.BindableItem

class GridFollowerItem(
    private val id: Int,
    private val name: String,
    private val avatar: String?,
    val clickCallback: (Int) -> Unit
) : BindableItem<ItemFollowerGridBinding>() {
    private lateinit var binding: ItemFollowerGridBinding

    override fun bind(viewBinding: ItemFollowerGridBinding, position: Int) {
        binding = viewBinding
        binding.profileUserName.text = name
        avatar?.let { binding.profileUserAvatar.loadImage(it) }
        binding.root.setOnClickListener { clickCallback(id) }
    }

    override fun getLayout(): Int {
        return R.layout.item_follower_grid
    }

    override fun initializeViewBinding(view: View): ItemFollowerGridBinding {
        return ItemFollowerGridBinding.bind(view)
    }
}