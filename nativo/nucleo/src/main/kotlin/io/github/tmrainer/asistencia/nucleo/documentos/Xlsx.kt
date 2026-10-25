// Leer y escribir archivos de Excel (.xlsx) sin librerías: la primera hoja,
// para importar un padrón (leerXlsx), y los reportes (armarXlsx).
//
// Un .xlsx es un ZIP con XML adentro: el ZIP se recorre a mano y del JDK solo
// se usan Inflater, CRC32 y el lector de XML. Nada se manda a ningún lado.
// No lee el Excel antiguo (.xls), que es otro formato. Un XML mal formado da
// error («el archivo está dañado»).
package io.github.tmrainer.asistencia.nucleo.documentos

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor

/** Un .xlsx que no se puede leer, con el mensaje para la usuaria. */
class ErrorXlsx(mensaje: String) : Exception(mensaje)

private class Entrada(val metodo: Int, val inicio: Int, val largo: Int)

private fun ByteBuffer.u32(i: Int): Long = getInt(i).toLong() and 0xffffffffL
private fun ByteBuffer.u16(i: Int): Int = getShort(i).toInt() and 0xffff

/** TextDecoder de la web: UTF-8, sin la marca de orden de bytes del principio. */
private fun utf8(bytes: ByteArray): String = bytes.decodeToString().removePrefix("﻿")

/** Las entradas del ZIP: nombre -> { metodo, inicio, largo }. */
private fun indiceZip(bytes: ByteArray): Map<String, Entrada> {
    val v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var fin = -1
    var i = bytes.size - 22
    while (i >= maxOf(0, bytes.size - 65557)) {
        if (v.u32(i) == 0x06054b50L) { fin = i; break }
        i -= 1
    }
    if (fin < 0) throw ErrorXlsx("no es un archivo .xlsx")
    try {
        val total = v.u16(fin + 10)
        var p = v.u32(fin + 16).toInt()
        val entradas = LinkedHashMap<String, Entrada>()
        for (n in 0 until total) {
            if (v.u32(p) != 0x02014b50L) throw ErrorXlsx("el archivo está dañado")
            val metodo = v.u16(p + 10)
            val largo = v.u32(p + 20).toInt()
            val largoNombre = v.u16(p + 28)
            val largoExtra = v.u16(p + 30)
            val largoComentario = v.u16(p + 32)
            val local = v.u32(p + 42).toInt()
            val nombre = utf8(bytes.copyOfRange(p + 46, p + 46 + largoNombre))
            // Los datos empiezan después de la cabecera local, que tiene su propio largo de extra.
            val inicio = local + 30 + v.u16(local + 26) + v.u16(local + 28)
            entradas[nombre] = Entrada(metodo, inicio, largo)
            p += 46 + largoNombre + largoExtra + largoComentario
        }
        return entradas
    } catch (e: IndexOutOfBoundsException) {
        throw ErrorXlsx("el archivo está dañado")
    }
}

private fun leerEntrada(bytes: ByteArray, entradas: Map<String, Entrada>, nombre: String): String? {
    val e = entradas[nombre] ?: return null
    // Como subarray(): lo que se pasa del final se recorta.
    val desde = e.inicio.coerceIn(0, bytes.size)
    val crudo = bytes.copyOfRange(desde, (e.inicio.toLong() + e.largo).coerceIn(desde.toLong(), bytes.size.toLong()).toInt())
    if (e.metodo == 0) return utf8(crudo)
    if (e.metodo != 8) throw ErrorXlsx("compresión no soportada")
    val inflador = Inflater(true)
    try {
        inflador.setInput(crudo)
        val salida = ByteArrayOutputStream()
        val bloque = ByteArray(8192)
        while (!inflador.finished()) {
            val n = inflador.inflate(bloque)
            if (n == 0 && (inflador.needsInput() || inflador.needsDictionary())) throw ErrorXlsx("el archivo está dañado")
            salida.write(bloque, 0, n)
        }
        return utf8(salida.toByteArray())
    } catch (e: DataFormatException) {
        throw ErrorXlsx("el archivo está dañado")
    } finally {
        inflador.end()
    }
}

