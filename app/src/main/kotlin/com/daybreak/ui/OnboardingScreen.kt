package com.daybreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Maintainer onboarding (PRD §8.5): a short wizard to grant Bluetooth permissions,
 * allow background running (battery exemption), and set the wearer's age. Status is
 * hoisted in by the Activity so each step reflects the live grant state.
 */
@Composable
fun OnboardingScreen(
    permissionsGranted: Boolean,
    batteryExempt: Boolean,
    initialAge: Int,
    onRequestPermissions: () -> Unit,
    onRequestBattery: () -> Unit,
    onFinish: (Int) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var age by remember { mutableIntStateOf(initialAge) }
    val lastStep = 3

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            0 -> Welcome()
            1 -> PermissionStep(permissionsGranted, onRequestPermissions)
            2 -> BatteryStep(batteryExempt, onRequestBattery)
            else -> AgeStep(age, onAgeChange = { age = it })
        }

        Spacer(Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (step == 0) Arrangement.Center else Arrangement.SpaceBetween,
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) { Text("Back") }
            }
            Button(onClick = { if (step < lastStep) step++ else onFinish(age) }) {
                Text(if (step < lastStep) "Next" else "Finish")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Step ${step + 1} of ${lastStep + 1}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Welcome() {
    Title("Welcome to Daybreak")
    Body("Let's set up the ring. This takes about a minute and only needs to be done once.")
}

@Composable
private fun PermissionStep(granted: Boolean, onRequest: () -> Unit) {
    Title("Bluetooth access")
    Body("Daybreak needs Bluetooth to talk to the ring. It never uses the internet.")
    Spacer(Modifier.height(20.dp))
    if (granted) {
        StatusGranted("Bluetooth permission granted")
    } else {
        Button(onClick = onRequest) { Text("Grant Bluetooth permission") }
    }
}

@Composable
private fun BatteryStep(exempt: Boolean, onRequest: () -> Unit) {
    Title("Keep syncing in the background")
    Body("Allow Daybreak to run in the background so the night is ready each morning without opening the app.")
    Spacer(Modifier.height(20.dp))
    if (exempt) {
        StatusGranted("Background running allowed")
    } else {
        Button(onClick = onRequest) { Text("Allow background running") }
    }
}

@Composable
private fun AgeStep(age: Int, onAgeChange: (Int) -> Unit) {
    Title("Who's wearing the ring?")
    Body("The wearer's age sets a healthy sleep target. You can change this later.")
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        OutlinedButton(onClick = { if (age > 1) onAgeChange(age - 1) }) { Text("−", fontSize = 22.sp) }
        Text("$age", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { if (age < 120) onAgeChange(age + 1) }) { Text("+", fontSize = 22.sp) }
    }
    Spacer(Modifier.height(8.dp))
    Text("years old", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusGranted(text: String) {
    Text("✓ $text", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
}
