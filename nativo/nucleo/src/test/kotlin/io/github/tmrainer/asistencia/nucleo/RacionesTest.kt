// Grupos «Raciones» y «Asistencia» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.Origen
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosSinAsistencia
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciaPorAfiliado
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasConTodos
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.menuPorDefecto
import io.github.tmrainer.asistencia.nucleo.calculos.racionesDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.racionesPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEconomicoRaciones
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.PreciosMenu
import io.github.tmrainer.asistencia.nucleo.modelo.SinInscribir
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.modelo.padronVacio
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAtencion
import kotlin.test.Test

private fun marcas(cuantos: Int, prefijo: String, menu: String = "normal") =
    List(cuantos) { Asistencia("$prefijo$it", menu) }

private val PRECIOS = PreciosMenu(300, 100)

class RacionesTest {

    @Test
    fun `Las raciones se cuentan de la asistencia, no se escriben`() {
        val at = nuevaAtencion(null).copy(asistencias = listOf(
            Asistencia("a", "normal"), Asistencia("b", "normal"), Asistencia("c", "ayuda_social")
        ))
        val d = desgloseDelDia(at)
        igual(d.normal, 2)
        igual(d.ayudaSocial, 1)
        igual(d.total, 3)
        igual(d.origen, Origen.ASISTENCIA)
        igual(racionesDelDia(at), 3)
    }

    @Test
    fun `Quienes comen sin estar inscritos suman raciones y recaudación`() {
        val at = nuevaAtencion(PRECIOS).copy(
            asistencias = listOf(Asistencia("a", "normal")),
            sinInscribir = SinInscribir(normal = 2, ayudaSocial = 1)
        )
        val d = desgloseDelDia(at)
        igual(d.normal, 3)
        igual(d.ayudaSocial, 1)
        igual(d.total, 4)
        igual(d.sinInscribir, 3)
        igual(recaudacionDelDiaCent(at), 1000L, "3 x 3.00 + 1 x 1.00")
        // Valores raros no suman: el contador nunca baja de cero.
        // (En la web también se prueba 1.5; aquí el campo es entero por tipo.)
        igual(desgloseDelDia(at.copy(sinInscribir = SinInscribir(normal = -2, ayudaSocial = 0))).total, 1)
    }

    @Test
    fun `Un día sin nadie marcado cuenta cero raciones`() {
        val at = nuevaAtencion(null)
        igual(racionesDelDia(at), 0)
        igual(desgloseDelDia(at).origen, Origen.ASISTENCIA)
    }

    @Test
    fun `El precio se copia de la configuración al crear el día`() {
        val at = nuevaAtencion(PRECIOS)
        igual(at.precioMenuNormalCent, 300L)
        igual(at.precioMenuAyudaSocialCent, 100L)
        igual(at.precioTomadoDeConfig, true, "queda marcado de dónde vino")
        val sin = nuevaAtencion(null)
        igual(sin.precioMenuNormalCent, null)
        igual(sin.precioTomadoDeConfig, false)
    }

    @Test
    fun `La recaudación del día usa el precio de cada menú`() {
        val at = nuevaAtencion(PRECIOS).copy(asistencias = marcas(40, "n") + marcas(10, "s", "ayuda_social"))
        igual(recaudacionDelDiaCent(at), 40L * 300 + 10L * 100)
        igual(formatearSoles(recaudacionDelDiaCent(at)), "130.00")
    }

    @Test
    fun `Falta un precio con raciones de ese tipo, no se estima, devuelve null`() {
        fun base(precioNormal: Long?, precioAyuda: Long?, menus: List<String>) =
            nuevaAtencion(PreciosMenu(precioNormal, precioAyuda))
                .copy(asistencias = menus.mapIndexed { i, m -> Asistencia("x$i", m) })

        igual(recaudacionDelDiaCent(base(null, 100, listOf("normal"))), null)
        igual(recaudacionDelDiaCent(base(300, null, listOf("normal", "ayuda_social"))), null, "basta que falte uno")
        igual(recaudacionDelDiaCent(base(300, null, listOf("normal"))), 300L, "sin raciones de ayuda social, su precio no hace falta")
    }

