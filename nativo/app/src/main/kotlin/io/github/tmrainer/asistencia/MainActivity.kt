// MainActivity.kt — la única Activity: arma Compose y, al ocultarse, escribe
// lo pendiente del guardado diferido (como pagehide en la web).
package io.github.tmrainer.asistencia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.tmrainer.asistencia.ui.App

class MainActivity : ComponentActivity() {

    private val app get() = application as OllaConectaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(app.estado, app.preferencias) }
    }

    override fun onStop() {
        super.onStop()
        app.estado.guardarPendiente()
    }
}
