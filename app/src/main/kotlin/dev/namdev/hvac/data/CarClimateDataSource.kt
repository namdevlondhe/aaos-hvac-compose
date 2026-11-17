package dev.namdev.hvac.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import dev.namdev.hvac.core.ClimateCapabilities
import dev.namdev.hvac.core.ClimateCommand
import dev.namdev.hvac.core.ClimateDataSource
import dev.namdev.hvac.core.VehicleUpdate
import dev.namdev.hvac.core.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Real vehicle adapter: reads and writes VHAL HVAC properties through [CarPropertyManager].
 *
 * Area IDs are not hard-coded. They are discovered from each property's CarPropertyConfig,
 * so the same code works on the emulator and on different seat/zone layouts.
 */
@Suppress("TooManyFunctions")
class CarClimateDataSource(private val context: Context) : ClimateDataSource {

    override val isSimulated: Boolean = false

    private var car: Car? = null
    private lateinit var properties: CarPropertyManager

    private var driverTempArea: Int? = null
    private var passengerTempArea: Int? = null
    private var defrostArea: Int? = null

    override suspend fun connect(): ClimateCapabilities = withContext(Dispatchers.IO) {
        val connected = Car.createCar(context) ?: error("Car service is not available")
        car = connected
        properties = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

        val tempAreas = areasOf(VehiclePropertyIds.HVAC_TEMPERATURE_SET)
        driverTempArea = tempAreas.firstOrNull { it and Area.SEAT_ROW_1_LEFT != 0 }
        passengerTempArea = tempAreas.firstOrNull { it and Area.SEAT_ROW_1_RIGHT != 0 }
        defrostArea = areasOf(VehiclePropertyIds.HVAC_DEFROSTER)
            .firstOrNull { it and Area.WINDOW_FRONT_WINDSHIELD != 0 }

        readCapabilities()
    }

