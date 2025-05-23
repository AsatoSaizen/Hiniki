package ani.himitsu.media

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.currActivity
import ani.himitsu.databinding.ItemGenreBinding
import ani.himitsu.loadImage
import bit.himitsu.content.toPx

class GenreAdapter(
    private val type: String,
    private val big: Boolean = false
) : RecyclerView.Adapter<GenreAdapter.GenreViewHolder>() {
    var genres = mutableMapOf<String, String>()
    var pos = arrayListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val binding = ItemGenreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        if (big) binding.genreCard.updateLayoutParams { height = 72.toPx }
        return GenreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val binding = holder.binding
        if (pos.size > position) {
            val genre = genres[pos[position]]
            binding.genreTitle.text = pos[position]
            binding.genreImage.loadImage(genre)
        }
    }

    override fun getItemCount(): Int = genres.size
    inner class GenreViewHolder(val binding: ItemGenreBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                itemView.context.startActivity(
                    Intent(itemView.context, SearchActivity::class.java)
                        .putExtra("type", type)
                        .putExtra("genre", pos[bindingAdapterPosition])
                        .putExtra("sortBy", AniList.sortBy[2])
                        .putExtra("hideKeyboard", true)
                        .also {
                            if (pos[bindingAdapterPosition].lowercase() == "hentai") {
                                if (!AniList.adult) Toast.makeText(
                                    itemView.context,
                                    currActivity()?.getString(R.string.content_18),
                                    Toast.LENGTH_SHORT
                                ).show()
                                it.putExtra("hentai", true)
                            }
                        }
                )
            }
        }
    }

    fun addGenre(genre: Pair<String, String>) {
        genres[genre.first] = genre.second
        pos.add(genre.first)
        notifyItemInserted(pos.size - 1)
    }
}