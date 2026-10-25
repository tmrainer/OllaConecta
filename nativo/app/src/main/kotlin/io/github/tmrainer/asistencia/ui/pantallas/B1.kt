// B1.kt — el Formato B-1 por pasos (pagina-b1.js): una sección a la vez, con
// «Atrás / Siguiente» y una barra que dice dónde estás y qué paso tiene algo
// pendiente. No se avanza con un dato obligatorio pendiente: se dice cuál y
// se lleva ahí. Las sugerencias se ofrecen; nunca se aplican solas.
//
// Ver cómo queda en la hoja y exportar llegan en una fase siguiente.
package io.github.tmrainer.asistencia.ui.pantallas

import io.github.tmrainer.asistencia.nucleo.modelo.uuid
import io.github.tmrainer.asistencia.nucleo.modelo.GastoPropio
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.nucleo.documentos.hojasDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelB1
import io.github.tmrainer.asistencia.ui.componentes.Documento
import io.github.tmrainer.asistencia.ui.componentes.BotonesExportar
import io.github.tmrainer.asistencia.AlElegir
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.alertas.Nivel
import io.github.tmrainer.asistencia.nucleo.alertas.alertaProximaEntrega
import io.github.tmrainer.asistencia.nucleo.calculos.gastoPorOrigen
import io.github.tmrainer.asistencia.nucleo.calculos.proponerPeriodo
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.aNumero
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasEncabezado
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasPerfil
import io.github.tmrainer.asistencia.nucleo.validaciones.Hallazgo
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.validarRendicion
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.BloqueSuave
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.BotonFlotante
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.CampoSoles
import io.github.tmrainer.asistencia.ui.componentes.Chip
import io.github.tmrainer.asistencia.ui.componentes.Chips
import io.github.tmrainer.asistencia.ui.componentes.EstadoGuardado
import io.github.tmrainer.asistencia.ui.componentes.Ficha
import io.github.tmrainer.asistencia.ui.componentes.FranjaEstado
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.PanelHallazgos
import io.github.tmrainer.asistencia.ui.componentes.Pastilla
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.componentes.TonoPastilla
import io.github.tmrainer.asistencia.ui.componentes.vibrar
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.launch

private val PASOS = listOf("Centro de atención", "Periodo", "Ingresos (dinero recibido)", "Compras (egresos)", "Firmas", "Revisar")
private val UNIDADES = listOf("Kg", "g", "L", "ml", "unid", "saco", "bolsa", "caja", "varios")
private val ORIGENES = listOf("subsidio" to "Subsidio municipal", "ayuda_social" to "Ayuda social / donación", "aporte_propio" to "Aporte propio del centro")

/** Paso de cada hallazgo, por la sección donde está su campo. */
private fun pasoDe(campo: String): Int = when {
    campo in listOf("tipoCentro", "nombreCentro", "codigoPca", "nombrePresidenta", "dniPresidenta", "celular") -> 1
    campo.startsWith("periodo.") || campo == "fechaRendicion" -> 2
    campo == "montoSubsidio" || campo == "fechaAsignacion" -> 3
    campo == "egresos" || campo.startsWith("egreso.") || campo == "totales" -> 4
    campo in listOf("nombrePresidentaFirma", "dniPresidentaFirma", "nombreTesorera", "dniTesorera") -> 5
    else -> 6
}

private fun List<Hallazgo>.errorDe(campo: String): String? = firstOrNull { it.campo == campo && it.severidad == Severidad.ERROR && !it.falta }?.mensaje
private fun List<Hallazgo>.avisoDe(campo: String): String? = firstOrNull { it.campo == campo && it.severidad == Severidad.ADVERTENCIA }?.mensaje

/** «Octubre», o las fechas del periodo, o «sin periodo». Solo para mostrar. */
private fun nombreDelMes(r: Rendicion): String {
    val p = r.periodo
    if (p.etiqueta.trim().isNotEmpty()) return p.etiqueta.trim()
    if (p.inicio.isNotEmpty() || p.fin.isNotEmpty()) return "${aDdMmAa(p.inicio).ifEmpty { "…" }} a ${aDdMmAa(p.fin).ifEmpty { "…" }}"
    return "sin periodo"
}

private fun egresoCompleto(f: Egreso) = f.fechaCompra.isNotEmpty() && f.descripcion.trim().isNotEmpty() && f.montoTotalCent != null

/** «12-10-26 · Arroz · S/ 45.00», o qué le falta. */
private fun resumenEgreso(f: Egreso): String {
    val partes = ArrayList<String>()
    if (f.fechaCompra.isNotEmpty()) partes.add(aDdMmAa(f.fechaCompra))
    if (f.descripcion.trim().isNotEmpty()) partes.add(f.descripcion.trim())
    val monto = formatearSoles(f.montoTotalCent)
    if (monto.isNotEmpty()) partes.add("S/ $monto")
    return if (partes.isEmpty()) "sin llenar" else partes.joinToString(" · ")
}

