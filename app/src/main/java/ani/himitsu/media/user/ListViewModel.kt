package ani.himitsu.media.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.media.cereal.Media
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.tryWithSuspend

class ListViewModel : ViewModel() {
    var grid = MutableLiveData(PrefManager.getVal<Boolean>(PrefName.ListGrid))

    private val lists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    private val unfilteredLists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    fun getLists(): LiveData<MutableMap<String, ArrayList<Media>>> = lists
    suspend fun loadLists(anime: Boolean, userId: Int, sortOrder: String? = null) {
        tryWithSuspend {
            val res = AniList.query.getMediaLists(anime, userId, sortOrder)
            lists.postValue(res)
            unfilteredLists.postValue(res)
        }
    }

    fun filterLists(genre: String) {
        if (genre == "All") {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        val filteredLists = currentLists.mapValues { entry ->
            entry.value.filter { media ->
                genre in media.genres
            } as ArrayList<Media>
        }.toMutableMap()

        lists.postValue(filteredLists)
    }

    fun searchLists(search: String?) {
        if (search.isNullOrBlank()) {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        val filteredLists = currentLists.mapValues { entry ->
            entry.value.filter { media ->
                media.name?.contains(
                    search,
                    ignoreCase = true
                ) == true || media.synonyms.any { it.contains(search, ignoreCase = true) } ||
                media.nameRomaji.contains(
                    search,
                    ignoreCase = true
                )
            } as ArrayList<Media>
        }.toMutableMap()

        lists.postValue(filteredLists)
    }

    fun unfilterLists() {
        lists.postValue(unfilteredLists.value)
    }

}