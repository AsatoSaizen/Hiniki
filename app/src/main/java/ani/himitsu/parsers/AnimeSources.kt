package ani.himitsu.parsers

import ani.himitsu.Lazier
import ani.himitsu.lazyList
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

object AnimeSources : WatchSources() {
    override var list: List<Lazier<BaseParser>> = emptyList()
    var pinnedAnimeSources: List<String> = emptyList()
    var isInitialized = false

    suspend fun init(fromExtensions: StateFlow<List<AnimeExtension.Installed>>) {
        pinnedAnimeSources =
            PrefManager.getNullableVal<List<String>>(PrefName.AnimeSourcesOrder, null).orEmpty()

        // Initialize with the first value from StateFlow
        val initialExtensions = fromExtensions.first()
        list = createParsersFromExtensions(initialExtensions) + Lazier(
            { OfflineAnimeParser() },
            "Downloaded"
        )
        isInitialized = true

        // Update as StateFlow emits new values
        fromExtensions.collect { extensions ->
            list = sortPinnedAnimeSources(
                createParsersFromExtensions(extensions),
                pinnedAnimeSources
            ) + Lazier(
                { OfflineAnimeParser() },
                "Downloaded"
            )
        }
    }

    fun performReorderAnimeSources() {
        //remove the downloaded source from the list to avoid duplicates
        list = list.filter { it.name != "Downloaded" }
        list = sortPinnedAnimeSources(list, pinnedAnimeSources) + Lazier(
            { OfflineAnimeParser() },
            "Downloaded"
        )
    }

    private fun createParsersFromExtensions(extensions: List<AnimeExtension.Installed>): List<Lazier<BaseParser>> {
        return extensions.map { extension ->
            Lazier({ DynamicAnimeParser(extension) }, extension.name)
        }
    }

    private fun sortPinnedAnimeSources(
        sources: List<Lazier<BaseParser>>,
        pinnedAnimeSources: List<String>
    ): List<Lazier<BaseParser>> {
        val pinnedSourcesMap = sources.filter { pinnedAnimeSources.contains(it.name) }
            .associateBy { it.name }
        val orderedPinnedSources = pinnedAnimeSources.mapNotNull { name ->
            pinnedSourcesMap[name]
        }
        val unpinnedSources = sources.filterNot { pinnedAnimeSources.contains(it.name) }
        return orderedPinnedSources + unpinnedSources
    }
}

object HAnimeSources : WatchSources() {
    private val aList: List<Lazier<BaseParser>> = lazyList()
    override val list = aList + AnimeSources.list
}
