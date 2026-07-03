package com.daybreak.summary

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device LLM summary via MediaPipe LLM Inference (PRD §8.3 P2). The app is fully
 * offline and the model is large, so it is **not** bundled or downloaded — the maintainer
 * sideloads a MediaPipe-compatible model (e.g. a 4-bit Gemma `.task`) to:
 *
 *   Android/data/com.daybreak/files/llm/model.task
 *
 * If the file is missing or inference fails, [summarize] returns null and the caller
 * keeps the rule-based sentence. Engine init is lazy and one-shot.
 */
class LlmSummarizer(private val context: Context) : DailySummarizer {

    @Volatile
    private var engine: LlmInference? = null
    @Volatile
    private var triedInit = false

    private fun modelFile(): File = File(context.getExternalFilesDir(null), "llm/model.task")

    @Synchronized
    private fun ensureEngine(): LlmInference? {
        engine?.let { return it }
        if (triedInit) return null
        triedInit = true

        val file = modelFile()
        if (!file.exists()) {
            Log.i(TAG, "AI summary: no model at ${file.absolutePath}; using rule-based fallback")
            return null
        }
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(256)
                .build()
            LlmInference.createFromOptions(context, options).also { engine = it }
        } catch (t: Throwable) {
            Log.w(TAG, "AI summary: engine init failed", t)
            null
        }
    }

    override suspend fun summarize(prompt: String): String? = withContext(Dispatchers.Default) {
        val llm = ensureEngine() ?: return@withContext null
        try {
            llm.generateResponse(prompt)?.trim()?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "AI summary: generation failed", t)
            null
        }
    }

    private companion object {
        const val TAG = "LlmSummarizer"
    }
}
