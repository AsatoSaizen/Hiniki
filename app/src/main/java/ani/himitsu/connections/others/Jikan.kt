package ani.himitsu.connections.others

import ani.himitsu.client
import ani.himitsu.media.anime.Episode
import ani.himitsu.tryWithSuspend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Jikan {

    const val apiUrl = "https://api.jikan.moe/v4/"

    suspend inline fun <reified T : Any> query(endpoint: String): T? {
        return tryWithSuspend { client.get("$apiUrl$endpoint").parsed() }
    }

    suspend fun getEpisodes(malId: Int): Map<String, Episode> {
        var hasNextPage = true
        var page = 0
        val eps = mutableMapOf<String, Episode>()
        while (hasNextPage) {
            page++
            val res = query<EpisodeResponse>("anime/$malId/episodes?page=$page")
            res?.data?.forEach {
                val ep = it.malID.toString()
                eps[ep] = Episode(
                    ep, title = it.title,
                    filler = it.filler,
                )
            }
            hasNextPage = res?.pagination?.hasNextPage == true
        }
        return eps
    }

    @Serializable
    data class EpisodeResponse(
        val pagination: Pagination? = null,
        val data: List<Datum>? = null
    ) {
        @Serializable
        data class Datum(
            @SerialName("mal_id")
            val malID: Int,
            val title: String? = null,
            val filler: Boolean,
            //            val recap: Boolean,
        )

        @Serializable
        data class Pagination(
            @SerialName("has_next_page")
            val hasNextPage: Boolean? = null
        )
    }

}


