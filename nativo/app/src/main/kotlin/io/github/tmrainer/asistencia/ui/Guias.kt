// Lo que abre «Ayuda» en cada pantalla: cómo se usa ESA pantalla, en pocos pasos, con
// los mismos nombres de botones que se ven. Se abre cuando se pide (no interrumpe).
package io.github.tmrainer.asistencia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.tmrainer.asistencia.Preferencias
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.IconosBarras
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.AnilloFoco
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

/**
 * Un paso de una guía: qué hacer, en una o dos frases, y su dibujo. [objetivos]:
 * las partes de la pantalla que se iluminan (Modifier.objetivoGuia); si no
 * están en la pantalla, se ve el dibujo. [soloSiEsta]: habla de algo que no
 * siempre aparece («Usar estas fechas»); si no está, el paso se salta. [siEsta]:
 * el paso vale solo si esa parte está (lo de abajo de una lista con personas).
 * En una lista larga, ambos miran lo de arriba: la guía la sube antes de elegir.
 */
class PasoGuia(val titulo: String, val texto: String, val dibujo: String, vararg objetivos: String, val soloSiEsta: Boolean = false, val siEsta: String? = null) {
    val objetivos = objetivos.toList()
}

/**
 * La guía de una pantalla: [nombre] es de qué pantalla («Asistencia»). [clave]:
 * se recuerda que se vio (Preferencias.guiaVista). [despues]: al final ofrece
 * seguir con otra guía.
 */
class Guia(val nombre: String, val pasos: List<PasoGuia>, val clave: String? = null, val despues: Despues? = null)

/** Lo que ofrece una guía al terminar: [texto] lleva a [guia]; [principal] la pone como el botón de avanzar. */
class Despues(val texto: String, val principal: Boolean, val guia: () -> Guia)

/** El Inicio: qué hay en cada sección. También es la bienvenida de la primera vez. */
val GUIA_INICIO = Guia("Inicio", listOf(
    PasoGuia("Hoy", "Las raciones de hoy, lo recaudado y cuánto falta para entregar el B-1.", "casa", "inicio.hoy"),
    PasoGuia("Pasar lista hoy", "Tócalo cuando sirvan la comida: ahí marcas a quien vino.", "lista", "inicio.lista"),
    PasoGuia("Atajos", "Inscribir a alguien, anotar una compra, y ver las entregas con el reporte del mes, con un toque.", "lista", "inicio.tareas", "inicio.tareas2"),
    PasoGuia("El menú de abajo", "Asistencia, Padrón, B-1 y Entregas. Desde cualquier pantalla, toca uno para ir.", "lista", "nav.asistencia", "nav.padron", "nav.b1", "nav.entregas"),
    PasoGuia("Tus datos", "Están solo en este teléfono. Aquí la app te recuerda guardar una copia.", "escudo", "inicio.datos"),
    PasoGuia("Ajustes", "Para agrandar la letra, oír la pantalla en voz alta o corregir tus datos.", "ajustes", "ajustes"),
    PasoGuia("Ayuda", "En cada pantalla, toca «Ayuda» para ver cómo se usa esa pantalla.", "ayuda", "ayuda")
))

