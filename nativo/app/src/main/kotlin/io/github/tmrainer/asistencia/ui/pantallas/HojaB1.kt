// HojaB1.kt — cómo queda el B-1 en la hoja oficial (vista-previa.html, fase 4). Solo muestra: el mismo dibujo del PDF de referencia
// (dibujarHoja() sobre LienzoTrazos, en :nucleo), con los datos en las
// posiciones de PLANTILLA_B1. No imprime. Si hay más compras que renglones,
// van en una hoja más, como en el papel.
//
// No se portan la foto de fondo ni el panel para mover las cajas (son para el
// equipo, al calibrar la hoja física).
package io.github.tmrainer.asistencia.ui.pantallas

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.documentos.LienzoTrazos
import io.github.tmrainer.asistencia.nucleo.documentos.Trazo
import io.github.tmrainer.asistencia.nucleo.documentos.dibujarHoja
import io.github.tmrainer.asistencia.nucleo.documentos.hojasDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.componentes.EstadoVacio
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

private const val MM_POR_PT = 25.4 / 72

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
    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Vista previa del B-1", r?.periodo?.etiqueta?.trim()?.ifEmpty { null } ?: "En curso")
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            TituloSeccion("Cómo queda en la hoja")
            if (r == null) {
                EstadoVacio("boleta", "Todavía no hay un B-1", "Empieza a llenarlo en B-1.")
                return@Column
            }
            val texto = "Así caen tus datos en la hoja oficial del B-1. Es una copia de referencia: lo que falta queda en blanco, como en el papel."
            ParaLeer(texto)
            Pista(texto, Modifier.padding(bottom = Medidas.e2))
            Pista("Para guardarlo o mandarlo en PDF o Excel: en el B-1, paso «Revisar», «Exportar».", Modifier.padding(bottom = Medidas.e4))
            hojas.forEachIndexed { i, trazos ->
                if (hojas.size > 1) Text("Hoja ${i + 1} de ${hojas.size}", style = tipos.chicoFuerte, color = c.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e2))
                // Siempre en papel blanco, también en modo oscuro: es la hoja.
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(210f / 297f).shadow(2.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)).background(Color.White)
                ) { pintar(trazos) }
                Spacer(Modifier.height(Medidas.e4))
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
        is Trazo.Texto -> drawIntoCanvas { lienzo ->
            val p = if (t.negrita) negrita else normal
            p.color = android.graphics.Color.rgb((t.gris * 255).toInt(), (t.gris * 255).toInt(), (t.gris * 255).toInt())
            p.textSize = (t.pt * MM_POR_PT).toFloat() * k
            lienzo.nativeCanvas.drawText(t.texto, t.xMm.toFloat() * k, t.yBaseMm.toFloat() * k, p)
        }
    }
}
