// La guía en vivo de la cámara del DNI (Encuadre.kt), sobre fotogramas
// dibujados aquí: un fondo, una tarjeta clara con «texto» a rayas, un reflejo.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.dni.Caja
import io.github.tmrainer.asistencia.nucleo.dni.Consejo
import io.github.tmrainer.asistencia.nucleo.dni.Grises
import io.github.tmrainer.asistencia.nucleo.dni.aconsejar
import io.github.tmrainer.asistencia.nucleo.dni.buscarTarjeta
import io.github.tmrainer.asistencia.nucleo.dni.evaluar
import io.github.tmrainer.asistencia.nucleo.dni.medirCalidad
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val ANCHO = 320
private const val ALTO = 240
// El recuadro, como lo pone el visor: un DNI de 85,6 × 54 mm al centro.
private val GUIA = Caja(0.2f, 0.25f, 0.8f, 0.25f + 0.6f * ANCHO / ALTO * 54f / 85.6f)

/**
 * Un fotograma: fondo de gris [fondo] y, si hay [tarjeta], una tarjeta de
 * gris 190 con rayas de «texto» (si [conTexto]) y un reflejo opcional.
 */
private fun fotograma(fondo: Int = 50, tarjeta: Caja? = GUIA, conTexto: Boolean = true, reflejo: Caja? = null): Grises {
    val v = IntArray(ANCHO * ALTO) { fondo }
    fun dentro(c: Caja?, x: Int, y: Int) = c != null && x >= c.izquierda * ANCHO && x < c.derecha * ANCHO && y >= c.arriba * ALTO && y < c.abajo * ALTO
    for (y in 0 until ALTO) for (x in 0 until ANCHO) {
        if (dentro(tarjeta, x, y)) {
            val fila = ((y - tarjeta!!.arriba * ALTO) / 6).toInt()
            v[y * ANCHO + x] = if (conTexto && fila % 2 == 1 && (x / 3) % 2 == 0) 70 else 190
        }
        if (dentro(reflejo, x, y)) v[y * ANCHO + x] = 255
    }
    return Grises(ANCHO, ALTO, v)
}

private fun cerca(a: Float, b: Float, msj: String) = assertTrue(abs(a - b) < 0.03f, "$msj: $a y $b")

class EncuadreTest {

    @Test
    fun `Encuentra los cuatro bordes de una tarjeta bien puesta`() {
        val t = assertNotNull(buscarTarjeta(fotograma(), GUIA), "debía encontrarla")
        cerca(t.izquierda, GUIA.izquierda, "izquierda"); cerca(t.derecha, GUIA.derecha, "derecha")
        cerca(t.arriba, GUIA.arriba, "arriba"); cerca(t.abajo, GUIA.abajo, "abajo")
        igual(evaluar(fotograma(), GUIA, conEnfoque = true, hayLinterna = true).consejo, Consejo.BIEN)
    }

    @Test
    fun `Sin tarjeta, o con una forma que no es la de un DNI, no hay bordes`() {
        igual(buscarTarjeta(fotograma(tarjeta = null), GUIA), null, "fondo liso")
        val cuadrada = Caja(0.3f, 0.2f, 0.7f, 0.2f + 0.4f * ANCHO / ALTO)
        igual(buscarTarjeta(fotograma(tarjeta = cuadrada), GUIA), null, "una forma cuadrada")
        igual(evaluar(fotograma(fondo = 120, tarjeta = null), GUIA, conEnfoque = true, hayLinterna = false).consejo, Consejo.FUERA)
    }

    @Test
    fun `Lejos, cerca o corrida - el consejo lo dice`() {
        val chica = Caja(0.29f, 0.33f, 0.71f, 0.33f + 0.42f * ANCHO / ALTO * 54f / 85.6f)
        val t = assertNotNull(buscarTarjeta(fotograma(tarjeta = chica), GUIA), "la tarjeta lejana también se ve")
        cerca(t.ancho, chica.ancho, "ancho")
        igual(evaluar(fotograma(tarjeta = chica), GUIA, true, true).consejo, Consejo.ACERCA)

        val grande = Caja(0.17f, 0.22f, 0.83f, 0.22f + 0.66f * ANCHO / ALTO * 54f / 85.6f)
        igual(evaluar(fotograma(tarjeta = grande), GUIA, true, true).consejo, Consejo.ALEJA)

        val corrida = Caja(GUIA.izquierda + 0.1f, GUIA.arriba, GUIA.derecha + 0.1f, GUIA.abajo)
        igual(evaluar(fotograma(tarjeta = corrida), GUIA, true, true).consejo, Consejo.CENTRA)
    }

    @Test
    fun `Poca luz va primero, y el reflejo se mide sobre la tarjeta`() {
        val oscuro = Grises(ANCHO, ALTO, IntArray(ANCHO * ALTO) { 25 })
        igual(aconsejar(medirCalidad(oscuro, GUIA), null, GUIA, true, hayLinterna = true), Consejo.POCA_LUZ_LINTERNA)
        igual(aconsejar(medirCalidad(oscuro, GUIA), null, GUIA, true, hayLinterna = false), Consejo.POCA_LUZ)
        val brillo = Caja(0.4f, 0.4f, 0.6f, 0.55f)
        igual(evaluar(fotograma(reflejo = brillo), GUIA, true, true).consejo, Consejo.REFLEJO)
        igual(evaluar(fotograma(fondo = 255, tarjeta = null), GUIA, true, true).consejo, Consejo.FUERA, "un fondo blanco no es un reflejo")
        // El plástico del DNI ya quema un 2–3 % sin reflejo (medido en un moto g77): eso no es un reflejo.
        val brillito = Caja(0.45f, 0.45f, 0.45f + 20f / ANCHO, 0.45f + 15f / ALTO)
        igual(evaluar(fotograma(reflejo = brillito), GUIA, true, true).consejo, Consejo.BIEN, "un 2,5 % quemado")
    }

    @Test
    fun `Borroso - quieto si la cámara enfoca sola, más lejos si es de foco fijo`() {
        val liso = fotograma(conTexto = false)
        assertNotNull(buscarTarjeta(liso, GUIA))
        igual(evaluar(liso, GUIA, conEnfoque = true, hayLinterna = true).consejo, Consejo.QUIETO)
        igual(evaluar(liso, GUIA, conEnfoque = false, hayLinterna = true).consejo, Consejo.ALEJA_ENFOQUE)
    }

    @Test
    fun `Con el recuadro pegado al borde del fotograma, no se sale de la imagen`() {
        for (guia in listOf(Caja(0.0f, 0.0f, 0.6f, 0.5f), Caja(0.4f, 0.5f, 1.0f, 1.0f))) {
            buscarTarjeta(fotograma(tarjeta = guia), guia)
            evaluar(fotograma(tarjeta = guia), guia, true, true)
        }
    }

    @Test
    fun `Las líneas de texto no se confunden con un borde`() {
        // Solo «texto» sobre el fondo, sin tarjeta: rayas cortadas, nunca una línea continua.
        val v = IntArray(ANCHO * ALTO) { 50 }
        for (y in 0 until ALTO) for (x in 0 until ANCHO) if ((y / 6) % 2 == 1 && (x / 3) % 2 == 0) v[y * ANCHO + x] = 200
        igual(buscarTarjeta(Grises(ANCHO, ALTO, v), GUIA), null)
    }
}
