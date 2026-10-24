// GeneradorQr.kt — la matriz de módulos de un código QR, para que la pantalla
// la dibuje (un Canvas de Compose). Porte a Kotlin de la parte de
// qrcode-generator 2.0.4 (web/vendor/qrcode-generator.js) que usa la web en
// dibujarQr() de camara-qr.js: qrcode(0, 'L'), modo numérico si el texto es
// solo cifras y alfanumérico si no, make(), getModuleCount() e isDark().
// No se portan el modo byte ni el kanji, ni lo que dibuja (tablas, SVG, GIF).
//
// La matriz sale idéntica, módulo por módulo, a la de la web: lo prueba
// QrCruzadoTest con matrices generadas por la librería JS
// (nativo/herramientas/qr-cruzado.mjs).
//
// Licencia de la librería original (MIT), que se conserva aquí y en
// nucleo/src/main/resources/licencias/LICENCIA-qrcode-generator.txt (viaja
// dentro del APK):
//
//---------------------------------------------------------------------
//
// QR Code Generator for JavaScript
//
// Copyright (c) 2009 Kazuhiko Arase
//
// URL: http://www.d-project.com/
//
// Licensed under the MIT license:
//  http://www.opensource.org/licenses/mit-license.php
//
// The word 'QR Code' is registered trademark of
// DENSO WAVE INCORPORATED
//  http://www.denso-wave.com/qrcode/faqpatent-e.html
//
//---------------------------------------------------------------------
//
// MIT License
//
// Copyright (c) 2009 Kazuhiko Arase
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
package io.github.tmrainer.asistencia.nucleo.qr

import kotlin.math.abs

/**
 * Los módulos de un QR ya armado. Sin el margen blanco: la norma pide 4
 * módulos alrededor, y lo pone quien dibuja (como dibujarQr() en la web).
 */
class MatrizQr internal constructor(
    /** Versión del QR (1 a 40): cuánto cabe. */
    val version: Int,
    private val modulos: Array<BooleanArray>
) {
    /** Módulos por lado (getModuleCount()). */
    val tamano: Int get() = modulos.size

    /** ¿Va negro el módulo de esa fila y columna? (isDark()) */
    fun oscuro(fila: Int, columna: Int): Boolean = modulos[fila][columna]
}

/**
 * Arma el QR de un texto de la app: nivel de corrección L (menos módulos, se
 * lee mejor desde otra pantalla), la versión más chica en que entra, modo
 * numérico si son solo cifras (QR de antes, lista-qr) y alfanumérico si no
 * (base45 de compartir-qr).
 * @throws IllegalArgumentException si tiene letras fuera del alfabeto del QR
 *   (minúsculas, tildes…) o no cabe ni en la versión 40. Los textos de
 *   codificarListaQr() y codificarParaQr() siempre entran.
 */
fun matrizQr(texto: String): MatrizQr {
    val numerico = texto.isNotEmpty() && texto.all { it in '0'..'9' }
    val datos = Segmento(if (numerico) MODO_NUMERICO else MODO_ALFANUMERICO, texto)
    datos.revisar()
    // make() con typeNumber 0: la primera versión en que entran los datos.
    var version = 1
    while (version < 40) {
        if (bitsDe(datos, version) <= bloquesL(version).sumOf { it.datos } * 8) break
        version += 1
    }
    val bytes = crearDatos(version, datos)
    return MatrizQr(version, armar(version, bytes, mejorMascara(version, bytes)).aBooleanos())
}

// ------------------------------------------------------------------- datos

private const val MODO_NUMERICO = 1 shl 0
private const val MODO_ALFANUMERICO = 1 shl 1
private const val NIVEL_L = 1          // QRErrorCorrectionLevel.L
private const val PAD0 = 0xEC
private const val PAD1 = 0x11

private const val ALFANUMERICO = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

private class Segmento(val modo: Int, val texto: String) {

    fun revisar() {
        val malo = texto.firstOrNull { if (modo == MODO_NUMERICO) it !in '0'..'9' else ALFANUMERICO.indexOf(it) < 0 }
        require(malo == null) { "illegal char :$malo" }
    }

