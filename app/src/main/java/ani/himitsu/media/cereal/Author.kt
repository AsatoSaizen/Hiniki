package ani.himitsu.media.cereal

import ani.himitsu.connections.anilist.api.FuzzyDate
import java.io.Serializable

data class Author(
    var id: Int,
    var name: String?,
    var image: String?,
    var role: String?,
    var description: String? = null,
    var gender: String? = null,
    var age: Int? = null,
    var dateOfBirth: FuzzyDate? = null,
    var yearMedia: MutableMap<String, ArrayList<Media>>? = null,
    var character: ArrayList<Character>? = null
) : Serializable
