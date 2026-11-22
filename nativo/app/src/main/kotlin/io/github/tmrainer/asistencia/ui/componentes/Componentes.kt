// Las piezas que se repiten en todas las pantallas. Ninguna calcula nada:
// reciben datos y dibujan.
package io.github.tmrainer.asistencia.ui.componentes

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.semantics.heading
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.aCentimos
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.aIso
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import io.github.tmrainer.asistencia.nucleo.validaciones.Hallazgo
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.AnilloFoco
import io.github.tmrainer.asistencia.ui.tema.anilloFoco
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TituloSeccion(texto: String, apoyo: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = if (apoyo) tipos.textoNegrita else tipos.seccion,
        color = if (apoyo) colores.tintaSuave else colores.titulo,
        modifier = modifier.padding(bottom = Medidas.e3).semantics { heading() }
    )
}

@Composable
fun Pista(texto: String, modifier: Modifier = Modifier) {
    Text(texto, style = tipos.pista, color = colores.tintaSuave, modifier = modifier)
}

/** Una sección: título, pista opcional y su contenido; espacio abajo. */
@Composable
fun Seccion(titulo: String, pista: String? = null, apoyo: Boolean = false, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = Medidas.e5)) {
        TituloSeccion(titulo, apoyo)
        if (pista != null) Pista(pista, Modifier.padding(bottom = Medidas.e3))
        contenido()
    }
}

enum class TipoBoton { PRINCIPAL, SECUNDARIO, PELIGRO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Boton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: TipoBoton = TipoBoton.SECUNDARIO,
    diminuto: Boolean = false,
    activo: Boolean = false,
    habilitado: Boolean = true,
    icono: String? = null
) {
    val c = colores
    val relleno = tipo == TipoBoton.PRINCIPAL || activo
    val fondo = if (relleno) c.acento else Color.Transparent
    val textoColor = when {
        relleno -> Color.White
        tipo == TipoBoton.PELIGRO -> c.error
        else -> c.acentoTexto
    }
    val bordeColor = when {
        relleno -> c.acento
        tipo == TipoBoton.PELIGRO -> c.error
        else -> c.contorno
    }
    val fuente = remember { MutableInteractionSource() }
    // En el relleno verde la capa al tocar oscurece: la blanca de Material bajaba el
    // contraste del texto blanco (4.0 en oscuro).
    CompositionLocalProvider(LocalRippleConfiguration provides if (relleno) RippleConfiguration(color = Color.Black) else LocalRippleConfiguration.current) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        // 56 dp los normales: 48 es el mínimo de Android, y a quien recién empieza o ve
        // menos le cuesta menos acertar uno más grande. Los chicos, 48 dp como mínimo.
        modifier = modifier.anilloFoco(fuente).defaultMinSize(minHeight = if (diminuto) 48.dp else 56.dp),
        interactionSource = fuente,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.5.dp, if (habilitado) bordeColor else bordeColor.copy(alpha = .45f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = fondo, contentColor = textoColor,
            disabledContainerColor = fondo.copy(alpha = if (relleno) .45f else 0f), disabledContentColor = textoColor.copy(alpha = .45f)
        ),
        contentPadding = PaddingValues(horizontal = if (diminuto) Medidas.e4 else Medidas.e5, vertical = 0.dp)
    ) {
        if (icono != null) { Icono(icono, 22.dp); Spacer(Modifier.width(Medidas.e2)) }
        Text(texto, style = if (diminuto) tipos.chicoFuerte else tipos.textoNegrita, textAlign = TextAlign.Center)
    }
    }
}

/** Botón de texto subrayado. */
@Composable
fun Enlace(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fuente = remember { MutableInteractionSource() }
    TextButton(onClick = onClick, modifier = modifier.anilloFoco(fuente), interactionSource = fuente, contentPadding = PaddingValues(horizontal = Medidas.e1, vertical = 6.dp)) {
        Text(texto, style = tipos.chicoFuerte, color = colores.acentoTexto)
    }
}

