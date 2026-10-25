// Trae los datos del APK 1.7 (la web dentro de Capacitor), que se instala por
// encima con el mismo paquete pero guardaba todo en el localStorage del WebView.
// La cáscara Android saca los textos crudos de las claves b1.*; aquí se
// traducen al respaldo de siempre, que Estado.restaurar() escribe.
package io.github.tmrainer.asistencia.nucleo.almacen

import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Las claves de localStorage de la web (CLAVES en modelo.js). */
object ClaveWeb {
    const val RENDICIONES = "b1.rendiciones.v1"
    const val PADRON = "b1.padron.v1"
    const val CALENDARIO = "b1.calendario.v1"
    const val CONFIG = "b1.config.v1"
    const val RESPALDO = "b1.respaldo.v1"
    const val OLLA = "b1.olla.v1"
}

/**
 * Lo que trajo el 1.7: la lectura del respaldo armado con sus claves, cuándo
 * guardó la última copia (o null) y las claves que no se pudieron leer.
 */
data class DatosWeb(val lectura: LecturaRespaldo, val ultimaCopia: String?, val ilegibles: List<String>)

/** Traduce los textos crudos de localStorage (clave b1.* → texto) al respaldo de la app. */
fun leerDatosWeb(crudos: Map<String, String>): DatosWeb {
    val ilegibles = ArrayList<String>()
    // Como leer() de la web: lo que no es JSON, o no es un objeto o lista, no se lee.
    fun parte(clave: String): JsonElement? {
        val texto = crudos[clave] ?: return null
        val json = try { JSON.parseToJsonElement(texto) } catch (e: SerializationException) { null }
        if (json is JsonObject || json is JsonArray) return json
        ilegibles.add(clave)
        return null
    }
    val respaldo = buildJsonObject {
        put("aplicacion", JsonPrimitive("rendicion-b1"))
        parte(ClaveWeb.PADRON)?.let { put("padron", desempacarPadron(it)) }
        parte(ClaveWeb.RENDICIONES)?.let { put("rendiciones", it) }
        parte(ClaveWeb.CALENDARIO)?.let { put("calendario", it) }
        parte(ClaveWeb.CONFIG)?.let { put("config", it) }
    }
    val ultimaCopia = ((parte(ClaveWeb.RESPALDO) as? JsonObject)?.get("ultimoEn") as? JsonPrimitive)?.contentOrNull?.takeIf { esIso(it) }
    return DatosWeb(leerRespaldo(respaldo), ultimaCopia, ilegibles)
}

// El padrón de localStorage va empacado: la asistencia de cada día en casillas.

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** Texto de casillas → posiciones marcadas, de menor a mayor. */
private fun deCasillas(texto: String): List<Int> {
    val posiciones = ArrayList<Int>()
    for ((g, c) in texto.withIndex()) {
        val n = B64.indexOf(c)
        for (b in 0 until 6) if (n and (1 shl b) != 0) posiciones.add(g * 6 + b)
    }
    return posiciones
}

/** Formato de desarrollo: un UUID iba como «~» y sus 16 bytes en base64url. */
private fun idLargo(id: JsonElement): JsonElement {
    val texto = (id as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return id
    if (texto.length != 23 || texto[0] != '~') return id
    val hex = StringBuilder()
    var acumulado = 0
    var bits = 0
    for (c in texto.substring(1)) {
        acumulado = (acumulado shl 6) or B64.indexOf(c)
        bits += 6
        while (bits >= 4 && hex.length < 32) {
            bits -= 4
            hex.append(((acumulado shr bits) and 15).toString(16))
        }
        acumulado = acumulado and ((1 shl bits) - 1)
    }
    val h = hex.toString()
    return JsonPrimitive("${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}")
}

/** Falso en JavaScript: ausente, null, false, 0 o "". */
private fun JsonElement?.falso(): Boolean = when (this) {
    null, is JsonNull -> true
    is JsonPrimitive -> if (isString) content.isEmpty() else booleanOrNull == false || doubleOrNull == 0.0
    else -> false
}

private fun asistencia(afiliadoId: JsonElement?, social: Boolean) = buildJsonObject {
    if (afiliadoId != null) put("afiliadoId", afiliadoId)
    put("tipoMenu", JsonPrimitive(if (social) "ayuda_social" else "normal"))
}

/** El camino de vuelta de empacarPadron(). Lo que ya viene sin empacar, pasa igual. */
fun desempacarPadron(padron: JsonElement): JsonElement {
    if (padron !is JsonObject) return padron
    val atenciones = padron["atenciones"] as? JsonArray ?: return padron
    val afiliados = padron["afiliados"] as? JsonArray ?: JsonArray(emptyList())
    val nuevas = atenciones.map { at ->
        val m = (at as? JsonObject)?.get("marcas")
        if (m.falso()) return@map at
        val resto = (at as JsonObject).filterKeys { it != "marcas" }
        val marcas = m as? JsonObject ?: return@map at
        val vino = marcas["vino"]
        val ids = marcas["ids"]
        val asistencias: List<JsonElement> = when {
            vino is JsonPrimitive && vino.isString -> {
                val social = deCasillas((marcas["social"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "").toSet()
                val fuera = (marcas["fuera"] as? JsonArray) ?: JsonArray(emptyList())
                deCasillas(vino.content)
                    .filter { i -> !afiliados.getOrNull(i).falso() }
                    .map { i -> asistencia((afiliados[i] as? JsonObject)?.get("id"), i in social) } + fuera
            }
            ids is JsonArray -> {
                val social = (marcas["social"] as? JsonArray)?.toSet() ?: emptySet()
                ids.map { id -> asistencia(idLargo(id), id in social) }
            }
            else -> return@map at
        }
        JsonObject(resto + ("asistencias" to JsonArray(asistencias)))
    }
    return JsonObject(padron + ("atenciones" to JsonArray(nuevas)))
}
