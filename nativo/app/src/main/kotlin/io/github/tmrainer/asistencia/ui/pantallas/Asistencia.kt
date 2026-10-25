// Asistencia.kt — pasar lista (pagina-asistencia.js + ui-asistencia.js).
//
// Marcar a alguien es UN toque en su fila. El menú sale de cómo figura en el padrón
// (habitual → normal, ayuda social → menú de ayuda social) y solo hace falta
// tocarlo cuando ese día come otro. Sin desplegables.
//
// «Asistencia de hoy» siempre apunta a hoy. El registro del día NO se crea al
// abrir la pantalla: se crea cuando se marca a la primera persona.
package io.github.tmrainer.asistencia.ui.pantallas

import io.github.tmrainer.asistencia.nucleo.envio.correoDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.nucleo.documentos.excelDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDeAsistencia
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.tmrainer.asistencia.ui.ListaParaGuia
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.componentes.horaAhora
import io.github.tmrainer.asistencia.ui.componentes.Documento
import io.github.tmrainer.asistencia.ui.componentes.BloqueEnvio
import io.github.tmrainer.asistencia.ui.componentes.BotonesExportar
import io.github.tmrainer.asistencia.ui.camara.CamaraDni
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.calculos.Origen
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasConTodos
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.etiquetaGrupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.grupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.menuPorDefecto
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEconomicoRaciones
import io.github.tmrainer.asistencia.nucleo.calculos.sinInscribirDelDia
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.Atencion
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.SinInscribir
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.fechaLarga
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.nucleo.validaciones.Hallazgo
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAtenciones
import io.github.tmrainer.asistencia.nucleo.validaciones.validarPreciosMenu
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.GUIA_ASISTENCIA
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Avatar
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.CampoSoles
import io.github.tmrainer.asistencia.ui.componentes.Chip
import io.github.tmrainer.asistencia.ui.componentes.Chips
import io.github.tmrainer.asistencia.ui.componentes.Enlace
import io.github.tmrainer.asistencia.ui.componentes.EstadoGuardado
import io.github.tmrainer.asistencia.ui.componentes.EstadoVacio
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
import io.github.tmrainer.asistencia.ui.componentes.PanelHallazgos
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.componentes.vibrar
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lo que se busca de una persona: nombre sin tildes ni mayúsculas, y su DNI. */
private fun claveBusqueda(p: Afiliado) = "${normalizarNombre(nombreCompleto(p))} ${p.numeroDocumento}"

