// Entregas.kt — fechas de entrega del B-1 y cuenta regresiva
// (pagina-calendario.js). Lo primero que se ve: cuántos días faltan, grande,
// como en un reloj. Con permiso, el teléfono avisa aunque la app esté
// cerrada (AvisosTelefono.kt); sin él, se ofrece activarlos.
package io.github.tmrainer.asistencia.ui.pantallas

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.AvisosTelefono
import io.github.tmrainer.asistencia.OllaConectaApp
import io.github.tmrainer.asistencia.nucleo.alertas.Nivel
import io.github.tmrainer.asistencia.nucleo.alertas.PERIODICIDADES
import io.github.tmrainer.asistencia.nucleo.alertas.agendaEntregas
import io.github.tmrainer.asistencia.nucleo.alertas.alertaProximaEntrega
import io.github.tmrainer.asistencia.nucleo.modelo.Entrega
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.fechaLarga
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaEntrega
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.validarCalendario
import io.github.tmrainer.asistencia.nucleo.validaciones.validarRendicion
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.EstadoGuardado
import io.github.tmrainer.asistencia.ui.componentes.EstadoVacio
import io.github.tmrainer.asistencia.ui.componentes.FranjaEstado
import io.github.tmrainer.asistencia.ui.componentes.Ficha
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.PanelHallazgos
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.componentes.TonoPastilla
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

private val ETIQUETA_ESTADO = listOf("pendiente" to "Pendiente", "entregada" to "Entregada", "observada" to "Observada", "conforme" to "Conforme")
private val OPCIONES_AVISO = listOf(1 to "1 día antes", 3 to "3 días antes", 7 to "7 días antes", 15 to "15 días antes")