    /** qrNumber.write / qrAlphaNum.write */
    fun escribir(buffer: BufferBits) {
        val s = texto
        var i = 0
        if (modo == MODO_NUMERICO) {
            while (i + 2 < s.length) {
                buffer.put(s.substring(i, i + 3).toInt(), 10)
                i += 3
            }
            if (i < s.length) {
                if (s.length - i == 1) buffer.put(s.substring(i, i + 1).toInt(), 4)
                else if (s.length - i == 2) buffer.put(s.substring(i, i + 2).toInt(), 7)
            }
        } else {
            while (i + 1 < s.length) {
                buffer.put(ALFANUMERICO.indexOf(s[i]) * 45 + ALFANUMERICO.indexOf(s[i + 1]), 11)
                i += 2
            }
            if (i < s.length) buffer.put(ALFANUMERICO.indexOf(s[i]), 6)
        }
    }
}

/** QRUtil.getLengthInBits, solo para los dos modos que se usan. */
private fun bitsDelLargo(modo: Int, version: Int): Int = when {
    version < 10 -> if (modo == MODO_NUMERICO) 10 else 9
    version < 27 -> if (modo == MODO_NUMERICO) 12 else 11
    else -> if (modo == MODO_NUMERICO) 14 else 13
}

private fun escribirSegmento(buffer: BufferBits, datos: Segmento, version: Int) {
    buffer.put(datos.modo, 4)
    buffer.put(datos.texto.length, bitsDelLargo(datos.modo, version))
    datos.escribir(buffer)
}

private fun bitsDe(datos: Segmento, version: Int): Int =
    BufferBits().also { escribirSegmento(it, datos, version) }.largo

/** qrBitBuffer */
private class BufferBits {
    private var buffer = IntArray(64)
    var largo = 0
        private set

    fun byte(i: Int): Int = buffer[i]

    fun put(num: Int, length: Int) {
        for (i in 0 until length) putBit(((num ushr (length - i - 1)) and 1) == 1)
    }

