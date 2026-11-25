// El Formato B-1 por pasos: una sección a la vez, con «Atrás / Siguiente» y una barra
// que dice dónde estás y qué paso tiene algo pendiente. Las sugerencias se ofrecen;
// nunca se aplican solas.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.ui.layout.onGloballyPositioned
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.nucleo.documentos.hojasDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelB1
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.componentes.SelectorDesplegable
import io.github.tmrainer.asistencia.ui.componentes.Documento
import io.github.tmrainer.asistencia.ui.componentes.BotonesExportar
import io.github.tmrainer.asistencia.AlElegir
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.alertas.Nivel
import io.github.tmrainer.asistencia.nucleo.alertas.alertaProximaEntrega
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.calculos.conTotalCalculado
import io.github.tmrainer.asistencia.nucleo.calculos.totalDeLaFila
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.aNumero
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import io.github.tmrainer.asistencia.nucleo.sugerencias.conDatosDeLaOlla
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasB1
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasOlla
import io.github.tmrainer.asistencia.nucleo.validaciones.Hallazgo
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.validarRendicion
import io.github.tmrainer.asistencia.nucleo.calculos.codigoB1
import io.github.tmrainer.asistencia.nucleo.calculos.periodoDelMes
import io.github.tmrainer.asistencia.nucleo.modelo.Periodo
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import java.time.LocalDate
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.ayudaB1
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.PaginaParaGuia
import io.github.tmrainer.asistencia.ui.Ruta
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
import io.github.tmrainer.asistencia.ui.componentes.FilaCampos
import io.github.tmrainer.asistencia.ui.componentes.ventana
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

// Sin paso de firmas (decisión del 26-10-2026): el B-1 se completa con los datos de la olla, el
// periodo, los ingresos y las compras. Quién firma se escribe a mano, en el papel.
private val PASOS = listOf("Centro de atención", "Periodo", "Ingresos (dinero recibido)", "Compras (egresos)", "Revisar")
private val CAMPOS_FIRMA = setOf("nombrePresidentaFirma", "dniPresidentaFirma", "nombreTesorera", "dniTesorera")

// La fecha de rendición tampoco se pide (decisión del 22-11-2026): la pone la app, el día en que se arma el B-1.
private val NO_SE_PIDEN = CAMPOS_FIRMA + "fechaRendicion"

/** Lo que la app revisa del B-1: lo de [validarRendicion], menos lo que aquí no se pide (firmas y fecha de rendición). */
internal fun hallazgosDelB1(r: Rendicion): List<Hallazgo> = validarRendicion(r).filter { it.campo !in NO_SE_PIDEN }

/** El mes que sigue al del B-1 [r]; sin mes elegido, el de hoy. */
private fun mesSiguiente(r: Rendicion): Periodo {
    val f = aFecha(r.periodo.inicio)?.plusMonths(1) ?: LocalDate.now()
    return periodoDelMes(f.year, f.monthValue)
}
// «varios» no está aquí: lo pone «¿Cómo viene la boleta?» → «Varios juntos».
private val UNIDADES = listOf("Kg", "g", "L", "ml", "unid", "saco", "bolsa", "caja")

/** Paso de cada hallazgo, por la sección donde está su campo. */
private fun pasoDe(campo: String): Int = when {
    campo in listOf("tipoCentro", "nombreCentro", "codigoPca", "nombrePresidenta", "dniPresidenta", "celular") -> 1
    campo.startsWith("periodo.") || campo == "fechaRendicion" -> 2
    campo == "montoSubsidio" || campo == "fechaAsignacion" -> 3
    campo == "egresos" || campo.startsWith("egreso.") || campo == "totales" -> 4
    else -> 5
}

