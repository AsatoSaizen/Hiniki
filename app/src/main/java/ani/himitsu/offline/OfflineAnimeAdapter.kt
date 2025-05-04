/*
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package ani.himitsu.offline

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.ItemCharacterBinding
import ani.himitsu.loadImage
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.media.anime.Anime
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.cereal.Offline
import ani.himitsu.setAnimation
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.italic
import java.io.Serializable

class OfflineAnimeAdapter(
    private val offlineItems: ArrayList<Offline>
) : RecyclerView.Adapter<OfflineAnimeAdapter.CharacterViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val binding =
            ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        val binding = holder.binding
        setAnimation(binding.root.context, holder.binding.root)
        val item = offlineItems[position]
        val episodes = "${item.episodes} ${getString(R.string.episodes)}"
        binding.itemCompactRelation.text = episodes.italic
        binding.itemCompactImage.loadImage(item.picture)
        binding.itemCompactTitle.text = item.title
    }

    override fun getItemCount(): Int = offlineItems.size

    inner class CharacterViewHolder(val binding: ItemCharacterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val series = offlineItems[bindingAdapterPosition]
                val media = Media(
                    anime = Anime(
                        totalEpisodes = series.episodes,
                        episodeDuration = series.duration,
                        season = series.season,
                        seasonYear = series.seasonYear,
                    ),
                    id = series.id,
                    name = series.title,
                    nameRomaji = series.title,
                    userPreferredName = "",
                    cover = series.picture,
                    isAdult = false,
                    status = series.status,
                    format = series.type,
                    isFav = false,
                    isListPrivate = false,
                    idKitsu = series.idKitsu,
                    cameFromContinue = true
                )
                binding.root.context.startActivity(
                    Intent(binding.root.context, MediaDetailsActivity::class.java)
                        .putExtra("media", media as Serializable)
                )
            }
        }
    }
}