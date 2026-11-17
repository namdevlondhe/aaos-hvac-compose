package dev.namdev.hvac.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Checks that the code, the transition table and the committed diagram all agree. */
class ModeTransitionSpecTest {

    @Test
    fun `reducer follows every documented transition`() {
        HvacStateMachine.MODE_TRANSITIONS.forEach { transition ->
            val result = HvacStateMachine.reduce(stateIn(transition.from), transition.trigger)
            assertEquals("${transition.from} --${transition.label}-->", transition.to, result.state.mode)
        }
    }

    @Test
    fun `committed PlantUML diagram matches the code`() {
        val generated = PlantUmlExporter.stateDiagram()
        val file = File("../docs/hvac-state-machine.puml")

        if (System.getProperty("updateDocs") == "true") {
            file.writeText(generated)
        }

        assertEquals(
            "docs/hvac-state-machine.puml is out of date. Run: ./gradlew :hvac-core:test -DupdateDocs=true",
            generated,
            file.readText(),
        )
    }

    private fun stateIn(mode: HvacMode) = when (mode) {
        HvacMode.OFF -> ClimateState(power = false)
        HvacMode.MANUAL -> ClimateState(power = true, fanSpeed = 2)
        HvacMode.AUTO -> ClimateState(power = true, auto = true, fanSpeed = 2)
        HvacMode.DEFROST -> HvacStateMachine.reduce(ClimateState(power = true, fanSpeed = 2), ClimateEvent.DefrostToggled).state
    }
}
