// Grupo «Respaldos» de web/js/casos-prueba.js (revisarRespaldo), más la ida y
// vuelta del archivo y el respaldo con contraseña cifrado por la web.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.Respaldo
import io.github.tmrainer.asistencia.nucleo.modelo.calendarioVacio
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.cifrarRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.descifrarRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.escribirRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.esRespaldoCifrado
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.revisarRespaldo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private fun json(texto: String) = Json.parseToJsonElement(texto)

fun recurso(nombre: String): String =
    RespaldosTest::class.java.getResource("/$nombre")?.readText()
        ?: error("Falta el recurso $nombre: corre node nativo/herramientas/generar-ficticio.mjs")

class RespaldosTest {

    @Test
    fun `Revisar un respaldo dice qué trae y rechaza lo dañado`() {
        val r = revisarRespaldo(buildJsonObject {
            put("padron", json("""{ "afiliados": [{}, {}], "atenciones": [{}] }"""))
            putJsonArray("rendiciones") { add(JSON.encodeToJsonElement(nuevaRendicion())) }
            put("calendario", JSON.encodeToJsonElement(calendarioVacio()))
            put("config", json("{}"))
        })
        igual(r.error, null)
        igual(r.partes.joinToString(","), "padron,rendiciones,calendario,config")
        ok(r.resumen.startsWith("2 personas en el padrón y 1 día de asistencia"), r.resumen)
        // Lo que no trae (null o ausente) no se toca.
        igual(revisarRespaldo(json("""{ "padron": null, "calendario": {} }""")).partes.joinToString(","), "calendario")
        // Un B-1 vacío no se cuenta como algo que restaurar.
        igual(revisarRespaldo(json("""{ "rendiciones": [], "calendario": {} }""")).partes.joinToString(","), "calendario")
        ok("dañado" in revisarRespaldo(json("""{ "padron": { "afiliados": "x" } }""")).error!!)
        ok("dañado" in revisarRespaldo(json("""{ "rendiciones": {} }""")).error!!)
        ok("no trae" in revisarRespaldo(json("{}")).error!!)
        ok(revisarRespaldo(null).error != null)
        ok(revisarRespaldo(json("[1]")).error != null)
    }

    @Test
    fun `Un respaldo de la web se lee entero y vuelve a escribirse sin perder ninguna clave`() {
        val texto = recurso("respaldo-ficticio.json")
        val lectura = assertIs<LecturaRespaldo.Lista>(leerRespaldo(texto))
        igual(lectura.revision.partes, listOf("padron", "rendiciones", "calendario", "config"))
        igual(lectura.respaldo.padron!!.afiliados.size, 40)
        igual(lectura.respaldo.rendiciones.size, 2)

        val original = Json.parseToJsonElement(texto)
        val reescrito = Json.parseToJsonElement(escribirRespaldo(lectura.respaldo))
        val perdidas = clavesPerdidas(original, reescrito, "")
        ok(perdidas.isEmpty(), "claves del archivo que no sobreviven la ida y vuelta:\n" + perdidas.joinToString("\n"))

        // Y lo reescrito se vuelve a leer igual.
        val otraVez = assertIs<LecturaRespaldo.Lista>(leerRespaldo(escribirRespaldo(lectura.respaldo)))
        igual(otraVez.respaldo, lectura.respaldo)
    }

    @Test
    fun `Un respaldo con contraseña cifrado por la web se abre aquí, y con otra contraseña no`() {
        val sobre = Json.parseToJsonElement(recurso("respaldo-ficticio-protegido.json"))
        ok(esRespaldoCifrado(sobre))
        val protegido = assertIs<LecturaRespaldo.Protegido>(leerRespaldo(recurso("respaldo-ficticio-protegido.json")))
        val abierto = descifrarRespaldo(protegido.sobre, "prueba-1234")
        val lectura = assertIs<LecturaRespaldo.Lista>(leerRespaldo(abierto))
        igual(lectura.respaldo.padron!!.afiliados.size, 40)
        assertFailsWith<AEADBadTagException> { descifrarRespaldo(protegido.sobre, "prueba-1235") }
    }

    @Test
    fun `Lo que se cifra aquí se abre aquí, y no se ve nada por fuera`() {
        val r = Respaldo(rendiciones = listOf(nuevaRendicion().copy(nombreCentro = "Secreto")))
        val sobre = cifrarRespaldo(r, "clave-larga")
        ok("Secreto" !in sobre.toString())
        val lectura = assertIs<LecturaRespaldo.Lista>(leerRespaldo(descifrarRespaldo(sobre, "clave-larga")))
        igual(lectura.respaldo.rendiciones[0].nombreCentro, "Secreto")
    }

    @Test
    fun `Lo que no es un respaldo no rompe nada`() {
        assertIs<LecturaRespaldo.Fallo>(leerRespaldo("esto no es json"))
        assertIs<LecturaRespaldo.Fallo>(leerRespaldo("[1, 2]"))
        assertIs<LecturaRespaldo.Fallo>(leerRespaldo("{}"))
        assertIs<LecturaRespaldo.Fallo>(leerRespaldo("""{ "padron": { "afiliados": "x" } }"""))
    }
}

/** Claves (con su ruta) que están en `a` y no en `b`. Las listas se comparan por posición. */
private fun clavesPerdidas(a: JsonElement, b: JsonElement?, ruta: String): List<String> {
    val faltan = ArrayList<String>()
    when (a) {
        is JsonObject -> {
            val bo = b as? JsonObject
            for ((k, v) in a) {
                if (bo == null || k !in bo) faltan.add("$ruta.$k")
                else faltan.addAll(clavesPerdidas(v, bo[k], "$ruta.$k"))
            }
        }
        is JsonArray -> {
            val ba = b as? JsonArray
            a.forEachIndexed { i, v -> faltan.addAll(clavesPerdidas(v, ba?.getOrNull(i), "$ruta[$i]")) }
        }
        else -> {}
    }
    return faltan
}
