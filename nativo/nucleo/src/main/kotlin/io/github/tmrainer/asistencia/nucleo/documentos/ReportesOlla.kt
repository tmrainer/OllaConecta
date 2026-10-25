// ReportesOlla.kt — los reportes que Paola presenta, en PDF y en Excel:
// - del día: qué se cocinó, raciones preparadas y servidas, y quiénes recibieron;
// - del mes: la asistencia de cada persona, las raciones del mes día por día,
//   los insumos del B-1 comprados en el mes y, aparte, el gasto propio.
//
// Solo ordena y suma lo ya ingresado: lo que no se anotó sale como «no se
// anotó», nunca se completa. Puro: recibe los datos y devuelve los bytes.
package io.github.tmrainer.asistencia.nucleo.documentos

import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciaPorAfiliado
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.atencionesDelPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEconomicoRaciones
import io.github.tmrainer.asistencia.nucleo.modelo.GastoPropio
import io.github.tmrainer.asistencia.nucleo.modelo.MESES
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.fechaLarga
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.pad2
import kotlin.math.min

private const val NO_ANOTADO = "(no se anotó)"
private val ORIGEN = mapOf("subsidio" to "Subsidio", "ayuda_social" to "Ayuda social", "aporte_propio" to "Aporte propio")

// ---------------------------------------------------------------- los datos

/** Quien recibió ración ese día. */
data class AsistenteDelDia(val n: Int, val nombre: String, val documento: String, val menu: String)

data class ReporteDia(
    val fecha: String,
    val menuDelDia: String,
    val preparadas: Int?,
    val normal: Int,
    val ayudaSocial: Int,
    val sinInscribir: Int,
    val servidas: Int,
    val recaudadoCent: Long?,
    val asistentes: List<AsistenteDelDia>
)

fun reporteDelDia(padron: Padron, fecha: String): ReporteDia {
    val at = padron.atenciones.firstOrNull { it.fecha == fecha }
    val d = desgloseDelDia(at)
    val porId = padron.afiliados.associateBy { it.id }
    val asistentes = asistenciasDelDia(at).mapNotNull { a -> porId[a.afiliadoId]?.let { it to a.tipoMenu } }
        .sortedWith { x, y -> ORDEN_NOMBRES.compare(nombreCompleto(x.first), nombreCompleto(y.first)) }
        .mapIndexed { i, (p, menu) -> AsistenteDelDia(i + 1, nombreCompleto(p), p.numeroDocumento, if (menu == "ayuda_social") "Ayuda social" else "Normal") }
    return ReporteDia(fecha, at?.menuDelDia?.trim().orEmpty(), at?.racionesPreparadas, d.normal, d.ayudaSocial, d.sinInscribir, d.total,
        if (at != null) recaudacionDelDiaCent(at) else null, asistentes)
}

data class DiaDelMes(val fecha: String, val menuDelDia: String, val preparadas: Int?, val servidas: Int, val normal: Int, val ayudaSocial: Int, val sinInscribir: Int)

/** Una persona en el consolidado del mes: cuántos días vino. */
data class PersonaDelMes(val n: Int, val nombre: String, val documento: String, val dias: Int, val normal: Int, val ayudaSocial: Int, val activa: Boolean)

/** Un insumo del B-1 comprado en el mes. */
data class InsumoDelMes(val fecha: String, val descripcion: String, val cantidad: String, val montoCent: Long?, val origen: String)

data class ReporteMes(
    val mes: String,                 // AAAA-MM
    val inicio: String,
    val fin: String,
    val dias: List<DiaDelMes>,
    val servidas: Int,
    val normal: Int,
    val ayudaSocial: Int,
    val sinInscribir: Int,
    /** Suma de las preparadas de los días en que se anotaron. */
    val preparadas: Int,
    val diasConPreparadas: Int,
    val recaudadoCent: Long?,
    val personas: List<PersonaDelMes>,
    /** Marcas de personas que ya no están en el padrón (se borraron): cuentan en las raciones, sin nombre. */
    val marcasSinPersona: Int,
    val insumos: List<InsumoDelMes>,
    val insumosCent: Long,
    val gastosPropios: List<GastoPropio>,
    val gastoPropioCent: Long
) {
    val etiqueta: String get() = etiquetaMes(mes)
}