private fun xml(texto: String): Document {
    val fabrica = DocumentBuilderFactory.newInstance()
    fabrica.isNamespaceAware = true
    // Como el navegador: nada de entidades ni DTD externos.
    runCatching { fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    runCatching { fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { fabrica.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    return try {
        val lector = fabrica.newDocumentBuilder()
        lector.setErrorHandler(object : org.xml.sax.ErrorHandler {   // sin mensajes en la consola
            override fun warning(e: org.xml.sax.SAXParseException) {}
            override fun error(e: org.xml.sax.SAXParseException) {}
            override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
        })
        lector.parse(org.xml.sax.InputSource(java.io.StringReader(texto)))
    } catch (e: org.xml.sax.SAXException) {
        throw ErrorXlsx("el archivo está dañado")
    }
}

// Los XML de Excel usan espacios de nombres: se busca por nombre local.
private fun hijos(nodo: Node, nombre: String): List<Element> {
    val lista = when (nodo) {
        is Document -> nodo.getElementsByTagNameNS("*", nombre)
        is Element -> nodo.getElementsByTagNameNS("*", nombre)
        else -> return emptyList()
    }
    return List(lista.length) { lista.item(it) as Element }
}

/**
 * `Number(texto)` de JavaScript: espacios alrededor se ignoran, vacío es 0,
 * lo que no es número es NaN.
 */
private val DECIMAL = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$")
private val NUMERO_DE_FILA = Regex("\\d+$")
private val SALTOS = Regex("[\\t\\r\\n]+")
private val CONTROL = Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f]")

private fun numeroDeTexto(texto: String): Double {
    val t = texto.trimJs()
    if (t.isEmpty()) return 0.0
    if (DECIMAL.matches(t)) return t.toDouble()
    when (t) {
        "Infinity", "+Infinity" -> return Double.POSITIVE_INFINITY
        "-Infinity" -> return Double.NEGATIVE_INFINITY
    }
    val base = when (t.take(2).lowercase()) { "0x" -> 16; "0o" -> 8; "0b" -> 2; else -> 0 }
    if (base != 0) return t.drop(2).toBigIntegerOrNull(base)?.toDouble() ?: Double.NaN
    return Double.NaN
}

private fun esEntero(d: Double) = d.isFinite() && d == floor(d)

/** Fechas: los formatos de número de Excel que muestran días, meses o años. */
private fun estilosDeFecha(texto: String?): Set<Int> {
    val fechas = mutableSetOf<Int>()
    if (texto.isNullOrEmpty()) return fechas
    val doc = xml(texto)
    val propios = hijos(doc, "numFmt").associate { it.getAttribute("numFmtId") to it.getAttribute("formatCode") }
    val xfs = hijos(doc, "cellXfs").firstOrNull() ?: return fechas
    hijos(xfs, "xf").forEachIndexed { i, xf ->
        val id = numeroDeTexto(xf.getAttribute("numFmtId"))
        val codigo = propios[numeroJs(id)]
        val esFecha = (id >= 14 && id <= 22) || (id >= 45 && id <= 47) ||
            (codigo != null && Regex("[dmy]", RegexOption.IGNORE_CASE)
                .containsMatchIn(codigo.replace(Regex("\"[^\"]*\"|\\[[^\\]]*]"), "")))
        if (esFecha) fechas.add(i)
    }
    return fechas
}

/** Número de serie de Excel -> 'AAAA-MM-DD'. */
private fun serieAFecha(serie: Double, desde1904: Boolean): String {
    val dias = floor(serie).toLong() + (if (desde1904) 1462 else 0)
    return LocalDate.of(1899, 12, 30).plusDays(dias).toString()
}

/** «B12» -> 1 (columnas desde 0). */
private fun columna(ref: String): Int {
    var n = 0
    for (c in ref.replace(NUMERO_DE_FILA, "")) n = n * 26 + (c.code - 64)
    return n - 1
}

private fun celdaTsv(valor: String): String {
    val t = valor.replace(SALTOS, " ").trimJs()
    return if (t.contains('"')) "\"${t.replace("\"", "\"\"")}\"" else t
}

