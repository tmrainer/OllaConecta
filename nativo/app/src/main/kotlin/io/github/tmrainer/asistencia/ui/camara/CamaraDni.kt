// CamaraDni.kt — pasar lista leyendo el DNI con la cámara (camara-dni.js, modo
// asistencia), sobre toda la pantalla: se abre con ctx.abrirCapa { CamaraDni(…) }.
// Solo la zona de lectura mecánica (MRZ) del reverso, con el reconocimiento de
// texto de ML Kit: el número del DNI ahí lleva dígito de control. El PDF417 no
// se usa (sin verificar). Es continuo: cada DNI leído (dos
// lecturas iguales) se entrega y se sigue con el siguiente; el mismo DNI frente
// a la cámara no se repite. La olla no inscribe: esto solo marca a
// quien ya está en el padrón.
//
// La lectura no se guarda ni se registra: se entrega y se descarta.
package io.github.tmrainer.asistencia.ui.camara

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tmrainer.asistencia.nucleo.dni.buscarMrzTd1
import io.github.tmrainer.asistencia.nucleo.dni.unirFilasOcr
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.vibrar
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.delay

private const val PISTA_ASISTENCIA = "Muestra el lado del DNI con tres líneas de «<». Puedes pasar uno tras otro."

/**
 * La cámara para pasar lista: [alLeerDni] recibe cada número de DNI leído y
 * devuelve qué decir en el visor («✓ Marcaste a…»).
 */
@Composable
fun CamaraDni(alCerrar: () -> Unit, alLeerDni: (String) -> String) {
    val context = LocalContext.current
    val principal = remember { ContextCompat.getMainExecutor(context) }
    var permiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var negado by remember { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dado -> permiso = dado; negado = !dado }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.CAMERA) }

    var estadoTexto by remember { mutableStateOf("Abriendo la cámara…") }
    var abriendo by remember { mutableStateOf(true) }
    var hayLinterna by remember { mutableStateOf(false) }
    var linterna by remember { mutableStateOf(false) }
    var falla by remember { mutableStateOf<String?>(null) }
    var destellos by remember { mutableIntStateOf(0) }
    var cerrado by remember { mutableStateOf(false) }

    // El estado de la búsqueda vive en el hilo del visor; lo que se ve, en el principal.
    val busqueda = remember { Busqueda() }
    val lector = remember { LectorTexto() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { busqueda.cerrado = true; lector.close() } }

    fun cerrar() { if (!cerrado) { cerrado = true; busqueda.cerrado = true; alCerrar() } }

    fun analizar(imagen: android.graphics.Bitmap) {
        val b = busqueda
        if (b.cerrado) return
        val fragmentos = try { lector.leer(imagen) } catch (e: LectorNoDisponible) {
            principal.execute { falla = "El lector del DNI todavía no está listo en este teléfono: lo instalan los servicios de Google Play. Prueba en unos minutos, con internet, o toca a la persona en la lista." }
            b.cerrado = true
            return
        } catch (e: Exception) { return }    // un fotograma que no se pudo leer: se prueba con el siguiente
        if (b.cerrado) return
        principal.execute { if (abriendo) { abriendo = false; estadoTexto = "Buscando…" } }
        val filas = unirFilasOcr(fragmentos)
        val mrz = buscarMrzTd1(filas)
        val clave = mrz?.lineas?.joinToString("") ?: ""
        if (mrz == null || clave != b.anterior) { b.anterior = clave; return }
        b.anterior = ""
        // El mismo DNI sigue frente a la cámara: no se vuelve a marcar.
        val dni = mrz.campos.numeroDocumento
        val ahora = System.currentTimeMillis()
        if (dni != b.ultimoDni || ahora - b.ultimoEn > 5000) {
            principal.execute {
                vibrar(context, 60); destellos += 1
                estadoTexto = alLeerDni(dni)
            }
        }
        b.ultimoDni = dni
        b.ultimoEn = ahora
    }

    // Sobre toda la app (Contexto.abrirCapa), entre la barra de arriba y la de abajo.
    run {
        Column(
            Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(Medidas.e4),
            verticalArrangement = Arrangement.spacedBy(Medidas.e3)
        ) {
            Text("Pasar lista con el DNI", style = tipos.titulo, color = Color.White)
            Text(PISTA_ASISTENCIA, style = tipos.texto, color = Color.White.copy(alpha = .85f))
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(Medidas.rM)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                val mensajeFalla = falla
                when {
                    negado -> Text("La app necesita permiso para usar la cámara.", style = tipos.texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(Medidas.e4))
                    mensajeFalla != null -> Text(mensajeFalla, style = tipos.texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(Medidas.e4))
                    permiso -> {
                        VisorCamara(
                            recorte = Recorte.FRANJA_MRZ, linterna = linterna,
                            alTenerLinterna = { hayLinterna = it },
                            alFallar = { falla = "$it Revisa que la app tenga permiso para usarla." },
                            analizar = ::analizar,
                            modifier = Modifier.fillMaxSize()
                        )
                        Recuadro(Recorte.FRANJA_MRZ, destellos)
                        if (abriendo) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Abriendo la cámara…", style = tipos.chico, color = Color.White, modifier = Modifier.padding(top = Medidas.e2))
                        }
                    }
                }
            }
            if (!abriendo && falla == null && !negado) {
                Text(estadoTexto, style = tipos.textoFuerte, color = Color.White, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Acciones {
                if (negado) Boton("Dar permiso en Ajustes", {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    cerrar()
                })
                if (hayLinterna) Boton(if (linterna) "Apagar la linterna" else "Prender la linterna", { linterna = !linterna }, activo = linterna)
                Boton("Terminar", ::cerrar)
            }
        }
    }
}

