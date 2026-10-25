// Inicio.kt — la pantalla de Inicio (pagina-inicio.js): el día de un vistazo,
// la próxima entrega, las tareas («Pasar lista hoy», «Anotar una compra»…), la
// copia de los datos y «Más». También la bienvenida de la primera vez.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.alertas.Nivel
import io.github.tmrainer.asistencia.nucleo.alertas.alertaProximaEntrega
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.fechaLarga
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

@Composable
fun PantallaInicio(ctx: Contexto) {
    val padron by ctx.estado.padron.collectAsStateWithLifecycle()
    val config by ctx.estado.config.collectAsStateWithLifecycle()
    val calendario by ctx.estado.calendario.collectAsStateWithLifecycle()
    val perfil by ctx.preferencias.perfil.collectAsStateWithLifecycle()
    val c = colores
    val hoy = hoyIso()

    // El nombre del B-1; mientras no lo tenga, la olla que dijo quien usa el teléfono.
    val centro = config.ultimoCentro?.nombreCentro?.trim().orEmpty().ifEmpty { perfil.olla.trim() }
    val hayDatos = padron.afiliados.isNotEmpty()

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, if (centro.isNotEmpty()) centro else "OllaConecta", fechaLarga(hoy), conVolver = false, conAjustes = true)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            // ---- el día de hoy ----
            val atHoy = padron.atenciones.firstOrNull { it.fecha == hoy }
            val d = desgloseDelDia(atHoy)
            val rec = if (atHoy != null) recaudacionDelDiaCent(atHoy) else 0L
            val alerta = alertaProximaEntrega(calendario, hoy)
            ParaLeer("Hoy: ${cuantos(d.total, "ración", "raciones")}" + if (rec == null) ", falta el precio" else ", S/ ${formatearSoles(rec)} recaudado")
            Column(Modifier.objetivoGuia("inicio.hoy").fillMaxWidth().shadow(1.dp, RoundedCornerShape(Medidas.rL)).clip(RoundedCornerShape(Medidas.rL)).background(c.marca).padding(Medidas.e5, Medidas.e4)) {
                Text("Hoy", style = tipos.pista, color = c.marcaSuave)
                Row(Modifier.padding(top = Medidas.e1, bottom = Medidas.e3), horizontalArrangement = Arrangement.spacedBy(Medidas.e6)) {
                    Column {
                        Text("${d.total}", style = tipos.cifra, color = Color.White)
                        Text("raciones", style = tipos.pista, color = c.marcaSuave)
                    }
                    Column {
                        Text(if (rec == null) "—" else "S/ ${formatearSoles(rec).removeSuffix(".00")}", style = tipos.cifra, color = Color.White)
                        Text(if (rec == null) "falta el precio" else "recaudado", style = tipos.pista, color = c.marcaSuave)
                    }
                }
                val textoEntrega: String
                val fondoEntrega: Color
                val dr = alerta?.diasRestantes
                if (alerta != null && alerta.configurado && dr != null) {
                    val n = kotlin.math.abs(dr)
                    textoEntrega = when {
                        dr < 0 -> "La entrega del B-1 venció hace ${cuantos(n, "día")}"
                        dr == 0 -> "Hoy vence la entrega del B-1"
                        else -> "Faltan ${cuantos(n, "día")} para entregar el B-1"
                    }
                    fondoEntrega = when (alerta.nivel) {
                        Nivel.VENCIDA, Nivel.HOY, Nivel.URGENTE -> Color(0xFF8F2A1C)
                        Nivel.PROXIMA -> Color(0xFF7D5300)
                        Nivel.LEJANA -> Color.White.copy(alpha = .1f)
                    }
                } else {
                    textoEntrega = "Anota cuándo entregas el B-1 y la app te avisa"
                    fondoEntrega = Color.White.copy(alpha = .1f)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(fondoEntrega)
                        .clickable(role = Role.Button) { ctx.ir(Ruta.ENTREGAS) }.defaultMinSize(minHeight = 48.dp).padding(Medidas.e3, 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icono("reloj", 22.dp, Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(textoEntrega, style = tipos.pista, color = Color.White)
                }
            }

            // ---- las tareas: ícono y palabra ----
            Spacer(Modifier.height(Medidas.e4))
            Row(
                Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(Medidas.rL)).clip(RoundedCornerShape(Medidas.rL)).background(c.acento)
                    .clickable(role = Role.Button) { ctx.ir(Ruta.ASISTENCIA) }.defaultMinSize(minHeight = 96.dp).padding(Medidas.e4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(Medidas.rL)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icono("lista", 30.dp, Color.White) }
                Spacer(Modifier.width(Medidas.e4))
                Text("Pasar lista hoy", style = tipos.titulo, color = Color.White)
            }
            Spacer(Modifier.height(Medidas.e3))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                Tarea("personaMas", "Inscribir a una persona", c.superficie2, c.acentoTexto, Modifier.weight(1f)) { ctx.ir(Ruta.INSCRIBIR) }
                Tarea("boleta", "Anotar una compra", c.avisoFondo, c.aviso, Modifier.weight(1f)) { ctx.ir(Ruta.B1, "compras") }
            }
            Spacer(Modifier.height(Medidas.e3))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                Tarea("calendario", "Ver entregas", c.azulFondo, c.azul, Modifier.weight(1f)) { ctx.ir(Ruta.ENTREGAS) }
                Tarea("archivo", "Reportes del día y del mes", c.superficie2, c.acentoTexto, Modifier.weight(1f)) { ctx.ir(Ruta.REPORTES) }
            }

            // ---- la copia de los datos ----
            val dias = ctx.estado.diasDesdeUltimaCopia()
            Spacer(Modifier.height(Medidas.e4))
            if (dias != null && dias < 14) {
                Row(Modifier.objetivoGuia("inicio.datos"), verticalAlignment = Alignment.CenterVertically) {
                    Icono("escudo", 22.dp, c.ok)
                    Spacer(Modifier.width(10.dp))
                    val nota = if (dias == 0) "Copia de tus datos guardada hoy" else "Copia de tus datos guardada hace ${cuantos(dias, "día")}"
                    ParaLeer(nota)
                    Text(nota, style = tipos.pista, color = c.tintaSuave, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Medidas.e2))
                    // «Tus datos» va donde importa: junto a la copia.
                    Boton("Ver tus datos", { ctx.ir(Ruta.DATOS) }, diminuto = true)
                }
            } else if (hayDatos) {
                Row(Modifier.objetivoGuia("inicio.datos").fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.avisoFondo).padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.CenterVertically) {
                    Icono("escudo", 22.dp, c.aviso)
                    Spacer(Modifier.width(10.dp))
                    val nota = if (dias == null) "Todavía no guardaste una copia de tus datos" else "Tu última copia es de hace $dias días"
                    ParaLeer(nota)
                    Text(nota, style = tipos.pista, color = c.tinta, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Medidas.e2))
                    Boton("Guardar una copia", { ctx.ir(Ruta.DATOS) }, diminuto = true)
                }
            } else {
                // Un teléfono sin datos: de aquí se traen los de otro teléfono o de una copia.
                Row(Modifier.objetivoGuia("inicio.datos").fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie).padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.CenterVertically) {
                    Icono("escudo", 22.dp, c.acentoTexto)
                    Spacer(Modifier.width(10.dp))
                    Text("¿Tus datos están en otro teléfono?", style = tipos.pista, color = c.tinta, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Medidas.e2))
                    Boton("Traer mis datos", { ctx.ir(Ruta.DATOS) }, diminuto = true)
                }
            }


        }
    }
}

