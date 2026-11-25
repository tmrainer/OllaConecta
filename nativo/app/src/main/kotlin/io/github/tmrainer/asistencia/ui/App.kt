// La cáscara: tema, navegación, cabecera, menú de abajo y el aviso de abajo con
// «Deshacer». Las pantallas van en ui/pantallas.
package io.github.tmrainer.asistencia.ui

import androidx.compose.ui.semantics.heading
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.tmrainer.asistencia.Preferencias
import io.github.tmrainer.asistencia.Voz
import io.github.tmrainer.asistencia.perfilListo
import io.github.tmrainer.asistencia.ui.pantallas.PrimerUso
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import io.github.tmrainer.asistencia.ui.componentes.AvisoAbajo
import io.github.tmrainer.asistencia.ui.componentes.Avisador
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.ventana
import io.github.tmrainer.asistencia.ui.componentes.IconoRelleno
import io.github.tmrainer.asistencia.ui.componentes.IconosBarras
import io.github.tmrainer.asistencia.ui.componentes.Lectura
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
import io.github.tmrainer.asistencia.ui.componentes.LocalLectura
import io.github.tmrainer.asistencia.ui.componentes.TituloParaLeer
import io.github.tmrainer.asistencia.ui.pantallas.PantallaAjustes
import io.github.tmrainer.asistencia.ui.pantallas.PantallaAsistencia
import io.github.tmrainer.asistencia.ui.pantallas.PantallaDia
import io.github.tmrainer.asistencia.ui.pantallas.PantallaDias
import io.github.tmrainer.asistencia.ui.pantallas.PantallaB1
import io.github.tmrainer.asistencia.ui.pantallas.PantallaDatos
import io.github.tmrainer.asistencia.ui.pantallas.PantallaEntregas
import io.github.tmrainer.asistencia.ui.pantallas.PantallaInicio
import io.github.tmrainer.asistencia.ui.pantallas.PantallaHojaB1
import io.github.tmrainer.asistencia.ui.pantallas.PantallaImportar
import io.github.tmrainer.asistencia.ui.pantallas.PantallaInscribir
import io.github.tmrainer.asistencia.ui.pantallas.PantallaPadron
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.TemaOlla
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

/** Las secciones del menú de abajo, en orden. */
enum class Ruta(val ruta: String, val texto: String, val icono: String) {
    INICIO("inicio", "Inicio", "casa"),
    ASISTENCIA("asistencia", "Asistencia", "lista"),
    DIAS("dias", "Días anteriores", "reloj"),
    DIA("dia", "Día anterior", "reloj"),
    PADRON("padron", "Padrón", "personas"),
    INSCRIBIR("inscribir", "Inscribir", "personaMas"),
    IMPORTAR("importar", "Hoja de la Municipalidad", "archivo"),
    HOJA("hoja", "Vista previa del B-1", "boleta"),
    B1("b1", "B-1", "boleta"),
    ENTREGAS("entregas", "Entregas", "calendario"),
    DATOS("datos", "Tus datos", "escudo"),
    AJUSTES("ajustes", "Ajustes", "ajustes")
}

val SECCIONES = listOf(Ruta.INICIO, Ruta.ASISTENCIA, Ruta.PADRON, Ruta.B1, Ruta.ENTREGAS)

/** Lo que toda pantalla recibe: el estado, las preferencias y cómo ir a otra. */
class Contexto(
    val estado: Estado,
    val preferencias: Preferencias,
    val nav: NavHostController,
    val avisos: Avisador,
    val voz: Voz,
    val abrirGuia: (Guia) -> Unit
) {
    /** La bienvenida de la primera vez: la guía del Inicio. */
    fun abrirBienvenida() = abrirGuia(GUIA_INICIO)

    /** Lo que se muestra sobre toda la app (la cámara): tapa también el menú de abajo. */
    var capa by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun abrirCapa(contenido: @Composable () -> Unit) { capa = contenido }
    fun cerrarCapa() { capa = null }

    /** La Lectura de cada pantalla abierta, por su entrada en la navegación. */
    internal val lecturas = HashMap<String, Lectura>()

    /** Dice un texto con la voz del teléfono; si no puede, lo avisa abajo. */
    fun decir(texto: String) {
        voz.decir(texto) { avisos.mostrar("Este teléfono no puede leer en voz alta.") }
    }

    /** Lee la pantalla de arriba: su título y lo que marcó con ParaLeer. */
    fun leerPantalla() {
        val lectura = nav.currentBackStackEntry?.id?.let { lecturas[it] } ?: return
        decir(lectura.texto())
    }

    fun ir(ruta: Ruta, argumento: String? = null) {
        val destino = if (argumento != null) "${ruta.ruta}?a=$argumento" else ruta.ruta
        nav.navigate(destino) {
            if (ruta in SECCIONES) { popUpTo(Ruta.INICIO.ruta) { saveState = false }; launchSingleTop = true }
        }
    }
    fun volver() { if (!nav.popBackStack()) ir(Ruta.INICIO) }
}

