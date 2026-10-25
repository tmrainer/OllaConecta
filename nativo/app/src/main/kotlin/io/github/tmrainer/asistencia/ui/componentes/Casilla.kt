// Casilla.kt — una casilla con su texto: toda la fila la marca o desmarca.
// Para elegir qué se aplica antes de guardar (la hoja municipal, lo recibido
// por QR): un toque, sin desplegables.
package io.github.tmrainer.asistencia.ui.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

/** Una persona con su casilla: toda la fila la marca o desmarca. */
@Composable
fun Casilla(marcada: Boolean, alCambiar: (Boolean) -> Unit, nombre: String, detalle: String? = null, aviso: String? = null) {
    val c = colores
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { alCambiar(!marcada) }.padding(Medidas.e2, Medidas.e2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(marcada, alCambiar, colors = CheckboxDefaults.colors(checkedColor = c.acento, uncheckedColor = c.contorno))
        Column(Modifier.weight(1f)) {
            Text(nombre, style = tipos.textoNegrita, color = c.titulo)
            if (detalle != null) Text(detalle, style = tipos.chico, color = c.tintaSuave)
            if (aviso != null) Text(aviso, style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