@Composable
fun PantallaB1(ctx: Contexto, argumento: String?) {
    val estado = ctx.estado
    val rendiciones by estado.rendiciones.collectAsStateWithLifecycle()
    val config by estado.config.collectAsStateWithLifecycle()
    val calendario by estado.calendario.collectAsStateWithLifecycle()
    val perfil by ctx.preferencias.perfil.collectAsStateWithLifecycle()
    val guardado by estado.guardado.collectAsStateWithLifecycle()
    val c = colores
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()

    // rendiciones[0] es el B-1 en curso. Sin ninguno, se crea uno vacío.
    LaunchedEffect(rendiciones.isEmpty()) { if (rendiciones.isEmpty()) estado.cambiarRendiciones { listOf(nuevaRendicion()) } }
    var verId by remember { mutableStateOf<String?>(null) }
    val indice = maxOf(0, rendiciones.indexOfFirst { it.id == verId })
    val rendicion = rendiciones.getOrNull(indice) ?: return
    LaunchedEffect(rendicion.id, rendicion.egresos.isEmpty()) {
        if (rendicion.egresos.isEmpty()) estado.cambiarRendicion(rendicion.id) { it.copy(egresos = listOf(nuevoEgreso())) }
    }
    fun cambiar(cambio: (Rendicion) -> Rendicion) = estado.cambiarRendicion(rendicion.id, cambio)
    fun cambiarEgreso(id: String, cambio: (Egreso) -> Egreso) = cambiar { r -> r.copy(egresos = r.egresos.map { if (it.id == id) cambio(it) else it }) }

    val hallazgos = remember(rendicion) { validarRendicion(rendicion) }
    val pendientesPorPaso = remember(hallazgos) { hallazgos.filter { it.severidad == Severidad.ERROR }.map { pasoDe(it.campo) }.toSet() }

    var pasoActual by remember {
        mutableIntStateOf(
            if (argumento == "compras") 4
            else validarRendicion(rendicion).filter { it.severidad == Severidad.ERROR }.map { pasoDe(it.campo) }.minOrNull() ?: 6
        )
    }
    var avisoPasos by remember { mutableStateOf("") }
    // Campos aceptados de una sugerencia, marcados hasta que se editen.
    val propuestos = remember { mutableStateOf(setOf<String>()) }
    val desplazamiento = rememberScrollState()
    var egresoAbierto by remember { mutableStateOf<String?>(null) }
    var confirmarMes by remember { mutableStateOf(false) }

    fun irAPaso(n: Int) {
        pasoActual = n.coerceIn(1, PASOS.size)
        alcance.launch { desplazamiento.scrollTo(0) }
    }

    /** No se avanza con un dato obligatorio pendiente: se dice cuál y se lleva ahí. */
    fun bloqueoHasta(n: Int): Boolean {
        val pendientes = validarRendicion(rendicion).filter { it.severidad == Severidad.ERROR && pasoDe(it.campo) >= pasoActual && pasoDe(it.campo) < n }.sortedBy { pasoDe(it.campo) }
        if (pendientes.isEmpty()) return false
        val paso = pasoDe(pendientes[0].campo)
        if (paso != pasoActual) irAPaso(paso)
        val delPaso = pendientes.filter { pasoDe(it.campo) == paso }
        val enLista = { xs: List<String> -> if (xs.size > 1) "${xs.dropLast(1).joinToString(", ")} y ${xs.last()}" else xs[0] }
        val faltan = delPaso.filter { Regex("^Faltan? ").containsMatchIn(it.mensaje) }.map { it.mensaje.replace(Regex("^Faltan? "), "").removeSuffix(".") }
        val revisar = delPaso.filter { !Regex("^Faltan? ").containsMatchIn(it.mensaje) }.map { it.mensaje.removeSuffix(".") }
        val partes = ArrayList<String>()
        if (faltan.isNotEmpty()) partes.add("Para seguir, falta: ${enLista(if (faltan.size > 3) faltan.take(3) + "${faltan.size - 3} más" else faltan)}.")
        if (revisar.isNotEmpty()) partes.add("${if (faltan.isNotEmpty()) "Y revisa" else "Para seguir, revisa"}: ${revisar.take(2).joinToString("; ")}.")
        avisoPasos = partes.joinToString(" ")
        vibrar(contexto, 60)
        if (pendientes[0].campo.startsWith("egreso.")) egresoAbierto = pendientes[0].campo.removePrefix("egreso.")
        return true
    }

    fun avanzarA(n: Int) {
        if (n > pasoActual && bloqueoHasta(n)) return
        avisoPasos = ""
        irAPaso(n)
    }

    fun irACampo(campo: String) {
        irAPaso(pasoDe(campo))
        if (campo.startsWith("egreso.")) egresoAbierto = campo.removePrefix("egreso.")
    }

    /** Exportar pide el B-1 completo: si falta algo, se dice qué y se lleva ahí (listoParaExportar de la web). */
    fun listoParaExportar(): Boolean {
        val pendientes = validarRendicion(rendicion).filter { it.severidad == Severidad.ERROR }.sortedBy { pasoDe(it.campo) }
        if (pendientes.isEmpty()) { ctx.estado.guardarPendiente(); return true }
        ctx.avisos.mostrar("Antes de exportar, completa el B-1: " +
            pendientes.take(2).joinToString("; ") { it.mensaje.removeSuffix(".").replace(Regex("^Faltan? "), "falta ") } +
            (if (pendientes.size > 2) " y ${pendientes.size - 2} más" else "") + ".")
        irACampo(pendientes[0].campo)
        return false
    }

    fun agregarCompra() {
        val nuevo = nuevoEgreso()
        cambiar { it.copy(egresos = it.egresos + nuevo) }
        egresoAbierto = nuevo.id
    }
    // «Anotar una compra» desde el Inicio: si la última ya tiene datos, una nueva.
    LaunchedEffect(argumento) {
        if (argumento == "compras") {
            val ultima = rendicion.egresos.lastOrNull()
            if (ultima != null && egresoCompleto(ultima)) agregarCompra() else egresoAbierto = ultima?.id
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Cabecera(ctx, "Formato B-1", "Rendición de cuentas")
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(desplazamiento).widthIn(max = Medidas.anchoColumna)
                    .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, if (pasoActual == 4) 110.dp else 40.dp)
            ) {
                val avisosAlmacen = estado.avisosAlmacenamiento()
                if (avisosAlmacen.isNotEmpty()) AvisoSistema(avisosAlmacen.joinToString(" "), Modifier.padding(bottom = Medidas.e4))
                if (indice > 0) {
                    FranjaEstado("Estás viendo el B-1 de un mes anterior: ${nombreDelMes(rendicion)}.", TonoPastilla.ADVERTENCIA, Modifier.padding(bottom = Medidas.e3), boton = "Volver al B-1 del mes" to { verId = null })
                }
                BannerEntrega(ctx, calendario, hoyIso())
                PanelHallazgos(hallazgos, alIrA = { irACampo(it) })

                // ---- la barra de pasos ----
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PASOS.forEachIndexed { i, nombre ->
                        val n = i + 1
                        val color = when {
                            n == pasoActual -> c.acento
                            n in pendientesPorPaso -> c.pendiente
                            n < pasoActual -> c.acentoTexto
                            else -> c.seleccion
                        }
                        Box(
                            Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(Medidas.rS)).clickable(role = Role.Button) { avanzarA(n) }
                                .semantics { contentDescription = "Paso $n: $nombre" + if (n in pendientesPorPaso) ", le falta algo" else "" },
                            contentAlignment = Alignment.Center
                        ) { Box(Modifier.fillMaxWidth().height(if (n == pasoActual) 8.dp else 6.dp).clip(RoundedCornerShape(999.dp)).background(color)) }
                    }
                }
                Text("Paso $pasoActual de ${PASOS.size}", style = tipos.pista, color = c.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e3))

                when (pasoActual) {
                    1 -> PasoCentro(rendicion, hallazgos, config, perfil, propuestos, ::cambiar)
                    2 -> PasoPeriodo(rendicion, hallazgos, ::cambiar)
                    3 -> PasoIngresos(rendicion, hallazgos, ::cambiar)
                    4 -> PasoCompras(rendicion, hallazgos, egresoAbierto, { egresoAbierto = it }, ::cambiar, ::cambiarEgreso)
                    5 -> PasoFirmas(rendicion, hallazgos, ::cambiar)
                    else -> PasoRevisar(
                        rendicion, rendiciones, indice, hallazgos, ::irACampo, abrir = { verId = it }, empezarMes = { confirmarMes = true },
                        listoParaExportar = ::listoParaExportar, verHoja = { ctx.ir(Ruta.HOJA, rendicion.id) }
                    )
                }
                EstadoGuardado(guardado, "Se guarda solo, mientras escribes.")
                Text("El B-1 es una declaración jurada: revisa todo.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e5))
            }
            // ---- Atrás / Siguiente, pegados abajo, con su propio fondo y una línea arriba ----
            Separador()
            Column(Modifier.fillMaxWidth().background(c.superficie).padding(Medidas.e4, 10.dp)) {
                if (avisoPasos.isNotEmpty()) {
                    Text(avisoPasos, style = tipos.textoFuerte, color = c.error, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.errorFondo).padding(Medidas.e4, Medidas.e3))
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Boton("Atrás", { irAPaso(pasoActual - 1) }, habilitado = pasoActual > 1)
                    if (pasoActual < PASOS.size) {
                        Boton("Siguiente: ${PASOS[pasoActual].replace(Regex(" \\(.*\\)$"), "")}", { avanzarA(pasoActual + 1) }, Modifier.weight(1f), tipo = TipoBoton.PRINCIPAL)
                    }
                }
            }
        }
        if (pasoActual == 4) BotonFlotante("Compra", ::agregarCompra, Modifier.align(Alignment.BottomEnd).padding(end = Medidas.e4, bottom = 84.dp))
    }

    if (confirmarMes) {
        AlertDialog(
            onDismissRequest = { confirmarMes = false },
            containerColor = c.superficie,
            title = { Text("¿Empezar el B-1 del mes siguiente?", style = tipos.seccion, color = c.titulo) },
            text = { Text("El B-1 de ${nombreDelMes(rendicion)} queda guardado en «Meses anteriores».", style = tipos.texto, color = c.tinta) },
            confirmButton = {
                Boton("Empezar", {
                    confirmarMes = false
                    // Solo cada cuánto se entrega: lo demás lo propone la app desde el mes anterior.
                    val nueva = nuevaRendicion().copy(periodo = nuevaRendicion().periodo.copy(tipo = rendicion.periodo.tipo))
                    estado.cambiarRendiciones { listOf(nueva) + it }
                    verId = null
                    irAPaso(1)
                }, tipo = TipoBoton.PRINCIPAL)
            },
            dismissButton = { Boton("Cancelar", { confirmarMes = false }) }
        )
    }
}

