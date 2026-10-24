// EscaneoDniWebTest.kt — EscaneoDni.kt contra web/js/escaneo-dni.js, caso por
// caso. nativo/herramientas/escaneo-dni-casos.mjs corre el módulo de la web
// sobre entradas sintéticas (MRZ con sus controles ICAO, ruido del OCR, filas
// partidas, anversos con tildes y ñ; todo inventado) y guarda lo que dio en
// escaneo-dni.esperado.json. Aquí se recalcula en Kotlin y se compara.
//
//   node nativo/herramientas/escaneo-dni-casos.mjs     (para regenerarlo)
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.dni.CampoCruzado
import io.github.tmrainer.asistencia.nucleo.dni.CamposMrz
import io.github.tmrainer.asistencia.nucleo.dni.EstadoCampo
import io.github.tmrainer.asistencia.nucleo.dni.FragmentoOcr
import io.github.tmrainer.asistencia.nucleo.dni.LecturaAnverso
import io.github.tmrainer.asistencia.nucleo.dni.LecturaMrz
import io.github.tmrainer.asistencia.nucleo.dni.buscarMrzTd1
import io.github.tmrainer.asistencia.nucleo.dni.combinarMrzYAnverso
import io.github.tmrainer.asistencia.nucleo.dni.digitoControlIcao
import io.github.tmrainer.asistencia.nucleo.dni.leerMrzTd1
import io.github.tmrainer.asistencia.nucleo.dni.leerPdf417DniAzul
import io.github.tmrainer.asistencia.nucleo.dni.normalizarLetras
import io.github.tmrainer.asistencia.nucleo.dni.parsearCadenaDni
import io.github.tmrainer.asistencia.nucleo.dni.plantillaAnonima
import io.github.tmrainer.asistencia.nucleo.dni.unirFilasOcr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.fail

class EscaneoDniWebTest {

    @Test
    fun `Las entradas sintéticas dan lo mismo que la web`() {
        val casos = Json.parseToJsonElement(recurso("escaneo-dni.esperado.json")).jsonObject["casos"]!!.jsonArray
        ok(casos.size > 500, "muy pocos casos: ${casos.size}")
        val distintos = ArrayList<String>()
        casos.forEachIndexed { i, c ->
            val funcion = c.jsonObject["funcion"]!!.jsonPrimitive.content
            val entrada = c.jsonObject["entrada"]!!.jsonObject
            val obtenido = calcular(funcion, entrada)
            val esperado = c.jsonObject["salida"]!!
            if (obtenido != esperado) distintos.add("caso $i ($funcion):\n  web:    $esperado\n  kotlin: $obtenido")
        }
        if (distintos.isNotEmpty()) fail("${distintos.size} de ${casos.size} casos distintos de la web:\n" + distintos.take(15).joinToString("\n"))
    }
}

// ------------------------------------------------------ de JSON a Kotlin y vuelta

