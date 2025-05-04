package ani.himitsu.media

import ani.himitsu.media.cereal.Media

val Media.progressDialog: String get() = "${id}_progressDialog"
val Media.saveProgress: String get() = "${id}_save_progress"
fun Media.maxValue(number: String): String {
    return "${id}_${number}_max"
}

val Media.currentEpisode: String get() = "${id}_current_ep"
val Media.fullscreenInt: String get() = "${id}_fullscreenInt"
val Media.speedValue: String get() = "${id}_speed"
val Media.subLanguage: String get() = "subLang_${id}"
fun Media.subtitles(episodeNumber: String): String {
    return "${id}_${episodeNumber}_subtitles"
}

val Media.currentChapter: String get() = "${id}_current_chp"
val Media.currentSettings: String get() = "${id}_current_settings"