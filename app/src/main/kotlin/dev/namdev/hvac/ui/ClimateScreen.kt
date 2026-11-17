package dev.namdev.hvac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.namdev.hvac.core.ClimateCapabilities
import dev.namdev.hvac.core.ClimateEvent
import dev.namdev.hvac.core.ClimateState
import dev.namdev.hvac.core.HvacMode
import dev.namdev.hvac.core.StoreState
import dev.namdev.hvac.core.Zone
import java.util.Locale

/** Automotive touch targets: at least 76dp. */
private val TouchTarget = 88.dp

@Composable
fun ClimateScreen(state: StoreState, onIntent: (ClimateEvent.UserIntent) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            StoreState.Connecting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is StoreState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.reason, style = MaterialTheme.typography.headlineSmall)
            }
            is StoreState.Ready -> ClimatePanel(state.climate, state.capabilities, state.simulated, onIntent)
        }
    }
}

@Composable
private fun ClimatePanel(
    climate: ClimateState,
    caps: ClimateCapabilities,
    simulated: Boolean,
    onIntent: (ClimateEvent.UserIntent) -> Unit,
) {
    val enabled = climate.mode != HvacMode.OFF
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Header(climate.mode, simulated)
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TemperatureControl("Driver", climate.driverTemp, enabled) {
                onIntent(ClimateEvent.TemperatureChanged(Zone.DRIVER, it))
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FanControl(climate.fanSpeed, caps.fanMax, enabled) { onIntent(ClimateEvent.FanChanged(it)) }
                ToggleRow(climate, onIntent)
            }
            TemperatureControl("Passenger", climate.passengerTemp, enabled) {
                onIntent(ClimateEvent.TemperatureChanged(Zone.PASSENGER, it))
            }
        }
    }
}

@Composable
private fun Header(mode: HvacMode, simulated: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Climate", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(24.dp))
        Badge(mode.name, MaterialTheme.colorScheme.primaryContainer)
        if (simulated) {
            Spacer(Modifier.width(12.dp))
            Badge("SIMULATED VEHICLE", MaterialTheme.colorScheme.tertiaryContainer)
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun TemperatureControl(label: String, celsius: Float, enabled: Boolean, onStep: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        ControlButton("+", enabled) { onStep(+1) }
        Text(
            text = String.format(Locale.US, "%.1f°", celsius),
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
        )
        ControlButton("−", enabled) { onStep(-1) }
    }
}

@Composable
private fun FanControl(level: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ControlButton("−", enabled) { onChange(-1) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..max) {
                val active = enabled && i <= level
                Box(
                    Modifier
                        .size(width = 18.dp, height = (16 + i * 8).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        ControlButton("+", enabled) { onChange(+1) }
    }
}

@Composable
private fun ToggleRow(climate: ClimateState, onIntent: (ClimateEvent.UserIntent) -> Unit) {
    val on = climate.mode != HvacMode.OFF
    val defrost = climate.mode == HvacMode.DEFROST
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleTile("Power", climate.power, enabled = true) { onIntent(ClimateEvent.PowerToggled) }
        ToggleTile("A/C", climate.ac, enabled = on && !defrost) { onIntent(ClimateEvent.AcToggled) }
        ToggleTile("AUTO", climate.auto, enabled = on && !defrost) { onIntent(ClimateEvent.AutoToggled) }
        ToggleTile("Recirc", climate.recirc, enabled = on && !defrost) { onIntent(ClimateEvent.RecircToggled) }
        ToggleTile("Defrost", climate.defrost, enabled = on) { onIntent(ClimateEvent.DefrostToggled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleTile(label: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (checked && enabled) colors.primary else colors.surfaceVariant,
        contentColor = if (checked && enabled) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier.size(width = 120.dp, height = TouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(TouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 36.sp)
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ClimateScreenPreview() {
    HvacTheme {
        ClimateScreen(
            state = StoreState.Ready(
                climate = ClimateState(power = true, ac = true, auto = true, fanSpeed = 3, driverTemp = 21.5f),
                capabilities = ClimateCapabilities(),
                simulated = true,
            ),
            onIntent = {},
        )
    }
}
