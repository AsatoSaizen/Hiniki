package ani.himitsu.notifications.subscription

import ani.himitsu.R
import ani.himitsu.media.MediaNameAdapter
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.cereal.Selected
import ani.himitsu.parsers.AnimeParser
import ani.himitsu.parsers.AnimeSources
import ani.himitsu.parsers.BaseParser
import ani.himitsu.parsers.Episode
import ani.himitsu.parsers.MangaChapter
import ani.himitsu.parsers.MangaParser
import ani.himitsu.parsers.MangaSources
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.tryWithSuspend
import ani.himitsu.util.Logger
import bit.himitsu.nio.Strings.getString
import kotlinx.coroutines.withTimeoutOrNull

object SubscriptionHelper {
    private fun loadSelected(
        mediaId: Int
    ): Selected {
        val data =
            PrefManager.getNullableCustomVal("Selected-${mediaId}", null, Selected::class.java)
                ?: Selected().let {
                    it.sourceIndex = 0
                    it.preferDub = PrefManager.getVal(PrefName.SettingsPreferDub)
                    it
                }
        return data
    }

    private fun saveSelected(mediaId: Int, data: Selected) {
        PrefManager.setCustomVal("Selected-${mediaId}", data)
    }

    fun getAnimeParser(id: Int): AnimeParser {
        val sources = AnimeSources
        Logger.log("getAnimeParser size: ${sources.list.size}")
        val selected = loadSelected(id)
        if (selected.sourceIndex >= sources.list.size) {
            selected.sourceIndex = 0
        }
        val parser = sources[selected.sourceIndex]
        parser.selectDub = selected.preferDub
        return parser
    }

    suspend fun getEpisode(
        parser: AnimeParser,
        subscribeMedia: SubscribeMedia
    ): Episode? {
        val selected = loadSelected(subscribeMedia.id)
        val ep = withTimeoutOrNull(10 * 1000) {
            tryWithSuspend {
                val show = parser.loadSavedShowResponse(subscribeMedia.id)
                    ?: forceLoadShowResponse(subscribeMedia, selected, parser)
                    ?: throw Exception(
                        getString(
                            R.string.failed_to_load_data,
                            subscribeMedia.id
                        )
                    )
                show.sAnime?.let {
                    parser.getLatestEpisode(
                        show.link, show.extra,
                        it, selected.latest
                    )
                }
            }
        }

        return ep?.apply {
            selected.latest = number.toFloat()
            saveSelected(subscribeMedia.id, selected)
        }
    }

    fun getMangaParser(id: Int): MangaParser {
        val sources = MangaSources
        Logger.log("getMangaParser size: ${sources.list.size}")
        val selected = loadSelected(id)
        if (selected.sourceIndex >= sources.list.size) {
            selected.sourceIndex = 0
        }
        return sources[selected.sourceIndex]
    }

    suspend fun getChapter(
        parser: MangaParser,
        subscribeMedia: SubscribeMedia
    ): MangaChapter? {
        val selected = loadSelected(subscribeMedia.id)
        val chp = withTimeoutOrNull(10 * 1000) {
            tryWithSuspend {
                val show = parser.loadSavedShowResponse(subscribeMedia.id)
                    ?: forceLoadShowResponse(subscribeMedia, selected, parser)
                    ?: throw Exception(
                        getString(
                            R.string.failed_to_load_data,
                            subscribeMedia.id
                        )
                    )
                show.sManga?.let {
                    parser.getLatestChapter(
                        show.link, show.extra,
                        it, selected.latest
                    )
                }
            }
        }

        return chp?.apply {
            selected.latest = MediaNameAdapter.findChapterNumber(number) ?: 0f
            saveSelected(subscribeMedia.id, selected)
        }
    }

    private suspend fun forceLoadShowResponse(subscribeMedia: SubscribeMedia, selected: Selected, parser: BaseParser): ShowResponse? {
        val tempMedia = Media(
            id = subscribeMedia.id,
            name = null,
            nameRomaji = subscribeMedia.name,
            userPreferredName = subscribeMedia.name,
            isAdult = subscribeMedia.isAdult,
            isFav = false,
            isListPrivate = false,
            userScore = 0,
            userRepeat = 0,
            format = null,
            selected = selected
        )
        parser.autoSearch(tempMedia)
        return parser.loadSavedShowResponse(subscribeMedia.id)
    }

    data class SubscribeMedia(
        val isAnime: Boolean,
        val isAdult: Boolean,
        val id: Int,
        val name: String,
        val cover: String?,
        val banner: String?
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    private const val SUBSCRIPTIONS = "subscriptions"

    @Suppress("UNCHECKED_CAST")
    fun getSubscriptions(): Map<Int, SubscribeMedia> =
        PrefManager.getNullableCustomVal(
            SUBSCRIPTIONS,
            mapOf<Int, SubscribeMedia>(),
            Map::class.java
        ) as Map<Int, SubscribeMedia>

    fun clearSubscriptions() {
        PrefManager.removeCustomVal(SUBSCRIPTIONS)
        PrefManager.removeVal(PrefName.SubscriptionNotificationStore)
        toast(R.string.subscription_deleted)
    }

    fun deleteSubscription(id: Int, showSnack: Boolean = false) {
        val data = PrefManager.getNullableCustomVal(
            SUBSCRIPTIONS,
            mapOf<Int, SubscribeMedia>(),
            Map::class.java
        )!!.toMutableMap()
        val mediaId = data[id] as SubscribeMedia
        data.remove(id)
        PrefManager.setCustomVal(SUBSCRIPTIONS, data)
        val subscriptionStore = PrefManager.getNullableVal<List<SubscriptionStore>>(
            PrefName.SubscriptionNotificationStore, null
        )?.filterNot { it.mediaId == mediaId.id }
        PrefManager.setVal(PrefName.SubscriptionNotificationStore, subscriptionStore)
        if (showSnack) toast(R.string.subscription_deleted)
    }

    fun saveSubscription(media: Media, subscribed: Boolean) {
        val data = PrefManager.getNullableCustomVal(
            SUBSCRIPTIONS,
            mapOf<Int, SubscribeMedia>(),
            Map::class.java
        )!!.toMutableMap()
        if (subscribed) {
            if (!data.containsKey(media.id)) {
                val new = SubscribeMedia(
                    media.anime != null,
                    media.isAdult,
                    media.id,
                    media.userPreferredName,
                    media.cover,
                    media.banner
                )
                data[media.id] = new
            }
        } else {
            data.remove(media.id)
        }
        PrefManager.setCustomVal(SUBSCRIPTIONS, data)
    }
}