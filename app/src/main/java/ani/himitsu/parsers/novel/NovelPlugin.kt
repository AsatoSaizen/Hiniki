package ani.himitsu.parsers.novel

sealed class NovelPlugin {
    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        val lang: String,
        val sources: List<AvailablePluginSources>,
        val iconUrl: String,
        val repository: String
    ) : NovelExtension()
}

data class AvailablePluginSources(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
) {
    fun toPluginSourceData(): PluginSourceData {
        return PluginSourceData(
            id = this.id,
            lang = this.lang,
            name = this.name,
        )
    }
}

data class PluginSourceData(
    val id: Long,
    val lang: String,
    val name: String,
) {

    val isMissingInfo: Boolean = name.isBlank() || lang.isBlank()
}
