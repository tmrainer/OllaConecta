// El padrón y la asistencia como PDF, para guardar o mandar por WhatsApp. No
// hay formato oficial para estos dos («Copia de referencia»): solo ordenan y
// suman lo que ya está en el padrón y la asistencia, sin completar ni deducir.
package io.github.tmrainer.asistencia.nucleo.documentos

import io.github.tmrainer.asistencia.nucleo.calculos.TRAMOS_ETARIOS
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciaPorAfiliado
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.atencionesDelPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.filasPadron
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import kotlin.math.min

private val DIAS = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
private val INICIAL = listOf("D", "L", "M", "M", "J", "V", "S")
private fun diaDeSemana(iso: String) = diaDeSemanaJs(iso)
internal const val MARGEN = 14.0

/** Un texto dentro de su celda: más chico hasta 5 pt y, si igual no entra, cortado con «…». */
internal fun celda(l: Lienzo, texto: Any?, x: Double, y: Double, w: Double, h: Double, pt: Double,
                  align: String = "left", negrita: Boolean = false) {
    if (texto == null || texto == "") return
    var t = if (texto is Number) numeroJs(texto) else texto.toString()
    val libre = w - 1.6
    var p = pt
    while (p > 5 && anchoMm(t, p, negrita) > libre) p -= 0.25
    if (anchoMm(t, p, negrita) > libre) {
        while (t.length > 1 && anchoMm("$t…", p, negrita) > libre) t = t.dropLast(1)
        t = t.trimEndJs() + "…"
    }
    val a = anchoMm(t, p, negrita)
    val xt = if (align == "right") x + w - 0.8 - a else if (align == "center") x + (w - a) / 2 else x + 0.8
    l.texto(t, xt, y + h / 2 + p / 2.8346 * 0.36, p, negrita)
}

/** Una columna de tabla. `align`: "left" | "center" | "right". */
internal class ColumnaTabla(val titulo: String, val ancho: Double, val align: String)

/**
 * Una tabla: fila de títulos (gris) y filas de datos alternando un fondo muy
 * claro, para seguir la fila con el dedo.
 * @return dónde termina (mm)
 */
internal fun tabla(l: Lienzo, x: Double, y: Double, columnas: List<ColumnaTabla>, filas: List<List<Any?>>,
                  altoTitulo: Double = 8.0, altoFila: Double = 6.0, pt: Double = 8.0, ultimaNegrita: Boolean = false): Double {
    val ancho = columnas.fold(0.0) { s, c -> s + c.ancho }
    l.gris(0.88); l.relleno(x, y, ancho, altoTitulo)
    l.gris(0.0); var cx = x
    for (c in columnas) { celda(l, c.titulo, cx, y, c.ancho, altoTitulo, 7.0, if (c.align == "left") "left" else "center", true); cx += c.ancho }
    var fy = y + altoTitulo
    filas.forEachIndexed { i, fila ->
        if (i % 2 == 1) { l.gris(0.96); l.relleno(x, fy, ancho, altoFila) }
        l.gris(0.0); cx = x
        val negrita = ultimaNegrita && i == filas.size - 1
        columnas.forEachIndexed { k, c -> celda(l, fila.getOrNull(k), cx, fy, c.ancho, altoFila, pt, c.align, negrita); cx += c.ancho }
        fy += altoFila
    }
    l.gris(0.55); l.grosor(0.2)
    l.caja(x, y, ancho, fy - y)
    l.linea(x, y + altoTitulo, x + ancho, y + altoTitulo)
    cx = x; for (c in columnas.dropLast(1)) { cx += c.ancho; l.linea(cx, y, cx, fy) }
    l.gris(0.0)
    return fy
}

internal class HojaDoc(val l: LienzoPdf, val apaisada: Boolean, val ancho: Double)
internal class Nueva(val l: LienzoPdf, val ancho: Double, val alto: Double, val arriba: Double)

/** Hojas con encabezado (título, centro, periodo) y, al final, el pie con «Hoja i de n». */
internal class Documento(val titulo: String, val subtitulo: String, val generado: String) {
    private val hojas = mutableListOf<HojaDoc>()

    fun nueva(apaisada: Boolean = false): Nueva {
        val ancho = if (apaisada) 297.0 else 210.0
        val l = lienzo(if (apaisada) 210.0 else 297.0)
        l.gris(0.3)
        l.texto("COPIA DE REFERENCIA — generada con OllaConecta el $generado. No reemplaza a un formato oficial.", MARGEN, 10.0, 6.5, negrita = true)
        l.gris(0.0)
        l.texto(titulo, MARGEN, 20.0, 13.0, negrita = true)
        if (subtitulo.isNotEmpty()) l.texto(subtitulo, MARGEN, 26.5, 8.5)
        hojas.add(HojaDoc(l, apaisada, ancho))
        return Nueva(l, ancho, if (apaisada) 210.0 else 297.0, 32.0)
    }

