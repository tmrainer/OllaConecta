// Fechas de entrega del B-1 y cuenta regresiva. Lo primero que se ve: cuántos días
// faltan, grande, como en un reloj. Con permiso, el teléfono avisa aunque la app esté
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import io.github.tmrainer.asistencia.nucleo.validaciones.Hallazgo
import io.github.tmrainer.asistencia.ui.ListaParaGuia
import io.github.tmrainer.asistencia.ui.componentes.SelectorDesplegable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.AvisosTelefono
import io.github.tmrainer.asistencia.OllaConectaApp
import io.github.tmrainer.asistencia.nucleo.alertas.Nivel
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
import io.github.tmrainer.asistencia.nucleo.calculos.codigoB1
import io.github.tmrainer.asistencia.nucleo.documentos.etiquetaMes
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import java.time.LocalDate
import io.github.tmrainer.asistencia.nucleo.calculos.proponerPeriodo
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.GUIA_ENTREGAS
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.BarraDesplegable
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.EstadoGuardado
import io.github.tmrainer.asistencia.ui.componentes.EstadoVacio
import io.github.tmrainer.asistencia.ui.componentes.FranjaEstado
import io.github.tmrainer.asistencia.ui.componentes.Pastilla
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
// Pendiente sin color; entregada y conforme, bien; observada, a revisar. Cada uno con su
// ícono: en claro el verde de «bien» y el neutro casi no se distinguen.
private val TONO_ESTADO = mapOf(
    "pendiente" to (TonoPastilla.NEUTRA to "reloj"), "entregada" to (TonoPastilla.OK to "ok"),
    "observada" to (TonoPastilla.ADVERTENCIA to "aviso"), "conforme" to (TonoPastilla.OK to "ok")
)
private val OPCIONES_AVISO = listOf(1 to "1 día antes", 3 to "3 días antes", 7 to "7 días antes", 15 to "15 días antes")

/** Los días marcados, en palabras: «1, 3 y 7 días antes». */
private fun diasAntes(dias: List<Int>): String {
    val xs = dias.sorted().map { it.toString() }
    if (xs.isEmpty()) return "Sin aviso"
    val lista = if (xs.size > 1) "${xs.dropLast(1).joinToString(", ")} y ${xs.last()}" else xs[0]
    return if (xs == listOf("1")) "1 día antes" else "$lista días antes"
}

/**
 * El color de una entrega según cuánto falta, igual en el Inicio, el reloj y la lista:
 * vencida u hoy en rojo, faltan pocos días en amarillo, lejos sin color.
 */
fun tonoEntrega(nivel: Nivel): TonoPastilla = when (nivel) {
    Nivel.VENCIDA, Nivel.HOY -> TonoPastilla.ERROR
    Nivel.URGENTE, Nivel.PROXIMA -> TonoPastilla.ADVERTENCIA
    Nivel.LEJANA -> TonoPastilla.NEUTRA
}

