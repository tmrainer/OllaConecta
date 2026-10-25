// CamaraDni.kt — leer el DNI con la cámara (camara-dni.js), sobre toda la
// pantalla: se abre con ctx.abrirCapa { CamaraDni(…) }. Solo la zona de lectura mecánica (MRZ) y el anverso, con el
// reconocimiento de texto de ML Kit; el PDF417 no se usa (sin verificar). La secuencia es la de EscaneoDni.kt:
//
// - Inscribir: el reverso hasta dos lecturas iguales de la MRZ con sus
//   controles; luego el anverso (tildes, ñ y el segundo apellido), cruzado con
//   la MRZ. Si en unas vueltas no aparece, se pide voltear y se ofrece «Omitir».
//   El resultado solo llena el formulario: inscribir sigue siendo de la persona.
// - Pasar lista: solo el reverso, continuo. Cada DNI leído se entrega y se
//   sigue con el siguiente; el mismo DNI frente a la cámara no se repite.
//
// La lectura no se guarda ni se registra: se entrega y se descarta.
package io.github.tmrainer.asistencia.ui.camara

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tmrainer.asistencia.nucleo.dni.Consejo
import io.github.tmrainer.asistencia.nucleo.dni.EstadoCampo
import io.github.tmrainer.asistencia.nucleo.dni.LecturaAnverso
import io.github.tmrainer.asistencia.nucleo.dni.LecturaMrz
import io.github.tmrainer.asistencia.nucleo.dni.buscarMrzTd1
import io.github.tmrainer.asistencia.nucleo.dni.combinarMrzYAnverso
import io.github.tmrainer.asistencia.nucleo.dni.evaluar
import io.github.tmrainer.asistencia.nucleo.dni.unirFilasOcr
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.vibrar
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PISTA_REVERSO = "Pon en el recuadro el lado con tres líneas de «<». Usa buena luz."
private const val PISTA_ASISTENCIA = "Muestra el lado con tres líneas de «<». Pasa uno tras otro."
private const val PISTA_ANVERSO = "Ahora voltea el DNI: el lado de la foto."

// En la cámara el fondo siempre es oscuro: verde y ámbar claros, iguales en los dos modos.
internal val VERDE_VISOR = Color(0xFF9FCF8A)
internal val AMBAR_VISOR = Color(0xFFF0C26C)

/** En el DNI azul los nombres están en la misma cara que la MRZ: unas vueltas antes de pedir voltear. */
private const val VUELTAS_MISMA_CARA = 3

/** Lo que se lee para inscribir: la MRZ con sus controles y, si se pudo, el anverso cruzado. */
class LecturaDni(val mrz: LecturaMrz, val anverso: LecturaAnverso?)

/**
 * La cámara del DNI. Con [alLeerDni] es «pasar lista» (continuo: devuelve
 * qué decir en el visor, «✓ Marcaste a…»); si no, es inscribir y [alLeer]
 * recibe una sola lectura, tras la cual se cierra.
 */