// Las curvas de .toque-marca en estilos.css: ease-out y la enfatizada de Material.
private val SALIDA = CubicBezierEasing(0f, 0f, .58f, 1f)
private val ENFATIZADA = CubicBezierEasing(.2f, 0f, 0f, 1f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaAsistencia(ctx: Contexto) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val config by estado.config.collectAsStateWithLifecycle()
    val guardado by estado.guardado.collectAsStateWithLifecycle()
    val c = colores
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val hoy = hoyIso()

    // El día de hoy: el guardado, o uno nuevo que todavía no se guarda.
    val hoyNueva = remember(hoy) { nuevaAtencion(estado.preciosActuales()).copy(fecha = hoy) }
    val atHoy = padron.atenciones.firstOrNull { it.fecha == hoy } ?: hoyNueva.copy(
        precioMenuNormalCent = config.precioMenuNormalCent, precioMenuAyudaSocialCent = config.precioMenuAyudaSocialCent
    )

    /** Cambia un día; si todavía no estaba guardado (el de hoy), lo agrega. */
    fun cambiarDia(base: Atencion, cambio: (Atencion) -> Atencion) {
        estado.cambiarPadron { p ->
            val i = p.atenciones.indexOfFirst { it.id == base.id }
            if (i >= 0) p.copy(atenciones = p.atenciones.toMutableList().also { it[i] = cambio(it[i]) })
            else p.copy(atenciones = p.atenciones + cambio(base))
        }
    }

    // Revisar todos los días es lo más caro de un toque: 600 ms después del último.
    var hallazgos by remember { mutableStateOf(emptyList<Hallazgo>()) }
    LaunchedEffect(padron, config) {
        delay(600)
        hallazgos = withContext(Dispatchers.Default) {
            validarAtenciones(padron) + validarPreciosMenu(config, padron.atenciones.isNotEmpty())
        }
    }

    val activos = remember(padron.afiliados, hoy) {
        afiliadosActivos(padron, hoy).sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
    }
    val claves = remember(activos) { activos.associate { it.id to claveBusqueda(it) } }
    var busqueda by remember { mutableStateOf("") }
    var soloSinMarcar by remember { mutableStateOf(false) }
    val marcados = remember(atHoy.asistencias) { atHoy.asistencias.map { it.afiliadoId }.toSet() }
    val menus = remember(atHoy.asistencias) { atHoy.asistencias.associate { it.afiliadoId to it.tipoMenu } }
    val aguja = normalizarNombre(busqueda)
    val visibles = remember(activos, aguja, soloSinMarcar, marcados) {
        activos.filter { p -> (aguja.isEmpty() || claves.getValue(p.id).contains(aguja)) && !(soloSinMarcar && p.id in marcados) }
    }
    val sinMarcar = activos.count { it.id !in marcados }

    fun alternar(at: Atencion, persona: Afiliado) {
        val antes = at
        val vino = persona.id !in at.asistencias.map { it.afiliadoId }
        cambiarDia(at) { a ->
            if (vino) a.copy(legado = null, asistencias = a.asistencias + Asistencia(persona.id, menuPorDefecto(persona)))
            else a.copy(asistencias = a.asistencias.filter { it.afiliadoId != persona.id })
        }
        vibrar(contexto)
        avisos.mostrar("${if (vino) "Marcaste a" else "Quitaste a"} ${persona.nombres.ifEmpty { nombreCompleto(persona) }}") {
            cambiarDia(antes) { a -> a.copy(asistencias = antes.asistencias, legado = antes.legado) }
        }
    }

    fun marcarTodos(at: Atencion, personas: List<Afiliado>, marcar: Boolean) {
        val antes = at
        cambiarDia(at) { a -> if (marcar) a.copy(legado = null, asistencias = asistenciasConTodos(a, personas)) else a.copy(asistencias = emptyList()) }
        avisos.mostrar(if (marcar) "Marcaste a todos" else "Quitaste a todos") {
            cambiarDia(antes) { a -> a.copy(asistencias = antes.asistencias, legado = antes.legado) }
        }
    }

    // Pasar lista mostrando el DNI: la zona de texto del reverso (el número lleva
    // dígito de control). Marca a quien está en el padrón; no agrega ni guarda
    // nada más del DNI.

    /** Marca a la persona de ese DNI y devuelve qué pasó, para el visor. Lee el padrón de ahora, no el de cuando se abrió la cámara. */
    fun marcarPorDni(dni: String): String {
        val p = estado.padron.value
        val persona = afiliadosActivos(p, hoy).firstOrNull { it.numeroDocumento.trim() == dni }
        if (persona == null) {
            val deBaja = p.afiliados.any { it.numeroDocumento.trim() == dni }
            return if (deBaja) "Ese DNI está de baja en el padrón." else "Ese DNI no está en el padrón. Inscríbelo en Padrón."
        }
        val nombre = nombreCompleto(persona)
        val cfg = estado.config.value
        val at = p.atenciones.firstOrNull { it.fecha == hoy }
            ?: hoyNueva.copy(precioMenuNormalCent = cfg.precioMenuNormalCent, precioMenuAyudaSocialCent = cfg.precioMenuAyudaSocialCent)
        if (at.asistencias.any { it.afiliadoId == persona.id }) return "Ya estaba en la lista de hoy: $nombre."
        alternar(at, persona)
        return "✓ Marcaste a $nombre"
    }

    var diaAbierto by remember { mutableStateOf<String?>(null) }
    val periodoDefecto = remember { estado.periodoPorDefecto() }
    var inicio by remember { mutableStateOf(periodoDefecto.inicio) }
    var fin by remember { mutableStateOf(periodoDefecto.fin) }

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Asistencia", fechaLarga(hoy), guia = GUIA_ASISTENCIA)
        val lista = rememberLazyListState()
        ListaParaGuia(lista)
        LazyColumn(
            Modifier.fillMaxWidth().widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally),
            state = lista,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            val avisosAlmacen = estado.avisosAlmacenamiento()
            if (avisosAlmacen.isNotEmpty()) item { AvisoSistema(avisosAlmacen.joinToString(" "), Modifier.padding(bottom = Medidas.e4)) }

            item { Pista("Toca a quien vino. Las raciones se cuentan solas.", Modifier.padding(bottom = Medidas.e3)) }
            if (activos.isNotEmpty()) item {
                Boton("Pasar lista leyendo el DNI", { ctx.abrirCapa { CamaraDni(alCerrar = ctx::cerrarCapa, alLeerDni = ::marcarPorDni) } }, Modifier.padding(bottom = Medidas.e3).objetivoGuia("asistencia.dni"), icono = "camara")
            }

            if (activos.isEmpty()) {
                item {
                    EstadoVacio("personas", "Todavía no hay personas", "Primero inscribe a las personas o carga la hoja de la Municipalidad.",
                        "Inscribir a la primera persona" to { ctx.ir(Ruta.INSCRIBIR) })
                }
            } else {
                // Lo que se usa mientras se pasa lista: el conteo, el buscador y «los que faltan».
                stickyHeader {
                    Column(Modifier.fillMaxWidth().background(c.fondo).padding(bottom = Medidas.e2)) {
                        BarraHoy(atHoy, activos.size)
                        Row(Modifier.padding(top = Medidas.e2).objetivoGuia("asistencia.buscar"), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                            if (activos.size > 12) {
                                Campo("Buscar", busqueda, { busqueda = it }, Modifier.weight(1f), placeholder = "Buscar…")
                            }
                            val textoFaltan = when {
                                sinMarcar == 0 -> if (soloSinMarcar) "Ya vinieron todos: ver la lista" else "Ya vinieron todos"
                                soloSinMarcar -> if (sinMarcar == 1) "Viendo a quien falta" else "Viendo los $sinMarcar que faltan"
                                else -> if (sinMarcar == 1) "Ver a quien falta" else "Ver los $sinMarcar que faltan"
                            }
                            Boton(textoFaltan, { soloSinMarcar = !soloSinMarcar }, diminuto = true, activo = soloSinMarcar, habilitado = !(sinMarcar == 0 && !soloSinMarcar))
                        }
                    }
                }
                if (visibles.isEmpty()) {
                    item {
                        if (soloSinMarcar) EstadoVacio("ok", "Lista completa", "Ya marcaste a todos.", logrado = true)
                        else EstadoVacio("buscar", "Nadie coincide con esa búsqueda", "Prueba con el apellido o el DNI.")
                    }
                }
                items(visibles, key = { "hoy-" + it.id }) { persona ->
                    // La primera fila es la que muestra la guía («Toca a quien vino»).
                    Box(if (persona.id == visibles.first().id) Modifier.objetivoGuia("asistencia.lista") else Modifier) {
                        FilaToque(
                            persona = persona,
                            presente = persona.id in marcados,
                            menu = menus[persona.id] ?: menuPorDefecto(persona),
                            grupo = grupoEtario(persona, hoy),
                            primera = persona.id == visibles.first().id,
                            ultima = persona.id == visibles.last().id,
                            alTocar = { alternar(atHoy, persona) }
                        )
                    }
                }
                item {
                    Box(Modifier.objetivoGuia("asistencia.sinPadron")) { ContadorSinInscribir(atHoy) { nuevo -> cambiarDia(atHoy) { it.copy(sinInscribir = nuevo) } } }
                    Acciones(Modifier.padding(top = Medidas.e3)) {
                        Boton("Marcar a todos", { marcarTodos(atHoy, activos, true) }, diminuto = true)
                        Boton("Quitar a todos", { marcarTodos(atHoy, activos, false) }, diminuto = true)
                    }
                }
                // Lo que se cocinó y cuántas raciones se prepararon, después de
                // la lista: pasar lista es lo primero y la primera persona se ve al abrir.
                item {
                    Spacer(Modifier.height(Medidas.e5))
                    TituloSeccion("Hoy se cocinó")
                    RegistroDelDia(atHoy) { cambio -> cambiarDia(atHoy, cambio) }
                }
                // A UNACEM va la asistencia de HOY (el periodo del resumen es para exportar).
                item {
                    Spacer(Modifier.height(Medidas.e5))
                    TituloSeccion("Enviar a UNACEM")
                    BloqueEnvio(
                        ctx, "asistencia", hoy,
                        "Envía la asistencia de hoy en Excel, desde tu correo.",
                        siNoHay = "Hoy no marcaste a nadie: no hay nada que enviar.",
                        armar = {
                            val p = estado.padron.value
                            val delDia = p.atenciones.firstOrNull { it.fecha == hoy }
                            if (delDia == null || desgloseDelDia(delDia).total == 0) null
                            else correoDeAsistencia(p, hoy, hoy, estado.config.value.ultimoCentro?.nombreCentro.orEmpty(), horaAhora(), ctx.preferencias.perfil.value.nombre)
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(Medidas.e4)); PanelHallazgos(hallazgos) }

            // ---- otros días ----
            item {
                TituloSeccion("Otros días", apoyo = true)
                Pista("Para corregir o completar días pasados.", Modifier.padding(bottom = Medidas.e3))
                Boton("+ Registrar otro día", modifier = Modifier.objetivoGuia("asistencia.otros"), onClick = {
                    // Sin fecha: la pone la persona; la ficha se abre.
                    val at = nuevaAtencion(estado.preciosActuales()).copy(fecha = "")
                    estado.cambiarPadron { it.copy(atenciones = it.atenciones + at) }
                    diaAbierto = at.id
                })
                Spacer(Modifier.height(Medidas.e3))
            }
            val otros = padron.atenciones.filter { it.fecha != hoy }.sortedByDescending { it.fecha }
            if (otros.isEmpty()) {
                item { Text("Todavía no hay días anteriores.", style = tipos.pista, color = c.tintaSuave, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e4, Medidas.e6), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            items(otros, key = { "dia-" + it.id }) { at ->
                FichaDia(
                    at = at, padron = padron, abierta = diaAbierto == at.id,
                    primera = at.id == otros.first().id, ultima = at.id == otros.last().id,
                    alAbrir = { diaAbierto = if (diaAbierto == at.id) null else at.id },
                    cambiar = { cambio -> cambiarDia(at, cambio) },
                    alternar = { p -> alternar(at, p) },
                    marcarTodos = { personas, marcar -> marcarTodos(at, personas, marcar) },
                    quitarDia = {
                        val pos = padron.atenciones.indexOfFirst { it.id == at.id }
                        estado.cambiarPadron { p -> p.copy(atenciones = p.atenciones.filter { it.id != at.id }) }
                        diaAbierto = null
                        avisos.mostrar("Quitaste el día ${if (at.fecha.isNotEmpty()) aDdMmAa(at.fecha) else ""}".trim()) {
                            estado.cambiarPadron { p -> p.copy(atenciones = p.atenciones.toMutableList().also { it.add(maxOf(0, minOf(pos, it.size)), at) }) }
                        }
                    }
                )
            }

            // ---- precio del menú ----
            item {
                Spacer(Modifier.height(Medidas.e5))
                Column(Modifier.objetivoGuia("asistencia.precio")) {
                TituloSeccion("Precio del menú", apoyo = true)
                Pista("Vale para hoy y los días nuevos. Cada día anterior guarda su precio.", Modifier.padding(bottom = Medidas.e3))
                CampoSoles("Menú normal (S/)", config.precioMenuNormalCent, { v -> estado.cambiarConfig { it.copy(precioMenuNormalCent = v) } })
                Spacer(Modifier.height(Medidas.e3))
                CampoSoles("Menú de ayuda social (S/)", config.precioMenuAyudaSocialCent, { v -> estado.cambiarConfig { it.copy(precioMenuAyudaSocialCent = v) } }, pista = "Si es gratuito, escribe 0.00.")
                }
            }

            // ---- resumen ----
            item {
                Spacer(Modifier.height(Medidas.e5))
                TituloSeccion("Resumen", apoyo = true)
                Boton("Reporte del día y del mes", { ctx.ir(Ruta.REPORTES) }, Modifier.padding(bottom = Medidas.e3).objetivoGuia("asistencia.reporte"), icono = "archivo")
                Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                    CampoFecha("Desde", inicio, { if (it.isNotEmpty()) inicio = it }, Modifier.weight(1f))
                    CampoFecha("Hasta", fin, { if (it.isNotEmpty()) fin = it }, Modifier.weight(1f))
                }
                val r = remember(padron, inicio, fin) { resumenEconomicoRaciones(padron, inicio, fin) }
                Chips(Modifier.padding(top = Medidas.e3).objetivoGuia("asistencia.cifras")) {
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

/** El conteo de hoy: «12 de 40», la barra y el detalle (menú normal, ayuda social, recaudado). */
@Composable
private fun BarraHoy(at: Atencion, activos: Int) {
    val c = colores
    val d = desgloseDelDia(at)
    val inscritos = d.total - d.sinInscribir
    val resumen = "$inscritos de $activos personas marcadas" + if (d.sinInscribir > 0) ", y ${cuantos(d.sinInscribir, "ración", "raciones")} sin inscribir." else "."
    ParaLeer(resumen)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie).padding(Medidas.e3, 10.dp)
            .semantics { contentDescription = resumen }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$inscritos de $activos", style = tipos.titulo.copy(fontSize = tipos.titulo.fontSize * 1.1f), color = c.titulo)
            Spacer(Modifier.width(Medidas.e3))
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(999.dp)).background(c.seleccion)) {
                val fraccion = if (activos > 0) minOf(1f, inscritos.toFloat() / activos) else 0f
                Box(Modifier.fillMaxWidth(fraccion).height(8.dp).background(c.acento))
            }
        }
        val detalle = if (d.total == 0) listOf(if (activos == 0) "No hay nadie en el padrón todavía." else "Nadie marcado todavía.")
        else buildList {
            if (d.sinInscribir > 0) add("+${d.sinInscribir} sin inscribir")
            add("${d.normal} con menú normal")
            if (d.ayudaSocial > 0) add("${d.ayudaSocial} de ayuda social")
            val rec = recaudacionDelDiaCent(at)
            add(if (rec == null) "falta el precio del menú" else "S/ ${formatearSoles(rec)}")
        }
        Text(detalle.joinToString(" · "), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e1))
    }
}