@Composable
private fun Tarea(icono: String, texto: String, fondoIcono: Color, colorIcono: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = colores
    Column(
        // Llena la altura de la fila: las dos tarjetas de una fila miden lo mismo.
        modifier.fillMaxHeight().shadow(1.dp, RoundedCornerShape(Medidas.rL)).clip(RoundedCornerShape(Medidas.rL)).background(c.superficie)
            .clickable(role = Role.Button, onClick = onClick).defaultMinSize(minHeight = 128.dp).padding(Medidas.e4),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(Medidas.rL)).background(fondoIcono), contentAlignment = Alignment.Center) { Icono(icono, 30.dp, colorIcono) }
        Spacer(Modifier.height(Medidas.e4))
        Text(texto, style = tipos.seccion, color = c.titulo)
    }
}

@Composable
fun EnlaceMas(icono: String, titulo: String, detalle: String, onClick: () -> Unit) {
    val c = colores
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie).clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp).padding(Medidas.e4, Medidas.e3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icono(icono, 24.dp, c.acentoTexto)
        Spacer(Modifier.width(Medidas.e3))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = tipos.textoFuerte, color = c.tinta)
            Text(detalle, style = tipos.pista, color = c.tintaSuave)
        }
        Icono("flecha", 20.dp, c.tintaSuave)
    }
}
