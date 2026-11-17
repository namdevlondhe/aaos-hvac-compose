package dev.namdev.hvac.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.namdev.hvac.core.ClimateEvent
import dev.namdev.hvac.core.ClimateStore
import dev.namdev.hvac.core.StoreState
import dev.namdev.hvac.data.ClimateDataSourceFactory
import kotlinx.coroutines.flow.StateFlow

/** Thin Android wrapper. All logic lives in hvac-core (ClimateStore + HvacStateMachine). */
class ClimateViewModel(application: Application) : AndroidViewModel(application) {

    private val dataSource = ClimateDataSourceFactory.create(application)
    private val store = ClimateStore(dataSource, viewModelScope)

    val state: StateFlow<StoreState> = store.state

    init {
        store.start()
    }

    fun onIntent(intent: ClimateEvent.UserIntent) = store.dispatch(intent)

    override fun onCleared() {
        dataSource.disconnect()
    }
}
