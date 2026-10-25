// El código de cada B-1 (CodigoPeriodo.kt): Q1, Q2, M y S1–S5, y qué periodo rinde cada entrega.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.codigoPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.proponerPeriodo
import io.github.tmrainer.asistencia.nucleo.modelo.Periodo
import kotlin.test.Test

private fun p(inicio: String, fin: String) = Periodo(inicio = inicio, fin = fin)

class CodigoPeriodoTest {

    @Test
    fun `Quincenas y mes completo`() {
        igual(codigoPeriodo(p("2026-10-01", "2026-10-15")), "Octubre-2026-Q1")
        igual(codigoPeriodo(p("2026-10-16", "2026-10-31")), "Octubre-2026-Q2")
        igual(codigoPeriodo(p("2026-10-01", "2026-10-31")), "Octubre-2026-M")
        igual(codigoPeriodo(p("2027-02-16", "2027-02-28")), "Febrero-2027-Q2", "febrero termina el 28")
        igual(codigoPeriodo(p("2026-09-01", "2026-09-30")), "Setiembre-2026-M", "Setiembre, como en el formato")
    }

    @Test
    fun `Semanas de lunes a domingo - del mes de su jueves`() {
        igual(codigoPeriodo(p("2026-10-05", "2026-10-11")), "Octubre-2026-S2", "jueves 8: 2.º jueves (el 1 de octubre es jueves)")
        igual(codigoPeriodo(p("2026-10-12", "2026-10-18")), "Octubre-2026-S3")
        igual(codigoPeriodo(p("2026-09-28", "2026-10-04")), "Octubre-2026-S1", "cruza el mes: el jueves 1 es de octubre")
        igual(codigoPeriodo(p("2026-10-26", "2026-11-01")), "Octubre-2026-S5", "jueves 29: 5.º jueves")
    }

    @Test
    fun `Fechas que no son un periodo completo - no hay código`() {
        igual(codigoPeriodo(p("2026-10-03", "2026-10-17")), null)
        igual(codigoPeriodo(p("2026-10-01", "2026-10-16")), null)
        igual(codigoPeriodo(p("2026-10-06", "2026-10-12")), null, "semana que no empieza en lunes")
        igual(codigoPeriodo(p("", "2026-10-15")), null)
        igual(codigoPeriodo(null), null)
    }

    @Test
    fun `Qué B-1 rinde cada entrega - el periodo que contiene la fecha`() {
        fun rinde(fecha: String, tipo: String) = codigoPeriodo(proponerPeriodo(tipo, fecha).aPeriodo())
        igual(rinde("2026-10-20", "quincenal"), "Octubre-2026-Q2")
        igual(rinde("2026-10-15", "quincenal"), "Octubre-2026-Q1", "entregar el mismo 15")
        igual(rinde("2026-09-14", "mensual"), "Setiembre-2026-M", "como el ejemplar llenado a mano")
        igual(rinde("2026-10-23", "semanal"), "Octubre-2026-S4", "viernes 23: la semana del 19 al 25")
        igual(rinde("2026-11-06", "semanal"), "Noviembre-2026-S1", "viernes 6: la semana del 2 al 8")
    }
}
