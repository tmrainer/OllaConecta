// Calculos.kt — porte de web/js/calculos.js. Funciones puras: sin Android,
// sin estado, sin red. Todo monto en céntimos enteros.
package io.github.tmrainer.asistencia.nucleo.calculos

import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.Atencion
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.MESES
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.Periodo
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.aIso
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// ------------------------------------------------- Totales del Formato B-1

fun totalRendicionGastos(rendicion: Rendicion): Long =
    rendicion.egresos.sumOf { it.montoTotalCent ?: 0L }

/**
 * DECISIÓN ABIERTA: sin verificar.
 * min(subsidio, total) se dedujo de UN solo ejemplar llenado a mano, en el que
 * el total (S/ 209.85) superaba al subsidio (S/ 200.00). El tramo en que el
 * total es MENOR que el subsidio no tiene ninguna evidencia detrás.
 * No apoyarse en esta fórmula como si estuviera confirmada.
 */
fun gastosConSubsidio(rendicion: Rendicion): Long? {
    val subsidio = rendicion.montoSubsidioCent ?: return null
    return min(subsidio, totalRendicionGastos(rendicion))
}

fun aporteCentroAtencion(rendicion: Rendicion): Long? {
    val subsidio = rendicion.montoSubsidioCent ?: return null
    return max(0L, totalRendicionGastos(rendicion) - subsidio)
}

data class Totales(val total: Long?, val conSubsidio: Long?, val aporte: Long?)

/**
 * Los tres renglones del pie. Si falta el subsidio, los tres quedan en null:
 * un cero donde falta un dato es un dato inventado.
 */
fun totales(rendicion: Rendicion): Totales {
    val subsidio = rendicion.montoSubsidioCent ?: return Totales(null, null, null)
    val total = totalRendicionGastos(rendicion)
    return Totales(total = total, conSubsidio = min(subsidio, total), aporte = max(0L, total - subsidio))
}

val ORIGENES_FONDO = listOf("subsidio", "ayuda_social", "aporte_propio")

data class GastoPorOrigen(val subsidio: Long, val ayudaSocial: Long, val aportePropio: Long) {
    val suma: Long get() = subsidio + ayudaSocial + aportePropio
}

fun gastoPorOrigen(rendicion: Rendicion): GastoPorOrigen {
    var subsidio = 0L
    var ayuda = 0L
    var propio = 0L
    for (fila in rendicion.egresos) {
        val monto = fila.montoTotalCent ?: continue
        when (if (fila.origenFondo in ORIGENES_FONDO) fila.origenFondo else "subsidio") {
            "ayuda_social" -> ayuda += monto
            "aporte_propio" -> propio += monto
            else -> subsidio += monto
        }
    }
    return GastoPorOrigen(subsidio, ayuda, propio)
}

data class Descuadre(val esperado: Long, val declarado: Long, val diferencia: Long)

/** Comprobación de fila: cantidad x precio unitario frente al monto total. */
fun descuadreFila(fila: Egreso?): Descuadre? {
    if (fila == null) return null
    if (fila.cantidadVarios || fila.precioUnitarioVarios) return null
    val cantidad = fila.cantidad ?: return null
    val precio = fila.precioUnitarioCent ?: return null
    val declarado = fila.montoTotalCent ?: return null
    val esperado = (cantidad * precio).roundToLong()
    val diferencia = declarado - esperado
    if (kotlin.math.abs(diferencia) <= 1) return null
    return Descuadre(esperado, declarado, diferencia)
}

// --------------------------------------------------------- Padrón

/**
 * ¿Estaba activa esta persona en la fecha `ref` ('AAAA-MM-DD' válida)?
 * Una baja (activo = false con bajaEn) no toca el historial: antes de esa
 * fecha la persona sigue contando. Solo activo = false sin fecha de baja la
 * deja fuera de todas las fechas.
 */
fun activoEn(a: Afiliado, ref: String): Boolean {
    if (!a.activo && !esIso(a.bajaEn)) return false
    if (esIso(a.altaEn) && a.altaEn > ref) return false
    if (esIso(a.bajaEn) && a.bajaEn <= ref) return false
    return true
}

fun afiliadosActivos(padron: Padron, fechaIso: String? = null): List<Afiliado> {
    val ref = if (esIso(fechaIso)) fechaIso!! else hoyIso()
    return padron.afiliados.filter { activoEn(it, ref) }
}

