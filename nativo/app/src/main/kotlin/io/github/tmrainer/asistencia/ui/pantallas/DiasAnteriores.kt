// Los días que pasaron, aparte de pasar lista (decisión del 25-11-2026): con todos los
// días en la pantalla de Asistencia, deslizar rápido daba tirones y con un año de datos
// se congelaba. Aquí van la lista de días (una línea cada uno, con sus cuentas hechas una
// sola vez y fuera de la pantalla), el reporte del día y el resumen. Cada día se abre en
// su propia pantalla, con la lista de personas que se arma a medida que se desliza.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.calculos.Origen
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.grupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.menuPorDefecto
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEconomicoRaciones
import io.github.tmrainer.asistencia.nucleo.documentos.excelDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.fechaLarga
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.GUIA_DIA
import io.github.tmrainer.asistencia.ui.GUIA_DIAS
import io.github.tmrainer.asistencia.ui.ListaParaGuia
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.BotonesExportar
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.CampoSoles
import io.github.tmrainer.asistencia.ui.componentes.Chip
import io.github.tmrainer.asistencia.ui.componentes.Chips
import io.github.tmrainer.asistencia.ui.componentes.Documento
import io.github.tmrainer.asistencia.ui.componentes.Enlace
import io.github.tmrainer.asistencia.ui.componentes.EstadoGuardado
import io.github.tmrainer.asistencia.ui.componentes.EstadoVacio
import io.github.tmrainer.asistencia.ui.componentes.FilaCampos
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lo que muestra la línea de un día: sus cuentas, hechas una vez. */
private data class LineaDia(val id: String, val fecha: String, val raciones: Int, val ayudaSocial: Int, val recaudadoCent: Long?)

/** Los días anteriores (todos menos hoy), el reporte del día y el resumen de unas fechas. */
@Composable
fun PantallaDias(ctx: Contexto) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val guardado by estado.guardado.collectAsStateWithLifecycle()
    val c = colores
    val hoy = hoyIso()
    // Las cuentas de cada día, fuera de la pantalla: con cientos de días no traban el deslizar.
    val lineas by produceState<List<LineaDia>?>(null, padron.atenciones, hoy) {
        value = withContext(Dispatchers.Default) {
            padron.atenciones.filter { it.fecha != hoy }.sortedByDescending { it.fecha }.map { at ->
                val d = desgloseDelDia(at)
                LineaDia(at.id, at.fecha, d.total, d.ayudaSocial, recaudacionDelDiaCent(at))
            }
        }
    }
    val periodoDefecto = remember { estado.periodoPorDefecto() }
    var inicio by remember { mutableStateOf(periodoDefecto.inicio) }
    var fin by remember { mutableStateOf(periodoDefecto.fin) }

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Días anteriores", "Corregir días, reportes y resumen", guia = GUIA_DIAS)
        val lista = rememberLazyListState()
        ListaParaGuia(lista)
        LazyColumn(
            Modifier.fillMaxWidth().widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally),
            state = lista, contentPadding = PaddingValues(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            item {
                Pista("Para corregir o completar un día que pasó, ábrelo. Si falta un día, regístralo.", Modifier.padding(bottom = Medidas.e3))
                Boton("+ Registrar otro día", modifier = Modifier.objetivoGuia("dias.nuevo"), onClick = {
                    // Sin fecha: la pone la persona en la pantalla del día.
                    val at = nuevaAtencion(estado.preciosActuales()).copy(fecha = "")
                    estado.cambiarPadron { it.copy(atenciones = it.atenciones + at) }
                    ctx.ir(Ruta.DIA, at.id)
                })
                Spacer(Modifier.height(Medidas.e4))
            }
            val ls = lineas
            when {
                ls == null -> item { Spacer(Modifier.height(Medidas.e6)) }
                ls.isEmpty() -> item {
                    Text("Todavía no hay días anteriores.", style = tipos.pista, color = c.tintaSuave, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e4, Medidas.e6))
                }
                else -> items(ls, key = { it.id }) { l ->
                    val primera = l.id == ls.first().id
                    Box(if (primera) Modifier.objetivoGuia("dias.lista") else Modifier) {
                        FilaDia(l, primera, l.id == ls.last().id) { ctx.ir(Ruta.DIA, l.id) }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Medidas.e5))
                TituloSeccion("Reporte del día", apoyo = true)
                ReporteDelDia(ctx, Modifier.objetivoGuia("dias.reporte"))
            }

            item {
                Spacer(Modifier.height(Medidas.e5))
                TituloSeccion("Resumen", apoyo = true)
                FilaCampos {
                    CampoFecha("Desde", inicio, { if (it.isNotEmpty()) inicio = it })
                    CampoFecha("Hasta", fin, { if (it.isNotEmpty()) fin = it })
                }
                val r by produceState<io.github.tmrainer.asistencia.nucleo.calculos.ResumenEconomico?>(null, padron, inicio, fin) {
                    value = withContext(Dispatchers.Default) { resumenEconomicoRaciones(padron, inicio, fin) }
                }
                r?.let { r ->
                    Chips(Modifier.padding(top = Medidas.e3).objetivoGuia("dias.cifras")) {
                        Chip("Raciones", "${r.racionesTotales}")
                        Chip("Normales", "${r.racionesNormales}")
                        Chip("Ayuda social", "${r.racionesAyudaSocial}")
                        if (r.recaudacionCent != null) {
                            Chip("Recaudado", "S/ ${formatearSoles(r.recaudacionCent)}")
                            Chip("Promedio por ración", "S/ ${formatearSoles(r.precioPromedioRacionCent)}")
                        }
                    }
                    // Solo se explica lo que no se ve en las cifras: cuando falta algo.
                    val partes = ArrayList<String>()
                    if (r.racionesTotales == 0) partes.add("Nadie marcado en estas fechas.")
                    else if (r.recaudacionCent == null) partes.add("Falta el precio del menú para calcular lo recaudado.")
                    else if (r.racionesConPrecio < r.racionesTotales) {
                        val sin = r.racionesTotales - r.racionesConPrecio
                        partes.add("${if (sin == 1) "1 ración no tiene" else "$sin raciones no tienen"} precio y no cuenta${if (sin == 1) "" else "n"} en el promedio.")
                    }
                    if (r.diasDeLegado > 0) partes.add("${if (r.diasDeLegado == 1) "1 día trae" else "${r.diasDeLegado} días traen"} raciones anotadas a mano en la versión anterior.")
                    if (partes.isNotEmpty()) Text(partes.joinToString(" "), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 10.dp))
                }
                val centro = estado.config.value.ultimoCentro?.nombreCentro?.trim().orEmpty()
                BotonesExportar(
                    pdf = { Documento(pdfDeAsistencia(estado.padron.value, inicio, fin, centro, selloAhora()), "Asistencia $inicio a $fin", "Asistencia (copia de referencia)") },
                    excel = { Documento(excelDeAsistencia(estado.padron.value, inicio, fin, centro), "Asistencia $inicio a $fin", "Asistencia") },
                    modifier = Modifier.padding(top = Medidas.e3)
                )
                EstadoGuardado(guardado, "Se guarda solo, mientras tocas.")
            }
        }
    }
}