/** Botón flotante: una acción principal por pantalla, siempre en el mismo lugar. */
@Composable
fun BotonFlotante(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = colores
    Row(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(Medidas.rL))
            .clip(RoundedCornerShape(Medidas.rL))
            .background(c.acento)
            // Al tocarlo se aclara (blanco), como un botón relleno: el negro no se notaría.
            .clickable(interactionSource = null, indication = AnilloFoco(ripple(color = Color.White)), role = Role.Button, onClick = onClick)
            .height(56.dp)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icono("mas", 26.dp, Color.White)
        Spacer(Modifier.width(10.dp))
        Text(texto, style = tipos.textoNegrita, color = Color.White)
    }
}

/** Fila de botones con espacio entre ellos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Acciones(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    // 12 dp entre botones: con dedos menos precisos, no se toca el de al lado.
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3), verticalArrangement = Arrangement.spacedBy(Medidas.e3)) { contenido() }
}

/**
 * Campo de texto con su rótulo dentro (el `label` de Material, que TalkBack
 * lee al tocar el campo), pista abajo y, si hay, el error al lado (nunca solo
 * color). `soloDigitos` limpia lo que no sea cifra.
 */
@Composable
fun Campo(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    pista: String? = null,
    error: String? = null,
    aviso: String? = null,
    placeholder: String? = null,
    obligatorio: Boolean = false,
    soloDigitos: Boolean = false,
    decimal: Boolean = false,
    maxLargo: Int = Int.MAX_VALUE,
    habilitado: Boolean = true,
    unaLinea: Boolean = true,
    capitalizar: Boolean = false,
    correo: Boolean = false
) {
    val c = colores
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = { nuevo ->
                var v = if (soloDigitos) nuevo.filter { it.isDigit() } else nuevo
                if (v.length > maxLargo) v = v.take(maxLargo)
                alCambiar(v)
            },
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            enabled = habilitado,
            label = {
                Text(buildAnnotatedString {
                    append(etiqueta)
                    if (obligatorio) withStyle(SpanStyle(color = c.error, fontWeight = FontWeight.Bold)) { append(" *") }
                })
            },
            singleLine = unaLinea,
            textStyle = tipos.texto.copy(color = c.tinta),
            placeholder = if (placeholder != null) ({ Text(placeholder, style = tipos.texto, color = c.tintaSuave) }) else null,
            isError = error != null,
            shape = RoundedCornerShape(Medidas.rS),
            keyboardOptions = KeyboardOptions(
                // Nombres y apellidos: el teclado pone la mayúscula de cada palabra.
                capitalization = if (capitalizar) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                keyboardType = when {
                    soloDigitos -> KeyboardType.Number
                    decimal -> KeyboardType.Decimal
                    correo -> KeyboardType.Email
                    else -> KeyboardType.Text
                }
            ),
            colors = coloresCampo(aviso != null)
        )
        if (error != null) Text(error, style = tipos.chico, color = c.error, modifier = Modifier.padding(top = 2.dp))
        else if (aviso != null) Text(aviso, style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = 2.dp))
        if (pista != null) Text(pista, style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun coloresCampo(conAviso: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = if (conAviso) colores.aviso else colores.acentoTexto,
    unfocusedBorderColor = if (conAviso) colores.aviso else colores.contorno,
    errorBorderColor = colores.error,
    focusedContainerColor = colores.superficie, unfocusedContainerColor = colores.superficie,
    errorContainerColor = colores.superficie, disabledContainerColor = colores.superficie2,
    cursorColor = colores.acentoTexto, errorCursorColor = colores.error,
    focusedLabelColor = if (conAviso) colores.aviso else colores.acentoTexto, unfocusedLabelColor = colores.tintaSuave,
    errorLabelColor = colores.error, disabledLabelColor = colores.tintaSuave,
    focusedTextColor = colores.tinta, unfocusedTextColor = colores.tinta, disabledTextColor = colores.tintaSuave
)

@Composable
fun Rotulo(etiqueta: String, obligatorio: Boolean = false) {
    Row(Modifier.padding(bottom = 6.dp)) {
        Text(etiqueta, style = tipos.chicoFuerte, color = colores.tinta)
        if (obligatorio) Text(" *", style = tipos.chicoNegrita, color = colores.error)
    }
}

/**
 * Monto en soles: el modelo guarda céntimos enteros; aquí se escribe «12.50».
 * Un cambio que llega de afuera (una sugerencia) reemplaza el texto solo si
 * no es lo que ya se está escribiendo.
 */
@Composable
fun CampoSoles(
    etiqueta: String,
    valorCent: Long?,
    alCambiar: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    pista: String? = null,
    error: String? = null,
    aviso: String? = null,
    obligatorio: Boolean = false,
    habilitado: Boolean = true
) {
    var texto by remember { mutableStateOf(formatearSoles(valorCent)) }
    LaunchedEffect(valorCent) { if (aCentimos(texto) != valorCent) texto = formatearSoles(valorCent) }
    Campo(etiqueta, texto, { texto = it; alCambiar(aCentimos(it)) }, modifier, pista, error, aviso,
        placeholder = "0.00", obligatorio = obligatorio, decimal = true, habilitado = habilitado)
}

/**
 * Fecha ISO en el modelo; se elige con el calendario del teléfono y se ve como DD-MM-AA.
 * [desde] y [hasta] (ISO, vacío = sin límite): los días de afuera no se pueden elegir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    etiqueta: String,
    valorIso: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    pista: String? = null,
    error: String? = null,
    aviso: String? = null,
    obligatorio: Boolean = false,
    desde: String = "",
    hasta: String = ""
) {
    val c = colores
    var abierto by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Rotulo(etiqueta, obligatorio)
        val bordeColor = when { error != null -> c.error; aviso != null -> c.aviso; else -> c.contorno }
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(Medidas.rS))
                .background(c.superficie)
                .border(1.dp, bordeColor, RoundedCornerShape(Medidas.rS))
                .clickable(role = Role.Button) { abierto = true }
                .semantics { contentDescription = "$etiqueta: ${if (valorIso.isEmpty()) "sin fecha" else aDdMmAa(valorIso)}" }
                .padding(horizontal = Medidas.e3, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (valorIso.isEmpty()) "Elegir fecha" else aDdMmAa(valorIso),
                style = tipos.texto, color = if (valorIso.isEmpty()) c.tintaSuave else c.tinta,
                modifier = Modifier.weight(1f)
            )
            Icono("calendario", 20.dp, c.acentoTexto)
        }
        if (error != null) Text(error, style = tipos.chico, color = c.error, modifier = Modifier.padding(top = 2.dp))
        else if (aviso != null) Text(aviso, style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = 2.dp))
        if (pista != null) Text(pista, style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 4.dp))
    }
    if (abierto) {
        fun aMs(iso: String) = aFecha(iso)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val min = aFecha(desde)
        val max = aFecha(hasta)
        val inicial = aMs(valorIso)
        val estadoFecha = rememberDatePickerState(
            initialSelectedDateMillis = inicial,
            initialDisplayedMonthMillis = inicial ?: aMs(desde) ?: aMs(hasta),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return (min == null || d >= min) && (max == null || d <= max)
                }
                override fun isSelectableYear(year: Int) = (min == null || year >= min.year) && (max == null || year <= max.year)
            }
        )
        val rango = when {
            min != null && max != null -> "Puedes elegir del ${aDdMmAa(desde)} al ${aDdMmAa(hasta)}."
            min != null -> "Puedes elegir desde el ${aDdMmAa(desde)}."
            max != null -> "Puedes elegir hasta el ${aDdMmAa(hasta)}."
            else -> null
        }
        // El verde de Material (primary) en oscuro no se lee sobre el fondo del calendario:
        // los botones, «hoy» y el campo para escribirla van con el verde de los textos.
        val coloresCalendario = DatePickerDefaults.colors(
            todayContentColor = c.acentoTexto, todayDateBorderColor = c.acentoTexto,
            dateTextFieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.acentoTexto, focusedLabelColor = c.acentoTexto, cursorColor = c.acentoTexto)
        )
        val coloresBoton = ButtonDefaults.textButtonColors(contentColor = c.acentoTexto)
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            colors = coloresCalendario,
            confirmButton = {
                // Sin un día válido elegido no hay nada que usar; para borrarla está «Quitar la fecha».
                TextButton(onClick = {
                    val ms = estadoFecha.selectedDateMillis ?: return@TextButton
                    alCambiar(aIso(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    abierto = false
                }, enabled = estadoFecha.selectedDateMillis != null, colors = coloresBoton) { Text("Usar esta fecha", style = tipos.textoNegrita) }
            },
            dismissButton = {
                if (valorIso.isNotEmpty()) TextButton(onClick = { alCambiar(""); abierto = false }, colors = coloresBoton) { Text("Quitar la fecha", style = tipos.textoFuerte) }
                TextButton(onClick = { abierto = false }, colors = coloresBoton) { Text("Cancelar", style = tipos.textoFuerte) }
            }
        ) {
            DatePicker(
                state = estadoFecha,
                colors = coloresCalendario,
                showModeToggle = true,
                title = {
                    val margen = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    // Sin día elegido, «Usar esta fecha» está apagado: el título dice por qué.
                    val titulo = listOfNotNull(rango, if (estadoFecha.selectedDateMillis == null) "Elige un día." else null).joinToString(" ")
                    if (titulo.isNotEmpty()) Text(titulo, style = tipos.textoFuerte, modifier = margen)
                    else DatePickerDefaults.DatePickerTitle(estadoFecha.displayMode, margen)
                }
            )
        }
    }
}

/** Un dato de hoy en ISO, para los campos de fecha que nacen vacíos. */
fun hoyLocalIso(): String = aIso(LocalDate.now())

/**
 * Opciones en forma de ficha: se ven todas de una
 * vez y se elige con un toque. Para una sola elección o varias.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Opciones(
    opciones: List<Pair<T, String>>,
    elegida: (T) -> Boolean,
    alElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e2), verticalArrangement = Arrangement.spacedBy(Medidas.e2)) {
        for ((valor, texto) in opciones) Ficha(texto, elegida(valor), { alElegir(valor) }, habilitado = habilitado)
    }
}

/**
 * Una ficha: borde contorno, y rellena de «selección» cuando está elegida.
 * Es una opción de un grupo (TalkBack dice si está elegida); con [comoBoton],
 * un botón que se ve como ficha (abre un menú).
 */
@Composable
fun Ficha(texto: String, elegida: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, minAlto: Dp = 48.dp, comoBoton: Boolean = false) {
    val c = colores
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (elegida) c.seleccion else c.superficie)
            .border(if (elegida) 2.dp else 1.dp, if (elegida) c.acentoTexto else c.contorno, RoundedCornerShape(999.dp))
            .then(
                if (comoBoton) Modifier.clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
                else Modifier.selectable(selected = elegida, enabled = habilitado, role = Role.RadioButton, onClick = onClick)
            )
            .defaultMinSize(minHeight = minAlto)
            .padding(horizontal = Medidas.e4),
        contentAlignment = Alignment.Center
    ) {
        // Elegida no es solo un color: lleva ✓ y borde grueso (el relleno solo da 1.3:1).
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (elegida) { Icono("ok", 18.dp, if (habilitado) c.acentoTexto else c.tintaSuave); Spacer(Modifier.width(6.dp)) }
            Text(texto, style = if (elegida) tipos.textoFuerte else tipos.texto, color = if (habilitado) c.tinta else c.tintaSuave)
        }
    }
}