    override fun vehicleUpdates(): Flow<VehicleUpdate> = callbackFlow {
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                value.toUpdate()?.let { trySend(it) }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "Property error: ${VehiclePropertyIds.toString(propertyId)} area=0x${areaId.toString(HEX)}")
            }
        }
        // Registering for ON_CHANGE also delivers the current value straight away.
        WATCHED_PROPERTIES
            .filter { properties.getCarPropertyConfig(it) != null }
            .forEach { properties.registerCallback(callback, it, CarPropertyManager.SENSOR_RATE_ONCHANGE) }

        awaitClose { properties.unregisterCallback(callback) }
    }

    override suspend fun execute(command: ClimateCommand) {
        withContext(Dispatchers.IO) {
            try {
                write(command)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Rejected by vehicle: $command", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing CONTROL_CAR_CLIMATE permission for $command", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Property not available right now: $command", e)
            }
        }
    }

    override fun disconnect() {
        car?.disconnect()
        car = null
    }

    private fun write(command: ClimateCommand) {
        when (command) {
            is ClimateCommand.SetPower -> setBooleanInAllAreas(VehiclePropertyIds.HVAC_POWER_ON, command.on)
            is ClimateCommand.SetAc -> setBooleanInAllAreas(VehiclePropertyIds.HVAC_AC_ON, command.on)
            is ClimateCommand.SetAuto -> setBooleanInAllAreas(VehiclePropertyIds.HVAC_AUTO_ON, command.on)
            is ClimateCommand.SetRecirc -> setBooleanInAllAreas(VehiclePropertyIds.HVAC_RECIRC_ON, command.on)
            is ClimateCommand.SetFanSpeed -> areasOf(VehiclePropertyIds.HVAC_FAN_SPEED).forEach {
                properties.setIntProperty(VehiclePropertyIds.HVAC_FAN_SPEED, it, command.level)
            }
            is ClimateCommand.SetDefrost -> defrostArea?.let {
                properties.setBooleanProperty(VehiclePropertyIds.HVAC_DEFROSTER, it, command.on)
            }
            is ClimateCommand.SetTemperature -> areaFor(command.zone)?.let {
                properties.setFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, it, command.celsius)
            }
        }
    }

    private fun CarPropertyValue<*>.toUpdate(): VehicleUpdate? {
        if (status != CarPropertyValue.STATUS_AVAILABLE) return null
        val raw = value ?: return null
        val isPrimaryArea = areaId == areasOf(propertyId).firstOrNull()
        return when (propertyId) {
            VehiclePropertyIds.HVAC_POWER_ON -> VehicleUpdate.Power(raw as Boolean).takeIf { isPrimaryArea }
            VehiclePropertyIds.HVAC_AC_ON -> VehicleUpdate.Ac(raw as Boolean).takeIf { isPrimaryArea }
            VehiclePropertyIds.HVAC_AUTO_ON -> VehicleUpdate.Auto(raw as Boolean).takeIf { isPrimaryArea }
            VehiclePropertyIds.HVAC_RECIRC_ON -> VehicleUpdate.Recirc(raw as Boolean).takeIf { isPrimaryArea }
            VehiclePropertyIds.HVAC_FAN_SPEED -> VehicleUpdate.FanSpeed(raw as Int).takeIf { isPrimaryArea }
            VehiclePropertyIds.HVAC_DEFROSTER ->
                VehicleUpdate.Defrost(raw as Boolean).takeIf { areaId == defrostArea }
            VehiclePropertyIds.HVAC_TEMPERATURE_SET -> when (areaId) {
                driverTempArea -> VehicleUpdate.Temperature(Zone.DRIVER, raw as Float)
                passengerTempArea -> VehicleUpdate.Temperature(Zone.PASSENGER, raw as Float)
                else -> null
            }
            else -> null
        }
    }

    private fun readCapabilities(): ClimateCapabilities {
        val defaults = ClimateCapabilities()
        val fanConfig = properties.getCarPropertyConfig(VehiclePropertyIds.HVAC_FAN_SPEED)
        val fanArea = fanConfig?.areaIds?.firstOrNull()
        val fanMin = fanArea?.let { fanConfig.getMinValue(it) as? Int } ?: defaults.fanMin
        val fanMax = fanArea?.let { fanConfig.getMaxValue(it) as? Int } ?: defaults.fanMax

        // HVAC_TEMPERATURE_SET configArray: [minC*10, maxC*10, stepC*10, minF*10, maxF*10, stepF*10]
        val tempConfig = properties.getCarPropertyConfig(VehiclePropertyIds.HVAC_TEMPERATURE_SET)?.configArray
        return if (tempConfig != null && tempConfig.size >= CELSIUS_CONFIG_SIZE) {
            ClimateCapabilities(
                tempMin = tempConfig[0] / TENTHS,
                tempMax = tempConfig[1] / TENTHS,
                tempStep = tempConfig[2] / TENTHS,
                fanMin = fanMin,
                fanMax = fanMax,
            )
        } else {
            defaults.copy(fanMin = fanMin, fanMax = fanMax)
        }
    }

    private fun setBooleanInAllAreas(propertyId: Int, value: Boolean) =
        areasOf(propertyId).forEach { properties.setBooleanProperty(propertyId, it, value) }

    private fun areasOf(propertyId: Int): IntArray =
        properties.getCarPropertyConfig(propertyId)?.areaIds ?: IntArray(0)

    private fun areaFor(zone: Zone): Int? = if (zone == Zone.DRIVER) driverTempArea else passengerTempArea

    /** VHAL area bit flags (VehicleAreaSeat / VehicleAreaWindow). */
    private object Area {
        const val SEAT_ROW_1_LEFT = 0x0001
        const val SEAT_ROW_1_RIGHT = 0x0004
        const val WINDOW_FRONT_WINDSHIELD = 0x0001
    }

    private companion object {
        const val TAG = "CarClimateDataSource"
        const val HEX = 16
        const val TENTHS = 10f
        const val CELSIUS_CONFIG_SIZE = 3

        val WATCHED_PROPERTIES = listOf(
            VehiclePropertyIds.HVAC_POWER_ON,
            VehiclePropertyIds.HVAC_AC_ON,
            VehiclePropertyIds.HVAC_AUTO_ON,
            VehiclePropertyIds.HVAC_RECIRC_ON,
            VehiclePropertyIds.HVAC_FAN_SPEED,
            VehiclePropertyIds.HVAC_DEFROSTER,
            VehiclePropertyIds.HVAC_TEMPERATURE_SET,
        )
    }
}
