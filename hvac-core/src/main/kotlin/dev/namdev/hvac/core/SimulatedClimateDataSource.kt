package dev.namdev.hvac.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Behaves like a VHAL: a write is applied after a short latency and then
 * reported back as a property change event.
 */
class SimulatedClimateDataSource(
    initial: ClimateState = ClimateState(power = true, ac = true, fanSpeed = 2),
    private val capabilities: ClimateCapabilities = ClimateCapabilities(),
    private val latencyMs: Long = 80,
) : ClimateDataSource {

    private val vehicle = MutableStateFlow(initial)

    /** What the "hardware" currently holds. Useful in tests. */
    val vehicleState: StateFlow<ClimateState> = vehicle

    override val isSimulated: Boolean = true

    override suspend fun connect(): ClimateCapabilities = capabilities

    override fun vehicleUpdates(): Flow<VehicleUpdate> = flow {
        var previous: ClimateState? = null
        vehicle.collect { current ->
            diff(previous, current).forEach { emit(it) }
            previous = current
        }
    }

    override suspend fun execute(command: ClimateCommand) {
        delay(latencyMs)
        vehicle.update { it.apply(command) }
    }

    override fun disconnect() = Unit

    private fun ClimateState.apply(command: ClimateCommand): ClimateState = when (command) {
        is ClimateCommand.SetPower -> copy(power = command.on)
        is ClimateCommand.SetAc -> copy(ac = command.on)
        is ClimateCommand.SetAuto -> copy(auto = command.on)
        is ClimateCommand.SetRecirc -> copy(recirc = command.on)
        is ClimateCommand.SetDefrost -> copy(defrost = command.on)
        is ClimateCommand.SetFanSpeed -> copy(fanSpeed = command.level)
        is ClimateCommand.SetTemperature -> when (command.zone) {
            Zone.DRIVER -> copy(driverTemp = command.celsius)
            Zone.PASSENGER -> copy(passengerTemp = command.celsius)
        }
    }

    private fun diff(old: ClimateState?, new: ClimateState): List<VehicleUpdate> = buildList {
        if (old?.power != new.power) add(VehicleUpdate.Power(new.power))
        if (old?.ac != new.ac) add(VehicleUpdate.Ac(new.ac))
        if (old?.auto != new.auto) add(VehicleUpdate.Auto(new.auto))
        if (old?.recirc != new.recirc) add(VehicleUpdate.Recirc(new.recirc))
        if (old?.defrost != new.defrost) add(VehicleUpdate.Defrost(new.defrost))
        if (old?.fanSpeed != new.fanSpeed) add(VehicleUpdate.FanSpeed(new.fanSpeed))
        if (old?.driverTemp != new.driverTemp) add(VehicleUpdate.Temperature(Zone.DRIVER, new.driverTemp))
        if (old?.passengerTemp != new.passengerTemp) {
            add(VehicleUpdate.Temperature(Zone.PASSENGER, new.passengerTemp))
        }
    }
}
