package ani.himitsu.media.cereal

import java.io.Serializable

data class Offline(
    var id: Int,
    var sources: List<String>,
    var title : String,
    var type : String,
    var episodes : Int,
    var status : String,
    var season: String,
    var seasonYear: Int?,
    var picture : String,
    var thumbnail : String,
    val duration: Int,
    val idKitsu: String
) : Serializable