// Importar.kt contra web/js/importar.js: las mismas entradas difíciles, con el
// mismo «hoy», deben dar lo mismo que en la web, todo menos los id (uuid()).
// El recurso lo arma node nativo/herramientas/importar-casos.mjs.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.importar.FilaTabla
import io.github.tmrainer.asistencia.nucleo.importar.Importacion
import io.github.tmrainer.asistencia.nucleo.importar.ResumenImportacion
import io.github.tmrainer.asistencia.nucleo.importar.desarmarCelda
import io.github.tmrainer.asistencia.nucleo.importar.detectarColumnas
import io.github.tmrainer.asistencia.nucleo.importar.detectarSeparador
import io.github.tmrainer.asistencia.nucleo.importar.filasImportables
import io.github.tmrainer.asistencia.nucleo.importar.pareceCabecera
import io.github.tmrainer.asistencia.nucleo.importar.parsearFechaFlexible
import io.github.tmrainer.asistencia.nucleo.importar.parsearGrupoEtario
import io.github.tmrainer.asistencia.nucleo.importar.parsearTabla
import io.github.tmrainer.asistencia.nucleo.importar.parsearTipoAfiliado
import io.github.tmrainer.asistencia.nucleo.importar.parsearTipoDocumento
import io.github.tmrainer.asistencia.nucleo.importar.prepararImportacion
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class ImportarWebTest {

    private val casos = JSON.parseToJsonElement(recurso("importar-casos.json")).jsonObject
    private val hoy = casos["hoy"]!!.jsonPrimitive.content

    private fun casosDe(funcion: String) = casos["funciones"]!!.jsonObject[funcion]!!.jsonArray.map { it.jsonObject }

    private fun texto(e: JsonElement?) = (e as? JsonPrimitive)?.contentOrNull
    private fun textos(e: JsonElement) = e.jsonArray.map { it.jsonPrimitive.content }
    private fun json(lista: List<String>) = JsonArray(lista.map(::JsonPrimitive))

    @Test
    fun `Las funciones sueltas dan lo mismo que en la web`() {
        for (c in casosDe("detectarSeparador")) {
            igual(detectarSeparador(texto(c["entrada"])).toString(), texto(c["esperado"]), "detectarSeparador ${c["entrada"]}")
        }
        for (c in casosDe("parsearTabla")) {
            val e = c["entrada"]!!.jsonObject
            val obtenido = parsearTabla(texto(e["texto"]), texto(e["separador"])?.single())
            igual(JsonArray(obtenido.map(::comoJson)), c["esperado"], "parsearTabla $e")
        }
        for (c in casosDe("desarmarCelda")) {
            igual(desarmarCelda(texto(c["entrada"])), texto(c["esperado"]), "desarmarCelda ${c["entrada"]}")
        }
        for (c in casosDe("detectarColumnas")) {
            val r = detectarColumnas(textos(c["entrada"]!!))
            val esperado = c["esperado"]!!.jsonObject
            igual(columnas(r.mapa), esperado["mapa"], "detectarColumnas ${c["entrada"]}")
            igual(json(r.sinReconocer), esperado["sinReconocer"], "sinReconocer ${c["entrada"]}")
        }
        for (c in casosDe("parsearFechaFlexible")) {
            igual(parsearFechaFlexible(texto(c["entrada"]), hoy), texto(c["esperado"]), "parsearFechaFlexible ${c["entrada"]}")
        }
        for (c in casosDe("parsearGrupoEtario")) {
            igual(parsearGrupoEtario(texto(c["entrada"])), texto(c["esperado"]), "parsearGrupoEtario ${c["entrada"]}")
        }
        for (c in casosDe("parsearTipoAfiliado")) {
            igual(parsearTipoAfiliado(texto(c["entrada"])), texto(c["esperado"]), "parsearTipoAfiliado ${c["entrada"]}")
        }
        for (c in casosDe("parsearTipoDocumento")) {
            igual(parsearTipoDocumento(texto(c["entrada"])), texto(c["esperado"]), "parsearTipoDocumento ${c["entrada"]}")
        }
        for (c in casosDe("pareceCabecera")) {
            igual(pareceCabecera(textos(c["entrada"]!!)), c["esperado"]!!.jsonPrimitive.content.toBoolean(),
                "pareceCabecera ${c["entrada"]}")
        }
    }

    @Test
    fun `Cada importación da lo mismo que en la web, menos los id`() {
        for (c in casos["importaciones"]!!.jsonArray.map { it.jsonObject }) {
            val nombre = texto(c["nombre"])
            val padron = JSON.decodeFromJsonElement(Padron.serializer(), c["padron"]!!)
            val r = prepararImportacion(texto(c["texto"]), padron, hoy)
            val esperado = c["esperado"]!!.jsonObject
            for (campo in listOf("columnas", "sinReconocer", "filas", "error", "importables")) {
                igual(comoJson(r)[campo], esperado[campo], "$nombre: $campo")
            }
            // Con error, la web no trae resumen; aquí viene en ceros.
            if (esperado["resumen"] is JsonNull) {
                ok(r.error != null, "$nombre: sin resumen en la web, debería haber error")
                igual(r.resumen, ResumenImportacion(), "$nombre: resumen")
            } else {
                igual(comoJson(r)["resumen"], esperado["resumen"], "$nombre: resumen")
            }
        }
    }

    private fun comoJson(f: FilaTabla) = JsonObject(mapOf("celdas" to json(f.celdas), "linea" to JsonPrimitive(f.linea)))

    private fun columnas(mapa: Map<String, Int>) = JsonObject(mapa.mapValues { JsonPrimitive(it.value) })

    // Sin el id (al azar) ni el registro en la Municipalidad, que la web no tiene.
    private fun sinId(a: Afiliado) = JsonObject(JSON.encodeToJsonElement(Afiliado.serializer(), a).jsonObject - "id" - "registroMunicipal" - "registroMunicipalEn")

    private fun comoJson(r: Importacion) = JsonObject(mapOf(
        "columnas" to columnas(r.columnas),
        "sinReconocer" to json(r.sinReconocer),
        "filas" to JsonArray(r.filas.map { f ->
            JsonObject(mapOf(
                "linea" to JsonPrimitive(f.linea),
                "afiliado" to sinId(f.afiliado),
                "nombreCompleto" to JsonPrimitive(f.nombreCompleto),
                "estado" to JsonPrimitive(f.estado),
                "avisos" to json(f.avisos),
                "ceroAgregado" to JsonPrimitive(f.ceroAgregado)
            ))
        }),
        "resumen" to JsonObject(mapOf(
            "lista" to JsonPrimitive(r.resumen.lista),
            "incompleta" to JsonPrimitive(r.resumen.incompleta),
            "duplicada" to JsonPrimitive(r.resumen.duplicada),
            "vacia" to JsonPrimitive(r.resumen.vacia),
            "conCero" to JsonPrimitive(r.resumen.conCero)
        )),
        "error" to JsonPrimitive(r.error),
        "importables" to JsonArray(filasImportables(r.filas).map { JsonPrimitive(it.linea) })
    ))
}