@Composable
fun PantallaEntregas(ctx: Contexto) {
    val estado = ctx.estado
    val calendario by estado.calendario.collectAsStateWithLifecycle()
    val rendiciones by estado.rendiciones.collectAsStateWithLifecycle()
    val guardado by estado.guardado.collectAsStateWithLifecycle()
    val c = colores
    val hoy = hoyIso()
    var msgAviso by remember { mutableStateOf("") }
    // El B-1 es mensual (decisión del 22-11-2026): las entregas también. Un calendario
    // semanal o quincenal de antes pasa a mensual, con la misma fecha de referencia.
    LaunchedEffect(calendario.periodicidad) { if (calendario.periodicidad != "mensual") estado.cambiarCalendario { it.copy(periodicidad = "mensual") } }
    val padron by estado.padron.collectAsStateWithLifecycle()
    // Los meses, desde el anterior al que se empezó a usar la app: lo de antes no se llenaba
    // con ella y solo sumaba faltantes. Por defecto, el del B-1 en curso; si no tiene, el de hoy.
    val desdeMes = ctx.preferencias.desdeEntregas.take(7)
    fun vale(m: String) = Regex("^\\d{4}-\\d{2}$").matches(m) && m >= desdeMes
    val mesB1 = rendiciones.firstOrNull()?.periodo?.inicio?.take(7)?.takeIf(::vale)
    var mes by remember(mesB1) { mutableStateOf(mesB1 ?: hoy.take(7)) }
    val meses = remember(padron, rendiciones, mes) {
        (listOf(hoy.take(7), mes) + rendiciones.map { it.periodo.inicio.take(7) } + padron.atenciones.map { it.fecha.take(7) })
            .filter(::vale).distinct().sortedDescending().take(8)
    }
    // Lo pesado se calcula aparte, fuera del hilo de la pantalla: la pantalla aparece enseguida.
    val indiceB1 = rendiciones.indexOfFirst { it.periodo.inicio.take(7) == mes }
    val r = rendiciones.getOrNull(indiceB1)
    val hsB1 by produceState<List<Hallazgo>?>(null, r) { value = if (r == null) emptyList() else withContext(Dispatchers.Default) { hallazgosDelB1(r) } }
    val lista = rememberLazyListState()

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Entregas", "El B-1 y el reporte de cada mes", guia = GUIA_ENTREGAS)
        ListaParaGuia(lista)
        // Una lista que arma solo lo que se ve (con muchos datos, abrir la pantalla tardaba):
        // el reporte, el gasto propio, las fechas y los avisos se arman al bajar.
        LazyColumn(
            Modifier.fillMaxWidth().widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally), state = lista,
            contentPadding = PaddingValues(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            item(key = "reloj") {
                Column {
                    val avisosAlmacen = estado.avisosAlmacenamiento()
                    if (avisosAlmacen.isNotEmpty()) AvisoSistema(avisosAlmacen.joinToString(" "), Modifier.padding(bottom = Medidas.e4))
                    RelojEntrega(alertaProximaEntrega(calendario, hoy, ctx.preferencias.desdeEntregas), calendario.periodicidad)
                    PanelHallazgos(validarCalendario(calendario))
                }
            }

            // Lo que se entrega cada mes, junto (decisión del 22-11-2026): el B-1, el reporte del mes y el gasto propio.
            item(key = "mes") {
                Column {
                    TituloSeccion("Lo que se entrega")
                    SelectorDesplegable("Mes", meses.map { it to etiquetaMes(it) }, mes, { mes = it }, Modifier.objetivoGuia("entregas.mes"), icono = "calendario")
                    Spacer(Modifier.height(Medidas.e4))
                    Leyenda("Formato B-1")
                    val hs = hsB1
                    when {
                        r == null -> FranjaEstado("Todavía no hay un B-1 de ${etiquetaMes(mes)}.", TonoPastilla.NEUTRA, Modifier.objetivoGuia("entregas.b1"), icono = "boleta",
                            boton = "Ir al B-1" to { ctx.ir(Ruta.B1) })
                        indiceB1 > 0 -> FranjaEstado("El B-1 ${codigoB1(r.periodo, LocalDate.now(), cerrado = true) ?: etiquetaMes(mes)} ya está cerrado.", TonoPastilla.OK, Modifier.objetivoGuia("entregas.b1"), icono = "ok")
                        hs == null -> Pista("Revisando el B-1…", Modifier.objetivoGuia("entregas.b1"))
                        else -> {
                            val faltan = hs.count { it.severidad == Severidad.ERROR && it.falta }
                            val mal = hs.count { it.severidad == Severidad.ERROR && !it.falta }
                            val listo = faltan == 0 && mal == 0
                            val partes = buildList {
                                if (faltan > 0) add(if (faltan == 1) "falta 1 dato" else "faltan $faltan datos")
                                if (mal > 0) add(if (mal == 1) "hay 1 dato por corregir" else "hay $mal datos por corregir")
                            }
                            FranjaEstado(
                                if (listo) "El B-1 de ${etiquetaMes(mes)} está listo para entregar." else "B-1 de ${etiquetaMes(mes)}: ${partes.joinToString(" y ")}.",
                                if (listo) TonoPastilla.OK else TonoPastilla.ADVERTENCIA, Modifier.objetivoGuia("entregas.b1"),
                                boton = (if (listo) "Ver el B-1" else "Completar el B-1") to { ctx.ir(Ruta.B1) }
                            )
                        }
                    }
                }
            }
            item(key = "reporte") {
                Column(Modifier.padding(top = Medidas.e4)) {
                    Leyenda("Reporte del mes")
                    ReporteDelMes(ctx, mes, Modifier.objetivoGuia("entregas.reporte"))
                }
            }
            item(key = "gasto") { Column(Modifier.padding(top = Medidas.e4)) { GastoPropioDelMes(ctx, rendiciones, mes, hoy) } }

            item(key = "proximas") {
                Column(Modifier.padding(top = Medidas.e5)) {
                    TituloSeccion("Próximas entregas")
                    if (!esIso(calendario.fechaLimiteBase)) {
                        EstadoVacio("calendario", "Todavía no hay fechas", "Pon abajo una fecha de entrega que conozcas. Aquí verás las próximas.")
                    } else {
                        ListaDeEntregas(agendaEntregas(calendario, hoy, 6, ctx.preferencias.desdeEntregas), calendario) { fecha, v ->
                            estado.cambiarCalendario { cal ->
                                val existe = cal.entregas.any { it.fechaLimite == fecha }
                                cal.copy(entregas = if (existe) cal.entregas.map { if (it.fechaLimite == fecha) it.copy(estado = v) else it }
                                else cal.entregas + nuevaEntrega(fecha).copy(estado = v))
                            }
                        }
                    }
                    AvisosDelTelefono(esIso(calendario.fechaLimiteBase))
                }
            }

            item(key = "vence") {
                Column(Modifier.padding(top = Medidas.e5)) {
                    TituloSeccion("Cuándo vence")
                    CampoFecha("Una fecha de entrega que conoces", calendario.fechaLimiteBase, { v -> estado.cambiarCalendario { it.copy(fechaLimiteBase = v) } }, Modifier.objetivoGuia("entregas.fecha"), pista = "Vence ese mismo día cada mes. La próxima o una pasada, da igual.")
                    Spacer(Modifier.height(Medidas.e3))
                    Column(Modifier.objetivoGuia("entregas.avisar")) {
                        var avisarAbierto by remember { mutableStateOf(false) }
                        // Se marcan varios: la barra queda abierta hasta que se toca otra vez.
                        BarraDesplegable("Avisarme", diasAntes(calendario.diasDeAviso), avisarAbierto, { avisarAbierto = !avisarAbierto }, icono = "reloj") {
                            Opciones(OPCIONES_AVISO, { it in calendario.diasDeAviso }, { dias ->
                                val otros = calendario.diasDeAviso.filter { it != dias }
                                val marcado = dias in calendario.diasDeAviso
                                if (marcado && otros.isEmpty()) { msgAviso = "Deja marcado al menos uno." }
                                else {
                                    msgAviso = ""
                                    estado.cambiarCalendario { it.copy(diasDeAviso = (if (marcado) otros else otros + dias).sortedDescending()) }
                                }
                            })
                        }
                    }
                    if (msgAviso.isNotEmpty()) Text(msgAviso, style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = 4.dp))
                    EstadoGuardado(guardado, "Se guarda solo, mientras escribes.")
                }
            }
        }
    }
}

