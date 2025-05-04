/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package ani.himitsu.connections.others

import ani.himitsu.FileUrl
import ani.himitsu.client
import ani.himitsu.media.anime.Episode
import ani.himitsu.media.cereal.Media
import ani.himitsu.tryWithSuspend
import com.google.gson.Gson
import com.lagradost.nicehttp.NiceResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

object Kitsu {
    private suspend fun getKitsuData(query: String): KitsuResponse? {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Accept-Encoding" to "gzip, deflate",
            "Accept-Language" to "en-US,en;q=0.5",
            "Host" to "kitsu.app",
            "Connection" to "keep-alive",
            "Origin" to "https://kitsu.app",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
        )
        val response = tryWithSuspend {
            val res = client.post(
                "https://kitsu.app/api/graphql",
                headers,
                data = mapOf("query" to query)
            )
            res
        }
        val json = decodeToString(response)
        val gson = Gson()
        return gson.fromJson(json, KitsuResponse::class.java)
    }

    suspend fun getKitsuEpisodesDetails(media: Media): Map<String, Episode>? {
        val query =
            """
query {
  lookupMapping(externalId: ${media.id}, externalSite: ANILIST_ANIME) {
    __typename
    ... on Anime {
      id
      episodes(first: 1500) {
        nodes {
          number
          titles {
            canonical
          }
          description
          thumbnail {
            large {
              url
            }
            original {
              url
            }
          }
        }
      }
    }
  }
}""".trimIndent()

        val result = getKitsuData(query) ?: return null
        // Logger.log("Kitsu Result: $result")
        media.idKitsu = result.data?.lookupMapping?.id
        return (result.data?.lookupMapping?.episodes?.nodes ?: return null).mapNotNull { ep ->
            val num = ep?.number?.toString() ?: return@mapNotNull null
            num to Episode(
                number = num,
                title = ep.titles?.canonical,
                desc = ep.description?.en,
                thumb = FileUrl[ep.thumbnail?.large?.url ?: ep.thumbnail?.original?.url],
            )
        }.toMap()
    }

    suspend fun getMangaKitsuId(media: Media): String? {
        val query =
            """
query {
  lookupMapping(externalId: ${media.id}, externalSite: ANILIST_MANGA) {
    __typename
    ... on Manga {
      id
    }
  }
}""".trimIndent()

        val result = getKitsuData(query) ?: return null
        media.idKitsu = result.data?.lookupMapping?.id
        return media.idKitsu
    }

    private fun decodeToString(res: NiceResponse?): String? {
        return when (res?.headers?.get("Content-Encoding")) {
            "gzip" -> {
                res.body.byteStream().use { inputStream ->
                    GZIPInputStream(inputStream).use { gzipInputStream ->
                        InputStreamReader(gzipInputStream).use { reader ->
                            reader.readText()
                        }
                    }
                }
            }
            else -> {
                res?.body?.string()
            }
        }
    }

    @Serializable
    private data class KitsuResponse(
        @SerialName("data") val data: Data? = null
    ) {
        @Serializable
        data class Data(
            @SerialName("lookupMapping") val lookupMapping: LookupMapping? = null
        )

        @Serializable
        data class LookupMapping(
            @SerialName("id") val id: String? = null,
            @SerialName("episodes") val episodes: Episodes? = null
        )

        @Serializable
        data class Episodes(
            @SerialName("nodes") val nodes: List<Node?>? = null
        )

        @Serializable
        data class Node(
            @SerialName("number") val number: Int? = null,
            @SerialName("titles") val titles: Titles? = null,
            @SerialName("description") val description: Description? = null,
            @SerialName("thumbnail") val thumbnail: Thumbnail? = null
        )

        @Serializable
        data class Description(
            @SerialName("en") val en: String? = null
        )

        @Serializable
        data class Thumbnail(
            @SerialName("large") val large: ThumbnailUrl? = null,
            @SerialName("original") val original: ThumbnailUrl? = null
        )

        @Serializable
        data class ThumbnailUrl(
            @SerialName("url") val url: String? = null
        )

        @Serializable
        data class Titles(
            @SerialName("canonical") val canonical: String? = null
        )
    }
}