@Composable
fun CamaraDni(
    alCerrar: () -> Unit,
    alLeer: (LecturaDni) -> Unit = {},
    alLeerDni: ((String) -> String)? = null
) {
    val enAsistencia = alLeerDni != null
    val context = LocalContext.current
    val principal = remember { ContextCompat.getMainExecutor(context) }
    var permiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var negado by remember { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dado -> permiso = dado; negado = !dado }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.CAMERA) }

    var pista by remember { mutableStateOf(if (enAsistencia) PISTA_ASISTENCIA else PISTA_REVERSO) }
    var estadoTexto by remember { mutableStateOf("Abriendo la cámara…") }
    var abriendo by remember { mutableStateOf(true) }
    var recorte by remember { mutableStateOf(Recorte.FRANJA_MRZ) }
    var hayLinterna by remember { mutableStateOf(false) }
    var linterna by remember { mutableStateOf(false) }
    var conOmitir by remember { mutableStateOf(false) }
    var falla by remember { mutableStateOf<String?>(null) }
    var destellos by remember { mutableIntStateOf(0) }
    var cerrado by remember { mutableStateOf(false) }
    var paso by remember { mutableIntStateOf(1) }
    // La guía en vivo: qué decir y dónde se ve la tarjeta. Lo leído espera la revisión.
    var consejo by remember { mutableStateOf<Consejo?>(null) }
    var tarjetaEnVisor by remember { mutableStateOf<RectF?>(null) }
    var leida by remember { mutableStateOf<LecturaDni?>(null) }
    // Los avisos de lo que pasó («Listas las tres líneas…», «✓ Marcaste a…») duran
    // un rato; después vuelve el consejo.
    LaunchedEffect(estadoTexto) { if (estadoTexto.isNotEmpty() && !abriendo) { delay(3_500); estadoTexto = "" } }

    // El estado de la búsqueda vive en el hilo del visor; lo que se ve, en el principal.
    val busqueda = remember { Busqueda() }
    val lector = remember { LectorTexto() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { busqueda.cerrado = true; lector.close() } }

    fun cerrar() { if (!cerrado) { cerrado = true; busqueda.cerrado = true; alCerrar() } }

    fun entregar(lectura: LecturaDni) {
        if (busqueda.cerrado) return
        busqueda.cerrado = true
        principal.execute {
            vibrar(context, 60)
            destellos += 1
            busqueda.entregar = { if (!cerrado) leida = lectura }
        }
    }

    fun analizar(imagen: android.graphics.Bitmap) {
        val b = busqueda
        if (b.cerrado) return
        val fragmentos = try { lector.leer(imagen) } catch (e: LectorNoDisponible) {
            principal.execute { falla = "El lector aún no está listo. Prueba en unos minutos, con internet." }
            b.cerrado = true
            return
        } catch (e: Exception) { return }    // un fotograma que no se pudo leer: se prueba con el siguiente
        if (b.cerrado) return
        principal.execute { if (abriendo) { abriendo = false; estadoTexto = "" } }
        val filas = unirFilasOcr(fragmentos)
        val mrzLeida = b.mrz
        if (mrzLeida == null) {
            val mrz = buscarMrzTd1(filas)
            val clave = mrz?.lineas?.joinToString("") ?: ""
            if (mrz != null && clave == b.anterior) {
                b.anterior = ""
                if (!enAsistencia) {
                    b.mrz = mrz
                    b.vueltas = 0
                    principal.execute {
                        vibrar(context, 40); destellos += 1
                        recorte = Recorte.TARJETA
                        estadoTexto = "Listas las tres líneas. Buscando los nombres…"
                    }
                } else {
                    // El mismo DNI sigue frente a la cámara: no se vuelve a marcar.
                    val dni = mrz.campos.numeroDocumento
                    val ahora = System.currentTimeMillis()
                    if (dni != b.ultimoDni || ahora - b.ultimoEn > 5000) {
                        principal.execute {
                            vibrar(context, 60); destellos += 1
                            estadoTexto = alLeerDni?.invoke(dni) ?: ""
                        }
                    }
                    b.ultimoDni = dni
                    b.ultimoEn = ahora
                }
            } else b.anterior = clave
        } else {
            b.vueltas += 1
            if (b.vueltas == VUELTAS_MISMA_CARA) principal.execute { paso = 2; pista = PISTA_ANVERSO; estadoTexto = ""; conOmitir = true }
            val anverso = combinarMrzYAnverso(mrzLeida.campos, filas)
            if (anverso != null) {
                b.lecturasAnverso += 1
                // Paterno y nombres iguales a la MRZ ya son dos fuentes de acuerdo. El
                // segundo apellido, si no aparece en unas lecturas, queda para escribirlo.
                // Con una letra dudosa, sí se esperan dos lecturas iguales.
                val cruzados = anverso.apellidoPaterno.estado == EstadoCampo.CONFIRMADO && anverso.nombres.estado == EstadoCampo.CONFIRMADO
                val conMaterno = anverso.apellidoMaterno.estado != EstadoCampo.NO_LEIDO
                if ((cruzados && (conMaterno || b.lecturasAnverso >= 4)) || anverso == b.anteriorAnverso) {
                    entregar(LecturaDni(mrzLeida, anverso))
                    return
                }
            }
            b.anteriorAnverso = anverso
        }
    }

    /** La guía en vivo, en el hilo del visor: antes de leer cada fotograma. */
    fun mirar(v: Vistazo) {
        val b = busqueda
        if (b.cerrado) return
        val e = evaluar(v.grises, v.guia, b.conEnfoque, b.hayLinterna)
        val r = v.enVisor
        val enVisor = e.tarjeta?.let { t -> RectF(r.left + t.izquierda * r.width(), r.top + t.arriba * r.height(), r.left + t.derecha * r.width(), r.top + t.abajo * r.height()) }
        // Un consejo nuevo se muestra cuando se repite en dos fotogramas seguidos: sin parpadeos.
        val nuevo = if (e.consejo == b.consejoVisto) e.consejo else null
        b.consejoVisto = e.consejo
        principal.execute {
            tarjetaEnVisor = enVisor
            if (nuevo != null && !abriendo) consejo = nuevo
        }
    }

    fun leerOtraVez() {
        busqueda.reiniciar()
        leida = null; paso = 1; pista = PISTA_REVERSO; recorte = Recorte.FRANJA_MRZ; conOmitir = false
        consejo = null; tarjetaEnVisor = null; abriendo = true; estadoTexto = "Abriendo la cámara…"
    }

    val porRevisar = leida
    if (porRevisar != null) {
        RevisionDni(porRevisar, usar = { if (!cerrado) { cerrado = true; alLeer(porRevisar); alCerrar() } }, otraVez = ::leerOtraVez)
        return
    }

    // Sobre toda la app (Contexto.abrirCapa), entre la barra de arriba y la de abajo.
    run {
        Column(
            Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(Medidas.e4),
            verticalArrangement = Arrangement.spacedBy(Medidas.e3)
        ) {
            Text(if (enAsistencia) "Pasar lista con el DNI" else "Leer el DNI", style = tipos.titulo, color = Color.White, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                DibujoDni(conFoto = paso == 2)
                Column(Modifier.weight(1f)) {
                    if (!enAsistencia) Text("Paso $paso", style = tipos.chicoFuerte, color = VERDE_VISOR)
                    Text(pista, style = tipos.texto, color = Color.White.copy(alpha = .85f))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(Medidas.rM)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                val mensajeFalla = falla
                when {
                    negado -> Text("La app necesita permiso para usar la cámara.", style = tipos.texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(Medidas.e4))
                    mensajeFalla != null -> Text(mensajeFalla, style = tipos.texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(Medidas.e4))
                    permiso -> {
                        VisorCamara(
                            recorte = recorte, linterna = linterna,
                            alTenerLinterna = { hayLinterna = it; busqueda.hayLinterna = it },
                            alFallar = { falla = "$it Revisa que la app tenga permiso para usarla." },
                            analizar = ::analizar,
                            modifier = Modifier.fillMaxSize(),
                            mirar = ::mirar,
                            alTenerEnfoque = { busqueda.conEnfoque = it }
                        )
                        Recuadro(recorte, destellos, tarjeta = tarjetaEnVisor, bien = consejo == Consejo.BIEN) { busqueda.entregar?.invoke() }
                        if (abriendo) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Abriendo la cámara…", style = tipos.chico, color = Color.White, modifier = Modifier.padding(top = Medidas.e2))
                        }
                    }
                }
            }
            val abajo = estadoTexto.ifEmpty { consejo?.texto ?: "" }
            if (!abriendo && falla == null && !negado && abajo.isNotEmpty()) {
                Text(abajo, style = tipos.textoFuerte, color = if (consejo == Consejo.BIEN && estadoTexto.isEmpty()) VERDE_VISOR else Color.White,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Acciones {
                if (negado) Boton("Dar permiso en Ajustes", {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    cerrar()
                })
                if (hayLinterna) Boton(if (linterna) "Apagar la linterna" else "Prender la linterna", { linterna = !linterna }, activo = linterna)
                val mrz = busqueda.mrz
                if (conOmitir && mrz != null) Boton("Omitir este paso", { if (!cerrado) { busqueda.cerrado = true; leida = LecturaDni(mrz, null) } })
                Boton(if (enAsistencia) "Terminar" else "Cancelar", ::cerrar)
            }
        }
    }
}

/** Lo que recuerda la búsqueda entre fotogramas (solo la toca el hilo del visor, salvo `cerrado`). */
private class Busqueda {
    @Volatile var cerrado = false
    // Lo que el visor sabe de la cámara, para el consejo.
    @Volatile var conEnfoque = true
    @Volatile var hayLinterna = false
    var consejoVisto: Consejo? = null
    var anterior = ""
    @Volatile var mrz: LecturaMrz? = null
    var vueltas = 0
    var lecturasAnverso = 0
    var anteriorAnverso: LecturaAnverso? = null
    var ultimoDni = ""
    var ultimoEn = 0L
    /** Lo que se hace al terminar el destello de «leído». */
    var entregar: (() -> Unit)? = null

    /** «Leer otra vez»: como recién abierta (la cámara ya está cerrada mientras se revisa). */
    fun reiniciar() {
        anterior = ""; mrz = null; vueltas = 0; lecturasAnverso = 0; anteriorAnverso = null
        consejoVisto = null; entregar = null; cerrado = false
    }
}

/**
 * El recuadro donde va el DNI (o el QR), con la sombra alrededor. Al leer, se
 * pone verde un instante con un ✓ (destelloLeido en ui.js); [alTerminarDestello]
 * corre cuando se apaga. Con la guía en vivo, [tarjeta] es el DNI que se ve
 * (en píxeles del visor): su contorno va en ámbar, y en verde con el recuadro
 * cuando está [bien] puesto.
 */
@Composable
fun Recuadro(recorte: Recorte, destellos: Int, tarjeta: RectF? = null, bien: Boolean = false, alTerminarDestello: () -> Unit = {}) {
    val verde = VERDE_VISOR
    val brillo = remember { Animatable(0f) }
    val marca = remember { Animatable(0f) }
    LaunchedEffect(destellos) {
        if (destellos == 0) return@LaunchedEffect
        // El ✓ entra con un pequeño rebote, como .camara-leido-marca.
        marca.snapTo(0f)
        launch { marca.animateTo(1f, tween(190, easing = CubicBezierEasing(.2f, 1.4f, .4f, 1f))) }
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
            drawRoundRect(if (brillo.value > 0f || bien) verde else Color.White, origen, Size(ancho, alto), CornerRadius(16.dp.toPx()), style = Stroke(3.dp.toPx()))
            if (tarjeta != null && brillo.value == 0f) {
                drawRoundRect(if (bien) verde else AMBAR_VISOR, Offset(tarjeta.left, tarjeta.top), Size(tarjeta.width(), tarjeta.height()), CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            if (recorte == Recorte.FRANJA_MRZ) {
                // La franja de las tres líneas: donde va el «<<<».
                val y = origen.y + alto * 0.55f
                drawLine(Color.White.copy(alpha = .5f), Offset(origen.x + 12.dp.toPx(), y), Offset(origen.x + ancho - 12.dp.toPx(), y), 1.5.dp.toPx())
            }
        }
        if (brillo.value > 0f) {
            Box(
                Modifier.graphicsLayer {
                    val m = marca.value
                    scaleX = .4f + .6f * m; scaleY = .4f + .6f * m
                    // Se va con el verde, en el último tramo.
                    alpha = m.coerceIn(0f, 1f) * (brillo.value / .3f).coerceAtMost(1f)
                }.size(96.dp).shadow(8.dp, CircleShape).background(Color(0xFF2E7D32), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icono("ok", 56.dp, Color.White, trazo = 3f) }
        }
    }
}
