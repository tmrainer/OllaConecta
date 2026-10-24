// Visor.kt — la cámara de atrás en vivo (camara-trasera.js y el visor de
// camara-dni.js / camara-qr.js): CameraX muestra la imagen y, en otro hilo,
// entrega cada fotograma recortado a lo que hay dentro del recuadro. Lo que
// ve la cámara solo pasa por el visor y por ese recorte en memoria: ningún
// fotograma se guarda ni sale del teléfono.
//
// Los lectores (ML Kit de los servicios de Google Play) están abajo: el texto
// para la MRZ y el anverso del DNI, los códigos para los QR.
package io.github.tmrainer.asistencia.ui.camara

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.tmrainer.asistencia.nucleo.dni.FragmentoOcr
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Qué parte de la imagen se lee: lo que el recuadro del visor encierra. */
enum class Recorte {
    /** La tarjeta del DNI entera (el anverso). */
    TARJETA,
    /** Solo el 45 % de abajo de la tarjeta, el de las tres líneas con «<»: la mitad de tiempo por lectura. */
    FRANJA_MRZ,
    /** Un cuadrado al centro, para un QR. */
    CUADRADO
}

/** Lo que el recuadro ocupa del visor, para dibujarlo encima: fracción del ancho y proporción. */
fun proporcionRecuadro(recorte: Recorte): Pair<Float, Float> =
    if (recorte == Recorte.CUADRADO) 0.8f to 1f else 0.86f to (54f / 85.6f)

/**
 * El visor de la cámara de atrás. [analizar] recibe el recorte de cada
 * fotograma en un hilo aparte (puede tardar: mientras, los fotogramas nuevos
 * se descartan). [alFallar] dice por qué no se pudo abrir.
 */
@Composable
fun VisorCamara(
    recorte: Recorte,
    linterna: Boolean,
    alTenerLinterna: (Boolean) -> Unit,
    alFallar: (String) -> Unit,
    analizar: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val tamVisor = remember { AtomicReference(IntSize.Zero) }
    val recorteActual = remember { AtomicReference(recorte) }
    recorteActual.set(recorte)
    val analizarActual by rememberUpdatedState(analizar)
    val vista = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camara by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(ciclo) {
        val hilo = Executors.newSingleThreadExecutor()
        val futuro = ProcessCameraProvider.getInstance(context)
        var proveedor: ProcessCameraProvider? = null
        futuro.addListener({
            val p = try { futuro.get() } catch (e: Exception) { alFallar("No se pudo abrir la cámara."); return@addListener }
            proveedor = p
            val previa = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            // 1920 × 1080 como en la web: con menos, las letras de la MRZ salen chicas.
            val analisis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    ).build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analisis.setAnalyzer(hilo) { cuadro ->
                val imagen = try { recortar(cuadro, tamVisor.get(), recorteActual.get()) } catch (e: Exception) { null } finally { cuadro.close() }
                if (imagen != null) {
                    try { analizarActual(imagen) } finally { imagen.recycle() }
                }
            }
            try {
                p.unbindAll()
                val c = p.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, previa, analisis)
                camara = c
                alTenerLinterna(c.cameraInfo.hasFlashUnit())
            } catch (e: Exception) {
                alFallar("No se pudo abrir la cámara.")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            proveedor?.unbindAll()
            hilo.shutdown()
        }
    }
    LaunchedEffect(linterna, camara) { camara?.cameraControl?.enableTorch(linterna) }
    AndroidView({ vista }, modifier.onSizeChanged { tamVisor.set(it) })
}

/**
 * Lo que cae dentro del recuadro, derecho y ampliado: más píxeles por letra,
 * menos confusiones (B por S, O por 0). El visor recorta la imagen para
 * llenarse (FILL_CENTER), así que se calcula qué parte del fotograma cae dentro.
 */
