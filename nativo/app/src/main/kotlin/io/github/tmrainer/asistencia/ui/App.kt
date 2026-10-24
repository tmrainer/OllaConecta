// App.kt — la cáscara: tema, navegación, cabecera con el menú ⋮, el menú de
// abajo (ícono y palabra, la sección actual con una píldora) y el aviso de
// abajo con «Deshacer». Las pantallas van en ui/pantallas.
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.tmrainer.asistencia.Preferencias
import io.github.tmrainer.asistencia.nucleo.estado.Estado
import io.github.tmrainer.asistencia.ui.componentes.AvisoAbajo
import io.github.tmrainer.asistencia.ui.componentes.Avisador
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
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
    val abrirBienvenida: () -> Unit
) {
    fun ir(ruta: Ruta, argumento: String? = null) {
        val destino = if (argumento != null) "${ruta.ruta}?a=$argumento" else ruta.ruta
        nav.navigate(destino) {
            if (ruta in SECCIONES) { popUpTo(Ruta.INICIO.ruta) { saveState = false }; launchSingleTop = true }
        }
    }
    fun volver() { if (!nav.popBackStack()) ir(Ruta.INICIO) }
}

@Composable
fun App(estado: Estado, preferencias: Preferencias, avisoInicial: String? = null) {
    val tema by preferencias.tema.collectAsStateWithLifecycle()
    val letra by preferencias.letra.collectAsStateWithLifecycle()
    TemaOlla(tema, letra) {
        val nav = rememberNavController()
        val avisos = remember { Avisador() }
        var bienvenida by remember { mutableStateOf(false) }
        val ctx = remember(nav) { Contexto(estado, preferencias, nav, avisos) { bienvenida = true } }
        val entrada by nav.currentBackStackEntryAsState()
        val rutaActual = entrada?.destination?.route?.substringBefore("?") ?: Ruta.INICIO.ruta
        val c = colores
        LaunchedEffect(avisoInicial) { avisoInicial?.let { avisos.mostrar(it) } }

        CompositionLocalProvider(LocalAvisos provides avisos) {
            Box(Modifier.fillMaxSize().background(c.fondo)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        NavHost(nav, startDestination = Ruta.INICIO.ruta) {
                            composable(Ruta.INICIO.ruta) { PantallaInicio(ctx) }
                            composable(Ruta.ASISTENCIA.ruta) { PantallaAsistencia(ctx) }
                            composable(Ruta.PADRON.ruta) { PantallaPadron(ctx) }
                            composable(Ruta.INSCRIBIR.ruta) { PantallaInscribir(ctx) }
                            composable("${Ruta.B1.ruta}?a={a}") { PantallaB1(ctx, it.arguments?.getString("a")) }
                            composable(Ruta.B1.ruta) { PantallaB1(ctx, null) }
                            composable(Ruta.ENTREGAS.ruta) { PantallaEntregas(ctx) }
                            composable(Ruta.DATOS.ruta) { PantallaDatos(ctx) }
                            composable(Ruta.AJUSTES.ruta) { PantallaAjustes(ctx) }
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

/** Cabecera verde: flecha para volver (salvo en el Inicio), título, subtítulo y el menú ⋮. */
@Composable
fun Cabecera(ctx: Contexto, titulo: String, subtitulo: String? = null, conVolver: Boolean = true, alVolver: () -> Unit = { ctx.volver() }) {
    val c = colores
    var menu by remember { mutableStateOf(false) }
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
            Box {
                Box(Modifier.size(48.dp).clip(CircleShape).background(if (menu) Color.White.copy(alpha = .14f) else Color.Transparent).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = "Más opciones" }, contentAlignment = Alignment.Center) {
                    Icono("puntos", 26.dp, Color.White)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.superficie, shape = RoundedCornerShape(Medidas.rM)) {
                    for ((icono, texto, accion) in listOf(
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