private fun List<Hallazgo>.errorDe(campo: String): String? = firstOrNull { it.campo == campo && it.severidad == Severidad.ERROR && !it.falta }?.mensaje
/** Un aviso aceptado vuelve a frenar si cambia lo que dice (cambió el dato). */
private fun clave(h: Hallazgo) = "${h.campo}|${h.mensaje}"
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

    // Campos puestos desde los datos de la olla o aceptados de una sugerencia, marcados hasta que se editen.
    val propuestos = remember { mutableStateOf(setOf<String>()) }
    // rendiciones[0] es el B-1 en curso. Sin ninguno, se crea uno con los datos de la olla ya puestos (y marcados).
    LaunchedEffect(rendiciones.isEmpty()) {
        if (rendiciones.isEmpty()) {
            val (nueva, puestos) = conDatosDeLaOlla(nuevaRendicion(), estado.config.value)
            estado.cambiarRendiciones { listOf(nueva) }
            propuestos.value = puestos
        }
    }
    var verId by remember { mutableStateOf<String?>(null) }
    val indice = maxOf(0, rendiciones.indexOfFirst { it.id == verId })
    val rendicion = rendiciones.getOrNull(indice) ?: return
    LaunchedEffect(rendicion.id, rendicion.egresos.isEmpty()) {
        if (rendicion.egresos.isEmpty()) estado.cambiarRendicion(rendicion.id) { it.copy(egresos = listOf(nuevoEgreso())) }
    }
    fun cambiar(cambio: (Rendicion) -> Rendicion) = estado.cambiarRendicion(rendicion.id, cambio)
    fun cambiarEgreso(id: String, cambio: (Egreso) -> Egreso) = cambiar { r -> r.copy(egresos = r.egresos.map { if (it.id == id) cambio(it) else it }) }

    val hallazgos = remember(rendicion) { hallazgosDelB1(rendicion) }
    val pendientesPorPaso = remember(hallazgos) { hallazgos.filter { it.severidad == Severidad.ERROR }.map { pasoDe(it.campo) }.toSet() }

    // Por B-1: al abrir otro desde «Meses anteriores», empieza en su primer paso con algo que corregir.
    var pasoActual by remember(rendicion.id) {
        mutableIntStateOf(
            if (argumento == "compras") 4
            else hallazgosDelB1(rendicion).filter { it.severidad == Severidad.ERROR }.map { pasoDe(it.campo) }.minOrNull() ?: PASOS.size
        )
    }
    var avisoPasos by remember { mutableStateOf("") }
    // Los avisos también frenan el paso: se corrigen o se aceptan con «Está bien así».
    var avisosPorAceptar by remember { mutableStateOf(listOf<Hallazgo>()) }
    var aceptados by remember(rendicion.id) { mutableStateOf(setOf<String>()) }
    var destino by remember { mutableIntStateOf(0) }
    // El alto de la barra de abajo: el aviso de «Deshacer» y el botón «Compra» van encima, sin taparla.
    var altoBarra by remember { mutableStateOf(0.dp) }
    val densidad = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { avisos.sobreBarra = 0.dp } }
    // «Para seguir, revisa…» se va solo cuando el paso ya no tiene errores (corregido o quitado).
    LaunchedEffect(hallazgos, pasoActual) {
        if (avisosPorAceptar.isNotEmpty()) {
            val siguen = hallazgos.map(::clave).toSet()
            if (avisosPorAceptar.none { clave(it) in siguen } || pasoDe(avisosPorAceptar[0].campo) != pasoActual) { avisosPorAceptar = emptyList(); avisoPasos = "" }
        } else if (avisoPasos.isNotEmpty() && hallazgos.none { it.severidad == Severidad.ERROR && pasoDe(it.campo) == pasoActual }) avisoPasos = ""
    }
    val desplazamiento = rememberScrollState()
    PaginaParaGuia(desplazamiento)
    var egresoAbierto by remember { mutableStateOf<String?>(null) }
    var confirmarMes by remember { mutableStateOf(false) }

    fun irAPaso(n: Int) {
        pasoActual = n.coerceIn(1, PASOS.size)
        alcance.launch { desplazamiento.scrollTo(0) }
    }

    /**
     * No se avanza con un dato obligatorio pendiente ni con un aviso sin mirar: se dice cuál y se lleva ahí.
     * Lo obligatorio se mira desde el paso 1 («Anotar una compra» entra directo a Compras, sin pasar
     * por los anteriores); los avisos, solo los de los pasos que se dejan atrás ahora.
     */
    fun bloqueoHasta(n: Int): Boolean {
        val enCamino = hallazgosDelB1(rendicion).filter { pasoDe(it.campo) < n }
        val pendientes = enCamino.filter { it.severidad == Severidad.ERROR }.sortedBy { pasoDe(it.campo) }
        avisosPorAceptar = emptyList()
        if (pendientes.isEmpty()) {
            val porMirar = enCamino.filter { it.severidad == Severidad.ADVERTENCIA && pasoDe(it.campo) >= pasoActual && clave(it) !in aceptados }.sortedBy { pasoDe(it.campo) }
            if (porMirar.isEmpty()) return false
            val paso = pasoDe(porMirar[0].campo)
            if (paso != pasoActual) irAPaso(paso)
            avisosPorAceptar = porMirar.filter { pasoDe(it.campo) == paso }
            destino = n
            avisoPasos = "Revisa antes de seguir: ${avisosPorAceptar.take(2).joinToString(" ") { it.mensaje }}"
            vibrar(contexto, 60)
            if (porMirar[0].campo.startsWith("egreso.")) egresoAbierto = porMirar[0].campo.removePrefix("egreso.")
            return true
        }
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
        avisoPasos = ""; avisosPorAceptar = emptyList()
        irAPaso(n)
    }

    fun irACampo(campo: String) {
        irAPaso(pasoDe(campo))
        if (campo.startsWith("egreso.")) egresoAbierto = campo.removePrefix("egreso.")
    }

    /** Exportar pide el B-1 completo: si falta algo, se dice qué y se lleva ahí. */
    fun listoParaExportar(): Boolean {
        val pendientes = hallazgosDelB1(rendicion).filter { it.severidad == Severidad.ERROR }.sortedBy { pasoDe(it.campo) }
        if (pendientes.isEmpty()) { ctx.estado.guardarPendiente(); return true }
        ctx.avisos.mostrar("Antes de exportar, completa el B-1: " +
            pendientes.take(2).joinToString("; ") { it.mensaje.removeSuffix(".").replace(Regex("^Faltan? "), "falta ") } +
            (if (pendientes.size > 2) " y ${pendientes.size - 2} más" else "") + ".")
        irACampo(pendientes[0].campo)
        return false
    }

    /** «Completar ahora»: al primer paso anterior con algo pendiente, diciendo qué falta. */
    fun completarAntes(paso: Int) {
        irAPaso(paso)
        bloqueoHasta(paso + 1)
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
            // El nombre del B-1 abierto: el mes y el año («Noviembre-2026»).
            Cabecera(ctx, "Formato B-1", codigoB1(rendicion.periodo, LocalDate.now(), cerrado = true) ?: "Rendición de cuentas", guiaSegun = { ayudaB1(ctx.preferencias, pasoActual) })
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
                PanelHallazgos(hallazgos, Modifier.objetivoGuia("b1.revisar"), alIrA = { irACampo(it) })

                Row(Modifier.objetivoGuia("b1.barra").fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PASOS.forEachIndexed { i, nombre ->
                        val n = i + 1
                        // Los que vienen, solo con borde: rellenos de verde pálido casi no se veían.
                        val color = when {
                            n == pasoActual -> c.acento
                            n in pendientesPorPaso -> c.azul
                            n < pasoActual -> c.acentoTexto
                            else -> null
                        }
                        Box(
                            Modifier.weight(1f).objetivoGuia("b1.paso$n").height(48.dp).clip(RoundedCornerShape(Medidas.rS)).clickable(role = Role.Button) { avanzarA(n) }
                                .semantics { contentDescription = "Paso $n: $nombre" + if (n in pendientesPorPaso) ", le falta algo" else "" },
                            contentAlignment = Alignment.Center
                        ) {
                            val barra = Modifier.fillMaxWidth().height(if (n == pasoActual) 8.dp else 6.dp).clip(RoundedCornerShape(999.dp))
                            Box(if (color != null) barra.background(color) else barra.border(1.5.dp, c.contorno, RoundedCornerShape(999.dp)))
                        }
                    }
                }
                // Lo que dice el azul de la barra también va en palabras (el color nunca va solo).
                val faltan = pendientesPorPaso.sorted()
                val queFalta = when (faltan.size) {
                    0 -> ""
                    1 -> ". Al paso ${faltan[0]} le falta algo"
                    else -> ". A los pasos ${faltan.dropLast(1).joinToString(", ")} y ${faltan.last()} les falta algo"
                }
                Row(Modifier.fillMaxWidth().padding(bottom = Medidas.e3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                    Text("Paso $pasoActual de ${PASOS.size}$queFalta", style = tipos.pista, color = c.tintaSuave, modifier = Modifier.weight(1f).objetivoGuia("b1.barraTexto"))
                    // La hoja se puede mirar en cualquier paso, sin exportar. En «Revisar» tiene su botón grande.
                    if (pasoActual < PASOS.size) Boton("Ver la hoja", { ctx.ir(Ruta.HOJA, rendicion.id) }, diminuto = true, icono = "boleta")
                }
                // «Anotar una compra» entra directo a Compras: lo que quedó pendiente antes se dice, y se puede ir.
                val antes = faltan.filter { it < pasoActual }
                if (pasoActual < PASOS.size && antes.isNotEmpty()) {
                    val nombres = antes.map { "$it (${PASOS[it - 1].replace(Regex(" \\(.*\\)$"), "")})" }
                    val cuales = if (nombres.size > 1) "los pasos ${nombres.dropLast(1).joinToString(", ")} y ${nombres.last()}" else "el paso ${nombres[0]}"
                    FranjaEstado(
                        "Para entregar el B-1 falta completar $cuales. Puedes seguir anotando y completarlo después.",
                        TonoPastilla.ADVERTENCIA, Modifier.padding(bottom = Medidas.e3).objetivoGuia("b1.faltaAntes"), icono = "aviso",
                        boton = "Completar ahora" to { completarAntes(antes[0]) }
                    )
                }

                when (pasoActual) {
                    1 -> PasoCentro(rendicion, hallazgos, config, perfil, propuestos, ::cambiar)
                    2 -> PasoPeriodo(rendicion, hallazgos, ::cambiar)
                    3 -> PasoIngresos(rendicion, hallazgos, config, propuestos, ::cambiar)
                    4 -> PasoCompras(rendicion, hallazgos, egresoAbierto, { egresoAbierto = it }, ::cambiar, ::cambiarEgreso, agregar = ::agregarCompra)
                    else -> PasoRevisar(
                        rendicion, rendiciones, indice, hallazgos, ::irACampo, abrir = { verId = it }, empezarMes = { confirmarMes = true },
                        listoParaExportar = ::listoParaExportar, verHoja = { ctx.ir(Ruta.HOJA, rendicion.id) }
                    )
                }
                EstadoGuardado(guardado, "Se guarda solo, mientras escribes.")
                Text("El B-1 es una declaración jurada: revisa todo.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e5))
            }
            // Atrás / Siguiente, pegados abajo.
            Separador()
            Column(Modifier.fillMaxWidth().background(c.superficie).onGloballyPositioned {
                altoBarra = with(densidad) { it.size.height.toDp() }; avisos.sobreBarra = altoBarra
            }.padding(Medidas.e4, 10.dp)) {
                if (avisoPasos.isNotEmpty()) {
                    val esAviso = avisosPorAceptar.isNotEmpty()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(if (esAviso) c.avisoFondo else c.errorFondo).padding(Medidas.e4, Medidas.e3)) {
                        Text(avisoPasos, style = tipos.textoFuerte, color = if (esAviso) c.aviso else c.error)
                        if (esAviso) Boton("Está bien así", {
                            aceptados = aceptados + avisosPorAceptar.map(::clave)
                            avanzarA(destino)
                        }, Modifier.padding(top = 10.dp), diminuto = true)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth().objetivoGuia("b1.botones"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Boton("Atrás", { irAPaso(pasoActual - 1) }, habilitado = pasoActual > 1)
                    if (pasoActual < PASOS.size) {
                        Boton("Siguiente: ${PASOS[pasoActual].replace(Regex(" \\(.*\\)$"), "")}", { avanzarA(pasoActual + 1) }, Modifier.weight(1f), tipo = TipoBoton.PRINCIPAL)
                    }
                }
            }
        }
        // En una pantalla baja no flota (tapaba «Ver la hoja»): va al final de la lista de compras.
        if (pasoActual == 4 && !ventana().baja) BotonFlotante("Compra", ::agregarCompra, Modifier.align(Alignment.BottomEnd).padding(end = Medidas.e4, bottom = altoBarra + Medidas.e3).objetivoGuia("b1.compra"))
    }

    if (confirmarMes) {
        AlertDialog(
            onDismissRequest = { confirmarMes = false },
            containerColor = c.superficie,
            title = { Text("¿Cerrar ${nombreDelMes(rendicion)}?", style = tipos.seccion, color = c.titulo) },
            text = {
                val nombre = codigoB1(rendicion.periodo, LocalDate.now(), cerrado = true) ?: nombreDelMes(rendicion)
                Text("Se arma el B-1 «$nombre» con todo el mes, con fecha de rendición de hoy, y empieza el de ${mesSiguiente(rendicion).etiqueta}. " +
                    "Al cerrarlo quedas en ${nombreDelMes(rendicion)}, listo para mandarlo.", style = tipos.texto, color = c.tinta)
            },
            confirmButton = {
                Boton("Cerrar el mes", {
                    confirmarMes = false
                    val cerrado = rendicion.id
                    cambiar { it.copy(fechaRendicion = hoyIso()) }
                    // El mes que sigue, ya elegido; el encabezado, las firmas y el subsidio, de los datos de la olla (marcados).
                    val (nueva, puestos) = conDatosDeLaOlla(nuevaRendicion().copy(periodo = mesSiguiente(rendicion)), estado.config.value)
                    estado.cambiarRendiciones { listOf(nueva) + it }
                    propuestos.value = puestos
                    // Se queda en el B-1 cerrado, en «Revisar», para mandarlo ya con el nombre del mes.
                    verId = cerrado
                    avisos.mostrar("Cerraste ${nombreDelMes(rendicion)}. Mándalo desde aquí.")
                }, tipo = TipoBoton.PRINCIPAL)
            },
            dismissButton = { Boton("Cancelar", { confirmarMes = false }) }
        )
    }
}

