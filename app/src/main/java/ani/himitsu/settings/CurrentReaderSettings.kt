package ani.himitsu.settings

import ani.himitsu.connections.discord.Discord
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import java.io.Serializable

val CurrentReaderSettings.bottomTopPaged
    get() = layout == CurrentReaderSettings.Layouts.PAGED
            && direction == CurrentReaderSettings.Directions.BOTTOM_TO_TOP

val CurrentReaderSettings.directionRLBT
    get() = direction == CurrentReaderSettings.Directions.RIGHT_TO_LEFT
            || direction == CurrentReaderSettings.Directions.BOTTOM_TO_TOP

val CurrentReaderSettings.directionHorz
    get() = direction == CurrentReaderSettings.Directions.LEFT_TO_RIGHT
            || direction == CurrentReaderSettings.Directions.RIGHT_TO_LEFT

val CurrentReaderSettings.directionVert
    get() = direction == CurrentReaderSettings.Directions.TOP_TO_BOTTOM
            || direction == CurrentReaderSettings.Directions.BOTTOM_TO_TOP

data class CurrentReaderSettings(
    var direction: Directions = Directions[PrefManager.getVal(PrefName.Direction)]
        ?: Directions.TOP_TO_BOTTOM,
    var layout: Layouts = Layouts[PrefManager.getVal(PrefName.LayoutReader)]
        ?: Layouts.CONTINUOUS,
    var dualPageMode: DualPageModes = DualPageModes[PrefManager.getVal(PrefName.DualPageModeReader)]
        ?: DualPageModes.Automatic,
    var overScrollMode: Boolean = PrefManager.getVal(PrefName.OverScrollMode),
    var trueColors: Boolean = PrefManager.getVal(PrefName.TrueColors),
    var hardColors: Boolean = PrefManager.getVal(PrefName.HardColors),
    var photoNegative: Boolean = PrefManager.getVal(PrefName.PhotoNegative),
    var autoNegative: Boolean = PrefManager.getVal(PrefName.AutoNegative),
    var rotation: Boolean = PrefManager.getVal(PrefName.Rotation),
    var padding: Boolean = PrefManager.getVal(PrefName.Padding),
    var pageTurn: Boolean = PrefManager.getVal(PrefName.PageTurn),
    var hideScrollBar: Boolean = PrefManager.getVal(PrefName.HideScrollBar),
    var hidePageNumbers: Boolean = PrefManager.getVal(PrefName.HidePageNumbers),
    var horizontalScrollBar: Boolean = PrefManager.getVal(PrefName.HorizontalScrollBar),
    var keepScreenOn: Boolean = PrefManager.getVal(PrefName.KeepScreenOn),
    var volumeButtons: Boolean = PrefManager.getVal(PrefName.VolumeButtonsReader),
    var wrapImages: Boolean = PrefManager.getVal(PrefName.WrapImages),
//    var longClickImage: Boolean = PrefManager.getVal(PrefName.LongClickImage),
    var cropBorders: Boolean = PrefManager.getVal(PrefName.CropBorders),
    var cropBorderThreshold: Int = PrefManager.getVal(PrefName.CropBorderThreshold),
    var discordRPC: Boolean = Discord.getSavedToken()
) : Serializable {

    enum class Directions {
        TOP_TO_BOTTOM,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        LEFT_TO_RIGHT;

        companion object {
            operator fun get(value: Int) = entries.firstOrNull { it.ordinal == value }
        }
    }

    enum class Layouts {
        PAGED,
        CONTINUOUS_PAGED,
        CONTINUOUS;

        companion object {
            operator fun get(value: Int) = entries.firstOrNull { it.ordinal == value }
        }
    }

    enum class DualPageModes {
        No, Automatic, Force;

        companion object {
            operator fun get(value: Int) = entries.firstOrNull { it.ordinal == value }
        }
    }
}

