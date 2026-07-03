package com.daybreak.summary

/** Inputs for generating the daily summary sentence. */
data class SummaryRequest(
    val recovery: Int,
    val sleep: Int,
    val activity: Int,
    val recoveryDriver: String,
    val sleepDriver: String,
)

/** Produces a plain-language daily summary, or null if it can't (caller falls back). */
interface DailySummarizer {
    suspend fun summarize(prompt: String): String?
}

/** Builds the LLM prompt from the day's scores and their dominant drivers (pure). */
object PromptBuilder {
    fun build(req: SummaryRequest): String = buildString {
        append("You are a warm, encouraging wellness companion for an older adult. ")
        append("In ONE short, friendly sentence (max 20 words), tell them how today looks ")
        append("and whether to take it easy or be active. Avoid numbers, avoid medical claims.\n")
        append("Recovery score ${req.recovery}/100 (main factor: ${req.recoveryDriver}). ")
        append("Sleep score ${req.sleep}/100 (main factor: ${req.sleepDriver}). ")
        append("Activity score ${req.activity}/100.\n")
        append("Summary:")
    }
}

/** Chooses the AI summary when enabled and available, otherwise the rule-based fallback (pure). */
object SummarySelector {
    fun choose(aiEnabled: Boolean, aiSummary: String?, ruleBased: String): String =
        if (aiEnabled && !aiSummary.isNullOrBlank()) aiSummary.trim() else ruleBased
}
