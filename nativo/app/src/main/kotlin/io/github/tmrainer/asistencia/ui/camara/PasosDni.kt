// PasosDni.kt — lo que acompaña a la cámara del DNI: el dibujo de cada paso
// (qué lado mostrar) y la revisión de lo leído antes de usarlo. La revisión es
// solo texto: el fotograma no se guarda ni se muestra.
package io.github.tmrainer.asistencia.ui.camara

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.nucleo.dni.CampoCruzado
import io.github.tmrainer.asistencia.nucleo.dni.EstadoCampo
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.FranjaEstado
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TonoPastilla
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

/**
 * Un DNI dibujado, con lo que la cámara tiene que ver en verde: las tres
 * líneas de «<» (paso 1) o la foto y los nombres (paso 2). Vale para el DNI
 * azul y el DNIe. Es un adorno: lo que hay que hacer lo dice el texto de al lado.
 */
@Composable
fun DibujoDni(conFoto: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(88.dp, 56.dp)) {
        val w = size.width
        val h = size.height
        val esquina = CornerRadius(6.dp.toPx())
        val tenue = Color.White.copy(alpha = .35f)
        drawRoundRect(Color.White.copy(alpha = .12f), cornerRadius = esquina)
        drawRoundRect(Color.White.copy(alpha = .6f), cornerRadius = esquina, style = Stroke(1.5.dp.toPx()))
        val grueso = 2.5.dp.toPx()
        if (conFoto) {
            drawRoundRect(VERDE_VISOR, Offset(w * .08f, h * .2f), Size(w * .26f, h * .56f), CornerRadius(3.dp.toPx()))
            for (i in 0..2) {
                val y = h * (.3f + i * .18f)
                drawLine(VERDE_VISOR, Offset(w * .42f, y), Offset(w * (.88f - i * .14f), y), grueso, StrokeCap.Round)
            }
        } else {
            for (i in 0..1) {
                val y = h * (.2f + i * .14f)
                drawLine(tenue, Offset(w * .1f, y), Offset(w * .6f, y), 2.dp.toPx(), StrokeCap.Round)
            }
            // Las tres líneas, a trazos: como los «<<<» de la MRZ.
            val trazos = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
            for (i in 0..2) {
                val y = h * (.6f + i * .13f)
                drawLine(VERDE_VISOR, Offset(w * .08f, y), Offset(w * .92f, y), grueso, pathEffect = trazos)
            }
        }
    }
}

/** Una fila de la revisión: el dato y, si hace falta, qué hacer con él. */
private class Dato(val etiqueta: String, val valor: String, val nota: String? = null)

private const val NO_SE_LEYO = "No se leyó. Lo escribes después."

/** Un nombre: el del lado de la foto si se cruzó con la MRZ; si no, el de la MRZ (sin tildes ni ñ). */
private fun nombre(etiqueta: String, cruce: CampoCruzado?, deMrz: String): Dato = when {
    cruce == null -> if (deMrz.isEmpty()) Dato(etiqueta, "—", NO_SE_LEYO) else Dato(etiqueta, deMrz, "Viene sin tildes ni ñ: revísalo.")
    cruce.estado == EstadoCampo.DUDOSO -> Dato(etiqueta, "—", "No se leyó bien: ${cruce.opciones.joinToString(" o ")}. Lo eliges después.")
    cruce.estado == EstadoCampo.NO_LEIDO || cruce.valor.isEmpty() -> Dato(etiqueta, "—", NO_SE_LEYO)
    cruce.estado == EstadoCampo.SIN_CONFIRMAR -> Dato(etiqueta, cruce.valor, "Compáralo con el DNI.")
    else -> Dato(etiqueta, cruce.valor)
}

/**
 * Lo leído, para revisarlo con el DNI en la mano antes de usarlo. [usar]
 * lleva los datos al formulario (que todavía no inscribe a nadie); [otraVez]
 * vuelve a la cámara.
 */
@Composable
fun RevisionDni(l: LecturaDni, usar: () -> Unit, otraVez: () -> Unit) {
    val c = colores
    val m = l.mrz.campos
    val a = l.anverso
    val datos = listOf(
        Dato("DNI", m.numeroDocumento.ifEmpty { "—" }),
        nombre("Apellido paterno", a?.apellidoPaterno, m.apellidos.joinToString(" ")),
        nombre("Apellido materno", a?.apellidoMaterno, ""),
        nombre("Nombres", a?.nombres, m.nombres),
        Dato("Fecha de nacimiento", aDdMmAa(m.fechaNacimiento).ifEmpty { "—" }),
        Dato("Sexo", m.sexo.ifEmpty { "—" })
    )
    val peruano = m.pais == "PER"
    Column(
        Modifier.fillMaxSize().background(c.fondo).systemBarsPadding().verticalScroll(rememberScrollState()).padding(Medidas.e4),
        verticalArrangement = Arrangement.spacedBy(Medidas.e3)
    ) {
        Text("Revisa los datos", style = tipos.titulo, color = c.titulo, modifier = Modifier.semantics { heading() })
        if (!peruano) FranjaEstado("No parece un DNI peruano. Escribe los datos a mano.", TonoPastilla.ERROR, icono = "aviso")
        else Text("Compáralos con el DNI que tienes en la mano.", style = tipos.texto, color = c.tintaSuave)
        Superficie {
            datos.forEachIndexed { i, d ->
                if (i > 0) Separador()
                Column(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3)) {
                    Text(d.etiqueta, style = tipos.chico, color = c.tintaSuave)
                    Text(d.valor, style = tipos.textoFuerte, color = c.tinta)
                    val nota = d.nota
                    if (nota != null) Row(Modifier.padding(top = Medidas.e1), verticalAlignment = Alignment.Top) {
                        Icono("aviso", 15.dp, c.aviso, Modifier.padding(top = 3.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(nota, style = tipos.pista, color = c.tinta)
                    }
                }
            }
        }
        Acciones {
            if (peruano) Boton("Usar estos datos", usar, tipo = TipoBoton.PRINCIPAL)
            Boton("Leer otra vez", otraVez)
        }
    }
}
