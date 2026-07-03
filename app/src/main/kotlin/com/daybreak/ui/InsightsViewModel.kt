package com.daybreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daybreak.ble.RingClient
import com.daybreak.data.DaybreakRepository
import com.daybreak.data.HomeData
import com.daybreak.data.InsightsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * View model for the Insights screen — now the app's main (and only) screen, so it also
 * owns what the old home dashboard did: the resume-time ring sync, the on-demand
 * heart-rate measurement, and wearer notes.
 */
class InsightsViewModel(
    private val repository: DaybreakRepository,
    private val ringClient: RingClient,
) : ViewModel() {

    private val _range = MutableStateFlow(DEFAULT_RANGE)
    val range: StateFlow<Int> = _range.asStateFlow()

    private val _data = MutableStateFlow<InsightsData?>(null)
    val data: StateFlow<InsightsData?> = _data.asStateFlow()

    /** Battery, calibration and last-updated info shown in the header. */
    private val _home = MutableStateFlow<HomeData?>(null)
    val home: StateFlow<HomeData?> = _home.asStateFlow()

    private val _aiSummary = MutableStateFlow(repository.aiSummaryEnabled())
    val aiSummary: StateFlow<Boolean> = _aiSummary.asStateFlow()

    /** Live BPM from an on-demand measurement (null when idle). */
    private val _liveHeartRate = MutableStateFlow<Int?>(null)
    val liveHeartRate: StateFlow<Int?> = _liveHeartRate.asStateFlow()

    /** True while a real-time heart-rate measurement is running (disables the button). */
    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    @Volatile
    private var syncing = false

    init {
        viewModelScope.launch { refresh() }
    }

    /** Called on every app resume: show stored data immediately, then sync the ring live. */
    fun open() {
        viewModelScope.launch {
            refresh()
            if (syncing || _measuring.value) return@launch
            syncing = true
            try {
                runCatching { repository.syncFromRing(ringClient) }
                refresh()
            } finally {
                syncing = false
            }
        }
    }

    /**
     * On-demand heart-rate reading via the ring's real-time mode (PRD: measure now).
     * Guards against overlapping with a background/open sync so only one BLE exchange runs.
     */
    fun measureHeartRate() {
        if (syncing || _measuring.value) return
        _measuring.value = true
        _liveHeartRate.value = null
        viewModelScope.launch {
            try {
                if (!ringClient.connect()) return@launch
                ringClient.measureHeartRate { bpm -> _liveHeartRate.value = bpm }
            } catch (_: Throwable) {
                // keep the last live reading (if any); the button re-enables in finally
            } finally {
                ringClient.disconnect()
                _measuring.value = false
            }
        }
    }

    fun setAiSummary(enabled: Boolean) {
        repository.setAiSummaryEnabled(enabled)
        _aiSummary.value = enabled
    }

    fun setRange(days: Int) {
        _range.value = days
        viewModelScope.launch { _data.value = repository.getInsights(days) }
    }

    private suspend fun refresh() {
        _home.value = repository.homeData()
        _data.value = repository.getInsights(_range.value)
    }

    companion object {
        const val DEFAULT_RANGE = 7

        /** Range options shown in the selector: label -> number of days. */
        val RANGES = listOf("Today" to 1, "Week" to 7, "Month" to 30)

        fun factory(repository: DaybreakRepository, ringClient: RingClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    InsightsViewModel(repository, ringClient) as T
            }
    }
}
