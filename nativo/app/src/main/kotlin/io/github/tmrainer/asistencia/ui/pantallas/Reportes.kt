// Los reportes para presentar: el
// del día (menú, raciones y quiénes recibieron) y el del mes (asistencia por
// persona, raciones, insumos del B-1 y, aparte, el gasto propio), en PDF y
// Excel. El archivo se arma al tocar y va al menú «Compartir» del teléfono.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.documentos.etiquetaMes
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.reporteDelDia
import io.github.tmrainer.asistencia.nucleo.documentos.reporteDelMes
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.componentes.BotonesExportar
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.Documento
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

@Composable
fun PantallaReportes(ctx: Contexto) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val rendiciones by estado.rendiciones.collectAsStateWithLifecycle()
    val c = colores
    val hoy = hoyIso()
    val centro = estado.config.value.ultimoCentro?.nombreCentro?.trim().orEmpty()
    val responsable = ctx.preferencias.perfil.value.nombre
    var fecha by remember { mutableStateOf(hoy) }
    // Los meses con algo anotado, del más nuevo al más viejo; siempre el de hoy.
    val meses = remember(padron, rendiciones) {
        (listOf(hoy.take(7)) + padron.atenciones.map { it.fecha.take(7) } + rendiciones.flatMap { r -> r.egresos.map { it.fechaCompra.take(7) } })
            .filter { Regex("^\\d{4}-\\d{2}$").matches(it) }.distinct().sortedDescending().take(6)
    }
    var mes by remember { mutableStateOf(hoy.take(7)) }

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Reportes", "Para presentar, en PDF o Excel")
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            TituloSeccion("Reporte del día")
            Pista("Menú, raciones y quiénes recibieron. Con línea para firmar.", Modifier.padding(bottom = Medidas.e3))
            CampoFecha("Día", fecha, { if (it.isNotEmpty()) fecha = it })
            val dia = remember(padron, fecha) { reporteDelDia(padron, fecha) }
            val textoDia = if (dia.servidas == 0 && dia.menuDelDia.isEmpty()) "Ese día no tiene nada anotado."
            else "${aDdMmAa(fecha)}: ${cuantos(dia.servidas, "ración servida", "raciones servidas")}" +
                (if (dia.preparadas != null) " de ${dia.preparadas} preparadas" else "") + (if (dia.menuDelDia.isNotEmpty()) ". Se cocinó: ${dia.menuDelDia}." else ".")
            ParaLeer(textoDia)
            Text(textoDia, style = tipos.texto, color = c.tinta, modifier = Modifier.padding(vertical = Medidas.e3))
            BotonesExportar(
                pdf = { Documento(pdfDelDia(estado.padron.value, fecha, centro, responsable, selloAhora()), "Reporte del dia $fecha", "Reporte del día ${aDdMmAa(fecha)}") },
                excel = { Documento(excelDelDia(estado.padron.value, fecha, centro), "Reporte del dia $fecha", "Reporte del día ${aDdMmAa(fecha)}") }
            )

            Spacer(Modifier.height(Medidas.e6))
            TituloSeccion("Reporte del mes")
            Pista("Asistencia por persona, raciones del mes e insumos del B-1. El gasto propio va aparte.", Modifier.padding(bottom = Medidas.e3))
            Opciones(meses.map { it to etiquetaMes(it) }, { it == mes }, { mes = it })
            val delMes = remember(padron, rendiciones, mes) { reporteDelMes(padron, rendiciones, mes) }
            val textoMes = "${delMes.etiqueta}: ${cuantos(delMes.dias.size, "día")} con atención, ${cuantos(delMes.servidas, "ración servida", "raciones servidas")}, " +
                "${cuantos(delMes.personas.count { it.dias > 0 }, "persona")} que vinieron y ${cuantos(delMes.insumos.size, "compra")} del B-1."
            ParaLeer(textoMes)
            Text(textoMes, style = tipos.texto, color = c.tinta, modifier = Modifier.padding(vertical = Medidas.e3))
            BotonesExportar(
                pdf = { Documento(pdfDelMes(estado.padron.value, estado.rendiciones.value, mes, centro, responsable, selloAhora()), "Reporte del mes $mes", "Reporte del mes ${etiquetaMes(mes)}") },
                excel = { Documento(excelDelMes(estado.padron.value, estado.rendiciones.value, mes, centro), "Reporte del mes $mes", "Reporte del mes ${etiquetaMes(mes)}") }
            )
        }
    }
}
