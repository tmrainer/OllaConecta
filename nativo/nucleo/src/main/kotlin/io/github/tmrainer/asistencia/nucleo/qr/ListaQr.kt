// El QR de antes, solo cifras, con la asistencia de un día. Funciones puras:
// sin Android, sin estado.
//
// Ya no se genera, pero un teléfono con la versión anterior todavía lo muestra
// y hay que poder leerlo (leerParteQr() de CompartirQr.kt lo reconoce).
//
// Formato de cada parte (todo cifras):
//   «81» (marca y versión) · AAAAMMDD · parte (2) · total (2) · cuántas (3)
//   · por persona: DNI (8) y menú (0 normal, 1 ayuda social)
// Si no caben en un código, se parte en varios que se muestran por turnos.
package io.github.tmrainer.asistencia.nucleo.qr

import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia

private const val MARCA = "81"
private const val POR_PARTE_LISTA = 80           // ~750 cifras: un QR que lee bien una cámara modesta

private val OCHO_CIFRAS = Regex("\\d{8}")
private val FECHA = Regex("\\d{4}-\\d{2}-\\d{2}")

/** Una marca: el DNI y el menú ("normal" | "ayuda_social"). */
data class MarcaLista(val dni: String, val menu: String)

/** Una parte leída del QR de antes. */
data class ParteLista(val fecha: String, val parte: Int, val total: Int, val marcas: List<MarcaLista>)

/**
 * Solo DNIs de 8 cifras (los demás no se pueden pasar así).
 * @return el texto de cada QR, en orden; vacío si no hay marcas o la fecha no
 *   es AAAA-MM-DD
 */
fun codificarListaQr(fecha: String, marcas: List<MarcaLista>): List<String> {
    val validas = marcas.filter { OCHO_CIFRAS.matches(it.dni) }
    if (!FECHA.matches(fecha) || validas.isEmpty()) return emptyList()
    val partes = validas.chunked(POR_PARTE_LISTA)
    fun dos(n: Int) = n.toString().padStart(2, '0')
    return partes.mapIndexed { n, grupo ->
        MARCA + fecha.replace("-", "") + dos(n + 1) + dos(partes.size) +
            grupo.size.toString().padStart(3, '0') +
            grupo.joinToString("") { it.dni + (if (it.menu == "ayuda_social") "1" else "0") }
    }
}

/** Lee una parte. NUNCA lanza: si no es un QR de esta app o no cuadra, null. */
fun decodificarParteQr(texto: String?): ParteLista? {
    // trim() de JavaScript quita también el BOM.
    val s = texto?.trim { it.isWhitespace() || it == '﻿' } ?: ""
    if (s.isEmpty() || !s.all { it in '0'..'9' } || !s.startsWith(MARCA) || s.length < 17) return null
    val f = s.substring(2, 10)
    val fecha = "${f.substring(0, 4)}-${f.substring(4, 6)}-${f.substring(6, 8)}"
    val parte = s.substring(10, 12).toInt()
    val total = s.substring(12, 14).toInt()
    val cuantas = s.substring(14, 17).toInt()
    val cuerpo = s.substring(17)
    if (parte == 0 || total == 0 || parte > total || cuerpo.length != cuantas * 9) return null
    val marcas = ArrayList<MarcaLista>()
    for (i in cuerpo.indices step 9) {
        val menu = cuerpo[i + 8]
        if (menu != '0' && menu != '1') return null
        marcas.add(MarcaLista(cuerpo.substring(i, i + 8), if (menu == '1') "ayuda_social" else "normal"))
    }
    return ParteLista(fecha, parte, total, marcas)
}

data class MarcasSumadas(val agregar: List<Asistencia>, val yaEstaban: Int, val desconocidos: Int)

/**
 * Qué pasa al sumar las marcas recibidas a las de un día. No modifica nada:
 * devuelve qué agregar, para que la pantalla lo aplique (y pueda deshacerlo).
 * @param personas activas ese día
 * @param yaMarcados ids ya marcados ese día
 */
fun sumarMarcasRecibidas(recibidas: List<MarcaLista>, personas: List<Afiliado>, yaMarcados: Set<String>): MarcasSumadas {
    val porDni = personas.associateBy { it.numeroDocumento.trim() }     // como Map: gana la última
    val agregar = ArrayList<Asistencia>()
    var yaEstaban = 0
    var desconocidos = 0
    val vistos = HashSet<String>()
    for (m in recibidas) {
        if (!vistos.add(m.dni)) continue
        val p = porDni[m.dni]
        when {
            p == null -> desconocidos += 1
            p.id in yaMarcados -> yaEstaban += 1
            else -> agregar.add(Asistencia(p.id, if (m.menu == "ayuda_social") "ayuda_social" else "normal"))
        }
    }
    return MarcasSumadas(agregar, yaEstaban, desconocidos)
}