/**
 * Una fila, un toque: avatar, nombre, grupo y menú, el ✓ a la derecha. El menú
 * solo se muestra: sale del registro de la persona (su tipo en el padrón), no
 * se cambia aquí.
 */
@Composable
fun FilaToque(
    persona: Afiliado, presente: Boolean, menu: String, grupo: String?, primera: Boolean, ultima: Boolean,
    alTocar: () -> Unit
) {
    val c = colores
    val forma = RoundedCornerShape(
        topStart = if (primera) Medidas.rM else 0.dp, topEnd = if (primera) Medidas.rM else 0.dp,
        bottomStart = if (ultima) Medidas.rM else 0.dp, bottomEnd = if (ultima) Medidas.rM else 0.dp
    )
    val nombre = nombreCompleto(persona)
    Column(Modifier.fillMaxWidth().clip(forma).background(if (presente) c.superficie2 else c.superficie)) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(role = Role.Checkbox, onClick = alTocar)
                    .semantics { contentDescription = "$nombre, ${if (menu == "ayuda_social") "ayuda social, " else ""}${if (presente) "marcada" else "sin marcar"}" }
                    .defaultMinSize(minHeight = 64.dp).padding(Medidas.e4, 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(persona, grupo)
                Spacer(Modifier.width(Medidas.e3))
                Column(Modifier.weight(1f)) {
                    Text(nombre, style = tipos.textoFuerte, color = c.tinta)
                    Text((if (grupo != null) etiquetaGrupoEtario(grupo) else "Sin grupo") + if (menu == "ayuda_social") " · Ayuda social" else "", style = tipos.chico, color = c.tintaSuave)
                }
                Spacer(Modifier.width(Medidas.e3))
                // Como .toque-marca en la web: el círculo se llena y el ✓ crece en 150 ms.
                val fondoMarca by animateColorAsState(if (presente) c.acento else Color.Transparent, tween(150, easing = SALIDA), label = "fondo")
                val bordeMarca by animateColorAsState(if (presente) c.acento else c.contorno, tween(150, easing = SALIDA), label = "borde")
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(fondoMarca)
                        .border(2.dp, bordeMarca, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Con el nombre completo: dentro de una fila tomaría el de RowScope.
                    androidx.compose.animation.AnimatedVisibility(
                        presente,
                        enter = scaleIn(tween(150, easing = ENFATIZADA), initialScale = .25f) + fadeIn(tween(150, easing = SALIDA)),
                        exit = scaleOut(tween(150, easing = ENFATIZADA), targetScale = .25f) + fadeOut(tween(150, easing = SALIDA))
                    ) { Icono("ok", 18.dp, Color.White) }
                }
            }
        }
        if (!ultima) Separador()
    }
}

