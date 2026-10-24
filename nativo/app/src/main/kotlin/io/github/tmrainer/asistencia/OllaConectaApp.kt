// OllaConectaApp.kt — la Application de la app nativa. Crea el Estado sobre
// la carpeta de datos del teléfono (filesDir) y lo carga al arrancar; las
// pantallas lo observan desde aquí. Cada vez que cambia el calendario (también
// al arrancar, al recuperar una copia o al traer los datos del 1.7) vuelve a
// programar los avisos del teléfono.
package io.github.tmrainer.asistencia

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.tmrainer.asistencia.nucleo.almacen.Almacen
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class OllaConectaApp : Application() {

    /** Los datos de la olla: padrón, config, calendario y rendiciones. */
    lateinit var estado: Estado
        private set

    /** Lo que es de este teléfono: modo, letra, perfil. No va en las copias. */
    lateinit var preferencias: Preferencias
        private set

    /** La voz del teléfono; se prende la primera vez que se usa. */
    val voz by lazy { Voz(this) { preferencias.vozLenta.value } }

    /** A qué pantalla ir al tocar un aviso del teléfono (MainActivity la pone, App la consume). */
    val abrir = MutableStateFlow<String?>(null)

    private val fondo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Los datos viven en filesDir/datos: una carpeta propia, separada de
        // lo que la copia de seguridad de Google o el WebView dejen en filesDir.
        val almacen = Almacen(File(filesDir, "datos"))
        estado = Estado(almacen, CoroutineScope(SupervisorJob() + Dispatchers.IO)).cargar()
        // Consolida una migración o una reparación de lo leído (como guardarYa() al abrir una página).
        estado.guardarYa()
        preferencias = Preferencias(this)
        fondo.launch { estado.calendario.collect { AvisosTelefono.reprogramar(this@OllaConectaApp, it) } }
    }

    /** Tras dar el permiso de avisos: programarlos ya, sin esperar un cambio del calendario. */
    fun reprogramarAvisos(): Int = AvisosTelefono.reprogramar(this, estado.calendario.value)

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