/** Rótulo de un grupo de opciones (legend). */
@Composable
fun Leyenda(texto: String, obligatorio: Boolean = false) = Rotulo(texto, obligatorio)

/** «Etiqueta: valor», con el valor en negrita. */
@Composable
fun Chip(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    val c = colores
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.superficie2)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = Medidas.e3, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$etiqueta: ", style = tipos.chico, color = c.tinta)
        Text(valor, style = tipos.chicoNegrita, color = c.titulo)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e2), verticalArrangement = Arrangement.spacedBy(Medidas.e2)) { contenido() }
}

/**
 * Una barra que se estira: cerrada es una sola fila con [titulo] y lo elegido
 * ([resumen]); al tocarla se abre hacia abajo y muestra el [contenido] (las
 * opciones). Para que muchas fichas no llenen la pantalla.
 */
@Composable
fun BarraDesplegable(
    titulo: String,
    resumen: String,
    abierta: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: String? = null,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val c = colores
    val giro by animateFloatAsState(if (abierta) -90f else 90f, label = "flecha")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Medidas.rS))
            .background(c.superficie)
            .border(if (abierta) 2.dp else 1.dp, if (abierta) c.acentoTexto else c.contorno, RoundedCornerShape(Medidas.rS))
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = alTocar)
                .semantics { stateDescription = if (abierta) "Abierta" else "Cerrada" }
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = Medidas.e4, vertical = Medidas.e2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icono != null) { Icono(icono, 20.dp, c.acentoTexto); Spacer(Modifier.width(Medidas.e3)) }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = tipos.chico, color = c.tintaSuave)
                Text(resumen, style = tipos.textoFuerte, color = c.titulo)
            }
            Icono("flecha", 20.dp, c.tintaSuave, Modifier.rotate(giro))
        }
        if (abierta) {
            HorizontalDivider(color = c.contorno)
            Column(Modifier.padding(Medidas.e4), content = contenido)
        }
    }
}

