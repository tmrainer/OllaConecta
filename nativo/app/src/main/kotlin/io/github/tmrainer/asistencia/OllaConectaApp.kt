// La Application: crea el Estado sobre la carpeta de datos del teléfono y lo carga al
// arrancar. Cada vez que cambia el calendario vuelve a programar los avisos del teléfono.
package io.github.tmrainer.asistencia

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.tmrainer.asistencia.nucleo.almacen.Almacen
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.modelo.Generacion
import io.github.tmrainer.asistencia.nucleo.modelo.ahoraIso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

    /** El correo a UNACEM: a dónde, cuándo se preparó cada envío y el recordatorio de la noche. */
    val envios by lazy { Envios(this) }

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
        // Consolida una migración o una reparación de lo leído.
        estado.guardarYa()
        preferencias = Preferencias(this)
        // Varios cambios seguidos del calendario reprograman los avisos una sola vez.
        fondo.launch { estado.calendario.collectLatest { delay(1_000); AvisosTelefono.reprogramar(this@OllaConectaApp, it) } }
        envios.programarRecordatorio()
    }

    /** El B-1 exportado: los últimos 20 van en la copia. */
    fun anotarB1Exportado(rendicionId: String, formato: String) {
        estado.cambiarRendicion(rendicionId) { r ->
            r.copy(generaciones = (r.generaciones + Generacion(en = ahoraIso(), formato = formato, totalCent = totales(r).total, como = "compartido")).takeLast(20))
        }
        estado.guardarYa()
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
