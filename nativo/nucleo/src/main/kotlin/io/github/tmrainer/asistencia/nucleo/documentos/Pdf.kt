// Pdf.kt — porte de web/js/pdf.js: escribir un PDF a mano: texto, líneas y
// recuadros, con las fuentes estándar Helvetica y Helvetica-Bold (codificación
// WinAnsi, con tildes y ñ). Sin librerías ni red. Puro: devuelve
// los bytes.
//
// Lo usan el PDF del B-1 (PdfB1.kt) y los del padrón y la asistencia
// (PdfReportes.kt).
//
// Diferencia con la web: el «lienzo» es una interfaz (Lienzo). El del PDF
// (LienzoPdf) escribe los operadores igual que la web; LienzoTrazos guarda lo
// mismo como datos (líneas, recuadros y textos en mm) para que la app dibuje la
// hoja en pantalla sin pasar por los bytes del PDF, como hace la vista previa
// de la web con su lienzo SVG.
package io.github.tmrainer.asistencia.nucleo.documentos

import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

const val PT = 72 / 25.4 // puntos por mm

// Anchos de Helvetica (AFM estándar, milésimas de em) para medir el texto.
private val ANCHOS = intArrayOf(278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
    556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556, 1015,
    667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667, 611,
    722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, 333,
    556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333, 500, 278,
    556, 500, 722, 500, 500, 500, 334, 260, 334, 584)
private val ESPECIALES = mapOf(
    '–'.code to 0x96, '—'.code to 0x97, '…'.code to 0x85, '‘'.code to 0x91,
    '’'.code to 0x92, '“'.code to 0x93, '”'.code to 0x94, '•'.code to 0x95
)

/** Texto a bytes WinAnsi (Latin-1 + algunos signos); lo que no existe, «?». Cada Char vale un byte. */
fun aWinAnsi(texto: String): String {
    val s = StringBuilder()
    texto.codePoints().forEach { c ->
        val especial = ESPECIALES[c]
        when {
            especial != null -> s.append(especial.toChar())
            c in 32..255 && c !in 127 until 160 -> s.append(c.toChar())
            else -> s.append('?')
        }
    }
    return s.toString().replace(ESCAPAR) { "\\" + it.value }
}
private val ESCAPAR = Regex("[\\\\()]")

/** Ancho del texto en mm a `pt` puntos (las letras con tilde, como su base). */
fun anchoMm(texto: String, pt: Double, negrita: Boolean = false): Double {
    var mil = 0
    texto.codePoints().forEach { cp ->
        val base = Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFD)[0]
        val c = base.code
        mil += if (c in 32..126) ANCHOS[c - 32] else 556
    }
    return (mil.toDouble() / 1000) * pt * (if (negrita) 1.07 else 1.0) / PT
}

// ------------------------------------------- números como los escribe la web
//
// El PDF y el Excel llevan números escritos como texto. Para que salgan los
// mismos bytes que en la web, se escriben como JavaScript: `String(n)` y
// `n.toFixed(2)`.

/** `String(n)` de JavaScript: 9, 7.5, 0.45, 1e+21. */
internal fun numeroJs(n: Number): String {
    if (n !is Double && n !is Float) return n.toString()
    val d = n.toDouble()
    if (d.isNaN()) return "NaN"
    if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
    if (d == 0.0) return "0"
    // Double.toString da los dígitos más cortos que vuelven al mismo número, igual que JavaScript.
    val exacto = BigDecimal(d.toString()).stripTrailingZeros()
    val a = abs(d)
    if (a >= 1e-6 && a < 1e21) return exacto.toPlainString()
    val digitos = exacto.unscaledValue().abs().toString()
    val exponente = digitos.length - 1 - exacto.scale()
    val mantisa = if (digitos.length == 1) digitos else digitos[0] + "." + digitos.substring(1)
    return (if (d < 0) "-" else "") + mantisa + "e" + (if (exponente >= 0) "+" else "") + exponente
}

/** `n.toFixed(decimales)` de JavaScript: redondea el valor exacto, el empate hacia arriba. */
internal fun fijo(n: Double, decimales: Int = 2): String {
    val texto = BigDecimal(abs(n)).setScale(decimales, RoundingMode.HALF_UP).toPlainString()
    return if (n < 0) "-$texto" else texto
}

