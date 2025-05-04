package ani.himitsu.media.cereal

import java.io.Serializable

data class AniProgress(
    val mediaId: Int,
    var progress: Int
) : Serializable