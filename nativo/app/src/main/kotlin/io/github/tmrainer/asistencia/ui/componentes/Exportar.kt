// Los botones «PDF» y «Excel» de Asistencia, Padrón, Reportes y B-1, y el bloque
// «Enviar a UNACEM». Nada se muestra antes en pantalla: el archivo se arma al tocar,
// fuera del hilo de la pantalla, y va al menú «Compartir» del teléfono (que también
// ofrece «Guardar en el teléfono») o al correo.
package io.github.tmrainer.asistencia.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.AlElegir
import io.github.tmrainer.asistencia.MIME_PDF
import io.github.tmrainer.asistencia.OllaConectaApp
import io.github.tmrainer.asistencia.abrirCorreo
import io.github.tmrainer.asistencia.compartirArchivo
import io.github.tmrainer.asistencia.nucleo.envio.Correo
import io.github.tmrainer.asistencia.nucleo.envio.MIME_XLSX
import io.github.tmrainer.asistencia.nucleo.envio.nombreArchivo
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un archivo por armar: sus bytes, el nombre (sin extensión) y el título del menú «Compartir». */
class Documento(val bytes: ByteArray, val nombre: String, val titulo: String, val alElegir: AlElegir? = null)

/**
 * «PDF» y «Excel», del mismo ancho, en una fila. Cada función arma el archivo al
 * tocar, fuera del hilo de la pantalla; devuelve null si no se puede (y ya
 * dijo por qué). [puede] corre antes, en la pantalla: puede avisar y llevar
 * a lo que falta (el B-1 incompleto). [pdfPrincipal]: el PDF es lo que se
 * hace en esa pantalla (mandar el B-1): va relleno, el único de la pantalla.
 * Sin [excel], solo el botón del PDF (la ficha de socia).
 *
 * El archivo va directo al menú «Compartir» de Android: ahí se elige WhatsApp, el
 * correo, Drive o «Guardar en el teléfono» (Compartir.kt), sin elegirlo antes aquí.
 */
@Composable
fun BotonesExportar(pdf: () -> Documento?, excel: (() -> Documento?)? = null, modifier: Modifier = Modifier, puede: () -> Boolean = { true }, pdfPrincipal: Boolean = false) {
    val context = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var armando by remember { mutableStateOf(false) }
    fun entregar(armar: () -> Documento?, mime: String, extension: String) {
        if (armando || !puede()) return
        armando = true
        alcance.launch {
            try {
                // Lo que arma el PDF o el Excel corre aparte: la pantalla no se congela.
                val d = withContext(Dispatchers.Default) { armar() }
                if (d != null) compartirArchivo(context, nombreArchivo(d.nombre, extension), mime, d.bytes, d.titulo, d.alElegir)
            } catch (e: Exception) {
                avisos.mostrar("No se pudo armar el archivo. Inténtalo de nuevo.")
            } finally { armando = false }
        }
    }
    Column(modifier) {
        Pista("Al tocar, elige a dónde va: WhatsApp, correo o «Guardar en el teléfono».", Modifier.padding(bottom = Medidas.e2))
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
            Boton(if (armando) "Armando…" else "PDF", { entregar(pdf, MIME_PDF, "pdf") }, Modifier.weight(1f), icono = "archivo", habilitado = !armando,
                tipo = if (pdfPrincipal) TipoBoton.PRINCIPAL else TipoBoton.SECUNDARIO)
            if (excel != null) Boton(if (armando) "Armando…" else "Excel", { entregar(excel, MIME_XLSX, "xlsx") }, Modifier.weight(1f), icono = "archivo", habilitado = !armando)
        }
    }
}

/**
 * «Enviar a UNACEM»: arma el correo con el Excel y lo abre en la app de
 * correo del teléfono, con el destinatario de Ajustes. Dice si ya se preparó.
 * [tipo] es «asistencia» o «padron»; [fecha], el día del envío. Si [armar]
 * devuelve null no hay nada que mandar, y se dice [siNoHay].
 */
@Composable
fun BloqueEnvio(ctx: Contexto, tipo: String, fecha: String, pista: String, siNoHay: String, armar: () -> Correo?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as OllaConectaApp
    val envios = app.envios
    val correo by envios.correo.collectAsStateWithLifecycle()
    val cambios by envios.cambios.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    val c = colores
    Column(modifier) {
        Pista(pista, Modifier.padding(bottom = Medidas.e3))
        if (!envios.hayCorreo()) {
            Text(
                if (correo.isBlank()) "Falta el correo de UNACEM. Ponlo una vez en Ajustes." else "El correo de UNACEM en Ajustes está mal escrito.",
                style = tipos.chicoFuerte, color = c.aviso, modifier = Modifier.padding(bottom = Medidas.e2)
            )
            Boton("Poner el correo en Ajustes", { ctx.ir(Ruta.AJUSTES) }, diminuto = true)
            return@Column
        }
        Boton(if (tipo == "padron") "Enviar el padrón a UNACEM" else "Enviar a UNACEM", {
            alcance.launch {
                val armado = try { withContext(Dispatchers.Default) { armar() } } catch (e: Exception) {
                    ctx.avisos.mostrar("No se pudo armar el correo. Inténtalo de nuevo."); return@launch
                }
                if (armado == null) { ctx.avisos.mostrar(siNoHay); return@launch }
                if (!abrirCorreo(context, armado, correo, AlElegir.Envio(tipo, fecha))) {
                    ctx.avisos.mostrar("Este teléfono no tiene una app de correo. Instala Gmail para mandarlo.")
                }
            }
        }, tipo = TipoBoton.PRINCIPAL, icono = "nube")
        val estado = remember(cambios, fecha) { envios.ultimo(tipo, fecha) }
        // Lo enviado lleva ✓: el verde ok solo casi no se distingue del verde de los enlaces.
        Row(Modifier.padding(top = Medidas.e2), verticalAlignment = Alignment.CenterVertically) {
            if (estado != null) { Icono("ok", 16.dp, c.ok); Spacer(Modifier.width(6.dp)) }
            Text(estado ?: "Va a: ${correo.trim()}", style = tipos.chico, color = if (estado != null) c.ok else c.tintaSuave)
        }
    }
}

/** «20:15»: la hora de ahora, para el pie del correo. */
fun horaAhora(): String = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