fun edadEnFecha(fechaNacimientoIso: String?, fechaRefIso: String?): Int? {
    val nac = aFecha(fechaNacimientoIso) ?: return null
    val ref = aFecha(fechaRefIso) ?: return null
    var edad = ref.year - nac.year
    val cumplioEsteAno = ref.monthValue > nac.monthValue ||
        (ref.monthValue == nac.monthValue && ref.dayOfMonth >= nac.dayOfMonth)
    if (!cumplioEsteAno) edad -= 1
    return edad
}

data class TramoEtario(val clave: String, val etiqueta: String, val detalle: String, val min: Int, val max: Int)

// Tramos de las «etapas de vida» del MINSA (RM 030-2020-MINSA). Cuatro grupos
// con las mismas fronteras: «adulto» reúne al adulto joven y al adulto.
val TRAMOS_ETARIOS = listOf(
    TramoEtario("nino", "Niño/a", "0 a 11 años", 0, 11),
    TramoEtario("adolescente", "Adolescente", "12 a 17 años", 12, 17),
    TramoEtario("adulto", "Adulto/a", "18 a 59 años", 18, 59),
    TramoEtario("adulto_mayor", "Adulto/a mayor", "60 a más", 60, Int.MAX_VALUE)
)

/** Clave del tramo etario para una edad en años. null si la edad no es válida. */
fun grupoPorEdad(edad: Int?): String? {
    if (edad == null || edad < 0) return null
    return TRAMOS_ETARIOS.firstOrNull { edad >= it.min && edad <= it.max }?.clave
}

/**
 * Se calcula SIEMPRE respecto a una fecha de referencia, nunca se congela al
 * alta. Sin fecha de nacimiento ni grupo manual devuelve null: no se asume "adulto".
 */
fun grupoEtario(afiliado: Afiliado, fechaRefIso: String? = null): String? {
    if (esIso(afiliado.fechaNacimiento)) {
        return grupoPorEdad(edadEnFecha(afiliado.fechaNacimiento, fechaRefIso ?: hoyIso()))
    }
    return afiliado.grupoEtarioManual?.takeIf { it.isNotEmpty() }
}

fun etiquetaGrupoEtario(clave: String?): String =
    TRAMOS_ETARIOS.firstOrNull { it.clave == clave }?.etiqueta ?: "Sin dato"

data class TipoAfiliado(val clave: String, val etiqueta: String, val menu: String)

val TIPOS_AFILIADO = listOf(
    TipoAfiliado("habitual", "Habitual", "normal"),
    TipoAfiliado("caso_social", "Ayuda social", "ayuda_social")
)

/** Menú que le corresponde por defecto a una persona, según cómo se inscribió. */
fun menuPorDefecto(afiliado: Afiliado): String =
    TIPOS_AFILIADO.firstOrNull { it.clave == afiliado.tipoAfiliado }?.menu ?: "normal"

data class ResumenEtario(val nino: Int, val adolescente: Int, val adulto: Int, val adultoMayor: Int, val sinDato: Int)

/** Conteo por grupo etario de los afiliados activos a una fecha. */
fun resumenEtario(padron: Padron, fechaRefIso: String? = null): ResumenEtario {
    val ref = if (esIso(fechaRefIso)) fechaRefIso!! else hoyIso()
    val conteo = mutableMapOf("nino" to 0, "adolescente" to 0, "adulto" to 0, "adulto_mayor" to 0, "sin_dato" to 0)
    for (a in afiliadosActivos(padron, ref)) {
        val g = grupoEtario(a, ref) ?: "sin_dato"
        conteo[g] = (conteo[g] ?: 0) + 1
    }
    return ResumenEtario(conteo["nino"]!!, conteo["adolescente"]!!, conteo["adulto"]!!, conteo["adulto_mayor"]!!, conteo["sin_dato"]!!)
}

// ------------------------------------------- Asistencia, raciones y precios
//
// Las raciones NO se escriben: se cuentan de la asistencia marcada. El precio
// sí está fijado de antemano y se copia a cada día nuevo.

fun atencionesDelPeriodo(padron: Padron, inicioIso: String?, finIso: String?): List<Atencion> {
    if (!esIso(inicioIso) || !esIso(finIso)) return emptyList()
    return padron.atenciones.filter { esIso(it.fecha) && it.fecha >= inicioIso!! && it.fecha <= finIso!! }
}

