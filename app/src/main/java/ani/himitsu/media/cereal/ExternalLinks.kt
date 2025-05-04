package ani.himitsu.media.cereal

import java.io.Serializable

data class ExternalLinks(
    var amazon: String? = null,
    var crunchy: String? = null,
    var disney: String? = null,
    var hidive: String? = null,
    var hulu: String? = null,
    var max: String? = null,
    var netflix: String? = null,
    var tubi: String? = null,
    var vrv: String? = null,
    var adn: String? = null,
    var youtube: String? = null
) : Serializable