/** «Octubre 2026». */
fun etiquetaMes(mes: String): String {
    val f = aFecha("$mes-01") ?: return mes
    return "${MESES[f.monthValue - 1]} ${f.year}"
}

/** El primer y el último día del mes AAAA-MM. */
fun diasDelMes(mes: String): Pair<String, String> {
    val f = aFecha("$mes-01") ?: return "" to ""
    return "$mes-01" to "$mes-${pad2(f.lengthOfMonth())}"
}

fun reporteDelMes(padron: Padron, rendiciones: List<Rendicion>, mes: String): ReporteMes {
    val (inicio, fin) = diasDelMes(mes)
    val dias = atencionesDelPeriodo(padron, inicio, fin).sortedBy { it.fecha }.map { at ->
        val d = desgloseDelDia(at)
        DiaDelMes(at.fecha, at.menuDelDia.trim(), at.racionesPreparadas, d.total, d.normal, d.ayudaSocial, d.sinInscribir)
    }
    val economico = resumenEconomicoRaciones(padron, inicio, fin)
    // Todas las personas activas en el mes (también las que no vinieron) y quienes vinieron.
    val acumulado = asistenciaPorAfiliado(padron, inicio, fin).associateBy { it.afiliadoId }
    val activas = afiliadosActivos(padron, fin).map { it.id }.toSet()
    val incluidas = padron.afiliados.filter { it.id in activas || it.id in acumulado }
        .sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
    val personas = incluidas.mapIndexed { i, a ->
        val x = acumulado[a.id]
        PersonaDelMes(i + 1, nombreCompleto(a), a.numeroDocumento, x?.dias ?: 0, x?.normal ?: 0, x?.ayudaSocial ?: 0, a.id in activas)
    }
    fun enElMes(iso: String) = esIso(iso) && iso >= inicio && iso <= fin
    val insumos = rendiciones.flatMap { it.egresos }.filter { enElMes(it.fechaCompra) }.sortedBy { it.fechaCompra }.map { e ->
        val cantidad = when {
            e.cantidadVarios -> "varios"
            e.cantidad != null -> listOf(numeroJs(e.cantidad), e.unidadMedida).filter { it.isNotEmpty() }.joinToString(" ")
            else -> e.unidadMedida
        }
        InsumoDelMes(e.fechaCompra, e.descripcion.trim(), cantidad, e.montoTotalCent, ORIGEN[e.origenFondo] ?: "Subsidio")
    }
    val gastos = rendiciones.flatMap { it.gastosPropios }.filter { enElMes(it.fecha) }.sortedBy { it.fecha }
    return ReporteMes(
        mes, inicio, fin, dias,
        servidas = dias.sumOf { it.servidas }, normal = dias.sumOf { it.normal }, ayudaSocial = dias.sumOf { it.ayudaSocial },
        sinInscribir = dias.sumOf { it.sinInscribir },
        preparadas = dias.sumOf { it.preparadas ?: 0 }, diasConPreparadas = dias.count { it.preparadas != null },
        recaudadoCent = economico.recaudacionCent,
        personas = personas,
        marcasSinPersona = acumulado.filterKeys { id -> padron.afiliados.none { it.id == id } }.values.sumOf { it.dias },
        insumos = insumos, insumosCent = insumos.sumOf { it.montoCent ?: 0L },
        gastosPropios = gastos, gastoPropioCent = gastos.sumOf { it.montoCent ?: 0L }
    )
}

// ---------------------------------------------------------------- en PDF

private fun encabezado(centro: String, resto: String) = listOf(centro.trim(), resto).filter { it.isNotEmpty() }.joinToString(" · ")

/** Las líneas para firmar a mano, con el nombre de la responsable si se sabe. */
private fun firma(n: Nueva, y: Double, responsable: String) {
    val l = n.l
    l.gris(0.0); l.grosor(0.3)
    l.linea(MARGEN, y, MARGEN + 70, y)
    l.texto("Firma de la responsable", MARGEN, y + 4.5, 8.0, negrita = true)
    if (responsable.isNotBlank()) l.texto(responsable.trim(), MARGEN, y + 9.5, 8.5)
}

private fun lineas(n: Nueva, desde: Double, pares: List<Pair<String, String>>): Double {
    var y = desde
    for ((rotulo, valor) in pares) {
        n.l.texto(rotulo, MARGEN, y, 9.0, negrita = true)
        n.l.texto(valor, MARGEN + 48, y, 9.0)
        y += 6.0
    }
    return y
}

