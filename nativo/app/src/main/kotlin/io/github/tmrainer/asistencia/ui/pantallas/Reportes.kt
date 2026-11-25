// Los reportes para presentar, en PDF y Excel (decisión del 22-11-2026: ya no tienen
// pantalla propia). El del día (menú, raciones y quiénes recibieron) va en Asistencia;
// el del mes (asistencia por persona, raciones, insumos del B-1) y el gasto propio, en
// Entregas, junto al B-1 del mes. El archivo se arma al tocar y va al menú «Compartir».
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.documentos.etiquetaMes
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.reporteDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.ReporteMes
import io.github.tmrainer.asistencia.nucleo.documentos.ReporteDia
import io.github.tmrainer.asistencia.nucleo.documentos.reporteDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.nucleo.modelo.GastoPropio
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.modelo.uuid
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.BotonesExportar
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.CampoSoles
import io.github.tmrainer.asistencia.ui.componentes.Documento
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «Reporte del día» (en Asistencia): menú, raciones y quiénes recibieron, con línea para firmar. */
@Composable
internal fun ReporteDelDia(ctx: Contexto, modifier: Modifier = Modifier) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val c = colores
    val centro = estado.config.value.ultimoCentro?.nombreCentro?.trim().orEmpty()
    val responsable = ctx.preferencias.perfil.value.nombre
    var fecha by remember { mutableStateOf(hoyIso()) }
    Column(modifier) {
        Pista("Menú, raciones y quiénes recibieron. Con línea para firmar.", Modifier.padding(bottom = Medidas.e3))
        CampoFecha("Día", fecha, { if (it.isNotEmpty()) fecha = it })
        // Fuera del hilo de la pantalla: al volver a esta parte deslizando, se rearmaba aquí.
        val delDia by produceState<ReporteDia?>(null, padron, fecha) { value = withContext(Dispatchers.Default) { reporteDelDia(padron, fecha) } }
        val dia = delDia
        val textoDia = if (dia == null) "Contando ${aDdMmAa(fecha)}…"
        else if (dia.servidas == 0 && dia.menuDelDia.isEmpty()) "Ese día no tiene nada anotado."
        else "${aDdMmAa(fecha)}: ${cuantos(dia.servidas, "ración servida", "raciones servidas")}" +
            (if (dia.preparadas != null) " de ${dia.preparadas} preparadas" else "") + (if (dia.menuDelDia.isNotEmpty()) ". Se cocinó: ${dia.menuDelDia}." else ".")
        ParaLeer(textoDia)
        Text(textoDia, style = tipos.texto, color = c.tinta, modifier = Modifier.padding(vertical = Medidas.e3))
        BotonesExportar(
            pdf = { Documento(pdfDelDia(estado.padron.value, fecha, centro, responsable, selloAhora()), "Reporte del dia $fecha", "Reporte del día ${aDdMmAa(fecha)}") },
            excel = { Documento(excelDelDia(estado.padron.value, fecha, centro), "Reporte del dia $fecha", "Reporte del día ${aDdMmAa(fecha)}") }
        )
    }
}

/** «Reporte del mes» [mes] («2026-11», en Entregas): asistencia por persona, raciones e insumos del B-1. */
@Composable
internal fun ReporteDelMes(ctx: Contexto, mes: String, modifier: Modifier = Modifier) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val rendiciones by estado.rendiciones.collectAsStateWithLifecycle()
    val c = colores
    val centro = estado.config.value.ultimoCentro?.nombreCentro?.trim().orEmpty()
    val responsable = ctx.preferencias.perfil.value.nombre
    // Se cuenta aparte, fuera del hilo de la pantalla: con muchos días y personas tardaba.
    val delMes by produceState<ReporteMes?>(null, padron, rendiciones, mes) { value = withContext(Dispatchers.Default) { reporteDelMes(padron, rendiciones, mes) } }
    Column(modifier) {
        val d = delMes
        val textoMes = if (d == null) "Contando ${etiquetaMes(mes)}…"
            else "${d.etiqueta}: ${cuantos(d.dias.size, "día")} con atención, ${cuantos(d.servidas, "ración servida", "raciones servidas")}, " +
                "${cuantos(d.personas.count { it.dias > 0 }, "persona")} que vinieron y ${cuantos(d.insumos.size, "compra")} del B-1."
        ParaLeer(textoMes)
        Text(textoMes, style = tipos.texto, color = c.tinta, modifier = Modifier.padding(bottom = Medidas.e3))
        BotonesExportar(
            pdf = { Documento(pdfDelMes(estado.padron.value, estado.rendiciones.value, mes, centro, responsable, selloAhora()), "Reporte del mes $mes", "Reporte del mes ${etiquetaMes(mes)}") },
            excel = { Documento(excelDelMes(estado.padron.value, estado.rendiciones.value, mes, centro), "Reporte del mes $mes", "Reporte del mes ${etiquetaMes(mes)}") }
        )
    }
}

