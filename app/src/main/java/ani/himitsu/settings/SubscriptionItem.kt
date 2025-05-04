package ani.himitsu.settings

import android.content.Intent
import android.view.View
import ani.himitsu.R
import ani.himitsu.databinding.ItemSubscriptionBinding
import ani.himitsu.loadImage
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.notifications.subscription.SubscriptionHelper
import ani.himitsu.util.customAlertDialog
import bit.himitsu.nio.italic
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.viewbinding.BindableItem

class SubscriptionItem(
    val id: Int,
    private val media: SubscriptionHelper.SubscribeMedia,
    private val adapter: GroupieAdapter
) : BindableItem<ItemSubscriptionBinding>() {
    private lateinit var binding: ItemSubscriptionBinding
    override fun bind(p0: ItemSubscriptionBinding, p1: Int) {
        val context = p0.root.context
        binding = p0
        val parserName = if (media.isAnime)
            SubscriptionHelper.getAnimeParser(media.id).name
        else
            SubscriptionHelper.getMangaParser(media.id).name
        binding.subscriptionName.text = media.name
        binding.subscriptionSource.text = parserName.italic
        binding.subscriptionInfo.setOnClickListener {
            it.context.startActivity(
                Intent(context, MediaDetailsActivity::class.java).putExtra(
                    "mediaId", media.id
                )
            )
        }
        binding.subscriptionCover.loadImage(media.cover)
        binding.subscriptionCover.setOnClickListener {
            it.context.startActivity(
                Intent(context, MediaDetailsActivity::class.java).putExtra(
                    "mediaId", media.id
                )
            )
        }
        binding.deleteSubscription.setOnClickListener {
            it.context.customAlertDialog().apply {
                setMessage(R.string.remove_subscription, media.name)
                setPositiveButton(R.string.yes) {
                    SubscriptionHelper.deleteSubscription(id, true)
                    adapter.remove(this@SubscriptionItem)
                }
                setNegativeButton(R.string.no)
                show()
            }
        }
    }

    override fun getLayout(): Int {
        return R.layout.item_subscription
    }

    override fun initializeViewBinding(p0: View): ItemSubscriptionBinding {
        return ItemSubscriptionBinding.bind(p0)
    }
}