@Composable
fun App(estado: Estado, preferencias: Preferencias, voz: Voz, avisoInicial: String? = null, abrir: MutableStateFlow<String?>? = null) {
    val tema by preferencias.tema.collectAsStateWithLifecycle()
    val letra by preferencias.letra.collectAsStateWithLifecycle()
    TemaOlla(tema, letra) {
        val nav = rememberNavController()
        val avisos = remember { Avisador() }
        val objetivos = remember { Objetivos() }
        var guia by remember { mutableStateOf<Guia?>(null) }
        // Quién eres y de qué olla: se pide al abrir mientras falte (PrimerUso).
        val perfil by preferencias.perfil.collectAsStateWithLifecycle()
        val config by estado.config.collectAsStateWithLifecycle()
        var trayendoDatos by remember { mutableStateOf(false) }
        val ctx = remember(nav) { Contexto(estado, preferencias, nav, avisos, voz) { guia = it } }
        val entrada by nav.currentBackStackEntryAsState()
        val rutaActual = entrada?.destination?.route?.substringBefore("?") ?: Ruta.INICIO.ruta
        val pidiendoPerfil = !perfilListo(perfil) && !(trayendoDatos && rutaActual == Ruta.DATOS.ruta)
        val c = colores
        // Arriba está siempre la cabecera verde oscuro; abajo, la barra del tema elegido.
        IconosBarras(arribaOscuro = true, abajoOscuro = c.esOscuro)
        LaunchedEffect(avisoInicial) { avisoInicial?.let { avisos.mostrar(it) } }
        // Tocar un aviso del teléfono abre su pantalla (MainActivity deja la ruta en `abrir`).
        if (abrir != null) LaunchedEffect(abrir) {
            abrir.collect { destino ->
                Ruta.values().firstOrNull { it.ruta == destino }?.let { ctx.ir(it) }
                if (destino != null) abrir.value = null
            }
        }

        CompositionLocalProvider(LocalAvisos provides avisos, LocalObjetivos provides objetivos) {
            Box(Modifier.fillMaxSize().background(c.fondo)) {
                // Lo de abajo de PrimerUso y de una guía no se usa: TalkBack tampoco lo lee.
                Column(Modifier.fillMaxSize().then(if (pidiendoPerfil || guia != null) Modifier.clearAndSetSemantics { } else Modifier)) {
                    Box(Modifier.weight(1f)) {
                        // Al cambiar de pantalla, una después de la otra (fundido en secuencia, como pide
                        // Material para el menú de abajo): la de antes se va en 60 ms y recién entonces
                        // entra la nueva. Nunca se ven las dos encimadas, como en el cruce por defecto.
                        NavHost(
                            nav, startDestination = Ruta.INICIO.ruta,
                            enterTransition = { ENTRA }, exitTransition = { SALE },
                            popEnterTransition = { ENTRA }, popExitTransition = { SALE }
                        ) {
                            pantalla(ctx, Ruta.INICIO.ruta) { PantallaInicio(ctx) }
                            pantalla(ctx, Ruta.ASISTENCIA.ruta) { PantallaAsistencia(ctx) }
                            pantalla(ctx, Ruta.DIAS.ruta) { PantallaDias(ctx) }
                            pantalla(ctx, "${Ruta.DIA.ruta}?a={a}") { PantallaDia(ctx, it.arguments?.getString("a")) }
                            pantalla(ctx, Ruta.PADRON.ruta) { PantallaPadron(ctx) }
                            pantalla(ctx, Ruta.INSCRIBIR.ruta) { PantallaInscribir(ctx) }
                            pantalla(ctx, Ruta.IMPORTAR.ruta) { PantallaImportar(ctx) }
                            pantalla(ctx, "${Ruta.HOJA.ruta}?a={a}") { PantallaHojaB1(ctx, it.arguments?.getString("a")) }
                            pantalla(ctx, "${Ruta.B1.ruta}?a={a}") { PantallaB1(ctx, it.arguments?.getString("a")) }
                            pantalla(ctx, Ruta.B1.ruta) { PantallaB1(ctx, null) }
                            pantalla(ctx, Ruta.ENTREGAS.ruta) { PantallaEntregas(ctx) }
                            pantalla(ctx, Ruta.DATOS.ruta) { PantallaDatos(ctx) }
                            pantalla(ctx, Ruta.AJUSTES.ruta) { PantallaAjustes(ctx) }
                        }
                    }
                    // Copiar de la hoja y cargar la hoja llevan su propio botón abajo: el menú no se pone en medio.
                    if (rutaActual != Ruta.INSCRIBIR.ruta && rutaActual != Ruta.IMPORTAR.ruta) BarraAbajo(rutaActual) { ctx.ir(it) }
                }
                AvisoAbajo(avisos, Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp + Medidas.e3 + avisos.sobreBarra).windowInsetsPadding(WindowInsets.navigationBars))
                // Mientras falte el perfil, la app no se usa; salvo para ir a «Tus datos» a traerlos de otro teléfono.
                if (pidiendoPerfil) {
                    PrimerUso(
                        ctx, ollaPropuesta = config.ultimoCentro?.nombreCentro?.trim().orEmpty(),
                        alTraerDatos = { trayendoDatos = true; ctx.ir(Ruta.DATOS) },
                        alTerminar = { trayendoDatos = false; if (!preferencias.bienvenidaVista.value) ctx.abrirBienvenida() }
                    )
                }
                // La guía entra y se va con un fundido; la última abierta se sigue viendo mientras se va.
                var guiaVista by remember { mutableStateOf<Guia?>(null) }
                if (guia != null) guiaVista = guia
                AnimatedVisibility(guia != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
                    guiaVista?.let { g -> GuiaFoco(ctx, g) { if (g === GUIA_INICIO) preferencias.marcarBienvenidaVista(); guia = null } }
                }
                ctx.capa?.let { contenido ->
                    // Los toques no pasan a la pantalla de abajo.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                        BackHandler { ctx.cerrarCapa() }
                        contenido()
                    }
                }
            }
        }
    }
}