/** Quienes comieron sin estar en el padrón: solo se cuentan, por menú, con −/+. */
@Composable
fun ContadorSinInscribir(at: Atencion, alCambiar: (SinInscribir) -> Unit) {
    val c = colores
    val actual = sinInscribirDelDia(at)
    Superficie(Modifier.padding(top = Medidas.e4)) {
        Column(Modifier.padding(Medidas.e4, Medidas.e3)) {
            Text("Comieron sin estar en el padrón", style = tipos.textoFuerte, color = c.tinta, modifier = Modifier.padding(bottom = Medidas.e2))
            for ((clave, nombre, valor) in listOf(Triple("normal", "Menú normal", actual.normal), Triple("ayudaSocial", "Ayuda social", actual.ayudaSocial))) {
                Row(Modifier.fillMaxWidth().padding(vertical = Medidas.e1), verticalAlignment = Alignment.CenterVertically) {
                    Text(nombre, style = tipos.texto, color = c.tinta, modifier = Modifier.weight(1f))
                    fun poner(delta: Int) {
                        val n = maxOf(0, valor + delta)
                        alCambiar(if (clave == "normal") SinInscribir(n, actual.ayudaSocial) else SinInscribir(actual.normal, n))
                    }
                    BotonPaso("−", "Uno menos, ${nombre.lowercase()}") { poner(-1) }
                    Text("$valor", style = tipos.seccion, color = c.titulo, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    BotonPaso("+", "Uno más, ${nombre.lowercase()}") { poner(1) }
                }
            }
        }
    }
}

@Composable
private fun BotonPaso(texto: String, descripcion: String, onClick: () -> Unit) {
    val c = colores
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(999.dp)).border(1.5.dp, c.contorno, RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) { Text(texto, style = tipos.titulo, color = c.acentoTexto) }
}

