// MainActivity.kt — la única Activity: arma Compose y, al ocultarse, escribe
// lo pendiente del guardado diferido (como pagehide en la web). La primera vez
// en un teléfono que tenía el APK 1.7, trae sus datos antes de mostrar nada
// (MigracionDel17); mientras, sigue la pantalla de inicio.
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
        val inicio = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val migracion = MigracionDel17(app)
        if (migracion.pendiente()) {
            var trayendo = true
            inicio.setKeepOnScreenCondition { trayendo }
            migracion.traer { aviso -> trayendo = false; mostrar(aviso) }
        } else mostrar(null)
    }

    private fun mostrar(aviso: String?) = setContent { App(app.estado, app.preferencias, aviso) }

    override fun onStop() {
        super.onStop()
        app.estado.guardarPendiente()
    }
}