    fun bytes(nombre: String): ByteArray {
        hojas.forEachIndexed { i, h ->
            val alto = if (h.apaisada) 210.0 else 297.0
            h.l.gris(0.4)
            val t = "Hoja ${i + 1} de ${hojas.size}"
            h.l.texto(t, h.ancho - MARGEN - anchoMm(t, 7.0), alto - 7, 7.0)
            h.l.gris(0.0)
        }
        return armarPdf(hojas.map { PaginaPdf(it.l.ops.joinToString("\n"), it.apaisada) }, nombre)
    }
}

private fun periodoTexto(inicio: String, fin: String) = "del ${aDdMmAa(inicio)} al ${aDdMmAa(fin)}"

/**
 * Las personas activas, numeradas y ordenadas por apellido, con los días que
 * vinieron en el periodo, y al final cuántas hay por grupo de edad.
 * @param generado «14-10-26 18:40»; por defecto, ahora
 */
fun pdfDelPadron(padron: Padron, fechaRef: String, inicio: String, fin: String, centro: String = "",
                 generado: String = selloAhora()): ByteArray {
    val filas = filasPadron(padron, fechaRef)
    val dias = asistenciaPorAfiliado(padron, inicio, fin).associate { it.afiliadoId to it.dias }
    val doc = Documento(
        titulo = "Padrón de personas",
        subtitulo = "${if (centro.isNotEmpty()) "$centro · " else ""}Activas al ${aDdMmAa(fechaRef)} · días que vinieron ${periodoTexto(inicio, fin)}",
        generado = generado
    )
    val columnas = listOf(
        ColumnaTabla("N°", 9.0, "right"), ColumnaTabla("Apellidos y nombres", 66.0, "left"),
        ColumnaTabla("Documento", 25.0, "center"), ColumnaTabla("Grupo de edad", 25.0, "center"),
        ColumnaTabla("Edad", 11.0, "center"), ColumnaTabla("Tipo", 24.0, "center"),
        ColumnaTabla("Días", 12.0, "right")
    )
    val datos = filas.map { f ->
        listOf(
            f.n, "${listOf(f.apellidoPaterno, f.apellidoMaterno).filter { it.isNotEmpty() }.joinToString(" ")}, ${f.nombres}".removePrefix(", "),
            "${f.tipoDocumento} ${f.numeroDocumento}".trimJs(), f.grupoEtarioEtiqueta.ifEmpty { "—" }, f.edad ?: "—",
            f.tipoAfiliadoEtiqueta.ifEmpty { "—" }, dias[f.afiliadoId] ?: 0
        )
    }
    val porHoja = 38
    var hoja = doc.nueva()
    if (datos.isEmpty()) hoja.l.texto("El padrón está vacío.", MARGEN, hoja.arriba + 8, 10.0)
    for (i in datos.indices step porHoja) {
        if (i > 0) hoja = doc.nueva()
        val finTabla = tabla(hoja.l, MARGEN, hoja.arriba, columnas, datos.subList(i, min(i + porHoja, datos.size)))
        if (i + porHoja >= datos.size) {
            val porGrupo = TRAMOS_ETARIOS.associate { it.clave to 0 }.toMutableMap()
            var social = 0
            for (f in filas) {
                val g = f.grupoEtario
                if (g != null && g in porGrupo) porGrupo[g] = porGrupo.getValue(g) + 1
                if (f.tipoAfiliado == "caso_social") social += 1
            }
            hoja.l.texto("Total: ${filas.size} personas", MARGEN, finTabla + 8, 9.5, negrita = true)
            hoja.l.texto(TRAMOS_ETARIOS.joinToString("   ·   ") { "${it.etiqueta}: ${porGrupo[it.clave]}" } + "   ·   Ayuda social: $social",
                MARGEN, finTabla + 14, 8.5)
        }
    }
    return doc.bytes("Padrón de personas")
}

private const val MAX_DIAS_PLANILLA = 31

/**
 * Resumen por día (raciones por menú, sin inscribir, recaudado) y, si el
 * periodo tiene hasta 31 días con atención, la planilla: una fila por
 * persona y una columna por día (X: menú normal; A: ayuda social).
 * @param generado «14-10-26 18:40»; por defecto, ahora
 */
