// Encuadre.kt — cómo está el DNI frente a la cámara, para guiar en vivo como
// las apps de los bancos («Acerca el DNI», «Hay un reflejo»): el brillo, los
// reflejos, la nitidez y dónde están los bordes de la tarjeta. Trabaja sobre
// los grises de un fotograma chico (unos 320 px de ancho), en memoria; no
// guarda nada. Sin librerías: sumas sobre los píxeles.
package io.github.tmrainer.asistencia.nucleo.dni

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Un fotograma en grises: un valor de 0 a 255 por píxel, fila por fila. */
class Grises(val ancho: Int, val alto: Int, val valores: IntArray) {
    init { require(valores.size == ancho * alto) }
    operator fun get(x: Int, y: Int) = valores[y * ancho + x]
}

/** Un rectángulo en fracciones del fotograma: de 0 a 1 en cada eje. */
data class Caja(val izquierda: Float, val arriba: Float, val derecha: Float, val abajo: Float) {
    val ancho get() = derecha - izquierda
    val alto get() = abajo - arriba
    val centroX get() = (izquierda + derecha) / 2
    val centroY get() = (arriba + abajo) / 2

    /** La misma caja sin un margen de [fraccion] de cada lado. */
    fun interior(fraccion: Float) = Caja(izquierda + ancho * fraccion, arriba + alto * fraccion, derecha - ancho * fraccion, abajo - alto * fraccion)
}

/** `brillo` medio (0–255), `reflejo` (fracción de píxeles quemados) y `nitidez` (varianza del laplaciano). */
data class Calidad(val brillo: Double, val reflejo: Double, val nitidez: Double)

/** Lo que se le dice a quien sostiene el DNI. Uno a la vez, el más urgente primero (ver aconsejar). */
enum class Consejo(val texto: String) {
    POCA_LUZ_LINTERNA("Hay poca luz. Prende la linterna."),
    POCA_LUZ("Hay poca luz. Busca un lugar más claro."),
    REFLEJO("Hay un reflejo. Inclina un poco el DNI."),
    FUERA("Pon el DNI dentro del recuadro."),
    ACERCA("Acerca el DNI."),
    ALEJA("Aleja un poco el DNI."),
    CENTRA("Centra el DNI en el recuadro."),
    QUIETO("Quédate quieto: se ve borroso."),
    // Una cámara sin enfoque automático solo se ve nítida desde un poco más lejos.
    ALEJA_ENFOQUE("Aleja un poco el DNI: así se ve nítido."),
    BIEN("Bien. No lo muevas.")
}

// Umbrales, en grises de 320 px de ancho. Reflejo y nitidez, medidos con un
// DNI real en un moto g77 (25-10-2026): el plástico ya quema un 2–3 % de los
// píxeles sin reflejo (con uno, 11 %); enfocado da 2400–9000 de nitidez y
// movido, 115–660.
private const val BRILLO_MINIMO = 55.0
private const val QUEMADO = 250
private const val REFLEJO_MAXIMO = 0.06
private const val NITIDEZ_MINIMA = 300.0
// La tarjeta, respecto al recuadro: ni tan chica, ni tan grande, ni tan corrida.
private const val ANCHO_MINIMO = 0.75f
private const val ANCHO_MAXIMO = 1.08f
private const val CORRIDA_MAXIMA = 0.12f

/** Brillo, reflejos y nitidez dentro de [zona]. */
fun medirCalidad(g: Grises, zona: Caja): Calidad {
    val x0 = max(1, (zona.izquierda * g.ancho).roundToInt()); val x1 = min(g.ancho - 2, (zona.derecha * g.ancho).roundToInt())
    val y0 = max(1, (zona.arriba * g.alto).roundToInt()); val y1 = min(g.alto - 2, (zona.abajo * g.alto).roundToInt())
    if (x1 <= x0 || y1 <= y0) return Calidad(0.0, 0.0, 0.0)
    var suma = 0L; var quemados = 0; var n = 0
    var sumaL = 0.0; var sumaL2 = 0.0
    for (y in y0..y1) for (x in x0..x1) {
        val v = g[x, y]
        suma += v; n += 1
        if (v >= QUEMADO) quemados += 1
        // Laplaciano de 4 vecinos: los bordes nítidos dan valores grandes.
        val l = (g[x - 1, y] + g[x + 1, y] + g[x, y - 1] + g[x, y + 1] - 4 * v).toDouble()
        sumaL += l; sumaL2 += l * l
    }
    val media = sumaL / n
    return Calidad(brillo = suma.toDouble() / n, reflejo = quemados.toDouble() / n, nitidez = sumaL2 / n - media * media)
}

/**
 * Los bordes de la tarjeta cerca del recuadro [guia], o null si no se ven
 * los cuatro. Un borde es una línea de cambio de gris CONTINUA: la fila (o
 * columna) donde más píxeles seguidos cambian. El texto también cambia de
 * gris, pero a saltos, y no llega a la fracción pedida.
 */
