// Sugerencias.kt — porte de web/js/sugerencias.js. Funciones puras que
// PROPONEN. Nunca aplican nada. Origen interno, procedencia
// visible, aceptación explícita, reversible.
package io.github.tmrainer.asistencia.nucleo.sugerencias

import io.github.tmrainer.asistencia.nucleo.modelo.Config
import io.github.tmrainer.asistencia.nucleo.modelo.Perfil
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.UltimoCentro
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre

/** Un valor propuesto y de dónde sale. Sin procedencia no se muestra. */
data class Propuesta(val valor: String, val procedencia: String)

private fun propuesta(valor: String?, procedencia: String): Propuesta? {
    if (valor == null || valor.trim().isEmpty()) return null
    return Propuesta(valor, procedencia)
}

/**
 * Encabezado a partir del último centro guardado por la propia usuaria.
 * La UI lo muestra FUERA de los campos y solo entra si la usuaria lo acepta.
 */
fun sugerenciasEncabezado(config: Config?): Map<String, Propuesta> {
    val ultimo = config?.ultimoCentro ?: return emptyMap()
    val proc = if (ultimo.guardadoEn.isNotEmpty()) "de tu rendición del ${aDdMmAa(ultimo.guardadoEn)}"
    else "de tu rendición anterior"
    val campos = listOf(
        "tipoCentro" to ultimo.tipoCentro,
        "nombreCentro" to ultimo.nombreCentro,
        "codigoPca" to ultimo.codigoPca,
        "nombrePresidenta" to ultimo.nombrePresidenta,
        "dniPresidenta" to ultimo.dniPresidenta,
        "celular" to ultimo.celular,
        "nombreTesorera" to ultimo.nombreTesorera,
        "dniTesorera" to ultimo.dniTesorera
    )
    val out = LinkedHashMap<String, Propuesta>()
    for ((c, v) in campos) propuesta(v, proc)?.let { out[c] = it }
    return out
}

/**
 * Encabezado y firmas a partir del perfil de quien usa el teléfono: la
 * presidenta propone sus datos en los campos de presidenta y la tesorera en
 * los suyos; «apoyo» no propone nada.
 */
fun sugerenciasPerfil(perfil: Perfil?): Map<String, Propuesta> {
    if (perfil == null) return emptyMap()
    val proc = "de tu perfil"
    val campos: List<Pair<String, String>> = when (perfil.rol) {
        "presidenta" -> listOf(
            "nombrePresidenta" to perfil.nombre, "dniPresidenta" to perfil.dni, "celular" to perfil.celular,
            "nombrePresidentaFirma" to perfil.nombre, "dniPresidentaFirma" to perfil.dni
        )
        "tesorera" -> listOf("nombreTesorera" to perfil.nombre, "dniTesorera" to perfil.dni)
        else -> emptyList()
    }
    val out = LinkedHashMap<String, Propuesta>()
    for ((c, v) in campos) propuesta(v, proc)?.let { out[c] = it }
    return out
}

/**
 * RUC ya usado con un proveedor que la usuaria escribió antes.
 * Solo busca entre sus propias rendiciones. Nunca consulta a nadie.
 */
fun sugerirRucProveedor(rendiciones: List<Rendicion>?, nombreProveedor: String?): Propuesta? {
    val buscado = normalizarNombre(nombreProveedor)
    if (buscado.isEmpty()) return null
    val vistos = ArrayList<Pair<String, String>>()   // ruc, fecha
    for (r in rendiciones ?: emptyList()) {
        for (e in r.egresos) {
            if (e.rucProveedor.isEmpty() || e.proveedorNombre.isEmpty()) continue
            if (normalizarNombre(e.proveedorNombre) != buscado) continue
            vistos.add(e.rucProveedor to e.fechaCompra)
        }
    }
    if (vistos.isEmpty()) return null
    val mejor = vistos.sortedByDescending { it.second }.first()
    return propuesta(mejor.first,
        if (mejor.second.isNotEmpty()) "de tu compra del ${aDdMmAa(mejor.second)}" else "de una compra anterior")
}

/** Descripciones de compra ya usadas, para elegir sin volver a teclear. */
fun descripcionesUsadas(rendiciones: List<Rendicion>?, limite: Int = 30): List<Propuesta> {
    val conteo = LinkedHashMap<String, Int>()
    for (r in rendiciones ?: emptyList()) {
        for (e in r.egresos) {
            val d = e.descripcion.trim()
            if (d.isEmpty()) continue
            conteo[d] = (conteo[d] ?: 0) + 1
        }
    }
    return conteo.entries
        .sortedByDescending { it.value }
        .take(limite)
        .map { (texto, veces) -> Propuesta(texto, "usada $veces vez/veces antes") }
}

/** Instantánea del encabezado para proponerlo la próxima vez. */
fun instantaneaCentro(rendicion: Rendicion?): UltimoCentro? {
    if (rendicion == null) return null
    return UltimoCentro(
        guardadoEn = rendicion.fechaRendicion,
        tipoCentro = rendicion.tipoCentro,
        nombreCentro = rendicion.nombreCentro,
        codigoPca = rendicion.codigoPca,
        nombrePresidenta = rendicion.nombrePresidenta,
        dniPresidenta = rendicion.dniPresidenta,
        celular = rendicion.celular,
        nombreTesorera = rendicion.nombreTesorera,
        dniTesorera = rendicion.dniTesorera
    )
}
