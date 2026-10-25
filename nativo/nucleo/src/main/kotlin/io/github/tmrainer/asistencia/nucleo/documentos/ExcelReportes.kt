// El padrón, la asistencia y el B-1 como Excel (.xlsx). Las mismas cifras que
// los PDF, pero en celdas: los números son números y los DNI, texto (no pierden
// el 0 de adelante). Solo ordenan y suman lo guardado; no completan nada.
package io.github.tmrainer.asistencia.nucleo.documentos

import io.github.tmrainer.asistencia.nucleo.calculos.TRAMOS_ETARIOS
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciaPorAfiliado
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.atencionesDelPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.filasPadron
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto

private val DIAS = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
private fun negrita(v: Any?) = Celda(v, negrita = true)
private fun soles(cent: Long?, conNegrita: Boolean = false): Any = if (cent != null) Celda(cent / 100.0, soles = true, negrita = conNegrita) else ""
private fun periodoTexto(inicio: String, fin: String) = "del ${aDdMmAa(inicio)} al ${aDdMmAa(fin)}"

// Los mismos títulos que entiende la importación: el archivo se puede volver a
// cargar en otro teléfono.
val CABECERA_PADRON = listOf("N°", "Apellido paterno", "Apellido materno", "Nombres", "Tipo de documento",
    "Número de documento", "Grupo de edad", "Edad", "Tipo", "Días asistidos")

/**
 * Hoja «Padrón»: las personas activas a `fechaRef`, numeradas y por apellido,
 * con los días que vinieron en el periodo. Hoja «Resumen»: cuántas por grupo
 * de edad (aparte, para que la primera hoja se pueda volver a importar).
 */
fun excelDelPadron(padron: Padron, fechaRef: String, inicio: String, fin: String, centro: String = ""): ByteArray {
    val filas = filasPadron(padron, fechaRef)
    val dias = asistenciaPorAfiliado(padron, inicio, fin).associate { it.afiliadoId to it.dias }
    val porGrupo = TRAMOS_ETARIOS.associate { it.clave to 0 }.toMutableMap()
    var social = 0
    var sinVenir = 0
    for (f in filas) {
        val g = f.grupoEtario
        if (g != null && g in porGrupo) porGrupo[g] = porGrupo.getValue(g) + 1
        if (f.tipoAfiliado == "caso_social") social += 1
        if ((dias[f.afiliadoId] ?: 0) == 0) sinVenir += 1
    }
    val subtitulo = "${if (centro.isNotEmpty()) "$centro · " else ""}Activas al ${aDdMmAa(fechaRef)} · días que vinieron ${periodoTexto(inicio, fin)}"
    return armarXlsx(listOf(
        HojaXlsx(
            nombre = "Padrón",
            anchos = listOf(5, 18, 18, 22, 10, 14, 15, 6, 13, 9),
            filas = listOf(
                listOf(negrita("Padrón de personas")), listOf(subtitulo), emptyList(),
                CABECERA_PADRON.map(::negrita)
            ) + filas.map { f ->
                listOf(f.n, f.apellidoPaterno, f.apellidoMaterno, f.nombres, f.tipoDocumento, f.numeroDocumento,
                    f.grupoEtarioEtiqueta, f.edad, f.tipoAfiliadoEtiqueta, dias[f.afiliadoId] ?: 0)
            }
        ),
        HojaXlsx(
            nombre = "Resumen",
            anchos = listOf(34, 10),
            filas = listOf(
                listOf(negrita("Padrón de personas")), listOf(subtitulo), emptyList(),
                listOf(negrita("Total de personas activas"), negrita(filas.size))
            ) + TRAMOS_ETARIOS.map { t -> listOf("${t.etiqueta} (${t.detalle})", porGrupo[t.clave]) } + listOf(
                listOf("Ayuda social", social),
                listOf("No vinieron ningún día en esas fechas", sinVenir)
            )
        )
    ))
}