@Composable
fun PantallaEntregas(ctx: Contexto) {
    val estado = ctx.estado
    val calendario by estado.calendario.collectAsStateWithLifecycle()
    val rendiciones by estado.rendiciones.collectAsStateWithLifecycle()
    val guardado by estado.guardado.collectAsStateWithLifecycle()
    val c = colores
    val hoy = hoyIso()
    var msgAviso by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Entregas", "Fechas de entrega del B-1")
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            val avisosAlmacen = estado.avisosAlmacenamiento()
            if (avisosAlmacen.isNotEmpty()) AvisoSistema(avisosAlmacen.joinToString(" "), Modifier.padding(bottom = Medidas.e4))

            // ---- cuenta regresiva ----
            val a = alertaProximaEntrega(calendario, hoy)
            val diasAlerta = a?.diasRestantes
            val fechaAlerta = a?.fechaLimite
            if (a != null && a.configurado && diasAlerta != null && fechaAlerta != null) {
                val dias: Int = diasAlerta
                val largo = when (calendario.periodicidad) { "semanal" -> 7; "quincenal" -> 15; else -> 30 }
                val lleno = if (dias < 0) 1f else (1f - dias.toFloat() / largo).coerceIn(0f, 1f)
                val colorReloj = when (a.nivel) { Nivel.PROXIMA -> c.aviso; Nivel.LEJANA -> c.acentoTexto; else -> c.error }
                Column(
                    Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(Medidas.rL)).clip(RoundedCornerShape(Medidas.rL)).background(c.superficie).padding(Medidas.e4, Medidas.e5),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(168.dp)) {
                            val grosor = 14.dp.toPx()
                            drawArc(c.superficie2, -90f, 360f, false, style = Stroke(grosor), size = Size(size.width - grosor, size.height - grosor), topLeft = androidx.compose.ui.geometry.Offset(grosor / 2, grosor / 2))
                            drawArc(colorReloj, -90f, 360f * lleno, false, style = Stroke(grosor, cap = StrokeCap.Butt), size = Size(size.width - grosor, size.height - grosor), topLeft = androidx.compose.ui.geometry.Offset(grosor / 2, grosor / 2))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (dias == 0) "¡Hoy!" else "${kotlin.math.abs(dias)}", style = tipos.cifra.copy(fontSize = tipos.cifra.fontSize * 1.25f), color = colorReloj)
                            if (dias != 0) Text(if (dias < 0) (if (dias == -1) "día de atraso" else "días de atraso") else if (dias == 1) "día" else "días", style = tipos.pista, color = c.tintaSuave)
                        }
                    }
                    val frase = when {
                        dias < 0 -> "La entrega del ${aDdMmAa(fechaAlerta)} venció hace ${cuantos(-dias, "día")}."
                        dias == 0 -> "Hoy vence la entrega del B-1 (${aDdMmAa(fechaAlerta)})."
                        else -> "Faltan ${cuantos(dias, "día")} para entregar el B-1: ${fechaLarga(fechaAlerta).lowercase()}."
                    }
                    ParaLeer(frase)
                    Text(
                        frase,
                        style = tipos.textoFuerte, color = c.titulo, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Medidas.e3)
                    )
                }
                Spacer(Modifier.height(Medidas.e4))
            }

            PanelHallazgos(validarCalendario(calendario))

            // ---- próximas entregas ----
            TituloSeccion("Próximas entregas")
            val r = rendiciones.firstOrNull()
            if (r != null) {
                val hs = validarRendicion(r)
                val faltan = hs.count { it.severidad == Severidad.ERROR && it.falta }
                val mal = hs.count { it.severidad == Severidad.ERROR && !it.falta }
                val listo = faltan == 0 && mal == 0
                val nombre = r.periodo.etiqueta.trim().let { if (it.isNotEmpty()) " de $it" else "" }
                val partes = buildList {
                    if (faltan > 0) add(if (faltan == 1) "falta 1 dato" else "faltan $faltan datos")
                    if (mal > 0) add("$mal por corregir")
                }
                FranjaEstado(
                    if (listo) "El Formato B-1$nombre está listo para entregar." else "Al Formato B-1$nombre le ${partes.joinToString(" y ")}.",
                    if (listo) TonoPastilla.OK else TonoPastilla.ADVERTENCIA,
                    Modifier.padding(bottom = Medidas.e3),
                    boton = (if (listo) "Ver el B-1" else "Completar el B-1") to { ctx.ir(Ruta.B1) }
                )
            }
            if (!esIso(calendario.fechaLimiteBase)) {
                EstadoVacio("calendario", "Todavía no hay fechas", "Pon abajo una fecha de entrega que conozcas. Aquí verás las próximas.")
            } else {
                val agenda = agendaEntregas(calendario, hoy, 6)
                Superficie {
                    agenda.forEachIndexed { i, item ->
                        val reg = calendario.entregas.firstOrNull { it.fechaLimite == item.fechaLimite }
                        val estadoItem = reg?.estado ?: "pendiente"
                        val texto = when {
                            item.diasRestantes < 0 -> "hace ${cuantos(kotlin.math.abs(item.diasRestantes), "día")}"
                            item.diasRestantes == 0 -> "es hoy"
                            item.diasRestantes == 1 -> "falta 1 día"
                            else -> "faltan ${item.diasRestantes} días"
                        }
                        Row(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                            Text(aDdMmAa(item.fechaLimite), style = tipos.textoNegrita, color = c.titulo, modifier = Modifier.width(76.dp))
                            Text(
                                texto, style = if (item.nivel == Nivel.LEJANA || item.nivel == Nivel.PROXIMA) tipos.chico else tipos.chicoFuerte,
                                color = when (item.nivel) { Nivel.VENCIDA -> c.error; Nivel.HOY, Nivel.URGENTE -> c.aviso; else -> c.tintaSuave },
                                modifier = Modifier.weight(1f)
                            )
                            var abierto by remember { mutableStateOf(false) }
                            Box {
                                Ficha(ETIQUETA_ESTADO.first { it.first == estadoItem }.second, estadoItem != "pendiente", { abierto = true })
                                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }, containerColor = c.superficie) {
                                    for ((v, t) in ETIQUETA_ESTADO) DropdownMenuItem(text = { Text(t, style = tipos.texto, color = c.tinta) }, onClick = {
                                        abierto = false
                                        estado.cambiarCalendario { cal ->
                                            val existe = cal.entregas.any { it.fechaLimite == item.fechaLimite }
                                            cal.copy(entregas = if (existe) cal.entregas.map { if (it.fechaLimite == item.fechaLimite) it.copy(estado = v) else it }
                                            else cal.entregas + nuevaEntrega(item.fechaLimite).copy(estado = v))
                                        }
                                    })
                                }
                            }
                        }
                        if (i < agenda.size - 1) Separador()
                    }
                }
            }
            AvisosDelTelefono(esIso(calendario.fechaLimiteBase))

            // ---- cada cuánto entregas ----
            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Cada cuánto entregas")
            Leyenda("Cada cuánto")
            Opciones(PERIODICIDADES.map { it.clave to "${it.etiqueta} (${it.detalle})" }, { it == calendario.periodicidad }, { v -> estado.cambiarCalendario { it.copy(periodicidad = v) } })
            Spacer(Modifier.height(Medidas.e3))
            CampoFecha("Una fecha de entrega que conoces", calendario.fechaLimiteBase, { v -> estado.cambiarCalendario { it.copy(fechaLimiteBase = v) } }, pista = "La próxima o una pasada, da igual.")
            Spacer(Modifier.height(Medidas.e3))
            Leyenda("Avisarme")
            Opciones(OPCIONES_AVISO, { it in calendario.diasDeAviso }, { dias ->
                val otros = calendario.diasDeAviso.filter { it != dias }
                val marcado = dias in calendario.diasDeAviso
                if (marcado && otros.isEmpty()) { msgAviso = "Deja marcado al menos uno." }
                else {
                    msgAviso = ""
                    estado.cambiarCalendario { it.copy(diasDeAviso = (if (marcado) otros else otros + dias).sortedDescending()) }
                }
            })
            if (msgAviso.isNotEmpty()) Text(msgAviso, style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = 4.dp))
            EstadoGuardado(guardado, "Se guarda solo, mientras escribes.")
        }
    }
}

