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
import ani.himitsu.media.cereal.Media
import ani.himitsu.setAnimation
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.italic
import java.io.Serializable

class OfflineMediaAdapter(
    private val offlineItems: List<Media>
) : RecyclerView.Adapter<OfflineMediaAdapter.CharacterViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val binding =
            ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        val binding = holder.binding
        setAnimation(binding.root.context, holder.binding.root)
        val item = offlineItems[position]
        val episodes = item.anime?.let {
            "${it.totalEpisodes} ${getString(R.string.episodes)}"
        } ?: item.manga?.let {
            "${it.totalChapters} ${getString(R.string.chapters)}"
        }
        binding.itemCompactRelation.text = episodes.italic
        binding.itemCompactImage.loadImage(item.cover)
        binding.itemCompactTitle.text = item.mangaName()
    }

    override fun getItemCount(): Int = offlineItems.size

    inner class CharacterViewHolder(val binding: ItemCharacterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val series = offlineItems[bindingAdapterPosition]
                binding.root.context.startActivity(
                    Intent(binding.root.context, MediaDetailsActivity::class.java)
                        .putExtra("media", series as Serializable)
                )
            }
        }
    }
}