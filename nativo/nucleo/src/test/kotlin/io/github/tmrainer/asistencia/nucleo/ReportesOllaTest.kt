// ReportesOllaTest.kt — los reportes del día y del mes: cuadran con lo que ya
// calcula la app (raciones, asistencia por persona), no inventan lo que no se
// anotó, y el gasto propio queda aparte de los insumos del B-1.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.asistenciaPorAfiliado
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEconomicoRaciones
import io.github.tmrainer.asistencia.nucleo.documentos.diasDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.etiquetaMes
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.excelDePendientes
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.leerXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.reporteDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.reporteDelMes
import io.github.tmrainer.asistencia.nucleo.modelo.GastoPropio
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class ReportesOllaTest {

    private val respaldo by lazy { assertIs<LecturaRespaldo.Lista>(leerRespaldo(recurso("respaldo-ficticio.json"))).respaldo }
    private val padron: Padron get() = respaldo.padron!!
    private val rendiciones: List<Rendicion> get() = respaldo.rendiciones!!

    @Test
    fun `Meses y etiquetas`() {
        igual(diasDelMes("2026-02"), "2026-02-01" to "2026-02-28")
        igual(diasDelMes("2024-02"), "2024-02-01" to "2024-02-29")
        igual(etiquetaMes("2026-09"), "Setiembre 2026")
    }

    @Test
    fun `El reporte del día cuadra con la lista y no inventa el menú`() {
        val dia = padron.atenciones.first { desgloseDelDia(it).total > 0 }
        val r = reporteDelDia(padron, dia.fecha)
        igual(r.servidas, desgloseDelDia(dia).total)
        igual(r.asistentes.size, dia.asistencias.size)
        igual(r.menuDelDia, "", "el ficticio no anotó menú")
        igual(r.preparadas, null)
        // Con menú y preparadas anotadas.
        val conMenu = padron.copy(atenciones = padron.atenciones.map { if (it.id == dia.id) it.copy(menuDelDia = " Arroz con pollo ", racionesPreparadas = 40) else it })
        val r2 = reporteDelDia(conMenu, dia.fecha)
        igual(r2.menuDelDia, "Arroz con pollo"); igual(r2.preparadas, 40)
        val texto = leerXlsx(excelDelDia(conMenu, dia.fecha, "Olla ficticia"))
        ok("Arroz con pollo" in texto && "40" in texto, texto.take(300))
        ok(String(pdfDelDia(conMenu, dia.fecha, "Olla ficticia", "Rosa Quispe", "24-10-26 10:00"), Charsets.ISO_8859_1).startsWith("%PDF"))
    }

    @Test
    fun `El reporte del mes cuadra con las raciones y la asistencia por persona`() {
        val mes = padron.atenciones.map { it.fecha.take(7) }.groupingBy { it }.eachCount().maxBy { it.value }.key
        val (inicio, fin) = diasDelMes(mes)
        val r = reporteDelMes(padron, rendiciones, mes)
        igual(r.servidas, resumenEconomicoRaciones(padron, inicio, fin).racionesTotales)
        val porPersona = asistenciaPorAfiliado(padron, inicio, fin).associate { it.afiliadoId to it.dias }
        // Una marca del ficticio es de alguien que se borró del padrón: cuenta aparte, sin nombre.
        igual(r.personas.sumOf { it.dias } + r.marcasSinPersona, porPersona.values.sum())
        ok(r.personas.map { it.n } == (1..r.personas.size).toList(), "numeradas de corrido")
        igual(r.diasConPreparadas, 0); igual(r.preparadas, 0)
        ok(r.insumos.all { it.fecha in inicio..fin }, "solo compras del mes")
        val leido = leerXlsx(excelDelMes(padron, rendiciones, mes, "Olla ficticia"))
        ok("Reporte del mes" in leido)
        ok(String(pdfDelMes(padron, rendiciones, mes, "Olla ficticia", "Rosa Quispe", "24-10-26 10:00"), Charsets.ISO_8859_1).startsWith("%PDF"))
    }

    @Test
    fun `El gasto propio va aparte y no suma a los insumos del B-1`() {
        val r0 = rendiciones.first()
        val conCompra = r0.egresos.firstOrNull { it.fechaCompra.isNotEmpty() } ?: return
        val mes = conCompra.fechaCompra.take(7)
        val antes = reporteDelMes(padron, rendiciones, mes)
        val conGasto = listOf(r0.copy(gastosPropios = listOf(GastoPropio("g1", "$mes-10", "Azúcar extra", 1250)))) + rendiciones.drop(1)
        val despues = reporteDelMes(padron, conGasto, mes)
        igual(despues.insumosCent, antes.insumosCent)
        igual(despues.gastoPropioCent, 1250L)
        igual(despues.gastosPropios.single().descripcion, "Azúcar extra")
    }

    @Test
    fun `La lista para la Municipalidad trae solo a las pendientes activas`() {
        val ids = padron.afiliados.filter { it.activo }.take(2).map { it.id }
        val baja = padron.afiliados.first { !it.activo || it.id !in ids }.id
        val p = padron.copy(afiliados = padron.afiliados.map {
            when (it.id) { in ids -> it.copy(registroMunicipal = "pendiente"); baja -> it.copy(registroMunicipal = "pendiente", activo = false); else -> it }
        })
        val texto = leerXlsx(excelDePendientes(p, "Olla ficticia"))
        val nombres = p.afiliados.filter { it.id in ids }.map { io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto(it) }
        ok(nombres.all { it in texto }, texto)
        igual(texto.lines().count { Regex("^\\d+\t").containsMatchIn(it) }, 2, "solo las dos pendientes activas")
    }
}
