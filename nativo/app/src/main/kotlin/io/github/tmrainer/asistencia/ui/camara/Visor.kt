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
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
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
import io.github.tmrainer.asistencia.nucleo.dni.Caja
import io.github.tmrainer.asistencia.nucleo.dni.FragmentoOcr
import io.github.tmrainer.asistencia.nucleo.dni.Grises
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan
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
 * La cámara de atrás que sirve para leer de cerca. DEFAULT_BACK_CAMERA es la
 * primera de la lista y en algunos teléfonos esa es la gran angular (de foco
 * fijo: un DNI a 15 cm sale borroso), una macro de 2 MP o el teleobjetivo
 * (no enfoca de cerca). Se prefiere, en este orden: enfoque automático, que
 * enfoque a 15 cm, que no sea gran angular y que tenga al menos 1920 píxeles
 * de ancho. A igual puntaje queda la que el teléfono pone primero.
 */
@OptIn(ExperimentalCamera2Interop::class)
private val CAMARA_PARA_LEER: CameraSelector = CameraSelector.Builder()
    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
    .addCameraFilter { camaras -> camaras.sortedByDescending { puntajeParaLeer(Camera2CameraInfo.from(it)) }.take(1) }
    .build()

@OptIn(ExperimentalCamera2Interop::class)
private fun puntajeParaLeer(c: Camera2CameraInfo): Int {
    // Dioptrías: 0 es foco fijo; 10 enfoca a 10 cm.
    val enfoque = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
    // Una cámara lógica (varias lentes en una) elige la lente sola: a zoom 1 no es la gran angular.
    val logica = c.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
    val focal = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
    val sensor = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val angulo = if (focal != null && focal > 0f && sensor != null) Math.toDegrees(2 * atan(sensor.width / (2.0 * focal))) else null
    val ancho = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.width ?: 0
    var puntaje = 0
    if (enfoque > 0f) puntaje += 8
    if (enfoque >= 1 / 0.15f) puntaje += 4
    if (logica || (angulo != null && angulo <= 90.0)) puntaje += 2
    if (ancho >= 1920) puntaje += 1
    return puntaje
}

/**
 * Un vistazo al fotograma para la guía en vivo: los grises de alrededor del
 * recuadro de la tarjeta, dónde cae el recuadro en ellos ([guia], en
 * fracciones) y dónde caen en el visor ([enVisor], en píxeles), para dibujar
 * encima lo que se encuentre.
 */
class Vistazo(val grises: Grises, val guia: Caja, val enVisor: RectF)

/**
 * El visor de la cámara de atrás. [analizar] recibe el recorte de cada
 * fotograma en un hilo aparte (puede tardar: mientras, los fotogramas nuevos
 * se descartan); antes, si se pide, [mirar] recibe su vistazo. [alFallar]
 * dice por qué no se pudo abrir; [alTenerEnfoque], si la cámara enfoca sola.
 */
