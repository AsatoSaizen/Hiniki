package ani.himitsu.media

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.himitsu.R
import ani.himitsu.blurImage
import ani.himitsu.connections.Status
import ani.himitsu.databinding.ItemMediaBasicBinding
import ani.himitsu.databinding.ItemMediaCompactBinding
import ani.himitsu.databinding.ItemMediaLargeBinding
import ani.himitsu.databinding.ItemMediaPageBinding
import ani.himitsu.databinding.ItemMediaPageSmallBinding
import ani.himitsu.loadImage
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.cereal.MediaSingleton
import ani.himitsu.setAnimation
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toBitmap
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.italic
import bit.himitsu.nio.string
import bit.himitsu.nio.totalEpisodeText
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import java.io.Serializable

class MediaAdaptor(
    var viewType: ViewType,
    private val mediaList: MutableList<Media>?,
    private val activity: FragmentActivity,
    private val matchParent: Boolean = false,
    private val viewPager: ViewPager2? = null,
    private val fav: Boolean = false,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    constructor(index: Int, mediaList: MutableList<Media>?, activity: FragmentActivity, matchParent: Boolean = false)
            : this(ViewType.entries[index], mediaList, activity, matchParent)

    var extension: String? = null
    var longClickAction: ((Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (this.viewType) {
            ViewType.COMPACT -> MediaViewHolder(
                ItemMediaCompactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            ViewType.LARGE -> MediaLargeViewHolder(
                ItemMediaLargeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            ViewType.BASIC -> MediaBasicViewHolder(
                ItemMediaBasicBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            ViewType.PAGE -> MediaPageViewHolder(
                ItemMediaPageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            ViewType.SMALL_PAGE -> MediaPageSmallViewHolder(
                ItemMediaPageSmallBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val media = mediaList?.getOrNull(position)
        if (media != null) {
            when (viewType) {
                ViewType.COMPACT -> {
                    val b = (holder as MediaViewHolder).binding
                    setAnimation(activity, b.root)
                    b.itemCompactImage.loadImage(media.cover)
                    b.itemCompactOngoing.isVisible =
                        media.status == getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text = ((
                            if (media.userScore == 0)
                                media.meanScore ?: 0
                            else
                                media.userScore
                            ) / 10.0).string
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(b.root.context, (
                            if (media.userScore != 0)
                                R.drawable.item_user_score
                            else
                                R.drawable.item_score
                            )
                    )
                    b.itemCompactUserProgress.text = (media.userProgress ?: "~").toString()
                    media.relation?.let { relation ->
                        b.itemCompactRelation.text = relation.italic
                        b.itemCompactType.visibility = View.VISIBLE
                    } ?: {
                        b.itemCompactType.visibility = View.GONE
                    }
                    if (media.anime != null) {
                        if (media.relation != null) b.itemCompactTypeImage.setImageDrawable(
                            AppCompatResources.getDrawable(
                                activity,
                                R.drawable.round_movie_filter_24
                            )
                        )
                    } else if (media.manga != null) {
                        if (media.relation != null) b.itemCompactTypeImage.setImageDrawable(
                            AppCompatResources.getDrawable(
                                activity,
                                R.drawable.round_import_contacts_24
                            )
                        )
                    }
                    b.itemCompactTypeImage.isVisible = activity !is CalendarActivity
                    b.itemCompactTotal.text = getString(
                        R.string.total_divider,
                        getItemTotal(media)
                    )

                    b.itemCompactProgressContainer.visibility = if (fav) View.GONE else View.VISIBLE
                }

                ViewType.LARGE -> {
                    val b = (holder as MediaLargeViewHolder).binding
                    setAnimation(activity, b.root)
                    b.itemCompactImage.loadImage(media.cover)
                    b.itemCompactBanner.blurImage(media.banner ?: media.cover)
                    b.itemCompactOngoing.isVisible =
                        media.status == getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text = ((
                            if (media.userScore == 0)
                                    media.meanScore ?: 0
                            else
                                media.userScore
                            ) / 10.0).string
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(b.root.context, (
                            if (media.userScore != 0)
                                R.drawable.item_user_score
                            else
                                R.drawable.item_score
                            )
                    )
                    b.itemCompactTotal.text = getItemTotal(media)
                    b.itemTotalLabel.text = getItemTotalLabel(media)
                    if (position == mediaList!!.size - 2 && viewPager != null) viewPager.post {
                        val start = mediaList.size
                        mediaList.addAll(mediaList)
                        val end = mediaList.size - start
                        notifyItemRangeInserted(start, end)
                    }
                }

                ViewType.BASIC -> {
                    val b = (holder as MediaBasicViewHolder).binding
                    val bannerAnimations: Boolean = PrefManager.getVal(PrefName.BannerAnimations)
                    if (bannerAnimations)
                        b.itemCompactBanner.setTransitionGenerator(
                            RandomTransitionGenerator(
                                (10000 + 15000 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong(),
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    val banner = if (bannerAnimations) b.itemCompactBanner else b.itemCompactBannerNoKen
                    banner.blurImage(media.banner ?: media.cover)
                    b.itemCompactOngoing.isVisible = media.status == getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text = ((
                            if (media.userScore == 0)
                                media.meanScore ?: 0
                            else
                                media.userScore
                            ) / 10.0).string
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(b.root.context, (
                            if (media.userScore != 0)
                                R.drawable.item_user_score
                            else
                                R.drawable.item_score
                            )
                    )
                    b.itemCompactTotal.text = getItemTotal(media)
                    b.itemTotalLabel.text = getItemTotalLabel(media)
                    if (position == mediaList!!.size - 2 && viewPager != null) viewPager.post {
                        val size = mediaList.size
                        mediaList.addAll(mediaList)
                        notifyItemRangeInserted(size - 1, mediaList.size)
                    }
                }

                ViewType.PAGE -> {
                    val b = (holder as MediaPageViewHolder).binding
                    val bannerAnimations: Boolean = PrefManager.getVal(PrefName.BannerAnimations)
                    b.itemCompactImage.loadImage(media.cover)
                    if (bannerAnimations)
                        b.itemCompactBanner.setTransitionGenerator(
                            RandomTransitionGenerator(
                                (10000 + 15000 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong(),
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    val banner = if (bannerAnimations) b.itemCompactBanner else b.itemCompactBannerNoKen
                    banner.blurImage(media.banner ?: media.cover)
                    b.itemCompactOngoing.isVisible = media.status == getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text = ((
                            if (media.userScore == 0)
                                media.meanScore ?: 0
                            else
                                media.userScore
                            ) / 10.0).string
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(b.root.context, (
                            if (media.userScore != 0)
                                R.drawable.item_user_score
                            else
                                R.drawable.item_score
                            )
                    )
                    b.itemCompactTotal.text = getItemTotal(media)
                    b.itemTotalLabel.text = getItemTotalLabel(media)
                    if (position == mediaList!!.size - 2 && viewPager != null) viewPager.post {
                        val size = mediaList.size
                        mediaList.addAll(mediaList)
                        notifyItemRangeInserted(size - 1, mediaList.size)
                    }
                }

                ViewType.SMALL_PAGE -> {
                    val b = (holder as MediaPageSmallViewHolder).binding
                    val bannerAnimations: Boolean = PrefManager.getVal(PrefName.BannerAnimations)
                    b.itemCompactImage.loadImage(media.cover)
                    if (bannerAnimations)
                        b.itemCompactBanner.setTransitionGenerator(
                            RandomTransitionGenerator(
                                (10000 + 15000 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong(),
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    val banner = if (bannerAnimations) b.itemCompactBanner else b.itemCompactBannerNoKen
                    banner.blurImage(media.banner ?: media.cover)
                    b.itemCompactOngoing.isVisible =
                        media.status == getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text = ((
                            if (media.userScore == 0)
                                    media.meanScore ?: 0
                            else
                                media.userScore
                            ) / 10.0).string
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(b.root.context, (
                            if (media.userScore != 0)
                                R.drawable.item_user_score
                            else
                                R.drawable.item_score
                            )
                    )
                    b.itemCompactStatus.text = media.status
                    media.genres.apply {
                        if (isNotEmpty()) {
                            var genres = ""
                            forEach { genres += "$it • " }
                            genres = genres.removeSuffix(" • ")
                            b.itemCompactGenres.text = genres
                        }
                    }
                    b.itemCompactTotal.text = getItemTotal(media)
                    b.itemTotalLabel.text = getItemTotalLabel(media)
                    if (position == mediaList.size - 2 && viewPager != null) viewPager.post {
                        val size = mediaList.size
                        mediaList.addAll(mediaList)
                        notifyItemRangeInserted(size - 1, mediaList.size)
                    }
                }
            }
        }
    }

    private fun getItemTotal(media: Media): String {
        return if (media.anime != null) {
            media.anime.totalEpisodeText
        } else if (media.manga != null) {
            if (media.status == Status.FINISHED) {
                "${media.manga.totalChapters ?: "??"}"
            } else {
                "[${media.manga.totalChapters ?: "??"}]"
            }

        } else ""
    }

    private fun getItemTotalLabel(media: Media): String {
        return when {
            media.anime != null -> {
                if ((media.anime.totalEpisodes ?: 0) != 1)
                    getString(R.string.episode_plural)
                else
                    getString(R.string.episode_singular)
            }
            media.manga != null -> {
                if ((media.manga.totalChapters ?: 0) != 1)
                    getString(R.string.chapter_plural)
                else
                    getString(R.string.chapter_singular)
            }
            else -> ""
        }
    }

    override fun getItemCount() = mediaList!!.size

    override fun getItemViewType(position: Int): Int {
        return viewType.ordinal
    }

    fun randomOptionClick() {
        val media = if (!mediaList.isNullOrEmpty()) {
            mediaList.randomOrNull()
        } else {
            null
        }
        media?.let {
            val index = mediaList?.indexOf(it) ?: -1
            clicked(index, null)
        }
    }

    inner class MediaViewHolder(val binding: ItemMediaCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            if (matchParent) itemView.updateLayoutParams { width = -1 }
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
        }
    }

    inner class MediaLargeViewHolder(val binding: ItemMediaLargeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaBasicViewHolder(val binding: ItemMediaBasicBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemCompactTitleContainer.setSafeOnClickListener {
                clicked(bindingAdapterPosition)
            }
            itemView.setOnTouchListener { _, _ -> true }
            binding.itemCompactTitleContainer.setOnLongClickListener {
                longClicked(bindingAdapterPosition)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaPageViewHolder(val binding: ItemMediaPageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemCompactImage.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage
                )
            }
            itemView.setOnTouchListener { _, _ -> true }
            binding.itemCompactImage.setOnLongClickListener { longClicked(bindingAdapterPosition) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaPageSmallViewHolder(val binding: ItemMediaPageSmallBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemCompactImage.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage
                )
            }
            binding.itemCompactTitleContainer.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage
                )
            }
            itemView.setOnTouchListener { _, _ -> true }
            binding.itemCompactImage.setOnLongClickListener { longClicked(bindingAdapterPosition) }
        }
    }

    fun clicked(position: Int, itemCompactImage: ImageView?) {
        if ((mediaList?.size ?: 0) > position && position != -1) {
            val media = mediaList?.get(position)
            if (itemCompactImage != null) MediaSingleton.bitmap = itemCompactImage.toBitmap()
            ContextCompat.startActivity(
                activity,
                Intent(activity, MediaDetailsActivity::class.java)
                    .putExtra("media", media as Serializable)
                    .putExtra("extension", extension),
                if (itemCompactImage != null) {
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity,
                        itemCompactImage,
                        ViewCompat.getTransitionName(itemCompactImage)!!
                    ).toBundle()
                } else {
                    null
                }
            )
        }
    }

    fun clicked(position: Int) {
        if ((mediaList?.size ?: 0) > position && position != -1) {
            val media = mediaList?.get(position)
            activity.startActivity(
                Intent(activity, MediaDetailsActivity::class.java)
                    .putExtra("media", media as Serializable)
                    .putExtra("extension", extension)
            )
        }
    }

    fun longClicked(position: Int): Boolean {
        if (longClickAction != null) {
            longClickAction?.invoke(position)
            return true
        }
        if ((mediaList?.size ?: 0) > position && position != -1) {
            val media = mediaList?.get(position) ?: return false
            if (activity.supportFragmentManager.findFragmentByTag("list") == null) {
                MediaListDialogSmallFragment.newInstance(media)
                    .show(activity.supportFragmentManager, "list")
                return true
            }
        }
        return false
    }
}

enum class ViewType {
    COMPACT,
    LARGE,
    BASIC,
    PAGE,
    SMALL_PAGE
}