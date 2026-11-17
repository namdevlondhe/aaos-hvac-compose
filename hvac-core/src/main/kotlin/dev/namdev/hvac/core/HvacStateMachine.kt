package dev.namdev.hvac.core

import dev.namdev.hvac.core.ClimateCommand.SetAc
import dev.namdev.hvac.core.ClimateCommand.SetAuto
import dev.namdev.hvac.core.ClimateCommand.SetDefrost
import dev.namdev.hvac.core.ClimateCommand.SetFanSpeed
import dev.namdev.hvac.core.ClimateCommand.SetPower
import dev.namdev.hvac.core.ClimateCommand.SetRecirc
import dev.namdev.hvac.core.ClimateCommand.SetTemperature
import dev.namdev.hvac.core.ClimateEvent.AcToggled
import dev.namdev.hvac.core.ClimateEvent.AutoToggled
import dev.namdev.hvac.core.ClimateEvent.DefrostToggled
import dev.namdev.hvac.core.ClimateEvent.FanChanged
import dev.namdev.hvac.core.ClimateEvent.PowerToggled
import dev.namdev.hvac.core.ClimateEvent.RecircToggled
import dev.namdev.hvac.core.ClimateEvent.TemperatureChanged
import kotlin.math.roundToInt

/** One documented mode change. The list below is the spec; tests check the reducer follows it. */
data class ModeTransition(
    val from: HvacMode,
    val to: HvacMode,
    val trigger: ClimateEvent.UserIntent,
    val label: String,
)

/**
 * Pure, side-effect-free HVAC state machine.
 *
 * `reduce(state, event)` returns the new state plus the property writes the vehicle must receive.
 * Keeping it pure makes every rule unit-testable on the JVM, without an emulator.
 *
 * Rules (guards):
 *  - OFF: only POWER is accepted.
 *  - DEFROST: forces A/C on, fresh air (recirc off), fan at max, AUTO off; A/C, AUTO and recirc are locked.
 *    Leaving DEFROST restores the fan speed and A/C that were set before it.
 *  - AUTO: a manual fan change is an override and drops back to MANUAL.
 *  - Temperatures are clamped to the vehicle range and snapped to its step.
 */
object HvacStateMachine {

    val MODE_TRANSITIONS: List<ModeTransition> = listOf(
        ModeTransition(HvacMode.OFF, HvacMode.MANUAL, PowerToggled, "POWER"),
        ModeTransition(HvacMode.MANUAL, HvacMode.OFF, PowerToggled, "POWER"),
        ModeTransition(HvacMode.AUTO, HvacMode.OFF, PowerToggled, "POWER"),
        ModeTransition(HvacMode.DEFROST, HvacMode.OFF, PowerToggled, "POWER"),
        ModeTransition(HvacMode.MANUAL, HvacMode.AUTO, AutoToggled, "AUTO"),
        ModeTransition(HvacMode.AUTO, HvacMode.MANUAL, AutoToggled, "AUTO"),
        ModeTransition(HvacMode.AUTO, HvacMode.MANUAL, FanChanged(+1), "FAN (manual override)"),
        ModeTransition(HvacMode.MANUAL, HvacMode.DEFROST, DefrostToggled, "DEFROST"),
        ModeTransition(HvacMode.AUTO, HvacMode.DEFROST, DefrostToggled, "DEFROST"),
        ModeTransition(HvacMode.DEFROST, HvacMode.MANUAL, DefrostToggled, "DEFROST (restore fan and A/C)"),
    )

    fun reduce(
        state: ClimateState,
        event: ClimateEvent,
        caps: ClimateCapabilities = ClimateCapabilities(),
    ): Transition = when (event) {
        is ClimateEvent.Vehicle -> Transition(applyVehicleUpdate(state, event.update))
        is ClimateEvent.UserIntent -> when {
            state.mode == HvacMode.OFF && event != PowerToggled -> Transition(state)
            else -> onUserIntent(state, event, caps)
        }
    }

