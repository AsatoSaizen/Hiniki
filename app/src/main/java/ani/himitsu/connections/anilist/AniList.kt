package ani.himitsu.connections.anilist

import ani.dantotsu.connections.comments.CommentsAPI
import ani.himitsu.R
import ani.himitsu.client
import ani.himitsu.connections.Status
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.util.Logger
import bit.himitsu.net.isServerDown
import bit.himitsu.nio.Strings.getString
import java.util.Calendar

object AniList {

    val ITEMS_PER_PAGE = when (PrefManager.getVal<Int>(PrefName.LoadingAffinity)) {
        -2 -> 50
        -1 -> 40
        0 -> 30
        1 -> 20
        2 -> 10
        else -> { 30 }
    }
    val PAGE_MAX_ITEMS = 50

    val CLIENT_ID = 19006 // 14959
    val LOGIN_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"

    val query: AniListQueries = AniListQueries()
    val mutation: AniListMutations = AniListMutations()

    var token: String? = null
    var username: String? = null
    var adult: Boolean = false
    var userid: Int? = null
    var avatar: String? = null
    var bg: String? = null
    var episodesWatched: Int? = null
    var chapterRead: Int? = null
    var unreadNotificationCount: Int = 0

    var genres: ArrayList<String>? = null
    var tags: Map<Boolean, List<String>>? = null

    var initialized = false

    var rateLimitReset: Long = 0

    val sortBy = listOf(
        "SCORE_DESC",
        "POPULARITY_DESC",
        "TRENDING_DESC",
        "START_DATE_DESC",
        "TITLE_ENGLISH",
        "TITLE_ENGLISH_DESC",
        "SCORE"
    )

    val source = listOf(
        "ORIGINAL",
        "MANGA",
        "LIGHT NOVEL",
        "VISUAL NOVEL",
        "VIDEO GAME",
        "OTHER",
        "NOVEL",
        "DOUJINSHI",
        "ANIME",
        "WEB NOVEL",
        "LIVE ACTION",
        "GAME",
        "COMIC",
        "MULTIMEDIA PROJECT",
        "PICTURE BOOK"
    )

    val animeStatus = listOf(
        Status.FINISHED,
        Status.RELEASING,
        Status.UNRELEASED,
        Status.CANCELLED
    )

    val mangaStatus = listOf(
        Status.FINISHED,
        Status.RELEASING,
        Status.UNRELEASED,
        Status.HIATUS,
        Status.CANCELLED
    )

    val seasons = listOf(
        "WINTER", "SPRING", "SUMMER", "FALL"
    )

    val animeFormats = listOf(
        "TV", "TV SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC"
    )

    val mangaFormats = listOf(
        "MANGA", "NOVEL", "ONE SHOT"
    )

    val authorRoles = listOf(
        "Original Creator", "Story & Art", "Story"
    )

    private val cal: Calendar = Calendar.getInstance()
    private val currentYear = cal.get(Calendar.YEAR)
    private val currentSeason: Int = when (cal.get(Calendar.MONTH)) {
        0, 1, 2 -> 0
        3, 4, 5 -> 1
        6, 7, 8 -> 2
        9, 10, 11 -> 3
        else -> 0
    }

    private fun getSeason(next: Boolean): Pair<String, Int> {
        var newSeason = if (next) currentSeason + 1 else currentSeason - 1
        var newYear = currentYear
        if (newSeason > 3) {
            newSeason = 0
            newYear++
        } else if (newSeason < 0) {
            newSeason = 3
            newYear--
        }
        return seasons[newSeason] to newYear
    }

    val currentSeasons = listOf(
        getSeason(false),
        seasons[currentSeason] to currentYear,
        getSeason(true)
    )

    fun getSavedToken(): Boolean {
        token = PrefManager.getVal(PrefName.AnilistToken, null as String?)
        return !token.isNullOrEmpty()
    }

    fun removeSavedToken() {
        token = null
        username = null
        adult = false
        userid = null
        avatar = null
        bg = null
        episodesWatched = null
        chapterRead = null
        PrefManager.removeVal(PrefName.AnilistToken)
        //logout from comments api
        CommentsAPI.logout()

    }

    suspend inline fun <reified T : Any> executeQuery(
        query: String,
        variables: String = "",
        force: Boolean = false,
        useToken: Boolean = true,
        show: Boolean = false,
        cache: Int? = null
    ): T? {
        return try {
            Logger.log("AniList Query: $query")
            if (rateLimitReset > System.currentTimeMillis() / 1000) {
                val rateLimit = getString(
                    R.string.rate_limit,
                    (rateLimitReset - (System.currentTimeMillis() / 1000)).toInt()
                )
                snackString(rateLimit)
                throw Exception(rateLimit)
            }
            val headers = mutableMapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "Accept" to "application/json"
            )

            if (token != null || force) {
                if (token != null && useToken) headers["Authorization"] = "Bearer $token"

                val json = client.post(
                    url = "https://graphql.anilist.co/",
                    headers = headers,
                    data = mapOf("query" to query, "variables" to variables),
                    cacheTime = cache ?: 10
                )
                val remaining = json.headers["X-RateLimit-Remaining"]?.toIntOrNull() ?: -1
                Logger.log("Remaining requests: $remaining")
                if (json.isServerDown || !json.text.startsWith("{")) {
                    throw Exception(getString(R.string.anilist_down))
                } else if (json.code == 429) {
                    val retry = json.headers["Retry-After"]?.toIntOrNull() ?: -1
                    val passedLimitReset = json.headers["X-RateLimit-Reset"]?.toLongOrNull() ?: 0
                    if (retry > 0) rateLimitReset = passedLimitReset

                    snackString(getString(R.string.rate_limit, retry))
                    throw Exception(getString(R.string.rate_limit, retry))
                }
                json.parsed()
            } else null
        } catch (e: Exception) {
            if (show) snackString(getString(R.string.anilist_data_error, e.message))
            Logger.log("AniList Query Error: ${e.message}")
            null
        }
    }
}