/** Un día anterior: plegado en una línea; abierto, su ficha completa. */
@Composable
private fun FichaDia(
    at: Atencion, padron: Padron, abierta: Boolean, primera: Boolean, ultima: Boolean,
    alAbrir: () -> Unit, cambiar: ((Atencion) -> Atencion) -> Unit,
    alternar: (Afiliado) -> Unit,
    marcarTodos: (List<Afiliado>, Boolean) -> Unit, quitarDia: () -> Unit
) {
    val c = colores
    val d = desgloseDelDia(at)
    val rec = recaudacionDelDiaCent(at)
    val forma = RoundedCornerShape(
        topStart = if (primera) Medidas.rM else 0.dp, topEnd = if (primera) Medidas.rM else 0.dp,
        bottomStart = if (ultima) Medidas.rM else 0.dp, bottomEnd = if (ultima) Medidas.rM else 0.dp
    )
    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie)) {
        Row(
            Modifier.fillMaxWidth().background(if (abierta) c.superficie2 else c.superficie).clickable(role = Role.Button, onClick = alAbrir)
                .defaultMinSize(minHeight = 56.dp).padding(Medidas.e4, Medidas.e3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)
        ) {
            Text(if (at.fecha.isNotEmpty()) aDdMmAa(at.fecha) else "Sin fecha", style = tipos.textoNegrita, color = c.titulo)
            val resumen = buildList {
                add("${d.total} raciones")
                if (d.ayudaSocial > 0) add("${d.ayudaSocial} de ayuda social")
                if (rec != null) add("S/ ${formatearSoles(rec)}")
            }
            Text(resumen.joinToString(" · "), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.weight(1f))
            Icono("flecha", 18.dp, if (abierta) c.acentoTexto else c.tintaSuave, Modifier.rotate(if (abierta) 90f else 0f))
        }
        if (abierta) {
            Column(Modifier.padding(Medidas.e4, Medidas.e3, Medidas.e4, Medidas.e4)) {
                CampoFecha("Fecha", at.fecha, { f -> cambiar { it.copy(fecha = f) } })
                Spacer(Modifier.height(Medidas.e3))
                RegistroDelDia(at, cambiar)
                Spacer(Modifier.height(Medidas.e3))
                Campo("Nota", at.nota, { n -> cambiar { it.copy(nota = n) } })
                Chips(Modifier.padding(top = Medidas.e3)) {
                    Chip("Raciones", "${d.total}"); Chip("Normal", "${d.normal}"); Chip("Ayuda social", "${d.ayudaSocial}")
                    Chip("Recaudado", if (rec == null) "—" else "S/ ${formatearSoles(rec)}")
                }
                if (d.origen == Origen.LEGADO) {
                    Text("Estas ${d.total} raciones vienen de la versión anterior. Si marcas la lista, se cuentan de la lista.",
                        style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
                }
                val activos = remember(padron.afiliados, at.fecha) {
                    afiliadosActivos(padron, at.fecha.ifEmpty { hoyIso() }).sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
                }
                Acciones(Modifier.padding(top = 10.dp)) {
                    Boton("Marcar todos", { marcarTodos(activos, true) }, diminuto = true)
                    Boton("Quitar todos", { marcarTodos(activos, false) }, diminuto = true)
                    Boton("Quitar el día", quitarDia, diminuto = true, tipo = TipoBoton.PELIGRO)
                }
                var filtro by remember { mutableStateOf("") }
                Campo("Buscar persona", filtro, { filtro = it }, Modifier.padding(top = 10.dp), placeholder = "Buscar…")
                val aguja = normalizarNombre(filtro)
                val lista = activos.filter { aguja.isEmpty() || claveBusqueda(it).contains(aguja) }
                val marcados = at.asistencias.map { it.afiliadoId }.toSet()
                val menus = at.asistencias.associate { it.afiliadoId to it.tipoMenu }
                Spacer(Modifier.height(Medidas.e3))
                if (lista.isEmpty()) {
                    Pista(if (aguja.isNotEmpty()) "Nadie coincide con esa búsqueda." else "No hay personas activas en el padrón para esa fecha.")
                } else {
                    Superficie {
                        lista.forEachIndexed { i, p ->
                            FilaToque(p, p.id in marcados, menus[p.id] ?: menuPorDefecto(p), grupoEtario(p, at.fecha.ifEmpty { hoyIso() }),
                                i == 0, i == lista.size - 1, { alternar(p) })
                        }
                    }
                }
                ContadorSinInscribir(at) { nuevo -> cambiar { it.copy(sinInscribir = nuevo) } }
                var precioAbierto by remember { mutableStateOf(false) }
                Enlace(if (precioAbierto) "Ocultar el precio del menú de este día" else "Precio del menú de este día", { precioAbierto = !precioAbierto }, Modifier.padding(top = 10.dp))
                if (precioAbierto) {
                    Pista(if (at.precioTomadoDeConfig) "Copiado del precio de arriba. Cámbialo solo si ese día fue distinto." else "Precio propio de este día.", Modifier.padding(bottom = Medidas.e2))
                    CampoSoles("Menú normal (S/)", at.precioMenuNormalCent, { v -> cambiar { it.copy(precioMenuNormalCent = v, precioTomadoDeConfig = false) } })
                    Spacer(Modifier.height(Medidas.e3))
                    CampoSoles("Menú de ayuda social (S/)", at.precioMenuAyudaSocialCent, { v -> cambiar { it.copy(precioMenuAyudaSocialCent = v, precioTomadoDeConfig = false) } })
                }
            }
        }
        if (!ultima) Separador()
    }
}


