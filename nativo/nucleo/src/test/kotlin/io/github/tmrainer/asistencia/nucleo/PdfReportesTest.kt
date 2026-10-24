// Grupo «PDF del padrón y la asistencia» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.documentos.pdfDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelPadron
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.PreciosMenu
import io.github.tmrainer.asistencia.nucleo.modelo.aIso
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import java.time.LocalDate
import kotlin.test.Test

class PdfReportesTest {

    @Test
    fun `Padrón y asistencia en PDF - páginas, totales y planilla horizontal solo hasta 31 días`() {
        fun paginas(t: String) = Regex("/Type /Page ").findAll(t).count()
        fun apaisadas(t: String) = Regex("MediaBox \\[0 0 841\\.89 595\\.28]").findAll(t).count()
        val afiliados = List(45) { i ->
            nuevoAfiliado().copy(apellidoPaterno = "PRUEBA", nombres = "PERSONA $i", numeroDocumento = (10000000 + i).toString(),
                tipoAfiliado = if (i % 2 == 1) "habitual" else "caso_social", grupoEtarioManual = "adulto", altaEn = "2026-01-01")
        }
        val padron = Padron(afiliados = afiliados, atenciones = (1..5).map { d ->
            nuevaAtencion(PreciosMenu(300, 0)).copy(fecha = "2026-09-0$d",
                asistencias = afiliados.take(10 + d).map { x ->
                    Asistencia(afiliadoId = x.id, tipoMenu = if (x.tipoAfiliado == "caso_social") "ayuda_social" else "normal")
                })
        })
        val p = comoTexto(pdfDelPadron(padron, fechaRef = "2026-09-30", inicio = "2026-09-01", fin = "2026-09-30", generado = "x"))
        ok(p.startsWith("%PDF-1.4") && p.trimEnd().endsWith("%%EOF"), "es un PDF")
        igual(paginas(p), 2, "45 personas: dos hojas")
        ok(p.contains("(Total: 45 personas)"), "con el total")
        ok(p.contains("COPIA DE REFERENCIA"), "dice que es copia de referencia")
        val asis = comoTexto(pdfDeAsistencia(padron, inicio = "2026-09-01", fin = "2026-09-30", generado = "x"))
        igual(apaisadas(asis), 2, "planilla por persona: 45 personas y el total, en dos hojas horizontales")
        ok(asis.contains("(65)"), "total de raciones: 11+12+13+14+15")
        val largo = padron.copy(atenciones = List(40) { k ->
            nuevaAtencion(null).copy(fecha = aIso(LocalDate.of(2026, 8, 1).plusDays(k.toLong())), asistencias = emptyList())
        })
        igual(apaisadas(comoTexto(pdfDeAsistencia(largo, inicio = "2026-08-01", fin = "2026-09-30", generado = "x"))), 0,
            "más de 31 días: sin planilla por persona")
        igual(paginas(comoTexto(pdfDelPadron(Padron(), fechaRef = "2026-09-30", inicio = "2026-09-01", fin = "2026-09-30", generado = "x"))), 1,
            "vacío: una hoja")
    }
}
