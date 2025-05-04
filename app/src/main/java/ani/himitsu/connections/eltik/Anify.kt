package ani.himitsu.connections.eltik

import ani.himitsu.FileUrl
import ani.himitsu.Mapper
import ani.himitsu.client
import ani.himitsu.media.anime.Episode
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import bit.himitsu.net.isServerDown
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

// https://github.com/Eltik/Anify
object Anify {

    suspend fun getAnifyEpisodeDetails(
        id :Int, host: String = "https://anify.eltik.cc"
    ): Map<String, Episode>? {
        val timeout = (PrefManager.getVal<Float>(PrefName.AnifyTimeout) * 1000).toLong()
        return try {
            val json = withTimeout(timeout) {
                client.get("$host/content-metadata/$id")
            }
            if (json.isServerDown) throw Exception(json.code.toString())
            // Logger.log("Anify Response: ${json.text}")
            json.parsed<JsonArray>().map {
                Mapper.json.decodeFromJsonElement<ContentMetadata>(it)
            }.firstOrNull()?.data?.associate { ep ->
                val num = ep.number.toString()
                num to Episode(
                    number = num,
                    title = ep.title,
                    desc = ep.description,
                    thumb = FileUrl[ep.img],
                    filler = ep.isFiller
                )
            }
        } catch (e: Exception) {
            if (e is TimeoutCancellationException)
                Logger.log("Anify Error: ${timeout}ms timeout exceeded")
            else
                Logger.log("Anify Error: ${e.message}")
            null
        }
    }

    @Serializable
    data class ContentMetadata(
        @SerialName("providerId")
        val providerId: String,
        @SerialName("data")
        val data: List<ProviderData>
    ) : java.io.Serializable {
        @Serializable
        data class ProviderData(
            @SerialName("id")
            val id: String,
            @SerialName("description")
            val description: String,
            @SerialName("hasDub")
            val hasDub: Boolean,
            @SerialName("img")
            val img: String?,
            @SerialName("isFiller")
            val isFiller: Boolean,
            @SerialName("number")
            val number: Int,
            @SerialName("title")
            val title: String,
            @SerialName("rating")
            val rating: Float? = null,
            @SerialName("updatedAt")
            val updatedAt: Long
        )
    }
}