package ani.himitsu.media.anime

import ani.himitsu.media.cereal.Author
import ani.himitsu.media.cereal.Studio
import java.io.Serializable

data class Anime(
    var totalEpisodes: Int? = null,

    var episodeDuration: Int? = null,
    var season: String? = null,
    var seasonYear: Int? = null,

    var op: ArrayList<String> = arrayListOf(),
    var ed: ArrayList<String> = arrayListOf(),

    var mainStudio: Studio? = null,
    var author: Author? = null,

    var nextAiringEpisode: Int? = null,
    var nextAiringEpisodeTime: Long? = null,

    var selectedEpisode: String? = null,
    var episodes: MutableMap<String, Episode>? = null,
    var slug: String? = null,
    var anifyEpisodes: Map<String, Episode>? = null,
    var kitsuEpisodes: Map<String, Episode>? = null,
    var fillerEpisodes: Map<String, Episode>? = null,
) : Serializable