/** El reporte del día. [responsable]: quién usa el teléfono (va bajo la línea de la firma). */
fun pdfDelDia(padron: Padron, fecha: String, centro: String = "", responsable: String = "", generado: String = selloAhora()): ByteArray {
    val r = reporteDelDia(padron, fecha)
    val doc = Documento("Reporte del día", encabezado(centro, "${fechaLarga(fecha)} (${aDdMmAa(fecha)})"), generado)
    var hoja = doc.nueva()
    var y = lineas(hoja, hoja.arriba + 4, listOf(
        "Qué se cocinó" to r.menuDelDia.ifEmpty { NO_ANOTADO },
        "Raciones preparadas" to (r.preparadas?.toString() ?: NO_ANOTADO),
        "Raciones servidas" to "${r.servidas} (${r.normal} de menú normal, ${r.ayudaSocial} de ayuda social" +
            (if (r.sinInscribir > 0) "; ${r.sinInscribir} sin estar en el padrón" else "") + ")",
        "Recaudado" to (r.recaudadoCent?.let { "S/ ${formatearSoles(it)}" } ?: "sin precio del menú")
    ))
    y += 4
    hoja.l.texto("Quiénes recibieron ración (${r.asistentes.size})", MARGEN, y, 10.0, negrita = true)
    val columnas = listOf(ColumnaTabla("N°", 10.0, "right"), ColumnaTabla("Apellidos y nombres", 100.0, "left"), ColumnaTabla("DNI", 32.0, "center"), ColumnaTabla("Menú", 40.0, "center"))
    val filas = r.asistentes.map { listOf(it.n, it.nombre, it.documento, it.menu) }
    var fin = y + 3
    if (filas.isEmpty()) { hoja.l.texto("Nadie marcado ese día.", MARGEN, y + 8, 9.0); fin = y + 10 }
    val porHoja = 34
    for (i in filas.indices step porHoja) {
        if (i > 0) { hoja = doc.nueva(); fin = hoja.arriba }
        fin = tabla(hoja.l, MARGEN, if (i == 0) y + 3 else hoja.arriba, columnas, filas.subList(i, min(i + porHoja, filas.size)))
    }
    // La firma entra debajo si hay lugar antes del pie (290 mm); si no, en una hoja más.
    if (fin + 18 + 12 > 285) { hoja = doc.nueva(); fin = hoja.arriba }
    firma(hoja, fin + 18, responsable)
    return doc.bytes("Reporte del día ${aDdMmAa(fecha)}")
}

