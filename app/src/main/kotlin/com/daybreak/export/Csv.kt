package com.daybreak.export

/** One exported day: raw nightly metrics plus computed scores (null until calibrated). */
data class ExportRow(
    val date: String,
    val recovery: Int?,
    val sleep: Int?,
    val activity: Int?,
    val totalSleepMin: Int,
    val deepMin: Int,
    val remMin: Int,
    val lightMin: Int,
    val awakeMin: Int,
    val steps: Int,
    val restingHr: Int,
)

/** Builds a local-only CSV from export rows (pure, so it's unit-testable). */
object CsvBuilder {
    private val HEADER = listOf(
        "date", "recovery", "sleep", "activity",
        "total_sleep_min", "deep_min", "rem_min", "light_min", "awake_min",
        "steps", "resting_hr",
    )

    fun build(rows: List<ExportRow>): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append('\n')
        for (r in rows) {
            sb.append(
                listOf(
                    r.date, r.recovery.cell(), r.sleep.cell(), r.activity.cell(),
                    r.totalSleepMin, r.deepMin, r.remMin, r.lightMin, r.awakeMin,
                    r.steps, r.restingHr,
                ).joinToString(",") { escape(it.toString()) },
            ).append('\n')
        }
        return sb.toString()
    }

    private fun Int?.cell(): String = this?.toString() ?: ""

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