/**
 * Si el teléfono puede avisar con la app cerrada. Sin permiso, un botón lo
 * pide; si Android ya no deja pedirlo (lo negaron antes), abre los ajustes de
 * avisos de la app. Se revisa de nuevo al volver a la pantalla.
 */
@Composable
private fun AvisosDelTelefono(hayFechas: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as OllaConectaApp
    val c = colores
    var permiso by remember { mutableStateOf(AvisosTelefono.hayPermiso(context)) }
    var programados by remember { mutableStateOf(-1) }
    LifecycleResumeEffect(Unit) {
        permiso = AvisosTelefono.hayPermiso(context)
        if (permiso) programados = app.reprogramarAvisos()
        onPauseOrDispose { }
    }
    val abrirAjustes = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dado ->
        permiso = AvisosTelefono.hayPermiso(context)
        // Permiso dado pero los avisos apagados en los ajustes del teléfono: se abren ahí.
        if (permiso) programados = app.reprogramarAvisos() else if (dado) abrirAjustes()
    }
    val prefs = context.getSharedPreferences("avisos", Context.MODE_PRIVATE)
    if (permiso) {
        Text("El teléfono te avisa a las ${AvisosTelefono.HORA_DEL_AVISO} de la mañana, aunque la app esté cerrada.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 10.dp))
        if (hayFechas && programados == 0) Pista("No hay avisos pendientes.", Modifier.padding(top = 4.dp))
    } else {
        Text("Sin permiso, el aviso solo se ve al abrir la app.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 10.dp))
        FranjaEstado(
            "Activa los avisos y el teléfono te recordará la entrega.",
            TonoPastilla.ADVERTENCIA, Modifier.padding(top = Medidas.e2),
            boton = "Activar los avisos" to {
                val actividad = context as? Activity
                val negadoParaSiempre = prefs.getBoolean("permisoPedido", false) && actividad != null &&
                    !actividad.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= 33 && !negadoParaSiempre) {
                    prefs.edit().putBoolean("permisoPedido", true).apply()
                    pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else abrirAjustes()
            }
        )
    }
}