/** El reporte del mes AAAA-MM: resumen, día por día, por persona, insumos del B-1 y gasto propio. */
fun pdfDelMes(padron: Padron, rendiciones: List<Rendicion>, mes: String, centro: String = "", responsable: String = "", generado: String = selloAhora()): ByteArray {
    val r = reporteDelMes(padron, rendiciones, mes)
    val doc = Documento("Reporte del mes", encabezado(centro, r.etiqueta), generado)
    var hoja = doc.nueva()
    var y = lineas(hoja, hoja.arriba + 4, listOf(
        "Días con atención" to "${r.dias.size}",
        "Raciones servidas" to "${r.servidas} (${r.normal} de menú normal, ${r.ayudaSocial} de ayuda social)",
        "Raciones preparadas" to if (r.diasConPreparadas == 0) NO_ANOTADO else "${r.preparadas} (anotadas en ${r.diasConPreparadas} de ${r.dias.size} días)",
        "Recaudado" to (r.recaudadoCent?.let { "S/ ${formatearSoles(it)}" } ?: "sin precio del menú"),
        "Insumos del B-1" to "S/ ${formatearSoles(r.insumosCent)} en ${r.insumos.size} compras",
        "Gasto propio" to if (r.gastosPropios.isEmpty()) "no se anotó" else "S/ ${formatearSoles(r.gastoPropioCent)} (no va en el B-1)"
    ))

    // Día por día.
    y += 4
    hoja.l.texto("Día por día", MARGEN, y, 10.0, negrita = true)
    val colDias = listOf(ColumnaTabla("Fecha", 20.0, "center"), ColumnaTabla("Qué se cocinó", 102.0, "left"), ColumnaTabla("Preparadas", 30.0, "right"), ColumnaTabla("Servidas", 30.0, "right"))
    val filasDias: MutableList<List<Any?>> = r.dias.map { listOf(aDdMmAa(it.fecha), it.menuDelDia.ifEmpty { "—" }, it.preparadas ?: "—", it.servidas) }.toMutableList()
    if (filasDias.isNotEmpty()) filasDias.add(listOf("Total", "", if (r.diasConPreparadas == 0) "—" else r.preparadas, r.servidas))
    var fin = y + 3
    if (r.dias.isEmpty()) { hoja.l.texto("No hay días con atención en este mes.", MARGEN, y + 8, 9.0); fin = y + 10 }
    val porHoja = 30
    for (i in filasDias.indices step porHoja) {
        if (i > 0) hoja = doc.nueva()
        fin = tabla(hoja.l, MARGEN, if (i == 0) y + 3 else hoja.arriba, colDias, filasDias.subList(i, min(i + porHoja, filasDias.size)), ultimaNegrita = i + porHoja >= filasDias.size)
    }

    // Asistencia por persona.
    hoja = doc.nueva()
    hoja.l.texto("Asistencia por persona (${r.personas.size})", MARGEN, hoja.arriba + 2, 10.0, negrita = true)
    val colPersonas = listOf(ColumnaTabla("N°", 10.0, "right"), ColumnaTabla("Apellidos y nombres", 92.0, "left"), ColumnaTabla("DNI", 28.0, "center"),
        ColumnaTabla("Días", 16.0, "right"), ColumnaTabla("Normal", 18.0, "right"), ColumnaTabla("Ayuda social", 18.0, "right"))
    val filasPersonas = r.personas.map { listOf(it.n, it.nombre + if (!it.activa) " (de baja)" else "", it.documento, it.dias, it.normal, it.ayudaSocial) }
    if (filasPersonas.isEmpty()) hoja.l.texto("El padrón está vacío.", MARGEN, hoja.arriba + 10, 9.0)
    val porHojaP = 38
    for (i in filasPersonas.indices step porHojaP) {
        if (i > 0) hoja = doc.nueva()
        fin = tabla(hoja.l, MARGEN, hoja.arriba + 5, colPersonas, filasPersonas.subList(i, min(i + porHojaP, filasPersonas.size)))
    }
    if (r.marcasSinPersona > 0) hoja.l.texto("Además, ${r.marcasSinPersona} ${if (r.marcasSinPersona == 1) "marca es" else "marcas son"} de personas que ya no están en el padrón: cuentan en las raciones.", MARGEN, fin + 5, 7.5)

    // Insumos del B-1 y, aparte, el gasto propio.
    hoja = doc.nueva()
    hoja.l.texto("Insumos del B-1 comprados en el mes", MARGEN, hoja.arriba + 2, 10.0, negrita = true)
    val colInsumos = listOf(ColumnaTabla("Fecha", 20.0, "center"), ColumnaTabla("Descripción", 78.0, "left"), ColumnaTabla("Cantidad", 30.0, "center"),
        ColumnaTabla("Dinero", 26.0, "center"), ColumnaTabla("Monto S/", 28.0, "right"))
    val filasInsumos: MutableList<List<Any?>> = r.insumos.map { listOf(aDdMmAa(it.fecha), it.descripcion.ifEmpty { "—" }, it.cantidad.ifEmpty { "—" }, it.origen, it.montoCent?.let(::formatearSoles) ?: "—") }.toMutableList()
    if (filasInsumos.isNotEmpty()) filasInsumos.add(listOf("Total", "", "", "", formatearSoles(r.insumosCent)))
    fin = hoja.arriba + 10
    if (filasInsumos.isEmpty()) hoja.l.texto("No hay compras del B-1 con fecha en este mes.", MARGEN, hoja.arriba + 10, 9.0)
    for (i in filasInsumos.indices step porHojaP) {
        if (i > 0) hoja = doc.nueva()
        fin = tabla(hoja.l, MARGEN, hoja.arriba + 5, colInsumos, filasInsumos.subList(i, min(i + porHojaP, filasInsumos.size)), ultimaNegrita = i + porHojaP >= filasInsumos.size)
    }
    if (r.gastosPropios.isNotEmpty()) {
        if (fin > 220) { hoja = doc.nueva(); fin = hoja.arriba }
        hoja.l.texto("Gasto propio (no va en el B-1)", MARGEN, fin + 10, 10.0, negrita = true)
        val colGastos = listOf(ColumnaTabla("Fecha", 20.0, "center"), ColumnaTabla("Qué compraron", 134.0, "left"), ColumnaTabla("Monto S/", 28.0, "right"))
        val filasGastos = r.gastosPropios.map { listOf(aDdMmAa(it.fecha), it.descripcion.ifEmpty { "—" }, it.montoCent?.let(::formatearSoles) ?: "—") } +
            listOf(listOf("Total", "", formatearSoles(r.gastoPropioCent)))
        fin = tabla(hoja.l, MARGEN, fin + 13, colGastos, filasGastos, ultimaNegrita = true)
    }
    // La firma entra debajo si hay lugar antes del pie (290 mm); si no, en una hoja más.
    if (fin + 18 + 12 > 285) { hoja = doc.nueva(); fin = hoja.arriba }
    firma(hoja, fin + 18, responsable)
    return doc.bytes("Reporte del mes ${r.etiqueta}")
}

