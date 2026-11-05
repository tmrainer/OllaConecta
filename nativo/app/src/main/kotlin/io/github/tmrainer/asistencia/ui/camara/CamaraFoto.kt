// Tomar la foto del DNI de una persona (decisión del 05-11-2026): el visor
// sobre toda la pantalla, un botón grande para tomarla, y después verla y
// decidir si se usa o se repite. No se lee nada de la foto: solo se guarda.
package io.github.tmrainer.asistencia.ui.camara

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.tipos

/** La imagen capturada, derecha (CameraX la entrega girada según el teléfono). */
private fun ImageProxy.aBitmap(): Bitmap {
    val b = toBitmap()
    val giro = imageInfo.rotationDegrees
    if (giro == 0) return b
    val m = Matrix().apply { postRotate(giro.toFloat()) }
    return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true).also { if (it !== b) b.recycle() }
}

/**
 * La cámara para la foto del DNI. [alTomar] recibe la foto que la persona
 * aceptó; después se cierra. [alCerrar] sin foto: nada cambia.
 */
@Composable
fun CamaraFoto(alCerrar: () -> Unit, alTomar: (Bitmap) -> Unit, titulo: String = "Foto del DNI", retrato: Boolean = false) {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val principal = remember { ContextCompat.getMainExecutor(context) }
    var permiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var negado by remember { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dado -> permiso = dado; negado = !dado }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.CAMERA) }

    var captura by remember { mutableStateOf<ImageCapture?>(null) }
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var tomando by remember { mutableStateOf(false) }
    var falla by remember { mutableStateOf<String?>(null) }
    BackHandler { if (foto != null) foto = null else alCerrar() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val tomada = foto
        if (tomada != null) {
            // Verla antes de usarla: que se lea el número y no esté movida.
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(Medidas.e4)) {
                Text("¿Se lee bien?", style = tipos.seccion, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = Medidas.e3))
                Image(tomada.asImageBitmap(), contentDescription = "La foto tomada del DNI",
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(Medidas.rM)))
                Row(Modifier.fillMaxWidth().padding(top = Medidas.e4), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                    Boton("Repetir", { foto = null }, Modifier.weight(1f), icono = "camara")
                    Boton("Usar esta foto", { alTomar(tomada); alCerrar() }, Modifier.weight(1f), tipo = TipoBoton.PRINCIPAL, icono = "ok")
                }
            }
            return@Box
        }
        if (permiso) {
            val vista = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
            DisposableEffect(ciclo) {
                val futuro = ProcessCameraProvider.getInstance(context)
                var proveedor: ProcessCameraProvider? = null
                var cerrado = false
                futuro.addListener({
                    if (cerrado) return@addListener
                    val p = try { futuro.get() } catch (e: Exception) { falla = "No se pudo abrir la cámara."; return@addListener }
                    proveedor = p
                    val previa = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
                    val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    try {
                        p.unbindAll()
                        p.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, previa, ic)
                        captura = ic
                    } catch (e: Exception) { falla = "No se pudo abrir la cámara." }
                }, principal)
                onDispose { cerrado = true; proveedor?.unbindAll() }
            }
            AndroidView({ vista }, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(Medidas.e4), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(Color.Black.copy(alpha = 0.55f)).padding(Medidas.e4, Medidas.e3)) {
                Text(titulo, style = tipos.seccion, color = Color.White)
                Text(
                    when {
                        negado -> "Sin permiso de cámara no se puede tomar la foto. Dáselo en los ajustes del teléfono."
                        falla != null -> falla!!
                        retrato -> "La persona de frente, con luz, del pecho para arriba."
                        else -> "Pon el DNI de frente, con luz, que entre entero y se lea el número."
                    },
                    style = tipos.texto, color = Color.White
                )
            }
            // El marco donde va el DNI (proporción de la tarjeta), para que salga derecho.
            // El marco: la tarjeta del DNI apaisada, o un retrato 3 × 4.
            Box(Modifier.fillMaxWidth(if (retrato) 0.6f else 1f).aspectRatio(if (retrato) 0.75f else 1.58f).align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(Medidas.rM)).padding(2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Medidas.rM)).background(Color.White.copy(alpha = 0.08f)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                Boton("Cerrar", alCerrar, Modifier.weight(1f))
                Boton(if (tomando) "Tomando…" else "Tomar la foto", {
                    val ic = captura ?: return@Boton
                    if (tomando) return@Boton
                    tomando = true
                    ic.takePicture(principal, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imagen: ImageProxy) {
                            try { foto = imagen.aBitmap() } catch (e: Exception) { falla = "No se pudo tomar la foto. Inténtalo de nuevo." } finally { imagen.close(); tomando = false }
                        }
                        override fun onError(e: ImageCaptureException) { falla = "No se pudo tomar la foto. Inténtalo de nuevo."; tomando = false }
                    })
                }, Modifier.weight(2f), tipo = TipoBoton.PRINCIPAL, icono = "camara", habilitado = permiso && captura != null && !tomando)
            }
        }
    }
}
