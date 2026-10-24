// App.kt — la cáscara: tema, navegación, cabecera con el menú ⋮, el menú de
// abajo (ícono y palabra, la sección actual con una píldora) y el aviso de
// abajo con «Deshacer». Las pantallas van en ui/pantallas. Cada pantalla
// anota lo que se lee en voz alta (Lectura); el menú ⋮ o el botón 🔊 lo leen.
package io.github.tmrainer.asistencia.ui

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import io.github.tmrainer.asistencia.ui.componentes.AvisoAbajo
import io.github.tmrainer.asistencia.ui.componentes.Avisador
import io.github.tmrainer.asistencia.ui.componentes.Icono
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
import io.github.tmrainer.asistencia.ui.pantallas.PantallaInscribir
import io.github.tmrainer.asistencia.ui.pantallas.PantallaPadron
import io.github.tmrainer.asistencia.ui.pantallas.Bienvenida
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
    val abrirBienvenida: () -> Unit
) {
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
        var bienvenida by remember { mutableStateOf(false) }
        val ctx = remember(nav) { Contexto(estado, preferencias, nav, avisos, voz) { bienvenida = true } }
        val entrada by nav.currentBackStackEntryAsState()
        val rutaActual = entrada?.destination?.route?.substringBefore("?") ?: Ruta.INICIO.ruta
        val c = colores
        LaunchedEffect(avisoInicial) { avisoInicial?.let { avisos.mostrar(it) } }
        // Tocar un aviso del teléfono abre su pantalla (MainActivity deja la ruta en `abrir`).
        if (abrir != null) LaunchedEffect(abrir) {
            abrir.collect { destino ->
                Ruta.values().firstOrNull { it.ruta == destino }?.let { ctx.ir(it) }
                if (destino != null) abrir.value = null
            }
        }

        CompositionLocalProvider(LocalAvisos provides avisos) {
            Box(Modifier.fillMaxSize().background(c.fondo)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        NavHost(nav, startDestination = Ruta.INICIO.ruta) {
                            pantalla(ctx, Ruta.INICIO.ruta) { PantallaInicio(ctx) }
                            pantalla(ctx, Ruta.ASISTENCIA.ruta) { PantallaAsistencia(ctx) }
                            pantalla(ctx, Ruta.PADRON.ruta) { PantallaPadron(ctx) }
                            pantalla(ctx, Ruta.INSCRIBIR.ruta) { PantallaInscribir(ctx) }
                            pantalla(ctx, "${Ruta.B1.ruta}?a={a}") { PantallaB1(ctx, it.arguments?.getString("a")) }
                            pantalla(ctx, Ruta.B1.ruta) { PantallaB1(ctx, null) }
                            pantalla(ctx, Ruta.ENTREGAS.ruta) { PantallaEntregas(ctx) }
                            pantalla(ctx, Ruta.DATOS.ruta) { PantallaDatos(ctx) }
                            pantalla(ctx, Ruta.AJUSTES.ruta) { PantallaAjustes(ctx) }
                        }
                    }
                    // Inscribir lleva su propio botón abajo: el menú no se pone en medio.
                    if (rutaActual != Ruta.INSCRIBIR.ruta) BarraAbajo(rutaActual) { ctx.ir(it) }
                }
                AvisoAbajo(avisos, Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp + Medidas.e3).windowInsetsPadding(WindowInsets.navigationBars))
                if (bienvenida) Bienvenida(ctx) { bienvenida = false }
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

/** Cabecera verde: flecha para volver (salvo en el Inicio), título, subtítulo y el menú ⋮. */
@Composable
fun Cabecera(ctx: Contexto, titulo: String, subtitulo: String? = null, conVolver: Boolean = true, alVolver: () -> Unit = { ctx.volver() }) {
    val c = colores
    var menu by remember { mutableStateOf(false) }
    val conVoz by ctx.preferencias.vozBoton.collectAsStateWithLifecycle()
    TituloParaLeer(titulo)
    Column(Modifier.fillMaxWidth().background(c.marca).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp).padding(start = if (conVolver) Medidas.e1 else Medidas.e4, end = 6.dp, top = Medidas.e2, bottom = Medidas.e2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (conVolver) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = alVolver).semantics { contentDescription = "Volver" }, contentAlignment = Alignment.Center) {
                    Icono("atras", 26.dp, Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = tipos.titulo, color = Color.White)
                if (subtitulo != null) Text(subtitulo, style = tipos.chico, color = c.marcaSuave)
            }
            // 🔊 si se pidió en Ajustes: leer la pantalla con un toque.
            if (conVoz) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { ctx.leerPantalla() }.semantics { contentDescription = "Leer esta pantalla en voz alta" }, contentAlignment = Alignment.Center) {
                    Icono("voz", 26.dp, Color.White)
                }
            }
            Box {
                Box(Modifier.size(48.dp).clip(CircleShape).background(if (menu) Color.White.copy(alpha = .14f) else Color.Transparent).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = "Más opciones" }, contentAlignment = Alignment.Center) {
                    Icono("puntos", 26.dp, Color.White)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.superficie, shape = RoundedCornerShape(Medidas.rM)) {
                    for ((icono, texto, accion) in listOf(
                        Triple("voz", "Leer esta pantalla en voz alta") { ctx.leerPantalla() },
                        Triple("ayuda", "¿Cómo se usa?") { ctx.abrirBienvenida() },
                        Triple("escudo", "Tus datos") { ctx.ir(Ruta.DATOS) },
                        Triple("ajustes", "Ajustes") { ctx.ir(Ruta.AJUSTES) }
                    )) {
                        DropdownMenuItem(
                            text = { Text(texto, style = tipos.texto, color = c.tinta) },
                            leadingIcon = { Icono(icono, 22.dp, c.acentoTexto) },
                            onClick = { menu = false; accion() }
                        )
                    }
                }
            }
        }
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
                    Modifier.weight(1f).defaultMinSize(minHeight = 68.dp)
                        .clickable(role = Role.Tab) { if (!activa) alIr(s) }
                        .semantics { contentDescription = s.texto + if (activa) ", sección actual" else "" }
                        .padding(top = Medidas.e2, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(999.dp)).background(if (activa) c.seleccion else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icono(s.icono, 24.dp, if (activa) c.navActivo else c.tintaSuave)
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
