package ani.himitsu.connections

object Status {
    const val FINISHED = "FINISHED"
    const val ONGOING = "ONGOING"
    const val RELEASING = "RELEASING"
    const val UPCOMING = "UPCOMING"
    const val UNRELEASED = "NOT YET RELEASED"
    const val CANCELLED = "CANCELLED"
    const val HIATUS = "HIATUS"
    const val UNKNOWN = "UNKNOWN"

    fun isReleasing(status : String?) : Boolean {
        return status == ONGOING || status == RELEASING
    }
}