private fun texto(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull
private fun textos(e: JsonElement?): List<String>? = (e as? JsonArray)?.map { texto(it)!! }

private fun calcular(funcion: String, e: JsonObject): JsonElement = when (funcion) {
    "digitoControlIcao" -> JsonPrimitive(digitoControlIcao(texto(e["texto"])))
    "leerMrzTd1" -> mrzJson(leerMrzTd1((e["lineas"] as? JsonArray)?.map { texto(it) }, texto(e["fechaRef"])!!))
    "buscarMrzTd1" -> mrzJson(buscarMrzTd1(textos(e["lineas"]), texto(e["fechaRef"])!!))
    "unirFilasOcr" -> lista(unirFilasOcr(fragmentos(e["fragmentos"])))
    "combinarMrzYAnverso" -> anversoJson(combinarMrzYAnverso(camposMrz(e["camposMrz"]), textos(e["filas"])))
    "deCamara" -> {
        val filasReverso = unirFilasOcr(fragmentos(e["reverso"]))
        val mrz = buscarMrzTd1(filasReverso, texto(e["fechaRef"])!!)
        val filasFrente = unirFilasOcr(fragmentos(e["frente"]))
        buildJsonObject {
            put("filasReverso", lista(filasReverso))
            put("mrz", mrzJson(mrz))
            put("filasFrente", lista(filasFrente))
            put("anverso", anversoJson(mrz?.let { combinarMrzYAnverso(it.campos, filasFrente) }))
        }
    }
    "normalizarLetras" -> JsonPrimitive(normalizarLetras(texto(e["texto"])))
    "plantillaAnonima" -> JsonPrimitive(plantillaAnonima(texto(e["texto"])))
    "leerPdf417DniAzul" -> leerPdf417DniAzul(texto(e["cadena"])).let { r ->
        buildJsonObject {
            put("largo", r.largo)
            put("campos", buildJsonObject {
                put("numeroDocumento", r.campos.numeroDocumento)
                put("apellidoPaterno", r.campos.apellidoPaterno)
                put("apellidoMaterno", r.campos.apellidoMaterno)
                put("nombres", r.campos.nombres)
                put("sexo", r.campos.sexo)
                put("vencimiento", r.campos.vencimiento)
            })
        }
    }
    "parsearCadenaDni" -> parsearCadenaDni(texto(e["cadena"])).let { r ->
        buildJsonObject {
            put("tipoDocumento", r.tipoDocumento)
            put("numeroDocumento", r.numeroDocumento)
            put("apellidoPaterno", r.apellidoPaterno)
            put("apellidoMaterno", r.apellidoMaterno)
            put("nombres", r.nombres)
            put("fechaNacimiento", r.fechaNacimiento)
            put("sexo", r.sexo)
            put("camposNoLeidos", lista(r.camposNoLeidos))
            put("confianza", r.confianza.clave)
        }
    }
    else -> error("función desconocida: $funcion")
}

private fun lista(l: List<String>) = JsonArray(l.map { JsonPrimitive(it) })

private fun fragmentos(e: JsonElement?): List<FragmentoOcr>? = (e as? JsonArray)?.map {
    val f = it.jsonObject
    FragmentoOcr(
        texto = texto(f["texto"])!!,
        arriba = f["arriba"]!!.jsonPrimitive.int,
        abajo = f["abajo"]!!.jsonPrimitive.int,
        izquierda = f["izquierda"]!!.jsonPrimitive.int,
        derecha = f["derecha"]?.jsonPrimitive?.int,
        angulo = f["angulo"]?.jsonPrimitive?.double
    )
}

private fun camposMrz(e: JsonElement?): CamposMrz? = (e as? JsonObject)?.let {
    CamposMrz(apellidos = textos(it["apellidos"]) ?: emptyList(), nombres = texto(it["nombres"]) ?: "")
}

private fun mrzJson(r: LecturaMrz?): JsonElement = if (r == null) JsonNull else buildJsonObject {
    put("estructura", r.estructura)
    put("controles", buildJsonObject {
        put("documento", r.controles.documento)
        put("nacimiento", r.controles.nacimiento)
        put("vencimiento", r.controles.vencimiento)
        put("general", r.controles.general)
    })
    put("campos", buildJsonObject {
        put("pais", r.campos.pais)
        put("numeroDocumento", r.campos.numeroDocumento)
        put("fechaNacimiento", r.campos.fechaNacimiento)
        put("sexo", r.campos.sexo)
        put("vencimiento", r.campos.vencimiento)
        put("apellidos", lista(r.campos.apellidos))
        put("nombres", r.campos.nombres)
    })
    // Solo buscarMrzTd1 trae las líneas (en la web, leerMrzTd1 no tiene esa clave).
    if (r.lineas.isNotEmpty()) put("lineas", lista(r.lineas))
}

private fun cruceJson(c: CampoCruzado) = buildJsonObject {
    put("valor", c.valor)
    put("estado", c.estado.clave)
    if (c.estado == EstadoCampo.DUDOSO) put("opciones", lista(c.opciones))
}

private fun anversoJson(a: LecturaAnverso?): JsonElement = if (a == null) JsonNull else buildJsonObject {
    put("apellidoPaterno", cruceJson(a.apellidoPaterno))
    put("apellidoMaterno", cruceJson(a.apellidoMaterno))
    put("nombres", cruceJson(a.nombres))
}