@Composable
private fun BannerEntrega(ctx: Contexto, calendario: io.github.tmrainer.asistencia.nucleo.modelo.Calendario, hoy: String) {
    val alerta = alertaProximaEntrega(calendario, hoy, ctx.preferencias.desdeEntregas) ?: return
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

@Composable
private fun PasoCentro(
    r: Rendicion, hs: List<Hallazgo>, config: io.github.tmrainer.asistencia.nucleo.modelo.Config,
    perfil: io.github.tmrainer.asistencia.nucleo.modelo.Perfil, propuestos: androidx.compose.runtime.MutableState<Set<String>>,
    cambiar: ((Rendicion) -> Rendicion) -> Unit
) {
    val c = colores
    TituloSeccion("Centro de atención")
    Pista("Cópialos de tu B-1 anterior o de tu resolución. Todos son obligatorios.", Modifier.padding(bottom = Medidas.e3))

    // La app es solo para ollas comunes: el tipo va fijo.
    LaunchedEffect(r.id, r.tipoCentro) { if (r.tipoCentro != "olla_comun") cambiar { it.copy(tipoCentro = "olla_comun") } }
    // Lo que falte se ofrece (no se aplica solo): los datos de la olla mandan; después el B-1 anterior y el perfil.
    val props = sugerenciasB1(config, perfil)
    val valorDe = { campo: String -> when (campo) {
        "nombreCentro" -> r.nombreCentro; "codigoPca" -> r.codigoPca; "distrito" -> r.distrito; "nombrePresidenta" -> r.nombrePresidenta
        "dniPresidenta" -> r.dniPresidenta; "celular" -> r.celular; else -> "x"
    } }
    // Las firmas no se piden: no se proponen.
    val pendientes = props.filter { (campo, _) -> campo !in CAMPOS_FIRMA && valorDe(campo).trim().isEmpty() }
    if (pendientes.isNotEmpty()) {
        BloqueSuave(Modifier.padding(bottom = Medidas.e3).objetivoGuia("b1.guardados")) {
            Text("Tienes datos guardados (${pendientes.values.map { it.procedencia }.distinct().joinToString(" y ")}). Puedes usarlos o escribirlos de nuevo.", style = tipos.pista, color = c.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e2))
            Boton("Usar los datos guardados (${pendientes.size})", {
                cambiar { x ->
                    var y = x
                    for ((campo, p) in pendientes) y = when (campo) {
                        "nombreCentro" -> y.copy(nombreCentro = p.valor); "codigoPca" -> y.copy(codigoPca = p.valor); "distrito" -> y.copy(distrito = p.valor)
                        "nombrePresidenta" -> y.copy(nombrePresidenta = p.valor); "dniPresidenta" -> y.copy(dniPresidenta = p.valor); "celular" -> y.copy(celular = p.valor)
                        else -> y
                    }
                    y
                }
                propuestos.value = propuestos.value + pendientes.keys
            })
        }
    }
    val marca = { campo: String -> if (campo in propuestos.value) "Dato propuesto — revísalo" else null }
    fun escribir(campo: String, f: (Rendicion) -> Rendicion) { propuestos.value = propuestos.value - campo; cambiar(f) }

    Leyenda("Tipo de centro")
    FranjaEstado("Olla común. La app es para ollas comunes: en la hoja sale marcado.", TonoPastilla.OK, Modifier.objetivoGuia("b1.tipo"), icono = "casa")
    hs.errorDe("tipoCentro")?.let { Text(it, style = tipos.chico, color = c.error) }
    Spacer(Modifier.height(Medidas.e3))
    Campo("Nombre del centro de atención", r.nombreCentro, { v -> escribir("nombreCentro") { it.copy(nombreCentro = v) } }, Modifier.objetivoGuia("b1.nombreCentro"), pista = marca("nombreCentro") ?: "Como lo registró la Municipalidad.", error = hs.errorDe("nombreCentro"), placeholder = "Ej.: Olla común Virgen de Fátima", obligatorio = true)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Código del Sistema Informático del PCA N.º", r.codigoPca, { v -> escribir("codigoPca") { it.copy(codigoPca = v) } }, pista = marca("codigoPca") ?: "El código que te dio la Municipalidad.", error = hs.errorDe("codigoPca"), aviso = hs.avisoDe("codigoPca"), placeholder = "Ej.: 098", obligatorio = true, soloDigitos = true)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Distrito", r.distrito, { v -> escribir("distrito") { it.copy(distrito = v) } }, pista = marca("distrito") ?: "El de la Municipalidad que recibe el B-1.", placeholder = "Ej.: Pachacámac", capitalizar = true)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Nombre de la presidenta", r.nombrePresidenta, { v -> escribir("nombrePresidenta") { it.copy(nombrePresidenta = v) } }, pista = marca("nombrePresidenta") ?: "Nombres y apellidos, como en su DNI.", error = hs.errorDe("nombrePresidenta"), obligatorio = true)
    Spacer(Modifier.height(Medidas.e3))
    Campo("DNI", r.dniPresidenta, { v -> escribir("dniPresidenta") { it.copy(dniPresidenta = v) } }, pista = marca("dniPresidenta") ?: "8 cifras.", error = hs.errorDe("dniPresidenta"), placeholder = "12345678", obligatorio = true, soloDigitos = true, maxLargo = 8)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Celular N.º", r.celular, { v -> escribir("celular") { it.copy(celular = v) } }, Modifier.objetivoGuia("b1.celular"), pista = marca("celular") ?: "9 cifras; empieza con 9.", error = hs.errorDe("celular"), placeholder = "987654321", obligatorio = true, soloDigitos = true, maxLargo = 9)
}

