// Pasar lista. Marcar a alguien es UN toque en su fila. El menú sale de cómo figura
// en el padrón (habitual → normal, ayuda social → menú de ayuda social) y solo hace
// falta tocarlo cuando ese día come otro. Sin desplegables.
//
// «Asistencia de hoy» siempre apunta a hoy. El registro del día NO se crea al
// abrir la pantalla: se crea cuando se marca a la primera persona.
package io.github.tmrainer.asistencia.ui.pantallas

import io.github.tmrainer.asistencia.nucleo.envio.correoDeAsistencia
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.tmrainer.asistencia.ui.ListaParaGuia
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.componentes.ventana
import io.github.tmrainer.asistencia.ui.componentes.horaAhora
import io.github.tmrainer.asistencia.ui.componentes.BloqueEnvio
import io.github.tmrainer.asistencia.ui.camara.CamaraDni
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasConTodos
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.etiquetaGrupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.grupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.menuPorDefecto
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.calculos.sinInscribirDelDia
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.Atencion
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.SinInscribir
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
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import android.content.Context
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Avatar
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.CampoSoles
import io.github.tmrainer.asistencia.ui.componentes.EstadoGuardado
import io.github.tmrainer.asistencia.ui.componentes.EstadoVacio
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.PanelHallazgos
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.componentes.vibrar
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lo que se busca de una persona: nombre sin tildes ni mayúsculas, y su DNI. */
internal fun claveBusqueda(p: Afiliado) = "${normalizarNombre(nombreCompleto(p))} ${p.numeroDocumento}"

/** Cambia un día; si todavía no estaba guardado (el de hoy), lo agrega. */
internal fun cambiarDia(estado: Estado, base: Atencion, cambio: (Atencion) -> Atencion) {
    estado.cambiarPadron { p ->
        val i = p.atenciones.indexOfFirst { it.id == base.id }
        if (i >= 0) p.copy(atenciones = p.atenciones.toMutableList().also { it[i] = cambio(it[i]) })
        else p.copy(atenciones = p.atenciones + cambio(base))
    }
}

/** Marca o quita a [persona] en el día [at], con vibración y «Deshacer». */
internal fun alternarAsistencia(ctx: Contexto, contexto: Context, at: Atencion, persona: Afiliado) {
    val antes = at
    val vino = at.asistencias.none { it.afiliadoId == persona.id }
    cambiarDia(ctx.estado, at) { a ->
        if (vino) a.copy(legado = null, asistencias = a.asistencias + Asistencia(persona.id, menuPorDefecto(persona)))
        else a.copy(asistencias = a.asistencias.filter { it.afiliadoId != persona.id })
    }
    vibrar(contexto)
    ctx.avisos.mostrar("${if (vino) "Marcaste a" else "Quitaste a"} ${persona.nombres.ifEmpty { nombreCompleto(persona) }}") {
        cambiarDia(ctx.estado, antes) { a -> a.copy(asistencias = antes.asistencias, legado = antes.legado) }
    }
}

/** Marca (o quita) a todas las [personas] en el día [at], con «Deshacer». */
internal fun marcarATodos(ctx: Contexto, at: Atencion, personas: List<Afiliado>, marcar: Boolean) {
    val antes = at
    cambiarDia(ctx.estado, at) { a -> if (marcar) a.copy(legado = null, asistencias = asistenciasConTodos(a, personas)) else a.copy(asistencias = emptyList()) }
    ctx.avisos.mostrar(if (marcar) "Marcaste a todos" else "Quitaste a todos") {
        cambiarDia(ctx.estado, antes) { a -> a.copy(asistencias = antes.asistencias, legado = antes.legado) }
    }
}

