package com.example.jarvis

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jarvis.ui.screens.JarvisScreen
import com.example.jarvis.ui.theme.JARVISTheme
import com.example.jarvis.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {
    private var jarvisViewModel: JarvisViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark blue, so keep system bar icons light whatever the system theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            JARVISTheme {
                val viewModel: JarvisViewModel = viewModel(
                    factory = JarvisViewModel.factory(applicationContext)
                )
                jarvisViewModel = viewModel
                JarvisScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStop() {
        jarvisViewModel?.onAppBackgrounded()
        super.onStop()
    }
}
