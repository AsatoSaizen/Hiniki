package ani.himitsu.media.anime

import StreamingEpisode
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.databinding.ItemEpisodeListBinding
import ani.himitsu.loadImage
import ani.himitsu.media.cereal.Media
import bit.himitsu.content.toPx
import bit.himitsu.webkit.ChromeIntegration

class StreamingAdapter(
    val media: Media
) : RecyclerView.Adapter<StreamingAdapter.EpisodeViewHolder>() {
    var streamingEpisodes: List<StreamingEpisode> = media.streamingEpisodes

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ).apply {
            itemDownload.visibility = View.GONE
            itemEpisodeDesc.visibility = View.GONE
            itemEpisodeViewed.visibility = View.GONE
            itemEpisodeHeading.updatePadding(right = 8.toPx)
        }
        return EpisodeViewHolder(binding)
    }

    fun appendEpisodes(additional: List<StreamingEpisode>) {
        streamingEpisodes = streamingEpisodes.plus(additional)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val binding = holder.binding
        val episode = streamingEpisodes[position]
        binding.itemEpisodeImage.loadImage(episode.thumbnail)
        val number = episode.title?.removePrefix("Episode ")?.substringBefore(" -")
            ?: "${holder.absoluteAdapterPosition + 1}"
        binding.itemEpisodeNumber.text = number
        binding.itemEpisodeTitle.text = episode.title
    }

    override fun getItemCount(): Int = streamingEpisodes.size

    inner class EpisodeViewHolder(val binding: ItemEpisodeListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val episode = streamingEpisodes[bindingAdapterPosition]
                ChromeIntegration.openStreamDialog(
                    itemView.context, episode.url, media, episode.title
                )
            }
        }
    }
}
