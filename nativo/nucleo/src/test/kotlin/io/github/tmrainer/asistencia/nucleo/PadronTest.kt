// Grupos «Padrón», «Grupos de edad» y «Reporte» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.TRAMOS_ETARIOS
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.edadEnFecha
import io.github.tmrainer.asistencia.nucleo.calculos.filasPadron
import io.github.tmrainer.asistencia.nucleo.calculos.grupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEtario
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.modelo.padronVacio
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.contar
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAfiliado
import kotlin.test.Test

class PadronTest {

    @Test
    fun `La edad se calcula respecto a una fecha de referencia`() {
        igual(edadEnFecha("2000-09-14", "2026-09-13"), 25, "un día antes del cumpleaños")
        igual(edadEnFecha("2000-09-14", "2026-09-14"), 26, "el día del cumpleaños")
        igual(edadEnFecha("2000-09-14", "2026-09-15"), 26)
        igual(edadEnFecha("", "2026-09-14"), null)
    }

    @Test
    fun `El grupo etario cambia solo al cruzar el cumpleaños`() {
        val a = nuevoAfiliado().copy(fechaNacimiento = "1966-09-14")
        igual(grupoEtario(a, "2026-09-13"), "adulto", "59 años")
        igual(grupoEtario(a, "2026-09-14"), "adulto_mayor", "60 años")
    }

    @Test
    fun `Sin fecha ni grupo manual devuelve null, nunca adulto por defecto`() {
        val a = nuevoAfiliado()
        igual(grupoEtario(a, "2026-09-14"), null)
        igual(grupoEtario(a.copy(grupoEtarioManual = "nino"), "2026-09-14"), "nino", "usa el manual si no hay fecha")
        igual(grupoEtario(a.copy(grupoEtarioManual = "nino", fechaNacimiento = "2000-01-01"), "2026-09-14"), "adulto",
            "la fecha manda sobre el manual")
    }

    @Test
    fun `Afiliados activos respeta altas y bajas`() {
        val p = padronVacio().copy(afiliados = listOf(
            nuevoAfiliado().copy(id = "a", altaEn = "2026-09-01", activo = true),
            nuevoAfiliado().copy(id = "b", altaEn = "2026-09-20", activo = true),
            nuevoAfiliado().copy(id = "c", altaEn = "2026-09-01", activo = false, bajaEn = "2026-09-05")
        ))
        igual(afiliadosActivos(p, "2026-09-10").size, 1)
        igual(afiliadosActivos(p, "2026-09-25").size, 2)
        igual(afiliadosActivos(p, "2026-08-01").size, 0)
        igual(afiliadosActivos(p, "2026-09-03").size, 2, "la baja no borra el historial: antes de bajaEn seguía activa")
        val conInactiva = p.copy(afiliados = p.afiliados + nuevoAfiliado().copy(id = "d", altaEn = "2026-09-01", activo = false, bajaEn = ""))
        igual(afiliadosActivos(conInactiva, "2026-09-03").size, 2, "inactiva sin fecha de baja: fuera en toda fecha")
    }

    @Test
    fun `El resumen etario cuenta sin dato aparte`() {
        val p = padronVacio().copy(afiliados = listOf(
            nuevoAfiliado().copy(altaEn = "2026-01-01", fechaNacimiento = "2015-01-01"),
            nuevoAfiliado().copy(altaEn = "2026-01-01")
        ))
        val r = resumenEtario(p, "2026-09-14")
        igual(r.nino, 1)
        igual(r.sinDato, 1)
        igual(r.adulto, 0)
    }

    @Test
    fun `El número de documento es obligatorio`() {
        val a = nuevoAfiliado().copy(apellidoPaterno = "Pérez", nombres = "Ana", fechaNacimiento = "1990-05-05")
        val p = padronVacio().copy(afiliados = listOf(a))
        val hs = validarAfiliado(a, p)
        ok(hs.any { it.severidad == Severidad.ERROR && "número de documento" in it.mensaje }, "sin documento debe dar error")
        val conDoc = a.copy(numeroDocumento = "00000001", tipoAfiliado = "habitual")
        igual(contar(validarAfiliado(conDoc, p.copy(afiliados = listOf(conDoc)))).errores, 0, "con documento de 8 dígitos, sin errores")
        val corto = conDoc.copy(numeroDocumento = "123")
        ok(validarAfiliado(corto, p.copy(afiliados = listOf(corto))).any { it.severidad == Severidad.ERROR && "8 dígitos" in it.mensaje })
    }

