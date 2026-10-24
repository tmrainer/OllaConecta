// Iconos.kt — los mismos trazos SVG de ui.js (TRAZOS), dibujados como
// vectores: nítidos a cualquier tamaño y siempre acompañados de una palabra.
// Trazo de 2,2 con puntas redondas sobre una caja de 24.
package io.github.tmrainer.asistencia.ui.componentes

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val TRAZOS = mapOf(
    "ok" to "M20 6 9 17l-5-5",
    "error" to "M18 6 6 18M6 6l12 12",
    "aviso" to "M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
    "reloj" to "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2",
    "flecha" to "m9 18 6-6-6-6",
    "casa" to "M3 11l9-7 9 7M5 10v10h14V10M10 20v-6h4v6",
    "lista" to "M4 6l2 2 3-3M4 13l2 2 3-3M12 7h8M12 14h8M5 20h1M12 20h8",
    "personas" to "M12.5 8a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0M2.5 20c.5-3.5 3-5.5 6.5-5.5s6 2 6.5 5.5M19.5 9a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0M17 14c2.5 0 4 1.5 4.5 4",
    "personaMas" to "M13.5 8a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0M3.5 20c.5-3.5 3-5.5 6.5-5.5 1.5 0 2.8.4 3.8 1M19 13v6M16 16h6",
    "boleta" to "M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3",
    "calendario" to "M5.5 5h13a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3.5 10h17M8 3v4M16 3v4M8 14h2M12 14h2",
    "atras" to "M15 5l-7 7 7 7",
    "puntos" to "M12 5h.01M12 12h.01M12 19h.01",
    "mas" to "M12 5v14M5 12h14",
    "voz" to "M4 9v6h4l5 4V5L8 9zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12",
    "escudo" to "M12 3l8 3v6c0 4.5-3.5 7.5-8 9-4.5-1.5-8-4.5-8-9V6zM8.5 12l2.5 2.5 4.5-5",
    "ayuda" to "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6M12 17.5h.01",
    "letra" to "M4 19l5-14 5 14M6 14h6M15 19l3-8 3 8M16.2 16h3.6",
    "buscar" to "M17.5 11a6.5 6.5 0 1 1-13 0 6.5 6.5 0 0 1 13 0M16 16l4.5 4.5",
    "nube" to "M7 18h10a4 4 0 0 0 .6-8A6 6 0 0 0 6 11.5 3.3 3.3 0 0 0 7 18z",
    "archivo" to "M14 3H6v18h12V7zM14 3v4h4M9 13h6M9 17h6",
    "camara" to "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3zM15 13a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
    "ajustes" to "M15.5 12a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    "lapiz" to "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"
)

private val vectores = HashMap<String, ImageVector>()

/** El vector de un ícono por nombre; uno desconocido da el de «aviso», como en la web. */
fun vectorIcono(nombre: String): ImageVector = vectores.getOrPut(nombre) {
    val d = TRAZOS[nombre] ?: TRAZOS.getValue("aviso")
    ImageVector.Builder(name = nombre, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        .build()
}

@Composable
fun Icono(nombre: String, tam: Dp = 16.dp, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Icon(imageVector = vectorIcono(nombre), contentDescription = null, tint = color, modifier = modifier.size(tam))
}
