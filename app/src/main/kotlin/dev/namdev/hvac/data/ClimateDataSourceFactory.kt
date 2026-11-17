package dev.namdev.hvac.data

import android.content.Context
import android.content.pm.PackageManager
import dev.namdev.hvac.core.ClimateDataSource
import dev.namdev.hvac.core.SimulatedClimateDataSource

/**
 * Uses the real vehicle when the app runs on AAOS and holds the climate permission;
 * otherwise falls back to the simulator so the HMI is always demoable.
 */
object ClimateDataSourceFactory {

    private const val PERMISSION_CONTROL_CAR_CLIMATE = "android.car.permission.CONTROL_CAR_CLIMATE"

    fun create(context: Context): ClimateDataSource {
        val isAutomotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val hasPermission = context.checkSelfPermission(PERMISSION_CONTROL_CAR_CLIMATE) ==
            PackageManager.PERMISSION_GRANTED
        return if (isAutomotive && hasPermission) {
            CarClimateDataSource(context.applicationContext)
        } else {
            SimulatedClimateDataSource()
        }
    }
}
