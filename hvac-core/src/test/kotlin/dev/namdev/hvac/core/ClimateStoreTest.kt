package dev.namdev.hvac.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClimateStoreTest {

    @Test
    fun `initial vehicle values reach the UI state`() = runTest {
        val vehicle = SimulatedClimateDataSource(initial = ClimateState(power = true, fanSpeed = 4, driverTemp = 20f))
        val store = ClimateStore(vehicle, backgroundScope).apply { start() }

        runCurrent()

        val ready = store.state.value as StoreState.Ready
        assertEquals(4, ready.climate.fanSpeed)
        assertEquals(20f, ready.climate.driverTemp)
        assertTrue(ready.simulated)
    }

    @Test
    fun `user intent is written to the vehicle and echoed back`() = runTest {
        val vehicle = SimulatedClimateDataSource(initial = ClimateState(power = true, fanSpeed = 2))
        val store = ClimateStore(vehicle, backgroundScope).apply { start() }
        runCurrent()

        store.dispatch(ClimateEvent.DefrostToggled)
        // The writes run in backgroundScope behind the simulated VHAL latency, and
        // advanceUntilIdle() ignores background work, so move virtual time forward explicitly.
        advanceTimeBy(1_000)
        runCurrent()

        val vehicleNow = vehicle.vehicleState.value
        assertTrue(vehicleNow.defrost)
        assertTrue(vehicleNow.ac)
        assertEquals(6, vehicleNow.fanSpeed)
        assertEquals(HvacMode.DEFROST, (store.state.value as StoreState.Ready).climate.mode)
    }
}
