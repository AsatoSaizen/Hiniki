package ani.himitsu.connections.github

import ani.himitsu.Mapper
import ani.himitsu.client
import ani.himitsu.settings.Developer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

class Forks {

    fun getForks(): Array<Developer> {
        val forks = arrayListOf<Developer>()
        runBlocking(Dispatchers.IO) {
            client.get("https://api.github.com/repos/rebelonion/Dantotsu/forks?sort=stargazers")
                .parsed<JsonArray>().map {
                    Mapper.json.decodeFromJsonElement<GithubResponse>(it)
                }.forEach {
                    forks.add(
                        Developer(
                            it.name,
                            it.owner.avatarUrl,
                            it.owner.login,
                            it.htmlUrl
                        )
                    )
                }
        }
        return forks.toTypedArray()
    }


    @Serializable
    data class GithubResponse(
        @SerialName("name")
        val name: String,
        val owner: Owner,
        @SerialName("html_url")
        val htmlUrl: String,
    ) {
        @Serializable
        data class Owner(
            @SerialName("login")
            val login: String,
            @SerialName("avatar_url")
            val avatarUrl: String
        )
    }
}