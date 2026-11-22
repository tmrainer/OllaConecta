// El código de cada B-1 según el rango de días que rinde:
// «Octubre-2026-Q1» (del 1 al 15), «Octubre-2026-Q2» (del 16 a fin de mes),
// «Octubre-2026-M» (el mes completo), «Octubre-2026-S2» (la 2.ª semana, de
// lunes a domingo). Así se sabe cuál es cuál, y qué entrega corresponde a
// qué B-1. Sale de las fechas del periodo: no se guarda aparte.
package io.github.tmrainer.asistencia.nucleo.calculos

import io.github.tmrainer.asistencia.nucleo.modelo.MESES
import io.github.tmrainer.asistencia.nucleo.modelo.Periodo
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.aIso
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

/**
 * El B-1 de un mes entero: del 1 al último día, «Noviembre 2026» en «Mes de rendición».
 * @param mes de 1 a 12
 */
fun periodoDelMes(anio: Int, mes: Int): Periodo {
    val a = LocalDate.of(anio, mes, 1)
    return Periodo(tipo = "mensual", inicio = aIso(a), fin = aIso(a.withDayOfMonth(a.lengthOfMonth())), etiqueta = "${MESES[mes - 1]} $anio")
}

/**
 * El código con que sale el B-1 de un mes (decisión del 22-11-2026). Al cerrar el mes, o
 * ya pasado: «Noviembre-2026». Mandado antes, según el día [hoy] en que se arma: del 15
 * al 21, «Noviembre-2026-Q1» (ya cerró la primera quincena); los demás días, la semana
 * del mes en que va: S1 (días 1 a 7), S2 (8 a 14), S4 (22 a 28), S5 (29 a 31).
 * Un periodo que no es un mes entero (B-1 antiguos) conserva su [codigoPeriodo].
 */
fun codigoB1(periodo: Periodo?, hoy: LocalDate, cerrado: Boolean): String? {
    val a = aFecha(periodo?.inicio) ?: return null
    val b = aFecha(periodo?.fin) ?: return null
    if (a.dayOfMonth != 1 || b != a.withDayOfMonth(a.lengthOfMonth())) return codigoPeriodo(periodo)
    if (cerrado || hoy.isAfter(b) || hoy.isBefore(a)) return prefijo(a)
    val d = hoy.dayOfMonth
    return if (d in 15..21) "${prefijo(a)}-Q1" else "${prefijo(a)}-S${(d - 1) / 7 + 1}"
}
