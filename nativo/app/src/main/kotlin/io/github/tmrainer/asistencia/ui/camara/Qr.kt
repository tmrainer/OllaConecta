// Mostrar y leer los QR para pasar datos de un teléfono a otro (el formato está en
// nucleo/qr). Varias partes van en UN solo código que cambia cada 0,8 s; quien recibe
// las junta en cualquier orden. Nada sale del teléfono: el QR se ve en pantalla y nada más.
package io.github.tmrainer.asistencia.ui.camara

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import io.github.tmrainer.asistencia.nucleo.qr.MatrizQr
import io.github.tmrainer.asistencia.nucleo.qr.Paquete
import io.github.tmrainer.asistencia.nucleo.qr.PartesRecibidas
import io.github.tmrainer.asistencia.nucleo.qr.juntarPartesQr
import io.github.tmrainer.asistencia.nucleo.qr.matrizQr
import androidx.compose.runtime.CompositionLocalProvider
import io.github.tmrainer.asistencia.ui.tema.COLORES_OSCURO
import io.github.tmrainer.asistencia.ui.tema.LocalColores
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Enlace
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.vibrar
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.delay

/** Cada cuánto cambia el código animado. Un teléfono de gama de entrada lee un cuadro en ~0,4 s. */
private const val CADA_MS = 800L

/** El QR de un envío, sobre toda la pantalla. [detalle] dice qué lleva y dónde recibirlo. */
@Composable
fun MostrarQr(partes: List<String>, titulo: String, detalle: String, alCerrar: () -> Unit) {
    val c = colores
    // Cada cuadro se arma una vez y después solo se alternan.
    val matrices = remember(partes) { partes.map { matrizQr(it) } }
    val varias = partes.size > 1
    var i by remember { mutableIntStateOf(0) }
    var deAUno by remember { mutableStateOf(false) }
    PantallaEncendidaYBrillante()
    LaunchedEffect(varias, deAUno) {
        if (!varias || deAUno) return@LaunchedEffect
        while (true) { delay(CADA_MS); i = (i + 1) % partes.size }
    }
    Column(
        Modifier.fillMaxSize().background(c.fondo).systemBarsPadding().verticalScroll(rememberScrollState()).padding(Medidas.e4),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Medidas.e3)
    ) {
        Text(titulo, style = tipos.titulo, color = c.titulo, modifier = Modifier.fillMaxWidth())
        Text(
            if (deAUno) "$detalle Pasa al siguiente cuando el otro teléfono vibre."
            else "$detalle${if (varias) " El código cambia solo: espera a que el otro teléfono vibre." else ""}",
            style = tipos.texto, color = c.tinta, modifier = Modifier.fillMaxWidth()
        )
        // Siempre blanco con negro, también en modo oscuro: así lo lee la cámara.
        DibujoQr(matrices[i], Modifier.widthIn(max = 420.dp()).fillMaxWidth())
        if (deAUno) Text("Código ${i + 1} de ${partes.size}", style = tipos.textoFuerte, color = c.titulo, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (varias && !deAUno) Enlace("¿No lo lee? Ver de a uno", { deAUno = true })
        Acciones {
            if (deAUno) {
                Boton("Anterior", { i = maxOf(0, i - 1) }, habilitado = i > 0)
                Boton("Siguiente", { i = minOf(partes.size - 1, i + 1) }, tipo = TipoBoton.PRINCIPAL, habilitado = i < partes.size - 1)
            }
            Boton("Listo", alCerrar)
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

/** Un QR con su margen blanco de 4 módulos (el que pide la norma). */
@Composable
fun DibujoQr(m: MatrizQr, modifier: Modifier = Modifier) {
    Canvas(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(Medidas.rS)).background(Color.White)
            .semantics { contentDescription = "Código QR con los datos" }
    ) {
        val n = m.tamano
        val borde = 4
        val tam = size.width / (n + 2 * borde)
        for (f in 0 until n) for (col in 0 until n) {
            if (m.oscuro(f, col)) drawRect(Color.Black, Offset((col + borde) * tam, (f + borde) * tam), Size(tam + 0.5f, tam + 0.5f))
        }
    }
}

/** Que la pantalla no se apague y vaya con todo el brillo mientras se muestra el QR; al cerrar, vuelve a lo de antes. */
@Composable
private fun PantallaEncendidaYBrillante() {
    val vista = LocalView.current
    val actividad = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        vista.keepScreenOn = true
        val ventana = actividad?.window
        val antes = ventana?.attributes?.screenBrightness
        ventana?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = 1f } }
        onDispose {
            vista.keepScreenOn = false
            if (ventana != null && antes != null) ventana.attributes = ventana.attributes.also { it.screenBrightness = antes }
        }
    }
}