/**
 * 0 domingo … 6 sábado, como `new Date(a, m - 1, d).getDay()`: una fecha que
 * no existe (31 de febrero) rueda a la siguiente, como en la web.
 */
internal fun diaDeSemanaJs(iso: String): Int {
    val (a, m, d) = iso.split("-").map { it.toInt() }
    return LocalDate.of(a, 1, 1).plusMonths(m - 1L).plusDays(d - 1L).dayOfWeek.value % 7
}

// ------------------------------------------------------------ dibujo en mm

/**
 * Una hoja para dibujar en mm desde arriba a la izquierda. Lo saben hacer el
 * PDF (LienzoPdf) y la pantalla (LienzoTrazos).
 */
interface Lienzo {
    fun gris(g: Double)
    fun grosor(mm: Double)
    fun punteada(si: Boolean)
    fun linea(x1: Double, y1: Double, x2: Double, y2: Double)
    fun caja(xMm: Double, yMm: Double, anchoMm: Double, altoMm: Double)
    fun relleno(xMm: Double, yMm: Double, anchoMm: Double, altoMm: Double)
    /** `yBase`: línea donde se apoyan las letras. Un texto vacío no se dibuja. */
    fun texto(t: String, xMm: Double, yBase: Double, pt: Double, negrita: Boolean = false)
}

/** El lienzo del PDF: los operadores de la página. A4: 297 de alto; apaisada: 210. */
class LienzoPdf(val altoMm: Double = 297.0) : Lienzo {
    val ops = mutableListOf<String>()
    private fun x(mm: Double) = fijo(mm * PT)
    private fun y(mm: Double) = fijo((altoMm - mm) * PT)

    override fun gris(g: Double) { ops.add("${numeroJs(g)} g ${numeroJs(g)} G") }
    override fun grosor(mm: Double) { ops.add("${fijo(mm * PT)} w") }
    override fun punteada(si: Boolean) { ops.add(if (si) "[0.6 1.8] 0 d" else "[] 0 d") }
    override fun linea(x1: Double, y1: Double, x2: Double, y2: Double) {
        ops.add("${x(x1)} ${y(y1)} m ${x(x2)} ${y(y2)} l S")
    }
    override fun caja(xMm: Double, yMm: Double, anchoMm: Double, altoMm: Double) {
        ops.add("${x(xMm)} ${y(yMm + altoMm)} ${fijo(anchoMm * PT)} ${fijo(altoMm * PT)} re S")
    }
    override fun relleno(xMm: Double, yMm: Double, anchoMm: Double, altoMm: Double) {
        ops.add("${x(xMm)} ${y(yMm + altoMm)} ${fijo(anchoMm * PT)} ${fijo(altoMm * PT)} re f")
    }
    override fun texto(t: String, xMm: Double, yBase: Double, pt: Double, negrita: Boolean) {
        if (t.isEmpty()) return
        ops.add("BT /${if (negrita) "F2" else "F1"} ${numeroJs(pt)} Tf ${x(xMm)} ${y(yBase)} Td (${aWinAnsi(t)}) Tj ET")
    }
}

/** Una hoja en blanco para el PDF, como `lienzo()` de la web. */
fun lienzo(altoMm: Double = 297.0) = LienzoPdf(altoMm)

/**
 * Lo dibujado, como datos: en mm desde arriba a la izquierda, con su gris
 * (0 negro, 1 blanco), grosor de línea en mm y si es punteada. Para pintarlo
 * en pantalla (Canvas de Compose) igual que en el PDF.
 */
sealed interface Trazo {
    data class Linea(val x1: Double, val y1: Double, val x2: Double, val y2: Double,
                     val gris: Double, val grosorMm: Double, val punteada: Boolean) : Trazo
    data class Caja(val xMm: Double, val yMm: Double, val anchoMm: Double, val altoMm: Double,
                    val gris: Double, val grosorMm: Double, val punteada: Boolean) : Trazo
    data class Relleno(val xMm: Double, val yMm: Double, val anchoMm: Double, val altoMm: Double,
                       val gris: Double) : Trazo
    /** Helvetica (o Helvetica-Bold) a `pt` puntos, apoyada en `yBaseMm`. */
    data class Texto(val texto: String, val xMm: Double, val yBaseMm: Double, val pt: Double,
                     val negrita: Boolean, val gris: Double) : Trazo
}

