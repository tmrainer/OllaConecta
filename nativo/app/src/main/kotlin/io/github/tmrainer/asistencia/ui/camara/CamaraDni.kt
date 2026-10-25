// Leer el DNI con la cámara, sobre toda la pantalla: la MRZ del reverso y el anverso,
// con el reconocimiento de texto de ML Kit (el PDF417 no se usa: no se verifica).
// Inscribir lee el reverso, pide voltear y lee el anverso; el resultado solo llena el
// formulario. Pasar lista lee solo reversos, uno tras otro.
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.runtime.CompositionLocalProvider
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.tema.COLORES_OSCURO
import io.github.tmrainer.asistencia.ui.tema.LocalColores
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.vibrar
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PISTA_REVERSO = "Pon en el recuadro el lado con tres líneas de «<»."
private const val PISTA_ASISTENCIA = "Muestra el lado con tres líneas de «<». Pasa uno tras otro."
private const val PISTA_ANVERSO = "Ahora voltea el DNI: el lado de la foto."

// En la cámara el fondo siempre es oscuro: verde y ámbar claros, iguales en los dos modos.
internal val VERDE_VISOR = Color(0xFF9FCF8A)
internal val AMBAR_VISOR = Color(0xFFF0C26C)

/** En el DNI azul los nombres están en la misma cara que la MRZ: unas vueltas antes de pedir voltear. */
private const val VUELTAS_MISMA_CARA = 3

/** Las pantallas de leer el DNI: antes de cada lado, la cámara de cada lado, y la espera entre los dos. */
private enum class Etapa { ANTES_ATRAS, ATRAS, REVISANDO_ATRAS, ANTES_FOTO, FOTO }

/** Lo que se lee para inscribir: la MRZ con sus controles y, si se pudo, el anverso cruzado. */
class LecturaDni(val mrz: LecturaMrz, val anverso: LecturaAnverso?)

/**
 * La cámara del DNI. Con [alLeerDni] es «pasar lista» (continuo: devuelve
 * qué decir en el visor, «✓ Marcaste a…»); si no, es inscribir y [alLeer]
 * recibe una sola lectura, tras la cual se cierra. Sin [conRevision], lo leído
 * se entrega sin la pantalla «Revisa los datos»: quien lo pide ya lo compara
 * (confirmar los datos del primer uso). [yaEsta]: si el número leído de las
 * tres líneas ya está en el padrón, no se pide el otro lado ni se revisa: se
 * entrega enseguida y quien lo pidió avisa que ya está inscrita.
 */
