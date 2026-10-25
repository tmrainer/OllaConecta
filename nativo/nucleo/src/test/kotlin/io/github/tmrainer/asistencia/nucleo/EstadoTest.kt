// Pruebas del estado en memoria y su guardado: guardarPronto escribe 400 ms
// después del último cambio, guardarYa escribe de inmediato, al ocultarse se
// escribe lo pendiente, hoy sigue al precio, y el recordatorio de copia a los 14 días.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.almacen.Almacen
import io.github.tmrainer.asistencia.nucleo.almacen.Clave
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import io.github.tmrainer.asistencia.nucleo.estado.Guardado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.escribirRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class EstadoTest {

    private fun carpeta(): File = Files.createTempDirectory("estado").toFile().also { it.deleteOnExit() }

    private var hoy = "2026-10-20"

    private fun TestScope.estado(c: File = carpeta()) =
        Estado(Almacen(c), this, hoy = { hoy }, ahora = { "${hoy}T12:00:00.000Z" }).cargar()

    @Test
    fun `Sin nada guardado arranca vacío y guardado`() = runTest {
        val e = estado()
        igual(e.padron.value.afiliados.size, 0)
        igual(e.config.value.precioMenuNormalCent, null)
        igual(e.rendiciones.value.size, 0)
        igual(e.guardado.value, Guardado.GUARDADO)
        ok(!e.hayDatosGuardados())
    }

    @Test
    fun `Varios cambios seguidos producen una sola escritura, 400 ms después del último`() = runTest {
        val c = carpeta()
        val e = estado(c)
        e.cambiarPadron { it.copy(afiliados = it.afiliados + nuevoAfiliado(hoy).copy(id = "a")) }
        igual(e.guardado.value, Guardado.PENDIENTE)
        advanceTimeBy(300); runCurrent()
        ok(!File(c, "padron.json").exists(), "todavía no se escribió")
        e.cambiarPadron { it.copy(afiliados = it.afiliados + nuevoAfiliado(hoy).copy(id = "b")) }
        advanceTimeBy(300); runCurrent()
        ok(!File(c, "padron.json").exists(), "el segundo toque reinició el plazo")
        advanceTimeBy(101); runCurrent()
        igual(e.guardado.value, Guardado.GUARDADO)
        val guardado = JSON.decodeFromString(Padron.serializer(), File(c, "padron.json").readText())
        igual(guardado.afiliados.map { it.id }, listOf("a", "b"))
        ok(!File(c, "config.json").exists(), "solo se escribe lo que cambió")
    }

    @Test
    fun `Al ocultarse se escribe lo pendiente sin esperar, y guardarYa escribe todo`() = runTest {
        val c = carpeta()
        val e = estado(c)
        e.cambiarCalendario { it.copy(fechaLimiteBase = "2026-11-15") }
        ok(e.guardarPendiente())
        ok(File(c, "calendario.json").exists())
        advanceTimeBy(500); runCurrent()
        igual(e.guardado.value, Guardado.GUARDADO, "el plazo cumplido solo avisa")

        ok(e.guardarYa())
        ok(File(c, "padron.json").exists() && File(c, "config.json").exists() && File(c, "rendiciones.json").exists())
    }

    @Test
    fun `Lo guardado vuelve igual al cargar de nuevo`() = runTest {
        val c = carpeta()
        val e = estado(c)
        e.cambiarConfig { it.copy(precioMenuNormalCent = 300, precioMenuAyudaSocialCent = 100) }
        e.cambiarRendiciones { listOf(nuevaRendicion().copy(id = "r1", nombreCentro = "Olla X")) }
        e.cambiarCalendario { it.copy(periodicidad = "semanal") }
        ok(e.guardarYa())
        val otra = estado(c)
        igual(otra.config.value, e.config.value)
        igual(otra.rendiciones.value, e.rendiciones.value)
        igual(otra.calendario.value, e.calendario.value)
        igual(otra.padron.value, e.padron.value)
    }

    @Test
    fun `El día de hoy sigue al precio de la configuración`() = runTest {
        val e = estado()
        e.cambiarConfig { it.copy(precioMenuNormalCent = 300, precioMenuAyudaSocialCent = 100) }
        e.cambiarPadron { it.copy(atenciones = listOf(nuevaAtencion(e.preciosActuales(), hoy).copy(id = "hoy"))) }
        igual(e.padron.value.atenciones[0].precioMenuNormalCent, 300L)
        e.cambiarConfig { it.copy(precioMenuNormalCent = 350) }
        igual(e.padron.value.atenciones[0].precioMenuNormalCent, 350L, "hoy tomó el precio nuevo")
        igual(e.padron.value.atenciones[0].precioMenuAyudaSocialCent, 100L, "los DOS precios")
        // Un día con precio propio no se toca.
        e.cambiarPadron { p -> p.copy(atenciones = p.atenciones.map { it.copy(precioTomadoDeConfig = false) }) }
        e.cambiarConfig { it.copy(precioMenuNormalCent = 400) }
        igual(e.padron.value.atenciones[0].precioMenuNormalCent, 350L)
    }

    @Test
    fun `Editar el B-1 en curso anota cuándo y deja su encabezado para sugerirlo`() = runTest {
        val e = estado()
        e.cambiarRendiciones { listOf(nuevaRendicion().copy(id = "r1"), nuevaRendicion().copy(id = "r0")) }
        e.cambiarRendicion("r1") { it.copy(nombreCentro = "Olla Y", fechaRendicion = "2026-10-01") }
        igual(e.rendiciones.value[0].nombreCentro, "Olla Y")
        igual(e.rendiciones.value[0].actualizadoEn, "2026-10-20T12:00:00.000Z")
        igual(e.config.value.ultimoCentro?.nombreCentro, "Olla Y")
        igual(e.config.value.ultimoCentro?.guardadoEn, "2026-10-01")
        e.cambiarRendicion("r0") { it.copy(nombreCentro = "Vieja") }
        igual(e.config.value.ultimoCentro?.nombreCentro, "Olla Y", "un B-1 anterior no cambia las sugerencias")
        e.cambiarRendicion("no-existe") { it.copy(nombreCentro = "X") }
        igual(e.rendiciones.value.size, 2)
    }

    @Test
    fun `Recuperar una copia escribe solo lo que trae y recarga`() = runTest {
        val c = carpeta()
        val e = estado(c)
        e.cambiarCalendario { it.copy(fechaLimiteBase = "2026-11-15") }
        e.cambiarPadron { it.copy(afiliados = listOf(nuevoAfiliado(hoy).copy(id = "local"))) }
        // Un cambio pendiente no debe pisar lo restaurado.
        val lectura = assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-ficticio.json")))
        ok(e.restaurar(lectura))
        igual(e.padron.value.afiliados.size, 40)
        igual(e.rendiciones.value.size, 2)
        igual(e.calendario.value.fechaLimiteBase, "2026-09-15")
        advanceTimeBy(500); runCurrent()
        igual(estado(c).padron.value.afiliados.size, 40, "lo pendiente de antes no se escribió encima")

        // Una copia que solo trae el calendario deja el padrón como está.
        val soloCalendario = assertIs<LecturaRespaldo.Lista>(leerRespaldo("""{ "calendario": { "periodicidad": "semanal", "fechaLimiteBase": "2026-12-01" } }"""))
        ok(e.restaurar(soloCalendario))
        igual(e.padron.value.afiliados.size, 40)
        igual(e.calendario.value.periodicidad, "semanal")
    }

    @Test
    fun `Lo guardado en el teléfono tiene el mismo formato que la copia exportada`() = runTest {
        val c = carpeta()
        val e = estado(c)
        val lectura = assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-ficticio.json")))
        e.restaurar(lectura)
        val copia = Json.parseToJsonElement(escribirRespaldo(e.armarCopia()))
        val enDisco = Json.parseToJsonElement(File(c, "padron.json").readText())
        igual(enDisco, copia.let { (it as kotlinx.serialization.json.JsonObject)["padron"] })
        igual(Json.parseToJsonElement(File(c, "rendiciones.json").readText()), (copia as kotlinx.serialization.json.JsonObject)["rendiciones"])
    }

    @Test
    fun `El recordatorio de copia, con datos, sin copia o a los 14 días`() = runTest {
        val e = estado()
        ok(!e.tocaRecordarCopia(), "sin datos no hay nada que perder")
        e.cambiarPadron { it.copy(afiliados = listOf(nuevoAfiliado(hoy))) }
        ok(e.tocaRecordarCopia(), "con datos y sin copia")
        igual(e.diasDesdeUltimaCopia(), null)
        ok(e.registrarCopia())
        igual(e.diasDesdeUltimaCopia(), 0)
        ok(!e.tocaRecordarCopia())
        hoy = "2026-11-02"
        igual(e.diasDesdeUltimaCopia(), 13)
        ok(!e.tocaRecordarCopia())
        hoy = "2026-11-03"
        ok(e.tocaRecordarCopia())
    }

    @Test
    fun `Un archivo dañado no impide cargar lo demás, y borrar todo deja vacío`() = runTest {
        val c = carpeta()
        File(c, "padron.json").writeText("{ roto")
        File(c, "config.json").writeText("""{ "precioMenuNormalCent": 300 }""")
        val e = estado(c)
        igual(e.padron.value.afiliados.size, 0)
        igual(e.config.value.precioMenuNormalCent, 300L)
        ok(e.avisosAlmacenamiento().single().contains("padron.json.respaldo"))
        ok(File(c, "padron.json.respaldo").exists())
        ok(e.borrarTodo())
        igual(c.listFiles()!!.size, 0)
        igual(e.config.value.precioMenuNormalCent, null)
    }

    @Test
    fun `Un fallo al escribir se ve como FALLO, nunca como guardado`() = runTest {
        val c = carpeta()
        val e = estado(c)
        e.cambiarPadron { it.copy(atenciones = listOf(nuevaAtencion(null, hoy).copy(asistencias = listOf(Asistencia("a", "normal"))))) }
        c.deleteRecursively()
        File(c.path).writeText("ocupa el lugar de la carpeta")   // no se puede crear la carpeta
        advanceTimeBy(500); runCurrent()
        igual(e.guardado.value, Guardado.FALLO)
        ok(e.avisosAlmacenamiento().any { "No se pudo guardar" in it })
        File(c.path).delete()
    }

    @Test
    fun `El periodo por defecto es el mes en curso`() = runTest {
        val e = estado()
        igual(e.periodoPorDefecto().inicio, "2026-10-01")
        igual(e.periodoPorDefecto().fin, "2026-10-20")
        igual(Clave.values().size, 5)
    }
}