// ---------------------------------------------------------------- en Excel

private fun n(v: Any?) = Celda(v, negrita = true)
private fun soles(cent: Long?): Any = if (cent != null) Celda(cent / 100.0, soles = true) else ""

fun excelDelDia(padron: Padron, fecha: String, centro: String = ""): ByteArray {
    val r = reporteDelDia(padron, fecha)
    val filas = mutableListOf<List<Any?>>(
        listOf(n("Reporte del día")),
        listOf(encabezado(centro, "${fechaLarga(fecha)} (${aDdMmAa(fecha)})")),
        emptyList(),
        listOf(n("Qué se cocinó"), r.menuDelDia.ifEmpty { NO_ANOTADO }),
        listOf(n("Raciones preparadas"), r.preparadas ?: NO_ANOTADO),
        listOf(n("Raciones servidas"), r.servidas),
        listOf("Menú normal", r.normal),
        listOf("Ayuda social", r.ayudaSocial),
        listOf("Sin estar en el padrón", r.sinInscribir),
        listOf(n("Recaudado (S/)"), soles(r.recaudadoCent)),
        emptyList(),
        listOf(n("N°"), n("Apellidos y nombres"), n("DNI"), n("Menú"))
    )
    r.asistentes.forEach { filas.add(listOf(it.n, it.nombre, it.documento, it.menu)) }
    return armarXlsx(listOf(HojaXlsx("Reporte del día", filas, listOf(22, 40, 14, 14))))
}

fun excelDelMes(padron: Padron, rendiciones: List<Rendicion>, mes: String, centro: String = ""): ByteArray {
    val r = reporteDelMes(padron, rendiciones, mes)
    val titulo = encabezado(centro, r.etiqueta)
    val resumen = listOf(
        listOf(n("Reporte del mes")), listOf(titulo), emptyList(),
        listOf(n("Días con atención"), r.dias.size),
        listOf(n("Raciones servidas"), r.servidas),
        listOf("Menú normal", r.normal),
        listOf("Ayuda social", r.ayudaSocial),
        listOf("Sin estar en el padrón", r.sinInscribir),
        listOf(n("Raciones preparadas"), if (r.diasConPreparadas == 0) NO_ANOTADO else r.preparadas),
        listOf("Días con preparadas anotadas", r.diasConPreparadas),
        listOf(n("Recaudado (S/)"), soles(r.recaudadoCent)),
        listOf(n("Insumos del B-1 (S/)"), soles(r.insumosCent)),
        listOf(n("Gasto propio, fuera del B-1 (S/)"), if (r.gastosPropios.isEmpty()) "no se anotó" else soles(r.gastoPropioCent))
    )
    val porDia = listOf(listOf(n("Fecha"), n("Qué se cocinó"), n("Preparadas"), n("Servidas"), n("Menú normal"), n("Ayuda social"), n("Sin estar en el padrón"))) +
        r.dias.map { listOf(aDdMmAa(it.fecha), it.menuDelDia, it.preparadas ?: "", it.servidas, it.normal, it.ayudaSocial, it.sinInscribir) }
    val porPersona = listOf(listOf(n("N°"), n("Apellidos y nombres"), n("DNI"), n("Días"), n("Menú normal"), n("Ayuda social"), n("Estado"))) +
        r.personas.map { listOf(it.n, it.nombre, it.documento, it.dias, it.normal, it.ayudaSocial, if (it.activa) "Activa" else "De baja") } +
        (if (r.marcasSinPersona > 0) listOf(emptyList(), listOf("", "Marcas de personas que ya no están en el padrón", "", r.marcasSinPersona)) else emptyList())
    val insumos = listOf(listOf(n("Fecha"), n("Descripción"), n("Cantidad"), n("Dinero"), n("Monto (S/)"))) +
        r.insumos.map { listOf(aDdMmAa(it.fecha), it.descripcion, it.cantidad, it.origen, soles(it.montoCent)) } +
        listOf(listOf(n("Total"), "", "", "", soles(r.insumosCent)))
    val gastos = listOf(listOf(n("Fecha"), n("Qué compraron"), n("Monto (S/)"))) +
        r.gastosPropios.map { listOf(aDdMmAa(it.fecha), it.descripcion, soles(it.montoCent)) } +
        listOf(listOf(n("Total"), "", soles(r.gastoPropioCent)))
    return armarXlsx(listOf(
        HojaXlsx("Resumen", resumen, listOf(34, 30)),
        HojaXlsx("Día por día", porDia, listOf(12, 40, 12, 10, 12, 12, 20)),
        HojaXlsx("Por persona", porPersona, listOf(6, 40, 14, 8, 12, 12, 10)),
        HojaXlsx("Insumos del B-1", insumos, listOf(12, 40, 14, 14, 12)),
        HojaXlsx("Gasto propio", gastos, listOf(12, 40, 12))
    ))
}

