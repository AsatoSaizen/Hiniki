package ani.himitsu.media.cereal

import java.io.Serializable

data class Selected(
    var window: Int = 0,
    var recyclerStyle: Int? = null,
    var recyclerReversed: Boolean = false,
    var chip: Int = 0,
    // var source: String? = null,
    var sourceIndex: Int = 0,
    var langIndex: Int = 0,
    var preferDub: Boolean = false,
    var server: String? = null,
    var video: Int = 0,
    var latest: Float = 0f,
    var scanlators: List<String>? = null,
) : Serializable
