// El B-1 como PDF de referencia, para cuando no se puede imprimir sobre la hoja
// oficial: el formato (el de Pachacámac 2026) en gris y los datos en negro, en
// las posiciones de PlantillaB1.kt. Con un LienzoTrazos, la pantalla recibe el
// mismo dibujo como datos. Desde el 05-11-2026 ya no es el PDF de la web (VMT).
package io.github.tmrainer.asistencia.nucleo.documentos

import kotlin.math.max
import kotlin.math.min

/** Parte un texto en dos renglones que entren en `libre` mm a `pt`, o null. */
private fun enDos(texto: String, libre: Double, pt: Double): List<String>? {
    val palabras = texto.split(Regex("\\s+"))
    for (i in palabras.size - 1 downTo 1) {
        val a = palabras.subList(0, i).joinToString(" ")
        val b = palabras.subList(i, palabras.size).joinToString(" ")
        if (anchoMm(a, pt) <= libre && anchoMm(b, pt) <= libre) return listOf(a, b)
    }
    return null
}

/**
 * Un dato dentro de su caja. Lo que no entra: primero hasta 2 pt más chico en
 * un renglón, después en dos renglones, después más chico hasta 5 pt y, si
 * igual no entra, cortado con «…». Se prioriza que se lea.
 */
private fun datoEnCaja(l: Lienzo, texto: String?, caja: CampoB1, ptInicial: Double) {
    if (texto.isNullOrEmpty()) return
    val xMm = caja.xMm
    val yMm = caja.yMm
    val w = caja.anchoMm
    val h = caja.altoMm
    val align = caja.align
    var ptBase = ptInicial
    val margen = 0.8
    val libre = w - 2 * margen
    // Primero, un poco más chico en un solo renglón (hasta 2 pt menos).
    var pt1 = ptBase
    while (pt1 > ptBase - 2 && anchoMm(texto, pt1) > libre) pt1 -= 0.25
    if (anchoMm(texto, pt1) <= libre) ptBase = pt1
    if (anchoMm(texto, ptBase) > libre) {
        val pt2 = min(ptBase - 1, (h - 0.5) / 2 * PT / 1.05)
        val dos = if (pt2 >= 4.4) enDos(texto, libre, pt2) else null
        if (dos != null) {
            dos.forEachIndexed { i, t ->
                val a = anchoMm(t, pt2)
                val xm = if (align == "right") xMm + w - margen - a else if (align == "center") xMm + (w - a) / 2 else xMm + margen
                l.texto(t, xm, yMm + (i + 1) * (h - 0.4) / 2, pt2)
            }
            return
        }
    }
    var pt = ptBase
    while (pt > 5 && anchoMm(texto, pt) > libre) pt -= 0.25
    var t: String = texto
    while (t.length > 1 && anchoMm("$t…", pt) > libre && anchoMm(t, pt) > libre) t = t.dropLast(1)
    if (t != texto) t = t.trimEndJs() + "…"
    val ancho = anchoMm(t, pt)
    val xm = if (align == "right") xMm + w - margen - ancho else if (align == "center") xMm + (w - ancho) / 2 else xMm + margen
    l.texto(t, xm, yMm + h - 0.7, pt)
}

private val P = PLANTILLA_B1
private const val ROTULO = 6.5 // pt

private fun campo(clave: String) = P.campos.getValue(clave)

/**
 * Rótulos, líneas y recuadros del formato (el de Pachacámac 2026), en gris,
 * sobre cualquier lienzo: el del PDF y el de la vista previa (LienzoTrazos).
 * [distrito]: el de la Municipalidad, para el título y la nota.
 */