@Composable
private fun BannerEntrega(ctx: Contexto, calendario: io.github.tmrainer.asistencia.nucleo.modelo.Calendario, hoy: String) {
    val alerta = alertaProximaEntrega(calendario, hoy) ?: return
    val c = colores
    if (!alerta.configurado) {
        FranjaEstado(alerta.mensaje, TonoPastilla.NEUTRA, Modifier.padding(bottom = Medidas.e4), icono = "reloj", boton = "Configurar entregas" to { ctx.ir(Ruta.ENTREGAS) })
        return
    }
    val dias = alerta.diasRestantes ?: 0
    val titulo = when (alerta.nivel) {
        Nivel.VENCIDA -> "Entrega vencida"; Nivel.HOY -> "Se entrega hoy"; Nivel.URGENTE -> "Falta muy poco"; Nivel.PROXIMA -> "Se acerca la entrega"; Nivel.LEJANA -> "Próxima entrega"
    }
    val cuenta = when {
        dias < 0 -> "${-dias} ${if (dias == -1) "día" else "días"} de atraso"
        dias == 0 -> "hoy"
        dias == 1 -> "falta 1 día"
        else -> "faltan $dias días"
    }
    val tono = when (alerta.nivel) { Nivel.VENCIDA -> TonoPastilla.ERROR; Nivel.HOY, Nivel.URGENTE -> TonoPastilla.ADVERTENCIA; else -> TonoPastilla.NEUTRA }
    // Una sola idea: la cuenta. El detalle está en Entregas.
    FranjaEstado("$titulo: $cuenta.", tono, Modifier.padding(bottom = Medidas.e4), icono = if (alerta.nivel == Nivel.VENCIDA) "aviso" else "reloj", boton = "Ver entregas" to { ctx.ir(Ruta.ENTREGAS) })
}

