// Grupo «Hoja oficial del B-1» de web/js/casos-prueba.js, más el dibujo de la
// hoja como datos (LienzoTrazos) para la vista previa en pantalla.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.documentos.FILAS_EGRESOS
import io.github.tmrainer.asistencia.nucleo.documentos.FilaEgreso
import io.github.tmrainer.asistencia.nucleo.documentos.LienzoTrazos
import io.github.tmrainer.asistencia.nucleo.documentos.PLANTILLA_B1
import io.github.tmrainer.asistencia.nucleo.documentos.Trazo
import io.github.tmrainer.asistencia.nucleo.documentos.anchoMm
import io.github.tmrainer.asistencia.nucleo.documentos.dibujarFormato
import io.github.tmrainer.asistencia.nucleo.documentos.dibujarHoja
import io.github.tmrainer.asistencia.nucleo.documentos.hojasDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.lienzo
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelB1
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import kotlin.test.Test

/** Los bytes de un PDF como texto, un Char por byte (como en la web). */
fun comoTexto(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

class HojaB1Test {

    @Test
    fun `Los datos se escriben como en el papel (DD-MM-AA, soles, varios, una X)`() {
        val h = hojasDelB1(rendicionDelEjemplar())[0]
        igual(h.campos["marcaOllaComun"], "X")
        igual(h.campos["marcaComedor"], "", "el otro recuadro queda vacío")
        igual(h.campos["fechaRendicion"], "14-09-26")
        igual(h.campos["montoSubsidio"], "200.00", "sin «S/»: ya está impreso")
        igual(h.filas.size, 2)
        igual(h.filas[0].boleta, "E001-143")
        igual(h.filas[0].cantidad, "1", "como en la web: 1, no 1.0")
        igual(h.filas[1].cantidad, "varios")
        igual(h.filas[1].precioUnitario, "varios")
        igual(h.filas[1].montoTotal, "141.85")
        igual(h.campos["totalConSubsidio"], "200.00")
        igual(h.campos["totalAporte"], "9.85")
        igual(h.campos["totalRendicion"], "209.85")
    }

    @Test
    fun `Aporte en cero - renglón vacío, compra vacía - no ocupa renglón`() {
        val r = rendicionDelEjemplar().let { it.copy(egresos = listOf(it.egresos[0], nuevoEgreso())) }
        val h = hojasDelB1(r)[0]
        igual(h.filas.size, 1, "la ficha vacía no se imprime")
        igual(h.campos["totalAporte"], "", "«registrar de corresponder»")
        igual(h.campos["totalRendicion"], "68.00")
    }

    @Test
    fun `Más compras que renglones - otra hoja, encabezado repetido y totales solo en la última`() {
        val r = rendicionDelEjemplar().copy(egresos = List(FILAS_EGRESOS + 3) {
            nuevoEgreso().copy(fechaCompra = "2026-09-10", descripcion = "Arroz", cantidad = 1.0, unidadMedida = "Kg", montoTotalCent = 100)
        })
        val hojas = hojasDelB1(r)
        igual(hojas.size, 2)
        igual(hojas[0].filas.size, FILAS_EGRESOS)
        igual(hojas[1].filas.size, 3)
        igual(hojas[1].campos["nombreCentro"], "Centro de prueba", "encabezado repetido")
        ok("totalRendicion" !in hojas[0].campos, "la primera hoja no lleva totales")
        igual(hojas[1].campos["totalRendicion"], "20.00")
    }

    @Test
    fun `Cada dato de la hoja tiene su lugar en la plantilla, dentro de la hoja A4`() {
        val h = hojasDelB1(rendicionDelEjemplar())[0]
        for (k in h.campos.keys) ok(k in PLANTILLA_B1.campos, "falta la posición de $k")
        for (k in FilaEgreso.CLAVES) ok(k in PLANTILLA_B1.egresos.columnas, "falta la columna $k")
        for ((k, c) in PLANTILLA_B1.campos) {
            ok(c.xMm >= 0 && c.xMm + c.anchoMm <= 210 && c.yMm >= 0 && c.yMm + c.altoMm <= 297, "$k se sale de la hoja")
        }
        val e = PLANTILLA_B1.egresos
        ok(e.primeraFilaYMm + e.filas * e.altoFilaMm < PLANTILLA_B1.campos.getValue("totalConSubsidio").yMm + 0.5, "las filas pisan los totales")
    }

    @Test
    fun `El PDF de referencia es un PDF válido, con una página por hoja y los datos adentro`() {
        val r = rendicionDelEjemplar().let { it.copy(egresos = List(FILAS_EGRESOS + 1) { _ -> it.egresos[1] }) }
        val texto = comoTexto(pdfDelB1(hojasDelB1(r), generado = "14-10-26 19:05"))
        ok(texto.startsWith("%PDF-1.4"), "empieza como PDF")
        ok(texto.trimEnd().endsWith("%%EOF"), "termina como PDF")
        igual(Regex("/Type /Page ").findAll(texto).count(), 2, "dos hojas, dos páginas")
        ok(texto.contains("(141.85)"), "lleva el monto de la compra")
        ok(texto.contains("(2553.30)"), "lleva el total (18 × 141.85)")
        ok(texto.contains("COPIA DE REFERENCIA"), "avisa que es una copia de referencia")
        val xref = Regex("startxref\n(\\d+)").find(texto)!!.groupValues[1].toInt()
        ok(texto.substring(xref, xref + 4) == "xref", "la tabla xref está donde dice")
        ok(anchoMm("MMMM", 10.0) > anchoMm("iiii", 10.0), "mide el ancho de las letras")
    }

    // ------------------------------------------- la hoja como datos (pantalla)

    @Test
    fun `La hoja dibujada como datos es la misma del PDF, dentro de la A4`() {
        val h = hojasDelB1(rendicionDelEjemplar())[0]
        val pantalla = LienzoTrazos()
        dibujarHoja(pantalla, h, generado = "14-10-26 19:05")
        val pdf = lienzo()
        dibujarHoja(pdf, h, generado = "14-10-26 19:05")
        // Los operadores que dibujan (l S, re S, re f, Tj ET); los demás cambian el gris, el grosor o la raya.
        val dibujos = pdf.ops.count { it.endsWith(" S") || it.endsWith(" f") || it.endsWith(" ET") }
        igual(pantalla.trazos.size, dibujos, "un trazo por cada línea, recuadro, relleno y texto del PDF")
        val textos = pantalla.trazos.filterIsInstance<Trazo.Texto>()
        ok(textos.any { it.texto == "141.85" && it.gris == 0.0 }, "los datos, en negro")
        ok(textos.any { it.texto == "NOTA:" && it.negrita && it.gris == 0.45 }, "los rótulos, en gris")
        ok(pantalla.trazos.filterIsInstance<Trazo.Linea>().any { it.punteada }, "las líneas punteadas del encabezado")
        for (t in pantalla.trazos) {
            val (x, y) = when (t) {
                is Trazo.Linea -> t.x2 to t.y2
                is Trazo.Caja -> t.xMm + t.anchoMm to t.yMm + t.altoMm
                is Trazo.Relleno -> t.xMm + t.anchoMm to t.yMm + t.altoMm
                is Trazo.Texto -> t.xMm + anchoMm(t.texto, t.pt, t.negrita) to t.yBaseMm
            }
            ok(x in 0.0..210.0 && y in 0.0..297.0, "$t se sale de la hoja")
        }
        val soloFormato = LienzoTrazos().also { dibujarFormato(it) }.trazos
        ok(soloFormato.filterIsInstance<Trazo.Texto>().none { it.texto == "141.85" }, "el formato solo, sin datos")
    }
}
