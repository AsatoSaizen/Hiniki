package ani.himitsu.connections.anilist

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.himitsu.R
import ani.himitsu.connections.discord.Discord
import ani.himitsu.connections.mal.MAL
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.cereal.SearchResults
import ani.himitsu.notifications.subscription.SubscriptionHelper
import ani.himitsu.profile.User
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.tryWithSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

suspend fun getUserId(context: Context, block: () -> Unit) {
    if (!AniList.initialized) {
        if (AniList.query.getUserData()) {
            tryWithSuspend {
                if (MAL.token != null && !MAL.query.getUserData())
                    snackString(context.getString(R.string.error_loading_mal_user_data))
            }
        } else {
            throw Exception()
        }
    }
    block.invoke()
}

class AniListHomeViewModel : ViewModel() {
    private val listImages: MutableLiveData<ArrayList<String?>> =
        MutableLiveData<ArrayList<String?>>(arrayListOf())

    fun getListImages(): LiveData<ArrayList<String?>> = listImages
    suspend fun setListImages() = listImages.postValue(AniList.query.getBannerImages())

    private val subscribedItems: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getSubscriptions(): LiveData<ArrayList<Media>> = subscribedItems

    fun setSubscriptions(subscribed: ArrayList<Media>) {
        subscribedItems.postValue(subscribed)
    }

    private val animeContinue: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getAnimeContinue(): LiveData<ArrayList<Media>> = animeContinue

    private val animeFav: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getAnimeFav(): LiveData<ArrayList<Media>> = animeFav

    private val animePlanned: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getAnimePlanned(): LiveData<ArrayList<Media>> = animePlanned

    private val mangaContinue: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getMangaContinue(): LiveData<ArrayList<Media>> = mangaContinue

    private val mangaFav: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getMangaFav(): LiveData<ArrayList<Media>> = mangaFav

    private val mangaPlanned: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getMangaPlanned(): LiveData<ArrayList<Media>> = mangaPlanned

    private val recommendation: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getRecommendation(): LiveData<ArrayList<Media>> = recommendation

    private val userStatus: MutableLiveData<ArrayList<User>> =
        MutableLiveData<ArrayList<User>>(null)

    fun getUserStatus(): LiveData<ArrayList<User>> = userStatus

    private val hiddenAnime: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getHiddenAnime(): LiveData<ArrayList<Media>> = hiddenAnime

    private val hiddenManga: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getHiddenManga(): LiveData<ArrayList<Media>> = hiddenManga

    @Suppress("UNCHECKED_CAST")
    suspend fun initHomePage() {
        val res = AniList.query.initHomePage()
        res["currentAnime"]?.let { animeContinue.postValue(it as ArrayList<Media>?) }
        res["favoriteAnime"]?.let { animeFav.postValue(it as ArrayList<Media>?) }
        res["plannedAnime"]?.let { animePlanned.postValue(it as ArrayList<Media>?) }
        res["currentManga"]?.let { mangaContinue.postValue(it as ArrayList<Media>?) }
        res["favoriteManga"]?.let { mangaFav.postValue(it as ArrayList<Media>?) }
        res["plannedManga"]?.let { mangaPlanned.postValue(it as ArrayList<Media>?) }
        res["recommendations"]?.let { recommendation.postValue(it as ArrayList<Media>?) }
        res["status"]?.let { userStatus.postValue(it as ArrayList<User>?) }
        res["hiddenAnime"]?.let { hiddenAnime.postValue(it as ArrayList<Media>?) }
        res["hiddenManga"]?.let { hiddenManga.postValue(it as ArrayList<Media>?) }

        val subscribed = arrayListOf<Media>()
        SubscriptionHelper.getSubscriptions().values.forEach { media ->
            try {
                subscribed.add(
                    res.values.flatten().first { (it as Media).id == media.id } as Media
                )
            } catch (ignored: Exception) { }
        }
        setSubscriptions(subscribed)
    }