    fun putBit(bit: Boolean) {
        val i = largo / 8
        if (i >= buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        if (bit) buffer[i] = buffer[i] or (0x80 ushr (largo % 8))
        largo += 1
    }
}

/** createData: los bytes de datos con su relleno, y la corrección de errores. */
private fun crearDatos(version: Int, datos: Segmento): IntArray {
    val bloques = bloquesL(version)
    val buffer = BufferBits()
    escribirSegmento(buffer, datos, version)
    val totalDatos = bloques.sumOf { it.datos }
    require(buffer.largo <= totalDatos * 8) { "code length overflow. (${buffer.largo}>${totalDatos * 8})" }
    if (buffer.largo + 4 <= totalDatos * 8) buffer.put(0, 4)      // fin
    while (buffer.largo % 8 != 0) buffer.putBit(false)
    while (true) {
        if (buffer.largo >= totalDatos * 8) break
        buffer.put(PAD0, 8)
        if (buffer.largo >= totalDatos * 8) break
        buffer.put(PAD1, 8)
    }
    return crearBytes(buffer, bloques)
}

/** createBytes: Reed-Solomon por bloque y los bloques intercalados. */
private fun crearBytes(buffer: BufferBits, bloques: List<BloqueRs>): IntArray {
    var offset = 0
    var maxDc = 0
    var maxEc = 0
    val dcdata = arrayOfNulls<IntArray>(bloques.size)
    val ecdata = arrayOfNulls<IntArray>(bloques.size)
    for ((r, b) in bloques.withIndex()) {
        val dcCount = b.datos
        val ecCount = b.total - dcCount
        maxDc = maxOf(maxDc, dcCount)
        maxEc = maxOf(maxEc, ecCount)
        val dc = IntArray(dcCount) { 0xff and buffer.byte(it + offset) }
        dcdata[r] = dc
        offset += dcCount
        val rsPoly = polinomioCorrector(ecCount)
        val modPoly = Polinomio(dc, rsPoly.largo - 1).modulo(rsPoly)
        val ec = IntArray(rsPoly.largo - 1)
        for (i in ec.indices) {
            val modIndex = i + modPoly.largo - ec.size
            ec[i] = if (modIndex >= 0) modPoly[modIndex] else 0
        }
        ecdata[r] = ec
    }
    val data = IntArray(bloques.sumOf { it.total })
    var index = 0
    for (i in 0 until maxDc) for (r in bloques.indices) {
        val dc = dcdata[r]!!
        if (i < dc.size) data[index++] = dc[i]
    }
    for (i in 0 until maxEc) for (r in bloques.indices) {
        val ec = ecdata[r]!!
        if (i < ec.size) data[index++] = ec[i]
    }
    return data
}

// ------------------------------------------------------------ la matriz

/** Módulos en armado: -1 sin poner (null en la librería), 0 claro, 1 oscuro. */
private class Modulos(val n: Int) {
    val m = Array(n) { ByteArray(n) { -1 } }
    fun libre(f: Int, c: Int) = m[f][c].toInt() == -1
    fun poner(f: Int, c: Int, oscuro: Boolean) { m[f][c] = if (oscuro) 1 else 0 }
    fun oscuro(f: Int, c: Int) = m[f][c].toInt() == 1
    fun aBooleanos() = Array(n) { f -> BooleanArray(n) { c -> oscuro(f, c) } }
}

/** getBestMaskPattern: la máscara con menos puntos perdidos (la primera si empatan). */
private fun mejorMascara(version: Int, datos: IntArray): Int {
    var minimo = 0.0
    var patron = 0
    for (i in 0 until 8) {
        val perdidos = puntosPerdidos(armar(version, datos, i, prueba = true))
        if (i == 0 || minimo > perdidos) {
            minimo = perdidos
            patron = i
        }
    }
    return patron
}

/** makeImpl */
private fun armar(version: Int, datos: IntArray, mascara: Int, prueba: Boolean = false): Modulos {
    val q = Modulos(version * 4 + 17)
    patronDePosicion(q, 0, 0)
    patronDePosicion(q, q.n - 7, 0)
    patronDePosicion(q, 0, q.n - 7)
    patronesDeAlineacion(q, version)
    patronDeTiempo(q)
    infoDeFormato(q, prueba, mascara)
    if (version >= 7) infoDeVersion(q, version, prueba)
    ubicarDatos(q, datos, mascara)
    return q
}

/** setupPositionProbePattern */
private fun patronDePosicion(q: Modulos, fila: Int, col: Int) {
    for (r in -1..7) {
        if (fila + r <= -1 || q.n <= fila + r) continue
        for (c in -1..7) {
            if (col + c <= -1 || q.n <= col + c) continue
            q.poner(fila + r, col + c,
                (r in 0..6 && (c == 0 || c == 6)) || (c in 0..6 && (r == 0 || r == 6)) || (r in 2..4 && c in 2..4))
        }
    }
}

/** setupTimingPattern */
private fun patronDeTiempo(q: Modulos) {
    for (r in 8 until q.n - 8) if (q.libre(r, 6)) q.poner(r, 6, r % 2 == 0)
    for (c in 8 until q.n - 8) if (q.libre(6, c)) q.poner(6, c, c % 2 == 0)
}

/** setupPositionAdjustPattern */
private fun patronesDeAlineacion(q: Modulos, version: Int) {
    val pos = POSICIONES_ALINEACION[version - 1]
    for (fila in pos) for (col in pos) {
        if (!q.libre(fila, col)) continue
        for (r in -2..2) for (c in -2..2) {
            q.poner(fila + r, col + c, r == -2 || r == 2 || c == -2 || c == 2 || (r == 0 && c == 0))
        }
    }
}

/** setupTypeNumber */
private fun infoDeVersion(q: Modulos, version: Int, prueba: Boolean) {
    val bits = bchVersion(version)
    for (i in 0 until 18) q.poner(i / 3, i % 3 + q.n - 8 - 3, !prueba && ((bits shr i) and 1) == 1)
    for (i in 0 until 18) q.poner(i % 3 + q.n - 8 - 3, i / 3, !prueba && ((bits shr i) and 1) == 1)
}

/** setupTypeInfo */
private fun infoDeFormato(q: Modulos, prueba: Boolean, mascara: Int) {
    val bits = bchFormato((NIVEL_L shl 3) or mascara)
    for (i in 0 until 15) {           // vertical
        val mod = !prueba && ((bits shr i) and 1) == 1
        when {
            i < 6 -> q.poner(i, 8, mod)
            i < 8 -> q.poner(i + 1, 8, mod)
            else -> q.poner(q.n - 15 + i, 8, mod)
        }
    }
    for (i in 0 until 15) {           // horizontal
        val mod = !prueba && ((bits shr i) and 1) == 1
        when {
            i < 8 -> q.poner(8, q.n - i - 1, mod)
            i < 9 -> q.poner(8, 15 - i - 1 + 1, mod)
            else -> q.poner(8, 15 - i - 1, mod)
        }
    }
    q.poner(q.n - 8, 8, !prueba)      // módulo fijo
}

/** mapData */
private fun ubicarDatos(q: Modulos, datos: IntArray, mascara: Int) {
    var inc = -1
    var fila = q.n - 1
    var bitIndex = 7
    var byteIndex = 0
    var col = q.n - 1
    while (col > 0) {
        if (col == 6) col -= 1
        while (true) {
            for (c in 0 until 2) {
                if (q.libre(fila, col - c)) {
                    var oscuro = false
                    if (byteIndex < datos.size) oscuro = ((datos[byteIndex] ushr bitIndex) and 1) == 1
                    if (enmascara(mascara, fila, col - c)) oscuro = !oscuro
                    q.poner(fila, col - c, oscuro)
                    bitIndex -= 1
                    if (bitIndex == -1) {
                        byteIndex += 1
                        bitIndex = 7
                    }
                }
            }
            fila += inc
            if (fila < 0 || q.n <= fila) {
                fila -= inc
                inc = -inc
                break
            }
        }
        col -= 2
    }
}

/** QRUtil.getMaskFunction */
private fun enmascara(mascara: Int, i: Int, j: Int): Boolean = when (mascara) {
    0 -> (i + j) % 2 == 0
    1 -> i % 2 == 0
    2 -> j % 3 == 0
    3 -> (i + j) % 3 == 0
    4 -> (i / 2 + j / 3) % 2 == 0
    5 -> (i * j) % 2 + (i * j) % 3 == 0
    6 -> ((i * j) % 2 + (i * j) % 3) % 2 == 0
    7 -> ((i * j) % 3 + (i + j) % 2) % 2 == 0
    else -> error("bad maskPattern:$mascara")
}

/**
 * QRUtil.getLostPoint. En Double, como en JavaScript: la parte de la
 * proporción de oscuros no es entera, y el desempate entre máscaras depende
 * de ella.
 */
private fun puntosPerdidos(q: Modulos): Double {
    val n = q.n
    var perdidos = 0.0
    // nivel 1: módulos rodeados de iguales
    for (fila in 0 until n) for (col in 0 until n) {
        var iguales = 0
        val oscuro = q.oscuro(fila, col)
        for (r in -1..1) {
            if (fila + r < 0 || n <= fila + r) continue
            for (c in -1..1) {
                if (col + c < 0 || n <= col + c) continue
                if (r == 0 && c == 0) continue
                if (oscuro == q.oscuro(fila + r, col + c)) iguales += 1
            }
        }
        if (iguales > 5) perdidos += (3 + iguales - 5)
    }
    // nivel 2: cuadros de 2×2 parejos
    for (fila in 0 until n - 1) for (col in 0 until n - 1) {
        var cuenta = 0
        if (q.oscuro(fila, col)) cuenta += 1
        if (q.oscuro(fila + 1, col)) cuenta += 1
        if (q.oscuro(fila, col + 1)) cuenta += 1
        if (q.oscuro(fila + 1, col + 1)) cuenta += 1
        if (cuenta == 0 || cuenta == 4) perdidos += 3
    }
    // nivel 3: 1:1:3:1:1, que se confunde con un patrón de posición
    for (fila in 0 until n) for (col in 0 until n - 6) {
        if (q.oscuro(fila, col) && !q.oscuro(fila, col + 1) && q.oscuro(fila, col + 2) && q.oscuro(fila, col + 3) &&
            q.oscuro(fila, col + 4) && !q.oscuro(fila, col + 5) && q.oscuro(fila, col + 6)) perdidos += 40
    }
    for (col in 0 until n) for (fila in 0 until n - 6) {
        if (q.oscuro(fila, col) && !q.oscuro(fila + 1, col) && q.oscuro(fila + 2, col) && q.oscuro(fila + 3, col) &&
            q.oscuro(fila + 4, col) && !q.oscuro(fila + 5, col) && q.oscuro(fila + 6, col)) perdidos += 40
    }
    // nivel 4: proporción de oscuros (mismo orden de operaciones que la librería)
    var oscuros = 0
    for (col in 0 until n) for (fila in 0 until n) if (q.oscuro(fila, col)) oscuros += 1
    val ratio = abs(100.0 * oscuros / n / n - 50) / 5
    perdidos += ratio * 10
    return perdidos
}

// --------------------------------------------------------- BCH y Galois

private const val G15 = (1 shl 10) or (1 shl 8) or (1 shl 5) or (1 shl 4) or (1 shl 2) or (1 shl 1) or (1 shl 0)
private const val G18 = (1 shl 12) or (1 shl 11) or (1 shl 10) or (1 shl 9) or (1 shl 8) or (1 shl 5) or (1 shl 2) or (1 shl 0)
private const val G15_MASK = (1 shl 14) or (1 shl 12) or (1 shl 10) or (1 shl 4) or (1 shl 1)

private fun digitosBch(dato: Int): Int {
    var d = dato
    var digitos = 0
    while (d != 0) {
        digitos += 1
        d = d ushr 1
    }
    return digitos
}

private fun bchFormato(dato: Int): Int {
    var d = dato shl 10
    while (digitosBch(d) - digitosBch(G15) >= 0) d = d xor (G15 shl (digitosBch(d) - digitosBch(G15)))
    return ((dato shl 10) or d) xor G15_MASK
}

private fun bchVersion(dato: Int): Int {
    var d = dato shl 12
    while (digitosBch(d) - digitosBch(G18) >= 0) d = d xor (G18 shl (digitosBch(d) - digitosBch(G18)))
    return (dato shl 12) or d
}

/** QRMath: tablas de GF(256). */
private val EXP = IntArray(256).also { t ->
    for (i in 0 until 8) t[i] = 1 shl i
    for (i in 8 until 256) t[i] = t[i - 4] xor t[i - 5] xor t[i - 6] xor t[i - 8]
}
private val LOG = IntArray(256).also { t -> for (i in 0 until 255) t[EXP[i]] = i }

private fun glog(n: Int): Int {
    require(n >= 1) { "glog($n)" }
    return LOG[n]
}

private fun gexp(n: Int): Int {
    var k = n
    while (k < 0) k += 255
    while (k >= 256) k -= 255
    return EXP[k]
}

/**
 * qrPolynomial. La librería deja `undefined` en los lugares agregados por
 * `shift`; valen como 0 en toda la cuenta, así que aquí son ceros.
 */
private class Polinomio(num: IntArray, desplazamiento: Int) {
    private val num: IntArray