/** Las asistencias marcadas de un día. Única puerta de lectura de `atencion.asistencias`. */
fun asistenciasDelDia(at: Atencion?): List<Asistencia> = at?.asistencias ?: emptyList()

/** ¿Está marcada esta persona en este día? */
fun asistio(at: Atencion?, afiliadoId: String): Boolean =
    asistenciasDelDia(at).any { it.afiliadoId == afiliadoId }

/**
 * Las asistencias de un día con TODAS las personas de `activos` marcadas, para
 * «Marcar a todos». Quien ya estaba conserva el menú elegido ese día; los
 * demás reciben el de su inscripción. No modifica `at`.
 */
fun asistenciasConTodos(at: Atencion?, activos: List<Afiliado>): List<Asistencia> =
    activos.map { p ->
        val previo = asistenciasDelDia(at).firstOrNull { it.afiliadoId == p.id }
        Asistencia(afiliadoId = p.id, tipoMenu = previo?.tipoMenu ?: menuPorDefecto(p))
    }

fun menuDe(at: Atencion?, afiliadoId: String): String? =
    asistenciasDelDia(at).firstOrNull { it.afiliadoId == afiliadoId }?.tipoMenu

data class ConteoSinInscribir(val normal: Int, val ayudaSocial: Int, val total: Int)

/**
 * Quienes comieron ese día sin estar en el padrón: solo se cuentan, por menú.
 * `at.sinInscribir` puede faltar: vale 0 y 0. Un valor raro (negativo) no suma.
 */
fun sinInscribirDelDia(at: Atencion?): ConteoSinInscribir {
    val s = at?.sinInscribir
    fun n(v: Int?) = if (v != null && v > 0) v else 0
    val normal = n(s?.normal)
    val ayuda = n(s?.ayudaSocial)
    return ConteoSinInscribir(normal, ayuda, normal + ayuda)
}

enum class Origen(val clave: String) { ASISTENCIA("asistencia"), LEGADO("legado") }

data class Desglose(val normal: Int, val ayudaSocial: Int, val total: Int, val origen: Origen, val sinInscribir: Int)

/**
 * Raciones de un día, por tipo de menú: las de la asistencia (o las anotadas a
 * mano en la versión anterior) más las de quienes comieron sin estar inscritos.
 */
fun desgloseDelDia(at: Atencion?): Desglose {
    val asistencias = asistenciasDelDia(at)
    val fuera = sinInscribirDelDia(at)
    val legado = at?.legado
    if (asistencias.isEmpty() && legado != null) {
        val n = (legado.racionesNormales ?: 0) + fuera.normal
        val a = (legado.racionesAyudaSocial ?: 0) + fuera.ayudaSocial
        return Desglose(n, a, n + a, Origen.LEGADO, fuera.total)
    }
    val normal = asistencias.count { it.tipoMenu != "ayuda_social" } + fuera.normal
    val ayuda = asistencias.count { it.tipoMenu == "ayuda_social" } + fuera.ayudaSocial
    return Desglose(normal, ayuda, normal + ayuda, Origen.ASISTENCIA, fuera.total)
}

fun racionesDelDia(at: Atencion?): Int = desgloseDelDia(at).total

/**
 * Lo recaudado en un día: raciones x precio de su menú.
 * null si falta el precio de un tipo de menú que SÍ tuvo raciones.
 */
fun recaudacionDelDiaCent(at: Atencion?): Long? {
    if (at == null) return null
    val d = desgloseDelDia(at)
    var total = 0L
    for ((raciones, precio) in listOf(d.normal to at.precioMenuNormalCent, d.ayudaSocial to at.precioMenuAyudaSocialCent)) {
        if (raciones == 0) continue
        if (precio == null) return null   // falta el precio: no se estima
        total += raciones * precio
    }
    return total
}

fun racionesPeriodo(padron: Padron, inicioIso: String?, finIso: String?): Int =
    atencionesDelPeriodo(padron, inicioIso, finIso).sumOf { racionesDelDia(it) }

fun diasAtendidos(padron: Padron, inicioIso: String?, finIso: String?): Int =
    atencionesDelPeriodo(padron, inicioIso, finIso).count { racionesDelDia(it) > 0 }

