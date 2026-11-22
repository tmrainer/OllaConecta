// Elegir una opción de una lista (decisión del 22-11-2026): un campo cerrado que dice lo
// elegido y, al tocarlo, una lista vertical debajo que se desliza, con lo elegido marcado y
// a la vista. Para listas de 4 o más opciones de una sola elección (meses, filtros,
// parentesco, unidades). Con 2 o 3 opciones van botones a la vista (Opciones): un toque.
package io.github.tmrainer.asistencia.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

private val ALTO_OPCION = 52.dp
private const val VISIBLES = 5

/**
 * [rotulo] arriba, chico («Mes»); debajo, lo elegido. [opciones]: (valor, texto). Sin nada
 * elegido dice [vacio]. Con más de [VISIBLES] opciones la lista se desliza y muestra su barra.
 */
@Composable
fun <T> SelectorDesplegable(
    rotulo: String,
    opciones: List<Pair<T, String>>,
    elegido: T?,
    alElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
    icono: String? = null,
    vacio: String = "Elige una opción"
) {
    val c = colores
    var abierto by remember { mutableStateOf(false) }
    var ancho by remember { mutableStateOf(0) }
    val densidad = LocalDensity.current
    val texto = opciones.firstOrNull { it.first == elegido }?.second ?: vacio
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().onSizeChanged { ancho = it.width }
                .clip(RoundedCornerShape(Medidas.rS)).background(c.superficie)
                .border(if (abierto) 2.dp else 1.dp, if (abierto) c.acentoTexto else c.contorno, RoundedCornerShape(Medidas.rS))
                .clickable { abierto = true }
                .semantics { role = Role.DropdownList; stateDescription = if (abierto) "Abierto" else "Cerrado" }
                .defaultMinSize(minHeight = 56.dp).padding(horizontal = Medidas.e4, vertical = Medidas.e2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icono != null) { Icono(icono, 20.dp, c.acentoTexto); Spacer(Modifier.width(Medidas.e3)) }
            Column(Modifier.weight(1f)) {
                if (rotulo.isNotEmpty()) Text(rotulo, style = tipos.chico, color = c.tintaSuave)
                Text(texto, style = tipos.textoFuerte, color = if (elegido == null) c.tintaSuave else c.titulo)
            }
            Icono("flecha", 20.dp, c.tintaSuave, Modifier.rotate(if (abierto) -90f else 90f))
        }
        val desplazar = rememberScrollState()
        val indice = opciones.indexOfFirst { it.first == elegido }
        // Al abrir, lo elegido a la vista (en el medio, si se puede).
        LaunchedEffect(abierto) {
            if (abierto && indice > 0) with(densidad) { desplazar.scrollTo(((indice - 1) * ALTO_OPCION.toPx()).toInt()) }
        }
        val barra = c.contorno
        DropdownMenu(
            expanded = abierto, onDismissRequest = { abierto = false }, scrollState = desplazar, containerColor = c.superficie,
            modifier = Modifier.width(with(densidad) { ancho.toDp() }).heightIn(max = ALTO_OPCION * VISIBLES + 16.dp)
                // La barra del costado dice que hay más (la lista se desliza).
                .drawWithContent {
                    drawContent()
                    if (desplazar.maxValue > 0) {
                        val total = size.height + desplazar.maxValue
                        val alto = size.height * size.height / total
                        val y = (size.height - alto) * desplazar.value / desplazar.maxValue
                        drawRoundRect(barra, Offset(size.width - 6.dp.toPx(), y), Size(4.dp.toPx(), alto), CornerRadius(2.dp.toPx()))
                    }
                }
        ) {
            opciones.forEach { (valor, t) ->
                val es = valor == elegido
                DropdownMenuItem(
                    text = { Text(t, style = if (es) tipos.textoFuerte else tipos.texto, color = if (es) c.acentoTexto else c.tinta) },
                    onClick = { abierto = false; alElegir(valor) },
                    leadingIcon = { if (es) Icono("ok", 20.dp, c.acentoTexto) else Spacer(Modifier.width(20.dp)) },
                    modifier = Modifier.heightIn(min = ALTO_OPCION).then(if (es) Modifier.background(c.seleccion) else Modifier)
                )
            }
        }
    }
}
