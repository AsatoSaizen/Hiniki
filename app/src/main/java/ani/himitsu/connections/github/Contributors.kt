package ani.himitsu.connections.github

import ani.himitsu.Mapper
import ani.himitsu.R
import ani.himitsu.client
import ani.himitsu.settings.Developer
import bit.himitsu.nio.Strings.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.Collections

class Contributors {

    fun getContributors(): Array<Developer> {
        val contributors = arrayListOf<Developer>()
        runBlocking(Dispatchers.IO) {
            val res = client.get("https://api.github.com/repos/rebelonion/Dantotsu/contributors?q=contributions&order=desc")
                .parsed<JsonArray>().map {
                    Mapper.json.decodeFromJsonElement<GithubResponse>(it)
                }
            Collections.swap(res, res.indexOf(res.first { it.login == "AbandonedCart" }), 0)
            res.filterNot { it.login == "itsmechinmoy" }.forEach {
                contributors.add(
                    Developer(
                        it.login,
                        it.avatarUrl,
                        when (it.login) {
                            "AbandonedCart" ->  "${getString(R.string.himitsu)} ${getString(R.string.dev_maintainer, "Himitsu")}"
                            else -> getString(R.string.contributor)
                        },
                        it.htmlUrl
                    )
                )
            }
            contributors.addAll(1,
                arrayOf(
                    Developer(
                        "MoonPic",
                        "https://gitlab.com/uploads/-/system/user/avatar/21385212/avatar.png",
                        "${getString(R.string.himitsu)} ${getString(R.string.dev_maintainer, "Website")}",
                        "https://github.com/moonpic"
                    ),
                    Developer(
                        "Aniyomi",
                        "https://avatars.githubusercontent.com/u/136799407?s=200&v=4",
                        "Extension Support",
                        "https://github.com/aniyomiorg"
                    ),
                    Developer(
                        "Kuukiyomi",
                        "https://avatars.githubusercontent.com/u/97435834?v=4",
                        "Torrent Support",
                        "https://github.com/LuftVerbot/kuukiyomi"
                    )
                ).toList()
            )
        }
        return contributors.toTypedArray()
    }


    @Serializable
    data class GithubResponse(
        @SerialName("login")
        val login: String,
        @SerialName("avatar_url")
        val avatarUrl: String,
        @SerialName("html_url")
        val htmlUrl: String
    )
}