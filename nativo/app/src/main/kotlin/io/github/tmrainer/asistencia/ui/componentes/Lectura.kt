// Qué lee «Leer esta pantalla en voz alta»: el título de la cabecera y lo que cada
// pantalla marcó. Una pieza marcada se anota mientras está en pantalla y se borra al
// salir; se lee en el orden en que apareció.
package io.github.tmrainer.asistencia.ui.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

class Lectura {
    private val partes = LinkedHashMap<Any, String>()
    private var titulo = ""

    internal fun poner(clave: Any, texto: String) { partes[clave] = texto }
    internal fun quitar(clave: Any) { partes.remove(clave) }
    internal fun ponerTitulo(texto: String) { titulo = texto }

    /** Todo lo que se lee ahora, en orden, en una sola frase. */
    fun texto(): String =
        (listOf(titulo) + partes.values).filter { it.isNotBlank() }.joinToString(". ").replace(Regex("\\s+"), " ")
}

val LocalLectura = staticCompositionLocalOf { Lectura() }

/** Marca un texto para leerlo en voz alta mientras esta pieza esté en pantalla. */
@Composable
fun ParaLeer(texto: String) {
    val lectura = LocalLectura.current
    val clave = remember { Any() }
    // Se anota una vez (para no perder su lugar) y se actualiza si cambia.
    DisposableEffect(lectura) { onDispose { lectura.quitar(clave) } }
    SideEffect { lectura.poner(clave, texto) }
}

/** El título que se lee primero (lo pone la Cabecera). */
@Composable
fun TituloParaLeer(texto: String) {
    val lectura = LocalLectura.current
    SideEffect { lectura.ponerTitulo(texto) }
}
