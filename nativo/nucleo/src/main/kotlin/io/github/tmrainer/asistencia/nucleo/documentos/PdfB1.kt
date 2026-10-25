// El B-1 como PDF de referencia, para cuando no se puede imprimir sobre la hoja
// oficial: el formato en gris y los datos en negro, en las posiciones de
// PlantillaB1.kt. Con un LienzoTrazos, la pantalla recibe el mismo dibujo como
// datos. El PDF sale igual byte a byte que el de la web.
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
 * Rótulos, líneas y recuadros del formato, en gris, sobre cualquier lienzo: el
 * del PDF y el de la vista previa en pantalla (LienzoTrazos).
 */
fun dibujarFormato(l: Lienzo) {
    val e = P.egresos
    val c = e.columnas
    val cols = c.values.toList()
    val finFilas = e.primeraFilaYMm + e.filas * e.altoFilaMm
    val izq = cols[0].xMm
    val der = cols[cols.size - 1].xMm + cols[cols.size - 1].anchoMm

    l.gris(0.45)
    // Encabezado
    l.texto("PROGRAMA DE COMPLEMENTACIÓN - PCA", 105 - anchoMm("PROGRAMA DE COMPLEMENTACIÓN - PCA", 8.0, true) / 2, 30.0, 8.0, negrita = true)
    val t2 = "FORMATO B-1 (BALANCE DE CENTROS DE ATENCIÓN SUBSIDIADOS)"
    l.texto(t2, 105 - anchoMm(t2, 8.0, true) / 2, 34.0, 8.0, negrita = true)
    val t3 = "MUNICIPALIDAD DISTRITAL DE VILLA MARÍA DEL TRIUNFO"
    l.texto(t3, 105 - anchoMm(t3, 8.0, true) / 2, 39.5, 8.0, negrita = true)

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
        Triple("NOMBRE DEL CENTRO DE ATENCIÓN:", "nombreCentro", 16.0), Triple("CÓDIGO DE PCA N°:", "codigoPca", 139.0),
        Triple("MES DE RENDICIÓN:", "periodo", 16.0), Triple("FECHA DE RENDICIÓN:", "fechaRendicion", 139.0),
        Triple("NOMBRE DE LA PRESIDENTE(A):", "nombrePresidenta", 16.0), Triple("DNI:", "dniPresidenta", 16.0),
        Triple("CELULAR N°:", "celular", 139.0)
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
    l.texto("MONTO DEL SUBSIDIO:", izq + 1, ms.yMm + ms.altoMm, ROTULO)
    l.texto("FECHA DE LA ASIGNACIÓN:", izq + 1, fa.yMm + fa.altoMm, ROTULO)
    l.texto("S/", iDiv + 1, ms.yMm + ms.altoMm, ROTULO)

    // Egresos
    val eTop = e.primeraFilaYMm - 16.9
    val cabecera = e.primeraFilaYMm - 12.4
    val tot = listOf(campo("totalConSubsidio"), campo("totalAporte"), campo("totalRendicion"))
    val eBot = tot[2].yMm + tot[2].altoMm
    l.caja(izq, eTop, der - izq, eBot - eTop)
    l.relleno(izq, eTop, der - izq, 4.4)
    l.gris(1.0)
    l.texto("EGRESOS", 105 - anchoMm("EGRESOS", 7.5, true) / 2, eTop + 3.3, 7.5, negrita = true)
    l.gris(0.45)
    l.linea(izq, cabecera, der, cabecera)
    for (i in 0..e.filas) l.linea(izq, e.primeraFilaYMm + i * e.altoFilaMm, der, e.primeraFilaYMm + i * e.altoFilaMm)
    for (t in tot) l.linea(izq, t.yMm + t.altoMm, der, t.yMm + t.altoMm)
    cols.drop(1).forEach { col -> l.linea(col.xMm, cabecera, col.xMm, finFilas) }
    l.linea(c.getValue("precioUnitario").xMm, finFilas, c.getValue("precioUnitario").xMm, eBot)
    l.linea(c.getValue("montoTotal").xMm, finFilas, c.getValue("montoTotal").xMm, eBot)
    val titulos = linkedMapOf(
        "fechaCompra" to listOf("FECHA DE LA", "COMPRA"), "descripcion" to listOf("DESCRIPCIÓN DE LA COMPRA"),
        "cantidad" to listOf("CANT."), "unidadMedida" to listOf("UNIDAD DE", "MEDIDA (Kg,", "L, otros)"),
        "rucProveedor" to listOf("RUC DEL PROVEEDOR"), "boleta" to listOf("N° DE LA BOLETA", "(SERIE Y", "CORRELATIVO)"),
        "precioUnitario" to listOf("PRECIO", "UNITARIO"), "montoTotal" to listOf("MONTO TOTAL (S/)")
    )
    for ((k, lineas) in titulos) {
        val col = c.getValue(k)
        val alto = lineas.size * 2.4
        lineas.forEachIndexed { i, t ->
            val pt = min(5.5, 5.5 * (col.anchoMm - 1) / max(anchoMm(t, 5.5), 0.1))
            l.texto(t, col.xMm + (col.anchoMm - anchoMm(t, pt)) / 2, cabecera + (12.4 - alto) / 2 + 2 + i * 2.4, pt)
        }
    }
    val rotTot = listOf("GASTOS CON EL SUBSIDIO ECONÓMICO:", "APORTE DEL CENTRO DE ATENCIÓN (registrar de corresponder):",
        "TOTAL DE RENDICIÓN DE GASTOS:")
    tot.forEachIndexed { i, t -> l.texto(rotTot[i], izq + 1, t.yMm + t.altoMm - 0.9, ROTULO) }

    // Nota
    val nota = listOf(
        "NOTA:",
        "1) Los documentos originales quedarán en custodia en la Municipalidad Distrital de Villa María del Triunfo para ser verificados oportunamente.",
        "2) Los documentos no deben presentar borrones y/o enmendaduras, sean Acta de Asamblea, Boletas de Venta, Formato de balance; de lo",
        "contrario no serán recepcionados por el personal de la Municipalidad Distrital de Villa María del Triunfo."
    )
    nota.forEachIndexed { i, t -> l.texto(t, 16.0, (207 + i * 4).toDouble(), 6.0, negrita = i == 0) }

    // Firmas
    val pf = campo("nombrePresidentaFirma")
    val tf = campo("nombreTesorera")
    val pd = campo("dniPresidentaFirma")
    val td = campo("dniTesorera")
    l.grosor(0.35)
    l.linea(16.0, pf.yMm - 6, 73.0, pf.yMm - 6)
    l.linea(139.0, tf.yMm - 5.6, 200.0, tf.yMm - 5.6)
    l.texto("PRESIDENTE(A)", 44.5 - anchoMm("PRESIDENTE(A)", 6.5, true) / 2, pf.yMm - 2.6, 6.5, negrita = true)
    l.texto("TESORERO(A)", 169.5 - anchoMm("TESORERO(A)", 6.5, true) / 2, tf.yMm - 2.2, 6.5, negrita = true)
    l.texto("NOMBRE Y APELLIDO:", 16.0, pf.yMm + pf.altoMm, ROTULO)
    l.texto("DNI N°:", 16.0, pd.yMm + pd.altoMm, ROTULO)
    l.texto("NOMBRE Y APELLIDO:", 139.0, tf.yMm + tf.altoMm, ROTULO)
    l.texto("DNI N°:", 139.0, td.yMm + td.altoMm, ROTULO)

    // Recibido por la Municipalidad (lo llena el personal municipal)
    l.texto("Recibido por la Municipalidad Distrital de Villa María del Triunfo", 16.0, 265.0, 6.5, negrita = true)
    l.caja(izq, 268.1, der - izq, 11.0)
    l.linea(izq, 273.5, der, 273.5)
    l.linea(140.0, 268.1, 140.0, 273.5)
    l.texto("Nombres y Apellidos:", izq + 1, 272.2, ROTULO)
    l.texto("DNI:", 141.0, 272.2, ROTULO)
    l.texto("Observaciones:", izq + 1, 277.8, ROTULO, negrita = true)
    val dj = listOf(
        "El presente documento tiene carácter de Declaración Jurada, de acuerdo al Art. 06 de la Ley 25035; el fraude o falsedad en la documentación",
        "o declaración determina la falta de cumplimiento de requisito y el inicio de las acciones penales correspondientes."
    )
    dj.forEachIndexed { i, t -> l.texto(t, 16.0, 284 + i * 3.6, 5.8) }
}

/**
 * Una hoja del PDF entera: el formato, el aviso de copia de referencia y los
 * datos en negro, cada uno dentro de su caja.
 * @param generado «14-10-26 18:40»
 * @param numero desde 1; con `total` > 1 se escribe «Hoja numero de total»
 */
fun dibujarHoja(l: Lienzo, h: HojaB1, generado: String = selloAhora(), numero: Int = 1, total: Int = 1) {
    dibujarFormato(l)
    // Aviso arriba: que nadie la confunda con el formato oficial firmado.
    l.gris(0.25)
    l.texto("COPIA DE REFERENCIA — generada con OllaConecta el $generado. No reemplaza al formato oficial firmado.", 14.0, 10.0, 7.0, negrita = true)
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
 */
fun pdfDelB1(hojas: List<HojaB1>, generado: String = selloAhora(), titulo: String = "Formato B-1"): ByteArray =
    armarPdf(hojas.mapIndexed { i, h ->
        val l = lienzo()
        dibujarHoja(l, h, generado, i + 1, hojas.size)
        PaginaPdf(l.ops.joinToString("\n"))
    }, titulo)