@Composable
private fun PasoPeriodo(r: Rendicion, hs: List<Hallazgo>, cambiar: ((Rendicion) -> Rendicion) -> Unit) {
    val c = colores
    TituloSeccion("Mes que se rinde")
    // Solo el mes (decisión del 22-11-2026): sin calendarios. El B-1 va del 1 al último día.
    Pista("El B-1 se entrega cada mes. Elige de qué mes es: va del primer al último día.", Modifier.padding(bottom = Medidas.e3))
    val hoy = LocalDate.now()
    val opciones = (-3..1).map { hoy.plusMonths(it.toLong()) }.map { periodoDelMes(it.year, it.monthValue) }
    val delMes = opciones.firstOrNull { it.inicio == r.periodo.inicio && it.fin == r.periodo.fin }
        ?: codigoB1(r.periodo, hoy, cerrado = true)?.takeIf { !it.contains("-Q") && !it.contains("-S") }?.let { r.periodo }
    // Del más nuevo al más viejo, como en Entregas.
    SelectorDesplegable("Mes", opciones.reversed().map { it.inicio.take(7) to it.etiqueta }, if (delMes != null) r.periodo.inicio.take(7) else null, { clave ->
        val m = opciones.first { it.inicio.take(7) == clave }
        // Una fecha de rendición vieja ya no vale para otro mes: la pone la app al armarlo.
        cambiar { it.copy(periodo = m, fechaRendicion = "") }
    }, Modifier.objetivoGuia("b1.mes"), icono = "calendario", vacio = "Elige el mes")
    (hs.errorDe("periodo.inicio") ?: hs.errorDe("periodo.etiqueta"))?.let { Text(it, style = tipos.chico, color = c.error, modifier = Modifier.padding(top = Medidas.e2)) }
    hs.avisoDe("periodo.inicio")?.let { Text(it, style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = Medidas.e2)) }
    if (delMes == null && r.periodo.inicio.isNotEmpty()) Pista("Este B-1 tiene fechas de antes. Elige un mes para cambiarlas.", Modifier.padding(top = Medidas.e2))
    // Cómo sale según cuándo se manda: antes del cierre, la semana o la quincena; al cerrar, el mes.
    val alCerrar = codigoB1(r.periodo, hoy, cerrado = true)
    val hoySale = codigoB1(r.periodo, hoy, cerrado = false)
    if (alCerrar != null) {
        val texto = if (hoySale != null && hoySale != alCerrar)
            "Al cerrar el mes sale como «$alCerrar». Si lo mandas antes, sale con la semana o la quincena en que va: hoy, «$hoySale»."
        else "Sale como «$alCerrar»."
        FranjaEstado(texto, TonoPastilla.OK, Modifier.padding(top = Medidas.e3).objetivoGuia("b1.codigo"), icono = "boleta")
    }
    Pista("La fecha de rendición la pone la app: el día en que armas el B-1.", Modifier.padding(top = Medidas.e3))
}

