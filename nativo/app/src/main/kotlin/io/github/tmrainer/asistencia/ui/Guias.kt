// Guias.kt — lo que abre «Ayuda» en cada pantalla: cómo se usa ESA pantalla,
// en pocos pasos, con los mismos nombres de botones que se ven. La del
// Inicio dice qué hay en cada sección y es también la bienvenida de la
// primera vez. La ayuda se abre cuando se pide, en el lugar (no interrumpe).
// Se muestra sobre la pantalla misma: todo se oscurece menos la parte de la
// que habla cada paso, y el texto va sobre lo oscuro, sin ventana ni tarjeta.
package io.github.tmrainer.asistencia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

/**
 * Un paso de una guía: qué hacer, en una o dos frases, y su dibujo. [objetivos]:
 * las partes de la pantalla que se iluminan (Modifier.objetivoGuia); si no
 * están en la pantalla, se ve el dibujo.
 */
class PasoGuia(val titulo: String, val texto: String, val dibujo: String, vararg objetivos: String) {
    val objetivos = objetivos.toList()
}

/** La guía de una pantalla: [nombre] es de qué pantalla («Asistencia»). */
class Guia(val nombre: String, val pasos: List<PasoGuia>)

/** El Inicio: qué hay en cada sección. También es la bienvenida de la primera vez. */
val GUIA_INICIO = Guia("Inicio", listOf(
    PasoGuia("Inicio", "Aquí ves las raciones de hoy, lo recaudado y cuándo entregas el B-1.", "casa", "inicio.hoy"),
    PasoGuia("Asistencia", "Cada día, toca a quien vino a comer. Las raciones se cuentan solas.", "lista", "nav.asistencia"),
    PasoGuia("Padrón", "Las personas de la olla. Aquí inscribes a alguien nuevo y cargas la hoja de la Municipalidad.", "personas", "nav.padron"),
    PasoGuia("B-1", "La rendición de cuentas. Anota cada compra: la app suma y te dice qué falta.", "boleta", "nav.b1"),
    PasoGuia("Entregas", "Cuándo entregas el B-1. El teléfono te avisa antes.", "calendario", "nav.entregas"),
    PasoGuia("Tus datos", "Tus datos están solo en este teléfono. Guarda una copia cada dos semanas.", "escudo", "inicio.datos"),
    PasoGuia("Ayuda", "En cada pantalla, toca «Ayuda» arriba para ver cómo se usa.", "ayuda", "ayuda")
))

val GUIA_ASISTENCIA = Guia("Asistencia", listOf(
    PasoGuia("Toca a quien vino", "Toca el círculo de cada persona que vino a comer. Si te equivocas, tócalo otra vez.", "lista", "asistencia.lista"),
    PasoGuia("O lee su DNI", "Toca «Pasar lista leyendo el DNI» y muestra cada DNI a la cámara, uno tras otro.", "camara", "asistencia.dni"),
    PasoGuia("Busca o mira quién falta", "Escribe un nombre o un DNI en «Buscar». «Ver los que faltan» muestra a quien no marcaste.", "buscar", "asistencia.buscar"),
    PasoGuia("Quien no está en el padrón", "Si comió alguien que no está inscrito, anótalo en «Comieron sin estar en el padrón».", "personaMas", "asistencia.sinPadron"),
    PasoGuia("Otros días", "Para un día que pasó, toca «+ Registrar otro día» o abre ese día en «Otros días».", "reloj", "asistencia.otros"),
    PasoGuia("Precio del menú", "Si cambia el precio, cámbialo en «Precio del menú». Vale desde hoy.", "lapiz", "asistencia.precio"),
    PasoGuia("El resumen", "Abajo ves las raciones y lo recaudado. «Reporte del día y del mes» lo arma en PDF o Excel.", "archivo", "asistencia.reporte", "asistencia.cifras")
))

val GUIA_PADRON = Guia("Padrón", listOf(
    PasoGuia("Las personas de la olla", "Aquí están todas. Toca a una para ver sus datos, corregirlos o darla de baja.", "personas", "padron.lista"),
    PasoGuia("Busca y filtra", "Escribe un nombre o un DNI. Las fichas de arriba muestran solo las activas, a quienes les faltan datos y más.", "buscar", "padron.buscar", "padron.filtros"),
    PasoGuia("Inscribe a alguien", "Toca «Inscribir», abajo. Puedes leer su DNI con la cámara o escribir los datos.", "personaMas", "padron.inscribir"),
    PasoGuia("Llévala a la Municipalidad", "Quien inscribes queda pendiente. Cuando la registren, toca «Ya la registraron en la Municipalidad».", "aviso"),
    PasoGuia("La hoja de la Municipalidad", "Cuando te den la hoja en Excel, toca «Cargar la hoja (Excel)». Antes de guardar verás qué cambia.", "archivo", "padron.hoja")
))