fun pdfDeAsistencia(padron: Padron, inicio: String, fin: String, centro: String = "",
                    generado: String = selloAhora()): ByteArray {
    val dias = atencionesDelPeriodo(padron, inicio, fin).sortedBy { it.fecha }
    val doc = Documento("Asistencia", "${if (centro.isNotEmpty()) "$centro · " else ""}${periodoTexto(inicio, fin)}", generado)

    // 1. Resumen por día
    val columnas = listOf(
        ColumnaTabla("Fecha", 22.0, "center"), ColumnaTabla("Día", 24.0, "left"),
        ColumnaTabla("Menú normal", 25.0, "right"), ColumnaTabla("Ayuda social", 25.0, "right"),
        ColumnaTabla("Sin inscribir*", 25.0, "right"), ColumnaTabla("Raciones", 25.0, "right"),
        ColumnaTabla("Recaudado S/", 30.0, "right")
    )
    var sNormal = 0; var sAyuda = 0; var sFuera = 0; var sTotal = 0; var sSoles = 0L
    val filas: MutableList<List<Any?>> = dias.map { at ->
        val d = desgloseDelDia(at)
        val fuera = d.sinInscribir
        val soles = recaudacionDelDiaCent(at)
        sNormal += d.normal; sAyuda += d.ayudaSocial; sFuera += fuera; sTotal += d.total; sSoles += soles ?: 0
        listOf(aDdMmAa(at.fecha), DIAS[diaDeSemana(at.fecha)], d.normal, d.ayudaSocial, fuera, d.total,
            if (soles == null) "—" else formatearSoles(soles))
    }.toMutableList()
    if (filas.isNotEmpty()) filas.add(listOf("Total", "${dias.size} días", sNormal, sAyuda, sFuera, sTotal, formatearSoles(sSoles)))
    val porHoja = 38
    var hoja = doc.nueva()
    hoja.l.texto("Resumen por día", MARGEN, hoja.arriba + 2, 10.0, negrita = true)
    if (filas.isEmpty()) hoja.l.texto("No hay días con asistencia en estas fechas.", MARGEN, hoja.arriba + 10, 9.0)
    for (i in filas.indices step porHoja) {
        if (i > 0) hoja = doc.nueva()
        val finTabla = tabla(hoja.l, MARGEN, hoja.arriba + 5, columnas, filas.subList(i, min(i + porHoja, filas.size)),
            ultimaNegrita = i + porHoja >= filas.size)
        hoja.l.texto("* Quienes comieron sin estar en el padrón. Ya están contados en «Menú normal» y «Ayuda social».", MARGEN, finTabla + 5, 7.0)
    }

    // 2. Planilla por persona (hojas apaisadas)
    if (dias.isNotEmpty() && dias.size <= MAX_DIAS_PLANILLA) {
        val marcas = dias.map { at -> asistenciasDelDia(at).associate { it.afiliadoId to it.tipoMenu } }
        val vinieron = marcas.flatMap { it.keys }.toSet()
        val unicas = LinkedHashMap<String, Afiliado>()
        for (a in afiliadosActivos(padron, fin) + padron.afiliados.filter { it.id in vinieron }) unicas[a.id] = a
        val personas = unicas.values.sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
        val anchoDia = min(8.0, (297 - 2 * MARGEN - 8 - 60 - 12) / dias.size)
        val cols = listOf(ColumnaTabla("N°", 8.0, "right"), ColumnaTabla("Apellidos y nombres", 60.0, "left")) +
            dias.map { at -> ColumnaTabla("${at.fecha.substring(8)} ${INICIAL[diaDeSemana(at.fecha)]}", anchoDia, "center") } +
            ColumnaTabla("Días", 12.0, "right")
        val filasP: MutableList<List<Any?>> = personas.mapIndexed { i, a ->
            val fila = marcas.map { m -> if (a.id in m) (if (m[a.id] == "ayuda_social") "A" else "X") else "" }
            listOf<Any?>(i + 1, nombreCompleto(a)) + fila + fila.count { it.isNotEmpty() }
        }.toMutableList()
        filasP.add(listOf<Any?>("", "Total del día") + marcas.map { it.size } + marcas.sumOf { it.size })
        val porHojaP = 30
        for (i in filasP.indices step porHojaP) {
            val h = doc.nueva(true)
            h.l.texto("Por persona · X: vino (menú normal) · A: vino (ayuda social)", MARGEN, h.arriba + 2, 9.0, negrita = true)
            tabla(h.l, MARGEN, h.arriba + 5, cols, filasP.subList(i, min(i + porHojaP, filasP.size)), altoFila = 5.0, pt = 7.5,
                ultimaNegrita = i + porHojaP >= filasP.size)
        }
    } else if (dias.size > MAX_DIAS_PLANILLA) {
        hoja.l.texto("La planilla por persona sale con $MAX_DIAS_PLANILLA días o menos: elige fechas más cortas (por ejemplo, un mes).", MARGEN, 286.0, 7.5)
    }
    return doc.bytes("Asistencia")
}
