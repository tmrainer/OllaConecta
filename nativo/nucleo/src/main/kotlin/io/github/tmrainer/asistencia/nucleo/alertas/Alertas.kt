// Alertas.kt — porte de web/js/alertas.js: cuenta regresiva hasta la próxima
// entrega del formato. Funciones PURAS.
//
// La app NO conoce el calendario oficial de la Municipalidad. Todo sale de dos
// datos que escribe la usuaria: cada cuánto entrega, y UNA fecha de entrega.
package io.github.tmrainer.asistencia.nucleo.alertas

import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.aIso
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.diasEntre
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso

data class Periodicidad(val clave: String, val etiqueta: String, val detalle: String)

val PERIODICIDADES = listOf(
    Periodicidad("semanal", "Semanal", "cada 7 días"),
    Periodicidad("quincenal", "Quincenal", "cada 15 días"),
    Periodicidad("mensual", "Mensual", "el mismo día de cada mes")
)

/** Avanza una fecha un periodo. Mensual conserva el día, ajustando a fin de mes. */
fun avanzarPeriodo(fechaIso: String?, periodicidad: String?, pasos: Int = 1): String {
    val f = aFecha(fechaIso) ?: return ""
    return when (periodicidad) {
        "semanal" -> aIso(f.plusDays(7L * pasos))
        "quincenal" -> aIso(f.plusDays(15L * pasos))
        else -> aIso(f.plusMonths(pasos.toLong()))
    }
}

fun retrocederPeriodo(fechaIso: String?, periodicidad: String?, pasos: Int = 1): String =
    avanzarPeriodo(fechaIso, periodicidad, -pasos)

/**
 * Cuántos periodos hay que avanzar desde la base para llegar a la primera
 * fecha que no ha pasado (puede ser negativo si la base es futura).
 * Siempre se cuenta DESDE LA BASE, nunca de a un paso sobre la fecha anterior.
 */
private fun pasosHastaHoy(calendario: Calendario, hoy: String): Int {
    val per = calendario.periodicidad.ifEmpty { "mensual" }
    val base = calendario.fechaLimiteBase
    var n = 0
    while (n > -2000 && avanzarPeriodo(base, per, n) > hoy) n -= 1
    while (n < 2000 && avanzarPeriodo(base, per, n) < hoy) n += 1
    return n
}

private fun hoyO(hoyRef: String?) = if (esIso(hoyRef)) hoyRef!! else hoyIso()

/**
 * Serie de fechas de entrega alrededor de hoy, a partir de la fecha base.
 * La primera es la primera que no ha pasado todavía.
 */
fun fechasDeEntrega(calendario: Calendario?, hoyRef: String? = null, cuantas: Int = 6): List<String> {
    val hoy = hoyO(hoyRef)
    if (calendario == null || !esIso(calendario.fechaLimiteBase)) return emptyList()
    val per = calendario.periodicidad.ifEmpty { "mensual" }
    val n = pasosHastaHoy(calendario, hoy)
    return List(cuantas) { i -> avanzarPeriodo(calendario.fechaLimiteBase, per, n + i) }
}

/** Fecha de entrega inmediatamente anterior a hoy, o "" si no hay base. */
fun entregaAnterior(calendario: Calendario?, hoyRef: String? = null): String {
    val hoy = hoyO(hoyRef)
    if (calendario == null || !esIso(calendario.fechaLimiteBase)) return ""
    val per = calendario.periodicidad.ifEmpty { "mensual" }
    return avanzarPeriodo(calendario.fechaLimiteBase, per, pasosHastaHoy(calendario, hoy) - 1)
}

enum class Nivel(val clave: String) {
    VENCIDA("vencida"), HOY("hoy"), URGENTE("urgente"), PROXIMA("proxima"), LEJANA("lejana")
}

val NIVELES = Nivel.values().toList()

/**
 * Con avisos [7, 3, 1]: a 7 días «se acerca», a 3 o menos «falta muy poco».
 * El corte de «urgente» es el segundo aviso más bajo; con un solo aviso, ese mismo.
 */
fun nivelPorDias(dias: Int, diasDeAviso: List<Int>?): Nivel {
    if (dias < 0) return Nivel.VENCIDA
    if (dias == 0) return Nivel.HOY
    val avisos = if (!diasDeAviso.isNullOrEmpty()) diasDeAviso else listOf(7, 3, 1)
    val orden = avisos.sorted()
    val corteUrgente = if (orden.size > 1) orden[1] else orden[0]
    val corteProxima = orden.last()
    if (dias <= corteUrgente) return Nivel.URGENTE
    if (dias <= corteProxima) return Nivel.PROXIMA
    return Nivel.LEJANA
}

private fun estadoRegistrado(calendario: Calendario?, fechaLimite: String): String =
    calendario?.entregas?.firstOrNull { it.fechaLimite == fechaLimite }?.estado ?: "pendiente"

data class AlertaEntrega(
    val configurado: Boolean,
    val fechaLimite: String?,
    val diasRestantes: Int?,
    val nivel: Nivel,
    val estado: String?,
    val mensaje: String
)