// ------------------------------------------------------------------ pasos

@Composable
private fun PasoCentro(
    r: Rendicion, hs: List<Hallazgo>, config: io.github.tmrainer.asistencia.nucleo.modelo.Config,
    perfil: io.github.tmrainer.asistencia.nucleo.modelo.Perfil, propuestos: androidx.compose.runtime.MutableState<Set<String>>,
    cambiar: ((Rendicion) -> Rendicion) -> Unit
) {
    val c = colores
    TituloSeccion("Centro de atención")
    Pista("Cópialos de tu B-1 anterior o de tu resolución. Todos son obligatorios.", Modifier.padding(bottom = Medidas.e3))

    // Una sugerencia NUNCA se aplica sola: solo se ofrece. Lo del perfil manda: es lo más actual.
    val props = sugerenciasEncabezado(config) + sugerenciasPerfil(perfil)
    val valorDe = { campo: String -> when (campo) {
        "tipoCentro" -> r.tipoCentro; "nombreCentro" -> r.nombreCentro; "codigoPca" -> r.codigoPca; "nombrePresidenta" -> r.nombrePresidenta
        "dniPresidenta" -> r.dniPresidenta; "celular" -> r.celular; "nombreTesorera" -> r.nombreTesorera; "dniTesorera" -> r.dniTesorera
        "nombrePresidentaFirma" -> r.nombrePresidentaFirma; "dniPresidentaFirma" -> r.dniPresidentaFirma; else -> ""
    } }
    val pendientes = props.filter { (campo, _) -> valorDe(campo).trim().isEmpty() }
    if (pendientes.isNotEmpty()) {
        BloqueSuave(Modifier.padding(bottom = Medidas.e3)) {
            Text("Tienes datos guardados (${pendientes.values.map { it.procedencia }.distinct().joinToString(" y ")}). Puedes usarlos o escribirlos de nuevo.", style = tipos.pista, color = c.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e2))
            Boton("Usar los datos guardados (${pendientes.size})", {
                cambiar { x ->
                    var y = x
                    for ((campo, p) in pendientes) y = when (campo) {
                        "tipoCentro" -> y.copy(tipoCentro = p.valor); "nombreCentro" -> y.copy(nombreCentro = p.valor); "codigoPca" -> y.copy(codigoPca = p.valor)
                        "nombrePresidenta" -> y.copy(nombrePresidenta = p.valor); "dniPresidenta" -> y.copy(dniPresidenta = p.valor); "celular" -> y.copy(celular = p.valor)
                        "nombreTesorera" -> y.copy(nombreTesorera = p.valor); "dniTesorera" -> y.copy(dniTesorera = p.valor)
                        "nombrePresidentaFirma" -> y.copy(nombrePresidentaFirma = p.valor); "dniPresidentaFirma" -> y.copy(dniPresidentaFirma = p.valor); else -> y
                    }
                    y
                }
                propuestos.value = propuestos.value + pendientes.keys.filter { it != "tipoCentro" }
            })
        }
    }
    val marca = { campo: String -> if (campo in propuestos.value) "Dato propuesto — revísalo" else null }
    fun escribir(campo: String, f: (Rendicion) -> Rendicion) { propuestos.value = propuestos.value - campo; cambiar(f) }

    Leyenda("Tipo de centro (marca uno)")
    Opciones(listOf("comedor" to "Comedor", "olla_comun" to "Olla común"), { it == r.tipoCentro }, { v -> cambiar { it.copy(tipoCentro = v) } })
    hs.errorDe("tipoCentro")?.let { Text(it, style = tipos.chico, color = c.error) }
    Spacer(Modifier.height(Medidas.e3))
    Campo("Nombre del centro de atención", r.nombreCentro, { v -> escribir("nombreCentro") { it.copy(nombreCentro = v) } }, pista = marca("nombreCentro") ?: "Como lo registró la Municipalidad.", error = hs.errorDe("nombreCentro"), placeholder = "Ej.: Olla común Virgen de Fátima", obligatorio = true)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Código de PCA N.º", r.codigoPca, { v -> escribir("codigoPca") { it.copy(codigoPca = v) } }, pista = marca("codigoPca") ?: "El código que te dio la Municipalidad.", error = hs.errorDe("codigoPca"), aviso = hs.avisoDe("codigoPca"), placeholder = "Ej.: 0123", obligatorio = true, soloDigitos = true)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Nombre de la presidenta", r.nombrePresidenta, { v -> escribir("nombrePresidenta") { it.copy(nombrePresidenta = v) } }, pista = marca("nombrePresidenta") ?: "Nombres y apellidos, como en su DNI.", error = hs.errorDe("nombrePresidenta"), obligatorio = true)
    Spacer(Modifier.height(Medidas.e3))
    Campo("DNI", r.dniPresidenta, { v -> escribir("dniPresidenta") { it.copy(dniPresidenta = v) } }, pista = marca("dniPresidenta") ?: "8 cifras.", error = hs.errorDe("dniPresidenta"), placeholder = "12345678", obligatorio = true, soloDigitos = true, maxLargo = 8)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Celular N.º", r.celular, { v -> escribir("celular") { it.copy(celular = v) } }, pista = marca("celular") ?: "9 cifras; empieza con 9.", error = hs.errorDe("celular"), placeholder = "987654321", obligatorio = true, soloDigitos = true, maxLargo = 9)
}