// Curvas: ease-out y la enfatizada de Material.
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
    val hoy = hoyIso()

    // El día de hoy: el guardado, o uno nuevo que todavía no se guarda.
    val hoyNueva = remember(hoy) { nuevaAtencion(estado.preciosActuales()).copy(fecha = hoy) }
    val atHoy = padron.atenciones.firstOrNull { it.fecha == hoy } ?: hoyNueva.copy(
        precioMenuNormalCent = config.precioMenuNormalCent, precioMenuAyudaSocialCent = config.precioMenuAyudaSocialCent
    )

    fun cambiarDia(base: Atencion, cambio: (Atencion) -> Atencion) = cambiarDia(estado, base, cambio)

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
    // El grupo de edad de cada uno, una vez (no en cada fila al deslizar).
    val grupos = remember(activos, hoy) { activos.associate { it.id to grupoEtario(it, hoy) } }
    var busqueda by remember { mutableStateOf("") }
    var soloSinMarcar by remember { mutableStateOf(false) }
    val marcados = remember(atHoy.asistencias) { atHoy.asistencias.map { it.afiliadoId }.toSet() }
    val menus = remember(atHoy.asistencias) { atHoy.asistencias.associate { it.afiliadoId to it.tipoMenu } }
    val aguja = normalizarNombre(busqueda)
    val visibles = remember(activos, aguja, soloSinMarcar, marcados) {
        activos.filter { p -> (aguja.isEmpty() || claves.getValue(p.id).contains(aguja)) && !(soloSinMarcar && p.id in marcados) }
    }
    val sinMarcar = activos.count { it.id !in marcados }

    fun alternar(at: Atencion, persona: Afiliado) = alternarAsistencia(ctx, contexto, at, persona)
    fun marcarTodos(at: Atencion, personas: List<Afiliado>, marcar: Boolean) = marcarATodos(ctx, at, personas, marcar)

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
                        "Inscribir a la primera persona" to { ctx.ir(Ruta.INSCRIBIR) }, modifier = Modifier.objetivoGuia("asistencia.vacia"))
                }
            } else {
                // Lo que se usa mientras se pasa lista: el conteo, el buscador y «los que faltan».
                stickyHeader {
                    Column(Modifier.fillMaxWidth().background(c.fondo).padding(bottom = Medidas.e2)) {
                        BarraHoy(atHoy, activos.size)
                        val textoFaltan = when {
                                sinMarcar == 0 -> if (soloSinMarcar) "Ya vinieron todos: ver la lista" else "Ya vinieron todos"
                                soloSinMarcar -> if (sinMarcar == 1) "Viendo a quien falta" else "Viendo los $sinMarcar que faltan"
                                else -> if (sinMarcar == 1) "Ver a quien falta" else "Ver los $sinMarcar que faltan"
                            }
                        // Con poco lugar, el buscador a todo el ancho y el botón debajo: lado a lado se apretaban.
                        if (ventana().angosta) Column(Modifier.padding(top = Medidas.e2).objetivoGuia("asistencia.buscar"), verticalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                            if (activos.size > 12) Campo("Buscar", busqueda, { busqueda = it }, placeholder = "Buscar…")
                            Boton(textoFaltan, { soloSinMarcar = !soloSinMarcar }, diminuto = true, activo = soloSinMarcar, habilitado = !(sinMarcar == 0 && !soloSinMarcar))
                        } else Row(Modifier.padding(top = Medidas.e2).objetivoGuia("asistencia.buscar"), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                            if (activos.size > 12) {
                                Campo("Buscar", busqueda, { busqueda = it }, Modifier.weight(1f), placeholder = "Buscar…")
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
                            grupo = grupos[persona.id],
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
                // Fin de la lista: la barra de buscar deja de quedar fija arriba (un encabezado vacío
                // la empuja). Lo de abajo no es pasar lista y se ve entero, también en la guía.
                stickyHeader(key = "finLista") { }
                // Lo que se cocinó y cuántas raciones se prepararon, después de
                // la lista: pasar lista es lo primero y la primera persona se ve al abrir.
                item {
                    Spacer(Modifier.height(Medidas.e5))
                    Column(Modifier.objetivoGuia("asistencia.cocino")) {
                        TituloSeccion("Hoy se cocinó")
                        RegistroDelDia(atHoy) { cambio -> cambiarDia(atHoy, cambio) }
                    }
                }
                // A UNACEM va la asistencia de HOY (el periodo del resumen es para exportar).
                item {
                    Spacer(Modifier.height(Medidas.e5))
                    Column(Modifier.objetivoGuia("asistencia.unacem")) {
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
            }

            item { Spacer(Modifier.height(Medidas.e4)); PanelHallazgos(hallazgos) }

            // Los días que pasaron, el reporte del día y el resumen tienen su pantalla: aquí,
            // con cientos de días, deslizar daba tirones.
            item {
                Spacer(Modifier.height(Medidas.e5))
                TituloSeccion("Días anteriores", apoyo = true)
                Pista("Para corregir un día que pasó, sacar el reporte del día o ver el resumen.", Modifier.padding(bottom = Medidas.e3))
                Boton("Días anteriores y reportes", { ctx.ir(Ruta.DIAS) }, Modifier.objetivoGuia("asistencia.otros"), icono = "reloj")
            }

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

            item {
                Spacer(Modifier.height(Medidas.e4))
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
                // El verde de los textos: el de los botones, en oscuro, casi no se ve sobre la pista.
                Box(Modifier.fillMaxWidth(fraccion).height(8.dp).background(c.acentoTexto))
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
                // El círculo se llena y el ✓ crece en 150 ms. Una sola animación por fila (antes eran
                // tres): al deslizar rápido se arman muchas filas y cada una pesaba.
                val marca by animateFloatAsState(if (presente) 1f else 0f, tween(150, easing = ENFATIZADA), label = "marca")
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(lerp(Color.Transparent, c.acento, marca))
                        .border(2.dp, lerp(c.contorno, c.acento, marca), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (marca > 0f) Icono("ok", 18.dp, Color.White, Modifier.graphicsLayer { val e = .25f + .75f * marca; scaleX = e; scaleY = e; alpha = marca })
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
                    Text("$valor", style = tipos.seccion, color = c.titulo, modifier = Modifier.widthIn(min = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

/**
 * El registro del día: qué se cocinó y cuántas raciones se prepararon. Los
 * escribe la encargada; las servidas se cuentan de la lista y se muestran al
 * lado, sin corregir ninguna de las dos.
 */
@Composable
internal fun RegistroDelDia(at: Atencion, cambiar: ((Atencion) -> Atencion) -> Unit) {
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
