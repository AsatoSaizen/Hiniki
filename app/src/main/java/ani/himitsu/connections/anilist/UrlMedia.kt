package ani.himitsu.connections.anilist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ani.himitsu.loadMedia
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.openLinkInBrowser
import ani.himitsu.profile.ProfileActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable
import kotlin.text.toIntOrNull

class UrlMedia : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data: Uri? = intent?.data
        val type = data?.pathSegments?.getOrNull(0)
        var id: Int = intent?.extras?.getInt("media", 0) ?: 0
        when (type) {
            "user" -> {
                val username = data.pathSegments?.getOrNull(1)
                username?.toIntOrNull()?.let {
                    startActivity(
                        Intent(this@UrlMedia, ProfileActivity::class.java)
                            .putExtra("userId", it)
                    )
                } ?: startActivity(
                    Intent(this@UrlMedia, ProfileActivity::class.java)
                        .putExtra("username", username)
                )
            }
            "anime", "manga" -> {
                var isMAL = false
                var continueMedia = true
                if (id == 0) {
                    id = data.pathSegments?.getOrNull(1)?.toIntOrNull() ?: 0
                    isMAL = data.host == "myanimelist.net"
                    continueMedia = false
                } else loadMedia = id

                lifecycleScope.launch(Dispatchers.IO) {
                    if (id != 0) {
                        AniList.query.getMedia(id, isMAL, type)?.let {
                            startActivity(Intent(
                                this@UrlMedia, MediaDetailsActivity::class.java
                            ).putExtra("media", it.apply {
                                cameFromContinue = continueMedia
                            } as Serializable))
                        } ?: openLinkInBrowser(data.toString())
                    }
                }
            }
            else -> {
                openLinkInBrowser(data.toString())
            }
        }
        finish()
    }
}