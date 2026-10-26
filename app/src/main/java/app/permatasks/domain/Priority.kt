package app.permatasks.domain

/** P1 is the most urgent, matching Todoist. */
enum class Priority(val level: Int, val label: String) {
    P1(1, "Urgent"),
    P2(2, "High"),
    P3(3, "Medium"),
    P4(4, "Normal");

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: P4
    }
}