@Composable
fun VisorCamara(
    recorte: Recorte,
    linterna: Boolean,
    alTenerLinterna: (Boolean) -> Unit,
    alFallar: (String) -> Unit,
    analizar: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
    mirar: ((Vistazo) -> Unit)? = null,
    alTenerEnfoque: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val tamVisor = remember { AtomicReference(IntSize.Zero) }
    val recorteActual = remember { AtomicReference(recorte) }
    recorteActual.set(recorte)
    val analizarActual by rememberUpdatedState(analizar)
    val mirarActual by rememberUpdatedState(mirar)
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
        // Si se sale antes de que la cámara termine de abrir, no se abre: si no,
        // quedaba prendida (y atada a la Activity) aunque ya no se viera.
        var cerrado = false
        futuro.addListener({
            if (cerrado) return@addListener
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
            val lienzo = Lienzo()
            analisis.setAnalyzer(hilo) { cuadro ->
                val giro = cuadro.imageInfo.rotationDegrees
                val fuente = try { lienzo.copiar(cuadro) } catch (e: Exception) { null } finally { cuadro.close() }
                if (fuente != null) {
                    val visor = tamVisor.get()
                    mirarActual?.let { m -> try { vistazo(fuente, giro, visor)?.let(m) } catch (e: Exception) { } }
                    val imagen = try { recortar(fuente, giro, visor, recorteActual.get()) } catch (e: Exception) { null }
                    if (imagen !== fuente && !lienzo.esPropio(fuente)) fuente.recycle()
                    if (imagen != null) {
                        try { analizarActual(imagen) } finally { if (!lienzo.esPropio(imagen)) imagen.recycle() }
                    }
                }
            }
            try {
                p.unbindAll()
                val c = p.bindToLifecycle(ciclo, CAMARA_PARA_LEER, previa, analisis)
                camara = c
                alTenerLinterna(c.cameraInfo.hasFlashUnit())
                alTenerEnfoque(enfocaSola(c.cameraInfo))
            } catch (e: Exception) {
                alFallar("No se pudo abrir la cámara.")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            cerrado = true
            proveedor?.unbindAll()
            hilo.shutdown()
        }
    }
    LaunchedEffect(linterna, camara) { camara?.cameraControl?.enableTorch(linterna) }
    AndroidView({ vista }, modifier.onSizeChanged { tamVisor.set(it) })
}

/**
 * Un solo lienzo para todos los fotogramas: convertir cada uno a una imagen
 * nueva eran unos 8 MB por fotograma (unos 10 por segundo) para el recolector
 * de memoria, que en un teléfono de gama baja se nota. Solo lo usa el hilo
 * del visor; no se recicla a mano (lo libera el recolector al cerrar).
 */
private class Lienzo {
    private var bitmap: Bitmap? = null

    /** El fotograma (RGBA_8888) como imagen: en el lienzo si sus filas vienen sin relleno. */
    fun copiar(cuadro: ImageProxy): Bitmap {
        val plano = cuadro.planes[0]
        // Con relleno al final de las filas, CameraX lo convierte (en una imagen nueva).
        if (plano.pixelStride != 4 || plano.rowStride != cuadro.width * 4) return cuadro.toBitmap()
        val destino = bitmap?.takeIf { it.width == cuadro.width && it.height == cuadro.height }
            ?: Bitmap.createBitmap(cuadro.width, cuadro.height, Bitmap.Config.ARGB_8888).also { bitmap = it }
        plano.buffer.rewind()
        destino.copyPixelsFromBuffer(plano.buffer)
        return destino
    }

    fun esPropio(b: Bitmap) = b === bitmap
}

/**
 * Lo que cae dentro del recuadro, derecho y ampliado: más píxeles por letra,
 * menos confusiones (B por S, O por 0). El visor recorta la imagen para
 * llenarse (FILL_CENTER), así que se calcula qué parte del fotograma cae dentro.
 */
private fun recortar(fuente: Bitmap, giro: Int, visor: IntSize, recorte: Recorte): Bitmap? {
    if (visor.width == 0 || visor.height == 0) return null
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
    return Bitmap.createBitmap(fuente, enFuente.left, enFuente.top, enFuente.width(), enFuente.height(), m, true)
}

/** Si la cámara enfoca sola (una de foco fijo da 0 dioptrías de enfoque mínimo). */
@OptIn(ExperimentalCamera2Interop::class)
private fun enfocaSola(info: CameraInfo): Boolean =
    (Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f) > 0f

/**
 * El vistazo de la guía en vivo: alrededor del recuadro de la tarjeta (con
 * un 35 % de margen por lado, para ver los bordes aunque esté corrida), en
 * grises y achicado a 320 px de ancho. El recuadro está al centro del visor
 * y el visor al centro del fotograma (FILL_CENTER), como en recortar().
 */
private fun vistazo(fuente: Bitmap, giro: Int, visor: IntSize): Vistazo? {
    if (visor.width == 0 || visor.height == 0) return null
    val sw = fuente.width
    val sh = fuente.height
    val fw = if (giro % 180 == 0) sw else sh
    val fh = if (giro % 180 == 0) sh else sw
    val escala = max(visor.width.toFloat() / fw, visor.height.toFloat() / fh)
    val (fraccion, proporcion) = proporcionRecuadro(Recorte.TARJETA)
    // El recuadro dibujado y la zona de alrededor, en el fotograma derecho.
    val gw = fraccion * visor.width / escala
    val gh = gw * proporcion
    val gx = (fw - gw) / 2
    val gy = (fh - gh) / 2
    val zona = Rect(
        max(0f, gx - gw * 0.35f).roundToInt(), max(0f, gy - gh * 0.35f).roundToInt(),
        min(fw.toFloat(), gx + gw * 1.35f).roundToInt(), min(fh.toFloat(), gy + gh * 1.35f).roundToInt()
    )
    if (zona.width() < 32 || zona.height() < 32) return null
    val enFuente = aLaFuente(zona, giro, sw, sh)
    val k = 320f / zona.width()
    val m = Matrix().apply { postRotate(giro.toFloat()); postScale(k, k) }
    val chico = Bitmap.createBitmap(fuente, enFuente.left, enFuente.top, enFuente.width(), enFuente.height(), m, true)
    val ancho = chico.width
    val alto = chico.height
    val pixeles = IntArray(ancho * alto)
    chico.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
    if (chico !== fuente) chico.recycle()
    for (i in pixeles.indices) {
        val p = pixeles[i]
        pixeles[i] = ((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8
    }
    val zw = zona.width().toFloat()
    val zh = zona.height().toFloat()
    val guia = Caja((gx - zona.left) / zw, (gy - zona.top) / zh, (gx + gw - zona.left) / zw, (gy + gh - zona.top) / zh)
    // Del fotograma derecho al visor: se escala y se quita lo que FILL_CENTER deja afuera.
    val fueraX = (fw * escala - visor.width) / 2
    val fueraY = (fh * escala - visor.height) / 2
    val enVisor = RectF(zona.left * escala - fueraX, zona.top * escala - fueraY, zona.right * escala - fueraX, zona.bottom * escala - fueraY)
    return Vistazo(Grises(ancho, alto, pixeles), guia, enVisor)
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