    suspend fun loadMain() {
        AniList.getSavedToken()
        MAL.getSavedToken()
        Discord.getSavedToken()
        val ret = AniList.query.getGenresAndTags()
        withUIContext {
            genres.value = ret
        }
        genres.postValue(AniList.query.getGenresAndTags())
    }

    val empty = MutableLiveData<Boolean>(null)

    var loaded: Boolean = false
    val genres: MutableLiveData<Boolean?> = MutableLiveData(null)
}

class AniListAnimeViewModel : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val type = "ANIME"
    private val trending: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getTrending(): LiveData<MutableList<Media>> = trending
    suspend fun loadTrending(i: Int) {
        val (season, year) = AniList.currentSeasons[i]
        trending.postValue(
            AniList.query.search(
                type,
                perPage = 12,
                sort = AniList.sortBy[2],
                season = season,
                seasonYear = year,
                hd = true,
                adultOnly = PrefManager.getVal(PrefName.AdultOnly)
            )?.results
        )
    }

    private val animePopular = MutableLiveData<SearchResults?>(null)

    fun getPopular(): LiveData<SearchResults?> = animePopular
    suspend fun loadPopular(
        type: String,
        searchVal: String? = null,
        genres: ArrayList<String>? = null,
        sort: String = AniList.sortBy[1],
        onList: Boolean = true,
    ) {
        animePopular.postValue(
            AniList.query.search(
                type,
                search = searchVal,
                onList = if (onList) null else false,
                sort = sort,
                genres = genres,
                adultOnly = PrefManager.getVal(PrefName.AdultOnly)
            )
        )
    }

    suspend fun loadNextPage(r: SearchResults) = animePopular.postValue(
        AniList.query.search(
            r.type,
            r.page + 1,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.status,
            r.source,
            r.format,
            r.countryOfOrigin,
            r.isAdult,
            r.onList,
            adultOnly = PrefManager.getVal(PrefName.AdultOnly),
        )
    )

    var loaded: Boolean = false
    private val updated: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getUpdated(): LiveData<MutableList<Media>> = updated

    private val popularMovies: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getMovies(): LiveData<MutableList<Media>> = popularMovies

    private val topRatedAnime: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getTopRated(): LiveData<MutableList<Media>> = topRatedAnime

    private val mostFavAnime: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getMostFav(): LiveData<MutableList<Media>> = mostFavAnime
    suspend fun loadAll() {
        val list = AniList.query.loadAnimeList()
        updated.postValue(list["recentUpdates"])
        popularMovies.postValue(list["trendingMovies"])
        topRatedAnime.postValue(list["topRated"])
        mostFavAnime.postValue(list["mostFav"])
    }
}

class AniListMangaViewModel : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val type = "MANGA"
    private val trending: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getTrending(): LiveData<MutableList<Media>> = trending
    suspend fun loadTrending() =
        trending.postValue(
            AniList.query.search(
                type,
                perPage = 10,
                sort = AniList.sortBy[2],
                hd = true,
                adultOnly = PrefManager.getVal(PrefName.AdultOnly)
            )?.results
        )

    private val mangaPopular = MutableLiveData<SearchResults?>(null)
    fun getPopular(): LiveData<SearchResults?> = mangaPopular
    suspend fun loadPopular(
        type: String,
        searchVal: String? = null,
        genres: ArrayList<String>? = null,
        sort: String = AniList.sortBy[1],
        onList: Boolean = true,
    ) {
        mangaPopular.postValue(
            AniList.query.search(
                type,
                search = searchVal,
                onList = if (onList) null else false,
                sort = sort,
                genres = genres,
                adultOnly = PrefManager.getVal(PrefName.AdultOnly)
            )
        )
    }

    suspend fun loadNextPage(r: SearchResults) = mangaPopular.postValue(
        AniList.query.search(
            r.type,
            r.page + 1,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.status,
            r.source,
            r.format,
            r.countryOfOrigin,
            r.isAdult,
            r.onList,
            r.excludedGenres,
            r.excludedTags,
            r.startYear,
            r.seasonYear,
            r.season,
            adultOnly = PrefManager.getVal(PrefName.AdultOnly)
        )
    )

    var loaded: Boolean = false

    private val popularManga: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getPopularManga(): LiveData<MutableList<Media>> = popularManga

    private val popularManhwa: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getPopularManhwa(): LiveData<MutableList<Media>> = popularManhwa

    private val popularNovel: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getPopularNovel(): LiveData<MutableList<Media>> = popularNovel

    private val topRatedManga: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getTopRated(): LiveData<MutableList<Media>> = topRatedManga

    private val mostFavManga: MutableLiveData<MutableList<Media>> =
        MutableLiveData<MutableList<Media>>(null)

    fun getMostFav(): LiveData<MutableList<Media>> = mostFavManga
    suspend fun loadAll() {
        val list = AniList.query.loadMangaList()
        popularManga.postValue(list["trendingManga"])
        popularManhwa.postValue(list["trendingManhwa"])
        popularNovel.postValue(list["trendingNovel"])
        topRatedManga.postValue(list["topRated"])
        mostFavManga.postValue(list["mostFav"])
    }
}

