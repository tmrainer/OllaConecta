// Grupos «Totales» y «Periodo» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.aporteCentroAtencion
import io.github.tmrainer.asistencia.nucleo.calculos.descuadreFila
import io.github.tmrainer.asistencia.nucleo.calculos.duracionPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.gastoPorOrigen
import io.github.tmrainer.asistencia.nucleo.calculos.gastosConSubsidio
import io.github.tmrainer.asistencia.nucleo.calculos.proponerPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.totalRendicionGastos
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import kotlin.test.Test

class TotalesTest {

    @Test
    fun `El ejemplar llenado a mano da 200,00 - 9,85 - 209,85`() {
        val t = totales(rendicionDelEjemplar())
        igual(formatearSoles(t.conSubsidio), "200.00", "gastos con el subsidio")
        igual(formatearSoles(t.aporte), "9.85", "aporte del centro")
        igual(formatearSoles(t.total), "209.85", "total de rendición")
    }

    @Test
    fun `La fila con varios entra en la suma por su monto total`() {
        val r = rendicionDelEjemplar()
        igual(totalRendicionGastos(r), 20985L)
        igual(r.egresos[1].cantidad, null, "la fila varios no inventa una cantidad")
        igual(r.egresos[1].precioUnitarioCent, null, "ni un precio unitario")
    }

    @Test
    fun `Total MENOR que el subsidio (tramo sin verificar)`() {
        val r = rendicionDelEjemplar().copy(egresos = listOf(egresoCon(15000)))
        val t = totales(r)
        igual(t.total, 15000L)
        igual(t.conSubsidio, 15000L, "min(subsidio, total)")
        igual(t.aporte, 0L, "no hay aporte del centro")
        igual(t.conSubsidio!! + t.aporte!!, t.total, "el invariante se mantiene")
    }

    @Test
    fun `Total IGUAL al subsidio`() {
        val t = totales(rendicionDelEjemplar().copy(egresos = listOf(egresoCon(20000))))
        igual(t.conSubsidio, 20000L)
        igual(t.aporte, 0L)
        igual(t.total, 20000L)
    }

    @Test
    fun `Filas vacías no suman ni rompen el cálculo`() {
        val base = rendicionDelEjemplar()
        val r = base.copy(egresos = base.egresos + nuevoEgreso() + nuevoEgreso())
        igual(totalRendicionGastos(r), 20985L)
        igual(totales(r).aporte, 985L)
    }

    @Test
    fun `Sin monto de subsidio los tres totales quedan en null, no en cero`() {
        val r = rendicionDelEjemplar().copy(montoSubsidioCent = null)
        val t = totales(r)
        igual(t.total, null)
        igual(t.conSubsidio, null)
        igual(t.aporte, null)
        igual(gastosConSubsidio(r), null)
        igual(aporteCentroAtencion(r), null)
        igual(formatearSoles(t.total), "", "en pantalla se ve vacío, no 0.00")
    }

    @Test
    fun `Invariante gastos + aporte == total en muchos casos`() {
        for (subsidio in listOf(0L, 1L, 999L, 20000L, 100000L)) {
            for (montos in listOf(listOf(), listOf(1L), listOf(6800L, 14185L), listOf(50000L, 50000L, 1L))) {
                val r = rendicionDelEjemplar().copy(montoSubsidioCent = subsidio, egresos = montos.map { egresoCon(it) })
                val t = totales(r)
                igual(t.conSubsidio!! + t.aporte!!, t.total, "subsidio $subsidio, montos $montos")
            }
        }
    }

    @Test
    fun `La suma por origen de fondo iguala el total`() {
        val base = rendicionDelEjemplar()
        val r = base.copy(egresos = listOf(base.egresos[0], base.egresos[1].copy(origenFondo = "ayuda_social")))
        val po = gastoPorOrigen(r)
        igual(po.subsidio, 6800L)
        igual(po.ayudaSocial, 14185L)
        igual(po.aportePropio, 0L)
        igual(po.suma, totalRendicionGastos(r))
    }

    @Test
    fun `descuadreFila detecta el descuadre y calla ante varios`() {
        igual(descuadreFila(Egreso(cantidad = 2.0, precioUnitarioCent = 500, montoTotalCent = 1000)), null)
        val d = descuadreFila(Egreso(cantidad = 2.0, precioUnitarioCent = 500, montoTotalCent = 1200))
        ok(d != null && d.esperado == 1000L && d.declarado == 1200L, "debe reportar ambos números")
        igual(descuadreFila(Egreso(cantidadVarios = true, cantidad = 2.0, precioUnitarioCent = 500, montoTotalCent = 9999)), null)
        igual(descuadreFila(Egreso(cantidad = 2.0, precioUnitarioCent = 500, montoTotalCent = 1001)), null, "tolerancia de 1 céntimo")
    }
}

class PeriodoTest {

    @Test
    fun `Propone mes completo y ofrece las dos etiquetas`() {
        val p = proponerPeriodo("mensual", "2026-09-14")
        igual(p.inicio, "2026-09-01")
        igual(p.fin, "2026-09-30")
        igual(p.etiqueta, "Setiembre", "como en el ejemplar llenado a mano")
        ok("Mensual" in p.alternativas, "también ofrece «Mensual»")
        igual(duracionPeriodo(p.aPeriodo()), 30)
    }

    @Test
    fun `Propone quincena y semana correctas`() {
        val q1 = proponerPeriodo("quincenal", "2026-09-07")
        igual(q1.inicio, "2026-09-01")
        igual(q1.fin, "2026-09-15")
        igual(q1.etiqueta, "Quincenal")

        val q2 = proponerPeriodo("quincenal", "2026-09-20")
        igual(q2.inicio, "2026-09-16")
        igual(q2.fin, "2026-09-30")

        val s = proponerPeriodo("semanal", "2026-09-10") // jueves
        igual(s.inicio, "2026-09-07", "lunes de esa semana")
        igual(s.fin, "2026-09-13", "domingo de esa semana")
        igual(s.etiqueta, "Entrega semanal")
        igual(duracionPeriodo(s.aPeriodo()), 7)
    }
}
