// Pruebas del almacén de archivos (fase 2). Equivalen a lo que la web prueba
// de leer()/guardar() en localStorage: tolerar lo ausente y lo corrupto, no
// borrar nada en silencio, y que lo guardado tenga el formato del respaldo.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.almacen.Almacen
import io.github.tmrainer.asistencia.nucleo.almacen.Clave
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class AlmacenTest {

    private fun carpeta(): File = Files.createTempDirectory("almacen").toFile().also { it.deleteOnExit() }

    @Test
    fun `Lo que no existe vuelve null, sin avisos`() {
        val a = Almacen(carpeta())
        igual(a.leer(Clave.PADRON), null)
        igual(a.avisosAlmacenamiento().size, 0)
        igual(a.usoAlmacenamiento(), 0L)
    }

    @Test
    fun `Lo guardado se lee igual y no deja archivos temporales`() {
        val c = carpeta()
        val a = Almacen(c)
        ok(a.guardar(Clave.CONFIG, Json.parseToJsonElement("""{ "precioMenuNormalCent": 300 }""")))
        igual(Almacen(c).leer(Clave.CONFIG)!!.jsonObject["precioMenuNormalCent"].toString(), "300")
        igual(c.listFiles()!!.map { it.name }.sorted(), listOf("config.json"))
    }

    @Test
    fun `Un archivo corrupto se aparta, se avisa y se arranca vacío`() {
        val c = carpeta()
        File(c, "padron.json").writeText("{ esto no es json")
        val a = Almacen(c)
        igual(a.leer(Clave.PADRON), null)
        ok(File(c, "padron.json.respaldo").exists(), "se conserva aparte")
        ok(!File(c, "padron.json").exists())
        ok(a.avisosAlmacenamiento().single().contains("padron.json.respaldo"))
        // Y se puede volver a guardar encima.
        ok(a.guardar(Clave.PADRON, """{"version":3,"afiliados":[],"atenciones":[]}"""))
        ok(a.leer(Clave.PADRON) != null)
    }

    @Test
    fun `JSON válido con otra forma también se aparta`() {
        val c = carpeta()
        File(c, "config.json").writeText("[1, 2, 3]")
        val a = Almacen(c)
        ok(a.leer(Clave.CONFIG) != null, "es JSON: leer() lo entrega")
        a.apartarIlegible(Clave.CONFIG)
        ok(File(c, "config.json.respaldo").exists())
        igual(a.leer(Clave.CONFIG), null)
    }

    @Test
    fun `Guardar el mismo texto no vuelve a escribir`() {
        val c = carpeta()
        val a = Almacen(c)
        a.guardar(Clave.CALENDARIO, "{\"version\":1}")
        val f = File(c, "calendario.json")
        f.setLastModified(1_000_000L)
        a.guardar(Clave.CALENDARIO, "{\"version\":1}")
        igual(f.lastModified(), 1_000_000L, "sin cambios, el archivo no se toca")
        a.guardar(Clave.CALENDARIO, "{\"version\":2}")
        ok(f.lastModified() != 1_000_000L)
    }

    @Test
    fun `Borrar todo deja la carpeta sin datos ni apartados`() {
        val c = carpeta()
        val a = Almacen(c)
        a.guardar(Clave.PADRON, "{}")
        File(c, "config.json.respaldo").writeText("x")
        ok(a.borrarTodo())
        igual(c.listFiles()!!.size, 0)
    }
}