/** Un día en una línea: la fecha y sus cuentas. Tocarlo abre su pantalla. */
@Composable
private fun FilaDia(l: LineaDia, primera: Boolean, ultima: Boolean, alAbrir: () -> Unit) {
    val c = colores
    val forma = RoundedCornerShape(
        topStart = if (primera) Medidas.rM else 0.dp, topEnd = if (primera) Medidas.rM else 0.dp,
        bottomStart = if (ultima) Medidas.rM else 0.dp, bottomEnd = if (ultima) Medidas.rM else 0.dp
    )
    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = alAbrir).defaultMinSize(minHeight = 56.dp).padding(Medidas.e4, Medidas.e3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)
        ) {
            Text(if (l.fecha.isNotEmpty()) aDdMmAa(l.fecha) else "Sin fecha", style = tipos.textoNegrita, color = c.titulo)
            val resumen = buildList {
                add("${l.raciones} raciones")
                if (l.ayudaSocial > 0) add("${l.ayudaSocial} de ayuda social")
                if (l.recaudadoCent != null) add("S/ ${formatearSoles(l.recaudadoCent)}")
            }
            Text(resumen.joinToString(" · "), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.weight(1f))
            Icono("flecha", 18.dp, c.tintaSuave)
        }
        if (!ultima) Separador()
    }
}