// FALTA (azul): a algo le falta un dato. Distinto del amarillo de «revisar»: el marrón
// de antes era el mismo tono que el amarillo, y con daltonismo se confundía con el rojo.
enum class TonoPastilla { OK, ERROR, ADVERTENCIA, FALTA, NEUTRA }

/** Con [onClick], un botón de 48 dp con flechita hacia abajo: abre un menú para cambiar el estado. */
@Composable
fun Pastilla(texto: String, tono: TonoPastilla, icono: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = colores
    val (color, fondo) = when (tono) {
        TonoPastilla.OK -> c.ok to c.okFondo
        TonoPastilla.ERROR -> c.error to c.errorFondo
        TonoPastilla.ADVERTENCIA -> c.aviso to c.avisoFondo
        TonoPastilla.FALTA -> c.azul to c.azulFondo
        TonoPastilla.NEUTRA -> c.acentoTexto to c.superficie2
    }
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(fondo)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick).defaultMinSize(minHeight = 48.dp) else Modifier.defaultMinSize(minHeight = 32.dp))
            .padding(horizontal = Medidas.e3, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icono != null) { Icono(icono, 14.dp, color); Spacer(Modifier.width(6.dp)) }
        Text(texto, style = tipos.chicoFuerte, color = color)
        if (onClick != null) { Spacer(Modifier.width(4.dp)); Icono("flecha", 16.dp, color, Modifier.rotate(90f)) }
    }
}