    init {
        var offset = 0
        while (offset < num.size && num[offset] == 0) offset += 1
        this.num = IntArray(num.size - offset + desplazamiento)
        System.arraycopy(num, offset, this.num, 0, num.size - offset)
    }

    operator fun get(i: Int) = num[i]
    val largo: Int get() = num.size

    fun multiplicar(e: Polinomio): Polinomio {
        val r = IntArray(largo + e.largo - 1)
        for (i in 0 until largo) for (j in 0 until e.largo) r[i + j] = r[i + j] xor gexp(glog(this[i]) + glog(e[j]))
        return Polinomio(r, 0)
    }

    /** mod, sin recursión. */
    fun modulo(e: Polinomio): Polinomio {
        var p = this
        while (p.largo - e.largo >= 0) {
            val ratio = glog(p[0]) - glog(e[0])
            val r = p.num.copyOf()
            for (i in 0 until e.largo) r[i] = r[i] xor gexp(glog(e[i]) + ratio)
            p = Polinomio(r, 0)
        }
        return p
    }
}

private val CORRECTORES = HashMap<Int, Polinomio>()

/** QRUtil.getErrorCorrectPolynomial (se guarda: se pide una vez por bloque). */
private fun polinomioCorrector(largo: Int): Polinomio = synchronized(CORRECTORES) {
    CORRECTORES.getOrPut(largo) {
        var a = Polinomio(intArrayOf(1), 0)
        for (i in 0 until largo) a = a.multiplicar(Polinomio(intArrayOf(1, gexp(i)), 0))
        a
    }
}

// ----------------------------------------------------------------- tablas

private class BloqueRs(val total: Int, val datos: Int)

/** QRRSBlock.getRSBlocks para el nivel L. */
private fun bloquesL(version: Int): List<BloqueRs> {
    val t = BLOQUES_L[version - 1]
    val lista = ArrayList<BloqueRs>()
    for (i in 0 until t.size / 3) repeat(t[i * 3]) { lista.add(BloqueRs(t[i * 3 + 1], t[i * 3 + 2])) }
    return lista
}

/** Filas «L» de RS_BLOCK_TABLE: (cuántos bloques, total, datos) por versión. */
private val BLOQUES_L = arrayOf(
    intArrayOf(1, 26, 19), // 1
    intArrayOf(1, 44, 34), // 2
    intArrayOf(1, 70, 55), // 3
    intArrayOf(1, 100, 80), // 4
    intArrayOf(1, 134, 108), // 5
    intArrayOf(2, 86, 68), // 6
    intArrayOf(2, 98, 78), // 7
    intArrayOf(2, 121, 97), // 8
    intArrayOf(2, 146, 116), // 9
    intArrayOf(2, 86, 68, 2, 87, 69), // 10
    intArrayOf(4, 101, 81), // 11
    intArrayOf(2, 116, 92, 2, 117, 93), // 12
    intArrayOf(4, 133, 107), // 13
    intArrayOf(3, 145, 115, 1, 146, 116), // 14
    intArrayOf(5, 109, 87, 1, 110, 88), // 15
    intArrayOf(5, 122, 98, 1, 123, 99), // 16
    intArrayOf(1, 135, 107, 5, 136, 108), // 17
    intArrayOf(5, 150, 120, 1, 151, 121), // 18
    intArrayOf(3, 141, 113, 4, 142, 114), // 19
    intArrayOf(3, 135, 107, 5, 136, 108), // 20
    intArrayOf(4, 144, 116, 4, 145, 117), // 21
    intArrayOf(2, 139, 111, 7, 140, 112), // 22
    intArrayOf(4, 151, 121, 5, 152, 122), // 23
    intArrayOf(6, 147, 117, 4, 148, 118), // 24
    intArrayOf(8, 132, 106, 4, 133, 107), // 25
    intArrayOf(10, 142, 114, 2, 143, 115), // 26
    intArrayOf(8, 152, 122, 4, 153, 123), // 27
    intArrayOf(3, 147, 117, 10, 148, 118), // 28
    intArrayOf(7, 146, 116, 7, 147, 117), // 29
    intArrayOf(5, 145, 115, 10, 146, 116), // 30
    intArrayOf(13, 145, 115, 3, 146, 116), // 31
    intArrayOf(17, 145, 115), // 32
    intArrayOf(17, 145, 115, 1, 146, 116), // 33
    intArrayOf(13, 145, 115, 6, 146, 116), // 34
    intArrayOf(12, 151, 121, 7, 152, 122), // 35
    intArrayOf(6, 151, 121, 14, 152, 122), // 36
    intArrayOf(17, 152, 122, 4, 153, 123), // 37
    intArrayOf(4, 152, 122, 18, 153, 123), // 38
    intArrayOf(20, 147, 117, 4, 148, 118), // 39
    intArrayOf(19, 148, 118, 6, 149, 119), // 40
)

/** PATTERN_POSITION_TABLE: centros de los patrones de alineación por versión. */
private val POSICIONES_ALINEACION = arrayOf(
    intArrayOf(),
    intArrayOf(6, 18),
    intArrayOf(6, 22),
    intArrayOf(6, 26),
    intArrayOf(6, 30),
    intArrayOf(6, 34),
    intArrayOf(6, 22, 38),
    intArrayOf(6, 24, 42),
    intArrayOf(6, 26, 46),
    intArrayOf(6, 28, 50),
    intArrayOf(6, 30, 54),
    intArrayOf(6, 32, 58),
    intArrayOf(6, 34, 62),
    intArrayOf(6, 26, 46, 66),
    intArrayOf(6, 26, 48, 70),
    intArrayOf(6, 26, 50, 74),
    intArrayOf(6, 30, 54, 78),
    intArrayOf(6, 30, 56, 82),
    intArrayOf(6, 30, 58, 86),
    intArrayOf(6, 34, 62, 90),
    intArrayOf(6, 28, 50, 72, 94),
    intArrayOf(6, 26, 50, 74, 98),
    intArrayOf(6, 30, 54, 78, 102),
    intArrayOf(6, 28, 54, 80, 106),
    intArrayOf(6, 32, 58, 84, 110),
    intArrayOf(6, 30, 58, 86, 114),
    intArrayOf(6, 34, 62, 90, 118),
    intArrayOf(6, 26, 50, 74, 98, 122),
    intArrayOf(6, 30, 54, 78, 102, 126),
    intArrayOf(6, 26, 52, 78, 104, 130),
    intArrayOf(6, 30, 56, 82, 108, 134),
    intArrayOf(6, 34, 60, 86, 112, 138),
    intArrayOf(6, 30, 58, 86, 114, 142),
    intArrayOf(6, 34, 62, 90, 118, 146),
    intArrayOf(6, 30, 54, 78, 102, 126, 150),
    intArrayOf(6, 24, 50, 76, 102, 128, 154),
    intArrayOf(6, 28, 54, 80, 106, 132, 158),
    intArrayOf(6, 32, 58, 84, 110, 136, 162),
    intArrayOf(6, 26, 54, 82, 110, 138, 166),
    intArrayOf(6, 30, 58, 86, 114, 142, 170),
)