val GUIA_ASISTENCIA = Guia("Asistencia", listOf(
    PasoGuia("Primero, las personas", "Para pasar lista, primero inscribe a las personas en «Padrón» o carga la hoja de la Municipalidad.", "personas", "asistencia.vacia", soloSiEsta = true),
    PasoGuia("Toca a quien vino", "Toca el círculo de cada persona que vino a comer. Si te equivocas, tócalo otra vez.", "lista", "asistencia.lista", soloSiEsta = true),
    PasoGuia("O lee su DNI", "Toca «Pasar lista leyendo el DNI» y muestra cada DNI a la cámara, uno tras otro.", "camara", "asistencia.dni", soloSiEsta = true),
    PasoGuia("Busca o mira quién falta", "Escribe un nombre o un DNI en «Buscar». «Ver los que faltan» muestra a quien no marcaste.", "buscar", "asistencia.buscar", soloSiEsta = true),
    PasoGuia("Quien no está en el padrón", "Si comió alguien que no está inscrito, súmalo con «+» en «Comieron sin estar en el padrón».", "personaMas", "asistencia.sinPadron", siEsta = "asistencia.lista"),
    PasoGuia("Qué se cocinó", "Escribe qué se cocinó hoy y cuántas raciones prepararon. Sale en el reporte del día.", "lapiz", "asistencia.cocino", siEsta = "asistencia.lista"),
    PasoGuia("Enviar a UNACEM", "Al terminar, toca «Enviar a UNACEM»: la asistencia de hoy sale en Excel desde tu correo.", "archivo", "asistencia.unacem", siEsta = "asistencia.lista"),
    PasoGuia("Otros días", "Para un día que pasó, toca «+ Registrar otro día» o abre ese día en «Otros días».", "reloj", "asistencia.otros"),
    PasoGuia("Precio del menú", "Si cambia el precio, cámbialo en «Precio del menú». Vale desde hoy.", "lapiz", "asistencia.precio"),
    PasoGuia("Reporte del día", "Elige el día y toca «PDF» o «Excel»: sale qué se cocinó, las raciones y quiénes recibieron, con línea para firmar.", "archivo", "asistencia.reporte"),
    PasoGuia("El resumen", "Abajo ves las raciones y lo recaudado de las fechas que elijas.", "archivo", "asistencia.cifras")
))

val GUIA_PADRON = Guia("Padrón", listOf(
    PasoGuia("Lo que falta", "Si a alguien le falta un dato o hay que corregirlo, sale aquí. Toca «Ver el detalle» y la app abre a esa persona.", "aviso", "padron.revisar", soloSiEsta = true),
    PasoGuia("Las personas de la olla", "Aquí están todas. Toca a una para ver sus datos, corregirlos o darla de baja.", "personas", "padron.lista", soloSiEsta = true),
    PasoGuia("Busca y filtra", "Escribe un nombre o un DNI. En «Mostrar» elige a quiénes ver: solo las activas, a quienes les faltan datos y más.", "buscar", "padron.buscar", "padron.filtros", soloSiEsta = true),
    PasoGuia("Inscribe a alguien", "Toca «Inscribir», abajo. Puedes leer su DNI con la cámara o escribir los datos.", "personaMas", "padron.inscribir"),
    PasoGuia("Llévala a la Municipalidad", "Quien inscribes queda pendiente. Cuando la registren, toca «Ya la registraron en la Municipalidad».", "aviso"),
    PasoGuia("La hoja de la Municipalidad", "Cuando te den la hoja en Excel, toca «Cargar la hoja (Excel)». Antes de guardar verás qué cambia.", "archivo", "padron.hoja")
))

val GUIA_INSCRIBIR = Guia("Inscribir", listOf(
    PasoGuia("De dónde viene", "«Llegó a la olla»: queda pendiente hasta que la registre la Municipalidad. «Ya está en la hoja de la Municipalidad»: la copias de esa hoja.", "personaMas", "inscribir.origen"),
    PasoGuia("Lee el DNI", "Toca «Leer el DNI con la cámara». La app llena los datos y te dice qué revisar.", "camara", "inscribir.dni"),
    PasoGuia("O escribe los datos", "Copia los nombres y el DNI tal como están en el documento. Los campos con * son obligatorios.", "lapiz", "inscribir.paterno", "inscribir.nombres"),
    PasoGuia("Revisa e inscribe", "Compara con el DNI en la mano y toca «Inscribir».", "ok", "inscribir.boton")
))

private val PASOS_GENERAL_B1 = listOf(
    PasoGuia("Qué es el B-1", "La rendición de cuentas de tu olla. Cuando pongas las fechas, arriba sale su código: el mes y si es una quincena (Q1 o Q2), el mes entero (M) o una semana (S).", "boleta", "cabecera.subtitulo"),
    PasoGuia("Cinco pasos", "Se llena por partes. En la barra, el paso de ahora se ve más marcado y los que tienen algo pendiente, en azul. Toca un tramo para ir a ese paso. «Ver la hoja» la muestra como en el papel, en cualquier paso.", "lista", "b1.barra", "b1.barraTexto"),
    PasoGuia("Lo que quedó antes", "Si entraste con «Anotar una compra», aquí se dice qué falta en los pasos de antes. «Completar ahora» te lleva.", "aviso", "b1.faltaAntes", soloSiEsta = true),
    PasoGuia("Lo que falta", "«Por revisar» cuenta lo que falta o está mal. Toca «Ver el detalle» y la app te lleva al campo.", "aviso", "b1.revisar", soloSiEsta = true),
    PasoGuia("Avanza y vuelve", "«Siguiente» te lleva al paso que sigue y «Atrás», al de antes. Todo se guarda solo, mientras escribes.", "ok", "b1.botones")
)