val GUIA_INSCRIBIR = Guia("Inscribir", listOf(
    PasoGuia("Lee el DNI", "Toca «Leer el DNI con la cámara». La app llena los datos y te dice qué revisar.", "camara", "inscribir.dni"),
    PasoGuia("O escribe los datos", "Copia los nombres y el DNI tal como están en el documento. Los campos con * son obligatorios.", "lapiz", "inscribir.paterno", "inscribir.nombres"),
    PasoGuia("Revisa e inscribe", "Compara con el DNI en la mano y toca «Inscribir».", "ok", "inscribir.boton")
))

val GUIA_B1 = Guia("B-1", listOf(
    PasoGuia("El B-1 por pasos", "Se llena en 6 pasos. Avanza con «Siguiente» y vuelve con «Atrás».", "boleta", "b1.barra"),
    PasoGuia("Centro y periodo", "Pasos 1 y 2: los datos de tu olla y qué semana, quincena o mes rindes. Cópialos de tu B-1 anterior.", "casa", "b1.paso1", "b1.paso2"),
    PasoGuia("Ingresos", "Paso 3: cuánto dinero recibiste y cuándo.", "archivo", "b1.paso3"),
    PasoGuia("Compras", "Paso 4: toca «Compra» por cada boleta y cópiala tal cual. La app suma sola.", "mas", "b1.paso4"),
    PasoGuia("Firmas", "Paso 5: los nombres que van debajo de cada línea de firma.", "lapiz", "b1.paso5"),
    PasoGuia("Revisa", "Paso 6: la app te dice qué falta o está mal. Arriba, en ámbar, los pasos con algo pendiente.", "aviso", "b1.paso6"),
    PasoGuia("Mira y manda el B-1", "Toca «Ver cómo queda en la hoja» para verlo como en papel. Expórtalo en PDF o Excel para mandarlo.", "archivo", "b1.hoja")
))

val GUIA_ENTREGAS = Guia("Entregas", listOf(
    PasoGuia("Cada cuánto entregas", "Elige si entregas el B-1 cada semana, cada quincena o cada mes.", "calendario", "entregas.cada"),
    PasoGuia("Una fecha que conoces", "Pon una fecha de entrega, la próxima o una pasada. La app calcula las demás.", "reloj", "entregas.fecha"),
    PasoGuia("Avisarme", "Elige cuántos días antes quieres el aviso. Toca «Activar los avisos» para que el teléfono te avise.", "aviso", "entregas.avisar"),
    PasoGuia("Marca cada entrega", "En «Próximas entregas», cambia «Pendiente» por «Entregada» cuando la entregues.", "ok", "entregas.lista")
))

/**
 * Dónde está cada parte que una guía puede iluminar. Cada parte se anota con
 * Modifier.objetivoGuia; [lista] es la lista larga de la pantalla (LazyColumn),
 * donde lo de más abajo todavía no existe hasta que se baja.
 */
@OptIn(ExperimentalFoundationApi::class)
class Objetivos {
    internal val lugares = HashMap<String, LayoutCoordinates>()
    internal val pedidos = HashMap<String, BringIntoViewRequester>()
    internal var lista: ScrollableState? = null
}

val LocalObjetivos = staticCompositionLocalOf { Objetivos() }

/** Esta parte de la pantalla es [id] para las guías: un paso puede iluminarla. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.objetivoGuia(id: String): Modifier = composed {
    val objetivos = LocalObjetivos.current
    val pedido = remember { BringIntoViewRequester() }
    DisposableEffect(id) {
        objetivos.pedidos[id] = pedido
        onDispose {
            if (objetivos.pedidos[id] === pedido) { objetivos.pedidos.remove(id); objetivos.lugares.remove(id) }
        }
    }
    bringIntoViewRequester(pedido).onGloballyPositioned { objetivos.lugares[id] = it }
}

/** La lista larga de esta pantalla: la guía la baja para encontrar lo que ilumina. */
@Composable
fun ListaParaGuia(estado: ScrollableState) {
    val objetivos = LocalObjetivos.current
    DisposableEffect(estado) {
        objetivos.lista = estado
        onDispose { if (objetivos.lista === estado) objetivos.lista = null }
    }
}