    @Test
    fun `El precio promedio es ponderado por ración, no promedio de precios`() {
        val p = padronVacio().copy(atenciones = listOf(
            nuevaAtencion(PRECIOS).copy(fecha = "2026-09-10", asistencias = marcas(90, "n") + marcas(10, "s", "ayuda_social"))
        ))
        val r = resumenEconomicoRaciones(p, "2026-09-01", "2026-09-30")
        igual(r.racionesTotales, 100)
        igual(r.recaudacionCent, 90L * 300 + 10L * 100)        // 28 000
        igual(r.precioPromedioRacionCent, 280L, "no 200, que sería el promedio simple")
        igual(r.diasSinPrecio, 0)
    }

    @Test
    fun `Los días sin precio quedan fuera del promedio y se informan`() {
        val conPrecio = nuevaAtencion(PreciosMenu(300, 0)).copy(fecha = "2026-09-10", asistencias = marcas(100, "n"))
        val sinPrecio = nuevaAtencion(null).copy(fecha = "2026-09-11", asistencias = marcas(50, "m"))
        val r = resumenEconomicoRaciones(padronVacio().copy(atenciones = listOf(conPrecio, sinPrecio)), "2026-09-01", "2026-09-30")
        igual(r.racionesTotales, 150, "las raciones se cuentan todas")
        igual(r.racionesConPrecio, 100, "el promedio solo usa las que tienen precio")
        igual(r.precioPromedioRacionCent, 300L)
        igual(r.diasSinPrecio, 1)
    }

    @Test
    fun `Sin ningún precio anotado, no hay promedio, null, no cero`() {
        val p = padronVacio().copy(atenciones = listOf(
            nuevaAtencion(null).copy(fecha = "2026-09-10", asistencias = listOf(Asistencia("a", "normal")))
        ))
        val r = resumenEconomicoRaciones(p, "2026-09-01", "2026-09-30")
        igual(r.precioPromedioRacionCent, null)
        igual(r.recaudacionCent, null)
    }

    @Test
    fun `racionesPeriodo suma solo lo que cae dentro del periodo`() {
        fun dia(fecha: String, cuantos: Int) = nuevaAtencion(null).copy(fecha = fecha, asistencias = marcas(cuantos, fecha))
        val p = padronVacio().copy(atenciones = listOf(dia("2026-09-10", 50), dia("2026-09-20", 60), dia("2026-10-01", 70)))
        igual(racionesPeriodo(p, "2026-09-01", "2026-09-30"), 110)
    }

    @Test
    fun `Falta el precio de un menú con asistencia, es error`() {
        val at = nuevaAtencion(null).copy(id = "x", fecha = "2026-09-10", asistencias = listOf(Asistencia("a", "normal")))
        val p = padronVacio().copy(
            afiliados = listOf(nuevoAfiliado().copy(id = "a", altaEn = "2026-09-01")),
            atenciones = listOf(at)
        )
        ok(validarAtencion(at, p).any { it.severidad == Severidad.ERROR && "precio del menú normal" in it.mensaje })
    }
}

class AsistenciaTest {

    @Test
    fun `El menú por defecto sale de cómo se inscribió la persona`() {
        igual(menuPorDefecto(Afiliado(tipoAfiliado = "caso_social")), "ayuda_social")
        igual(menuPorDefecto(Afiliado(tipoAfiliado = "habitual")), "normal")
        igual(menuPorDefecto(Afiliado(tipoAfiliado = "")), "normal", "sin tipo, no adivina otra cosa")
    }