/** Lo que recuerda la búsqueda entre fotogramas (solo la toca el hilo del visor, salvo `cerrado`). */
private class Busqueda {
    @Volatile var cerrado = false
    var anterior = ""
    var ultimoDni = ""
    var ultimoEn = 0L
}

/**
 * El recuadro donde va el DNI (o el QR), con la sombra alrededor. Al leer, se
 * pone verde un instante; [alTerminarDestello] corre cuando se apaga.
 */
@Composable
fun Recuadro(recorte: Recorte, destellos: Int, alTerminarDestello: () -> Unit = {}) {
    val verde = colores.ok
    val brillo = remember { Animatable(0f) }
    LaunchedEffect(destellos) {
        if (destellos == 0) return@LaunchedEffect
        brillo.snapTo(1f)
        brillo.animateTo(0f, tween(450))
        delay(30)
        alTerminarDestello()
    }
    val (fraccion, proporcion) = proporcionRecuadro(recorte)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ancho = size.width * fraccion
            val alto = ancho * proporcion
            val origen = Offset((size.width - ancho) / 2, (size.height - alto) / 2)
            // Sombra fuera del recuadro: se ve dónde poner el DNI.
            drawRect(Color.Black.copy(alpha = .45f), Offset.Zero, Size(size.width, origen.y))
            drawRect(Color.Black.copy(alpha = .45f), Offset(0f, origen.y + alto), Size(size.width, size.height - origen.y - alto))
            drawRect(Color.Black.copy(alpha = .45f), Offset(0f, origen.y), Size(origen.x, alto))
            drawRect(Color.Black.copy(alpha = .45f), Offset(origen.x + ancho, origen.y), Size(size.width - origen.x - ancho, alto))
            if (brillo.value > 0f) drawRoundRect(verde.copy(alpha = .35f * brillo.value), origen, Size(ancho, alto), CornerRadius(16.dp.toPx()))
            drawRoundRect(if (brillo.value > 0f) verde else Color.White, origen, Size(ancho, alto), CornerRadius(16.dp.toPx()), style = Stroke(3.dp.toPx()))
            if (recorte == Recorte.FRANJA_MRZ) {
                // La franja de las tres líneas: donde va el «<<<».
                val y = origen.y + alto * 0.55f
                drawLine(Color.White.copy(alpha = .5f), Offset(origen.x + 12.dp.toPx(), y), Offset(origen.x + ancho - 12.dp.toPx(), y), 1.5.dp.toPx())
            }
        }
    }
}
