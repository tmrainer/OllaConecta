// App.kt — la cáscara: tema, navegación, cabecera con sus botones con palabra
// (Ayuda, Ajustes), el menú de abajo (ícono y palabra, la sección actual con
// una píldora) y el aviso de abajo con «Deshacer». Las pantallas van en
// ui/pantallas. Cada pantalla anota lo que se lee en voz alta (Lectura);
// «Ayuda» o el botón «Escuchar» lo leen.
package io.github.tmrainer.asistencia.ui

import androidx.compose.ui.semantics.heading
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import io.github.tmrainer.asistencia.ui.componentes.IconoRelleno
import io.github.tmrainer.asistencia.ui.componentes.Lectura
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
import io.github.tmrainer.asistencia.ui.componentes.LocalLectura
import io.github.tmrainer.asistencia.ui.componentes.TituloParaLeer
import io.github.tmrainer.asistencia.ui.pantallas.PantallaAjustes
import io.github.tmrainer.asistencia.ui.pantallas.PantallaAsistencia
import io.github.tmrainer.asistencia.ui.pantallas.PantallaB1
import io.github.tmrainer.asistencia.ui.pantallas.PantallaDatos
import io.github.tmrainer.asistencia.ui.pantallas.PantallaEntregas
import io.github.tmrainer.asistencia.ui.pantallas.PantallaInicio
import io.github.tmrainer.asistencia.ui.pantallas.PantallaHojaB1
import io.github.tmrainer.asistencia.ui.pantallas.PantallaImportar
import io.github.tmrainer.asistencia.ui.pantallas.PantallaReportes
import io.github.tmrainer.asistencia.ui.pantallas.PantallaInscribir
import io.github.tmrainer.asistencia.ui.pantallas.PantallaPadron
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.TemaOlla
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

/** Las secciones del menú de abajo, en orden, como PAGINAS en ui.js. */
enum class Ruta(val ruta: String, val texto: String, val icono: String) {
    INICIO("inicio", "Inicio", "casa"),
    ASISTENCIA("asistencia", "Asistencia", "lista"),
    PADRON("padron", "Padrón", "personas"),
    INSCRIBIR("inscribir", "Inscribir", "personaMas"),
    IMPORTAR("importar", "Hoja de la Municipalidad", "archivo"),
    HOJA("hoja", "Vista previa del B-1", "boleta"),
    REPORTES("reportes", "Reportes", "archivo"),
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
                        NavHost(nav, startDestination = Ruta.INICIO.ruta) {
                            pantalla(ctx, Ruta.INICIO.ruta) { PantallaInicio(ctx) }
                            pantalla(ctx, Ruta.ASISTENCIA.ruta) { PantallaAsistencia(ctx) }
                            pantalla(ctx, Ruta.PADRON.ruta) { PantallaPadron(ctx) }
                            pantalla(ctx, Ruta.INSCRIBIR.ruta) { PantallaInscribir(ctx) }
                            pantalla(ctx, Ruta.IMPORTAR.ruta) { PantallaImportar(ctx) }
                            pantalla(ctx, Ruta.REPORTES.ruta) { PantallaReportes(ctx) }
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
                AvisoAbajo(avisos, Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp + Medidas.e3).windowInsetsPadding(WindowInsets.navigationBars))
                // Mientras falte el perfil, la app no se usa; salvo para ir a «Tus datos» a traerlos de otro teléfono.
                if (pidiendoPerfil) {
                    PrimerUso(
                        ctx, ollaPropuesta = config.ultimoCentro?.nombreCentro?.trim().orEmpty(),
                        alTraerDatos = { trayendoDatos = true; ctx.ir(Ruta.DATOS) },
                        alTerminar = { trayendoDatos = false; if (!preferencias.bienvenidaVista.value) ctx.abrirBienvenida() }
                    )
                }
                guia?.let { g -> GuiaFoco(ctx, g) { if (g === GUIA_INICIO) preferencias.marcarBienvenidaVista(); guia = null } }
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
 * y, a la derecha, botones con ícono Y palabra: nada escondido tras «⋮», que
 * quien recién empieza no toca (NN/g). «Ayuda» siempre, con la [guia] de
 * esta pantalla; «Ajustes» en el Inicio ([conAjustes]); «Escuchar» si se
 * pidió en Ajustes.
 */
@Composable
fun Cabecera(ctx: Contexto, titulo: String, subtitulo: String? = null, conVolver: Boolean = true, conAjustes: Boolean = false, guia: Guia = GUIA_INICIO, alVolver: () -> Unit = { ctx.volver() }) {
    val c = colores
    val conVoz by ctx.preferencias.vozBoton.collectAsStateWithLifecycle()
    TituloParaLeer(titulo)
    Column(Modifier.fillMaxWidth().background(c.marca).windowInsetsPadding(WindowInsets.statusBars)) {
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
            Column(Modifier.weight(1f)) {
                Text(titulo, style = tipos.titulo, color = Color.White, modifier = Modifier.semantics { heading() })
                if (subtitulo != null) Text(subtitulo, style = tipos.chico, color = c.marcaSuave)
            }
            Spacer(Modifier.width(Medidas.e3))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                if (conAjustes) BotonCabecera("ajustes", "Ajustes") { ctx.ir(Ruta.AJUSTES) }
                // «Escuchar» con un toque, si se pidió en Ajustes (también está en Ayuda).
                if (conVoz) BotonCabecera("voz", "Escuchar") { ctx.leerPantalla() }
                // La ayuda en el lugar, cuando se pide: cómo se usa ESTA pantalla (Guias.kt).
                BotonCabecera("ayuda", "Ayuda", Modifier.objetivoGuia("ayuda")) { ctx.abrirGuia(guia) }
            }
        }
    }
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

/** El menú de abajo, igual en todas las pantallas: ícono y palabra, píldora en la sección actual. */
@Composable
fun BarraAbajo(rutaActual: String, alIr: (Ruta) -> Unit) {
    val c = colores
    Column(Modifier.fillMaxWidth().background(c.superficie)) {
        HorizontalDivider(color = c.borde, thickness = 1.dp)
        Row(Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            for (s in SECCIONES) {
                val activa = s.ruta == rutaActual
                Column(
                    Modifier.weight(1f).objetivoGuia("nav." + s.ruta).defaultMinSize(minHeight = 68.dp)
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
                    Text(s.texto, style = if (activa) tipos.diminuto.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else tipos.diminuto, color = if (activa) c.titulo else c.tintaSuave, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** ¿La pantalla es ancha (tablet o computadora)? Para poner dos columnas. */
@Composable
fun esAncha(): Boolean = LocalConfiguration.current.screenWidthDp >= 620
