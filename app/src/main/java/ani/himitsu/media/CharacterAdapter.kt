package ani.himitsu.media

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.databinding.ItemCharacterBinding
import ani.himitsu.loadImage
import ani.himitsu.media.cereal.Character
import ani.himitsu.setAnimation
import bit.himitsu.nio.italic
import java.io.Serializable

class CharacterAdapter(
    private val characterList: ArrayList<Character>
) : RecyclerView.Adapter<CharacterAdapter.CharacterViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val binding =
            ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        val binding = holder.binding
        setAnimation(binding.root.context, holder.binding.root)
        val character = characterList[position]
        character.voiceActor
        binding.itemCompactRelation.text = character.role.italic
        binding.itemCompactImage.loadImage(character.image)
        binding.itemCompactTitle.text = character.name
    }

    override fun getItemCount(): Int = characterList.size
    inner class CharacterViewHolder(val binding: ItemCharacterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val char = characterList[bindingAdapterPosition]
                ContextCompat.startActivity(
                    itemView.context,
                    Intent(
                        itemView.context,
                        CharacterDetailsActivity::class.java
                    ).putExtra("character", char as Serializable),
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        itemView.context as Activity,
                        Pair.create(
                            binding.itemCompactImage,
                            ViewCompat.getTransitionName(binding.itemCompactImage)!!
                        ),
                    ).toBundle()
                )
            }
        }
    }
}