/** La primera hoja de un .xlsx como texto separado por tabuladores. Lanza ErrorXlsx si no se puede leer. */
fun leerXlsx(bytes: ByteArray): String {
    val entradas = indiceZip(bytes)

    val libro = xml(leerEntrada(bytes, entradas, "xl/workbook.xml")?.takeIf { it.isNotEmpty() } ?: "<x/>")
    val primera = hijos(libro, "sheet").firstOrNull() ?: throw ErrorXlsx("el libro no tiene hojas")
    val desde1904 = hijos(libro, "workbookPr").firstOrNull()?.getAttribute("date1904") ?: ""
    val rid = primera.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        .ifEmpty { primera.getAttribute("r:id") }
    var ruta = "xl/worksheets/sheet1.xml"
    val rels = leerEntrada(bytes, entradas, "xl/_rels/workbook.xml.rels")
    if (!rels.isNullOrEmpty() && rid.isNotEmpty()) {
        val rel = hijos(xml(rels), "Relationship").firstOrNull { it.getAttribute("Id") == rid }
        if (rel != null) {
            val destino = rel.getAttribute("Target")
            ruta = if (destino.startsWith("/")) destino.substring(1) else "xl/" + destino.replace(Regex("^\\./"), "")
        }
    }

    val compartidas = mutableListOf<String>()
    val textos = leerEntrada(bytes, entradas, "xl/sharedStrings.xml")
    if (!textos.isNullOrEmpty()) {
        for (si in hijos(xml(textos), "si")) {
            compartidas.add(hijos(si, "t").joinToString("") { it.textContent })
        }
    }
    val fechas = estilosDeFecha(leerEntrada(bytes, entradas, "xl/styles.xml"))

    val hoja = leerEntrada(bytes, entradas, ruta)
    if (hoja.isNullOrEmpty()) throw ErrorXlsx("no se encontró la primera hoja")
    // Las filas vacías se dejan como líneas vacías: así el número de fila que
    // muestra la revisión es el mismo que se ve en Excel.
    val filas = mutableListOf<String>()
    for (fila in hijos(xml(hoja), "row")) {
        val r = numeroDeTexto(fila.getAttribute("r"))
        while (esEntero(r) && filas.size < r - 1) filas.add("")
        val celdas = mutableListOf<String>()
        for (c in hijos(fila, "c")) {
            val tipo = c.getAttribute("t")
            val v = hijos(c, "v").firstOrNull()
            var valor = ""
            if (tipo == "s") {
                val indice = if (v != null) numeroDeTexto(v.textContent) else Double.NaN
                valor = if (esEntero(indice) && indice >= 0 && indice < compartidas.size) compartidas[indice.toInt()] else ""
            } else if (tipo == "inlineStr") {
                valor = hijos(c, "t").joinToString("") { it.textContent }
            } else if (v != null) {
                valor = v.textContent
                val estilo = numeroDeTexto(c.getAttribute("s").ifEmpty { "0" })
                if (tipo == "d") {
                    valor = valor.take(10)                      // fecha ISO guardada como tal
                } else if ((tipo.isEmpty() || tipo == "n") && esEntero(estilo) && estilo.toInt() in fechas &&
                    numeroDeTexto(valor).isFinite()) {
                    valor = serieAFecha(numeroDeTexto(valor), desde1904 == "1" || desde1904 == "true")
                }
            }
            val i = if (c.getAttribute("r").isNotEmpty()) columna(c.getAttribute("r")) else celdas.size
            while (celdas.size < i) celdas.add("")
            if (i < celdas.size) celdas[i] = celdaTsv(valor) else celdas.add(celdaTsv(valor))
        }
        filas.add(celdas.joinToString("\t"))
    }
    return filas.joinToString("\n").replace(Regex("\n+$"), "")
}