/** La general del B-1; con [paso], al final lleva a la ayuda de ese paso. */
private fun guiaGeneralB1(paso: Int?) = Guia("B-1", PASOS_GENERAL_B1, clave = "b1",
    despues = paso?.let { n -> Despues("Ver la ayuda de este paso", principal = true) { guiaPasoB1(n) } })

private val NOMBRE_PASO_B1 = listOf("Centro", "Periodo", "Ingresos", "Compras", "Revisar")

/** Lo de cada paso del B-1, con sus piezas reales. */
private val PASOS_B1 = listOf(
    listOf(
        PasoGuia("Datos guardados", "Si ya llenaste un B-1, toca «Usar los datos guardados»: se copian y quedan marcados para que los revises.", "archivo", "b1.guardados", soloSiEsta = true),
        PasoGuia("Tipo de centro", "La app es para ollas comunes: ya viene marcado «Olla común».", "casa", "b1.tipo"),
        PasoGuia("Los datos del centro", "Este y los que siguen: el nombre, el código de PCA, el distrito, la presidenta, su DNI y el celular. Vienen de los datos de tu olla (Ajustes); revísalos.", "lapiz", "b1.nombreCentro")
    ),
    listOf(
        PasoGuia("El mes", "En «Mes», desliza y toca el mes del B-1. Va del primer al último día; la fecha de rendición la pone la app.", "calendario", "b1.mes"),
        PasoGuia("Cómo sale", "Al cerrar el mes, el B-1 sale con el mes y el año, como Noviembre-2026. Si lo mandas antes, sale con la semana (S1, S2, S4, S5) o la quincena (Q1) en que va.", "boleta", "b1.codigo", soloSiEsta = true)
    ),
    listOf(
        PasoGuia("El subsidio", "Escribe cuánto te dio la Municipalidad para este periodo, como dice tu papel.", "archivo", "b1.monto"),
        PasoGuia("La fecha", "El día que recibiste ese dinero.", "reloj", "b1.fechaAsignacion")
    ),
    listOf(
        PasoGuia("Una por boleta", "Toca «Compra» para anotar cada boleta. Cópiala tal cual: la fecha, qué compraste, la cantidad y el monto.", "mas", "b1.compra"),
        PasoGuia("Tus compras", "Cada compra queda en la lista. Tócala para abrirla y corregirla. En ámbar, lo que hay que revisar.", "lista", "b1.compras"),
        PasoGuia("La suma", "Los totales se suman solos de tus compras: no hay que hacer cuentas. Lo que compraron con su propio dinero no va aquí: se anota en Entregas, como gasto propio.", "ok", "b1.totales")
    ),
    listOf(
        PasoGuia("Qué falta", "Si falta algo, sale aquí con el paso donde está. Toca «Completar» o «Corregir» y la app te lleva.", "aviso", "b1.estado"),
        PasoGuia("Mira la hoja", "Toca «Ver cómo queda en la hoja» para verlo como en el papel antes de mandarlo.", "boleta", "b1.hoja"),
        PasoGuia("Mándalo", "Toca «PDF» y elige en el menú: WhatsApp, correo o «Guardar en el teléfono». Si te lo piden antes de fin de mes, sale con la semana o la quincena en que va.", "archivo", "b1.exportar"),
        PasoGuia("Cerrar el mes", "Al terminar el mes, toca «Cerrar…»: el B-1 queda con el nombre del mes, lo mandas en ese momento y empieza el siguiente.", "calendario", "b1.meses", "b1.mesSiguiente")
    )
)

/** La ayuda del paso [paso] (1 a 5) del B-1; al final ofrece la general. */
fun guiaPasoB1(paso: Int): Guia {
    val i = (paso - 1).coerceIn(0, PASOS_B1.size - 1)
    return Guia("B-1, paso ${i + 1}: ${NOMBRE_PASO_B1[i]}", PASOS_B1[i],
        despues = Despues("Cómo funciona todo el B-1", principal = false) { guiaGeneralB1(i + 1) })
}

