// ConfirmarDatos.kt — lo que una persona escribió de sí misma (PrimerUso),
// comparado con lo leído de su DNI: el DNI confirma, no llena.
package io.github.tmrainer.asistencia.nucleo.dni

/** Qué difiere entre lo escrito y el DNI. [faltanEnDni]: palabras escritas que el DNI no tiene. */
class Confirmacion(val numeroDistinto: Boolean, val faltanEnDni: List<String>) {
    val coincide get() = !numeroDistinto && faltanEnDni.isEmpty()
}

/**
 * El número tiene que ser el mismo. Del nombre, cada palabra escrita tiene que
 * estar en el DNI, sin mirar tildes ni mayúsculas: se puede escribir menos (un
 * solo apellido), no algo distinto.
 */
fun confirmarConDni(nombre: String, dni: String, nombreDni: String, dniDni: String): Confirmacion {
    val delDni = normalizarLetras(nombreDni).split(' ').toSet()
    val faltan = nombre.trim().split(Regex("\\s+")).filter { palabra ->
        normalizarLetras(palabra).split(' ').any { it.isNotEmpty() && it !in delDni }
    }
    return Confirmacion(dni.trim() != dniDni.trim(), faltan)
}