/**
 * Un lienzo que anota en vez de escribir el PDF (el lienzoSvg() de
 * pagina-vista-previa.js). Empieza como un PDF: negro, línea de 1 pt, continua.
 * La punteada del PDF es [0.6 1.8] pt: 0.21 mm de raya y 0.64 mm de espacio.
 */
class LienzoTrazos : Lienzo {
    val trazos = mutableListOf<Trazo>()
    private var gris = 0.0
    private var grosor = 1 / PT
    private var punteada = false

    override fun gris(g: Double) { gris = g }
    override fun grosor(mm: Double) { grosor = mm }
    override fun punteada(si: Boolean) { punteada = si }
    override fun linea(x1: Double, y1: Double, x2: Double, y2: Double) {
        trazos.add(Trazo.Linea(x1, y1, x2, y2, gris, grosor, punteada))
    }
    override fun caja(xMm: Double, yMm: Double, anchoMm: Double, altoMm: Double) {
        trazos.add(Trazo.Caja(xMm, yMm, anchoMm, altoMm, gris, grosor, punteada))
    }
    override fun relleno(xMm: Double, yMm: Double, anchoMm: Double, altoMm: Double) {
        trazos.add(Trazo.Relleno(xMm, yMm, anchoMm, altoMm, gris))
    }
    override fun texto(t: String, xMm: Double, yBase: Double, pt: Double, negrita: Boolean) {
        if (t.isEmpty()) return
        trazos.add(Trazo.Texto(t, xMm, yBase, pt, negrita, gris))
    }
}

/** Una página: contenido = `LienzoPdf.ops.joinToString("\n")`. */
data class PaginaPdf(val contenido: String, val apaisada: Boolean = false)

/** Arma el archivo PDF con las hojas dibujadas. */
fun armarPdf(paginasPdf: List<PaginaPdf>, titulo: String): ByteArray {
    val objetos = mutableListOf<String?>()
    fun agregar(cuerpo: String?): Int { objetos.add(cuerpo); return objetos.size }
    val catalogo = agregar(null)
    val paginas = agregar(null)
    val f1 = agregar("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    val f2 = agregar("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    val kids = paginasPdf.map { (contenido, apaisada) ->
        val flujo = agregar("<< /Length ${contenido.length} >>\nstream\n$contenido\nendstream")
        val caja = if (apaisada) "0 0 841.89 595.28" else "0 0 595.28 841.89"
        agregar("<< /Type /Page /Parent $paginas 0 R /MediaBox [$caja] " +
            "/Resources << /Font << /F1 $f1 0 R /F2 $f2 0 R >> >> /Contents $flujo 0 R >>")
    }
    objetos[catalogo - 1] = "<< /Type /Catalog /Pages $paginas 0 R >>"
    objetos[paginas - 1] = "<< /Type /Pages /Kids [${kids.joinToString(" ") { "$it 0 R" }}] /Count ${kids.size} >>"
    val info = agregar("<< /Title (${aWinAnsi(titulo)}) /Producer (OllaConecta) >>")

    // Un Char por byte, como el texto de la web (todo es ASCII o ya pasó por aWinAnsi).
    val pdf = StringBuilder("%PDF-1.4\n%âãÏÓ\n")
    val offsets = mutableListOf<Int>()
    objetos.forEachIndexed { i, o -> offsets.add(pdf.length); pdf.append("${i + 1} 0 obj\n$o\nendobj\n") }
    val xref = pdf.length
    pdf.append("xref\n0 ${objetos.size + 1}\n0000000000 65535 f \n")
    for (off in offsets) pdf.append("${off.toString().padStart(10, '0')} 00000 n \n")
    pdf.append("trailer\n<< /Size ${objetos.size + 1} /Root $catalogo 0 R /Info $info 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return ByteArray(pdf.length) { (pdf[it].code and 0xff).toByte() }
}

/**
 * «14-10-26 18:40»: cuándo se generó una copia (selloAhora() de
 * ui-reportes.js). Es el valor por defecto de `generado` en los PDF.
 */
fun selloAhora(ahora: LocalDateTime = LocalDateTime.now()): String {
    fun dos(n: Int) = n.toString().padStart(2, '0')
    return "${aDdMmAa("${ahora.year}-${dos(ahora.monthValue)}-${dos(ahora.dayOfMonth)}")} ${dos(ahora.hour)}:${dos(ahora.minute)}"
}
