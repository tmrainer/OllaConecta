// AvisoAbajo.kt — el aviso de abajo con «Deshacer» (avisoAbajo() en ui.js):
// hacer y ofrecer deshacer, en vez de preguntar «¿Está segura?». 4 s sin
// deshacer; 7 s con deshacer, que es tiempo de leerlo y reaccionar sin apuro.
package io.github.tmrainer.asistencia.ui.componentes

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.delay

class Aviso(val texto: String, val deshacer: (() -> Unit)?, val n: Long)

class Avisador {
    var actual by mutableStateOf<Aviso?>(null)
        private set
    private var contador = 0L

    fun mostrar(texto: String, deshacer: (() -> Unit)? = null) {
        contador += 1
        actual = Aviso(texto, deshacer, contador)
    }

    fun cerrar() { actual = null }
}

val LocalAvisos = staticCompositionLocalOf { Avisador() }

@Composable
fun AvisoAbajo(avisador: Avisador, modifier: Modifier = Modifier) {
    val aviso = avisador.actual
    LaunchedEffect(aviso?.n) {
        if (aviso == null) return@LaunchedEffect
        delay(if (aviso.deshacer != null) 7000 else 4000)
        if (avisador.actual?.n == aviso.n) avisador.cerrar()
    }
    val c = colores
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = aviso != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            val a = aviso ?: return@AnimatedVisibility
            Row(
                modifier = Modifier
                    .padding(horizontal = Medidas.e4)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(Medidas.rM))
                    .clip(RoundedCornerShape(Medidas.rM))
                    .background(c.marca)
                    .defaultMinSize(minHeight = 52.dp)
                    .padding(start = Medidas.e4, end = Medidas.e2, top = Medidas.e2, bottom = Medidas.e2)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(a.texto, style = tipos.texto, color = Color.White, modifier = Modifier.weight(1f))
                if (a.deshacer != null) {
                    Text(
                        "Deshacer", style = tipos.textoNegrita, color = Color(0xFFB9DBA8),
                        modifier = Modifier
                            .clip(RoundedCornerShape(Medidas.rS))
                            .clickable { avisador.cerrar(); a.deshacer.invoke() }
                            .defaultMinSize(minHeight = 44.dp)
                            .padding(horizontal = Medidas.e3, vertical = Medidas.e3)
                    )
                }
            }
        }
    }
}

/** Un toque corto de vibración, donde el teléfono lo permite. */
fun vibrar(context: Context, ms: Long = 20) {
    try {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    } catch (e: Exception) { /* sin vibración */ }
}
