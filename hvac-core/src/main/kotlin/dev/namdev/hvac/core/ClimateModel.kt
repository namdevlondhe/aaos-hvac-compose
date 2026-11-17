package dev.namdev.hvac.core

enum class Zone { DRIVER, PASSENGER }

enum class HvacMode { OFF, MANUAL, AUTO, DEFROST }

/** Ranges reported by the vehicle (or sensible defaults for the simulator). */
data class ClimateCapabilities(
    val tempMin: Float = 16f,
    val tempMax: Float = 28f,
    val tempStep: Float = 0.5f,
    val fanMin: Int = 1,
    val fanMax: Int = 6,
)

/** Everything the HMI shows. [mode] is derived, so the flags and the mode can never disagree. */
data class ClimateState(
    val power: Boolean = false,
    val ac: Boolean = false,
    val auto: Boolean = false,
    val recirc: Boolean = false,
    val defrost: Boolean = false,
    val fanSpeed: Int = 1,
    val driverTemp: Float = 22f,
    val passengerTemp: Float = 22f,
    /** Remembered on entering DEFROST so we can restore the cabin settings on exit. */
    val fanBeforeDefrost: Int? = null,
    val acBeforeDefrost: Boolean? = null,
) {
    val mode: HvacMode
        get() = when {
            !power -> HvacMode.OFF
            defrost -> HvacMode.DEFROST
            auto -> HvacMode.AUTO
            else -> HvacMode.MANUAL
        }

    fun temperature(zone: Zone): Float = if (zone == Zone.DRIVER) driverTemp else passengerTemp
}

/** Inputs to the state machine: user intents from the HMI, or updates from the vehicle. */
sealed interface ClimateEvent {
    sealed interface UserIntent : ClimateEvent

    data object PowerToggled : UserIntent
    data object AcToggled : UserIntent
    data object AutoToggled : UserIntent
    data object RecircToggled : UserIntent
    data object DefrostToggled : UserIntent
    data class FanChanged(val delta: Int) : UserIntent
    data class TemperatureChanged(val zone: Zone, val steps: Int) : UserIntent

    data class Vehicle(val update: VehicleUpdate) : ClimateEvent
}

/** A single property change reported by the vehicle (one VHAL property event). */
sealed interface VehicleUpdate {
    data class Power(val on: Boolean) : VehicleUpdate
    data class Ac(val on: Boolean) : VehicleUpdate
    data class Auto(val on: Boolean) : VehicleUpdate
    data class Recirc(val on: Boolean) : VehicleUpdate
    data class Defrost(val on: Boolean) : VehicleUpdate
    data class FanSpeed(val level: Int) : VehicleUpdate
    data class Temperature(val zone: Zone, val celsius: Float) : VehicleUpdate
}

/** Outputs of the state machine: property writes to send to the vehicle. */
sealed interface ClimateCommand {
    data class SetPower(val on: Boolean) : ClimateCommand
    data class SetAc(val on: Boolean) : ClimateCommand
    data class SetAuto(val on: Boolean) : ClimateCommand
    data class SetRecirc(val on: Boolean) : ClimateCommand
    data class SetDefrost(val on: Boolean) : ClimateCommand
    data class SetFanSpeed(val level: Int) : ClimateCommand
    data class SetTemperature(val zone: Zone, val celsius: Float) : ClimateCommand
}

data class Transition(val state: ClimateState, val commands: List<ClimateCommand> = emptyList())
