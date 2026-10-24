// Migracion.kt — migrarPadron() de web/js/modelo.js: cualquier versión → v3.
// Trabaja sobre el JSON crudo porque las versiones viejas traen campos que el
// modelo actual ya no tiene (racionesServidas, racionesNormales…).
package io.github.tmrainer.asistencia.nucleo.modelo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull

/** El lector de JSON del respaldo: tolera campos de más y nulos donde no van. */
val JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

private fun JsonObject.texto(clave: String): String? =
    (this[clave] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.entero(clave: String): Long? =
    (this[clave] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

private inline fun <reified T> JsonObject.decodificar(): T? =
    runCatching { JSON.decodeFromJsonElement<T>(this) }.getOrNull()

/**
 * Migración del padrón a la v3. No inventa datos.
 *
 * - v1 → v2: racionesServidas/racionesCasoSocial se separaban en dos cuentas.
 * - v2 → v3: las raciones ya no se escriben, se cuentan de la asistencia. Los
 *   días que traían cuentas escritas a mano y no tienen asistencia marcada
 *   conservan esas cifras en `legado`.
 *
 * Diferencia con la web: aquí `sinInscribir` se conserva; la web lo perdía al
 * restaurar un respaldo.
 */
fun migrarPadron(datos: JsonElement?): Padron {
    val obj = datos as? JsonObject ?: return padronVacio()

    val afiliados = (obj["afiliados"] as? JsonArray ?: JsonArray(emptyList()))
        .mapNotNull { (it as? JsonObject)?.decodificar<Afiliado>() }
        .map { a -> if (a.tipoDocumento == "sin_documento") a.copy(tipoDocumento = "dni") else a }

    val atenciones = ArrayList<Atencion>()
    for (el in obj["atenciones"] as? JsonArray ?: JsonArray(emptyList())) {
        val at = el as? JsonObject ?: continue
        val asistencias = (at["asistencias"] as? JsonArray ?: JsonArray(emptyList()))
            .mapNotNull { (it as? JsonObject)?.decodificar<Asistencia>() }
        var base = nuevaAtencion(null).copy(
            id = at.texto("id")?.takeIf { it.isNotEmpty() } ?: uuid(),
            fecha = at.texto("fecha") ?: "",
            nota = at.texto("nota") ?: "",
            asistencias = asistencias,
            precioMenuNormalCent = at.entero("precioMenuNormalCent"),
            precioMenuAyudaSocialCent = at.entero("precioMenuAyudaSocialCent"),
            precioTomadoDeConfig = false,
            sinInscribir = (at["sinInscribir"] as? JsonObject)?.decodificar<SinInscribir>()
        )
        val legado = at["legado"]
        if (legado is JsonObject) {
            base = base.copy(legado = legado.decodificar<Legado>())
        } else if (asistencias.isEmpty()) {
            // Cuentas escritas a mano en v1 o v2.
            val total = at.entero("racionesServidas")
            val social = at.entero("racionesCasoSocial")
            val normalesV2 = at.entero("racionesNormales")
            val socialV2 = at.entero("racionesAyudaSocial")

            val normales = normalesV2
                ?: if (total == null) null else (if (social == null) total else total - social)
            val ayuda = socialV2 ?: social

            if (normales != null || ayuda != null) {
                base = base.copy(legado = Legado(
                    racionesNormales = (normales ?: 0).toInt(),
                    racionesAyudaSocial = (ayuda ?: 0).toInt()
                ))
            }
        }
        atenciones.add(base)
    }
    return Padron(version = VERSION_PADRON, afiliados = afiliados, atenciones = atenciones)
}

/** Atajo para pasar un padrón ya tipado por la migración (idempotente). */
fun migrarPadron(padron: Padron): Padron = migrarPadron(JSON.encodeToJsonElement(Padron.serializer(), padron))

/** Lo que la web llama `datos.padron === null`: en Kotlin, JsonNull también es «no trae». */
internal fun JsonElement?.esNulo(): Boolean = this == null || this is JsonNull