    private fun onUserIntent(
        state: ClimateState,
        intent: ClimateEvent.UserIntent,
        caps: ClimateCapabilities,
    ): Transition {
        val defrosting = state.mode == HvacMode.DEFROST
        return when (intent) {
            PowerToggled -> state.copy(power = !state.power).let { Transition(it, listOf(SetPower(it.power))) }
            AcToggled -> if (defrosting) {
                Transition(state)
            } else {
                Transition(state.copy(ac = !state.ac), listOf(SetAc(!state.ac)))
            }
            AutoToggled -> if (defrosting) {
                Transition(state)
            } else {
                Transition(state.copy(auto = !state.auto), listOf(SetAuto(!state.auto)))
            }
            RecircToggled -> if (defrosting) {
                Transition(state)
            } else {
                Transition(state.copy(recirc = !state.recirc), listOf(SetRecirc(!state.recirc)))
            }
            DefrostToggled -> if (defrosting) exitDefrost(state) else enterDefrost(state, caps)
            is FanChanged -> changeFan(state, intent.delta, caps)
            is TemperatureChanged -> changeTemperature(state, intent, caps)
        }
    }

    private fun enterDefrost(state: ClimateState, caps: ClimateCapabilities): Transition {
        val next = state.copy(
            defrost = true,
            auto = false,
            ac = true,
            recirc = false,
            fanSpeed = caps.fanMax,
            fanBeforeDefrost = state.fanSpeed,
            acBeforeDefrost = state.ac,
        )
        val commands = buildList {
            add(SetDefrost(true))
            if (state.auto) add(SetAuto(false))
            if (!state.ac) add(SetAc(true))
            if (state.recirc) add(SetRecirc(false))
            if (state.fanSpeed != caps.fanMax) add(SetFanSpeed(caps.fanMax))
        }
        return Transition(next, commands)
    }

    private fun exitDefrost(state: ClimateState): Transition {
        val fan = state.fanBeforeDefrost ?: state.fanSpeed
        val ac = state.acBeforeDefrost ?: state.ac
        val next = state.copy(
            defrost = false,
            fanSpeed = fan,
            ac = ac,
            fanBeforeDefrost = null,
            acBeforeDefrost = null,
        )
        val commands = buildList {
            add(SetDefrost(false))
            if (fan != state.fanSpeed) add(SetFanSpeed(fan))
            if (ac != state.ac) add(SetAc(ac))
        }
        return Transition(next, commands)
    }

    private fun changeFan(state: ClimateState, delta: Int, caps: ClimateCapabilities): Transition {
        val level = (state.fanSpeed + delta).coerceIn(caps.fanMin, caps.fanMax)
        if (level == state.fanSpeed && !state.auto) return Transition(state)
        val commands = buildList {
            if (state.auto) add(SetAuto(false)) // manual override leaves AUTO
            if (level != state.fanSpeed) add(SetFanSpeed(level))
        }
        return Transition(state.copy(fanSpeed = level, auto = false), commands)
    }

    private fun changeTemperature(
        state: ClimateState,
        intent: TemperatureChanged,
        caps: ClimateCapabilities,
    ): Transition {
        val current = state.temperature(intent.zone)
        val target = snap(current + intent.steps * caps.tempStep, caps)
        if (target == current) return Transition(state)
        val next = when (intent.zone) {
            Zone.DRIVER -> state.copy(driverTemp = target)
            Zone.PASSENGER -> state.copy(passengerTemp = target)
        }
        return Transition(next, listOf(SetTemperature(intent.zone, target)))
    }

    /** Clamps to the range and rounds to the nearest step (avoids 21.999999 float drift). */
    internal fun snap(value: Float, caps: ClimateCapabilities): Float {
        val clamped = value.coerceIn(caps.tempMin, caps.tempMax)
        return (clamped / caps.tempStep).roundToInt() * caps.tempStep
    }

    private fun applyVehicleUpdate(state: ClimateState, update: VehicleUpdate): ClimateState = when (update) {
        is VehicleUpdate.Power -> state.copy(power = update.on)
        is VehicleUpdate.Ac -> state.copy(ac = update.on)
        is VehicleUpdate.Auto -> state.copy(auto = update.on)
        is VehicleUpdate.Recirc -> state.copy(recirc = update.on)
        is VehicleUpdate.Defrost -> state.copy(defrost = update.on)
        is VehicleUpdate.FanSpeed -> state.copy(fanSpeed = update.level)
        is VehicleUpdate.Temperature -> when (update.zone) {
            Zone.DRIVER -> state.copy(driverTemp = update.celsius)
            Zone.PASSENGER -> state.copy(passengerTemp = update.celsius)
        }
    }
}
