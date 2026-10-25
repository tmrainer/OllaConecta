// Lo que acompaña a la cámara del DNI: la pantalla que dice qué
// lado mostrar antes de cada paso (con su animación), el dibujo de cada paso y
// la revisión de lo leído antes de usarlo. La revisión es solo texto: el
// fotograma no se guarda ni se muestra.
package io.github.tmrainer.asistencia.ui.camara

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
 * Qué cara de qué DNI se dibuja. El DNI electrónico tiene las tres líneas de
 * «<» atrás y la foto con los nombres adelante; el DNI azul las tiene debajo
 * de la foto, en la misma cara.
 */
enum class CaraDni { TRES_LINEAS, FOTO, AZUL }

private val AZUL_DNI = Color(0xFF6E9BE0)

/**
 * Un DNI dibujado, con lo que la cámara tiene que ver en verde: las tres
 * líneas de «<» o la foto y los nombres. [luz], de 0 a 1: por dónde pasa la
 * luz que lo recorre como la cámara (null: sin luz). Es un adorno: lo que hay
 * que hacer lo dice el texto de al lado.
 */
@Composable
fun DibujoDni(cara: CaraDni, modifier: Modifier = Modifier, luz: Float? = null) {
    Canvas(modifier.size(88.dp, 56.dp)) {
        val w = size.width
        val h = size.height
        val esquina = CornerRadius(6.dp.toPx())
        val tenue = Color.White.copy(alpha = .35f)
        // El azul se reconoce por su color.
        drawRoundRect(if (cara == CaraDni.AZUL) AZUL_DNI.copy(alpha = .3f) else Color.White.copy(alpha = .12f), cornerRadius = esquina)
        drawRoundRect(if (cara == CaraDni.AZUL) AZUL_DNI else Color.White.copy(alpha = .6f), cornerRadius = esquina, style = Stroke(1.5.dp.toPx()))
        val grueso = 2.5.dp.toPx()
        val trazos = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
        // Las tres líneas, a trazos: como los «<<<» de la MRZ.
        fun tresLineas(desde: Float, paso: Float) {
            for (i in 0..2) {
                val y = h * (desde + i * paso)
                drawLine(VERDE_VISOR, Offset(w * .08f, y), Offset(w * .92f, y), grueso, pathEffect = trazos)
            }
        }
        when (cara) {
            CaraDni.FOTO -> {
                drawRoundRect(VERDE_VISOR, Offset(w * .08f, h * .2f), Size(w * .26f, h * .56f), CornerRadius(3.dp.toPx()))
                for (i in 0..2) {
                    val y = h * (.3f + i * .18f)
                    drawLine(VERDE_VISOR, Offset(w * .42f, y), Offset(w * (.88f - i * .14f), y), grueso, StrokeCap.Round)
                }
            }
            CaraDni.TRES_LINEAS -> {
                for (i in 0..1) {
                    val y = h * (.2f + i * .14f)
                    drawLine(tenue, Offset(w * .1f, y), Offset(w * .6f, y), 2.dp.toPx(), StrokeCap.Round)
                }
                tresLineas(.6f, .13f)
            }
            CaraDni.AZUL -> {
                // Foto y nombres arriba (también se leen), las tres líneas abajo.
                drawRoundRect(tenue, Offset(w * .08f, h * .12f), Size(w * .2f, h * .42f), CornerRadius(3.dp.toPx()))
                for (i in 0..2) {
                    val y = h * (.2f + i * .12f)
                    drawLine(tenue, Offset(w * .36f, y), Offset(w * (.86f - i * .12f), y), 2.dp.toPx(), StrokeCap.Round)
                }
                tresLineas(.68f, .11f)
            }
        }
        if (luz != null && luz > 0f && luz < 1f) {
            val y = h * luz
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, VERDE_VISOR, Color.Transparent)), Offset(0f, y), Offset(w, y), 3.dp.toPx())
        }
    }
}

/**
 * Antes de cada lado, como en las apps de los bancos: qué lado mostrar y su
 * dibujo animado. [paso] 1: el lado de las tres líneas de «<», en los dos DNI
 * (atrás en el electrónico, debajo de la foto en el azul). [paso] 2: el otro
 * lado, el de la foto del DNI electrónico. [alOmitir], si hay: seguir sin él.
 */
