// El código de cada B-1 según el rango de días que rinde:
// «Octubre-2026-Q1» (del 1 al 15), «Octubre-2026-Q2» (del 16 a fin de mes),
// «Octubre-2026-M» (el mes completo), «Octubre-2026-S2» (la 2.ª semana, de
// lunes a domingo). Así se sabe cuál es cuál, y qué entrega corresponde a
// qué B-1. Sale de las fechas del periodo: no se guarda aparte.
package io.github.tmrainer.asistencia.nucleo.calculos

import io.github.tmrainer.asistencia.nucleo.modelo.MESES
import io.github.tmrainer.asistencia.nucleo.modelo.Periodo
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import java.time.DayOfWeek
import java.time.LocalDate

private fun prefijo(f: LocalDate) = "${MESES[f.monthValue - 1]}-${f.year}"

/**
 * El código del periodo, o null si sus fechas no son una quincena, un mes o
 * una semana (lunes a domingo) completos: entonces no se inventa uno.
 * La semana es del mes en que cae su jueves (como las semanas ISO), y se
 * cuenta qué jueves de ese mes es: S1 a S5.
 */
fun codigoPeriodo(periodo: Periodo?): String? {
    val a = aFecha(periodo?.inicio) ?: return null
    val b = aFecha(periodo?.fin) ?: return null
    val finDeMes = a.withDayOfMonth(a.lengthOfMonth())
    return when {
        a.dayOfMonth == 1 && b == finDeMes -> "${prefijo(a)}-M"
        a.dayOfMonth == 1 && b == a.withDayOfMonth(15) -> "${prefijo(a)}-Q1"
        a.dayOfMonth == 16 && b == finDeMes -> "${prefijo(a)}-Q2"
        a.dayOfWeek == DayOfWeek.MONDAY && b == a.plusDays(6) -> {
            val jueves = a.plusDays(3)
            "${prefijo(jueves)}-S${(jueves.dayOfMonth - 1) / 7 + 1}"
        }
        else -> null
    }
}
