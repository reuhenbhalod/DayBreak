package com.daybreak.data

/** Maps tagged dates onto trend-chart positions so notes can be marked on the chart (pure). */
object TagMarks {
    fun indices(trendDates: List<String>, taggedDates: Set<String>): Set<Int> =
        trendDates.indices.filter { trendDates[it] in taggedDates }.toSet()
}
