/*
 * https://github.com/github/dmca/blob/master/2023/07/2023-07-12-crunchyroll.md
 */

package ani.himitsu.connections.mal

//import ani.himitsu.client
//import ani.dantotsu.parsers.ShowResponse
//import ani.himitsu.tryWithSuspend
//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//
//object MalSyncBackup {
//    @Serializable
//    data class MalBackUpSync(
//        @SerialName("Pages") val pages: Map<String, Map<String, Page>>? = null
//    )
//
//    @Serializable
//    data class Page(
//        val identifier: String,
//        val title: String,
//        val url: String? = null,
//        val image: String? = null,
//        val active: Boolean? = null,
//    )
//
//    suspend fun get(id: Int, name: String, dub: Boolean = false): ShowResponse? {
//        return tryWithSuspend {
//            val json =
//                client.get("https://raw.githubusercontent.com/MALSync/MAL-Sync-Backup/master/data/anilist/anime/$id.json")
//            if (json.text != "404: Not Found")
//                json.parsed<MalBackUpSync>().pages?.get(name)?.forEach {
//                    val page = it.value
//                    val isDub = page.title.replace(" ", "").endsWith("(dub)", true)
//                    val slug = if (dub == isDub) page.identifier else null
//                    if (slug != null && page.active == true && page.url != null) {
//                        val url = when (name) {
//                            "Gogoanime" -> slug
//                            "Tenshi" -> slug
//                            else -> page.url
//                        }
//                        return@tryWithSuspend ShowResponse(page.title, url, page.image ?: "")
//                    }
//                }
//            return@tryWithSuspend null
//        }
//    }
//}