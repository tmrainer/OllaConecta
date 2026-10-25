// EnvioTest.kt — el correo a UNACEM que arma el teléfono es el mismo que
// armaba el script de la nube: asunto, texto y nombre del adjunto, comparados
// con armarCorreo() de la web (herramientas/correo-casos.mjs). El adjunto es
// excelDeAsistencia / excelDelPadron, que DocumentosOraculoTest ya compara.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.documentos.excelDeAsistencia
import io.github.tmrainer.asistencia.nucleo.envio.correoDeAsistencia
import io.github.tmrainer.asistencia.nucleo.envio.correoDelPadron
import io.github.tmrainer.asistencia.nucleo.envio.esDiaDePadron
import io.github.tmrainer.asistencia.nucleo.envio.nombreArchivo
import io.github.tmrainer.asistencia.nucleo.envio.pareceCorreo
import io.github.tmrainer.asistencia.nucleo.envio.quincena
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class EnvioTest {

    private val padron: Padron by lazy {
        assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-ficticio.json"))).respaldo.padron!!
    }

    @Test
    fun `El correo es el mismo que armaba la web`() {
        val datos = JSON.parseToJsonElement(recurso("correo-casos.json")).jsonObject
        val olla = datos.getValue("olla").jsonPrimitive.content
        val quien = datos.getValue("quien").jsonPrimitive.content
        val hora = datos.getValue("hora").jsonPrimitive.content
        val casos = datos.getValue("casos").jsonArray.map { it.jsonObject }
        ok(casos.size >= 10, "Muy pocos casos: ${casos.size}")
        for (caso in casos) {
            val fecha = caso.getValue("fecha").jsonPrimitive.content
            val tipo = caso.getValue("tipo").jsonPrimitive.content
            val correo = if (tipo == "asistencia") correoDeAsistencia(padron, fecha, fecha, olla, hora, quien)
            else {
                igual(quincena(fecha), caso.getValue("inicio").jsonPrimitive.content to caso.getValue("fin").jsonPrimitive.content, "quincena $fecha")
                igual(esDiaDePadron(fecha), caso.getValue("esDiaDePadron").jsonPrimitive.boolean, "esDiaDePadron $fecha")
                correoDelPadron(padron, fecha, olla, hora, quien)
            }
            igual(correo.asunto, caso.getValue("asunto").jsonPrimitive.content, "asunto $tipo $fecha")
            igual(correo.cuerpo, caso.getValue("cuerpo").jsonPrimitive.content, "cuerpo $tipo $fecha")
            igual(correo.archivo, caso.getValue("archivo").jsonPrimitive.content, "archivo $tipo $fecha")
        }
    }

    @Test
    fun `El adjunto de la asistencia es el Excel de ese día`() {
        val c = correoDeAsistencia(padron, "2026-09-15", "2026-09-15", "Olla", "20:15")
        assertContentEquals(excelDeAsistencia(padron, "2026-09-15", "2026-09-15", "Olla"), c.bytes)
    }

    @Test
    fun `Sin nombre de la olla no se inventa uno`() {
        val c = correoDeAsistencia(Padron(), "2026-10-24", "2026-10-24", "  ", "09:00")
        igual(c.asunto, "Asistencia · 24-10-26")
        ok(c.cuerpo.startsWith("Asistencia de la olla, 24-10-26."), c.cuerpo)
        ok("Recaudado: sin precio del menú, no se calcula." in c.cuerpo || "Recaudado: S/ 0.00." in c.cuerpo, c.cuerpo)
        ok("Enviado desde OllaConecta a las 09:00." in c.cuerpo, "sin quién, no se nombra a nadie")
        igual(c.archivo, "Asistencia 2026-10-24.xlsx")
    }

    @Test
    fun `Nombre de archivo y correo`() {
        igual(nombreArchivo("Padrón Ñaña / 2026", "xlsx"), "Padron Nana 2026.xlsx")
        ok(pareceCorreo("asistencia@unacem.com.pe"))
        ok(pareceCorreo("  a@b.pe "))
        ok(!pareceCorreo("unacem.com.pe"))
        ok(!pareceCorreo("a@b"))
        ok(!pareceCorreo("a b@c.pe"))
    }
}
