// Tema.kt — el sistema visual de estilos.css en Compose: colores con nombre
// de uso (no de tono), la letra Atkinson Hyperlegible Next en tres grosores,
// cinco tamaños de letra que crecen enteros con «letra grande», una escala de
// espacios y tres radios. El modo oscuro solo cambia los valores de los colores.
package io.github.tmrainer.asistencia.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tmrainer.asistencia.Letra
import io.github.tmrainer.asistencia.R
import io.github.tmrainer.asistencia.Tema

data class Colores(
    val fondo: Color, val superficie: Color, val superficie2: Color, val seleccion: Color, val navActivo: Color,
    val tinta: Color, val tintaSuave: Color, val titulo: Color,
    val marca: Color, val marcaTexto: Color, val marcaSuave: Color, val acento: Color, val acentoTexto: Color,
    val borde: Color, val bordeFuerte: Color, val contorno: Color,
    val error: Color, val errorFondo: Color, val aviso: Color, val avisoFondo: Color,
    val ok: Color, val okFondo: Color, val azul: Color, val azulFondo: Color, val pendiente: Color,
    val gNino: Color, val gAdolescente: Color, val gAdulto: Color, val gMayor: Color, val gSin: Color,
    val esOscuro: Boolean
)

val COLORES_CLARO = Colores(
    fondo = Color(0xFFF5F7F0), superficie = Color(0xFFFFFFFF), superficie2 = Color(0xFFEEF3E8),
    seleccion = Color(0xFFD3E4C7), navActivo = Color(0xFF3A5533),
    tinta = Color(0xFF1F251C), tintaSuave = Color(0xFF59654F), titulo = Color(0xFF1B2E1E),
    marca = Color(0xFF1B2E1E), marcaTexto = Color(0xFFEEF2E9), marcaSuave = Color(0xFFA9BD9C),
    acento = Color(0xFF3A5533), acentoTexto = Color(0xFF4E6B43),
    borde = Color(0xFFDFE5D8), bordeFuerte = Color(0xFFB7C4AC), contorno = Color(0xFF6E9161),
    error = Color(0xFF8F2A1C), errorFondo = Color(0xFFFBECEA), aviso = Color(0xFF7D5300), avisoFondo = Color(0xFFFDF3DF),
    ok = Color(0xFF3F6B33), okFondo = Color(0xFFE7F1E1), azul = Color(0xFF2F6672), azulFondo = Color(0xFFE6F0F2),
    pendiente = Color(0xFFA87A22),
    gNino = Color(0xFF9C4A14), gAdolescente = Color(0xFF6A4C96), gAdulto = Color(0xFF2F6672), gMayor = Color(0xFF7A4A36), gSin = Color(0xFF5B6657),
    esOscuro = false
)

val COLORES_OSCURO = Colores(
    fondo = Color(0xFF101712), superficie = Color(0xFF19221B), superficie2 = Color(0xFF1F2B22),
    seleccion = Color(0xFF2F4834), navActivo = Color(0xFFFFFFFF),
    tinta = Color(0xFFE4EADF), tintaSuave = Color(0xFFA9B4A3), titulo = Color(0xFFE8F1E0),
    marca = Color(0xFF152219), marcaTexto = Color(0xFFEEF2E9), marcaSuave = Color(0xFFA9BD9C),
    acento = Color(0xFF4E7C43), acentoTexto = Color(0xFFA7CF94),
    borde = Color(0xFF2A362C), bordeFuerte = Color(0xFF3D4C40), contorno = Color(0xFF7A9D6C),
    error = Color(0xFFF29B8C), errorFondo = Color(0xFF3A1D19), aviso = Color(0xFFF0C26C), avisoFondo = Color(0xFF33290F),
    ok = Color(0xFF9FCF8A), okFondo = Color(0xFF1B2E1C), azul = Color(0xFF8FC5D1), azulFondo = Color(0xFF16292D),
    pendiente = Color(0xFFB88A35),
    gNino = Color(0xFF9C4A14), gAdolescente = Color(0xFF6A4C96), gAdulto = Color(0xFF2F6672), gMayor = Color(0xFF7A4A36), gSin = Color(0xFF5B6657),
    esOscuro = true
)

val ATKINSON = FontFamily(
    Font(R.font.atkinson_next_400, FontWeight.Normal),
    Font(R.font.atkinson_next_600, FontWeight.SemiBold),
    Font(R.font.atkinson_next_700, FontWeight.Bold)
)

