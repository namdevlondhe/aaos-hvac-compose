package dev.namdev.hvac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.namdev.hvac.ui.ClimateScreen
import dev.namdev.hvac.ui.ClimateViewModel
import dev.namdev.hvac.ui.HvacTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HvacTheme {
                val viewModel: ClimateViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                ClimateScreen(state = state, onIntent = viewModel::onIntent)
            }
        }
    }
}
