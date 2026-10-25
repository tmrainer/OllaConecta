// Grupo «Entregas» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.alertas.Nivel
import io.github.tmrainer.asistencia.nucleo.alertas.agendaEntregas
import io.github.tmrainer.asistencia.nucleo.alertas.alertaProximaEntrega
import io.github.tmrainer.asistencia.nucleo.alertas.avanzarPeriodo
import io.github.tmrainer.asistencia.nucleo.alertas.fechasDeEntrega
import io.github.tmrainer.asistencia.nucleo.alertas.nivelPorDias
import io.github.tmrainer.asistencia.nucleo.alertas.programaDeAvisos
import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.Entrega
import io.github.tmrainer.asistencia.nucleo.modelo.calendarioVacio
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.contar
import io.github.tmrainer.asistencia.nucleo.validaciones.validarCalendario
import kotlin.test.Test

class EntregasTest {

    @Test
    fun `avanzarPeriodo respeta semana, quincena y mes`() {
        igual(avanzarPeriodo("2026-09-14", "semanal"), "2026-09-21")
        igual(avanzarPeriodo("2026-09-14", "quincenal"), "2026-09-29")
        igual(avanzarPeriodo("2026-09-14", "mensual"), "2026-10-14")
        igual(avanzarPeriodo("2026-01-31", "mensual"), "2026-02-28", "ajusta a fin de mes")
        igual(avanzarPeriodo("2026-09-14", "mensual", -1), "2026-08-14")
    }

    @Test
    fun `Quincenal - la entrega queda a la misma distancia del fin de cada quincena`() {
        // 20-10 es 5 días después del 15: las siguientes, 5 días después de cada fin de quincena.
        igual(avanzarPeriodo("2026-10-20", "quincenal"), "2026-11-05", "fin de octubre + 5 (cada 15 días daba 04-11)")
        igual(avanzarPeriodo("2026-10-20", "quincenal", 2), "2026-11-20")
        igual(avanzarPeriodo("2026-10-20", "quincenal", 6), "2027-01-20", "no se corre con los meses")
        igual(avanzarPeriodo("2026-10-20", "quincenal", -1), "2026-10-05", "hacia atrás: fin de setiembre + 5")
        igual(avanzarPeriodo("2026-10-20", "quincenal", -20), "2025-12-20", "hacia atrás, cruzando el año")
        // El mismo fin de quincena: el 15 y el último día del mes.
        igual(avanzarPeriodo("2026-10-31", "quincenal"), "2026-11-15")
        igual(avanzarPeriodo("2026-11-15", "quincenal"), "2026-11-30")
        igual(avanzarPeriodo("2027-02-15", "quincenal"), "2027-02-28", "febrero")
    }

    @Test
    fun `Las fechas se cuentan hacia adelante y hacia atrás desde la base`() {
        val cal = calendarioVacio().copy(periodicidad = "mensual", fechaLimiteBase = "2026-03-14")
        val f = fechasDeEntrega(cal, "2026-09-20", 3)
        igual(f[0], "2026-10-14", "la primera que no ha pasado")
        igual(f[1], "2026-11-14")
        igual(f[2], "2026-12-14")

        val futuro = calendarioVacio().copy(periodicidad = "semanal", fechaLimiteBase = "2027-01-01")
        igual(fechasDeEntrega(futuro, "2026-09-20", 1)[0], "2026-09-25", "retrocede si la base es futura")
    }

    @Test
    fun `La serie mensual de un día 31 vuelve al 31 después de un mes corto`() {
        val cal = calendarioVacio().copy(periodicidad = "mensual", fechaLimiteBase = "2026-01-31")
        val f = fechasDeEntrega(cal, "2026-03-15", 3)
        igual(f[0], "2026-03-31", "no se queda en el 28 de febrero")
        igual(f[1], "2026-04-30")
        igual(f[2], "2026-05-31")

        val futura = calendarioVacio().copy(periodicidad = "mensual", fechaLimiteBase = "2026-05-31")
        val g = fechasDeEntrega(futura, "2026-02-10", 2)
        igual(g[0], "2026-02-28", "hacia atrás también se cuenta desde la base")
        igual(g[1], "2026-03-31")
        val a = alertaProximaEntrega(futura, "2026-03-05")!!
        igual(a.fechaLimite, "2026-02-28", "la entrega vencida sale de la misma serie")
        igual(a.nivel, Nivel.VENCIDA)
    }