/**
 * Lo comprado con el dinero de las señoras en el mes [mes]: no va en el B-1,
 * sale en el reporte del mes. Se guarda con el B-1 en curso (gastosPropios).
 */
@Composable
internal fun GastoPropioDelMes(ctx: Contexto, rendiciones: List<Rendicion>, mes: String, hoy: String) {
    val estado = ctx.estado
    val avisos = LocalAvisos.current
    val c = colores
    Leyenda("Gasto propio")
    Pista("Lo que compraron con su propio dinero, como azúcar extra. No va en el B-1: sale en el reporte del mes.", Modifier.padding(bottom = Medidas.e3))
    val gastos = rendiciones.flatMap { it.gastosPropios }.filter { it.fecha.take(7) == mes || it.fecha.isEmpty() }
    fun cambiarGasto(id: String, cambio: (GastoPropio) -> GastoPropio) = estado.cambiarRendiciones { rs ->
        rs.map { r -> if (r.gastosPropios.any { it.id == id }) r.copy(gastosPropios = r.gastosPropios.map { if (it.id == id) cambio(it) else it }) else r }
    }
    if (gastos.isNotEmpty()) {
        Superficie {
            gastos.forEachIndexed { i, g ->
                Column(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3)) {
                    CampoFecha("Fecha", g.fecha, { v -> cambiarGasto(g.id) { it.copy(fecha = v) } })
                    Spacer(Modifier.height(Medidas.e2))
                    Campo("Qué compraron", g.descripcion, { v -> cambiarGasto(g.id) { it.copy(descripcion = v) } })
                    Spacer(Modifier.height(Medidas.e2))
                    CampoSoles("Cuánto (S/)", g.montoCent, { v -> cambiarGasto(g.id) { it.copy(montoCent = v) } })
                    Boton("Quitar este gasto", {
                        val antes = estado.rendiciones.value
                        estado.cambiarRendiciones { rs -> rs.map { r -> r.copy(gastosPropios = r.gastosPropios.filter { it.id != g.id }) } }
                        avisos.mostrar("Quitaste el gasto propio") { estado.cambiarRendiciones { antes } }
                    }, Modifier.padding(top = Medidas.e2), diminuto = true, tipo = TipoBoton.PELIGRO)
                }
                if (i < gastos.size - 1) Separador()
            }
        }
        Text("Gasto propio de ${etiquetaMes(mes)}: S/ ${formatearSoles(gastos.mapNotNull { it.montoCent }.sum())}.", style = tipos.chicoFuerte, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
    }
    Boton("Anotar un gasto propio", {
        // Con la fecha del mes elegido: si es el de hoy, hoy.
        val g = GastoPropio(id = uuid(), fecha = if (hoy.take(7) == mes) hoy else "$mes-01")
        estado.cambiarRendiciones { rs -> if (rs.isEmpty()) listOf(nuevaRendicion().copy(gastosPropios = listOf(g))) else listOf(rs[0].copy(gastosPropios = rs[0].gastosPropios + g)) + rs.drop(1) }
    }, Modifier.padding(top = Medidas.e3).objetivoGuia("entregas.propio"), icono = "mas")
}