/** Una lista es UNA superficie con filas separadas por una línea fina. */
@Composable
fun Superficie(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(Medidas.rM))
            .clip(RoundedCornerShape(Medidas.rM))
            .background(colores.superficie)
    ) { contenido() }
}

/** Fondo suave sin borde (superficie-2) para resaltar un bloque. */
@Composable
fun BloqueSuave(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(colores.superficie2).padding(Medidas.e3, Medidas.e3, Medidas.e4, Medidas.e3)) { contenido() }
}

@Composable
fun Separador() = HorizontalDivider(color = colores.borde, thickness = 1.dp)

/** Iniciales en círculo, del color del grupo de edad (y la palabra sigue al lado). */
@Composable
fun Avatar(persona: Afiliado, grupo: String?, modifier: Modifier = Modifier) {
    val c = colores
    val letra = { t: String -> t.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "" }
    val iniciales = (letra(persona.nombres) + letra(persona.apellidoPaterno)).ifEmpty { "?" }
    val fondo = when (grupo) {
        "nino" -> c.gNino; "adolescente" -> c.gAdolescente; "adulto" -> c.gAdulto; "adulto_mayor" -> c.gMayor
        else -> c.gSin
    }
    Box(modifier.size(44.dp).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Text(iniciales, style = tipos.textoNegrita, color = Color.White)
    }
}

