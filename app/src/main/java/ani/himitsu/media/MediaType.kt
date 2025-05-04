package ani.himitsu.media

import ani.himitsu.R
import bit.himitsu.nio.Strings.getString

interface Type {
    fun asText(): String
    fun asDisplayText(): String

    companion object {
        fun fromText(string: String): Type? {
            return when (string.lowercase()) {
                "anime" -> MediaType.ANIME
                "manga" -> MediaType.MANGA
                "novel" -> MediaType.NOVEL
                "download" -> AddonType.DOWNLOAD
                else -> { null }
            }
        }
    }

    /** hardcoded string value of enum **/
    val text: String get() = this.asText()
    /** user-facing value for the enum **/
    val string: String get() = this.asDisplayText()
}

enum class MediaType : Type {
    ANIME,
    MANGA,
    NOVEL;

    override fun asText(): String {
        return when (this) {
            ANIME -> "Anime"
            MANGA -> "Manga"
            NOVEL -> "Novel"
        }
    }

    override fun asDisplayText(): String {
        return when (this) {
            ANIME -> getString(R.string.anime)
            MANGA -> getString(R.string.manga)
            NOVEL -> getString(R.string.novel)
        }
    }
}

enum class AddonType : Type {
    DOWNLOAD;

    override fun asText(): String {
        return when (this) {
            DOWNLOAD -> "Download"
        }
    }

    override fun asDisplayText(): String {
        return when (this) {
            DOWNLOAD -> getString(R.string.download)
        }
    }
}