private val SALE = fadeOut(tween(60))
private val ENTRA = fadeIn(tween(140, delayMillis = 60))

/** Un destino de la navegación con su propia Lectura: lo que se lee es de esta pantalla y no de la que se va. */
private fun NavGraphBuilder.pantalla(ctx: Contexto, ruta: String, contenido: @Composable (NavBackStackEntry) -> Unit) =
    composable(ruta) { entrada ->
        val lectura = remember { Lectura() }
        DisposableEffect(entrada.id) {
            ctx.lecturas[entrada.id] = lectura
            onDispose { ctx.lecturas.remove(entrada.id) }
        }
        CompositionLocalProvider(LocalLectura provides lectura) { contenido(entrada) }
    }

/**
 * Cabecera verde: flecha para volver (salvo en el Inicio), título, subtítulo
 * y, a la derecha y en una fila, «Ayuda» con ícono y palabra (siempre, con la
 * [guia] de esta pantalla) y, a su derecha, la rueda de Ajustes sola (en el
 * Inicio, [conAjustes]): un ícono que todos conocen. [guiaSegun] elige la guía al
 * tocar «Ayuda» (la del B-1 depende del paso y de si ya se vio la general). «Escuchar», si se pidió
 * en Ajustes, va como ícono a la izquierda de «Ayuda». Nada escondido tras «⋮».
 */
@Composable
fun Cabecera(ctx: Contexto, titulo: String, subtitulo: String? = null, conVolver: Boolean = true, conAjustes: Boolean = false, guia: Guia = GUIA_INICIO, guiaSegun: (() -> Guia)? = null, alVolver: () -> Unit = { ctx.volver() }) {
    val c = colores
    val conVoz by ctx.preferencias.vozBoton.collectAsStateWithLifecycle()
    TituloParaLeer(titulo)
    // Sobre el verde oscuro, la capa al tocar sus botones es blanca (la negra no se ve).
    Column(Modifier.fillMaxWidth().background(c.marca).windowInsetsPadding(WindowInsets.statusBars)) { CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(
            Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp).padding(start = if (conVolver) Medidas.e1 else Medidas.e4, end = Medidas.e3, top = Medidas.e2, bottom = Medidas.e2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (conVolver) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = alVolver).semantics { contentDescription = "Volver" }, contentAlignment = Alignment.Center) {
                    Icono("atras", 26.dp, Color.White)
                }
            }
            // La guía ilumina el título con su subtítulo: solo el subtítulo cortaba el título por la mitad.
            Column(Modifier.weight(1f).then(if (subtitulo != null) Modifier.objetivoGuia("cabecera.subtitulo") else Modifier)) {
                Text(titulo, style = tipos.titulo, color = Color.White, modifier = Modifier.semantics { heading() })
                if (subtitulo != null) Text(subtitulo, style = tipos.chico, color = c.marcaSuave)
            }
            Spacer(Modifier.width(Medidas.e2))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                // «Escuchar» con un toque, si se pidió en Ajustes (también está en Ayuda).
                if (conVoz) BotonIconoCabecera("voz", "Escuchar") { ctx.leerPantalla() }
                // La ayuda en el lugar, cuando se pide: cómo se usa ESTA pantalla (Guias.kt).
                // Con poco lugar (pantalla angosta o letra grande), solo el ícono: el título no se aprieta.
                val abrirAyuda = { ctx.abrirGuia(guiaSegun?.invoke() ?: guia) }
                if (ventana().angosta) BotonIconoCabecera("ayuda", "Ayuda", Modifier.objetivoGuia("ayuda"), abrirAyuda)
                else BotonCabecera("ayuda", "Ayuda", Modifier.objetivoGuia("ayuda"), abrirAyuda)
                if (conAjustes) BotonIconoCabecera("ajustes", "Ajustes", Modifier.objetivoGuia("ajustes")) { ctx.ir(Ruta.AJUSTES) }
            }
        }
    } }
}