/** Pantalla vacía: un dibujo grande, qué es este lugar y, si hay, un solo botón. */
@Composable
fun EstadoVacio(icono: String, titulo: String, texto: String? = null, boton: Pair<String, () -> Unit>? = null, logrado: Boolean = false, modifier: Modifier = Modifier) {
    val c = colores
    ParaLeer(listOfNotNull(titulo, texto).joinToString(". "))
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e6, Medidas.e6, Medidas.e6, Medidas.e5),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(if (logrado) c.acento else c.superficie), contentAlignment = Alignment.Center) {
            Icono(icono, 40.dp, if (logrado) Color.White else c.acentoTexto)
        }
        Spacer(Modifier.height(Medidas.e3))
        Text(titulo, style = tipos.seccion, color = c.tinta, textAlign = TextAlign.Center)
        if (texto != null) Text(texto, style = tipos.pista, color = c.tintaSuave, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Medidas.e2))
        if (boton != null) { Spacer(Modifier.height(Medidas.e3)); Boton(boton.first, boton.second, tipo = TipoBoton.PRINCIPAL) }
    }
}

/** Franja de aviso del sistema (almacenamiento, no se guardó). */
@Composable
fun AvisoSistema(texto: String, modifier: Modifier = Modifier, boton: Pair<String, () -> Unit>? = null) {
    val c = colores
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.avisoFondo).padding(Medidas.e4, Medidas.e3)) {
        Row(verticalAlignment = Alignment.Top) {
            Icono("aviso", 15.dp, c.aviso, Modifier.padding(top = 3.dp))
            Spacer(Modifier.width(6.dp))
            Text(texto, style = tipos.pista, color = c.tinta)
        }
        if (boton != null) Boton(boton.first, boton.second, Modifier.padding(top = Medidas.e2), diminuto = true)
    }
}

/** Fondo suave de un estado (ok, aviso, error) con texto y, si hay, un botón debajo: así el texto no queda apretado. */
@Composable
fun FranjaEstado(texto: String, tono: TonoPastilla, modifier: Modifier = Modifier, icono: String? = null, boton: Pair<String, () -> Unit>? = null) {
    val c = colores
    val fondo = when (tono) { TonoPastilla.OK -> c.okFondo; TonoPastilla.ERROR -> c.errorFondo; TonoPastilla.ADVERTENCIA -> c.avisoFondo; TonoPastilla.FALTA -> c.azulFondo; TonoPastilla.NEUTRA -> c.superficie2 }
    val colorIcono = when (tono) { TonoPastilla.OK -> c.ok; TonoPastilla.ERROR -> c.error; TonoPastilla.ADVERTENCIA -> c.aviso; TonoPastilla.FALTA -> c.azul; TonoPastilla.NEUTRA -> c.tintaSuave }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(fondo).padding(Medidas.e4, Medidas.e3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icono != null) { Icono(icono, 17.dp, colorIcono); Spacer(Modifier.width(Medidas.e2)) }
            Text(texto, style = tipos.pista, color = c.tinta, modifier = Modifier.weight(1f))
        }
        if (boton != null) Boton(boton.first, boton.second, Modifier.padding(top = Medidas.e3), diminuto = true)
    }
}

/**
 * Panel de revisión: lo que falta se cuenta aparte y en
 * tono neutro («Faltan 12 datos»); «por corregir» es lo mal escrito. Sin
 * hallazgos no se muestra nada. El detalle se pliega; de a 50.
 */
