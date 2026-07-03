package com.daybreak

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daybreak.ble.BlePermissions
import com.daybreak.data.SettingsStore
import com.daybreak.export.CsvBuilder
import com.daybreak.export.CsvExporter
import com.daybreak.sync.SyncScheduler
import com.daybreak.ui.InsightsScreen
import com.daybreak.ui.InsightsViewModel
import com.daybreak.ui.OnboardingScreen
import com.daybreak.ui.theme.DaybreakTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full app: a short one-time onboarding (permissions, background running, age), then the
 * Insights screen as the main (and only) page, with the on-demand heart-rate button.
 * Background sync is scheduled by [DaybreakApp]; each foreground resume also triggers a
 * live sync.
 */
class MainActivity : ComponentActivity() {

    private val container get() = (application as DaybreakApp).container
    private val settings: SettingsStore get() = container.settings

    /** Bumped after a permission/battery prompt so the onboarding UI re-reads grant state. */
    private var statusRefresh by mutableIntStateOf(0)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            statusRefresh++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DaybreakTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var onboarded by remember { mutableStateOf(settings.onboardingComplete) }
                    if (!onboarded) {
                        Onboarding(onDone = {
                            settings.onboardingComplete = true
                            SyncScheduler.scheduleDaily(this@MainActivity)
                            SyncScheduler.syncNow(this@MainActivity)
                            onboarded = true
                        })
                    } else {
                        MainApp()
                    }
                }
            }
        }
    }

    @Composable
    private fun Onboarding(onDone: () -> Unit) {
        statusRefresh // read so recomposition tracks refreshes
        OnboardingScreen(
            permissionsGranted = BlePermissions.allGranted(this),
            batteryExempt = isBatteryExempt(),
            initialAge = settings.wearerAge,
            onRequestPermissions = { permissionLauncher.launch(BlePermissions.onboarding()) },
            onRequestBattery = { requestBatteryExemption() },
            onFinish = { age ->
                settings.wearerAge = age
                onDone()
            },
        )
    }

    @Composable
    private fun MainApp() {
        val vm: InsightsViewModel =
            viewModel(factory = InsightsViewModel.factory(container.repository, container.ringClient))
        val data by vm.data.collectAsState()
        val home by vm.home.collectAsState()
        val range by vm.range.collectAsState()
        val aiSummary by vm.aiSummary.collectAsState()
        val liveHeartRate by vm.liveHeartRate.collectAsState()
        val measuring by vm.measuring.collectAsState()

        // Refresh from the ring on every foreground resume so the screen is current.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.open() }

        InsightsScreen(
            data = data,
            home = home,
            selectedRange = range,
            ranges = InsightsViewModel.RANGES,
            onRangeChange = vm::setRange,
            liveHeartRate = liveHeartRate,
            measuring = measuring,
            onMeasureHeartRate = vm::measureHeartRate,
            aiSummaryEnabled = aiSummary,
            onAiSummaryToggle = vm::setAiSummary,
            onExportCsv = { exportCsv() },
        )
    }

    override fun onResume() {
        super.onResume()
        // Re-read permission/battery grant state after returning from a system prompt.
        statusRefresh++
    }

    /** Build the CSV off the main thread, then hand it to the system share sheet. */
    private fun exportCsv() {
        lifecycleScope.launch {
            val csv = withContext(Dispatchers.IO) {
                CsvBuilder.build(container.repository.exportRows())
            }
            val intent = CsvExporter.shareIntent(this@MainActivity, csv)
            startActivity(Intent.createChooser(intent, "Export Daybreak data"))
        }
    }

    private fun isBatteryExempt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryExemption() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }
    }
}
