// Inicio.kt — la pantalla de Inicio (pagina-inicio.js): el día de un vistazo,
// la próxima entrega, las tareas («Pasar lista hoy», «Anotar una compra»…), la
// copia de los datos y «Más». También la bienvenida de la primera vez.
package io.github.tmrainer.asistencia.ui.pantallas

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

@Composable
fun PantallaInicio(ctx: Contexto) {
    val padron by ctx.estado.padron.collectAsStateWithLifecycle()
    val config by ctx.estado.config.collectAsStateWithLifecycle()
    val calendario by ctx.estado.calendario.collectAsStateWithLifecycle()
    val perfilOmitido by ctx.preferencias.perfilOmitido.collectAsStateWithLifecycle()
    val bienvenidaVista by ctx.preferencias.bienvenidaVista.collectAsStateWithLifecycle()
    val c = colores
    val hoy = hoyIso()

    val centro = config.ultimoCentro?.nombreCentro?.trim().orEmpty()
    val hayDatos = padron.afiliados.isNotEmpty()

    // La primera vez, con el padrón vacío, se explica cómo se usa.
    LaunchedEffect(bienvenidaVista, hayDatos) { if (!bienvenidaVista && !hayDatos) ctx.abrirBienvenida() }

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, if (centro.isNotEmpty()) centro else "OllaConecta", fechaLarga(hoy), conVolver = false)
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
            Column(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(Medidas.rL)).clip(RoundedCornerShape(Medidas.rL)).background(c.marca).padding(Medidas.e5, Medidas.e4)) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                Tarea("personaMas", "Inscribir a una persona", c.superficie2, c.acentoTexto, Modifier.weight(1f)) { ctx.ir(Ruta.INSCRIBIR) }
                Tarea("boleta", "Anotar una compra", c.avisoFondo, c.aviso, Modifier.weight(1f)) { ctx.ir(Ruta.B1, "compras") }
            }
            Spacer(Modifier.height(Medidas.e3))
            Tarea("calendario", "Ver entregas", c.azulFondo, c.azul, Modifier.fillMaxWidth()) { ctx.ir(Ruta.ENTREGAS) }

            // ---- la copia de los datos ----
            val dias = ctx.estado.diasDesdeUltimaCopia()
            Spacer(Modifier.height(Medidas.e4))
            if (dias != null && dias < 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icono("escudo", 22.dp, c.ok)
                    Spacer(Modifier.width(10.dp))
                    val nota = if (dias == 0) "Copia de tus datos guardada hoy" else "Copia de tus datos guardada hace ${cuantos(dias, "día")}"
                    ParaLeer(nota)
                    Text(nota, style = tipos.pista, color = c.tintaSuave)
                }
            } else if (hayDatos) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.avisoFondo).padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.CenterVertically) {
                    Icono("escudo", 22.dp, c.aviso)
                    Spacer(Modifier.width(10.dp))
                    val nota = if (dias == null) "Todavía no guardaste una copia de tus datos" else "Tu última copia es de hace $dias días"
                    ParaLeer(nota)
                    Text(nota, style = tipos.pista, color = c.tinta, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Medidas.e2))
                    Boton("Guardar una copia", { ctx.ir(Ruta.DATOS) }, diminuto = true)
                }
            }

            // ---- quién usa este teléfono ----
            if (!ctx.preferencias.perfilCompleto() && !perfilOmitido) {
                Spacer(Modifier.height(Medidas.e3))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie).padding(Medidas.e4, Medidas.e3)) {
                    Text(buildString { append("¿Quién usa este teléfono? ") }, style = tipos.textoNegrita, color = c.tinta)
                    Text("Con tu nombre y tu rol, la app te propone tus datos en el B-1.", style = tipos.texto, color = c.tinta, modifier = Modifier.padding(bottom = Medidas.e2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                        Boton("Poner mis datos", { ctx.ir(Ruta.AJUSTES) }, tipo = TipoBoton.PRINCIPAL, diminuto = true)
                        Boton("Ahora no", { ctx.preferencias.omitirPerfil() }, diminuto = true)
                    }
                }
            }

            // ---- Más: lo mismo que el menú ⋮, con una línea de qué hay ----
            Spacer(Modifier.height(Medidas.e5))
            Text("Más", style = tipos.textoFuerte, color = c.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e1))
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                EnlaceMas("escudo", "Tus datos", "Guardar una copia y pasar datos a otro teléfono") { ctx.ir(Ruta.DATOS) }
                EnlaceMas("ajustes", "Ajustes", "Tu perfil, tamaño de letra y modo oscuro") { ctx.ir(Ruta.AJUSTES) }
                EnlaceMas("ayuda", "¿Cómo se usa?", "Un recorrido corto por la aplicación") { ctx.abrirBienvenida() }
            }
        }
    }
}

