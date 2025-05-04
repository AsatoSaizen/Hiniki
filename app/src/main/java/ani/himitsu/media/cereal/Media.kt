package ani.himitsu.media.cereal

import StreamingEpisode
import android.graphics.Bitmap
import ani.himitsu.connections.anilist.api.FuzzyDate
import ani.himitsu.connections.anilist.api.MediaEdge
import ani.himitsu.connections.anilist.api.MediaList
import ani.himitsu.connections.anilist.api.MediaType
import ani.himitsu.media.anime.Anime
import ani.himitsu.media.manga.Manga
import ani.himitsu.media.reviews.Review
import ani.himitsu.profile.User
import java.io.Serializable
import ani.himitsu.connections.anilist.api.Media as ApiMedia

data class Media(
    val anime: Anime? = null,
    val manga: Manga? = null,
    val id: Int,

    var idMAL: Int? = null,
    var typeMAL: String? = null,

    val name: String?,
    val nameRomaji: String,
    val userPreferredName: String,

    var cover: String? = null,
    var banner: String? = null,
    var relation: String? = null,
    var favourites: Int? = null,

    var isAdult: Boolean,
    var isFav: Boolean = false,
    var notify: Boolean = false,

    var userListId: Int? = null,
    var isListPrivate: Boolean = false,
    var notes: String? = null,
    var userProgress: Int? = null,
    var userVolumes: Int? = null,
    var userStatus: String? = null,
    var userScore: Int = 0,
    var userRepeat: Int = 0,
    var userUpdatedAt: Long? = null,
    var userStartedAt: FuzzyDate = FuzzyDate(),
    var userCompletedAt: FuzzyDate = FuzzyDate(),
    var inCustomListsOf: MutableMap<String, Boolean>? = null,
    var userFavOrder: Int? = null,

    val status: String? = null,
    var format: String? = null,
    var source: String? = null,
    var countryOfOrigin: String? = null,
    val meanScore: Int? = null,
    var genres: ArrayList<String> = arrayListOf(),
    var tags: ArrayList<String> = arrayListOf(),
    var spoilers: ArrayList<String> = arrayListOf(),
    var description: String? = null,
    var synonyms: ArrayList<String> = arrayListOf(),
    var trailer: String? = null,
    var startDate: FuzzyDate? = null,
    var endDate: FuzzyDate? = null,
    var popularity: Int? = null,

    var timeUntilAiring: Long? = null,

    var characters: ArrayList<Character>? = null,
    var review: ArrayList<Review>? = null,
    var staff: ArrayList<Author>? = null,
    var prequel: Media? = null,
    var sequel: Media? = null,
    var relations: ArrayList<Media>? = null,
    var recommendations: ArrayList<Media>? = null,
    var users: ArrayList<User>? = null,
    var externalLinks: ExternalLinks = ExternalLinks(),
    var streamingEpisodes: ArrayList<StreamingEpisode> = arrayListOf(),

    var nameMAL: String? = null,
    var shareLink: String? = null,
    var selected: Selected? = null,

    var idKitsu: String? = null,

    var cameFromContinue: Boolean = false,

    var characterPages: Int = 0
) : Serializable {

    constructor(apiMedia: ApiMedia) : this(
        id = apiMedia.id,
        idMAL = apiMedia.idMal,
        popularity = apiMedia.popularity,
        name = apiMedia.title!!.english,
        nameRomaji = apiMedia.title!!.romaji,
        userPreferredName = apiMedia.title!!.userPreferred,
        cover = apiMedia.coverImage?.extraLarge ?: apiMedia.coverImage?.large ?: apiMedia.coverImage?.medium,
        banner = apiMedia.bannerImage,
        status = apiMedia.status.toString(),
        isFav = apiMedia.isFavourite!!,
        isAdult = apiMedia.isAdult == true,
        isListPrivate = apiMedia.mediaListEntry?.private == true,
        userProgress = apiMedia.mediaListEntry?.progress,
        userScore = apiMedia.mediaListEntry?.score?.toInt() ?: 0,
        userStatus = apiMedia.mediaListEntry?.status?.toString(),
        meanScore = apiMedia.meanScore,
        startDate = apiMedia.startDate,
        endDate = apiMedia.endDate,
        favourites = apiMedia.favourites,
        timeUntilAiring = apiMedia.nextAiringEpisode?.timeUntilAiring?.let { it.toLong() * 1000 },
        anime = if (apiMedia.type == MediaType.ANIME) Anime(
            totalEpisodes = apiMedia.episodes?.takeIf {
                it >= (apiMedia.nextAiringEpisode?.episode?.minus(1) ?: 0)
            } ?: apiMedia.nextAiringEpisode?.episode?.minus(1),
            nextAiringEpisode = apiMedia.nextAiringEpisode?.episode
        ) else null,
        manga = if (apiMedia.type == MediaType.MANGA) Manga(totalChapters = apiMedia.chapters) else null,
        format = apiMedia.format?.toString(),
    )

    constructor(mediaList: MediaList) : this(mediaList.media!!) {
        this.userProgress = mediaList.progress
        this.isListPrivate = mediaList.private == true
        this.userScore = mediaList.score?.toInt() ?: 0
        this.userStatus = mediaList.status?.toString()
        this.userUpdatedAt = mediaList.updatedAt?.toLong()
        this.genres = mediaList.media?.genres?.toMutableList() as? ArrayList<String>? ?: arrayListOf()
    }

    constructor(mediaEdge: MediaEdge) : this(mediaEdge.node!!) {
        this.relation = mediaEdge.relationType?.toString()
    }

    fun mainName() = name ?: nameMAL ?: nameRomaji
    fun mangaName() = if (countryOfOrigin != "JP") mainName() else nameRomaji
}

fun emptyMedia() = Media(
    id = 0,
    name = "No media found",
    nameRomaji = "No media found",
    userPreferredName = "",
    isAdult = false,
    isFav = false,
    isListPrivate = false,
    userScore = 0,
    userStatus = "",
    format = "",
)

object MediaSingleton {
    var media: Media? = null
    var bitmap: Bitmap? = null
}
