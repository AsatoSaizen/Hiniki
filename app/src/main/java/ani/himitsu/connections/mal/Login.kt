package ani.himitsu.connections.mal

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ani.himitsu.R
import ani.himitsu.client
import ani.himitsu.connections.mal.MAL.clientId
import ani.himitsu.connections.mal.MAL.saveResponse
import ani.himitsu.logError
import ani.himitsu.restart
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.tryWithSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Login : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val data: Uri = intent?.data
                ?: throw Exception(getString(R.string.mal_login_uri_not_found))
            val codeChallenge = PrefManager.getVal(PrefName.MALCodeChallenge, null as String?)
                ?: throw Exception(getString(R.string.mal_login_code_challenge_not_found))
            val code = data.getQueryParameter("code")
                ?: throw Exception(getString(R.string.mal_login_code_not_present))

            snackString(getString(R.string.logging_in_mal))
            lifecycleScope.launch(Dispatchers.IO) {
                tryWithSuspend(true) {
                    val res = client.post(
                        "https://myanimelist.net/v1/oauth2/token",
                        data = mapOf(
                            "client_id" to clientId,
                            "code" to code,
                            "code_verifier" to codeChallenge,
                            "grant_type" to "authorization_code"
                        )
                    ).parsed<MAL.ResponseToken>()
                    saveResponse(res)
                    MAL.token = res.accessToken
                    MAL.query.getUserData()
                    restart(ComponentName(packageName, SettingsActivity::class.qualifiedName!!))
                }
            }
        } catch (e: Exception) {
            logError(e, snackbar = false)
        }
    }

}