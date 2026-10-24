// QrCruzadoTest.kt — el QR del núcleo contra el de la WEB.
// nativo/herramientas/qr-cruzado.mjs corre los módulos de la web (Node 22) y
// escribe qr-cruzado.json: matrices de qrcode-generator y los envíos del
// respaldo ficticio (JSON y textos de los QR). Aquí se exige:
//  - la misma matriz, módulo por módulo;
//  - el mismo JSON de cada paquete, y leer los QR que armó la web;
//  - que la web lea los QR que arma Kotlin: se escriben en
//    build/qr-cruzado/partes-kotlin.json y, si hay Node 22 o más nuevo, se le
//    pasan a qr-cruzado.mjs --verificar. Sin Node, esa parte se salta y lo dice.
// Los bytes de deflate pueden diferir entre zlib de Node y el del JDK: por eso
// se compara lo descomprimido (el JSON), no el texto base45.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.Respaldo
import io.github.tmrainer.asistencia.nucleo.qr.Paquete
import io.github.tmrainer.asistencia.nucleo.qr.codificarParaQr
import io.github.tmrainer.asistencia.nucleo.qr.juntarPartesQr
import io.github.tmrainer.asistencia.nucleo.qr.leerParteQr
import io.github.tmrainer.asistencia.nucleo.qr.matrizQr
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDeAsistencia
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDeEntregas
import io.github.tmrainer.asistencia.nucleo.qr.paqueteDelPadron
import io.github.tmrainer.asistencia.nucleo.qr.textoJson
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.fail

class QrCruzadoTest {

    private val web: JsonObject = Json.parseToJsonElement(recurso("qr-cruzado.json")).jsonObject
    private val envios = web["envios"]!!.jsonObject

    /** Los mismos paquetes que arma qr-cruzado.mjs, desde el mismo respaldo ficticio. */
    private fun paquetes(): Map<String, Paquete> {
        val r: Respaldo = assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-ficticio.json"))).respaldo
        val padron = r.padron!!
        val fechas = padron.atenciones.map { it.fecha }
        return mapOf(
            "padron" to paqueteDelPadron(padron),
            "asistencia-un-dia" to paqueteDeAsistencia(padron, listOf(fechas.last())).paquete,
            "asistencia-todo" to paqueteDeAsistencia(padron, fechas).paquete,
            "entregas" to paqueteDeEntregas(r.calendario!!)
        )
    }

    private fun textos(clave: String, nombre: String) = envios[nombre]!!.jsonObject[clave]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `La matriz del QR es la misma que arma la web, módulo por módulo`() {
        val matrices = web["matrices"]!!.jsonArray
        ok(matrices.size >= 10)
        for (el in matrices) {
            val m = el.jsonObject
            val texto = m["texto"]!!.jsonPrimitive.content
            val filas = m["filas"]!!.jsonArray.map { it.jsonPrimitive.content }
            val q = matrizQr(texto)
            val nombre = "${texto.take(20)}… (${texto.length} car.)"
            igual(q.version, m["version"]!!.jsonPrimitive.int, "versión de $nombre")
            igual(q.tamano, filas.size, "tamaño de $nombre")
            for (f in filas.indices) {
                val fila = (0 until q.tamano).joinToString("") { if (q.oscuro(f, it)) "1" else "0" }
                if (fila != filas[f]) fail("$nombre: la fila $f no coincide\n  web:    ${filas[f]}\n  kotlin: $fila")
            }
        }
    }

    @Test
    fun `Del respaldo ficticio sale el mismo JSON que en la web`() {
        for ((nombre, paquete) in paquetes()) {
            igual(textoJson(paquete), envios[nombre]!!.jsonObject["json"]!!.jsonPrimitive.content, nombre)
        }
    }

    @Test
    fun `Kotlin lee los QR que arma la web`() {
        for ((nombre, paquete) in paquetes()) {
            for (clave in listOf("partes", "partesChicas")) {
                val leidas = textos(clave, nombre).map { leerParteQr(it) ?: fail("$nombre/$clave: no se reconoce $it") }
                val vuelta = juntarPartesQr(leidas.reversed()) ?: fail("$nombre/$clave: no se pudo juntar")
                igual(vuelta, paquete, "$nombre/$clave")
            }
        }
    }

    @Test
    fun `La web lee los QR que arma Kotlin (con Node)`() {
        val salida = buildJsonObject {
            for ((nombre, paquete) in paquetes()) {
                put(nombre, buildJsonObject {
                    val partes = codificarParaQr(paquete)
                    val chicas = codificarParaQr(paquete, porParte = 300)
                    for (p in listOf(partes, chicas)) igual(juntarPartesQr(p.map { leerParteQr(it)!! }), paquete, "$nombre: Kotlin lee lo suyo")
                    putJsonArray("partes") { partes.forEach { add(JsonPrimitive(it)) } }
                    putJsonArray("partesChicas") { chicas.forEach { add(JsonPrimitive(it)) } }
                })
            }
        }
        val archivo = File("build/qr-cruzado/partes-kotlin.json")
        archivo.parentFile.mkdirs()
        archivo.writeText(salida.toString())

        val script = File("../herramientas/qr-cruzado.mjs")
        if (!nodeSirve() || !script.exists()) {
            println("Sin Node 22 o más nuevo: la web no verificó ${archivo.path}. " +
                "A mano: node nativo/herramientas/qr-cruzado.mjs --verificar nativo/nucleo/${archivo.path}")
            return
        }
        val proceso = ProcessBuilder("node", script.path, "--verificar", archivo.path).redirectErrorStream(true).start()
        val texto = proceso.inputStream.bufferedReader().readText()
        ok(proceso.waitFor(60, TimeUnit.SECONDS), "qr-cruzado.mjs no terminó")
        println(texto)
        igual(proceso.exitValue(), 0, "la web no leyó lo que armó Kotlin:\n$texto")
    }

    private fun nodeSirve(): Boolean = try {
        val p = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
        val version = p.inputStream.bufferedReader().readText().trim()
        p.waitFor(10, TimeUnit.SECONDS) && (version.removePrefix("v").substringBefore('.').toIntOrNull() ?: 0) >= 22
    } catch (e: IOException) {
        false
    }
}
