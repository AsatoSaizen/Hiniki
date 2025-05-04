package ani.himitsu

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import ani.himitsu.util.Logger

fun openLinkInBrowser(link: String?) {
    link?.let {
        try {
            currContext().startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    data = Uri.parse(link)
                    selector = Intent(Intent.ACTION_VIEW).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        data = Uri.fromParts("http", "", null)
                    }
                }
            )
        } catch (_: ActivityNotFoundException) {
            snackString(R.string.no_intent_apps)
        } catch (e: Exception) {
            Logger.log(e)
        }
    }
}

fun openLinkInYouTube(link: String?) {
    link?.let {
        try {
            currContext().startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    data = Uri.parse(link)
                    setPackage("com.google.android.youtube")
                }
            )
        } catch (_: ActivityNotFoundException) {
            openLinkInBrowser(link)
        } catch (e: Exception) {
            Logger.log(e)
        }
    }
}

fun openInGooglePlay(packageName: String) {
    try {
        currContext().startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            )
        )
    } catch (_: ActivityNotFoundException) {
        currContext().startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
        )
    }
}

val String.isTorrentLink: Boolean get() {
    return startsWith("magnet:") || endsWith(".torrent")
}