/** Lo que abre «Ayuda» en el B-1: la primera vez, cómo funciona todo; después, lo del paso donde estás. */
fun ayudaB1(prefs: Preferencias, paso: Int): Guia =
    if (prefs.guiaVista("b1")) guiaPasoB1(paso) else guiaGeneralB1(paso)

val GUIA_ENTREGAS = Guia("Entregas", listOf(
    PasoGuia("El mes", "En la fila de meses, desliza y toca el mes del que quieres ver lo que se entrega.", "calendario", "entregas.mes"),
    PasoGuia("El B-1 del mes", "Aquí ves si el B-1 de ese mes está listo. Toca el botón para ir a completarlo o verlo.", "boleta", "entregas.b1"),
    PasoGuia("Reporte del mes", "La asistencia de cada persona, las raciones y las compras del B-1, en PDF o Excel.", "archivo", "entregas.reporte"),
    PasoGuia("Gasto propio", "Lo que compraron con su propio dinero no va en el B-1. Anótalo con «Anotar un gasto propio»: sale en el reporte del mes.", "mas", "entregas.propio"),
    PasoGuia("Una fecha que conoces", "Pon una fecha de entrega, la próxima o una pasada. Vence ese día de cada mes; la app calcula las demás.", "reloj", "entregas.fecha"),
    PasoGuia("Avisarme", "Elige cuántos días antes quieres el aviso. Puedes marcar más de uno.", "aviso", "entregas.avisar"),
    PasoGuia("Que el teléfono avise", "Toca «Activar los avisos» y acepta: el teléfono te avisa aunque la app esté cerrada.", "aviso", "entregas.activar", soloSiEsta = true),
    PasoGuia("Marca cada entrega", "En «Próximas entregas», cambia «Pendiente» por «Entregada» cuando la entregues.", "ok", "entregas.lista", soloSiEsta = true)
))

val GUIA_IMPORTAR = Guia("Hoja de la Municipalidad", listOf(
    PasoGuia("La fecha de la hoja", "Pon la fecha que dice la hoja, o el día que te la dieron.", "calendario", "importar.fecha"),
    PasoGuia("Elige el archivo", "Toca «Elegir el archivo de Excel» y busca la hoja que te mandaron, por ejemplo en WhatsApp o en Descargas.", "archivo", "importar.archivo"),
    PasoGuia("O pega las filas", "Si la tienes como texto, pégala aquí con la fila de títulos y toca «Ver los cambios».", "lapiz", "importar.pegar"),
    PasoGuia("Revisa y guarda", "La app te dice quién entra, quién vuelve y quién sale. Marca lo que vale y toca «Guardar la actualización». Nadie se borra.", "ok", "importar.guardar")
))

val GUIA_HOJA = Guia("Vista previa del B-1", listOf(
    PasoGuia("Así queda en el papel", "Tus datos en la hoja del B-1, como saldrán impresos. Lo que falta queda en blanco: complétalo en el B-1.", "boleta", "hoja.primera"),
    PasoGuia("Acércala", "Separa dos dedos sobre la hoja, tócala dos veces o usa «Acercar» para leer la letra chica. Acercada, se corre de lado.", "buscar", "hoja.zoom"),
    PasoGuia("Para mandarlo", "El PDF y el Excel se arman en el B-1, en el paso «Revisar».", "archivo")
))

val GUIA_DATOS = Guia("Tus datos", listOf(
    PasoGuia("Guarda una copia", "Cada dos semanas, toca «Guardar una copia». Si se pierde el teléfono, la recuperas con «Recuperar una copia».", "escudo", "datos.copia"),
    PasoGuia("Con contraseña", "La copia tiene nombres y DNI. Si la mandas por WhatsApp, guárdala con contraseña.", "escudo", "datos.contrasena"),
    PasoGuia("A otro teléfono, sin internet", "Un teléfono muestra un QR y el otro lo lee con «Recibir por QR». Antes de guardar se ve qué cambia.", "camara", "datos.qr"),
    PasoGuia("Borrar todo", "Deja la app como recién instalada. No se puede deshacer: guarda una copia antes.", "aviso", "datos.borrar")
))