/**
 * El registro del día: qué se cocinó y cuántas raciones se prepararon. Los
 * escribe la encargada; las servidas se cuentan de la lista y se muestran al
 * lado, sin corregir ninguna de las dos.
 */
@Composable
private fun RegistroDelDia(at: Atencion, cambiar: ((Atencion) -> Atencion) -> Unit) {
    val c = colores
    val servidas = desgloseDelDia(at).total
    Campo("Qué se cocinó", at.menuDelDia, { v -> cambiar { it.copy(menuDelDia = v) } }, placeholder = "Por ejemplo: arroz con pollo y ensalada", unaLinea = false)
    Spacer(Modifier.height(Medidas.e3))
    Campo(
        "Raciones preparadas", at.racionesPreparadas?.toString() ?: "",
        { v -> cambiar { it.copy(racionesPreparadas = v.toIntOrNull()) } }, soloDigitos = true, maxLargo = 4,
        pista = "Las servidas se cuentan solas de la lista."
    )
    val preparadas = at.racionesPreparadas
    val texto = "Servidas: ${cuantos(servidas, "ración", "raciones")}" + if (preparadas != null) " de ${cuantos(preparadas, "preparada")}." else "."
    ParaLeer(texto)
    Text(texto, style = tipos.textoFuerte, color = c.titulo, modifier = Modifier.padding(top = Medidas.e2))
}
