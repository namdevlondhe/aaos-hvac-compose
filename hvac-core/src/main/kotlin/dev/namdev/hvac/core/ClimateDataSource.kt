package dev.namdev.hvac.core

import kotlinx.coroutines.flow.Flow

/**
 * Port to the vehicle. The app provides two adapters:
 *  - CarClimateDataSource: real VHAL properties through CarPropertyManager
 *  - [SimulatedClimateDataSource]: in-memory vehicle for phones, CI and demos
 */
interface ClimateDataSource {
    val isSimulated: Boolean

    suspend fun connect(): ClimateCapabilities

    /** Current values on subscribe, then every change. */
    fun vehicleUpdates(): Flow<VehicleUpdate>

    suspend fun execute(command: ClimateCommand)

    fun disconnect()
}