@Composable
fun AntesDelLado(paso: Int, alSeguir: () -> Unit, alCancelar: () -> Unit, alOmitir: (() -> Unit)? = null) {
    val lineas = paso == 1
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(Medidas.e4)) {
        Text("Leer el DNI", style = tipos.titulo, color = Color.White)
        Text("Paso $paso de 2", style = tipos.chicoFuerte, color = VERDE_VISOR, modifier = Modifier.padding(top = Medidas.e1))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(Medidas.e5))
            if (lineas) AnimacionTresLineas() else AnimacionVoltear()
            Spacer(Modifier.height(Medidas.e6))
            Text(if (lineas) "El lado de las tres líneas" else "Ahora, el otro lado", style = tipos.titulo, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(
                if (lineas) "Busca el lado con tres líneas de «<». En el DNI electrónico está atrás; en el DNI azul, debajo de la foto."
                else "Voltea el DNI y pon el lado de la foto dentro del recuadro.",
                style = tipos.texto, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Medidas.e2)
            )
            // Al DNI azul casi nunca le toca este paso (sus nombres se leen con las líneas); si le toca, se omite.
            if (!lineas) Text("¿Tu DNI es azul? Su otro lado no tiene los nombres: toca «Omitir este paso».",
                style = tipos.pista, color = Color.White.copy(alpha = .7f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Medidas.e3))
            Spacer(Modifier.height(Medidas.e5))
        }
        if (alOmitir != null) Boton("Omitir este paso", alOmitir, Modifier.fillMaxWidth().padding(bottom = Medidas.e3))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
            Boton("Cancelar", alCancelar)
            Boton(if (lineas) "Empezar" else "Continuar", alSeguir, Modifier.weight(1f), tipo = TipoBoton.PRINCIPAL, icono = "camara")
        }
    }
}

/** Un DNI dibujado con su nombre debajo. */
@Composable
private fun DniConNombre(cara: CaraDni, nombre: String, ancho: Dp, luz: Float?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        DibujoDni(cara, Modifier.size(ancho, ancho * .63f), luz)
        Text(nombre, style = tipos.chicoFuerte, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(top = Medidas.e2))
    }
}

/** Los dos DNI, con la luz que pasa por sus tres líneas. En bucle, despacio. */
@Composable
private fun AnimacionTresLineas() {
    val avance by rememberInfiniteTransition(label = "lineas").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "lineas"
    )
    // La luz baja por la zona de las líneas (del 55 al 98 % del alto) y descansa un rato.
    val luz = if (avance < .7f) .55f + .43f * (avance / .7f) else null
    // Lado a lado: más chicos en un teléfono angosto.
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ancho = minOf(140.dp, (maxWidth - Medidas.e4) / 2)
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e4)) {
            DniConNombre(CaraDni.TRES_LINEAS, "DNI electrónico", ancho, luz)
            DniConNombre(CaraDni.AZUL, "DNI azul", ancho, luz)
        }
    }
}

/**
 * El DNI electrónico que se voltea, de las tres líneas a la foto, y luego una
 * luz que lo recorre. En bucle, despacio.
 */
@Composable
private fun AnimacionVoltear() {
    val avance by rememberInfiniteTransition(label = "voltear").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "voltear"
    )
    // 0–25 %: quieto en las tres líneas; 25–45 %: se voltea; 50–85 %: la luz lo recorre.
    val giro = FastOutSlowInEasing.transform(((avance - .25f) / .2f).coerceIn(0f, 1f))
    val yaVolteado = giro >= .5f
    val luz = if (yaVolteado) (avance - .5f) / .35f else null
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp, 140.dp).graphicsLayer { rotationY = 180f * giro; cameraDistance = 16f * density }) {
            // Pasada la mitad del giro se ve el otro lado, al derecho.
            DibujoDni(if (yaVolteado) CaraDni.FOTO else CaraDni.TRES_LINEAS, Modifier.fillMaxSize().graphicsLayer { if (yaVolteado) rotationY = 180f }, luz)
        }
        Text("DNI electrónico", style = tipos.chicoFuerte, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(top = Medidas.e2))
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