val GUIA_AJUSTES = Guia("Ajustes", listOf(
    PasoGuia("Quién usa este teléfono", "Tu nombre, tu rol y tu DNI. La app los propone en el B-1.", "personas", "ajustes.quien"),
    PasoGuia("Letra más grande", "En «Tamaño de la letra», elige «Grande» o «Muy grande». Abajo ves cómo queda.", "letra", "ajustes.letra"),
    PasoGuia("Leer en voz alta", "Con «Sí», arriba de cada pantalla sale «Escuchar» y la app te la lee. Puede ir más lento.", "voz", "ajustes.voz"),
    PasoGuia("Envío a UNACEM", "Escribe una vez el correo de UNACEM. Si falta enviar, la app te lo recuerda en la noche.", "archivo", "ajustes.unacem")
))

/**
 * Dónde está cada parte que una guía puede iluminar. Cada parte se anota con
 * Modifier.objetivoGuia; [lista] es la lista larga de la pantalla (LazyColumn),
 * donde lo de más abajo todavía no existe hasta que se baja.
 */
@OptIn(ExperimentalFoundationApi::class)
class Objetivos {
    internal val lugares = HashMap<String, LayoutCoordinates>()
    internal val pedidos = HashMap<String, BringIntoViewRequester>()
    internal var lista: ScrollableState? = null

    /** ¿[id] está en la pantalla (compuesto, aunque no se vea)? */
    fun hay(id: String) = id in pedidos
}

val LocalObjetivos = staticCompositionLocalOf { Objetivos() }

/** Esta parte de la pantalla es [id] para las guías: un paso puede iluminarla. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.objetivoGuia(id: String): Modifier = composed {
    val objetivos = LocalObjetivos.current
    val pedido = remember { BringIntoViewRequester() }
    DisposableEffect(id) {
        objetivos.pedidos[id] = pedido
        onDispose {
            if (objetivos.pedidos[id] === pedido) { objetivos.pedidos.remove(id); objetivos.lugares.remove(id) }
        }
    }
    bringIntoViewRequester(pedido).onGloballyPositioned { objetivos.lugares[id] = it }
}

/** La lista larga de esta pantalla: la guía la baja para encontrar lo que ilumina. */
@Composable
fun ListaParaGuia(estado: ScrollableState) {
    val objetivos = LocalObjetivos.current
    DisposableEffect(estado) {
        objetivos.lista = estado
        onDispose { if (objetivos.lista === estado) objetivos.lista = null }
    }
}

/** Lo que ocupan en la pantalla [ids] juntos; null si ninguno se ve. */
private fun lugarDe(objetivos: Objetivos, ids: List<String>): Rect? =
    ids.mapNotNull { id -> objetivos.lugares[id]?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 0f && it.height > 0f } }
        .reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }

/**
 * Trae [ids] a la vista: el primero y, en lo posible, los demás. En una lista
 * larga, si el primero aún no existe, la mueve a saltos hasta que aparezca (y
 * si no, vuelve hacia arriba); solo el último tramo es animado.
 */
@OptIn(ExperimentalFoundationApi::class)
private suspend fun traerALaVista(objetivos: Objetivos, ids: List<String>, salto: Float, holgura: Float) {
    val id = ids.firstOrNull() ?: return
    objetivos.lista?.let { lista ->
        while (id !in objetivos.pedidos && lista.canScrollForward) { lista.scrollBy(salto); withFrameNanos { } }
        while (id !in objetivos.pedidos && lista.canScrollBackward) { lista.scrollBy(-salto); withFrameNanos { } }
        // La lista arma lo que viene antes de mostrarlo: ya existe, pero sin lugar en la
        // pantalla, y así no se puede traer. Se baja un poco más hasta que lo tenga.
        var vueltas = 0
        while (id in objetivos.pedidos && lugarDe(objetivos, listOf(id)) == null && lista.canScrollForward && vueltas++ < 30) { lista.scrollBy(salto / 4); withFrameNanos { } }
    }
    // Con [holgura] debajo: lo que flota abajo (el botón «Compra») no lo tapa.
    suspend fun traer(x: String) {
        val tam = objetivos.lugares[x]?.takeIf { it.isAttached }?.size
        objetivos.pedidos[x]?.bringIntoView(tam?.let { Rect(0f, 0f, it.width.toFloat(), it.height + holgura) })
    }
    traer(id)
    for (otro in ids.drop(1)) traer(otro)
}