// ------------------------------------- las pendientes, para llevar a la Municipalidad

/**
 * Quienes la olla inscribió y falta registrar en la Municipalidad, para
 * llevar la lista al trámite: nombre, documento, fecha de nacimiento si se
 * sabe y desde cuándo vienen a la olla.
 */
private fun filasPendientes(padron: Padron): List<List<Any?>> =
    padron.afiliados.filter { it.activo && it.registroMunicipal == "pendiente" }
        .sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
        .mapIndexed { i, a ->
            listOf(i + 1, nombreCompleto(a), listOf(if (a.tipoDocumento == "ce") "CE" else "DNI", a.numeroDocumento).joinToString(" ").trim(),
                if (esIso(a.fechaNacimiento)) aDdMmAa(a.fechaNacimiento) else "—", if (esIso(a.altaEn)) aDdMmAa(a.altaEn) else "—")
        }

fun pdfDePendientes(padron: Padron, centro: String = "", responsable: String = "", generado: String = selloAhora()): ByteArray {
    val filas = filasPendientes(padron)
    val doc = Documento("Para registrar en la Municipalidad", encabezado(centro, cuantosTexto(filas.size)), generado)
    var hoja = doc.nueva()
    val columnas = listOf(ColumnaTabla("N°", 10.0, "right"), ColumnaTabla("Apellidos y nombres", 86.0, "left"), ColumnaTabla("Documento", 32.0, "center"),
        ColumnaTabla("Nacimiento", 26.0, "center"), ColumnaTabla("En la olla desde", 28.0, "center"))
    var fin = hoja.arriba + 10
    if (filas.isEmpty()) hoja.l.texto("No hay personas pendientes de registrar.", MARGEN, hoja.arriba + 8, 10.0)
    for (i in filas.indices step 38) {
        if (i > 0) hoja = doc.nueva()
        fin = tabla(hoja.l, MARGEN, hoja.arriba + 3, columnas, filas.subList(i, min(i + 38, filas.size)))
    }
    if (fin + 18 + 12 > 285) { hoja = doc.nueva(); fin = hoja.arriba }
    firma(hoja, fin + 18, responsable)
    return doc.bytes("Para registrar en la Municipalidad")
}

fun excelDePendientes(padron: Padron, centro: String = ""): ByteArray {
    val filas = listOf(listOf(n("Para registrar en la Municipalidad")), listOf(centro.trim()), emptyList(),
        listOf(n("N°"), n("Apellidos y nombres"), n("Documento"), n("Nacimiento"), n("En la olla desde"))) + filasPendientes(padron)
    return armarXlsx(listOf(HojaXlsx("Pendientes", filas, listOf(6, 40, 16, 12, 16))))
}

private fun cuantosTexto(n: Int) = if (n == 1) "1 persona" else "$n personas"