    @Test
    fun `Cuenta días por persona y separa por tipo de menú`() {
        val p = padronVacio().copy(
            afiliados = listOf(nuevoAfiliado().copy(id = "ana", altaEn = "2026-09-01"), nuevoAfiliado().copy(id = "rosa", altaEn = "2026-09-01")),
            atenciones = listOf(
                nuevaAtencion(null).copy(fecha = "2026-09-10", asistencias = listOf(Asistencia("ana", "normal"), Asistencia("rosa", "ayuda_social"))),
                nuevaAtencion(null).copy(fecha = "2026-09-11", asistencias = listOf(Asistencia("ana", "ayuda_social")))
            )
        )
        val filas = asistenciaPorAfiliado(p, "2026-09-01", "2026-09-30")
        igual(filas.size, 2)
        igual(filas[0].afiliadoId, "ana", "ordenado de más a menos días")
        igual(filas[0].dias, 2)
        igual(filas[0].normal, 1)
        igual(filas[0].ayudaSocial, 1)
    }

    @Test
    fun `Lista quién no asistió ningún día del periodo`() {
        val p = padronVacio().copy(
            afiliados = listOf(nuevoAfiliado().copy(id = "ana", altaEn = "2026-09-01"), nuevoAfiliado().copy(id = "rosa", altaEn = "2026-09-01")),
            atenciones = listOf(nuevaAtencion(null).copy(fecha = "2026-09-10", asistencias = listOf(Asistencia("ana", "normal"))))
        )
        val sin = afiliadosSinAsistencia(p, "2026-09-01", "2026-09-30")
        igual(sin.size, 1)
        igual(sin[0].id, "rosa")
    }

    @Test
    fun `Marcar a todos conserva el menú ya elegido y da a los demás el de su inscripción`() {
        val ana = nuevoAfiliado().copy(id = "ana", tipoAfiliado = "habitual")
        val rosa = nuevoAfiliado().copy(id = "rosa", tipoAfiliado = "caso_social")
        val luz = nuevoAfiliado().copy(id = "luz", tipoAfiliado = "habitual")
        // Ana ya estaba marcada y ese día comió el menú de ayuda social.
        val at = nuevaAtencion(null).copy(asistencias = listOf(Asistencia("ana", "ayuda_social")))
        val todas = asistenciasConTodos(at, listOf(ana, rosa, luz))
        igual(todas.size, 3)
        igual(todas[0].tipoMenu, "ayuda_social", "Ana conserva lo elegido ese día")
        igual(todas[1].tipoMenu, "ayuda_social", "Rosa, por su inscripción")
        igual(todas[2].tipoMenu, "normal", "Luz, por su inscripción")
        igual(at.asistencias.size, 1, "no modifica el día recibido")
    }

    @Test
    fun `Sin asistencia mira a las activas a la fecha pedida, como la tabla del reporte`() {
        val p = padronVacio().copy(afiliados = listOf(
            nuevoAfiliado().copy(id = "ana", altaEn = "2026-09-01"),
            nuevoAfiliado().copy(id = "rosa", altaEn = "2026-09-20")
        ))
        // Rosa entra el 20: al 15 todavía no está en el padrón ni en la tabla.
        igual(afiliadosSinAsistencia(p, "2026-09-01", "2026-09-30").size, 2, "por defecto, activas al cierre del periodo")
        val alQuince = afiliadosSinAsistencia(p, "2026-09-01", "2026-09-30", "2026-09-15")
        igual(alQuince.size, 1)
        igual(alQuince[0].id, "ana")
    }

    @Test
    fun `Persona marcada dos veces el mismo día es error`() {
        val at = nuevaAtencion(PreciosMenu(300, null)).copy(
            id = "x", fecha = "2026-09-10",
            asistencias = listOf(Asistencia("ana", "normal"), Asistencia("ana", "normal"))
        )
        val p = padronVacio().copy(afiliados = listOf(nuevoAfiliado().copy(id = "ana", altaEn = "2026-09-01")), atenciones = listOf(at))
        ok(validarAtencion(at, p).any { it.severidad == Severidad.ERROR && "dos veces" in it.mensaje })
    }
}