/**
 * La cámara que lee los QR de un envío hasta tener todas sus partes; entonces
 * entrega el paquete ya revisado y se cierra.
 */
@Composable
fun LeerQr(alRecibir: (Paquete) -> Unit, alCerrar: () -> Unit) {
    val context = LocalContext.current
    val principal = remember { ContextCompat.getMainExecutor(context) }
    var permiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var negado by remember { mutableStateOf(false) }
    val pedir = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { dado -> permiso = dado; negado = !dado }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(android.Manifest.permission.CAMERA) }

    var estadoTexto by remember { mutableStateOf("Buscando el código…") }
    var abriendo by remember { mutableStateOf(true) }
    var hayLinterna by remember { mutableStateOf(false) }
    var linterna by remember { mutableStateOf(false) }
    var falla by remember { mutableStateOf<String?>(null) }
    var destellos by remember { mutableIntStateOf(0) }
    val lector = remember { LectorQr() }
    val busqueda = remember { object { @Volatile var cerrado = false; var partes = PartesRecibidas(); var entregar: (() -> Unit)? = null } }
    DisposableEffect(Unit) { onDispose { busqueda.cerrado = true; lector.close() } }

    fun cerrar() { if (!busqueda.cerrado) { busqueda.cerrado = true }; alCerrar() }

    fun analizar(imagen: android.graphics.Bitmap) {
        if (busqueda.cerrado) return
        val textos = try { lector.leer(imagen) } catch (e: LectorNoDisponible) {
            principal.execute { falla = "El lector aún no está listo. Prueba en unos minutos, con internet." }
            busqueda.cerrado = true
            return
        } catch (e: Exception) { return }
        principal.execute { abriendo = false }
        var r = busqueda.partes
        for (t in textos) {
            val antes = r
            r = r.sumar(t)
            // Un toque corto por parte nueva y uno largo al final.
            if (r.partes.size > antes.partes.size || r.id != antes.id) principal.execute { vibrar(context, if (r.completas) 120 else 25) }
        }
        busqueda.partes = r
        if (r.completas) {
            val paquete = juntarPartesQr(r.partes.values)
            if (paquete == null) {
                busqueda.partes = PartesRecibidas()
                principal.execute { estadoTexto = "No se leyó bien. Vuelve a apuntar al código." }
                return
            }
            busqueda.cerrado = true
            principal.execute {
                destellos += 1
                busqueda.entregar = { alCerrar(); alRecibir(paquete) }
            }
        } else if (r.total > 1) {
            principal.execute { estadoTexto = "Leyendo… sigue apuntando hasta que vibre fuerte." }
        }
    }

    // Fondo siempre negro: los botones con los colores del modo oscuro, también en el claro.
    CompositionLocalProvider(LocalColores provides COLORES_OSCURO) {
    val c = colores
    Column(
        Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(Medidas.e4),
        verticalArrangement = Arrangement.spacedBy(Medidas.e3)
    ) {
        Text("Recibir de otro teléfono", style = tipos.titulo, color = Color.White)
        Text("Apunta a la pantalla del otro teléfono. Antes de guardar verás qué cambia.", style = tipos.texto, color = Color.White.copy(alpha = .85f))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(Medidas.rM)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
            val mensajeFalla = falla
            when {
                negado -> Text("La app necesita permiso para usar la cámara.", style = tipos.texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(Medidas.e4))
                mensajeFalla != null -> Text(mensajeFalla, style = tipos.texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(Medidas.e4))
                permiso -> {
                    VisorCamara(
                        recorte = Recorte.CUADRADO, linterna = linterna,
                        alTenerLinterna = { hayLinterna = it },
                        alFallar = { falla = "$it Revisa que la app tenga permiso para usarla." },
                        analizar = ::analizar, modifier = Modifier.fillMaxSize()
                    )
                    Recuadro(Recorte.CUADRADO, destellos) { busqueda.entregar?.invoke() }
                    if (abriendo) CircularProgressIndicator(color = Color.White)
                }
            }
        }
        if (falla == null && !negado) Text(estadoTexto, style = tipos.textoFuerte, color = Color.White, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Acciones {
            if (hayLinterna) Boton(if (linterna) "Apagar la linterna" else "Prender la linterna", { linterna = !linterna }, activo = linterna)
            Boton("Cancelar", ::cerrar)
        }
    }
    @Suppress("UNUSED_VARIABLE") val sinUso = c
    }
}