class AniListSearch : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val result: MutableLiveData<SearchResults?> = MutableLiveData<SearchResults?>(null)

    fun getSearch(): LiveData<SearchResults?> = result
    suspend fun loadSearch(r: SearchResults) = result.postValue(
        AniList.query.search(
            r.type,
            r.page,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.status,
            r.source,
            r.format,
            r.countryOfOrigin,
            r.isAdult,
            r.onList,
            r.excludedGenres,
            r.excludedTags,
            r.startYear,
            r.seasonYear,
            r.season
        )
    )

    suspend fun loadNextPage(r: SearchResults) = result.postValue(
        AniList.query.search(
            r.type,
            r.page + 1,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.status,
            r.source,
            r.format,
            r.countryOfOrigin,
            r.isAdult,
            r.onList,
            r.excludedGenres,
            r.excludedTags,
            r.startYear,
            r.seasonYear,
            r.season
        )
    )

    val allResults = MutableLiveData<ArrayList<Any>?>(null)
}

class GenresViewModel : ViewModel() {
    var genres: MutableMap<String, String>? = null
    var done = false
    var doneListener: (() -> Unit)? = null
    suspend fun loadGenres(genre: ArrayList<String>, listener: (Pair<String, String>) -> Unit) {
        if (genres == null) {
            genres = mutableMapOf()
            AniList.query.getGenres(genre) {
                genres!![it.first] = it.second
                listener.invoke(it)
                if (genres!!.size == genre.size) {
                    done = true
                    doneListener?.invoke()
                }
            }
        }
    }
}

class ProfileViewModel : ViewModel() {

    private val mangaFav: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getMangaFav(): LiveData<ArrayList<Media>> = mangaFav

    private val animeFav: MutableLiveData<ArrayList<Media>> =
        MutableLiveData<ArrayList<Media>>(null)

    fun getAnimeFav(): LiveData<ArrayList<Media>> = animeFav

    suspend fun setData(id: Int) {
        val res = AniList.query.initProfilePage(id)
        val mangaList = res?.data?.favoriteManga?.favourites?.manga?.edges?.mapNotNull {
            it.node?.let { i ->
                Media(i).apply { isFav = true }
            }
        }
        mangaFav.postValue(ArrayList(mangaList ?: arrayListOf()))
        val animeList = res?.data?.favoriteAnime?.favourites?.anime?.edges?.mapNotNull {
            it.node?.let { i ->
                Media(i).apply { isFav = true }
            }
        }
        animeFav.postValue(ArrayList(animeList ?: arrayListOf()))
    }

    fun refresh() {
        mangaFav.postValue(mangaFav.value)
        animeFav.postValue(animeFav.value)
    }
}