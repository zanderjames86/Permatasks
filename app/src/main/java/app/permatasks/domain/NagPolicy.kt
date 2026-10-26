package app.permatasks.domain

/**
 * How hard a task of a given priority nags.
 * After [escalateAfter] ignored reminders the nag becomes a full-screen alarm (if [fullScreen]).
 */
data class NagPolicy(
    val repeatMinutes: Int,
    val fullScreen: Boolean,
    val escalateAfter: Int,
) {
    fun shouldEscalate(ignoredNags: Int): Boolean = fullScreen && ignoredNags >= escalateAfter

    companion object {
        fun default(priority: Priority) = when (priority) {
            Priority.P1 -> NagPolicy(repeatMinutes = 5, fullScreen = true, escalateAfter = 2)
            Priority.P2 -> NagPolicy(repeatMinutes = 15, fullScreen = true, escalateAfter = 4)
            Priority.P3 -> NagPolicy(repeatMinutes = 30, fullScreen = false, escalateAfter = 6)
            Priority.P4 -> NagPolicy(repeatMinutes = 60, fullScreen = false, escalateAfter = 8)
        }
    }
}
