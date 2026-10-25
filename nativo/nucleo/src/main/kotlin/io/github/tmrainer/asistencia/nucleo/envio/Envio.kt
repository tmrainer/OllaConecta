// Envio.kt — el correo a UNACEM, armado en el teléfono (armarCorreo(),
// esDiaDePadron() y quincena() de nube/apps-script/Codigo.js). En la web lo
// manda un script en la nube; aquí la dirigente lo manda desde su propio
// correo: la app solo deja listos el asunto, el texto y el Excel adjunto
// (los mismos de excel-reportes.js), sin servidor ni cuenta.
//
// Solo ordena y suma lo ya ingresado: no completa ni deduce nada.
package io.github.tmrainer.asistencia.nucleo.envio

import io.github.tmrainer.asistencia.nucleo.calculos.filasPadron
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEconomicoRaciones
import io.github.tmrainer.asistencia.nucleo.documentos.excelDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelPadron
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.pad2
import java.text.Normalizer

const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Un correo listo para mandar: asunto, texto y el Excel adjunto (nombre y bytes). */
class Correo(val asunto: String, val cuerpo: String, val archivo: String, val bytes: ByteArray)

/** ¿Toca mandar el padrón ese día? El 15 y el último día del mes. */
fun esDiaDePadron(iso: String): Boolean {
    val f = aFecha(iso) ?: return false
    return f.dayOfMonth == 15 || f.dayOfMonth == f.lengthOfMonth()
}

/** La quincena a la que pertenece el día: del 1 al 15, o del 16 a fin de mes. */
fun quincena(iso: String): Pair<String, String> {
    val f = aFecha(iso) ?: return iso to iso
    val mes = iso.substring(0, 8)
    return if (f.dayOfMonth <= 15) "${mes}01" to "${mes}15" else "${mes}16" to "$mes${pad2(f.lengthOfMonth())}"
}

/**
 * El nombre del archivo: solo letras sin tilde, números, guion y punto (así
 * lo acepta Android y se lee bien en WhatsApp), como nombreArchivo() de la web.
 */
fun nombreArchivo(texto: String, extension: String): String {
    val sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"), "")
    return sinTildes.replace(Regex("[^A-Za-z0-9_.\\- ]+"), "").replace(Regex("\\s+"), " ").trim().take(70) + "." + extension
}

private fun pie(hora: String, quien: String) = listOf(
    "",
    "Enviado desde OllaConecta${if (quien.isNotBlank()) " por ${quien.trim()}" else ""} a las $hora.",
    "Si falta algo, la olla puede volver a enviar desde la app."
)

private fun periodo(inicio: String, fin: String) =
    if (inicio == fin) aDdMmAa(inicio) else "del ${aDdMmAa(inicio)} al ${aDdMmAa(fin)}"

/**
 * La asistencia de un periodo (para UNACEM, la de hoy: inicio = fin).
 * [olla] es el nombre del centro del último B-1; vacío, el correo dice «la olla».
 */
fun correoDeAsistencia(padron: Padron, inicio: String, fin: String, olla: String, hora: String, quien: String = ""): Correo {
    val nombre = olla.trim()
    val r = resumenEconomicoRaciones(padron, inicio, fin)
    val lineas = listOf(
        "Asistencia de ${if (nombre.isNotEmpty()) "la olla $nombre" else "la olla"}, ${periodo(inicio, fin)}.",
        "",
        "Raciones: ${r.racionesTotales} (${r.racionesNormales} de menú normal, ${r.racionesAyudaSocial} de ayuda social).",
        if (r.recaudacionCent == null) "Recaudado: sin precio del menú, no se calcula." else "Recaudado: S/ ${formatearSoles(r.recaudacionCent)}.",
        "Va adjunto el Excel con el resumen por día y la lista por persona."
    ) + pie(hora, quien)
    return Correo(
        asunto = listOf("Asistencia", nombre, periodo(inicio, fin)).filter { it.isNotEmpty() }.joinToString(" · "),
        cuerpo = lineas.joinToString("\n"),
        archivo = nombreArchivo(listOf("Asistencia", nombre, inicio).filter { it.isNotEmpty() }.joinToString(" "), "xlsx"),
        bytes = excelDeAsistencia(padron, inicio, fin, nombre)
    )
}

/** El padrón: las personas activas a [fecha], con los días que vinieron en su quincena. */
fun correoDelPadron(padron: Padron, fecha: String, olla: String, hora: String, quien: String = ""): Correo {
    val nombre = olla.trim()
    val (inicio, fin) = quincena(fecha)
    val filas = filasPadron(padron, fecha)
    val lineas = listOf(
        "Padrón de ${if (nombre.isNotEmpty()) "la olla $nombre" else "la olla"}: ${cuantos(filas.size, "persona activa", "personas activas")} al ${aDdMmAa(fecha)}, con los días que vinieron ${periodo(inicio, fin)}.",
        "Va adjunto el Excel con el padrón y el resumen por grupo de edad."
    ) + pie(hora, quien)
    return Correo(
        asunto = listOf("Padrón", nombre, "al ${aDdMmAa(fecha)}").filter { it.isNotEmpty() }.joinToString(" · "),
        cuerpo = lineas.joinToString("\n"),
        archivo = nombreArchivo(listOf("Padron", nombre, fecha).filter { it.isNotEmpty() }.joinToString(" "), "xlsx"),
        bytes = excelDelPadron(padron, fecha, inicio, fin, nombre)
    )
}

/** ¿Parece un correo? Una arroba con algo antes y un punto después; lo demás lo dice el correo al mandar. */
fun pareceCorreo(texto: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(texto.trim())