fun dibujarFormato(l: Lienzo, distrito: String = "") {
    val e = P.egresos
    val c = e.columnas
    val cols = c.values.toList()
    val finFilas = e.primeraFilaYMm + e.filas * e.altoFilaMm
    val izq = cols[0].xMm
    val der = cols[cols.size - 1].xMm + cols[cols.size - 1].anchoMm
    val muni = if (distrito.isBlank()) "MUNICIPALIDAD DISTRITAL" else "MUNICIPALIDAD DISTRITAL DE ${distrito.trim().uppercase()}"
    val laMuni = if (distrito.isBlank()) "la Municipalidad" else "la Municipalidad Distrital de ${distrito.trim()}"

    l.gris(0.45)
    // Encabezado
    val t1 = "FORMATO DE RENDICIÓN DE GASTOS DEL SUBSIDIO ECONÓMICO"
    l.texto(t1, 105 - anchoMm(t1, 8.0, true) / 2, 28.5, 8.0, negrita = true)
    val t2 = "PROGRAMA DE COMPLEMENTACIÓN ALIMENTARIA – PCA"
    l.texto(t2, 105 - anchoMm(t2, 8.0, true) / 2, 33.0, 8.0, negrita = true)
    l.texto(muni, 105 - anchoMm(muni, 8.0, true) / 2, 38.0, 8.0, negrita = true)

    l.texto("CENTRO DE ATENCIÓN:", 16.0, 49.5, ROTULO)
    l.texto("COMEDOR", 113.0, 49.5, ROTULO)
    l.texto("OLLA COMÚN", 159.0, 49.5, ROTULO)
    l.grosor(0.35)
    val mc = campo("marcaComedor")
    val mo = campo("marcaOllaComun")
    l.caja(mc.xMm, mc.yMm, mc.anchoMm, mc.altoMm)
    l.caja(mo.xMm, mo.yMm, mo.anchoMm, mo.altoMm)

    // Como en el papel: el rótulo empieza en el margen de su columna y la línea
    // punteada sigue hasta el final del espacio del dato.
    val renglones = listOf(
        Triple("NOMBRE DEL CENTRO DE ATENCIÓN:", "nombreCentro", 16.0),
        Triple("CÓDIGO DEL SISTEMA INFORMÁTICO DEL PCA N°:", "codigoPca", 16.0), Triple("DISTRITO:", "distrito", 139.0),
        Triple("MES DE RENDICIÓN:", "periodo", 16.0), Triple("FECHA DE RENDICIÓN:", "fechaRendicion", 139.0),
        Triple("NOMBRE DE LA PRESIDENTA/REPRESENTANTE QUE RECIBE EL SUBSIDIO:", "nombrePresidenta", 16.0),
        Triple("DNI N°:", "dniPresidenta", 16.0), Triple("CELULAR N°:", "celular", 139.0)
    )
    l.grosor(0.2)
    for ((rotulo, clave, xr) in renglones) {
        val p = campo(clave)
        val base = p.yMm + p.altoMm
        l.texto(rotulo, xr, base, ROTULO)
        l.punteada(true)
        l.linea(xr + anchoMm(rotulo, ROTULO) + 0.8, base + 0.4, p.xMm + p.anchoMm, base + 0.4)
        l.punteada(false)
    }

    // Ingresos
    val ms = campo("montoSubsidio")
    val fa = campo("fechaAsignacion")
    val iTop = ms.yMm - 4.8
    val iBot = fa.yMm + fa.altoMm + 2.6
    val iMid = (ms.yMm + ms.altoMm + fa.yMm) / 2
    val iDiv = fa.xMm - 1.3
    l.grosor(0.35)
    l.caja(izq, iTop, der - izq, iBot - iTop)
    l.relleno(izq, iTop, der - izq, 4.4)
    l.gris(1.0)
    l.texto("INGRESOS", 105 - anchoMm("INGRESOS", 7.5, true) / 2, iTop + 3.3, 7.5, negrita = true)
    l.gris(0.45)
    l.linea(izq, iTop + 4.4, der, iTop + 4.4)
    l.linea(izq, iMid, der, iMid)
    l.linea(iDiv, iTop + 4.4, iDiv, iBot)
    l.texto("MONTO DEL SUBSIDIO ECONÓMICO ASIGNADO:", izq + 1, ms.yMm + ms.altoMm, ROTULO)
    l.texto("FECHA DE ASIGNACIÓN:", izq + 1, fa.yMm + fa.altoMm, ROTULO)
    l.texto("S/", iDiv + 1, ms.yMm + ms.altoMm, ROTULO)

    // Egresos
    val eTop = e.primeraFilaYMm - 16.9
    val cabecera = e.primeraFilaYMm - 12.4
    val tot = listOf(campo("totalCompra"), campo("totalDeclaracionJurada"), campo("totalAporte"), campo("totalRendicion"))
    val eBot = tot.last().yMm + tot.last().altoMm
    l.caja(izq, eTop, der - izq, eBot - eTop)
    l.relleno(izq, eTop, der - izq, 4.4)
    l.gris(1.0)
    l.texto("EGRESOS", 105 - anchoMm("EGRESOS", 7.5, true) / 2, eTop + 3.3, 7.5, negrita = true)
    l.gris(0.45)
    l.linea(izq, cabecera, der, cabecera)
    for (i in 0..e.filas) l.linea(izq, e.primeraFilaYMm + i * e.altoFilaMm, der, e.primeraFilaYMm + i * e.altoFilaMm)
    for (t in tot) l.linea(izq, t.yMm + t.altoMm, der, t.yMm + t.altoMm)
    cols.drop(1).forEach { col -> l.linea(col.xMm, cabecera, col.xMm, finFilas) }
    l.linea(c.getValue("montoTotal").xMm, finFilas, c.getValue("montoTotal").xMm, eBot)
    val titulos = linkedMapOf(
        "fechaCompra" to listOf("FECHA"), "descripcion" to listOf("DESCRIPCIÓN DE LA", "COMPRA (ALIMENTOS)"),
        "cantidad" to listOf("CANTIDAD"), "unidadMedida" to listOf("UNIDAD DE", "MEDIDA (Kg,", "L, otros)"),
        "rucProveedor" to listOf("RUC DEL", "PROVEEDOR"), "boleta" to listOf("N° DE", "BOLETA/", "FACTURA"),
        "precioUnitario" to listOf("PRECIO", "UNITARIO"), "montoTotal" to listOf("MONTO", "(S/)")
    )
    for ((k, lineas) in titulos) {
        val col = c.getValue(k)
        val alto = lineas.size * 2.4
        lineas.forEachIndexed { i, t ->
            val pt = min(5.5, 5.5 * (col.anchoMm - 1) / max(anchoMm(t, 5.5), 0.1))
            l.texto(t, col.xMm + (col.anchoMm - anchoMm(t, pt)) / 2, cabecera + (12.4 - alto) / 2 + 2 + i * 2.4, pt)
        }
    }
    val rotTot = listOf("MONTO TOTAL DE COMPRA:", "MONTO DE DECLARACIÓN JURADA (registrar, de corresponder):",
        "APORTE DEL CENTRO DE ATENCIÓN (registrar, de corresponder):", "TOTAL DE RENDICIÓN DE GASTOS:")
    tot.forEachIndexed { i, t -> l.texto(rotTot[i], izq + 1, t.yMm + t.altoMm - 0.9, ROTULO, negrita = i == 0 || i == 3) }
    l.texto("S/", c.getValue("montoTotal").xMm + 1, tot[0].yMm + tot[0].altoMm - 0.9, ROTULO)
    l.texto("S/", c.getValue("montoTotal").xMm + 1, tot[1].yMm + tot[1].altoMm - 0.9, ROTULO)
    l.texto("S/", c.getValue("montoTotal").xMm + 1, tot[2].yMm + tot[2].altoMm - 0.9, ROTULO)
    l.texto("S/", c.getValue("montoTotal").xMm + 1, tot[3].yMm + tot[3].altoMm - 0.9, ROTULO)

    // Nota
    val nota = listOf(
        "Nota:",
        "1) Los documentos no deben presentar borrones y/o enmendaduras, sean Acta de Asamblea, Boletas de Venta, Formato de",
        "balance; de lo contrario no serán recepcionados por $laMuni.",
        "2) Los documentos originales quedan en custodia en $laMuni para ser verificados oportunamente."
    )
    nota.forEachIndexed { i, t -> l.texto(t, 16.0, (218 + i * 4).toDouble(), 6.0, negrita = i == 0) }

    // Firmas
    val pf = campo("nombrePresidentaFirma")
    val tf = campo("nombreTesorera")
    val pd = campo("dniPresidentaFirma")
    val td = campo("dniTesorera")
    l.grosor(0.35)
    l.linea(16.0, pf.yMm - 6, 93.0, pf.yMm - 6)
    l.linea(139.0, tf.yMm - 6, 205.0, tf.yMm - 6)
    l.texto("PRESIDENTA/REPRESENTANTE", 54.5 - anchoMm("PRESIDENTA/REPRESENTANTE", 6.5, true) / 2, pf.yMm - 2.6, 6.5, negrita = true)
    l.texto("TESORERO(A)", 172.0 - anchoMm("TESORERO(A)", 6.5, true) / 2, tf.yMm - 2.6, 6.5, negrita = true)
    l.texto("Nombres y Apellidos:", 16.0, pf.yMm + pf.altoMm, ROTULO)
    l.texto("DNI N°:", 16.0, pd.yMm + pd.altoMm, ROTULO)
    l.texto("Nombres y Apellidos:", 139.0, tf.yMm + tf.altoMm, ROTULO)
    l.texto("DNI N°:", 139.0, td.yMm + td.altoMm, ROTULO)
}