fun buscarTarjeta(g: Grises, guia: Caja): Caja? {
    val gx0 = guia.izquierda * g.ancho; val gx1 = guia.derecha * g.ancho
    val gy0 = guia.arriba * g.alto; val gy1 = guia.abajo * g.alto
    val gw = gx1 - gx0; val gh = gy1 - gy0
    if (gw < 20 || gh < 12) return null
    // Las filas se miden en el 70 % del medio del recuadro (las esquinas son redondas).
    val xs = (gx0 + gw * 0.15f).roundToInt()..(gx1 - gw * 0.15f).roundToInt()
    val ys = (gy0 + gh * 0.15f).roundToInt()..(gy1 - gh * 0.15f).roundToInt()
    val arriba = mejorFila(g, xs, (gy0 - gh * 0.35f).roundToInt()..(gy0 + gh * 0.3f).roundToInt()) ?: return null
    val abajo = mejorFila(g, xs, (gy1 - gh * 0.3f).roundToInt()..(gy1 + gh * 0.35f).roundToInt()) ?: return null
    val izquierda = mejorColumna(g, ys, (gx0 - gw * 0.25f).roundToInt()..(gx0 + gw * 0.25f).roundToInt()) ?: return null
    val derecha = mejorColumna(g, ys, (gx1 - gw * 0.25f).roundToInt()..(gx1 + gw * 0.25f).roundToInt()) ?: return null
    if (abajo <= arriba || derecha <= izquierda) return null
    // Un DNI mide 85,6 × 54 mm: si la forma no se parece, no es la tarjeta.
    val proporcion = (derecha - izquierda).toFloat() / (abajo - arriba)
    if (proporcion < 1.35f || proporcion > 1.85f) return null
    return Caja(izquierda.toFloat() / g.ancho, arriba.toFloat() / g.alto, derecha.toFloat() / g.ancho, abajo.toFloat() / g.alto)
}

// Cuánto tiene que cambiar el gris para contar como borde, y en qué fracción de la línea.
private const val SALTO = 14
private const val CONTINUIDAD = 0.6

// Las bandas pueden salirse del fotograma: se recortan dejando 3 píxeles (la
// tolerancia de ± 2 más el vecino del salto).
private fun mejorFila(g: Grises, xs: IntRange, banda: IntRange): Int? {
    val ys = max(3, banda.first)..min(g.alto - 4, banda.last)
    val cx = max(1, xs.first)..min(g.ancho - 2, xs.last)
    if (ys.isEmpty() || cx.isEmpty()) return null
    var mejor: Int? = null; var mejorFraccion = CONTINUIDAD
    for (y in ys) {
        // Un poco de tolerancia (± 2 filas) para una tarjeta apenas chueca.
        val fraccion = cx.count { x -> (y - 2..y + 2).any { yy -> abs(g[x, yy + 1] - g[x, yy - 1]) >= SALTO } }.toDouble() / cx.count()
        if (fraccion > mejorFraccion) { mejorFraccion = fraccion; mejor = y }
    }
    return mejor
}

private fun mejorColumna(g: Grises, ys: IntRange, banda: IntRange): Int? {
    val xs = max(3, banda.first)..min(g.ancho - 4, banda.last)
    val cy = max(1, ys.first)..min(g.alto - 2, ys.last)
    if (xs.isEmpty() || cy.isEmpty()) return null
    var mejor: Int? = null; var mejorFraccion = CONTINUIDAD
    for (x in xs) {
        val fraccion = cy.count { y -> (x - 2..x + 2).any { xx -> abs(g[xx + 1, y] - g[xx - 1, y]) >= SALTO } }.toDouble() / cy.count()
        if (fraccion > mejorFraccion) { mejorFraccion = fraccion; mejor = x }
    }
    return mejor
}

/** Lo que se ve en un fotograma: la tarjeta (si se encontró) y qué decir. */
data class Encuadre(val tarjeta: Caja?, val consejo: Consejo)

/**
 * Todo junto: busca la tarjeta y mide la calidad en su interior (sus bordes
 * siempre se ven nítidos; el texto de adentro es lo que se tiene que leer).
 */
fun evaluar(g: Grises, guia: Caja, conEnfoque: Boolean, hayLinterna: Boolean): Encuadre {
    val tarjeta = buscarTarjeta(g, guia)
    val calidad = medirCalidad(g, (tarjeta ?: guia).interior(0.1f))
    return Encuadre(tarjeta, aconsejar(calidad, tarjeta, guia, conEnfoque, hayLinterna))
}

/**
 * El consejo para este fotograma. [conEnfoque]: si la cámara enfoca sola
 * (si no, una imagen borrosa se arregla alejando el DNI, no quedándose quieto).
 */
fun aconsejar(calidad: Calidad, tarjeta: Caja?, guia: Caja, conEnfoque: Boolean, hayLinterna: Boolean): Consejo = when {
    calidad.brillo < BRILLO_MINIMO -> if (hayLinterna) Consejo.POCA_LUZ_LINTERNA else Consejo.POCA_LUZ
    tarjeta == null -> Consejo.FUERA
    tarjeta.ancho < guia.ancho * ANCHO_MINIMO -> Consejo.ACERCA
    tarjeta.ancho > guia.ancho * ANCHO_MAXIMO -> Consejo.ALEJA
    abs(tarjeta.centroX - guia.centroX) > guia.ancho * CORRIDA_MAXIMA ||
        abs(tarjeta.centroY - guia.centroY) > guia.alto * CORRIDA_MAXIMA -> Consejo.CENTRA
    // El reflejo se mide sobre la tarjeta: sin ella, un fondo blanco parecería un reflejo.
    calidad.reflejo > REFLEJO_MAXIMO -> Consejo.REFLEJO
    calidad.nitidez < NITIDEZ_MINIMA -> if (conEnfoque) Consejo.QUIETO else Consejo.ALEJA_ENFOQUE
    else -> Consejo.BIEN
}