private fun recortar(cuadro: ImageProxy, visor: IntSize, recorte: Recorte): Bitmap? {
    if (visor.width == 0 || visor.height == 0) return null
    val giro = cuadro.imageInfo.rotationDegrees
    val fuente = cuadro.toBitmap()
    val sw = fuente.width
    val sh = fuente.height
    // Medidas del fotograma ya derecho.
    val fw = if (giro % 180 == 0) sw else sh
    val fh = if (giro % 180 == 0) sh else sw
    val escala = max(visor.width.toFloat() / fw, visor.height.toFloat() / fh)
    val (fraccion, proporcion) = proporcionRecuadro(recorte)
    // Un 10 % más que el recuadro dibujado: un DNI un poco corrido igual entra.
    val ancho = min(fw.toFloat(), fraccion * visor.width * 1.1f / escala)
    val alto = min(fh.toFloat(), ancho * proporcion)
    val desde = if (recorte == Recorte.FRANJA_MRZ) 0.55f else 0f
    val x = (fw - ancho) / 2
    val y = (fh - alto) / 2 + alto * desde
    val h = alto * (1 - desde)
    val enDerecho = Rect(x.roundToInt(), y.roundToInt(), (x + ancho).roundToInt(), (y + h).roundToInt())
    val enFuente = aLaFuente(enDerecho, giro, sw, sh)
    val destino = when (recorte) { Recorte.FRANJA_MRZ -> 1200f; Recorte.TARJETA -> 1400f; Recorte.CUADRADO -> ancho }
    val m = Matrix().apply { postRotate(giro.toFloat()); val k = destino / ancho; postScale(k, k) }
    val salida = Bitmap.createBitmap(fuente, enFuente.left, enFuente.top, enFuente.width(), enFuente.height(), m, true)
    if (salida !== fuente) fuente.recycle()
    return salida
}

/** Un rectángulo del fotograma derecho, en las coordenadas del fotograma como lo da el sensor. */
private fun aLaFuente(r: Rect, giro: Int, sw: Int, sh: Int): Rect {
    val salida = when (giro) {
        90 -> Rect(r.top, sh - r.right, r.bottom, sh - r.left)
        180 -> Rect(sw - r.right, sh - r.bottom, sw - r.left, sh - r.top)
        270 -> Rect(sw - r.bottom, r.left, sw - r.top, r.right)
        else -> Rect(r)
    }
    salida.intersect(0, 0, sw, sh)
    return salida
}

// ---------------------------------------------------------------- lectores

/** El lector no está (sin servicios de Google Play) o todavía se instala. */
class LectorNoDisponible : Exception()

private fun <T> esperar(tarea: com.google.android.gms.tasks.Task<T>): T = try {
    Tasks.await(tarea)
} catch (e: ExecutionException) {
    val causa = e.cause
    if (causa is MlKitException && causa.errorCode == MlKitException.UNAVAILABLE) throw LectorNoDisponible()
    throw e
}

/** Texto de un fotograma, una línea de ML Kit por fragmento (como LectorTextoPlugin.java del 1.7). */
class LectorTexto : AutoCloseable {
    private val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Bloquea hasta leer: llamar desde el hilo del visor. */
    fun leer(imagen: Bitmap): List<FragmentoOcr> =
        esperar(reconocedor.process(InputImage.fromBitmap(imagen, 0))).textBlocks.flatMap { it.lines }.mapNotNull { linea ->
            val caja = linea.boundingBox ?: return@mapNotNull null
            val p = linea.cornerPoints
            val angulo = if (p != null && p.size >= 2) Math.toDegrees(atan2((p[1].y - p[0].y).toDouble(), (p[1].x - p[0].x).toDouble())) else 0.0
            FragmentoOcr(linea.text, caja.top, caja.bottom, caja.left, caja.right, angulo)
        }

    override fun close() = reconocedor.close()
}

/** Los QR de un fotograma, su texto tal cual. */
class LectorQr : AutoCloseable {
    private val lector = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())

    /** Bloquea hasta leer: llamar desde el hilo del visor. */
    fun leer(imagen: Bitmap): List<String> =
        esperar(lector.process(InputImage.fromBitmap(imagen, 0))).mapNotNull { it.rawValue }

    override fun close() = lector.close()
}
