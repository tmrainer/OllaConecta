// DocumentosOraculoTest.kt — los Excel y los PDF que arma Kotlin son los
// MISMOS bytes que arma la web con el respaldo ficticio y la misma fecha.
// nativo/herramientas/oraculo-documentos.mjs escribe lo esperado en
// src/test/resources/documentos/:
//
//   node nativo/herramientas/oraculo-documentos.mjs
//
// respaldo-documentos.json (lo escribe el mismo script) trae textos largos y
// raros, para pasar por los caminos que el ficticio no toca: letra más chica,
// dos renglones, corte con «…», letras fuera de WinAnsi.
//
// Los .tsv son lo que leerXlsx() de la web (en Chromium) lee de cada .xlsx:
// los de la web, el padrón de ejemplo de herramientas/ y uno comprimido como
// los que guarda Excel.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.documentos.excelDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelPadron
import io.github.tmrainer.asistencia.nucleo.documentos.hojasDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.leerXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelPadron
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertIs
import kotlin.test.fail

// Lo mismo que usa oraculo-documentos.mjs.
private const val GENERADO = "20-10-26 18:40"
private const val HOY = "2026-10-20"

private fun binario(nombre: String): ByteArray =
    DocumentosOraculoTest::class.java.getResource("/documentos/$nombre")?.readBytes()
        ?: error("Falta documentos/$nombre: corre node nativo/herramientas/oraculo-documentos.mjs")

class DocumentosOraculoTest {

    /** Los documentos que arma Kotlin, con los mismos nombres que los de la web. */
    private fun documentos(): Map<String, ByteArray> {
        val lectura = assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-ficticio.json")))
        val r = lectura.respaldo
        val padron = r.padron!!
        val centro = r.config!!.ultimoCentro!!.nombreCentro
        val docs = linkedMapOf(
            "padron.xlsx" to excelDelPadron(padron, fechaRef = HOY, inicio = "2026-10-01", fin = HOY, centro = centro),
            "padron.pdf" to pdfDelPadron(padron, fechaRef = HOY, inicio = "2026-10-01", fin = HOY, centro = centro, generado = GENERADO),
            "padron-vacio.xlsx" to excelDelPadron(Padron(), fechaRef = HOY, inicio = "2026-10-01", fin = HOY),
            "padron-vacio.pdf" to pdfDelPadron(Padron(), fechaRef = HOY, inicio = "2026-10-01", fin = HOY, generado = GENERADO),
            "asistencia-setiembre.xlsx" to excelDeAsistencia(padron, inicio = "2026-09-01", fin = "2026-09-30", centro = centro),
            "asistencia-setiembre.pdf" to pdfDeAsistencia(padron, inicio = "2026-09-01", fin = "2026-09-30", centro = centro, generado = GENERADO),
            "asistencia-todo.xlsx" to excelDeAsistencia(padron, inicio = "2026-08-01", fin = HOY),
            "asistencia-todo.pdf" to pdfDeAsistencia(padron, inicio = "2026-08-01", fin = HOY, generado = GENERADO),
            "asistencia-vacia.pdf" to pdfDeAsistencia(padron, inicio = "2025-01-01", fin = "2025-01-31", generado = GENERADO),
            "b1-vacio.xlsx" to excelDelB1(nuevaRendicion()),
            "b1-vacio.pdf" to pdfDelB1(hojasDelB1(null), generado = GENERADO)
        )
        r.rendiciones.forEachIndexed { i, rendicion ->
            docs["b1-${i + 1}.xlsx"] = excelDelB1(rendicion)
            docs["b1-${i + 1}.pdf"] = pdfDelB1(hojasDelB1(rendicion), generado = GENERADO, titulo = "Formato B-1 ${rendicion.periodo.etiqueta}")
        }
        // Textos que no entran en su caja, letras fuera de WinAnsi, cantidades con decimales.
        val largo = assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-documentos.json"))).respaldo
        val rLargo = largo.rendiciones[0]
        val pLargo = largo.padron!!
        docs["largo-b1.pdf"] = pdfDelB1(hojasDelB1(rLargo), generado = GENERADO, titulo = "B-1 (largo) \\ —")
        docs["largo-b1.xlsx"] = excelDelB1(rLargo)
        docs["largo-padron.pdf"] = pdfDelPadron(pLargo, fechaRef = HOY, inicio = "2026-10-01", fin = HOY, centro = rLargo.nombreCentro, generado = GENERADO)
        docs["largo-padron.xlsx"] = excelDelPadron(pLargo, fechaRef = HOY, inicio = "2026-10-01", fin = HOY, centro = rLargo.nombreCentro)
        docs["largo-asistencia.pdf"] = pdfDeAsistencia(pLargo, inicio = "2026-10-01", fin = HOY, centro = rLargo.nombreCentro, generado = GENERADO)
        docs["largo-asistencia.xlsx"] = excelDeAsistencia(pLargo, inicio = "2026-10-01", fin = HOY, centro = rLargo.nombreCentro)
        return docs
    }

    @TestFactory
    fun `Los Excel y los PDF son los mismos bytes que los de la web`(): List<DynamicTest> =
        documentos().map { (nombre, obtenido) -> DynamicTest.dynamicTest(nombre) { compararBytes(nombre, binario(nombre), obtenido) } }

    @TestFactory
    fun `leerXlsx lee lo mismo que la web`(): List<DynamicTest> =
        listOf("padron", "padron-vacio", "asistencia-setiembre", "asistencia-todo", "b1-1", "b1-2", "b1-vacio",
            "largo-b1", "largo-padron", "largo-asistencia", "padron-de-ejemplo", "leer-variado").map { n ->
            DynamicTest.dynamicTest("$n.xlsx") {
                val esperado = String(binario("$n.tsv"), Charsets.UTF_8)
                igual(leerXlsx(binario("$n.xlsx")), esperado, "$n.xlsx leído distinto que en la web")
            }
        }
}

/** Igual byte a byte; si no, dice dónde y muestra el texto de alrededor (un Char por byte). */
private fun compararBytes(nombre: String, esperado: ByteArray, obtenido: ByteArray) {
    if (esperado.contentEquals(obtenido)) return
    val i = (0 until minOf(esperado.size, obtenido.size)).firstOrNull { esperado[it] != obtenido[it] } ?: minOf(esperado.size, obtenido.size)
    fun alrededor(b: ByteArray) = String(b, maxOf(0, i - 80), minOf(b.size, i + 80) - maxOf(0, i - 80), Charsets.ISO_8859_1)
    fail("$nombre: distinto de la web en el byte $i (largo web ${esperado.size}, Kotlin ${obtenido.size})\n" +
        "web:    ${alrededor(esperado)}\nKotlin: ${alrededor(obtenido)}")
}