@Composable
fun PanelHallazgos(hallazgos: List<Hallazgo>, modifier: Modifier = Modifier, alIrA: ((String) -> Unit)? = null) {
    if (hallazgos.isEmpty()) return
    val c = colores
    val faltan = hallazgos.filter { it.severidad == Severidad.ERROR && it.falta }
    val errores = hallazgos.filter { it.severidad == Severidad.ERROR && !it.falta }
    val avisos = hallazgos.filter { it.severidad == Severidad.ADVERTENCIA }
    var abierto by remember { mutableStateOf(false) }
    var mostrados by remember { mutableStateOf(50) }
    // El margen va afuera: lo que se ilumina en la guía es el panel, sin su margen.
    Superficie(Modifier.padding(bottom = Medidas.e4).then(modifier)) {
        Column(Modifier.padding(Medidas.e4, Medidas.e3)) {
            Chips {
                if (faltan.isNotEmpty()) Pastilla(if (faltan.size == 1) "Falta 1 dato" else "Faltan ${faltan.size} datos", TonoPastilla.FALTA, "lapiz")
                if (errores.isNotEmpty()) Pastilla("${errores.size} por corregir", TonoPastilla.ERROR, "error")
                if (avisos.isNotEmpty()) Pastilla("${avisos.size} por revisar", TonoPastilla.ADVERTENCIA, "aviso")
            }
            Enlace(if (abierto) "Ocultar el detalle" else "Ver el detalle", { abierto = !abierto }, Modifier.padding(top = Medidas.e1))
            if (abierto) {
                val todos = errores + faltan + avisos
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (h in todos.take(mostrados)) {
                        val (fondo, colorIcono, tipo, icono) = when {
                            h.severidad == Severidad.ADVERTENCIA -> listOf(c.avisoFondo, c.aviso, "Revisar", "aviso")
                            h.falta -> listOf(c.azulFondo, c.azul, "Falta", "lapiz")
                            else -> listOf(c.errorFondo, c.error, "Error", "error")
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rS)).background(fondo as Color)
                                .clickable(enabled = alIrA != null) { alIrA?.invoke(h.campo) }
                                .padding(Medidas.e3, Medidas.e2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icono(icono as String, 15.dp, colorIcono as Color)
                            Spacer(Modifier.width(Medidas.e2))
                            Text(tipo as String, style = tipos.chicoNegrita, color = c.tinta)
                            Spacer(Modifier.width(Medidas.e2))
                            Text(h.mensaje, style = tipos.chico, color = c.tinta, modifier = Modifier.weight(1f))
                        }
                    }
                    if (todos.size > mostrados) Enlace("Ver ${minOf(50, todos.size - mostrados)} más", { mostrados += 50 })
                }
            }
        }
    }
}

/** El texto de abajo de cada pantalla: si lo último quedó guardado. */
@Composable
fun EstadoGuardado(guardado: io.github.tmrainer.asistencia.nucleo.estado.Guardado, enReposo: String, modifier: Modifier = Modifier) {
    val c = colores
    val fallo = guardado == io.github.tmrainer.asistencia.nucleo.estado.Guardado.FALLO
    Text(
        when (guardado) {
            io.github.tmrainer.asistencia.nucleo.estado.Guardado.FALLO -> "NO SE GUARDÓ lo último. Guarda una copia ahora."
            io.github.tmrainer.asistencia.nucleo.estado.Guardado.PENDIENTE -> enReposo
            io.github.tmrainer.asistencia.nucleo.estado.Guardado.GUARDADO -> "Guardado en este equipo."
        },
        style = if (fallo) tipos.chicoNegrita else tipos.chico,
        color = if (fallo) c.error else c.tintaSuave,
        modifier = modifier.padding(top = Medidas.e4, start = 2.dp)
    )
}

/**
 * Los íconos de las barras del teléfono (hora, batería, gestos): claros sobre lo
 * oscuro, oscuros sobre lo claro. Los decide lo que hay detrás en la app, no el modo
 * del teléfono. Al salir, vuelven a como estaban (la pantalla de abajo los había puesto).
 */
@Composable
fun IconosBarras(arribaOscuro: Boolean, abajoOscuro: Boolean) {
    val vista = LocalView.current
    DisposableEffect(arribaOscuro, abajoOscuro) {
        var contexto = vista.context
        while (contexto is ContextWrapper && contexto !is Activity) contexto = contexto.baseContext
        val ventana = (contexto as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val control = WindowCompat.getInsetsController(ventana, vista)
        val arribaAntes = control.isAppearanceLightStatusBars
        val abajoAntes = control.isAppearanceLightNavigationBars
        control.isAppearanceLightStatusBars = !arribaOscuro
        control.isAppearanceLightNavigationBars = !abajoOscuro
        onDispose {
            control.isAppearanceLightStatusBars = arribaAntes
            control.isAppearanceLightNavigationBars = abajoAntes
        }
    }
}
