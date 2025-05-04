package ani.himitsu.media.cereal

import java.io.Serializable

data class Studio(
    val id: String,
    val name: String,
    var yearMedia: MutableMap<String, ArrayList<Media>>? = null
) : Serializable
