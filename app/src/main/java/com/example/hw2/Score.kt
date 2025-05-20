package com.example.hw2

// Score.kt
data class Score(
    val coins: Int,
    val seconds: Int,
    val lat: Double,
    val lon: Double
) {
    val value get() = coins * seconds
}
// ScoreManager.kt
object ScoreManager {
    private const val MAX_ENTRIES = 10

    private val buckets = mutableMapOf(
        "buttons_slow" to mutableListOf<Score>(),
        "buttons_fast" to mutableListOf<Score>(),
        "sensors"      to mutableListOf<Score>()
    )

    /**
     * Add a new score and trim each list to the top 10 highest (by coins*seconds).
     */
    fun addScore(version: String, score: Score) {
        val list = buckets.getOrPut(version) { mutableListOf() }
        list += score
        // sort descending by computed value
        list.sortByDescending { it.value }
        // drop any beyond the MAX_ENTRIES
        if (list.size > MAX_ENTRIES) {
            list.subList(MAX_ENTRIES, list.size).clear()
        }
    }

    /**
     * Return a snapshot of the current top scores (up to 10) for the given version.
     */
    fun topScores(version: String): List<Score> =
        buckets[version]?.toList() ?: emptyList()
}