// Sobre lo oscuro, en tema claro u oscuro: siempre letra clara.
// Bien oscuro: lo de abajo no se lee y no se mezcla con el texto de la guía.
private val OSCURO = Color(0xFF0A0F0B).copy(alpha = .96f)
private val FONDO_TEXTO = Color(0xFF0A0F0B)
private val CLARO = Color.White
private val CLARO_SUAVE = Color.White.copy(alpha = .84f)

/**
 * Una guía, paso a paso, sobre la pantalla: todo se oscurece menos lo que
 * explica el paso, con un borde que brilla; el texto va al lado con más lugar.
 * «Escuchar» lee el paso; «Atrás» / «Siguiente»; «Listo» al final; «Cerrar»
 * o el botón de volver del teléfono la cierran cuando sea.
 */
@Composable
fun GuiaFoco(ctx: Contexto, guia: Guia, alCerrar: () -> Unit) {
    // Todo queda oscurecido, también detrás de las barras del teléfono.
    IconosBarras(arribaOscuro = true, abajoOscuro = true)
    var paso by remember(guia) { mutableIntStateOf(0) }
    val objetivos = LocalObjetivos.current
    // Los pasos que hablan de algo que no está en la pantalla se saltan. En una lista larga,
    // lo de arriba existe solo si se ve: antes de elegir, la lista vuelve al principio.
    var elegidos by remember(guia) { mutableStateOf<List<PasoGuia>?>(null) }
    LaunchedEffect(guia) {
        objetivos.lista?.let { lista -> while (lista.canScrollBackward) { lista.scrollBy(-100_000f); withFrameNanos { } } }
        withFrameNanos { }; withFrameNanos { }
        elegidos = guia.pasos.filter { q ->
            (q.siEsta == null || objetivos.hay(q.siEsta)) && (!q.soloSiEsta || q.objetivos.any(objetivos::hay))
        }.ifEmpty { guia.pasos }
    }
    val pasos = elegidos ?: run {
        // Mientras tanto, solo lo oscuro (dos cuadros): nada que leer todavía.
        Box(Modifier.fillMaxSize().background(OSCURO).pointerInput(Unit) { detectTapGestures { } })
        return
    }
    val p = pasos[paso]
    val c = colores
    BackHandler { if (paso > 0) paso -= 1 else alCerrar() }
    // Vista una vez, se recuerda (la general del B-1 no vuelve a salir primero).
    LaunchedEffect(guia) { guia.clave?.let(ctx.preferencias::marcarGuiaVista) }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "Cómo se usa: ${guia.nombre}" }
    ) {
        val alto = constraints.maxHeight.toFloat()
        val ancho = constraints.maxWidth.toFloat()
        val densidad = LocalDensity.current
        // Lo que se ilumina, seguido cuadro a cuadro: se mueve si la pantalla se desplaza.
        var lugar by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(guia, paso) {
            if (p.objetivos.isNotEmpty()) launch { traerALaVista(objetivos, p.objetivos, alto * .8f, with(densidad) { 88.dp.toPx() }) }
            while (true) { withFrameNanos { }; lugar = lugarDe(objetivos, p.objetivos) }
        }
        // De un paso al siguiente, el hueco viaja de una parte a la otra.
        val viaje = remember { Animatable(1f) }
        var desde by remember { mutableStateOf<Rect?>(null) }
        var dibujado by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(guia, paso) { desde = dibujado; viaje.snapTo(0f); viaje.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
        // Poco margen: con más, el recuadro toma un pedazo de lo que está al lado.
        val margen = with(densidad) { 6.dp.toPx() }
        // El texto y sus botones necesitan su lugar: lo que miden (con la letra elegida) y el
        // margen. Si lo iluminado es tan alto que no lo deja, se ilumina solo su comienzo.
        var altoTexto by remember { mutableStateOf(0) }
        val barras = maxOf(WindowInsets.statusBars.getTop(densidad), WindowInsets.navigationBars.getBottom(densidad))
        val lugarTexto = with(densidad) { maxOf(240.dp.toPx(), altoTexto + barras + Medidas.e5.toPx() + 12.dp.toPx()) }
        // Dentro de la pantalla: el borde que brilla se ve entero (el menú de abajo va pegado al borde).
        val borde = with(densidad) { 4.dp.toPx() }
        val r = lugar?.inflate(margen)?.intersect(Rect(borde, borde, ancho - borde, alto - borde))?.let { r ->
            when {
                alto - r.bottom >= r.top -> if (alto - r.bottom < lugarTexto) r.copy(bottom = maxOf(r.top + 4 * margen, alto - lugarTexto)) else r
                r.top < lugarTexto -> r.copy(top = minOf(r.bottom - 4 * margen, lugarTexto))
                else -> r
            }
        }
        val abajo = r == null || alto - r.bottom >= r.top
        val hueco = r?.let { desde?.let { d -> lerp(d, it, viaje.value) } ?: it }
        SideEffect { dibujado = hueco }
        val brillo by rememberInfiniteTransition(label = "brillo").animateFloat(
            .35f, .7f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "brillo"
        )
        val tono = c.marcaSuave

        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(OSCURO)
            if (hueco != null) {
                val esquina = CornerRadius(16.dp.toPx())
                drawRoundRect(Color.Black, hueco.topLeft, hueco.size, esquina, blendMode = BlendMode.Clear)
                val halo = 6.dp.toPx()
                drawRoundRect(tono.copy(alpha = brillo * .45f), hueco.topLeft - Offset(halo / 2, halo / 2), Size(hueco.width + halo, hueco.height + halo),
                    CornerRadius(esquina.x + halo / 2), style = Stroke(halo))
                drawRoundRect(CLARO, hueco.topLeft, hueco.size, esquina, style = Stroke(2.dp.toPx()))
            }
        }

        // El texto, del lado con más lugar; sin nada que iluminar, al centro y con su dibujo.
        // Al cambiar de paso, el de antes se va enseguida y el nuevo entra cuando el hueco
        // ya llegó a su lugar: no se lee un texto junto a la parte equivocada.
        // Cada texto lleva su guía y sus pasos: al pasar de la general a la del paso, el que
        // se va sigue siendo de la suya (no se busca su paso 4 en una guía de 3).
        AnimatedContent(
            targetState = Mostrado(guia, pasos, paso),
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 340)) + slideInVertically(tween(220, delayMillis = 340)) { it / 16 }) togetherWith
                    fadeOut(tween(100))
            },
            label = "paso"
        ) { (guiaMostrada, pasosMostrados, mostrado) ->
            val q = pasosMostrados[mostrado]
            val ultimoMostrado = mostrado == pasosMostrados.size - 1
            Column(
                Modifier.fillMaxSize()
                    .then(
                        when {
                            r == null -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            abajo -> Modifier.padding(top = with(densidad) { r.bottom.toDp() } + Medidas.e5).windowInsetsPadding(WindowInsets.navigationBars)
                            else -> Modifier.padding(bottom = with(densidad) { (alto - r.top).toDp() } + Medidas.e5).windowInsetsPadding(WindowInsets.statusBars)
                        }
                    )
                    .verticalScroll(rememberScrollState()).padding(horizontal = Medidas.e5),
                verticalArrangement = when { r == null -> Arrangement.Center; abajo -> Arrangement.Top; else -> Arrangement.Bottom }
            ) {
                // Detrás del texto, lo oscuro se cierra del todo, con bordes que se funden:
                // lo de abajo no se mezcla con las letras (sin tarjeta ni borde). Hacia lo
                // iluminado no se funde: taparía el borde de arriba o de abajo del hueco.
                val fundido = with(densidad) { 32.dp.toPx() }
                val sobre = if (r != null && abajo) 0f else fundido
                val bajo = if (r != null && !abajo) 0f else fundido
                Column(Modifier.widthIn(max = Medidas.anchoColumna).fillMaxWidth().align(Alignment.CenterHorizontally).onSizeChanged { altoTexto = it.height }.drawBehind {
                    val alto = size.height + sobre + bajo
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, sobre / alto to FONDO_TEXTO, 1 - bajo / alto to FONDO_TEXTO, 1f to Color.Transparent,
                            startY = -sobre, endY = size.height + bajo),
                        topLeft = Offset(-size.width, -sobre), size = Size(size.width * 3, alto)
                    )
                }) {
                    if (r == null) {
                        Box(Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = .1f)).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                            Icono(q.dibujo, 60.dp, tono)
                        }
                        Spacer(Modifier.height(Medidas.e4))
                    }
                    // De qué es la guía y cómo salir; luego qué hacer; luego por dónde va; abajo, avanzar.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cómo se usa: ${guiaMostrada.nombre}", style = tipos.chicoFuerte, color = tono, modifier = Modifier.weight(1f))
                        BotonFoco("Cerrar", alCerrar, chico = true)
                    }
                    Row(Modifier.padding(top = Medidas.e1), verticalAlignment = Alignment.CenterVertically) {
                        Text(q.titulo, style = tipos.titulo.copy(fontSize = tipos.titulo.fontSize * 1.2f), color = CLARO,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        BotonFoco("Escuchar", { ctx.decir("${q.titulo}. ${q.texto}") }, icono = "voz", chico = true)
                    }
                    Text(q.texto, style = tipos.texto, color = CLARO_SUAVE,
                        modifier = Modifier.padding(top = Medidas.e2).semantics { liveRegion = LiveRegionMode.Polite })
                    Row(Modifier.padding(top = Medidas.e4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pasosMostrados.forEachIndexed { i, _ ->
                            Box(Modifier.width(if (i == mostrado) 22.dp else 8.dp).height(8.dp).clip(CircleShape).background(if (i == mostrado) CLARO else Color.White.copy(alpha = .35f)))
                        }
                        Spacer(Modifier.width(Medidas.e2))
                        Text("${mostrado + 1} de ${pasosMostrados.size}", style = tipos.chico, color = CLARO_SUAVE)
                    }
                    // «Atrás» a la izquierda y avanzar ocupando el resto, como en toda la app. Al
                    // final, si la guía sigue con otra, se ofrece: como avanzar o debajo, aparte.
                    val despues = guiaMostrada.despues.takeIf { ultimoMostrado }
                    Row(Modifier.fillMaxWidth().padding(top = Medidas.e4), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                        if (mostrado > 0) BotonFoco("Atrás", { paso = mostrado - 1 })
                        if (despues?.principal == true) BotonFoco(despues.texto, { ctx.abrirGuia(despues.guia()) }, Modifier.weight(1f), principal = true)
                        else BotonFoco(if (ultimoMostrado) "Listo" else "Siguiente", { if (ultimoMostrado) alCerrar() else paso = mostrado + 1 },
                            Modifier.weight(1f), principal = true)
                    }
                    // Seguir con otra guía: un botón entero, con borde, que se vea (no un texto chico).
                    if (despues != null && !despues.principal) BotonFoco(despues.texto, { ctx.abrirGuia(despues.guia()) }, Modifier.fillMaxWidth().padding(top = Medidas.e3), icono = "ayuda")
                }
            }
        }
    }
}

