// Solo desde Ajustes → Probar el escaneo del DNI: lo que pasó
// en una lectura, para compartirlo con el equipo. Inscribir y pasar lista nunca lo
// piden: ahí la lectura no se guarda ni se registra.
package io.github.tmrainer.asistencia.ui.camara

import android.graphics.Bitmap

/** El fotograma que dio cada lado, con lo que el lector vio en él y cuándo. */
class CapturaDni(val imagen: Bitmap, val filas: List<String>, val ms: Long)

class RegistroDni {
    private val inicio = System.currentTimeMillis()
    @Volatile var fotogramas = 0
    @Volatile var reverso: CapturaDni? = null
    @Volatile var anverso: CapturaDni? = null

    /** Una copia (el visor reusa el suyo), a lo más de 1280 px de ancho. */
    fun capturar(imagen: Bitmap, filas: List<String>): CapturaDni {
        val escala = minOf(1f, 1280f / imagen.width)
        val copia = Bitmap.createScaledBitmap(imagen, (imagen.width * escala).toInt(), (imagen.height * escala).toInt(), true)
        return CapturaDni(if (copia === imagen) imagen.copy(imagen.config ?: Bitmap.Config.ARGB_8888, false) else copia, filas, System.currentTimeMillis() - inicio)
    }
}