/** ¿Empieza como un ZIP (y por lo tanto, quizás un .xlsx)? */
fun pareceXlsx(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

// Escribir: lo mínimo que Excel, Google Sheets y WPS (el del celular) abren sin quejarse:
// texto en la propia celda (sin tabla de textos compartidos), negrita y
// soles con dos decimales. El ZIP va sin comprimir y con fecha fija
// (01-01-1980): un reporte pesa pocos KB y el mismo dato da los mismos bytes.

private fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

/** ZIP sin compresión: [(nombre, bytes)] -> bytes. */
private fun armarZip(archivos: List<Pair<String, ByteArray>>): ByteArray {
    val partes = ByteArrayOutputStream()
    val central = ByteArrayOutputStream()
    var desplazamiento = 0
    fun bloque(largo: Int, campos: List<Triple<Int, Long, Int>>): ByteArray {
        val b = ByteBuffer.allocate(largo).order(ByteOrder.LITTLE_ENDIAN)
        for ((i, v, t) in campos) if (t == 4) b.putInt(i, v.toInt()) else b.putShort(i, v.toInt().toShort())
        return b.array()
    }
    for ((nombre, bytes) in archivos) {
        val n = nombre.encodeToByteArray()
        val crc = crc32(bytes)
        val largo = bytes.size.toLong()
        // Firma, versión 2.0, nombres en UTF-8, sin compresión, fecha 01-01-1980.
        val local = bloque(30, listOf(Triple(0, 0x04034b50L, 4), Triple(4, 20L, 2), Triple(6, 0x0800L, 2),
            Triple(8, 0L, 2), Triple(10, 0L, 2), Triple(12, 33L, 2), Triple(14, crc, 4), Triple(18, largo, 4),
            Triple(22, largo, 4), Triple(26, n.size.toLong(), 2), Triple(28, 0L, 2)))
        val cabecera = bloque(46, listOf(Triple(0, 0x02014b50L, 4), Triple(4, 20L, 2), Triple(6, 20L, 2),
            Triple(8, 0x0800L, 2), Triple(10, 0L, 2), Triple(12, 0L, 2), Triple(14, 33L, 2), Triple(16, crc, 4),
            Triple(20, largo, 4), Triple(24, largo, 4), Triple(28, n.size.toLong(), 2), Triple(30, 0L, 2),
            Triple(32, 0L, 2), Triple(34, 0L, 2), Triple(36, 0L, 2), Triple(38, 0L, 4),
            Triple(42, desplazamiento.toLong(), 4)))
        partes.write(local); partes.write(n); partes.write(bytes)
        central.write(cabecera); central.write(n)
        desplazamiento += 30 + n.size + bytes.size
    }
    val largoCentral = central.size().toLong()
    val fin = bloque(22, listOf(Triple(0, 0x06054b50L, 4), Triple(4, 0L, 2), Triple(6, 0L, 2),
        Triple(8, archivos.size.toLong(), 2), Triple(10, archivos.size.toLong(), 2), Triple(12, largoCentral, 4),
        Triple(16, desplazamiento.toLong(), 4), Triple(20, 0L, 2)))
    partes.write(central.toByteArray())
    partes.write(fin)
    return partes.toByteArray()
}

// Lo que no puede ir en un XML (caracteres de control) se quita.
private fun escaparXml(t: String): String = t.replace(CONTROL, "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** 0 -> A, 26 -> AA. */
private fun letraColumna(i: Int): String {
    var s = ""
    var n = i + 1
    while (n > 0) { s = ('A' + (n - 1) % 26) + s; n = (n - 1) / 26 }
    return s
}

// Estilos (cellXfs): 0 normal, 1 negrita, 2 soles (0.00), 3 soles en negrita.
private const val ESTILOS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
    "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
    "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
    "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
    "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
    "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "<xf numFmtId=\"2\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyNumberFormat=\"1\"/></cellXfs>" +
    "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"

/**
 * Una celda con formato. `v`: texto, número o vacío (null o ""); `soles`:
 * número con dos decimales.
 */
data class Celda(val v: Any?, val negrita: Boolean = false, val soles: Boolean = false)

/**
 * Una hoja del libro. Cada celda de `filas` es un String, un número (Int,
 * Long, Double), vacío (null o "") o una Celda. Una fila null o vacía queda
 * en blanco. `anchos`: de cada columna, en caracteres.
 */
data class HojaXlsx(val nombre: String?, val filas: List<List<Any?>?>, val anchos: List<Int> = emptyList())

private fun celdaXml(c: Any?, ref: String): String {
    val o = c as? Celda ?: Celda(c)
    if (o.v == null || o.v == "") return ""
    val s = (if (o.soles) 2 else 0) + (if (o.negrita) 1 else 0)
    val estilo = if (s != 0) " s=\"$s\"" else ""
    val v = o.v
    if (v is Number && v.toDouble().isFinite()) return "<c r=\"$ref\"$estilo><v>${numeroJs(v)}</v></c>"
    // Todo lo demás, texto: un DNI con su 0 adelante queda como se escribió.
    val texto = if (v is Number) numeroJs(v) else v.toString()
    return "<c r=\"$ref\" t=\"inlineStr\"$estilo><is><t xml:space=\"preserve\">${escaparXml(texto)}</t></is></c>"
}

private fun hojaXml(h: HojaXlsx): String {
    val cols = if (h.anchos.isNotEmpty()) {
        "<cols>${h.anchos.mapIndexed { i, a -> "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$a\" customWidth=\"1\"/>" }.joinToString("")}</cols>"
    } else ""
    val cuerpo = h.filas.mapIndexed { f, fila ->
        val celdas = (fila ?: emptyList()).mapIndexed { i, c -> celdaXml(c, letraColumna(i) + (f + 1)) }.joinToString("")
        if (celdas.isNotEmpty()) "<row r=\"${f + 1}\">$celdas</row>" else ""
    }.joinToString("")
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">$cols<sheetData>$cuerpo</sheetData></worksheet>"
}