/** Lo que muestra el texto de la guía en un momento: de qué guía, sus pasos y cuál. */
private data class Mostrado(val guia: Guia, val pasos: List<PasoGuia>, val paso: Int)

/** Un botón sobre lo oscuro: blanco relleno el principal; borde blanco los demás. */
@Composable
private fun BotonFoco(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, principal: Boolean = false, icono: String? = null, chico: Boolean = false) {
    val c = colores
    val forma = RoundedCornerShape(999.dp)
    val color = if (principal) c.marca else CLARO
    Row(
        modifier.defaultMinSize(minHeight = if (chico) 48.dp else 56.dp).clip(forma)
            .then(if (principal) Modifier.background(CLARO) else if (chico) Modifier else Modifier.border(1.5.dp, Color.White.copy(alpha = .7f), forma))
            // La capa al tocar, del color del texto: blanca sobre lo oscuro, verde sobre el blanco.
            .clickable(interactionSource = null, indication = AnilloFoco(ripple(color = color), color), role = Role.Button, onClick = onClick)
            .padding(horizontal = if (chico) Medidas.e3 else Medidas.e5),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icono(icono, 20.dp, color); Spacer(Modifier.width(6.dp)) }
        Text(texto, style = if (chico) tipos.chicoFuerte else tipos.textoFuerte, color = color)
    }
}
