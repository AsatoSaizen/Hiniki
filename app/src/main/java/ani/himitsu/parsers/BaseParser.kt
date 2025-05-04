package ani.himitsu.parsers

import android.graphics.drawable.Drawable
import ani.dantotsu.parsers.ShowResponse
import ani.himitsu.FileUrl
import ani.himitsu.R
import ani.himitsu.media.cereal.Media
import ani.himitsu.okHttpClient
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.util.Logger
import bit.himitsu.nio.Strings.getString
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.SManga
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.Request
import java.io.Serializable
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime


abstract class BaseParser {

    /**
     * Name that will be shown in Source Selection
     * **/
    open val name: String = ""

    /**
     * Name used to save the ShowResponse selected by user or by autoSearch
     * **/
    open val saveName: String = ""

    /**
     * The main URL of the Site
     * **/
    open val hostUrl: String = ""

    /**
     * override as `true` if the site **only** has NSFW media
     * **/
    open val isNSFW = false

    /**
     * mostly redundant for official app, But override if you want to add different languages
     * **/
    open val language = "English"

    /**
     * Icon of the site, can be null
     */
    open val icon: Drawable? = null

    /**
     *  Search for Anime/Manga/Novel, returns a List of Responses
     *
     *  use `encode(query)` to encode the query for making requests
     * **/
    abstract suspend fun search(query: String): List<ShowResponse>

    private suspend fun closestMatch(query: String?): ShowResponse? {
        setUserText(getString(R.string.searching_query, query))
        // Logger.log("Searching : $query")
        try {
            val results = query?.let { search(it) } ?: return null
            val sortedResults = if (results.isNotEmpty()) {
                results.sortedByDescending {
                    Logger.log("Result: ${it.name}")
                    FuzzySearch.ratio(it.name.lowercase(), query.lowercase())
                }
            } else {
                emptyList()
            }
            return sortedResults.firstOrNull()
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * The function app uses to auto find the anime/manga using Media data provided by anilist
     *
     * Isn't necessary to override, but recommended, if you want to improve auto search results
     * **/
    open suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        if (mediaObj.anime != null) {
            (this as? DynamicAnimeParser)?.let { ext ->
                mediaObj.selected?.langIndex?.let {
                    ext.sourceLanguage = it
                }
            }
        } else {
            (this as? DynamicMangaParser)?.let { ext ->
                mediaObj.selected?.langIndex?.let {
                    ext.sourceLanguage = it
                }
            }
        }

        var response: ShowResponse? = loadSavedShowResponse(mediaObj.id)
        if (response != null && this !is OfflineMangaParser && this !is OfflineAnimeParser) {
            saveShowResponse(mediaObj.id, response, true)
        } else {
            val closestName = closestMatch(mediaObj.mainName())
            val mainNameRatio = closestName?.name?.let {
                FuzzySearch.ratio(it.lowercase(), mediaObj.mainName().lowercase())
            } ?: 0
            Logger.log("Fuzzy ratio for closest match in results: $mainNameRatio for ${closestName?.name ?: "None"}")
            if (mainNameRatio > 98) {
                saveShowResponse(mediaObj.id, closestName)
                return closestName
            }
            val closestRomaji =  mediaObj.nameRomaji.takeUnless {
                it == mediaObj.mainName()
            }?.let {
                closestMatch(mediaObj.nameRomaji)
            }
            val romajiRatio = closestRomaji?.name?.let {
                FuzzySearch.ratio(it.lowercase(), mediaObj.nameRomaji.lowercase())
            } ?: 0
            Logger.log("Fuzzy ratio for closest match in RomajiResults: $romajiRatio for ${closestRomaji?.name ?: "None"}")
            if (romajiRatio > 98) {
                saveShowResponse(mediaObj.id, closestRomaji)
                return closestRomaji
            }
            val closestMal = mediaObj.nameMAL?.takeUnless {
                it == mediaObj.mainName()
            }. let {
                closestMatch(mediaObj.nameMAL)
            }
            val malRatio = closestMal?.name?.let {
                FuzzySearch.ratio(it.lowercase(), mediaObj.nameMAL!!.lowercase())
            } ?: 0
            Logger.log("Fuzzy ratio for closest match in results: $malRatio for ${closestMal?.name ?: "None"}")
            if (malRatio > 98) {
                saveShowResponse(mediaObj.id, closestMal)
                return closestMal
            }

            val max = maxOf(mainNameRatio, romajiRatio, malRatio)
            if (max != 0) {
                response = when (max) {
                    mainNameRatio -> closestName
                    romajiRatio -> closestRomaji
                    malRatio -> closestMal
                    else -> response
                }
            }
            saveShowResponse(mediaObj.id, response)
        }
        return response
    }

    /**
     * ping the site to check if it's working or not.
     * @return Triple<Int, Int?, String> : First Int is the status code, Second Int is the response time in milliseconds, Third String is the response message.
     */
    fun ping(): Triple<Int, Int?, String> {
        val client = okHttpClient
        var statusCode = 0
        var responseTime: Int? = null
        var responseMessage = ""
        try {
            val request = Request.Builder()
                .url(hostUrl)
                .build()
            responseTime = measureTime {
                client.newCall(request).execute().use { response ->
                    statusCode = response.code
                    responseMessage = response.message
                }
            }.inWholeMilliseconds.toInt()
        } catch (e: Exception) {
            Logger.log("Failed to ping $name")
            statusCode = -1
            responseMessage = if (e.message.isNullOrEmpty()) "None" else e.message!!
            Logger.log(e)
        }
        return Triple(statusCode, responseTime, responseMessage)
    }

    /**
     * Used to get an existing Search Response which was selected by the user.
     * @param mediaId : The mediaId of the Media object.
     * @return ShowResponse? : The ShowResponse object if found, else null.
     */
    open suspend fun loadSavedShowResponse(mediaId: Int): ShowResponse? {
        checkIfVariablesAreEmpty()
        return PrefManager.getNullableCustomVal(
            "${saveName}_$mediaId",
            null,
            ShowResponse::class.java
        )
    }

    /**
     * Used to save Shows Response using `saveName`.
     * @param mediaId : The mediaId of the Media object.
     * @param response : The ShowResponse object to save.
     * @param selected : Boolean : If the ShowResponse was selected by the user or not.
     */
    open fun saveShowResponse(mediaId: Int, response: ShowResponse?, selected: Boolean = false) {
        if (response != null) {
            checkIfVariablesAreEmpty()
            setUserText(
                "${
                    if (selected) getString(R.string.selected) else getString(R.string.found)
                } : ${response.name}"
            )
            PrefManager.setCustomVal("${saveName}_$mediaId", response)
        }
    }

    fun checkIfVariablesAreEmpty() {
        if (hostUrl.isEmpty()) throw UninitializedPropertyAccessException("Cannot find any installed extensions")
        if (name.isEmpty()) throw UninitializedPropertyAccessException("Cannot find any installed extensions")
        if (saveName.isEmpty()) throw UninitializedPropertyAccessException("Cannot find any installed extensions")
    }

    open var showUserText = ""
    open var showUserTextListener: ((String) -> Unit)? = null

    /**
     * Used to show messages & errors to the User, a useful way to convey what's currently happening or what was done.
     * **/
    fun setUserText(string: String) {
        showUserText = string
        showUserTextListener?.invoke(showUserText)
    }

    fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")
    fun decode(input: String): String = URLDecoder.decode(input, "utf-8")

    val defaultImage = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"
}


