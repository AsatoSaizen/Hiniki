package ani.himitsu.media.cereal

import java.io.Serializable

data class Atelier(
    val id: Int,
    val name: String,
    val isAnimationStudio: Boolean,
    var isFav: Boolean,
    var media: ArrayList<Media>? = null
) : Serializable