/** Arma un .xlsx con las hojas, en orden. */
fun armarXlsx(hojas: List<HojaXlsx>): ByteArray {
    // Excel no acepta nombres de hoja de más de 31 letras ni con []:*?/\
    val nombres = hojas.mapIndexed { i, h ->
        (h.nombre?.ifEmpty { null } ?: "Hoja ${i + 1}").replace(Regex("[\\[\\]:*?/\\\\]"), " ").take(31)
    }
    val nsR = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    val cabecera = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    val archivos = listOf(
        "[Content_Types].xml" to cabecera +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            hojas.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } +
            "</Types>",
        "_rels/.rels" to cabecera +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$nsR/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
        "xl/workbook.xml" to cabecera +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"$nsR\"><sheets>" +
            nombres.mapIndexed { i, n -> "<sheet name=\"${escaparXml(n)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") +
            "</sheets></workbook>",
        "xl/_rels/workbook.xml.rels" to cabecera +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            hojas.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$nsR/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
            "<Relationship Id=\"rId${hojas.size + 1}\" Type=\"$nsR/styles\" Target=\"styles.xml\"/></Relationships>",
        "xl/styles.xml" to ESTILOS
    ) + hojas.mapIndexed { i, h -> "xl/worksheets/sheet${i + 1}.xml" to hojaXml(h) }
    return armarZip(archivos.map { (nombre, texto) -> nombre to texto.encodeToByteArray() })
}

/** `trim()` de JavaScript: espacios Unicode y la marca U+FEFF; no los separadores U+001C a U+001F. */
internal fun String.trimJs(): String = trim { esEspacioJs(it) }
internal fun String.trimEndJs(): String = trimEnd { esEspacioJs(it) }
private fun esEspacioJs(c: Char) = (c.isWhitespace() && c !in '\u001C'..'\u001F') || c == '﻿'