    @Test
    fun `Sin configurar no inventa una fecha`() {
        val a = alertaProximaEntrega(calendarioVacio(), "2026-09-20")!!
        igual(a.configurado, false)
        ok(a.fechaLimite == null, "no debe traer ninguna fecha")
        igual(fechasDeEntrega(calendarioVacio(), "2026-09-20", 3).size, 0)
    }

    @Test
    fun `Cuenta los días que faltan y marca el nivel`() {
        val cal = calendarioVacio().copy(
            periodicidad = "mensual", fechaLimiteBase = "2026-09-20",
            entregas = listOf(Entrega(id = "1", fechaLimite = "2026-08-20", estado = "entregada"))
        )
        val a = alertaProximaEntrega(cal, "2026-09-17")!!
        igual(a.configurado, true)
        igual(a.fechaLimite, "2026-09-20")
        igual(a.diasRestantes, 3)
        igual(a.nivel, Nivel.URGENTE)

        igual(nivelPorDias(0, listOf(7, 3, 1)), Nivel.HOY)
        igual(nivelPorDias(-2, listOf(7, 3, 1)), Nivel.VENCIDA)
        igual(nivelPorDias(5, listOf(7, 3, 1)), Nivel.PROXIMA)
        igual(nivelPorDias(40, listOf(7, 3, 1)), Nivel.LEJANA)
    }

    @Test
    fun `Una entrega pasada sin marcar pesa más que la siguiente`() {
        val cal = calendarioVacio().copy(periodicidad = "mensual", fechaLimiteBase = "2026-09-20")
        val a = alertaProximaEntrega(cal, "2026-09-25")!!
        igual(a.nivel, Nivel.VENCIDA)
        igual(a.fechaLimite, "2026-09-20")
        igual(a.diasRestantes, -5)

        val marcada = cal.copy(entregas = listOf(Entrega(id = "1", fechaLimite = "2026-09-20", estado = "entregada")))
        val b = alertaProximaEntrega(marcada, "2026-09-25")!!
        igual(b.nivel, Nivel.LEJANA)
        igual(b.fechaLimite, "2026-10-20")
    }

    @Test
    fun `La agenda incluye la vencida y las siguientes`() {
        val cal = calendarioVacio().copy(periodicidad = "semanal", fechaLimiteBase = "2026-09-18")
        val agenda = agendaEntregas(cal, "2026-09-20", 3)
        igual(agenda.size, 4, "la anterior más tres")
        igual(agenda[0].fechaLimite, "2026-09-18")
        igual(agenda[0].nivel, Nivel.VENCIDA)
        igual(agenda[1].fechaLimite, "2026-09-25")
        ok(agenda[1].diasRestantes == 5)
    }

    @Test
    fun `Sin fecha base avisa, pero no da error`() {
        val hs = validarCalendario(calendarioVacio())
        igual(contar(hs).errores, 0)
        ok(hs.any { it.severidad == Severidad.ADVERTENCIA })
    }

    @Test
    fun `Los avisos del teléfono, días de anticipación y el mismo día, sin las ya entregadas`() {
        val c = Calendario(
            periodicidad = "mensual", fechaLimiteBase = "2026-11-05", diasDeAviso = listOf(7, 3, 1),
            entregas = listOf(Entrega(fechaLimite = "2026-11-05", estado = "entregada"))
        )
        val avisos = programaDeAvisos(c, "2026-10-11", 2)
        igual(avisos.joinToString(",") { it.fecha }, "2026-11-28,2026-12-02,2026-12-04,2026-12-05",
            "la del 05-11 ya se entregó: solo las de la siguiente")
        igual(avisos[3].texto, "Hoy vence la entrega del 05-12-26.")
        igual(avisos[2].texto, "Falta 1 día para la entrega del 05-12-26.")
        igual(avisos.map { it.id }.toSet().size, 4, "ids distintos")
        ok(avisos.all { it.id < (1 shl 30) * 2L }, "ids que Android acepta")
        igual(programaDeAvisos(Calendario(periodicidad = "mensual", fechaLimiteBase = ""), "2026-10-11").size, 0,
            "sin fecha de entrega no hay avisos")
        // Lo de hoy o antes no se programa: lo dice la franja al abrir.
        ok(programaDeAvisos(c, "2026-12-03", 1).all { it.fecha > "2026-12-03" })
    }
}
