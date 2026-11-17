package com.edurip.bujhechhi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.edurip.bujhechhi.server.ClassEngine
import com.edurip.bujhechhi.ui.AppRoot
import com.edurip.bujhechhi.ui.BjTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ClassEngine.init(this)
        setContent {
            BjTheme {
                AppRoot()
            }
        }
    }
}