@Composable
private fun PasoIngresos(
    r: Rendicion, hs: List<Hallazgo>, config: io.github.tmrainer.asistencia.nucleo.modelo.Config,
    propuestos: androidx.compose.runtime.MutableState<Set<String>>, cambiar: ((Rendicion) -> Rendicion) -> Unit
) {
    val c = colores
    TituloSeccion("Ingresos")
    // El subsidio de los datos de la olla: se ofrece si el campo está vacío; no se aplica solo.
    val propuesta = sugerenciasOlla(config)["montoSubsidio"]
    if (r.montoSubsidioCent == null && propuesta != null) {
        BloqueSuave(Modifier.padding(bottom = Medidas.e3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Subsidio ${propuesta.procedencia}", style = tipos.chicoNegrita, color = c.acentoTexto)
                    Text("S/ ${propuesta.valor}", style = tipos.textoNegrita, color = c.tinta)
                }
                Boton("Usar este monto", {
                    cambiar { it.copy(montoSubsidioCent = config.olla.montoSubsidioCent) }
                    propuestos.value = propuestos.value + "montoSubsidio"
                }, diminuto = true, tipo = TipoBoton.PRINCIPAL)
            }
        }
    }
    FilaCampos {
        CampoSoles("Monto del subsidio (S/)", r.montoSubsidioCent, { v -> propuestos.value = propuestos.value - "montoSubsidio"; cambiar { it.copy(montoSubsidioCent = v) } }, Modifier.objetivoGuia("b1.monto"),
            pista = if ("montoSubsidio" in propuestos.value) "Dato propuesto — revísalo" else "Lo que te dio la Municipalidad para este periodo.", error = hs.errorDe("montoSubsidio"), obligatorio = true)
        // Se recibe antes de la primera compra y de la rendición.
        val primeraCompra = r.egresos.map { it.fechaCompra }.filter(::esIso).minOrNull() ?: ""
        CampoFecha("Fecha de la asignación", r.fechaAsignacion, { v -> cambiar { it.copy(fechaAsignacion = v) } }, Modifier.objetivoGuia("b1.fechaAsignacion"), pista = "El día que recibiste ese dinero.", error = hs.errorDe("fechaAsignacion"), aviso = hs.avisoDe("fechaAsignacion"), obligatorio = true,
            hasta = listOf(r.fechaRendicion, primeraCompra).filter(::esIso).minOrNull() ?: "")
    }
}