/** Un día que pasó: su fecha, lo que se cocinó, quién vino y su precio. */
@Composable
fun PantallaDia(ctx: Contexto, id: String?) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val c = colores
    val contexto = LocalContext.current
    val at = padron.atenciones.firstOrNull { it.id == id }
    if (at == null) {
        Column(Modifier.fillMaxWidth()) {
            Cabecera(ctx, "Día", guia = GUIA_DIA)
            Box(Modifier.padding(Medidas.e4)) {
                EstadoVacio("reloj", "Ese día ya no está", "Lo quitaste o se borró.", "Ver los días anteriores" to { ctx.volver() })
            }
        }
        return
    }
    val fecha = at.fecha.ifEmpty { hoyIso() }
    val activos = remember(padron.afiliados, fecha) {
        afiliadosActivos(padron, fecha).sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
    }
    val claves = remember(activos) { activos.associate { it.id to claveBusqueda(it) } }
    val grupos = remember(activos, fecha) { activos.associate { it.id to grupoEtario(it, fecha) } }
    var filtro by remember { mutableStateOf("") }
    val aguja = normalizarNombre(filtro)
    val visibles = remember(activos, aguja) { activos.filter { aguja.isEmpty() || claves.getValue(it.id).contains(aguja) } }
    val marcados = remember(at.asistencias) { at.asistencias.map { it.afiliadoId }.toSet() }
    val menus = remember(at.asistencias) { at.asistencias.associate { it.afiliadoId to it.tipoMenu } }
    val d = desgloseDelDia(at)
    val rec = recaudacionDelDiaCent(at)
    fun cambiar(cambio: (io.github.tmrainer.asistencia.nucleo.modelo.Atencion) -> io.github.tmrainer.asistencia.nucleo.modelo.Atencion) = cambiarDia(estado, at, cambio)

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, if (at.fecha.isNotEmpty()) fechaLarga(at.fecha) else "Día sin fecha", "Día anterior", guia = GUIA_DIA)
        val lista = rememberLazyListState()
        ListaParaGuia(lista)
        LazyColumn(
            Modifier.fillMaxWidth().widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally),
            state = lista, contentPadding = PaddingValues(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            item {
                Column(Modifier.objetivoGuia("dia.datos")) {
                    CampoFecha("Fecha", at.fecha, { f -> cambiar { it.copy(fecha = f) } })
                    Spacer(Modifier.height(Medidas.e3))
                    RegistroDelDia(at) { cambio -> cambiar(cambio) }
                    Spacer(Modifier.height(Medidas.e3))
                    Campo("Nota", at.nota, { n -> cambiar { it.copy(nota = n) } })
                }
                Chips(Modifier.padding(top = Medidas.e3)) {
                    Chip("Raciones", "${d.total}"); Chip("Normal", "${d.normal}"); Chip("Ayuda social", "${d.ayudaSocial}")
                    Chip("Recaudado", if (rec == null) "—" else "S/ ${formatearSoles(rec)}")
                }
                if (d.origen == Origen.LEGADO) {
                    Text("Estas ${d.total} raciones vienen de la versión anterior. Si marcas la lista, se cuentan de la lista.",
                        style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
                }
                Spacer(Modifier.height(Medidas.e5))
                TituloSeccion("Quién vino")
                Acciones(Modifier.objetivoGuia("dia.todos")) {
                    Boton("Marcar a todos", { marcarATodos(ctx, at, activos, true) }, diminuto = true)
                    Boton("Quitar a todos", { marcarATodos(ctx, at, activos, false) }, diminuto = true)
                }
                if (activos.size > 12) Campo("Buscar persona", filtro, { filtro = it }, Modifier.padding(top = 10.dp), placeholder = "Buscar…")
                Spacer(Modifier.height(Medidas.e3))
                if (visibles.isEmpty()) {
                    Pista(if (aguja.isNotEmpty()) "Nadie coincide con esa búsqueda." else "No hay personas activas en el padrón para esa fecha.")
                }
            }
            items(visibles, key = { it.id }) { p ->
                Box(if (p.id == visibles.first().id) Modifier.objetivoGuia("dia.lista") else Modifier) {
                    FilaToque(p, p.id in marcados, menus[p.id] ?: menuPorDefecto(p), grupos[p.id],
                        p.id == visibles.first().id, p.id == visibles.last().id) { alternarAsistencia(ctx, contexto, at, p) }
                }
            }
            item {
                ContadorSinInscribir(at) { nuevo -> cambiar { it.copy(sinInscribir = nuevo) } }
                var precioAbierto by remember { mutableStateOf(false) }
                Enlace(if (precioAbierto) "Ocultar el precio del menú de este día" else "Precio del menú de este día", { precioAbierto = !precioAbierto }, Modifier.padding(top = 10.dp))
                if (precioAbierto) {
                    Pista(if (at.precioTomadoDeConfig) "Copiado del precio de Asistencia. Cámbialo solo si ese día fue distinto." else "Precio propio de este día.", Modifier.padding(bottom = Medidas.e2))
                    CampoSoles("Menú normal (S/)", at.precioMenuNormalCent, { v -> cambiar { it.copy(precioMenuNormalCent = v, precioTomadoDeConfig = false) } })
                    Spacer(Modifier.height(Medidas.e3))
                    CampoSoles("Menú de ayuda social (S/)", at.precioMenuAyudaSocialCent, { v -> cambiar { it.copy(precioMenuAyudaSocialCent = v, precioTomadoDeConfig = false) } })
                }
                Spacer(Modifier.height(Medidas.e5))
                Boton("Quitar este día", {
                    val pos = estado.padron.value.atenciones.indexOfFirst { it.id == at.id }
                    estado.cambiarPadron { p -> p.copy(atenciones = p.atenciones.filter { it.id != at.id }) }
                    ctx.volver()
                    ctx.avisos.mostrar("Quitaste el día ${if (at.fecha.isNotEmpty()) aDdMmAa(at.fecha) else ""}".trim()) {
                        estado.cambiarPadron { p -> p.copy(atenciones = p.atenciones.toMutableList().also { it.add(maxOf(0, minOf(pos, it.size)), at) }) }
                    }
                }, Modifier.objetivoGuia("dia.quitar"), tipo = TipoBoton.PELIGRO)
            }
        }
    }
}
