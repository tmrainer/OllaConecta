// Todo monto vive como ENTERO de céntimos (Long). Nunca coma flotante.
package io.github.tmrainer.asistencia.nucleo.modelo

import kotlin.math.abs

private val MONTO = Regex("^\\d+(\\.\\d{0,2})?$")
private val NUMERO = Regex("^\\d+(\\.\\d+)?$")

/**
 * Convierte texto escrito por la usuaria a céntimos enteros.
 * Devuelve null si está vacío o no es un monto válido. Nunca adivina.
 */
fun aCentimos(texto: String?): Long? {
    if (texto == null) return null
    val limpio = texto.trim().replace(Regex("\\s"), "").replace(',', '.')
    if (limpio.isEmpty()) return null
    if (!MONTO.matches(limpio)) return null
    val partes = limpio.split('.')
    val entera = partes[0].toLongOrNull() ?: return null
    val dec = (partes.getOrElse(1) { "" } + "00").substring(0, 2)
    return entera * 100 + dec.toLong()
}

/** Céntimos a texto "209.85". Devuelve "" si es null: lo vacío se muestra vacío. */
fun formatearSoles(centimos: Long?): String {
    if (centimos == null) return ""
    val signo = if (centimos < 0) "-" else ""
    val abs = abs(centimos)
    return signo + (abs / 100) + "." + pad2((abs % 100).toInt())
}

/** Cantidad numérica libre (puede llevar decimales). null si no es válida. */
fun aNumero(texto: String?): Double? {
    if (texto == null) return null
    val limpio = texto.trim().replace(',', '.')
    if (limpio.isEmpty()) return null
    if (!NUMERO.matches(limpio)) return null
    return limpio.toDoubleOrNull()
}