fun promedioRaciones(padron: Padron, inicioIso: String?, finIso: String?): Double? {
    val dias = diasAtendidos(padron, inicioIso, finIso)
    if (dias == 0) return null
    return racionesPeriodo(padron, inicioIso, finIso).toDouble() / dias
}

data class ResumenEconomico(
    val racionesTotales: Int,
    val racionesNormales: Int,
    val racionesAyudaSocial: Int,
    val recaudacionCent: Long?,
    val racionesConPrecio: Int,
    val diasSinPrecio: Int,
    val diasDeLegado: Int,
    val precioPromedioRacionCent: Long?
)

/**
 * Resumen económico de las raciones de un periodo. El promedio es PONDERADO
 * por ración. `diasSinPrecio` se muestra siempre junto a él.
 */
fun resumenEconomicoRaciones(padron: Padron, inicioIso: String?, finIso: String?): ResumenEconomico {
    var recaudacion = 0L
    var racionesConPrecio = 0
    var diasSinPrecio = 0
    var racionesNormales = 0
    var racionesAyuda = 0
    var diasDeLegado = 0

    for (at in atencionesDelPeriodo(padron, inicioIso, finIso)) {
        val d = desgloseDelDia(at)
        racionesNormales += d.normal
        racionesAyuda += d.ayudaSocial
        if (d.origen == Origen.LEGADO) diasDeLegado += 1

        val dia = recaudacionDelDiaCent(at)
        if (dia == null) { diasSinPrecio += 1; continue }
        recaudacion += dia
        racionesConPrecio += d.total
    }

    return ResumenEconomico(
        racionesTotales = racionesNormales + racionesAyuda,
        racionesNormales = racionesNormales,
        racionesAyudaSocial = racionesAyuda,
        recaudacionCent = if (racionesConPrecio > 0) recaudacion else null,
        racionesConPrecio = racionesConPrecio,
        diasSinPrecio = diasSinPrecio,
        diasDeLegado = diasDeLegado,
        precioPromedioRacionCent = if (racionesConPrecio > 0) (recaudacion.toDouble() / racionesConPrecio).roundToLong() else null
    )
}

fun costoPorRacionCent(rendicion: Rendicion, padron: Padron): Long? {
    val raciones = racionesPeriodo(padron, rendicion.periodo.inicio, rendicion.periodo.fin)
    if (raciones == 0) return null
    return (totalRendicionGastos(rendicion).toDouble() / raciones).roundToLong()
}

data class ConteoAsistencia(val total: Int, val normal: Int, val ayudaSocial: Int, val origen: Origen)

fun conteoAsistenciaDelDia(at: Atencion?): ConteoAsistencia {
    val d = desgloseDelDia(at)
    return ConteoAsistencia(d.total, d.normal, d.ayudaSocial, d.origen)
}

data class AsistenciaAcumulada(val afiliadoId: String, val dias: Int, val normal: Int, val ayudaSocial: Int)

/**
 * Días que asistió cada persona dentro de un periodo, ordenado de más a menos.
 * Solo aparece quien asistió al menos un día. Los días con `legado` no suman.
 */
fun asistenciaPorAfiliado(padron: Padron, inicioIso: String?, finIso: String?): List<AsistenciaAcumulada> {
    val mapa = LinkedHashMap<String, AsistenciaAcumulada>()
    for (at in atencionesDelPeriodo(padron, inicioIso, finIso)) {
        for (a in asistenciasDelDia(at)) {
            if (a.afiliadoId.isEmpty()) continue
            val fila = mapa[a.afiliadoId] ?: AsistenciaAcumulada(a.afiliadoId, 0, 0, 0)
            mapa[a.afiliadoId] = if (a.tipoMenu == "ayuda_social") {
                fila.copy(dias = fila.dias + 1, ayudaSocial = fila.ayudaSocial + 1)
            } else {
                fila.copy(dias = fila.dias + 1, normal = fila.normal + 1)
            }
        }
    }
    return mapa.values.sortedByDescending { it.dias }
}

/**
 * Personas activas del padrón que NO asistieron ni un día en el periodo.
 * `activasAIso` dice a qué fecha se mira quién está activo; por defecto, al cierre.
 */
fun afiliadosSinAsistencia(padron: Padron, inicioIso: String?, finIso: String?, activasAIso: String? = finIso): List<Afiliado> {
    val conAsistencia = asistenciaPorAfiliado(padron, inicioIso, finIso).map { it.afiliadoId }.toSet()
    return afiliadosActivos(padron, activasAIso).filter { it.id !in conAsistencia }
}

