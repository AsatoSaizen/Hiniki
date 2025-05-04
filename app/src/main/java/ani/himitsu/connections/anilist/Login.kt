package ani.himitsu.connections.anilist

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ani.himitsu.logError
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import bit.himitsu.content.reboot
import ani.himitsu.R

class Login : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data: Uri? = intent?.data
        try {
            AniList.token = Regex("""(?<=access_token=).+(?=&token_type)""")
                .find(data.toString())?.value
            PrefManager.setVal(PrefName.AnilistToken, AniList.token ?: "")
        } catch (e: Exception) {
            logError(e)
            snackString(R.string.ani_login_failed)
        }
        AniList.initialized = false
        reboot()
    }
}