@Composable
private fun PasoCompras(
    r: Rendicion, hs: List<Hallazgo>, abierto: String?, alAbrir: (String?) -> Unit,
    cambiar: ((Rendicion) -> Rendicion) -> Unit, cambiarEgreso: (String, (Egreso) -> Egreso) -> Unit, agregar: () -> Unit
) {
    val c = colores
    val avisos = LocalAvisos.current
    TituloSeccion("Egresos")
    Pista("Una ficha por compra, copiada tal cual de la boleta o del papel.", Modifier.padding(bottom = Medidas.e3))
    hs.errorDe("egresos")?.let { Text(it, style = tipos.chico, color = c.error, modifier = Modifier.padding(bottom = Medidas.e2)) }
    Superficie(Modifier.objetivoGuia("b1.compras")) {
        r.egresos.forEachIndexed { i, f ->
            val campo = "egreso.${f.id}"
            val conError = hs.any { it.campo == campo && it.severidad == Severidad.ERROR && !it.falta }
            val conAviso = hs.any { it.campo == campo && it.severidad == Severidad.ADVERTENCIA }
            val estaAbierta = abierto == f.id || (abierto == null && !egresoCompleto(f))
            Row(
                Modifier.fillMaxWidth().background(if (estaAbierta) c.superficie2 else c.superficie).clickable(role = Role.Button) { alAbrir(if (estaAbierta) "" else f.id) }
                    // TalkBack dice si la ficha está abierta (la flecha sola no se oye).
                    .semantics { stateDescription = if (estaAbierta) "Abierta" else "Cerrada" }
                    .defaultMinSize(minHeight = 56.dp).padding(Medidas.e4, Medidas.e3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)
            ) {
                Text("Compra ${i + 1}", style = tipos.textoNegrita, color = c.titulo)
                Text(resumenEgreso(f), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.weight(1f))
                if (conError) Pastilla("con error", TonoPastilla.ERROR) else if (conAviso) Pastilla("revisar", TonoPastilla.ADVERTENCIA)
                Icono("flecha", 18.dp, c.tintaSuave, Modifier.rotate(if (estaAbierta) 90f else 0f))
            }
            if (estaAbierta) {
                // La que se está llenando queda abierta: no se cierra sola al completarse,
                // con la boleta todavía por escribir.
                val editar: ((Egreso) -> Egreso) -> Unit = { cambio -> if (abierto != f.id) alAbrir(f.id); cambiarEgreso(f.id, cambio) }
                Column(Modifier.padding(Medidas.e4, Medidas.e2, Medidas.e4, Medidas.e4)) {
                    val mensajes = hs.filter { it.campo == campo && !(it.severidad == Severidad.ERROR && it.falta) }
                    for (m in mensajes) Text(m.mensaje, style = tipos.chico, color = if (m.severidad == Severidad.ERROR) c.error else c.aviso, modifier = Modifier.padding(bottom = Medidas.e2))
                    Boton("Quitar la compra ${i + 1}", {
                        val antes = r.egresos
                        cambiar { x -> x.copy(egresos = x.egresos.filter { it.id != f.id }.ifEmpty { listOf(nuevoEgreso()) }) }
                        avisos.mostrar("Quitaste la compra ${i + 1}") { cambiar { x -> x.copy(egresos = antes) } }
                    }, Modifier.padding(bottom = 10.dp), diminuto = true, tipo = TipoBoton.PELIGRO)
                    // Entre el día que llegó el subsidio y la rendición.
                    CampoFecha("Fecha de la compra", f.fechaCompra, { v -> editar { it.copy(fechaCompra = v) } }, pista = "La fecha que dice la boleta.", desde = r.fechaAsignacion, hasta = r.fechaRendicion)
                    Spacer(Modifier.height(10.dp))
                    Campo("Descripción de la compra", f.descripcion, { v -> editar { it.copy(descripcion = v) } }, pista = "Qué compraste. Ej.: Arroz superior.")
                    Spacer(Modifier.height(10.dp))
                    // Varios: la boleta junta varias cosas o no dice cuánto ni a cuánto (la carne
                    // de distintos cortes). No se pide desglosar: va «varios».
                    val varios = f.cantidadVarios || f.precioUnitarioVarios
                    Leyenda("¿Cómo viene la compra?")
                    Opciones(listOf(false to "Un producto, con cantidad y precio", true to "Varios juntos"), { it == varios }, { v ->
                        editar {
                            if (v) it.copy(cantidadVarios = true, precioUnitarioVarios = true, unidadMedida = "varios")
                            else it.copy(cantidadVarios = false, precioUnitarioVarios = false, unidadMedida = if (it.unidadMedida == "varios") "" else it.unidadMedida)
                        }
                    })
                    if (varios) {
                        Pista("Para cuando la boleta junta varias cosas o no dice cuánto ni a cuánto. En la hoja sale «varios» en cantidad, unidad y precio: escribe solo el monto total.", Modifier.padding(top = Medidas.e2))
                    } else {
                        Spacer(Modifier.height(10.dp))
                        // El estado va afuera de la fila: FilaCampos solo acomoda.
                        var cantidad by remember(f.id) { mutableStateOf(f.cantidad?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
                        FilaCampos {
                            Campo("Cantidad", cantidad, { v -> cantidad = v; editar { conTotalCalculado(it, it.copy(cantidad = aNumero(v))) } }, decimal = true)
                            SelectorDesplegable("Unidad de medida", listOf("" to "—") + UNIDADES.map { it to it }, f.unidadMedida.takeIf { it != "varios" }, { u -> editar { it.copy(unidadMedida = u) } })
                        }
                        Spacer(Modifier.height(10.dp))
                        CampoSoles("Precio unitario (S/)", f.precioUnitarioCent, { v -> editar { conTotalCalculado(it, it.copy(precioUnitarioCent = v)) } }, pista = "El precio de uno, como dice la boleta.")
                    }
                    Spacer(Modifier.height(10.dp))
                    // Calculado (cantidad × precio) se dice siempre: el que vale es el de la boleta.
                    val calculado = f.montoTotalCent != null && f.montoTotalCent == totalDeLaFila(f)
                    val pistaTotal = if (calculado) "Calculado: ${f.cantidad?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }} × S/ ${formatearSoles(f.precioUnitarioCent)}. Si tu boleta dice otro total, cámbialo."
                        else "Lo que pagaste por esta compra."
                    CampoSoles("Monto total (S/)", f.montoTotalCent, { v -> editar { it.copy(montoTotalCent = v) } }, pista = pistaTotal, obligatorio = true)
                    // Lo de la boleta: en el mercado muchas veces no dan; entonces queda vacío.
                    Spacer(Modifier.height(Medidas.e4))
                    Leyenda("La boleta, si te dieron")
                    Pista("Si compraste en el mercado sin boleta, déjalo vacío.", Modifier.padding(bottom = Medidas.e2))
                    Campo("RUC del vendedor", f.rucProveedor, { v -> editar { it.copy(rucProveedor = v) } }, pista = "11 cifras; está arriba en la boleta.", soloDigitos = true, maxLargo = 11)
                    Spacer(Modifier.height(10.dp))
                    FilaCampos {
                        Campo("Serie", f.boletaSerie, { v -> editar { it.copy(boletaSerie = v) } }, pista = "Ej.: B001")
                        Campo("Número", f.boletaCorrelativo, { v -> editar { it.copy(boletaCorrelativo = v) } }, pista = "Ej.: 000143")
                    }
                }
            }
            if (i < r.egresos.size - 1) Separador()
        }
    }
    // Agregar es el botón flotante «Compra»: una sola acción principal por pantalla. En una
    // pantalla baja, donde el flotante tapaba lo de abajo, es este botón al final de la lista.
    if (ventana().baja) Boton("Anotar otra compra", agregar, Modifier.padding(top = Medidas.e3).objetivoGuia("b1.compra"), icono = "mas", tipo = TipoBoton.PRINCIPAL)
    Text("${cuantos(r.egresos.size, "compra")}. La hoja del formato tiene 17 filas.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))

    // Los totales se suman solos; de solo lectura.
    val t = totales(r)
    Superficie(Modifier.padding(top = Medidas.e4).objetivoGuia("b1.totales")) {
        Column(Modifier.padding(Medidas.e4, Medidas.e3)) {
            // Los renglones de la hoja (Pachacámac 2026). El subsidio se gasta entero: lo que
            // se gasta de más es aporte de la olla, y lo rendido con el subsidio es el total.
            FilaTotal("Monto total de compra", t.total?.let { "S/ ${formatearSoles(it)}" })
            Separador()
            FilaTotal("Monto de declaración jurada (registrar, de corresponder)", null)
            Separador()
            // "Registrar de corresponder": si es cero, el renglón va vacío.
            FilaTotal("Aporte del centro de atención (lo que pusieron de más)", t.aporte?.takeIf { it != 0L }?.let { "S/ ${formatearSoles(it)}" })
            Separador()
            FilaTotal("Total de rendición de gastos", t.conSubsidio?.let { "S/ ${formatearSoles(it)}" }, destacado = true)
            Text("Se suman solos de tus compras. El aporte sale solo cuando las compras pasan el subsidio.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 10.dp))
            for (m in hs.filter { it.campo == "totales" }) Text(m.mensaje, style = tipos.chico, color = if (m.severidad == Severidad.ERROR) c.error else c.aviso, modifier = Modifier.padding(top = Medidas.e2))
        }
    }
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
private fun PasoRevisar(
    r: Rendicion, rendiciones: List<Rendicion>, indice: Int, hs: List<Hallazgo>,
    irACampo: (String) -> Unit, abrir: (String) -> Unit, empezarMes: () -> Unit,
    listoParaExportar: () -> Boolean, verHoja: () -> Unit
) {
    val c = colores
    TituloSeccion("Revisar antes de entregar")
    val errores = hs.filter { it.severidad == Severidad.ERROR }.sortedBy { pasoDe(it.campo) }
    if (errores.isEmpty()) {
        Row(Modifier.objetivoGuia("b1.estado"), verticalAlignment = Alignment.CenterVertically) {
            Icono("ok", 20.dp, c.ok)
            Spacer(Modifier.width(Medidas.e2))
            ParaLeer("El B-1 está completo. Revísalo con la tesorera y el fiscal antes de entregarlo.")
            Text("El B-1 está completo. Revísalo con la tesorera y el fiscal antes de entregarlo.", style = tipos.textoFuerte, color = c.ok)
        }
    } else {
        ParaLeer((if (errores.size == 1) "Antes de entregarlo, falta esto: " else "Antes de entregarlo, faltan ${errores.size} cosas: ") + errores.joinToString(". ") { it.mensaje.removeSuffix(".") })
        Text(if (errores.size == 1) "Antes de entregarlo, falta esto:" else "Antes de entregarlo, faltan ${errores.size} cosas:", style = tipos.texto, color = c.tinta, modifier = Modifier.padding(bottom = Medidas.e2))
        Superficie(Modifier.objetivoGuia("b1.estado")) {
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
            Chip("Se rinde", "S/ ${formatearSoles(t.conSubsidio)}")
        }
    }
    Spacer(Modifier.height(Medidas.e4))
    Boton("Ver cómo queda en la hoja", verHoja, Modifier.objetivoGuia("b1.hoja"), icono = "boleta")
    Spacer(Modifier.height(Medidas.e5))
    TituloSeccion("Exportar el B-1")
    // Antes de cerrar el mes sale con la semana o la quincena en que va («Noviembre-2026-S4»);
    // cerrado (o ya pasado el mes), con el mes y el año. La fecha de rendición es la del día en que se arma.
    val hoy = LocalDate.now()
    val cerrado = indice > 0 || (aFecha(r.periodo.fin)?.let { hoy.isAfter(it) } ?: false)
    val codigo = codigoB1(r.periodo, hoy, cerrado) ?: r.periodo.etiqueta.trim().ifEmpty { "en curso" }
    val anticipado = codigo.takeIf { !cerrado && it != codigoB1(r.periodo, hoy, cerrado = true) }
    FranjaEstado(
        if (anticipado != null) "Si lo mandas hoy, sale como «$anticipado»: lo anotado hasta hoy, antes del cierre del mes."
        else "Sale como «$codigo».",
        TonoPastilla.NEUTRA, Modifier.padding(bottom = Medidas.e3), icono = "boleta"
    )
    val conFecha = if (cerrado) r.copy(fechaRendicion = r.fechaRendicion.ifEmpty { hoyIso() }) else r.copy(fechaRendicion = hoyIso())
    BotonesExportar(
        pdf = { Documento(pdfDelB1(hojasDelB1(conFecha), selloAhora(), "Formato B-1 $codigo", anticipado), "B-1 $codigo", "Formato B-1 $codigo (copia de referencia)", AlElegir.B1(r.id, "pdf")) },
        excel = { Documento(excelDelB1(conFecha, anticipado), "B-1 $codigo", "Formato B-1 $codigo", AlElegir.B1(r.id, "excel")) },
        modifier = Modifier.objetivoGuia("b1.exportar"),
        puede = listoParaExportar,
        // En el último paso, mandar el B-1 es lo que se hace: la única acción rellena.
        pdfPrincipal = true
    )

    Spacer(Modifier.height(Medidas.e5))
    TituloSeccion("Meses anteriores", apoyo = true, modifier = Modifier.objetivoGuia("b1.meses"))
    if (indice == 0) {
        Pista("Al terminar el mes, ciérralo: se arma el B-1 con el nombre del mes y empieza el siguiente. Este queda guardado abajo.", Modifier.padding(bottom = Medidas.e3))
        Boton("Cerrar ${nombreDelMes(r)} y empezar ${mesSiguiente(r).etiqueta}", { if (listoParaExportar()) empezarMes() }, Modifier.objetivoGuia("b1.mesSiguiente"))
    }
    val anteriores = rendiciones.drop(1)
    if (anteriores.isNotEmpty()) {
        Superficie(Modifier.padding(top = Medidas.e3)) {
            rendiciones.forEachIndexed { i, x ->
                if (i == 0) return@forEachIndexed
                val tx = totales(x)
                Row(Modifier.fillMaxWidth().background(if (i == indice) c.superficie2 else c.superficie).padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                    Column(Modifier.weight(1f)) {
                        Text(nombreDelMes(x), style = tipos.textoFuerte, color = c.titulo)
                        codigoB1(x.periodo, LocalDate.now(), cerrado = true)?.takeIf { it.replace("-", " ") != nombreDelMes(x) }?.let { Text(it, style = tipos.chico, color = c.tintaSuave) }
                    }
                    Text(if (tx.total == null) "sin subsidio" else "S/ ${formatearSoles(tx.total)}", style = tipos.chico, color = c.tintaSuave)
                    if (i == indice) Text("abierto", style = tipos.chicoNegrita, color = c.acentoTexto) else Boton(if (x.periodo.etiqueta.isNotBlank()) "Abrir ${x.periodo.etiqueta.trim()}" else "Abrir este B-1", { abrir(x.id) }, diminuto = true)
                }
                if (i < rendiciones.size - 1) Separador()
            }
        }
    }
}