/**
 * La alerta que se muestra arriba de la pantalla. Si falta configurar el
 * calendario, lo dice: no se inventa una fecha.
 */
fun alertaProximaEntrega(calendario: Calendario?, hoyRef: String? = null): AlertaEntrega? {
    val hoy = hoyO(hoyRef)
    if (calendario == null || !esIso(calendario.fechaLimiteBase)) {
        return AlertaEntrega(
            configurado = false, fechaLimite = null, diasRestantes = null, nivel = Nivel.LEJANA, estado = null,
            mensaje = "Todavía no has indicado cuándo entregas el formato."
        )
    }

    // Una entrega ya vencida y no marcada como entregada pesa más que la siguiente.
    val anterior = entregaAnterior(calendario, hoy)
    if (anterior.isNotEmpty() && estadoRegistrado(calendario, anterior) == "pendiente") {
        val atraso = diasEntre(anterior, hoy) ?: 0
        return AlertaEntrega(
            configurado = true, fechaLimite = anterior, diasRestantes = -atraso, nivel = Nivel.VENCIDA,
            estado = "pendiente",
            mensaje = "La entrega del ${aDdMmAa(anterior)} venció hace ${cuantos(atraso, "día")} y sigue sin marcarse como entregada."
        )
    }

    val proxima = fechasDeEntrega(calendario, hoy, 1).firstOrNull() ?: return null
    val dias = diasEntre(hoy, proxima) ?: 0
    val nivel = nivelPorDias(dias, calendario.diasDeAviso)
    val cuando = when (dias) {
        0 -> "hoy"
        1 -> "mañana"
        else -> "en $dias días"
    }
    return AlertaEntrega(
        configurado = true, fechaLimite = proxima, diasRestantes = dias, nivel = nivel,
        estado = estadoRegistrado(calendario, proxima),
        mensaje = "Próxima entrega $cuando: ${aDdMmAa(proxima)}."
    )
}

data class EntregaAgenda(val fechaLimite: String, val diasRestantes: Int, val nivel: Nivel, val estado: String)

/** Fechas próximas con su estado y días restantes, para la página del calendario. */
fun agendaEntregas(calendario: Calendario?, hoyRef: String? = null, cuantas: Int = 6): List<EntregaAgenda> {
    val hoy = hoyO(hoyRef)
    val fechas = fechasDeEntrega(calendario, hoy, cuantas)
    val anterior = entregaAnterior(calendario, hoy)
    val lista = ArrayList<EntregaAgenda>()
    if (anterior.isNotEmpty()) {
        lista.add(EntregaAgenda(anterior, diasEntre(hoy, anterior) ?: 0, Nivel.VENCIDA, estadoRegistrado(calendario, anterior)))
    }
    for (f in fechas) {
        val dias = diasEntre(hoy, f) ?: 0
        lista.add(EntregaAgenda(f, dias, nivelPorDias(dias, calendario?.diasDeAviso), estadoRegistrado(calendario, f)))
    }
    return lista
}

data class Aviso(val id: Int, val fecha: String, val fechaEntrega: String, val dias: Int, val titulo: String, val texto: String)

/**
 * Los avisos que el teléfono muestra solo, con la app cerrada: uno por cada
 * día de aviso antes de cada una de las próximas entregas, y uno el mismo día.
 * Solo los que caen después de hoy y de entregas no marcadas como entregadas.
 *
 * `id` es un entero estable (fecha de entrega + días de anticipación): volver a
 * programar reemplaza el mismo aviso en vez de duplicarlo.
 */
fun programaDeAvisos(calendario: Calendario?, hoyRef: String? = null, cuantas: Int = 6): List<Aviso> {
    val hoy = hoyO(hoyRef)
    if (calendario == null || !esIso(calendario.fechaLimiteBase)) return emptyList()
    val validos = calendario.diasDeAviso.filter { it > 0 && it < 400 }
    val anticipacion = (validos.ifEmpty { listOf(7, 3, 1) } + 0).distinct()
    val avisos = ArrayList<Aviso>()
    for (entrega in fechasDeEntrega(calendario, hoy, cuantas)) {
        if (estadoRegistrado(calendario, entrega) == "entregada") continue
        val fEntrega = aFecha(entrega) ?: continue
        for (dias in anticipacion) {
            val fecha = aIso(fEntrega.minusDays(dias.toLong()))
            if (fecha <= hoy) continue      // lo de hoy ya lo dice la franja al abrir
            avisos.add(Aviso(
                id = entrega.replace("-", "").substring(2).toInt() * 1000 + dias,
                fecha = fecha,
                fechaEntrega = entrega,
                dias = dias,
                titulo = "Entrega del Formato B-1",
                texto = if (dias == 0) "Hoy vence la entrega del ${aDdMmAa(entrega)}."
                else "${if (dias == 1) "Falta 1 día" else "Faltan $dias días"} para la entrega del ${aDdMmAa(entrega)}."
            ))
        }
    }
    return avisos.sortedWith(compareBy({ it.fecha }, { it.id }))
}
