// Cómo queda el B-1 en la hoja oficial. Solo muestra: el mismo dibujo del PDF
// (dibujarHoja() sobre LienzoTrazos, en :nucleo), con los datos en las posiciones de
// PLANTILLA_B1. No imprime. Si hay más compras que renglones, van en una hoja más.
// Sin foto de fondo ni panel para mover las cajas: son para calibrar la hoja física.
// Se puede acercar (dos dedos, doble toque o «Acercar») para leerla en el teléfono sin exportarla.
package io.github.tmrainer.asistencia.ui.pantallas

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.documentos.LienzoTrazos
import io.github.tmrainer.asistencia.nucleo.documentos.Trazo
import io.github.tmrainer.asistencia.nucleo.documentos.dibujarHoja
import io.github.tmrainer.asistencia.nucleo.documentos.hojasDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.GUIA_HOJA
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.EstadoVacio
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlin.math.roundToInt

private const val MM_POR_PT = 25.4 / 72
private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 4f
private const val PASO_ZOOM = 0.5f

/**
 * Con dos dedos, acercar o alejar: [alCambiar] recibe cuánto cambió. Se mira antes que el
 * desplazamiento (pase Initial) para que los dos dedos no muevan la hoja; con uno, se desplaza igual.
 */
private fun Modifier.pellizco(alCambiar: (Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.count { it.pressed } >= 2) {
                val z = ev.calculateZoom()
                if (z != 1f) alCambiar(z)
                ev.changes.forEach { it.consume() }
            }
        } while (ev.changes.any { it.pressed })
    }
}

@Composable
fun PantallaHojaB1(ctx: Contexto, rendicionId: String?) {
    val rendiciones by ctx.estado.rendiciones.collectAsStateWithLifecycle()
    val r = rendiciones.firstOrNull { it.id == rendicionId } ?: rendiciones.firstOrNull()
    val c = colores
    // El dibujo de cada hoja, como datos: se arma una vez por cambio del B-1.
    val hojas = remember(r) {
        if (r == null) emptyList() else {
            val todas = hojasDelB1(r)
            val sello = selloAhora()
            todas.mapIndexed { i, h -> LienzoTrazos().also { dibujarHoja(it, h, sello, i + 1, todas.size) }.trazos.toList() }
        }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Vista previa del B-1", r?.periodo?.etiqueta?.trim()?.ifEmpty { null } ?: "En curso", guia = GUIA_HOJA)
        Column(
            Modifier.fillMaxWidth().pellizco { z -> zoom = (zoom * z).coerceIn(ZOOM_MIN, ZOOM_MAX) }
                .verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            TituloSeccion("Cómo queda en la hoja")
            if (r == null) {
                EstadoVacio("boleta", "Todavía no hay un B-1", "Empieza a llenarlo en B-1.")
                return@Column
            }
            val texto = "Así quedan tus datos en la hoja del B-1. Lo que falta queda en blanco."
            ParaLeer(texto)
            Pista(texto, Modifier.padding(bottom = Medidas.e2))
            Pista("El PDF y el Excel para entregar se arman en el B-1, paso «Revisar».", Modifier.padding(bottom = Medidas.e2))
            Pista("Para leerla mejor, separa dos dedos sobre la hoja, tócala dos veces o usa «Acercar».", Modifier.padding(bottom = Medidas.e3))
            Row(Modifier.fillMaxWidth().padding(bottom = Medidas.e3).objetivoGuia("hoja.zoom"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                Boton("Alejar", { zoom = (zoom - PASO_ZOOM).coerceAtLeast(ZOOM_MIN) }, diminuto = true, icono = "menos", habilitado = zoom > ZOOM_MIN)
                Text("${(zoom * 100).roundToInt()} %", style = tipos.textoFuerte, color = c.tinta, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Boton("Acercar", { zoom = (zoom + PASO_ZOOM).coerceAtMost(ZOOM_MAX) }, diminuto = true, icono = "mas", habilitado = zoom < ZOOM_MAX)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ancho = maxWidth
                // Acercada, la hoja es más ancha que la pantalla: se corre de lado.
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    hojas.forEachIndexed { i, trazos ->
                        if (hojas.size > 1) Text("Hoja ${i + 1} de ${hojas.size}", style = tipos.chicoFuerte, color = c.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e2))
                        // Siempre en papel blanco, también en modo oscuro: es la hoja.
                        Canvas(
                            Modifier.then(if (i == 0) Modifier.objetivoGuia("hoja.primera") else Modifier).width(ancho * zoom).aspectRatio(210f / 297f).shadow(2.dp, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp)).background(Color.White)
                                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = if (zoom > ZOOM_MIN) ZOOM_MIN else 2f }) }
                        ) { pintar(trazos) }
                        Spacer(Modifier.height(Medidas.e4))
                    }
                }
            }
        }
    }
}

/** Pinta los trazos de una hoja A4 (en mm) en el ancho del lienzo. */
private fun DrawScope.pintar(trazos: List<Trazo>) {
    val k = size.width / 210f      // píxeles por mm
    fun color(gris: Double) = Color(gris.toFloat(), gris.toFloat(), gris.toFloat())
    fun puntos(si: Boolean) = if (si) PathEffect.dashPathEffect(floatArrayOf(0.21f * k, 0.64f * k)) else null
    val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
    val negrita = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
    for (t in trazos) when (t) {
        is Trazo.Linea -> drawLine(
            color(t.gris), Offset(t.x1.toFloat() * k, t.y1.toFloat() * k), Offset(t.x2.toFloat() * k, t.y2.toFloat() * k),
            strokeWidth = maxOf(1f, t.grosorMm.toFloat() * k), pathEffect = puntos(t.punteada)
        )
        is Trazo.Caja -> drawRect(
            color(t.gris), Offset(t.xMm.toFloat() * k, t.yMm.toFloat() * k), Size(t.anchoMm.toFloat() * k, t.altoMm.toFloat() * k),
            style = Stroke(maxOf(1f, t.grosorMm.toFloat() * k), pathEffect = puntos(t.punteada))
        )
        is Trazo.Relleno -> drawRect(color(t.gris), Offset(t.xMm.toFloat() * k, t.yMm.toFloat() * k), Size(t.anchoMm.toFloat() * k, t.altoMm.toFloat() * k))
        is Trazo.Imagen -> {}  // el B-1 no lleva fotos
        is Trazo.Texto -> drawIntoCanvas { lienzo ->
            val p = if (t.negrita) negrita else normal
            p.color = android.graphics.Color.rgb((t.gris * 255).toInt(), (t.gris * 255).toInt(), (t.gris * 255).toInt())
            p.textSize = (t.pt * MM_POR_PT).toFloat() * k
            lienzo.nativeCanvas.drawText(t.texto, t.xMm.toFloat() * k, t.yBaseMm.toFloat() * k, p)
        }
    }
}
