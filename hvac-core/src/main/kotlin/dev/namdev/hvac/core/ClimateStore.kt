package dev.namdev.hvac.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StoreState {
    data object Connecting : StoreState
    data class Ready(
        val climate: ClimateState,
        val capabilities: ClimateCapabilities,
        val simulated: Boolean,
    ) : StoreState
    data class Failed(val reason: String) : StoreState
}

/**
 * Connects the state machine to a [ClimateDataSource]:
 * user intent -> reduce -> optimistic UI update -> property writes -> vehicle echo -> reduce.
 */
class ClimateStore(
    private val dataSource: ClimateDataSource,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<StoreState>(StoreState.Connecting)
    val state: StateFlow<StoreState> = _state.asStateFlow()

    @Suppress("TooGenericExceptionCaught")
    fun start() {
        scope.launch {
            val capabilities = try {
                dataSource.connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = StoreState.Failed(e.message ?: "Could not connect to the vehicle")
                return@launch
            }
            _state.value = StoreState.Ready(ClimateState(), capabilities, dataSource.isSimulated)
            dataSource.vehicleUpdates().collect { dispatch(ClimateEvent.Vehicle(it)) }
        }
    }

    fun dispatch(event: ClimateEvent) {
        var commands = emptyList<ClimateCommand>()
        _state.update { current ->
            if (current !is StoreState.Ready) return@update current
            val transition = HvacStateMachine.reduce(current.climate, event, current.capabilities)
            commands = transition.commands
            current.copy(climate = transition.state)
        }
        if (commands.isNotEmpty()) {
            scope.launch { commands.forEach { dataSource.execute(it) } }
        }
    }
}