/** El reloj de la próxima entrega (o de la vencida): cuántos días faltan o de atraso. */
@Composable
private fun RelojEntrega(a: io.github.tmrainer.asistencia.nucleo.alertas.AlertaEntrega?, periodicidad: String) {
    val c = colores
    val dias = a?.diasRestantes
    val fechaAlerta = a?.fechaLimite
    if (a == null || !a.configurado || dias == null || fechaAlerta == null) return
    val largo = when (periodicidad) { "semanal" -> 7; "quincenal" -> 15; else -> 30 }
    val lleno = if (dias < 0) 1f else (1f - dias.toFloat() / largo).coerceIn(0f, 1f)
    val colorReloj = when (tonoEntrega(a.nivel)) { TonoPastilla.ERROR -> c.error; TonoPastilla.ADVERTENCIA -> c.aviso; else -> c.acentoTexto }
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
        Text(frase, style = tipos.textoFuerte, color = c.titulo, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Medidas.e3))
    }
    Spacer(Modifier.height(Medidas.e4))
}

/** Las próximas entregas con su estado; [cambiarEstado] recibe la fecha y el estado nuevo. */
@Composable
private fun ListaDeEntregas(agenda: List<io.github.tmrainer.asistencia.nucleo.alertas.EntregaAgenda>, calendario: io.github.tmrainer.asistencia.nucleo.modelo.Calendario, cambiarEstado: (String, String) -> Unit) {
    val c = colores
    Box(Modifier.objetivoGuia("entregas.lista")) { Superficie {
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
                Text(aDdMmAa(item.fechaLimite), style = tipos.textoNegrita, color = c.titulo, modifier = Modifier.widthIn(min = 76.dp))
                Column(Modifier.weight(1f)) {
                    // Ya entregada, una fecha pasada no es una alerta.
                    val tono = if (estadoItem == "entregada" || estadoItem == "conforme") TonoPastilla.NEUTRA else tonoEntrega(item.nivel)
                    Text(
                        texto, style = if (tono == TonoPastilla.NEUTRA) tipos.chico else tipos.chicoFuerte,
                        color = when (tono) { TonoPastilla.ERROR -> c.error; TonoPastilla.ADVERTENCIA -> c.aviso; else -> c.tintaSuave }
                    )
                    // Qué B-1 se entrega ese día: el mes que propone para esa fecha, con su nombre («Noviembre-2026»).
                    codigoB1(proponerPeriodo(calendario.periodicidad, item.fechaLimite).aPeriodo(), LocalDate.now(), cerrado = true)?.let {
                        Text("B-1 $it", style = tipos.chico, color = c.tintaSuave)
                    }
                }
                var abierto by remember { mutableStateOf(false) }
                Box {
                    val textoEstado = ETIQUETA_ESTADO.first { it.first == estadoItem }.second
                    // Cada entrega dice de qué fecha es: no cinco «Pendiente» iguales.
                    val (tonoEstado, iconoEstado) = TONO_ESTADO.getValue(estadoItem)
                    Pastilla(textoEstado, tonoEstado, iconoEstado, modifier = Modifier.semantics { contentDescription = "Entrega del ${aDdMmAa(item.fechaLimite)}: $textoEstado" }) { abierto = true }
                    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }, containerColor = c.superficie) {
                        for ((v, t) in ETIQUETA_ESTADO) DropdownMenuItem(text = { Text(t, style = tipos.texto, color = c.tinta) }, onClick = {
                            abierto = false
                            cambiarEstado(item.fechaLimite, v)
                        })
                    }
                }
            }
            if (i < agenda.size - 1) Separador()
        }
    } }
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
    val alcance = rememberCoroutineScope()
    // Reprogramar son decenas de llamadas al sistema: aparte, para no frenar la pantalla.
    LifecycleResumeEffect(Unit) {
        permiso = AvisosTelefono.hayPermiso(context)
        if (permiso) alcance.launch(Dispatchers.Default) { programados = app.reprogramarAvisos() }
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
            TonoPastilla.ADVERTENCIA, Modifier.padding(top = Medidas.e2).objetivoGuia("entregas.activar"),
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