/** Los cinco tamaños de estilos.css, ya multiplicados por el tamaño de letra elegido. */
class Tipos(private val factor: Float) {
    private fun t(em: Float): TextUnit = (16f * em * factor).sp
    val cifra = TextStyle(fontFamily = ATKINSON, fontWeight = FontWeight.Bold, fontSize = t(2.4f), lineHeight = t(2.5f))
    val titulo = TextStyle(fontFamily = ATKINSON, fontWeight = FontWeight.Bold, fontSize = t(1.2f), lineHeight = t(1.45f))
    val seccion = TextStyle(fontFamily = ATKINSON, fontWeight = FontWeight.Bold, fontSize = t(1.05f), lineHeight = t(1.3f))
    val texto = TextStyle(fontFamily = ATKINSON, fontWeight = FontWeight.Normal, fontSize = t(1f), lineHeight = t(1.5f))
    val textoFuerte = texto.copy(fontWeight = FontWeight.SemiBold)
    val textoNegrita = texto.copy(fontWeight = FontWeight.Bold)
    // 14 sp: lo más chico para leer (el cuerpo es de 16 sp, como en Material 3).
    val chico = TextStyle(fontFamily = ATKINSON, fontWeight = FontWeight.Normal, fontSize = t(0.875f), lineHeight = t(1.25f))
    val chicoFuerte = chico.copy(fontWeight = FontWeight.SemiBold)
    val chicoNegrita = chico.copy(fontWeight = FontWeight.Bold)
    val pista = TextStyle(fontFamily = ATKINSON, fontWeight = FontWeight.Normal, fontSize = t(0.88f), lineHeight = t(1.3f))
    val diminuto = TextStyle(fontFamily = ATKINSON, fontWeight = FontWeight.SemiBold, fontSize = t(0.76f), lineHeight = t(1f))
}

/** Espacios 4 · 8 · 12 · 16 · 24 · 32 y esquinas 8 / 12 / 16. */
object Medidas {
    val e1: Dp = 4.dp; val e2: Dp = 8.dp; val e3: Dp = 12.dp; val e4: Dp = 16.dp; val e5: Dp = 24.dp; val e6: Dp = 32.dp
    val rS: Dp = 8.dp; val rM: Dp = 12.dp; val rL: Dp = 16.dp
    val anchoColumna: Dp = 720.dp
}

val LocalColores = staticCompositionLocalOf { COLORES_CLARO }
val LocalTipos = staticCompositionLocalOf { Tipos(1f) }

/** Acceso corto desde cualquier composable: `colores.acento`, `tipos.texto`. */
val colores: Colores @Composable get() = LocalColores.current
val tipos: Tipos @Composable get() = LocalTipos.current

@Composable
fun TemaOlla(tema: Tema, letra: Letra, contenido: @Composable () -> Unit) {
    val oscuro = when (tema) {
        Tema.CLARO -> false
        Tema.OSCURO -> true
        Tema.AUTO -> isSystemInDarkTheme()
    }
    val c = if (oscuro) COLORES_OSCURO else COLORES_CLARO
    val t = Tipos(letra.factor)
    // Los componentes de Material (campos, casillas, diálogos) toman los
    // mismos colores, para que nada se vea de otro sistema.
    // En oscuro secondary y error son claros: lo que va encima es oscuro.
    val esquema = if (oscuro) darkColorScheme(
        primary = c.acento, onPrimary = Color.White, primaryContainer = c.seleccion, onPrimaryContainer = c.titulo,
        secondary = c.acentoTexto, onSecondary = c.fondo, background = c.fondo, onBackground = c.tinta,
        surface = c.superficie, onSurface = c.tinta, surfaceVariant = c.superficie2, onSurfaceVariant = c.tintaSuave,
        outline = c.contorno, outlineVariant = c.borde, error = c.error, onError = c.fondo, errorContainer = c.errorFondo, onErrorContainer = c.error,
        surfaceContainer = c.superficie, surfaceContainerHigh = c.superficie2, surfaceContainerHighest = c.superficie2
    ) else lightColorScheme(
        primary = c.acento, onPrimary = Color.White, primaryContainer = c.seleccion, onPrimaryContainer = c.titulo,
        secondary = c.acentoTexto, onSecondary = Color.White, background = c.fondo, onBackground = c.tinta,
        surface = c.superficie, onSurface = c.tinta, surfaceVariant = c.superficie2, onSurfaceVariant = c.tintaSuave,
        outline = c.contorno, outlineVariant = c.borde, error = c.error, onError = Color.White, errorContainer = c.errorFondo, onErrorContainer = c.error,
        surfaceContainer = c.superficie, surfaceContainerHigh = c.superficie2, surfaceContainerHighest = c.superficie2
    )
    CompositionLocalProvider(LocalColores provides c, LocalTipos provides t) {
        MaterialTheme(
            colorScheme = esquema,
            typography = Typography(
                bodyLarge = t.texto, bodyMedium = t.texto, bodySmall = t.chico,
                labelLarge = t.textoFuerte, labelMedium = t.chicoFuerte, labelSmall = t.diminuto,
                titleLarge = t.titulo, titleMedium = t.seccion, titleSmall = t.textoFuerte,
                headlineSmall = t.titulo, headlineMedium = t.cifra
            ),
            content = contenido
        )
    }
}
