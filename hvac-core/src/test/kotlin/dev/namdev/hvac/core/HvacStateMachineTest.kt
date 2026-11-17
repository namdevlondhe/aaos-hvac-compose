package dev.namdev.hvac.core

import dev.namdev.hvac.core.ClimateCommand.SetAc
import dev.namdev.hvac.core.ClimateCommand.SetAuto
import dev.namdev.hvac.core.ClimateCommand.SetDefrost
import dev.namdev.hvac.core.ClimateCommand.SetFanSpeed
import dev.namdev.hvac.core.ClimateCommand.SetPower
import dev.namdev.hvac.core.ClimateCommand.SetRecirc
import dev.namdev.hvac.core.ClimateCommand.SetTemperature
import dev.namdev.hvac.core.ClimateEvent.AcToggled
import dev.namdev.hvac.core.ClimateEvent.DefrostToggled
import dev.namdev.hvac.core.ClimateEvent.FanChanged
import dev.namdev.hvac.core.ClimateEvent.PowerToggled
import dev.namdev.hvac.core.ClimateEvent.TemperatureChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacStateMachineTest {

    private val caps = ClimateCapabilities(tempMin = 16f, tempMax = 28f, tempStep = 0.5f, fanMin = 1, fanMax = 6)
    private val on = ClimateState(power = true, fanSpeed = 2)

    private fun reduce(state: ClimateState, event: ClimateEvent) = HvacStateMachine.reduce(state, event, caps)

    @Test
    fun `while OFF every intent except POWER is ignored`() {
        val off = ClimateState(power = false)

        listOf(AcToggled, DefrostToggled, FanChanged(+1), TemperatureChanged(Zone.DRIVER, +1)).forEach { intent ->
            val result = reduce(off, intent)
            assertEquals(off, result.state)
            assertTrue(result.commands.isEmpty())
        }
    }

    @Test
    fun `POWER turns the system on and writes the property`() {
        val result = reduce(ClimateState(power = false), PowerToggled)

        assertEquals(HvacMode.MANUAL, result.state.mode)
        assertEquals(listOf(SetPower(true)), result.commands)
    }

    @Test
    fun `entering DEFROST forces fresh air, A-C on and max fan`() {
        val auto = on.copy(auto = true, recirc = true, ac = false)

        val result = reduce(auto, DefrostToggled)

        assertEquals(HvacMode.DEFROST, result.state.mode)
        assertEquals(caps.fanMax, result.state.fanSpeed)
        assertEquals(
            listOf(SetDefrost(true), SetAuto(false), SetAc(true), SetRecirc(false), SetFanSpeed(caps.fanMax)),
            result.commands,
        )
    }

    @Test
    fun `leaving DEFROST restores the previous fan speed and A-C`() {
        val inDefrost = reduce(on.copy(ac = false, fanSpeed = 3), DefrostToggled).state

        val result = reduce(inDefrost, DefrostToggled)

        assertEquals(HvacMode.MANUAL, result.state.mode)
        assertEquals(3, result.state.fanSpeed)
        assertEquals(false, result.state.ac)
        assertEquals(listOf(SetDefrost(false), SetFanSpeed(3), SetAc(false)), result.commands)
    }

    @Test
    fun `A-C cannot be switched off during DEFROST`() {
        val inDefrost = reduce(on, DefrostToggled).state

        val result = reduce(inDefrost, AcToggled)

        assertTrue(result.state.ac)
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `manual fan change in AUTO is an override back to MANUAL`() {
        val result = reduce(on.copy(auto = true), FanChanged(+1))

        assertEquals(HvacMode.MANUAL, result.state.mode)
        assertEquals(listOf(SetAuto(false), SetFanSpeed(3)), result.commands)
    }

    @Test
    fun `fan speed is clamped to the vehicle range`() {
        val result = reduce(on.copy(fanSpeed = caps.fanMax), FanChanged(+1))

        assertEquals(caps.fanMax, result.state.fanSpeed)
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `temperature moves in steps and stops at the limits`() {
        val warmer = reduce(on.copy(driverTemp = 22f), TemperatureChanged(Zone.DRIVER, +1))
        assertEquals(22.5f, warmer.state.driverTemp)
        assertEquals(listOf(SetTemperature(Zone.DRIVER, 22.5f)), warmer.commands)

        val atMax = reduce(on.copy(passengerTemp = 28f), TemperatureChanged(Zone.PASSENGER, +1))
        assertEquals(28f, atMax.state.passengerTemp)
        assertTrue(atMax.commands.isEmpty())
    }

    @Test
    fun `vehicle updates change state without sending commands back`() {
        val result = reduce(on, ClimateEvent.Vehicle(VehicleUpdate.Temperature(Zone.PASSENGER, 19.5f)))

        assertEquals(19.5f, result.state.passengerTemp)
        assertTrue(result.commands.isEmpty())
    }
}