/** Lo que ocupan en la pantalla [ids] juntos; null si ninguno se ve. */
private fun lugarDe(objetivos: Objetivos, ids: List<String>): Rect? =
    ids.mapNotNull { id -> objetivos.lugares[id]?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 0f && it.height > 0f } }
        .reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }

/** Trae [id] a la vista. En una lista larga, si aún no existe, la baja hasta que aparezca (y si no, la sube). */
@OptIn(ExperimentalFoundationApi::class)
private suspend fun traerALaVista(objetivos: Objetivos, id: String, salto: Float) {
    objetivos.lista?.let { lista ->
        while (id !in objetivos.pedidos && lista.canScrollForward) lista.animateScrollBy(salto)
        while (id !in objetivos.pedidos && lista.canScrollBackward) lista.animateScrollBy(-salto)
    }
    objetivos.pedidos[id]?.bringIntoView()
}

// Sobre lo oscuro, en tema claro u oscuro: siempre letra clara.
// Bien oscuro: lo de abajo no se lee y no se mezcla con el texto de la guía.
private val OSCURO = Color(0xFF0A0F0B).copy(alpha = .96f)
private val FONDO_TEXTO = Color(0xFF0A0F0B)
private val CLARO = Color.White
private val CLARO_SUAVE = Color.White.copy(alpha = .84f)

/**
 * Una guía, paso a paso, sobre la pantalla: todo se oscurece menos lo que
 * explica el paso, con un borde que brilla; el texto va al lado con más lugar.
 * «Escuchar» lee el paso; «Atrás» / «Siguiente»; «Listo» al final; «Cerrar»
 * o el botón de volver del teléfono la cierran cuando sea.
 */
