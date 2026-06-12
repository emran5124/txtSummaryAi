package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.screens.MainCompose
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SummarizerViewModel
import com.example.ui.viewmodel.SummarizerViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: SummarizerViewModel by viewModels {
        SummarizerViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainCompose(viewModel = viewModel)
            }
        }
    }

    companion object {
        fun getDevApiKey(): String {
            return BuildConfig.GEMINI_API_KEY
        }
    }
}