/**
 * Hoja «Resumen por día» (raciones por menú, sin inscribir, recaudado) y hoja
 * «Por persona» (una fila por persona y una columna por día; X: menú normal,
 * A: ayuda social). A diferencia del PDF, la planilla sale con cualquier
 * cantidad de días: la hoja no tiene ancho de papel.
 */
fun excelDeAsistencia(padron: Padron, inicio: String, fin: String, centro: String = ""): ByteArray {
    val dias = atencionesDelPeriodo(padron, inicio, fin).sortedBy { it.fecha }
    val subtitulo = "${if (centro.isNotEmpty()) "$centro · " else ""}${periodoTexto(inicio, fin)}"

    var sNormal = 0; var sAyuda = 0; var sFuera = 0; var sTotal = 0; var sSoles = 0L
    val filasDia = dias.map { at ->
        val d = desgloseDelDia(at)
        val rec = recaudacionDelDiaCent(at)
        sNormal += d.normal; sAyuda += d.ayudaSocial; sFuera += d.sinInscribir; sTotal += d.total; sSoles += rec ?: 0
        listOf(aDdMmAa(at.fecha), DIAS[diaDeSemanaJs(at.fecha)], d.normal, d.ayudaSocial, d.sinInscribir, d.total,
            if (rec == null) "falta el precio" else soles(rec))
    }
    val resumen = mutableListOf<List<Any?>>(
        listOf(negrita("Asistencia")), listOf(subtitulo), emptyList(),
        listOf("Fecha", "Día", "Menú normal", "Ayuda social", "Sin inscribir*", "Raciones", "Recaudado (S/)").map(::negrita)
    )
    resumen.addAll(filasDia)
    if (dias.isNotEmpty()) {
        resumen.add(listOf(negrita("Total"), negrita(cuantos(dias.size, "día")), negrita(sNormal), negrita(sAyuda),
            negrita(sFuera), negrita(sTotal), soles(sSoles, true)))
        resumen.add(emptyList())
        resumen.add(listOf("* Quienes comieron sin estar en el padrón. Ya están contados en «Menú normal» y «Ayuda social»."))
    } else {
        resumen.add(listOf("No hay días con asistencia en estas fechas."))
    }

    val marcas = dias.map { at -> asistenciasDelDia(at).associate { it.afiliadoId to it.tipoMenu } }
    val vinieron = marcas.flatMap { it.keys }.toSet()
    val unicas = LinkedHashMap<String, Afiliado>()
    for (a in afiliadosActivos(padron, fin) + padron.afiliados.filter { it.id in vinieron }) unicas[a.id] = a
    val personas = unicas.values.sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
    val porPersona = listOf<List<Any?>>(
        listOf(negrita("Asistencia por persona")), listOf("$subtitulo · X: vino (menú normal) · A: vino (ayuda social)"), emptyList(),
        (listOf("N°", "Apellidos y nombres", "Documento") +
            dias.map { at -> "${aDdMmAa(at.fecha).take(5)} ${DIAS[diaDeSemanaJs(at.fecha)].take(3)}" } + "Días").map(::negrita)
    ) + personas.mapIndexed { i, a ->
        val fila = marcas.map { m -> if (a.id in m) (if (m[a.id] == "ayuda_social") "A" else "X") else "" }
        listOf<Any?>(i + 1, nombreCompleto(a), a.numeroDocumento) + fila + fila.count { it.isNotEmpty() }
    } + listOf(
        listOf<Any?>(null, negrita("Total del día"), null) + marcas.map { negrita(it.size) } + negrita(marcas.sumOf { it.size })
    )

    return armarXlsx(listOf(
        HojaXlsx(nombre = "Resumen por día", anchos = listOf(10, 11, 12, 12, 13, 10, 15), filas = resumen),
        HojaXlsx(nombre = "Por persona", anchos = listOf(5, 34, 12) + dias.map { 9 } + 6, filas = porPersona)
    ))
}

