// OllaConectaApp.kt — la Application de la app nativa. Crea el Estado sobre
// la carpeta de datos del teléfono (filesDir) y lo carga al arrancar; las
// pantallas lo observan desde aquí.
package io.github.tmrainer.asistencia

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.tmrainer.asistencia.nucleo.almacen.Almacen
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class OllaConectaApp : Application() {

    /** Los datos de la olla: padrón, config, calendario y rendiciones. */
    lateinit var estado: Estado
        private set

    /** Lo que es de este teléfono: modo, letra, perfil. No va en las copias. */
    lateinit var preferencias: Preferencias
        private set

    override fun onCreate() {
        super.onCreate()
        // Los datos viven en filesDir/datos: una carpeta propia, separada de
        // lo que la copia de seguridad de Google o el WebView dejen en filesDir.
        val almacen = Almacen(File(filesDir, "datos"))
        estado = Estado(almacen, CoroutineScope(SupervisorJob() + Dispatchers.IO)).cargar()
        // Consolida una migración o una reparación de lo leído (como guardarYa() al abrir una página).
        estado.guardarYa()
        preferencias = Preferencias(this)
    }

    /**
     * Al ocultarse la interfaz se escribe lo pendiente del guardado diferido:
     * Android puede matar el proceso sin avisar y el último toque se perdería.
     * MainActivity.onStop hace lo mismo; esto es la red de seguridad.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && ::estado.isInitialized) estado.guardarPendiente()
    }
}