// ------------------------------------------------- Reporte para el padrón

data class FilaPadron(
    val n: Int,
    val afiliadoId: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val nombres: String,
    val tipoDocumento: String,
    val numeroDocumento: String,
    val grupoEtario: String?,
    val grupoEtarioEtiqueta: String,
    val edad: Int?,
    val tipoAfiliado: String,
    val tipoAfiliadoEtiqueta: String,
    val sexo: String
)

/**
 * Filas del reporte del padrón: personas activas a `fechaRefIso`, ordenadas por
 * apellidos y nombres, y numeradas desde 1. Solo ordena y numera lo ingresado.
 */
fun filasPadron(padron: Padron, fechaRefIso: String? = null): List<FilaPadron> {
    val ref = if (esIso(fechaRefIso)) fechaRefIso!! else hoyIso()
    fun nombreOrden(a: Afiliado) = "${a.apellidoPaterno} ${a.apellidoMaterno} ${a.nombres}".trim()
    return afiliadosActivos(padron, ref)
        .sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreOrden(a), nombreOrden(b)) }
        .mapIndexed { i, a ->
            val g = grupoEtario(a, ref)
            FilaPadron(
                n = i + 1,
                afiliadoId = a.id,
                apellidoPaterno = a.apellidoPaterno,
                apellidoMaterno = a.apellidoMaterno,
                nombres = a.nombres,
                tipoDocumento = a.tipoDocumento.uppercase(),
                numeroDocumento = a.numeroDocumento,
                grupoEtario = g,
                grupoEtarioEtiqueta = if (g != null) etiquetaGrupoEtario(g) else "",
                edad = if (esIso(a.fechaNacimiento)) edadEnFecha(a.fechaNacimiento, ref) else null,
                tipoAfiliado = a.tipoAfiliado,
                tipoAfiliadoEtiqueta = when (a.tipoAfiliado) {
                    "caso_social" -> "Ayuda social"
                    "habitual" -> "Habitual"
                    else -> ""
                },
                sexo = a.sexo
            )
        }
}

// ----------------------------------------------------- Periodo propuesto
// La app PROPONE; la usuaria acepta o cambia. Nunca se aplica sola.

data class PeriodoPropuesto(
    val tipo: String,
    val inicio: String,
    val fin: String,
    val etiqueta: String,
    val alternativas: List<String>
) {
    fun aPeriodo() = Periodo(tipo = tipo, inicio = inicio, fin = fin, etiqueta = etiqueta)
}

fun proponerPeriodo(tipo: String, fechaRefIso: String? = null): PeriodoPropuesto {
    val ref = aFecha(if (esIso(fechaRefIso)) fechaRefIso else hoyIso())
        ?: return PeriodoPropuesto(tipo, "", "", "", emptyList())

    if (tipo == "semanal") {
        val inicio = ref.minusDays((ref.dayOfWeek.value - 1).toLong())   // lunes
        val fin = inicio.plusDays(6)                                      // domingo
        return PeriodoPropuesto(tipo, aIso(inicio), aIso(fin), "Entrega semanal", listOf("Entrega semanal"))
    }

    if (tipo == "quincenal") {
        val primeraMitad = ref.dayOfMonth <= 15
        val inicio = ref.withDayOfMonth(if (primeraMitad) 1 else 16)
        val fin = if (primeraMitad) ref.withDayOfMonth(15) else ref.withDayOfMonth(ref.lengthOfMonth())
        return PeriodoPropuesto(tipo, aIso(inicio), aIso(fin), "Quincenal", listOf("Quincenal"))
    }

    val inicio = ref.withDayOfMonth(1)
    val fin = ref.withDayOfMonth(ref.lengthOfMonth())
    val nombreMes = MESES[ref.monthValue - 1]
    // El ejemplar llenado a mano dice "Setiembre": se ofrecen las dos y elige la usuaria.
    return PeriodoPropuesto("mensual", aIso(inicio), aIso(fin), nombreMes, listOf(nombreMes, "Mensual"))
}

fun duracionPeriodo(periodo: Periodo?): Int? {
    if (periodo == null) return null
    val a = aFecha(periodo.inicio) ?: return null
    val b = aFecha(periodo.fin) ?: return null
    return ChronoUnit.DAYS.between(a, b).toInt() + 1
}
