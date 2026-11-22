// Cómo se acomoda la app a cualquier pantalla (decisión del 22-11-2026, a partir de la
// tablet): no por modelo de teléfono sino por el lugar que hay de verdad, contando la letra
// que eligió la persona. Una tablet con «pantalla ampliada» y letra grande tiene menos
// lugar para texto que un celular chico con letra normal.
//   · angosta: el botón «Ayuda» de la cabecera queda solo con su ícono, como «Ajustes».
//   · baja: el botón flotante («Compra», «Inscribir») queda solo con su «+», para tapar menos.
//   · FilaCampos: dos campos van lado a lado solo si caben y quedan parejos; si no, uno
//     debajo del otro (nunca desalineados).
package io.github.tmrainer.asistencia.ui.componentes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.ui.tema.Medidas

/** El lugar de la pantalla. [anchoTexto]: el ancho dividido por la escala de la letra. */
data class Ventana(val anchoTexto: Dp, val alto: Dp) {
    val angosta: Boolean get() = anchoTexto < 380.dp
    val baja: Boolean get() = alto < 720.dp
}

@Composable
fun ventana(): Ventana {
    val conf = LocalConfiguration.current
    val letra = LocalDensity.current.fontScale
    return Ventana((conf.screenWidthDp / letra).dp, conf.screenHeightDp.dp)
}

/**
 * Campos en una fila, del mismo ancho, si a cada uno le tocan al menos [minAncho] (por la
 * escala de la letra) y todos quedan de la misma altura: un rótulo o una pista que se parte
 * en dos líneas los desalineaba. Si no, van uno debajo del otro, a todo el ancho.
 * Los hijos no llevan `weight`: el ancho lo pone la fila.
 */
@Composable
fun FilaCampos(modifier: Modifier = Modifier, minAncho: Dp = 150.dp, contenido: @Composable () -> Unit) {
    val letra = LocalDensity.current.fontScale
    Layout(contenido, modifier) { hijos, restr ->
        val ancho = restr.maxWidth
        val espacio = Medidas.e3.roundToPx()
        if (hijos.isEmpty()) return@Layout layout(ancho, 0) {}
        val n = hijos.size
        val col = (ancho - espacio * (n - 1)) / n
        // Antes de medir, cuánto alto pediría cada uno con ese ancho (se mide una sola vez).
        val enFila = n > 1 && col >= (minAncho * letra).roundToPx() &&
            hijos.map { it.maxIntrinsicHeight(col) }.let { hs -> hs.max() - hs.min() <= 4.dp.roundToPx() }
        if (enFila) {
            val ps = hijos.map { it.measure(Constraints(minWidth = col, maxWidth = col)) }
            layout(ancho, ps.maxOf { it.height }) {
                var x = 0
                for (p in ps) { p.placeRelative(x, 0); x += col + espacio }
            }
        } else {
            val ps = hijos.map { it.measure(Constraints(minWidth = ancho, maxWidth = ancho)) }
            layout(ancho, ps.sumOf { it.height } + espacio * (n - 1)) {
                var y = 0
                for (p in ps) { p.placeRelative(0, y); y += p.height + espacio }
            }
        }
    }
}