    @Test
    fun `Documento repetido entre activos da error`() {
        val a = nuevoAfiliado().copy(id = "a", apellidoPaterno = "Pérez", nombres = "Ana", numeroDocumento = "00000001")
        val b = nuevoAfiliado().copy(id = "b", apellidoPaterno = "Quispe", nombres = "Rosa", numeroDocumento = "00000001")
        val p = padronVacio().copy(afiliados = listOf(a, b))
        ok(validarAfiliado(b, p).any { it.severidad == Severidad.ERROR && "ya está registrado" in it.mensaje })
    }
}

class GruposDeEdadTest {

    @Test
    fun `Los cuatro tramos siguen las etapas de vida del MINSA`() {
        fun g(edadAnios: Int) = grupoEtario(Afiliado(fechaNacimiento = "${2026 - edadAnios}-01-01"), "2026-06-01")
        igual(g(0), "nino")
        igual(g(11), "nino")
        igual(g(12), "adolescente")
        igual(g(17), "adolescente")
        igual(g(18), "adulto")
        igual(g(59), "adulto")
        igual(g(60), "adulto_mayor")
        igual(g(90), "adulto_mayor")
        igual(TRAMOS_ETARIOS.size, 4)
    }

    @Test
    fun `El grupo de edad y el tipo son obligatorios al inscribir`() {
        val a = nuevoAfiliado().copy(apellidoPaterno = "Pérez", nombres = "Ana", numeroDocumento = "00000001")
        val p = padronVacio().copy(afiliados = listOf(a))
        val hs = validarAfiliado(a, p)
        ok(hs.any { it.severidad == Severidad.ERROR && "grupo de edad" in it.mensaje }, "grupo")
        ok(hs.any { it.severidad == Severidad.ERROR && "habitual o de ayuda social" in it.mensaje }, "tipo")

        val completa = a.copy(grupoEtarioManual = "adulto", tipoAfiliado = "caso_social")
        igual(contar(validarAfiliado(completa, p.copy(afiliados = listOf(completa)))).errores, 0)

        val raro = completa.copy(grupoEtarioManual = "anciano")
        ok(validarAfiliado(raro, p.copy(afiliados = listOf(raro))).any { it.severidad == Severidad.ERROR && "no es uno de los cuatro" in it.mensaje })
    }
}

class ReporteTest {

    @Test
    fun `Las filas del padrón van numeradas y ordenadas por apellido`() {
        val p = padronVacio().copy(afiliados = listOf(
            nuevoAfiliado().copy(id = "1", apellidoPaterno = "Zapata", nombres = "Ana", numeroDocumento = "00000003", grupoEtarioManual = "adulto", tipoAfiliado = "habitual", altaEn = "2026-09-01"),
            nuevoAfiliado().copy(id = "2", apellidoPaterno = "Álvarez", nombres = "Rosa", numeroDocumento = "00000001", grupoEtarioManual = "nino", tipoAfiliado = "caso_social", altaEn = "2026-09-01"),
            nuevoAfiliado().copy(id = "3", apellidoPaterno = "Baja", nombres = "Luz", numeroDocumento = "00000002", grupoEtarioManual = "adulto", tipoAfiliado = "habitual", altaEn = "2026-09-01", activo = false, bajaEn = "2026-09-05")
        ))
        val filas = filasPadron(p, "2026-09-20")
        igual(filas.size, 2, "solo las activas")
        igual(filas[0].apellidoPaterno, "Álvarez", "ordenado con tildes")
        igual(filas[0].n, 1)
        igual(filas[1].n, 2)
        igual(filas[0].tipoAfiliadoEtiqueta, "Ayuda social")
        igual(filas[0].grupoEtarioEtiqueta, "Niño/a")
        igual(filas[0].edad, null, "sin fecha de nacimiento no se inventa una edad")
    }
}
