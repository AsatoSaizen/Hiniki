package ani.himitsu.media.anime

import ani.himitsu.media.MediaDetailsViewModel
import ani.himitsu.media.SourceAdapter
import ani.himitsu.media.SourceSearchDialogFragment
import ani.dantotsu.parsers.ShowResponse
import kotlinx.coroutines.CoroutineScope

class AnimeSourceAdapter(
    sources: List<ShowResponse>,
    val model: MediaDetailsViewModel,
    val i: Int,
    val id: Int,
    fragment: SourceSearchDialogFragment,
    scope: CoroutineScope
) : SourceAdapter(sources, fragment, scope) {

    override suspend fun onItemClick(source: ShowResponse) {
        model.overrideEpisodes(i, source, id)
    }
}