/**
 * Una hoja del PDF entera: el formato, el aviso de copia de referencia y los
 * datos en negro, cada uno dentro de su caja.
 * @param generado «14-10-26 18:40»
 * @param numero desde 1; con `total` > 1 se escribe «Hoja numero de total»
 * @param avance «15-11-26»: lo anotado hasta ese día, pedido antes de cerrar el periodo
 */
fun dibujarHoja(l: Lienzo, h: HojaB1, generado: String = selloAhora(), numero: Int = 1, total: Int = 1, avance: String? = null) {
    dibujarFormato(l, h.campos["distrito"].orEmpty())
    // Aviso arriba: que nadie la confunda con el formato oficial firmado (ni un avance con la rendición final).
    l.gris(0.25)
    l.texto(
        if (avance == null) "COPIA DE REFERENCIA — generada con OllaConecta el $generado. No reemplaza al formato oficial firmado."
        else "AVANCE AL $avance — lo anotado hasta ese día; no es la rendición final del periodo. Generado con OllaConecta el $generado.",
        14.0, 10.0, 7.0, negrita = true
    )
    if (total > 1) l.texto("Hoja $numero de $total", 180.0, 10.0, 7.0, negrita = true)

    l.gris(0.0)
    for ((clave, pos) in P.campos) {
        val t = h.campos[clave]
        if (t.isNullOrEmpty()) continue
        val pt = if (clave.startsWith("marca")) 12.0 else if (Regex("Firma$|Tesorera$").containsMatchIn(clave)) P.letraFirmasPt else P.letraPt
        datoEnCaja(l, t, pos, pt)
    }
    val e = P.egresos
    h.filas.forEachIndexed { i, fila ->
        for ((col, pos) in e.columnas) {
            datoEnCaja(l, fila[col], CampoB1(pos.xMm, e.primeraFilaYMm + i * e.altoFilaMm, pos.anchoMm, e.altoFilaMm, pos.align), P.letraTablaPt)
        }
    }
}

/**
 * El archivo PDF: una página por hoja de hojasDelB1().
 * @param generado «14-10-26 18:40»; por defecto, ahora
 * @param avance ver [dibujarHoja]
 */
fun pdfDelB1(hojas: List<HojaB1>, generado: String = selloAhora(), titulo: String = "Formato B-1", avance: String? = null): ByteArray =
    armarPdf(hojas.mapIndexed { i, h ->
        val l = lienzo()
        dibujarHoja(l, h, generado, i + 1, hojas.size, avance)
        PaginaPdf(l.ops.joinToString("\n"))
    }, titulo)