private fun cantidad(e: Egreso): Any = if (e.cantidadVarios) "varios" else e.cantidad?.takeIf { it.isFinite() } ?: ""
private fun precio(e: Egreso): Any = if (e.precioUnitarioVarios) "varios" else soles(e.precioUnitarioCent)

/**
 * El B-1 en una hoja, en el orden del formato: encabezado, ingresos, compras
 * con sus totales y firmas. Solo lo escrito: lo que falta queda vacío.
 */
fun excelDelB1(r: Rendicion): ByteArray {
    val t = totales(r)
    val egresos = r.egresos.filter { e -> e.fechaCompra.isNotEmpty() || e.descripcion.trimJs().isNotEmpty() || e.montoTotalCent != null }
    val centro = when (r.tipoCentro) { "comedor" -> "Comedor"; "olla_comun" -> "Olla común"; else -> "" }
    val p = r.periodo
    return armarXlsx(listOf(HojaXlsx(
        nombre = "Formato B-1",
        anchos = listOf(26, 34, 9, 11, 14, 16, 13, 14),
        filas = listOf(
            listOf(negrita("FORMATO B-1 (Balance de centros de atención subsidiados)")),
            listOf("Programa de Complementación Alimentaria - PCA · Municipalidad Distrital de Villa María del Triunfo"),
            emptyList(),
            listOf("Centro de atención", centro),
            listOf("Nombre del centro de atención", r.nombreCentro),
            listOf("Código de PCA N°", r.codigoPca),
            listOf("Mes de rendición", p.etiqueta),
            listOf("Periodo", if (p.inicio.isNotEmpty() || p.fin.isNotEmpty()) "${aDdMmAa(p.inicio)} al ${aDdMmAa(p.fin)}" else ""),
            listOf("Fecha de rendición", aDdMmAa(r.fechaRendicion)),
            listOf("Presidente(a)", r.nombrePresidenta),
            listOf("DNI", r.dniPresidenta),
            listOf("Celular N°", r.celular),
            emptyList(),
            listOf(negrita("INGRESOS")),
            listOf("Monto del subsidio (S/)", soles(r.montoSubsidioCent)),
            listOf("Fecha de la asignación", aDdMmAa(r.fechaAsignacion)),
            emptyList(),
            listOf(negrita("EGRESOS")),
            listOf("Fecha de la compra", "Descripción de la compra", "Cant.", "Unidad", "RUC del proveedor", "N° de boleta",
                "Precio unitario", "Monto total (S/)").map(::negrita)
        ) + egresos.map { e ->
            listOf(aDdMmAa(e.fechaCompra), e.descripcion, cantidad(e), e.unidadMedida,
                e.rucProveedor, listOf(e.boletaSerie, e.boletaCorrelativo).filter { it.trimJs().isNotEmpty() }.joinToString("-"),
                precio(e), soles(e.montoTotalCent))
        } + listOf(
            emptyList(),
            listOf("Gastos con el subsidio económico", null, null, null, null, null, null, soles(t.conSubsidio, true)),
            // «Registrar de corresponder»: si es cero, va vacío, como en la hoja.
            listOf("Aporte del centro de atención", null, null, null, null, null, null,
                if (t.aporte != null && t.aporte != 0L) soles(t.aporte, true) else ""),
            listOf(negrita("Total de rendición de gastos"), null, null, null, null, null, null, soles(t.total, true)),
            emptyList(),
            listOf(negrita("Presidente(a)"), r.nombrePresidentaFirma, null, null, negrita("DNI"), r.dniPresidentaFirma),
            listOf(negrita("Tesorero(a)"), r.nombreTesorera, null, null, negrita("DNI"), r.dniTesorera),
            emptyList(),
            listOf("Copia de referencia generada con OllaConecta. No reemplaza al formato oficial firmado.")
        )
    )))
}
