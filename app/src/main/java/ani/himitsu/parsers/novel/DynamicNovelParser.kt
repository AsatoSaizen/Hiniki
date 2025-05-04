package ani.himitsu.parsers.novel

import ani.dantotsu.parsers.Book
import ani.dantotsu.parsers.NovelInterface
import ani.dantotsu.parsers.NovelParser
import ani.dantotsu.parsers.ShowResponse
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DynamicNovelParser(val extension: NovelExtension.Installed) : NovelParser() {

    override val volumeRegex = Regex(
        "vol\\.? (\\d+(\\.\\d+)?)|volume (\\d+(\\.\\d+)?)", RegexOption.IGNORE_CASE
    )
    val client = Injekt.get<NetworkHelper>().requestClient

    override suspend fun search(query: String): List<ShowResponse> {
        val source = extension.sources.firstOrNull()
        return if (source is NovelInterface) {
            source.search(query, client)
        } else {
            emptyList()
        }
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?): Book {
        val source = extension.sources.firstOrNull()
        return if (source is NovelInterface) {
            source.loadBook(link, extra, client)
        } else {
            Book("", "", "", emptyList())
        }
    }
}