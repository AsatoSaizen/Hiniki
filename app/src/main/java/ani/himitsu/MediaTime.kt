package ani.himitsu

import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import ani.himitsu.connections.Status
import ani.himitsu.databinding.ItemCountDownBinding
import ani.himitsu.media.cereal.Genre
import ani.himitsu.media.cereal.Media
import ani.himitsu.util.CountUpTimer
import bit.himitsu.bakaupdates.MangaUpdates
import bit.himitsu.nio.Strings.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext
import java.text.SimpleDateFormat
import java.util.Locale

fun Media.setAnimeTimer(view: ViewGroup) {
    if (anime?.nextAiringEpisode != null && anime.nextAiringEpisodeTime != null
        && (anime.nextAiringEpisodeTime!! - System.currentTimeMillis() / 1000) <= 86400 * 28
    ) {
        val v = ItemCountDownBinding.inflate(LayoutInflater.from(view.context), view, false)
        view.addView(v.root, 0)
        v.mediaCountdownText.text =
            getString(
                R.string.episode_release_countdown,
                anime.nextAiringEpisode!!
            )

        object : CountDownTimer(
            (anime.nextAiringEpisodeTime!! + 10000) * 1000 - System.currentTimeMillis(),
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val a = millisUntilFinished / 1000
                v.mediaCountdown.text = currActivity()?.getString(
                    R.string.time_format,
                    a / 86400,
                    a % 86400 / 3600,
                    a % 86400 % 3600 / 60,
                    a % 86400 % 3600 % 60
                )
            }

            override fun onFinish() {
                v.mediaCountdownContainer.visibility = View.GONE
                snackString(R.string.congrats_vro)
            }
        }.start()
    }
}

fun Media.setMangaTimer(view: ViewGroup) {
    if (!Status.isReleasing(status) && status != Status.HIATUS) return
    CoroutineScope(Dispatchers.IO).launch {
        with(MangaUpdates()) {
            findLatestRelease(this@setMangaTimer)?.let {
                var timestamp: Long = it.metadata.series.lastUpdated!!.timestamp

                val latestChapter = getSeries(it)?.let { series ->
                    timestamp = series.lastUpdated?.timestamp ?: timestamp
                    currActivity()?.getString(R.string.chapter_number, series.latestChapter)
                } ?: {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                    timestamp = dateFormat.parse(it.record.releaseDate)?.time ?: timestamp
                    getLatestChapter(view.context, it)
                }
                val predicted = if (Status.isReleasing(status))
                    predictRelease(this@setMangaTimer, it.record.title, timestamp * 1000)
                else null
                val timeSince = (System.currentTimeMillis() - (timestamp * 1000)) / 1000

                withUIContext {
                    val v = ItemCountDownBinding.inflate(
                        LayoutInflater.from(view.context), view, false
                    )
                    view.addView(v.root, 0)
                    v.mediaCountdownText.text =
                        currActivity()?.getString(R.string.chapter_release_timeout, latestChapter)

                    predicted?.let { time ->
                        v.mediaPredication.text = if (System.currentTimeMillis() > time) {
                            currActivity()?.getString(R.string.chapter_delayed)
                        } else {
                            currActivity()?.getString(
                                R.string.chapter_predication,
                                SimpleDateFormat.getDateTimeInstance().format(time)
                                    .substringBeforeLast(' ').substringBeforeLast(' ')
                                // SimpleDateFormat parses MMMM to MO5 for May. This is a workaround
                            )
                        }
                        v.mediaPredication.isVisible = true
                    }

                    object : CountUpTimer(86400000) {
                        override fun onTick(second: Int) {
                            val a = second + timeSince
                            v.mediaCountdown.text = currActivity()?.getString(
                                R.string.time_format,
                                a / 86400,
                                a % 86400 / 3600,
                                a % 86400 % 3600 / 60,
                                a % 86400 % 3600 % 60
                            )
                        }

                        override fun onFinish() {
                            // The legend will never die.
                        }
                    }.start()
                }
            }
        }
    }
}

fun ViewGroup.displayTimer(media: Media) {
    when {
        media.anime != null -> media.setAnimeTimer(this)
        media.format == "MANGA" || media.format == "ONE_SHOT" -> media.setMangaTimer(this)
        else -> {} // No timer yet
    }
}

fun MutableMap<String, Genre>.checkGenreTime(genre: String): Boolean {
    if (containsKey(genre))
        return (System.currentTimeMillis() - get(genre)!!.time) >= (1000 * 60 * 60 * 24 * 7)
    return true
}