@Composable
fun GuiaFoco(ctx: Contexto, guia: Guia, alCerrar: () -> Unit) {
    var paso by remember(guia) { mutableIntStateOf(0) }
    val p = guia.pasos[paso]
    val ultimo = paso == guia.pasos.size - 1
    val objetivos = LocalObjetivos.current
    val c = colores
    BackHandler { if (paso > 0) paso -= 1 else alCerrar() }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "Cómo se usa: ${guia.nombre}" }
    ) {
        val alto = constraints.maxHeight.toFloat()
        val ancho = constraints.maxWidth.toFloat()
        // Lo que se ilumina, seguido cuadro a cuadro: se mueve si la pantalla se desplaza.
        var lugar by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(guia, paso) {
            p.objetivos.firstOrNull()?.let { launch { traerALaVista(objetivos, it, alto * .6f) } }
            while (true) { withFrameNanos { }; lugar = lugarDe(objetivos, p.objetivos) }
        }
        // De un paso al siguiente, el hueco viaja de una parte a la otra.
        val viaje = remember { Animatable(1f) }
        var desde by remember { mutableStateOf<Rect?>(null) }
        var dibujado by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(paso) { desde = dibujado; viaje.snapTo(0f); viaje.animateTo(1f, tween(380)) }
        val densidad = LocalDensity.current
        val margen = with(densidad) { 8.dp.toPx() }
        // El texto y sus botones necesitan este lugar. Si lo iluminado es tan alto que no
        // lo deja (teléfono chico, acostado o letra grande), se ilumina solo su comienzo.
        val lugarTexto = with(densidad) { 240.dp.toPx() }
        // Dentro de la pantalla: el borde que brilla se ve entero (el menú de abajo va pegado al borde).
        val borde = with(densidad) { 4.dp.toPx() }
        val r = lugar?.inflate(margen)?.intersect(Rect(borde, borde, ancho - borde, alto - borde))?.let { r ->
            when {
                alto - r.bottom >= r.top -> if (alto - r.bottom < lugarTexto) r.copy(bottom = maxOf(r.top + 4 * margen, alto - lugarTexto)) else r
                r.top < lugarTexto -> r.copy(top = minOf(r.bottom - 4 * margen, lugarTexto))
                else -> r
            }
        }
        val abajo = r == null || alto - r.bottom >= r.top
        val hueco = r?.let { desde?.let { d -> lerp(d, it, viaje.value) } ?: it }
        SideEffect { dibujado = hueco }
        val brillo by rememberInfiniteTransition(label = "brillo").animateFloat(
            .35f, .7f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "brillo"
        )
        val tono = c.marcaSuave

        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(OSCURO)
            if (hueco != null) {
                val esquina = CornerRadius(16.dp.toPx())
                drawRoundRect(Color.Black, hueco.topLeft, hueco.size, esquina, blendMode = BlendMode.Clear)
                val halo = 6.dp.toPx()
                drawRoundRect(tono.copy(alpha = brillo * .45f), hueco.topLeft - Offset(halo / 2, halo / 2), Size(hueco.width + halo, hueco.height + halo),
                    CornerRadius(esquina.x + halo / 2), style = Stroke(halo))
                drawRoundRect(CLARO, hueco.topLeft, hueco.size, esquina, style = Stroke(2.dp.toPx()))
            }
        }

        // El texto, del lado con más lugar; sin nada que iluminar, al centro y con su dibujo.
        Column(
            Modifier.fillMaxSize()
                .then(
                    when {
                        r == null -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                        abajo -> Modifier.padding(top = with(densidad) { r.bottom.toDp() } + Medidas.e5).windowInsetsPadding(WindowInsets.navigationBars)
                        else -> Modifier.padding(bottom = with(densidad) { (alto - r.top).toDp() } + Medidas.e5).windowInsetsPadding(WindowInsets.statusBars)
                    }
                )
                .verticalScroll(rememberScrollState()).padding(horizontal = Medidas.e5),
            verticalArrangement = when { r == null -> Arrangement.Center; abajo -> Arrangement.Top; else -> Arrangement.Bottom }
        ) {
            // Detrás del texto, lo oscuro se cierra del todo, con bordes que se funden:
            // lo de abajo no se mezcla con las letras (sin tarjeta ni borde).
            val fundido = with(densidad) { 32.dp.toPx() }
            Column(Modifier.widthIn(max = Medidas.anchoColumna).fillMaxWidth().align(Alignment.CenterHorizontally).drawBehind {
                val alto = size.height + 2 * fundido
                val f = fundido / alto
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, f to FONDO_TEXTO, 1 - f to FONDO_TEXTO, 1f to Color.Transparent, startY = -fundido, endY = size.height + fundido),
                    topLeft = Offset(-size.width, -fundido), size = Size(size.width * 3, alto)
                )
            }) {
                if (r == null) {
                    Box(Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = .1f)).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                        Icono(p.dibujo, 60.dp, tono)
                    }
                    Spacer(Modifier.height(Medidas.e4))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cómo se usa: ${guia.nombre} · ${paso + 1} de ${guia.pasos.size}", style = tipos.chicoFuerte, color = tono, modifier = Modifier.weight(1f))
                    BotonFoco("Cerrar", alCerrar, chico = true)
                }
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(p.titulo, style = tipos.titulo.copy(fontSize = tipos.titulo.fontSize * 1.2f), color = CLARO,
                        modifier = Modifier.padding(top = Medidas.e2, bottom = Medidas.e2).semantics { heading() })
                    Text(p.texto, style = tipos.texto, color = CLARO_SUAVE)
                }
                Row(Modifier.padding(top = Medidas.e4), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    guia.pasos.forEachIndexed { i, _ ->
                        Box(Modifier.width(if (i == paso) 22.dp else 8.dp).height(8.dp).clip(CircleShape).background(if (i == paso) CLARO else Color.White.copy(alpha = .35f)))
                    }
                }
                Row(Modifier.padding(top = Medidas.e4), horizontalArrangement = Arrangement.spacedBy(Medidas.e3), verticalAlignment = Alignment.CenterVertically) {
                    if (paso > 0) BotonFoco("Atrás", { paso -= 1 })
                    BotonFoco(if (ultimo) "Listo" else "Siguiente", { if (ultimo) alCerrar() else paso += 1 }, principal = true)
                    BotonFoco("Escuchar", { ctx.decir("${p.titulo}. ${p.texto}") }, icono = "voz", chico = true)
                }
            }
        }
    }
}

/** Un botón sobre lo oscuro: blanco relleno el principal; borde blanco los demás. */
@Composable
private fun BotonFoco(texto: String, onClick: () -> Unit, principal: Boolean = false, icono: String? = null, chico: Boolean = false) {
    val c = colores
    val forma = RoundedCornerShape(999.dp)
    val color = if (principal) c.marca else CLARO
    Row(
        Modifier.defaultMinSize(minHeight = 48.dp).clip(forma)
            .then(if (principal) Modifier.background(CLARO) else if (chico) Modifier else Modifier.border(1.5.dp, Color.White.copy(alpha = .7f), forma))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (chico) Medidas.e3 else Medidas.e5),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icono != null) { Icono(icono, 20.dp, color); Spacer(Modifier.width(6.dp)) }
        Text(texto, style = if (chico) tipos.chicoFuerte else tipos.textoFuerte, color = color)
    }
}
