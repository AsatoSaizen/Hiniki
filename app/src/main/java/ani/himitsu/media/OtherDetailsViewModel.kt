package ani.himitsu.media

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.media.cereal.Author
import ani.himitsu.media.cereal.Character
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.cereal.Studio
import ani.himitsu.media.reviews.Review
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.nio.twelveHour
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OtherDetailsViewModel : ViewModel() {
    private val character: MutableLiveData<Character> = MutableLiveData(null)
    fun getCharacter(): LiveData<Character> = character
    suspend fun loadCharacter(m: Character) {
        if (character.value == null) character.postValue(AniList.query.getCharacterDetails(m))
    }

    private val studio: MutableLiveData<Studio> = MutableLiveData(null)
    fun getStudio(): LiveData<Studio> = studio
    suspend fun loadStudio(m: Studio) {
        if (studio.value == null) studio.postValue(AniList.query.getStudioDetails(m))
    }

    private val author: MutableLiveData<Author> = MutableLiveData(null)
    fun getAuthor(): LiveData<Author> = author
    suspend fun loadAuthor(m: Author) {
        if (author.value == null) author.postValue(AniList.query.getAuthorDetails(m))
    }

    private val calendar: MutableLiveData<Map<String, MutableList<Media>>> = MutableLiveData(null)
    fun getCalendar(): LiveData<Map<String, MutableList<Media>>> = calendar
    suspend fun loadCalendar() {
        val curr = System.currentTimeMillis() / 1000
        val df = DateFormat.getDateInstance(DateFormat.FULL)
        val map = mutableMapOf<String, MutableList<Media>>()
        val idMap = mutableMapOf<String, MutableList<Int>>()
        val dateFormat = SimpleDateFormat("HH:mm", Locale.ROOT).apply {
            timeZone = if (PrefManager.getVal(PrefName.LocalTimeZone))
                TimeZone.getDefault()
            else
                TimeZone.getTimeZone("GMT")
        }
        AniList.query.recentlyUpdated(curr - (86400 * 7), curr + (86400 * 7)).forEach {
            val v = it.relation?.split(",")?.map { i -> i.toLong() }!!
            val dateInfo = df.format(Date(v[1] * 1000))
            val list = map.getOrPut(dateInfo) { mutableListOf() }
            val idList = idMap.getOrPut(dateInfo) { mutableListOf() }
            val dateTime: Date = Calendar.getInstance().apply {
                timeInMillis = v[1] * 1000
            }.time
            it.relation = "${dateFormat.format(dateTime).twelveHour}${
                if (PrefManager.getVal(PrefName.LocalTimeZone))
                    ""
                else
                    " GMT"
            }"
            if (!idList.contains(it.id)) {
                idList.add(it.id)
                list.add(it)
            }
        }
        calendar.postValue(map)
    }

    private val reviews: MutableLiveData<MutableMap<String, ArrayList<Review>>?> = MutableLiveData(null)
    fun getReviews(): LiveData<MutableMap<String, ArrayList<Review>>?> = reviews
    suspend fun loadReviews(type: String) {
        if (reviews.value?.getOrDefault(type, null) == null) {
            val map = reviews.value ?: mutableMapOf()
            map.getOrPut(type) { AniList.query.getReviews(type) }
            reviews.postValue(map)
        }
    }
}