/** Un botón de la cabecera: ícono y palabra, 48 dp de alto, con borde claro sobre el verde. */
@Composable
private fun BotonCabecera(icono: String, texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = .5f), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Medidas.e3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icono(icono, 20.dp, Color.White)
        Spacer(Modifier.width(6.dp))
        Text(texto, style = tipos.chicoFuerte, color = Color.White)
    }
}

/** Un botón de la cabecera solo con ícono: redondo, 48 dp, y su nombre para TalkBack. */
@Composable
private fun BotonIconoCabecera(icono: String, nombre: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = .5f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = nombre },
        contentAlignment = Alignment.Center
    ) { Icono(icono, 24.dp, Color.White) }
}

/**
 * El menú de abajo, igual en todas las pantallas: ícono y palabra, píldora en la sección actual.
 * Si todas entran, las cinco miden lo mismo. Con letra grande «Asistencia» no entra en un
 * quinto del ancho: entonces cada pestaña toma el ancho
 * de su palabra («B-1» cede lugar) y, si ni así entran, se achican todas por igual. Partirlas
 * en dos líneas las cortaba por la mitad («Asisten-cia»).
 */
@Composable
fun BarraAbajo(rutaActual: String, alIr: (Ruta) -> Unit) {
    val c = colores
    val negrita = tipos.diminuto.copy(fontWeight = FontWeight.Bold)
    Column(Modifier.fillMaxWidth().background(c.superficie)) {
        HorizontalDivider(color = c.borde, thickness = 1.dp)
        BoxWithConstraints(Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            val medidor = rememberTextMeasurer()
            val densidad = LocalDensity.current
            val disponible = constraints.maxWidth
            // Se mide en negrita, la de la sección actual, para que nada se mueva al cambiar de sección.
            val (escala, pesos) = remember(negrita, disponible, densidad) {
                val pildora = with(densidad) { 60.dp.toPx() }
                val aire = with(densidad) { Medidas.e2.toPx() }
                fun anchos(f: Float) = SECCIONES.map { s ->
                    maxOf(medidor.measure(s.texto, negrita.copy(fontSize = negrita.fontSize * f), maxLines = 1, softWrap = false).size.width.toFloat(), pildora) + aire
                }
                val completos = anchos(1f)
                if (completos.max() * SECCIONES.size <= disponible) return@remember 1f to SECCIONES.map { 1f }
                var f = 1f
                while (f > .5f && anchos(f).sum() > disponible) f -= .05f
                f to anchos(f)
            }
            Row(Modifier.fillMaxWidth()) {
                for ((i, s) in SECCIONES.withIndex()) {
                    val activa = s.ruta == rutaActual
                    Column(
                        Modifier.weight(pesos[i]).objetivoGuia("nav." + s.ruta).defaultMinSize(minHeight = 68.dp)
                            .clickable(role = Role.Tab) { if (!activa) alIr(s) }
                            .semantics { contentDescription = s.texto + if (activa) ", sección actual" else "" }
                            .padding(top = Medidas.e2, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(999.dp)).background(if (activa) c.seleccion else Color.Transparent), contentAlignment = Alignment.Center) {
                            if (activa) IconoRelleno(s.icono, 24.dp, c.navActivo, c.seleccion) else Icono(s.icono, 24.dp, c.tintaSuave)
                        }
                        Spacer(Modifier.height(Medidas.e1))
                        val estilo = if (activa) negrita else tipos.diminuto
                        Text(s.texto, style = estilo.copy(fontSize = estilo.fontSize * escala), color = if (activa) c.titulo else c.tintaSuave, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** ¿La pantalla es ancha (tablet o computadora)? Para poner dos columnas. */
@Composable
fun esAncha(): Boolean = LocalConfiguration.current.screenWidthDp >= 620