@Composable
private fun PasoPeriodo(r: Rendicion, hs: List<Hallazgo>, cambiar: ((Rendicion) -> Rendicion) -> Unit) {
    val c = colores
    TituloSeccion("Periodo que se rinde")
    Pista("¿Qué semana, quincena o mes estás rindiendo?", Modifier.padding(bottom = Medidas.e3))
    Leyenda("Cada cuánto se entrega")
    Opciones(listOf("semanal" to "Semanal", "quincenal" to "Quincenal", "mensual" to "Mensual"), { it == r.periodo.tipo }, { v -> cambiar { it.copy(periodo = it.periodo.copy(tipo = v)) } })
    Spacer(Modifier.height(Medidas.e3))
    CampoFecha("Fecha de rendición", r.fechaRendicion, { v -> cambiar { it.copy(fechaRendicion = v) } }, pista = "El día que entregas este B-1 en la Municipalidad.", error = hs.errorDe("fechaRendicion"), obligatorio = true)

    // Ayudas, no datos: se usan solo si se tocan.
    val p = proponerPeriodo(r.periodo.tipo.ifEmpty { "mensual" }, r.fechaRendicion.ifEmpty { hoyIso() })
    val yaIgual = r.periodo.inicio == p.inicio && r.periodo.fin == p.fin
    if (!(yaIgual && r.periodo.etiqueta.isNotEmpty())) {
        BloqueSuave(Modifier.padding(top = Medidas.e3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fechas del periodo", style = tipos.chicoNegrita, color = c.acentoTexto)
                    Text("${aDdMmAa(p.inicio)} a ${aDdMmAa(p.fin)}", style = tipos.textoNegrita, color = c.tinta)
                }
                Boton("Usar estas fechas", { cambiar { it.copy(periodo = it.periodo.copy(inicio = p.inicio, fin = p.fin)) } }, diminuto = true, tipo = TipoBoton.PRINCIPAL)
            }
            Text("Escríbelo así (toca uno)", style = tipos.chicoNegrita, color = c.acentoTexto, modifier = Modifier.padding(top = Medidas.e3, bottom = Medidas.e2))
            Acciones { for (alt in p.alternativas) Boton(alt, { cambiar { it.copy(periodo = it.periodo.copy(etiqueta = alt)) } }, diminuto = true, tipo = TipoBoton.PRINCIPAL) }
        }
    }
    Spacer(Modifier.height(Medidas.e3))
    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
        CampoFecha("Primer día del periodo", r.periodo.inicio, { v -> cambiar { it.copy(periodo = it.periodo.copy(inicio = v)) } }, Modifier.weight(1f), error = hs.errorDe("periodo.inicio"), aviso = hs.avisoDe("periodo.inicio"), obligatorio = true)
        CampoFecha("Último día del periodo", r.periodo.fin, { v -> cambiar { it.copy(periodo = it.periodo.copy(fin = v)) } }, Modifier.weight(1f), pista = "Las compras que rindes van de un día al otro.", obligatorio = true)
    }
    Spacer(Modifier.height(Medidas.e3))
    Campo("Mes de rendición", r.periodo.etiqueta, { v -> cambiar { it.copy(periodo = it.periodo.copy(etiqueta = v)) } }, pista = "Lo que se escribe en la hoja, en «Mes de rendición».", error = hs.errorDe("periodo.etiqueta"), placeholder = "Ej.: Setiembre 2026", obligatorio = true)
}

@Composable
private fun PasoIngresos(r: Rendicion, hs: List<Hallazgo>, cambiar: ((Rendicion) -> Rendicion) -> Unit) {
    TituloSeccion("Ingresos")
    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
        CampoSoles("Monto del subsidio (S/)", r.montoSubsidioCent, { v -> cambiar { it.copy(montoSubsidioCent = v) } }, Modifier.weight(1f), pista = "Lo que te dio la Municipalidad para este periodo.", error = hs.errorDe("montoSubsidio"), obligatorio = true)
        CampoFecha("Fecha de la asignación", r.fechaAsignacion, { v -> cambiar { it.copy(fechaAsignacion = v) } }, Modifier.weight(1f), pista = "El día que recibiste ese dinero.", error = hs.errorDe("fechaAsignacion"), aviso = hs.avisoDe("fechaAsignacion"), obligatorio = true)
    }
}

