// Pruebas de la migración del APK 1.7: los textos de localStorage
// que la web deja con el respaldo ficticio cargado, traducidos aquí, deben dar
// lo mismo que recuperar el respaldo ficticio. Los casos límite del padrón
// empacado se comparan con desempacarPadron() de la web. Los dos recursos los
// arma node nativo/herramientas/localstorage-ficticio.mjs.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.almacen.Almacen
import io.github.tmrainer.asistencia.nucleo.almacen.ClaveWeb
import io.github.tmrainer.asistencia.nucleo.almacen.desempacarPadron
import io.github.tmrainer.asistencia.nucleo.almacen.leerDatosWeb
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs

class MigracionWebTest {

    private fun crudos(): Map<String, String> =
        JSON.parseToJsonElement(recurso("localstorage-ficticio.json")).jsonObject.mapValues { (it.value as JsonPrimitive).content }

    @Test
    fun `El padrón empacado se desempaca igual que en la web`() {
        val casos = JSON.parseToJsonElement(recurso("localstorage-casos.json")) as JsonArray
        for ((i, caso) in casos.withIndex()) {
            igual(desempacarPadron(caso.jsonObject["entrada"]!!), caso.jsonObject["esperado"]!!, "caso $i")
        }
    }

    @Test
    fun `Lo del 1_7 da lo mismo que recuperar la copia`() {
        val datos = leerDatosWeb(crudos())
        val web = assertIs<LecturaRespaldo.Lista>(datos.lectura)
        val copia = assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-ficticio.json")))
        igual(web.revision.partes, copia.revision.partes)
        igual(web.revision.resumen, copia.revision.resumen)
        // Al empacar, la web guarda la asistencia de cada día en el orden del
        // padrón, no en el que se marcó: misma gente, quizás otro orden.
        fun enOrden(p: Padron?) = p?.copy(atenciones = p.atenciones.map { at -> at.copy(asistencias = at.asistencias.sortedBy { it.afiliadoId }) })
        igual(enOrden(web.respaldo.padron), enOrden(copia.respaldo.padron))
        igual(web.respaldo.rendiciones, copia.respaldo.rendiciones)
        igual(web.respaldo.calendario, copia.respaldo.calendario)
        igual(web.respaldo.config, copia.respaldo.config)
        igual(datos.ultimaCopia, "2026-10-05")
        igual(datos.ilegibles, emptyList())
    }

    @Test
    fun `Una clave dañada no impide traer las demás`() {
        val c = crudos().toMutableMap()
        c[ClaveWeb.CONFIG] = "{roto"
        c[ClaveWeb.CALENDARIO] = "42"
        val datos = leerDatosWeb(c)
        val web = assertIs<LecturaRespaldo.Lista>(datos.lectura)
        igual(web.revision.partes, listOf("padron", "rendiciones"))
        igual(datos.ilegibles, listOf(ClaveWeb.CALENDARIO, ClaveWeb.CONFIG))
    }

    @Test
    fun `Sin claves de la app no hay nada que traer`() {
        val datos = leerDatosWeb(mapOf("otra.cosa" to "{}"))
        assertIs<LecturaRespaldo.Fallo>(datos.lectura)
        igual(datos.ultimaCopia, null)
    }

    @Test
    fun `Restaurado en el teléfono, queda la última copia del 1_7`() = runTest {
        val c = Files.createTempDirectory("migracion").toFile().also { it.deleteOnExit() }
        val e = Estado(Almacen(c), this, hoy = { "2026-10-20" }).cargar()
        val datos = leerDatosWeb(crudos())
        ok(e.restaurar(assertIs<LecturaRespaldo.Lista>(datos.lectura)))
        ok(e.registrarCopia(datos.ultimaCopia!!))
        igual(e.padron.value.afiliados.size, 40)
        igual(e.diasDesdeUltimaCopia(), 15)
        // Lo escrito se vuelve a leer igual: el padrón ya va sin casillas.
        val enDisco = JSON.parseToJsonElement(java.io.File(c, "padron.json").readText()) as JsonObject
        ok((enDisco["atenciones"] as JsonArray).all { "marcas" !in it.jsonObject }, "sin casillas en el archivo")
    }
}