@Composable
fun CamaraDni(
    alCerrar: () -> Unit,
    alLeer: (LecturaDni) -> Unit = {},
    alLeerDni: ((String) -> String)? = null,
    conRevision: Boolean = true,
    yaEsta: ((String) -> Boolean)? = null
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
    // Pasar lista va directo a la cámara: se lee un DNI tras otro.
    var etapa by remember { mutableStateOf(if (enAsistencia) Etapa.ATRAS else Etapa.ANTES_ATRAS) }
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
    // Mientras se ve una pantalla de antes, la cámara sigue abierta pero no se lee.
    SideEffect { busqueda.pausa = etapa == Etapa.ANTES_ATRAS || etapa == Etapa.ANTES_FOTO }

    fun cerrar() { if (!cerrado) { cerrado = true; busqueda.cerrado = true; alCerrar() } }

    /** Lo leído: a revisar o, sin revisión, directo a quien lo pidió. */
    fun terminar(lectura: LecturaDni, revisar: Boolean = conRevision) {
        if (cerrado) return
        if (revisar) leida = lectura else { cerrado = true; alLeer(lectura); alCerrar() }
    }

    fun entregar(lectura: LecturaDni, revisar: Boolean = conRevision) {
        if (busqueda.cerrado) return
        busqueda.cerrado = true
        principal.execute {
            vibrar(context, 60)
            destellos += 1
            busqueda.entregar = { terminar(lectura, revisar) }
        }
    }

    fun analizar(imagen: android.graphics.Bitmap) {
        val b = busqueda
        if (b.cerrado) return
        // Llegan fotogramas: la cámara ya está abierta, aunque todavía no se lea.
        if (b.pausa) { principal.execute { if (abriendo) { abriendo = false; estadoTexto = "" } }; return }
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
                    // Ya está en el padrón: con el número basta; no se pide el otro lado.
                    if (yaEsta?.invoke(mrz.campos.numeroDocumento) == true) { entregar(LecturaDni(mrz, null), revisar = false); return }
                    b.mrz = mrz
                    b.vueltas = 0
                    principal.execute {
                        vibrar(context, 40); destellos += 1
                        recorte = Recorte.TARJETA
                        estadoTexto = ""
                        etapa = Etapa.REVISANDO_ATRAS
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
            if (b.vueltas == VUELTAS_MISMA_CARA) principal.execute {
                paso = 2; pista = PISTA_ANVERSO; estadoTexto = ""; conOmitir = true
                if (etapa == Etapa.REVISANDO_ATRAS) etapa = Etapa.ANTES_FOTO
            }
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
        if (b.cerrado || b.pausa) return
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
        etapa = Etapa.ATRAS
        consejo = null; tarjetaEnVisor = null; abriendo = true; estadoTexto = "Abriendo la cámara…"
    }

    /** Sin el lado de la foto: los nombres quedan como en la MRZ, sin tildes ni ñ. */
    fun omitir() {
        val mrz = busqueda.mrz ?: return
        busqueda.cerrado = true
        terminar(LecturaDni(mrz, null))
    }

    val porRevisar = leida
    if (porRevisar != null) {
        RevisionDni(porRevisar, usar = { if (!cerrado) { cerrado = true; alLeer(porRevisar); alCerrar() } }, otraVez = ::leerOtraVez)
        return
    }

    // Sobre toda la app (Contexto.abrirCapa), entre la barra de arriba y la de abajo.
    val antes = etapa == Etapa.ANTES_ATRAS || etapa == Etapa.ANTES_FOTO
    // El fondo de la cámara es siempre negro: sus botones usan los colores del modo
    // oscuro también en el claro (el verde del claro sobre negro no se lee bien).
    CompositionLocalProvider(LocalColores provides COLORES_OSCURO) { Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(Medidas.e4)
                .then(if (antes) Modifier.clearAndSetSemantics { } else Modifier),
            verticalArrangement = Arrangement.spacedBy(Medidas.e3)
        ) {
            Text(if (enAsistencia) "Pasar lista con el DNI" else "Leer el DNI", style = tipos.titulo, color = Color.White, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                // El lado de las tres líneas, en los dos DNI: el electrónico y el azul.
                if (paso == 2) DibujoDni(CaraDni.FOTO)
                else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DibujoDni(CaraDni.TRES_LINEAS, Modifier.size(60.dp, 38.dp))
                    DibujoDni(CaraDni.AZUL, Modifier.size(60.dp, 38.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (!enAsistencia) Text("Paso $paso de 2", style = tipos.chicoFuerte, color = VERDE_VISOR)
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
                        if (abriendo) Cargando("Abriendo la cámara…", demora = 0)
                        else if (etapa == Etapa.REVISANDO_ATRAS) Cargando("Listas las tres líneas. Revisando…")
                    }
                }
            }
            val abajo = estadoTexto.ifEmpty { consejo?.texto ?: "" }
            if (!abriendo && falla == null && !negado && abajo.isNotEmpty()) {
                Text(abajo, style = tipos.textoFuerte, color = if (consejo == Consejo.BIEN && estadoTexto.isEmpty()) VERDE_VISOR else Color.White,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            // Dos botones por fila como máximo: lo que no es de todos los días va solo, arriba.
            if (negado) Boton("Dar permiso en Ajustes", {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                cerrar()
            }, Modifier.fillMaxWidth(), tipo = TipoBoton.PRINCIPAL)
            if (conOmitir && busqueda.mrz != null) Boton("Omitir este paso", ::omitir, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                if (hayLinterna) Boton(if (linterna) "Apagar la linterna" else "Prender la linterna", { linterna = !linterna }, Modifier.weight(1f), activo = linterna)
                Boton(if (enAsistencia) "Terminar" else "Cancelar", ::cerrar, if (hayLinterna) Modifier else Modifier.fillMaxWidth())
            }
        }
        when (etapa) {
            Etapa.ANTES_ATRAS -> AntesDelLado(1, alSeguir = { etapa = Etapa.ATRAS }, alCancelar = ::cerrar)
            Etapa.ANTES_FOTO -> AntesDelLado(2, alSeguir = { etapa = Etapa.FOTO }, alCancelar = ::cerrar, alOmitir = ::omitir)
            else -> {}
        }
    } }
}

/**
 * El círculo de carga, en bucle, sobre el visor: al abrir la cámara o mientras
 * se revisa lo leído. Con [demora], solo si la espera se alarga (no por un instante).
 */
@Composable
private fun Cargando(texto: String, demora: Long = 450) {
    var visible by remember { mutableStateOf(demora == 0L) }
    LaunchedEffect(Unit) { delay(demora); visible = true }
    if (!visible) return
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = VERDE_VISOR, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
        Text(texto, style = tipos.textoFuerte, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Medidas.e3, start = Medidas.e4, end = Medidas.e4).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Lo que recuerda la búsqueda entre fotogramas (solo la toca el hilo del visor, salvo `cerrado`). */
private class Busqueda {
    @Volatile var cerrado = false
    /** Se ve una pantalla de antes de un lado: la cámara sigue, la lectura no. */
    @Volatile var pausa = false
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
 * pone verde un instante con un ✓; [alTerminarDestello]
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
        // El ✓ entra con un pequeño rebote.
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
