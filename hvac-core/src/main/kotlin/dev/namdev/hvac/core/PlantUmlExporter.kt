package dev.namdev.hvac.core

/**
 * Generates the architecture diagram from the code, so the design doc can never drift
 * from the implementation (the same idea as ASPICE SWE.2 traceability).
 */
object PlantUmlExporter {

    fun stateDiagram(transitions: List<ModeTransition> = HvacStateMachine.MODE_TRANSITIONS): String =
        buildString {
            appendLine("@startuml")
            appendLine("' Generated from HvacStateMachine.MODE_TRANSITIONS. Do not edit by hand.")
            appendLine("title HVAC mode state machine")
            appendLine("[*] --> OFF")
            transitions.forEach { appendLine("${it.from} --> ${it.to} : ${it.label}") }
            appendLine("@enduml")
        }
}
