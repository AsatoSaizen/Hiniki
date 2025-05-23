package ani.himitsu.connections.mal

import android.util.Base64
import ani.himitsu.R
import ani.himitsu.client
import ani.himitsu.openLinkInBrowser
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.tryWithSuspend
import bit.himitsu.nio.Strings.getString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom

object MAL {
    val query: MALQueries = MALQueries()
    const val clientId = "ee520fc1de2dbc4c30297ba9329b256c"
    var username: String? = null
    var avatar: String? = null
    var token: String? = null
    var userid: Int? = null

    fun loginIntent() {
        val codeVerifierBytes = ByteArray(96)
        SecureRandom().nextBytes(codeVerifierBytes)
        val codeChallenge = Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=')
            .replace("+", "-")
            .replace("/", "_")
            .replace("\n", "")

        PrefManager.setVal(PrefName.MALCodeChallenge, codeChallenge)
        openLinkInBrowser(
            "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$clientId&code_challenge=$codeChallenge"
        )
    }

    private suspend fun refreshToken(): ResponseToken? {
        return tryWithSuspend {
            val token = PrefManager.getNullableVal<ResponseToken>(PrefName.MALToken, null)
                ?: throw Exception(getString(R.string.refresh_token_load_failed))
            val res = client.post(
                "https://myanimelist.net/v1/oauth2/token",
                data = mapOf(
                    "client_id" to clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to token.refreshToken
                )
            ).parsed<ResponseToken>()
            saveResponse(res)
            return@tryWithSuspend res
        }
    }

    suspend fun getSavedToken(): Boolean {
        return tryWithSuspend(false) {
            var res: ResponseToken =
                PrefManager.getNullableVal<ResponseToken>(PrefName.MALToken, null)
                    ?: return@tryWithSuspend false
            if (System.currentTimeMillis() > res.expiresIn)
                res = refreshToken()
                    ?: throw Exception(getString(R.string.refreshing_token_failed))
            token = res.accessToken
            return@tryWithSuspend true
        } == true
    }

    fun removeSavedToken() {
        token = null
        username = null
        userid = null
        avatar = null
        PrefManager.removeVal(PrefName.MALToken)
    }

    fun saveResponse(res: ResponseToken) {
        res.expiresIn += System.currentTimeMillis()
        PrefManager.setVal(PrefName.MALToken, res)
    }

    @Serializable
    data class ResponseToken(
        @SerialName("token_type") val tokenType: String,
        @SerialName("expires_in") var expiresIn: Long,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

}