@Composable
private fun Tarea(icono: String, texto: String, fondoIcono: Color, colorIcono: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = colores
    Column(
        modifier.shadow(1.dp, RoundedCornerShape(Medidas.rL)).clip(RoundedCornerShape(Medidas.rL)).background(c.superficie)
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

private val PASOS_BIENVENIDA = listOf(
    Triple("Inscribe a las personas", "En Padrón anotas a cada persona una sola vez: nombre, DNI y su grupo de edad.", "personas"),
    Triple("Toca a quien vino", "Cada día, en Asistencia, tocas el nombre de quien llegó. La app cuenta las raciones sola.", "lista"),
    Triple("Anota tus compras", "En el Formato B-1 anotas cada boleta. La app suma y te dice qué falta antes de entregar.", "boleta"),
    Triple("Te avisa las entregas", "En Entregas pones cada cuánto entregas el B-1, y la app te avisa unos días antes.", "calendario"),
    Triple("Guarda una copia", "Tus datos están solo en este teléfono. Guarda una copia cada dos semanas, en Tus datos (menú ⋮).", "escudo")
)

/** Primer uso: ¿cómo se usa? Cinco pasos, «Saltar» y «Siguiente». */
@Composable
fun Bienvenida(ctx: Contexto, alCerrar: () -> Unit) {
    var paso by remember { mutableIntStateOf(0) }
    val c = colores
    val terminar = { ctx.preferencias.marcarBienvenidaVista(); alCerrar() }
    val (titulo, texto, dibujo) = PASOS_BIENVENIDA[paso]
    AlertDialog(
        onDismissRequest = terminar,
        containerColor = c.superficie,
        shape = RoundedCornerShape(Medidas.rL),
        title = null,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(c.superficie2), contentAlignment = Alignment.Center) { Icono(dibujo, 64.dp, c.acentoTexto) }
                Row(Modifier.padding(top = Medidas.e4), horizontalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                    PASOS_BIENVENIDA.forEachIndexed { i, _ ->
                        Box(Modifier.width(if (i == paso) 26.dp else 10.dp).height(10.dp).clip(CircleShape).background(if (i == paso) c.acento else c.seleccion))
                    }
                }
                Text(titulo, style = tipos.titulo.copy(fontSize = tipos.titulo.fontSize * 1.15f), color = c.titulo, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Medidas.e4, bottom = Medidas.e2))
                Text(texto, style = tipos.texto, color = c.tintaSuave, textAlign = TextAlign.Center)
                Text("Paso ${paso + 1} de ${PASOS_BIENVENIDA.size}", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e3))
                Boton("Escuchar", { ctx.decir("$titulo. $texto") }, Modifier.padding(top = Medidas.e3), diminuto = true, icono = "voz")
            }
        },
        confirmButton = {
            Boton(if (paso == PASOS_BIENVENIDA.size - 1) "Empezar" else "Siguiente", { if (paso == PASOS_BIENVENIDA.size - 1) terminar() else paso += 1 }, tipo = TipoBoton.PRINCIPAL)
        },
        dismissButton = { Boton("Saltar", terminar) }
    )
}
