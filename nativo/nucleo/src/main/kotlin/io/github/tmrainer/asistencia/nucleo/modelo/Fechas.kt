// Las fechas se guardan en ISO 'AAAA-MM-DD' y se muestran en 'DD-MM-AA'.
// Una fecha que no existe ('2024-02-31') da null: no se corrige sola.
package io.github.tmrainer.asistencia.nucleo.modelo

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private val ISO = Regex("^\\d{4}-\\d{2}-\\d{2}$")

internal fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** Fecha de hoy en hora LOCAL, como 'AAAA-MM-DD'. */
fun hoyIso(): String = aIso(LocalDate.now())

private val MARCA_TIEMPO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** Instante actual como lo escribe `new Date().toISOString()`: '2026-09-14T15:04:05.123Z'. */
fun ahoraIso(): String = MARCA_TIEMPO.format(java.time.Instant.now())

/** ¿Tiene `valor` la FORMA 'AAAA-MM-DD'? Solo revisa el formato. */
fun esIso(valor: String?): Boolean = valor != null && ISO.matches(valor)

/** ISO 'AAAA-MM-DD' a fecha. null si no tiene la forma o la fecha no existe. */
fun aFecha(iso: String?): LocalDate? {
    if (!esIso(iso)) return null
    return try {
        LocalDate.parse(iso)
    } catch (e: DateTimeParseException) {
        null
    }
}

fun aIso(fecha: LocalDate?): String {
    if (fecha == null) return ""
    return "${fecha.year.toString().padStart(4, '0')}-${pad2(fecha.monthValue)}-${pad2(fecha.dayOfMonth)}"
}

private val MESES_LARGOS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
    "septiembre", "octubre", "noviembre", "diciembre"
)
private val DIAS_SEMANA = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

/** «Sábado 11 de octubre»: la fecha como se dice. Solo para mostrar. */
fun fechaLarga(iso: String?): String {
    val f = aFecha(iso) ?: return ""
    return "${DIAS_SEMANA[f.dayOfWeek.value % 7]} ${f.dayOfMonth} de ${MESES_LARGOS[f.monthValue - 1]}"
}

/** 'AAAA-MM-DD' a 'DD-MM-AA', como se escribe en el papel. */
fun aDdMmAa(iso: String?): String {
    if (!esIso(iso)) return ""
    val (a, m, d) = iso!!.split("-")
    return "$d-$m-${a.substring(2)}"
}

fun diasEntre(isoA: String?, isoB: String?): Int? {
    val a = aFecha(isoA) ?: return null
    val b = aFecha(isoB) ?: return null
    return ChronoUnit.DAYS.between(a, b).toInt()
}

val MESES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre"
)