@Composable
private fun PasoCompras(
    r: Rendicion, hs: List<Hallazgo>, abierto: String?, alAbrir: (String?) -> Unit,
    cambiar: ((Rendicion) -> Rendicion) -> Unit, cambiarEgreso: (String, (Egreso) -> Egreso) -> Unit
) {
    val c = colores
    val avisos = LocalAvisos.current
    TituloSeccion("Egresos")
    Pista("Una ficha por boleta, copiada tal cual. Si dice «varios», márcalo y escribe solo el total.", Modifier.padding(bottom = Medidas.e3))
    hs.errorDe("egresos")?.let { Text(it, style = tipos.chico, color = c.error, modifier = Modifier.padding(bottom = Medidas.e2)) }
    Superficie {
        r.egresos.forEachIndexed { i, f ->
            val campo = "egreso.${f.id}"
            val conError = hs.any { it.campo == campo && it.severidad == Severidad.ERROR && !it.falta }
            val conAviso = hs.any { it.campo == campo && it.severidad == Severidad.ADVERTENCIA }
            val estaAbierta = abierto == f.id || (abierto == null && !egresoCompleto(f))
            Row(
                Modifier.fillMaxWidth().background(if (estaAbierta) c.superficie2 else c.superficie).clickable(role = Role.Button) { alAbrir(if (estaAbierta) "" else f.id) }
                    .defaultMinSize(minHeight = 56.dp).padding(Medidas.e4, Medidas.e3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)
            ) {
                Text("Compra ${i + 1}", style = tipos.textoNegrita, color = c.titulo)
                Text(resumenEgreso(f), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.weight(1f))
                if (conError) Pastilla("con error", TonoPastilla.ERROR) else if (conAviso) Pastilla("revisar", TonoPastilla.ADVERTENCIA)
                Icono("flecha", 18.dp, c.tintaSuave, Modifier.rotate(if (estaAbierta) 90f else 0f))
            }
            if (estaAbierta) {
                Column(Modifier.padding(Medidas.e4, Medidas.e2, Medidas.e4, Medidas.e4)) {
                    val mensajes = hs.filter { it.campo == campo && !(it.severidad == Severidad.ERROR && it.falta) }
                    for (m in mensajes) Text(m.mensaje, style = tipos.chico, color = if (m.severidad == Severidad.ERROR) c.error else c.aviso, modifier = Modifier.padding(bottom = Medidas.e2))
                    Boton("Quitar la compra ${i + 1}", {
                        val antes = r.egresos
                        cambiar { x -> x.copy(egresos = x.egresos.filter { it.id != f.id }.ifEmpty { listOf(nuevoEgreso()) }) }
                        avisos.mostrar("Quitaste la compra ${i + 1}") { cambiar { x -> x.copy(egresos = antes) } }
                    }, Modifier.padding(bottom = 10.dp), diminuto = true, tipo = TipoBoton.PELIGRO)
                    CampoFecha("Fecha de la compra", f.fechaCompra, { v -> cambiarEgreso(f.id) { it.copy(fechaCompra = v) } }, pista = "La fecha que dice la boleta.")
                    Spacer(Modifier.height(10.dp))
                    Campo("Descripción de la compra", f.descripcion, { v -> cambiarEgreso(f.id) { it.copy(descripcion = v) } }, pista = "Qué compraste. Ej.: Arroz superior.")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                        var cantidad by remember(f.id) { mutableStateOf(f.cantidad?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
                        Campo("Cantidad", cantidad, { v -> cantidad = v; cambiarEgreso(f.id) { it.copy(cantidad = aNumero(v)) } }, Modifier.weight(1f), decimal = true, habilitado = !f.cantidadVarios)
                        Column(Modifier.weight(1f)) {
                            Leyenda("Unidad de medida")
                            var unidadAbierta by remember { mutableStateOf(false) }
                            Box {
                                Ficha(f.unidadMedida.ifEmpty { "—" }, f.unidadMedida.isNotEmpty(), { unidadAbierta = true }, Modifier.fillMaxWidth(), minAlto = 48.dp)
                                androidx.compose.material3.DropdownMenu(expanded = unidadAbierta, onDismissRequest = { unidadAbierta = false }, containerColor = c.superficie) {
                                    for (u in listOf("—") + UNIDADES) androidx.compose.material3.DropdownMenuItem(text = { Text(u, style = tipos.texto, color = c.tinta) }, onClick = { unidadAbierta = false; cambiarEgreso(f.id) { it.copy(unidadMedida = if (u == "—") "" else u) } })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Medidas.e2))
                    Ficha("La boleta dice «varios» en la cantidad", f.cantidadVarios, { cambiarEgreso(f.id) { it.copy(cantidadVarios = !it.cantidadVarios, cantidad = if (!it.cantidadVarios) null else it.cantidad) } })
                    Spacer(Modifier.height(10.dp))
                    Leyenda("Origen del dinero")
                    // Lo comprado con dinero propio no va en el B-1: va abajo, en «Gasto propio».
                    // Una compra que ya lo tenía lo sigue mostrando, para poder cambiarlo.
                    Opciones(ORIGENES.filter { it.first != "aporte_propio" || f.origenFondo == "aporte_propio" }, { it == f.origenFondo }, { v -> cambiarEgreso(f.id) { it.copy(origenFondo = v) } })
                    Pista("No va al formato. El subsidio municipal es solo para alimentos.", Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    Campo("RUC del proveedor", f.rucProveedor, { v -> cambiarEgreso(f.id) { it.copy(rucProveedor = v) } }, pista = "11 cifras; está arriba en la boleta.", soloDigitos = true, maxLargo = 11)
                    Spacer(Modifier.height(10.dp))
                    Campo("Nombre del proveedor", f.proveedorNombre, { v -> cambiarEgreso(f.id) { it.copy(proveedorNombre = v) } }, pista = "No va al formato.")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                        Campo("Serie", f.boletaSerie, { v -> cambiarEgreso(f.id) { it.copy(boletaSerie = v) } }, Modifier.weight(1f), pista = "Ej.: B001", placeholder = "E001")
                        Campo("Correlativo", f.boletaCorrelativo, { v -> cambiarEgreso(f.id) { it.copy(boletaCorrelativo = v) } }, Modifier.weight(1f), pista = "Ej.: 000143", placeholder = "000143")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                        CampoSoles("Precio unitario (S/)", f.precioUnitarioCent, { v -> cambiarEgreso(f.id) { it.copy(precioUnitarioCent = v) } }, Modifier.weight(1f), pista = "Precio de uno. Si no aparece, marca «varios».", habilitado = !f.precioUnitarioVarios)
                        CampoSoles("Monto total (S/)", f.montoTotalCent, { v -> cambiarEgreso(f.id) { it.copy(montoTotalCent = v) } }, Modifier.weight(1f), pista = "Lo que pagaste por esta compra.")
                    }
                    Spacer(Modifier.height(Medidas.e2))
                    Ficha("La boleta dice «varios» en el precio", f.precioUnitarioVarios, { cambiarEgreso(f.id) { it.copy(precioUnitarioVarios = !it.precioUnitarioVarios, precioUnitarioCent = if (!it.precioUnitarioVarios) null else it.precioUnitarioCent) } })
                }
            }
            if (i < r.egresos.size - 1) Separador()
        }
    }
    // Agregar es el botón flotante «Compra»: una sola acción principal por pantalla.
    Text("${cuantos(r.egresos.size, "compra")}. La hoja del formato tiene 17 filas.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))

    // ---- totales: se suman solos; de solo lectura ----
    val t = totales(r)
    Superficie(Modifier.padding(top = Medidas.e4)) {
        Column(Modifier.padding(Medidas.e4, Medidas.e3)) {
            FilaTotal("Gastos con el subsidio económico", t.conSubsidio?.let { "S/ ${formatearSoles(it)}" })
            Separador()
            // "Registrar de corresponder": si es cero, el renglón va vacío.
            FilaTotal("Aporte del centro de atención (registrar de corresponder)", t.aporte?.takeIf { it != 0L }?.let { "S/ ${formatearSoles(it)}" })
            Separador()
            FilaTotal("Total de rendición de gastos", t.total?.let { "S/ ${formatearSoles(it)}" }, destacado = true)
            Text("Se suman solos de tus compras.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 10.dp))
            val po = gastoPorOrigen(r)
            Chips(Modifier.padding(top = Medidas.e2)) {
                if (po.subsidio != 0L) Chip("Subsidio", "S/ ${formatearSoles(po.subsidio)}")
                if (po.ayudaSocial != 0L) Chip("Ayuda social", "S/ ${formatearSoles(po.ayudaSocial)}")
                if (po.aportePropio != 0L) Chip("Aporte propio", "S/ ${formatearSoles(po.aportePropio)}")
            }
            for (m in hs.filter { it.campo == "totales" }) Text(m.mensaje, style = tipos.chico, color = if (m.severidad == Severidad.ERROR) c.error else c.aviso, modifier = Modifier.padding(top = Medidas.e2))
        }
    }

    // ---- gasto propio: fuera del B-1 ----
    Spacer(Modifier.height(Medidas.e5))
    TituloSeccion("Gasto propio (no va en el B-1)", apoyo = true)
    Pista("Lo comprado con su propio dinero, como azúcar extra. No va en el B-1: sale en el reporte del mes.", Modifier.padding(bottom = Medidas.e3))
    if (r.gastosPropios.isNotEmpty()) {
        Superficie {
            r.gastosPropios.forEachIndexed { i, g ->
                Column(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3)) {
                    fun cambiarGasto(cambio: (GastoPropio) -> GastoPropio) = cambiar { x -> x.copy(gastosPropios = x.gastosPropios.map { if (it.id == g.id) cambio(it) else it }) }
                    CampoFecha("Fecha", g.fecha, { v -> cambiarGasto { it.copy(fecha = v) } })
                    Spacer(Modifier.height(Medidas.e2))
                    Campo("Qué compraron", g.descripcion, { v -> cambiarGasto { it.copy(descripcion = v) } })
                    Spacer(Modifier.height(Medidas.e2))
                    CampoSoles("Cuánto (S/)", g.montoCent, { v -> cambiarGasto { it.copy(montoCent = v) } })
                    Boton("Quitar este gasto", {
                        val antes = r.gastosPropios
                        cambiar { x -> x.copy(gastosPropios = x.gastosPropios.filter { it.id != g.id }) }
                        avisos.mostrar("Quitaste el gasto propio") { cambiar { x -> x.copy(gastosPropios = antes) } }
                    }, Modifier.padding(top = Medidas.e2), diminuto = true, tipo = TipoBoton.PELIGRO)
                }
                if (i < r.gastosPropios.size - 1) Separador()
            }
        }
        val suma = r.gastosPropios.mapNotNull { it.montoCent }.sum()
        Text("Gasto propio: S/ ${formatearSoles(suma)}. No va en el B-1.", style = tipos.chicoFuerte, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
    }
    Boton("Anotar un gasto propio", {
        cambiar { x -> x.copy(gastosPropios = x.gastosPropios + GastoPropio(id = uuid(), fecha = hoyIso())) }
    }, Modifier.padding(top = Medidas.e3), diminuto = true, icono = "mas")
}

@Composable
private fun FilaTotal(rotulo: String, valor: String?, destacado: Boolean = false) {
    val c = colores
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = if (destacado) tipos.textoNegrita else tipos.texto, color = c.tinta, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Medidas.e3))
        Text(valor ?: "—", style = if (destacado) tipos.seccion.copy(fontSize = tipos.seccion.fontSize * 1.25f) else tipos.textoNegrita, color = if (valor == null) c.tintaSuave else c.titulo)
    }
}

@Composable
private fun PasoFirmas(r: Rendicion, hs: List<Hallazgo>, cambiar: ((Rendicion) -> Rendicion) -> Unit) {
    TituloSeccion("Firmas")
    Pista("Lo que va debajo de cada línea de firma. El sello se pone a mano.", Modifier.padding(bottom = Medidas.e3))
    Leyenda("Presidenta")
    Campo("Nombre y apellido", r.nombrePresidentaFirma, { v -> cambiar { it.copy(nombrePresidentaFirma = v) } }, pista = "Como en su DNI.", aviso = hs.avisoDe("nombrePresidentaFirma"))
    Spacer(Modifier.height(Medidas.e2))
    Campo("DNI N.º", r.dniPresidentaFirma, { v -> cambiar { it.copy(dniPresidentaFirma = v) } }, aviso = hs.avisoDe("dniPresidentaFirma"), soloDigitos = true, maxLargo = 8)
    Spacer(Modifier.height(Medidas.e4))
    Leyenda("Tesorera")
    Campo("Nombre y apellido", r.nombreTesorera, { v -> cambiar { it.copy(nombreTesorera = v) } }, pista = "Como en su DNI.", aviso = hs.avisoDe("nombreTesorera"))
    Spacer(Modifier.height(Medidas.e2))
    Campo("DNI N.º", r.dniTesorera, { v -> cambiar { it.copy(dniTesorera = v) } }, error = hs.errorDe("dniTesorera"), soloDigitos = true, maxLargo = 8)
}

@Composable
private fun PasoRevisar(
    r: Rendicion, rendiciones: List<Rendicion>, indice: Int, hs: List<Hallazgo>,
    irACampo: (String) -> Unit, abrir: (String) -> Unit, empezarMes: () -> Unit,
    listoParaExportar: () -> Boolean, verHoja: () -> Unit
) {
    val c = colores
    TituloSeccion("Revisar antes de entregar")
    val errores = hs.filter { it.severidad == Severidad.ERROR }.sortedBy { pasoDe(it.campo) }
    if (errores.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icono("ok", 20.dp, c.ok)
            Spacer(Modifier.width(Medidas.e2))
            ParaLeer("El B-1 está completo. Revísalo con la tesorera y el fiscal antes de entregarlo.")
            Text("El B-1 está completo. Revísalo con la tesorera y el fiscal antes de entregarlo.", style = tipos.textoFuerte, color = c.ok)
        }
    } else {
        ParaLeer((if (errores.size == 1) "Antes de entregarlo, falta esto: " else "Antes de entregarlo, faltan ${errores.size} cosas: ") + errores.joinToString(". ") { it.mensaje.removeSuffix(".") })
        Text(if (errores.size == 1) "Antes de entregarlo, falta esto:" else "Antes de entregarlo, faltan ${errores.size} cosas:", style = tipos.texto, color = c.tinta, modifier = Modifier.padding(bottom = Medidas.e2))
        Superficie {
            errores.forEachIndexed { i, h ->
                Row(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                    Pastilla("Paso ${pasoDe(h.campo)}", TonoPastilla.ADVERTENCIA)
                    Text(h.mensaje, style = tipos.texto, color = c.tinta, modifier = Modifier.weight(1f))
                    Boton(if (h.falta) "Completar" else "Corregir", { irACampo(h.campo) }, diminuto = true)
                }
                if (i < errores.size - 1) Separador()
            }
        }
    }
    val t = totales(r)
    if (t.total != null) {
        Chips(Modifier.padding(top = Medidas.e3)) {
            Chip("Subsidio", "S/ ${formatearSoles(r.montoSubsidioCent)}")
            Chip("Compras", "S/ ${formatearSoles(t.total)}")
            Chip("Aporte de la olla", "S/ ${formatearSoles(t.aporte)}")
        }
    }
    Spacer(Modifier.height(Medidas.e4))
    Boton("Ver cómo queda en la hoja", verHoja, icono = "boleta")
    Spacer(Modifier.height(Medidas.e5))
    TituloSeccion("Exportar el B-1")
    Pista("En PDF o Excel, para guardar o mandar por WhatsApp.", Modifier.padding(bottom = Medidas.e3))
    val etiqueta = r.periodo.etiqueta.trim().ifEmpty { "en curso" }
    BotonesExportar(
        pdf = { Documento(pdfDelB1(hojasDelB1(r), selloAhora(), "Formato B-1 $etiqueta"), "B-1 $etiqueta", "Formato B-1 $etiqueta (copia de referencia)", AlElegir.B1(r.id, "pdf")) },
        excel = { Documento(excelDelB1(r), "B-1 $etiqueta", "Formato B-1 $etiqueta", AlElegir.B1(r.id, "excel")) },
        puede = listoParaExportar
    )

    // ---- meses anteriores ----
    Spacer(Modifier.height(Medidas.e5))
    TituloSeccion("Meses anteriores", apoyo = true)
    if (indice == 0) {
        Pista("Al entregar este B-1, empieza el del mes siguiente. Este queda guardado abajo.", Modifier.padding(bottom = Medidas.e3))
        Boton("Empezar el B-1 del mes siguiente", empezarMes)
    }
    val anteriores = rendiciones.drop(1)
    if (anteriores.isNotEmpty()) {
        Superficie(Modifier.padding(top = Medidas.e3)) {
            rendiciones.forEachIndexed { i, x ->
                if (i == 0) return@forEachIndexed
                val tx = totales(x)
                Row(Modifier.fillMaxWidth().background(if (i == indice) c.superficie2 else c.superficie).padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                    Text(nombreDelMes(x), style = tipos.textoFuerte, color = c.titulo, modifier = Modifier.weight(1f))
                    Text(if (tx.total == null) "sin subsidio" else "S/ ${formatearSoles(tx.total)}", style = tipos.chico, color = c.tintaSuave)
                    if (i == indice) Text("abierto", style = tipos.chicoNegrita, color = c.acentoTexto) else Boton("Abrir", { abrir(x.id) }, diminuto = true)
                }
                if (i < rendiciones.size - 1) Separador()
            }
        }
    }
}
