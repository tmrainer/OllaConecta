// Datos.kt — «Tus datos» (pagina-datos.js): la copia (guardar, con
// contraseña, recuperar y cuándo fue la última), qué va en ella, dónde se
// guardan los datos y, al final y lejos de «Guardar una copia», «Borrar todo».
//
// Pasar a otro teléfono por QR (PasarDatos.kt), sin internet. La cuenta y la
// nube esperan una cuenta de la organización.
package io.github.tmrainer.asistencia.ui.pantallas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.fechaLarga
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.cifrarRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.descifrarRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.escribirRespaldo
import io.github.tmrainer.asistencia.nucleo.respaldo.leerRespaldo
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Composable
fun PantallaDatos(ctx: Contexto) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val c = colores
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }   // para releer «última copia» tras guardar

    // ---- guardar una copia: el selector de archivos del teléfono ----
    var textoAGuardar by remember { mutableStateOf<String?>(null) }
    val crear = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val texto = textoAGuardar ?: return@rememberLauncherForActivityResult
        textoAGuardar = null
        if (uri == null) return@rememberLauncherForActivityResult   // cerró el menú: no hubo copia
        alcance.launch {
            val ok = withContext(Dispatchers.IO) {
                try { contexto.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(texto.toByteArray(Charsets.UTF_8)) }; true } catch (e: Exception) { false }
            }
            if (ok) { estado.registrarCopia(); tick += 1; avisos.mostrar("Copia guardada.") }
            else avisos.mostrar("No se pudo guardar la copia. Inténtalo de nuevo.")
        }
    }
    fun guardarCopia(contrasena: String?) {
        val respaldo = estado.armarCopia()
        val nombre = "respaldo-b1-${hoyIso()}${if (contrasena != null) "-protegido" else ""}.json"
        alcance.launch {
            textoAGuardar = withContext(Dispatchers.Default) {
                if (contrasena != null) JSON.encodeToString(JsonElement.serializer(), cifrarRespaldo(respaldo, contrasena)) else escribirRespaldo(respaldo)
            }
            crear.launch(nombre)
        }
    }

    // ---- recuperar una copia ----
    var pidiendoContrasena by remember { mutableStateOf<JsonObject?>(null) }
    var confirmando by remember { mutableStateOf<LecturaRespaldo.Lista?>(null) }
    var errorCopia by remember { mutableStateOf<String?>(null) }
    fun recibirLectura(l: LecturaRespaldo) {
        when (l) {
            is LecturaRespaldo.Lista -> confirmando = l
            is LecturaRespaldo.Protegido -> pidiendoContrasena = l.sobre
            is LecturaRespaldo.Fallo -> errorCopia = l.mensaje
        }
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val lectura = withContext(Dispatchers.IO) {
                try {
                    val texto = contexto.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    leerRespaldo(texto)
                } catch (e: Exception) { LecturaRespaldo.Fallo("No se pudo leer el archivo. Elige una copia guardada con esta aplicación.") }
            }
            recibirLectura(lectura)
        }
    }

    var conContrasena by remember { mutableStateOf(false) }
    var confirmandoBorrar by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Tus datos", "Dónde están y cómo cuidarlos")
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            val avisosAlmacen = estado.avisosAlmacenamiento()
            if (avisosAlmacen.isNotEmpty()) AvisoSistema(avisosAlmacen.joinToString(" "), Modifier.padding(bottom = Medidas.e4))

            TituloSeccion("Copia de tus datos")
            Pista("Un archivo con todos tus datos, para recuperarlos o pasarlos a otro teléfono. Guarda una cada dos semanas.", Modifier.padding(bottom = Medidas.e2))
            val dias = remember(tick) { estado.diasDesdeUltimaCopia() }
            val textoUltima = when {
                dias == null -> "Todavía no guardaste ninguna copia en este teléfono."
                dias == 0 -> "Tu última copia es de hoy."
                else -> "Tu última copia es de hace ${cuantos(dias, "día")}."
            }
            ParaLeer(textoUltima)
            Text(textoUltima, style = if (dias == null || dias >= 14) tipos.textoFuerte else tipos.texto, color = if (dias == null || dias >= 14) c.aviso else c.tinta, modifier = Modifier.padding(bottom = Medidas.e3))
            Acciones {
                Boton("Guardar una copia", { guardarCopia(null) })
                Boton("Recuperar una copia", { abrir.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) })
            }
            Boton(if (conContrasena) "Ocultar «con contraseña»" else "Guardar con contraseña", { conContrasena = !conContrasena }, Modifier.padding(top = Medidas.e2), diminuto = true)
            if (conContrasena) {
                var c1 by remember { mutableStateOf("") }
                var c2 by remember { mutableStateOf("") }
                var msg by remember { mutableStateOf("") }
                Column(Modifier.padding(top = Medidas.e3)) {
                    Pista("Sin la contraseña nadie puede abrir la copia, ni tú.", Modifier.padding(bottom = Medidas.e2))
                    Campo("Contraseña (8 o más caracteres)", c1, { c1 = it })
                    Spacer(Modifier.height(Medidas.e2))
                    Campo("Repite la contraseña", c2, { c2 = it })
                    if (msg.isNotEmpty()) Text(msg, style = tipos.chico, color = c.error, modifier = Modifier.padding(top = 4.dp))
                    Boton("Guardar copia con contraseña", {
                        when {
                            c1.length < 8 -> msg = "La contraseña debe tener 8 o más caracteres."
                            c1 != c2 -> msg = "Las dos contraseñas no coinciden."
                            else -> { msg = ""; guardarCopia(c1) }
                        }
                    }, Modifier.padding(top = 10.dp))
                }
            }
            Pista("Tiene nombres y DNI. Si la mandas por WhatsApp, ponle contraseña.", Modifier.padding(top = Medidas.e3))

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Qué va en la copia")
            ParaLeer("Sí va: el padrón, la asistencia, el B-1 y las entregas. No va: tu perfil, la letra y el modo.")
            Text("Sí va: el padrón, la asistencia, el B-1 y las entregas.", style = tipos.texto, color = c.tinta)
            Text("No va: tu perfil, la letra y el modo. Son de este teléfono.", style = tipos.texto, color = c.tinta, modifier = Modifier.padding(top = Medidas.e2))

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Pasar a otro teléfono")
            Pista("Sin internet: un teléfono muestra un QR y el otro lo lee. Antes de guardar, se ve qué cambia.", Modifier.padding(bottom = Medidas.e3))
            Acciones {
                Boton("Pasar el padrón", { enviarPorQr(ctx, "padron") })
                Boton("Pasar la asistencia", { elegirDiasYEnviar(ctx) })
                Boton("Pasar las entregas", { enviarPorQr(ctx, "entregas") })
            }
            Spacer(Modifier.height(Medidas.e3))
            Boton("Recibir por QR", { recibirPorQr(ctx) }, icono = "camara")
            Pista("Para pasar todo, también el B-1, usa «Guardar una copia».", Modifier.padding(top = Medidas.e3))

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Dónde se guardan")
            Lugar("escudo", "En este teléfono, dentro de la app", listOf(
                "No se mandan a internet. Funciona sin señal.",
                "Si desinstalas la app o cambias de teléfono, se pueden perder."
            ))
            Lugar("nube", "En la copia de seguridad de Google, si está activada", listOf(
                "Android puede guardarlos en tu cuenta Google. Se activa en los ajustes del teléfono: Google → Copia de seguridad.",
                "El equipo del proyecto no la ve. No te fíes solo de ella."
            ))
            Lugar("archivo", "En las copias que guardas tú", listOf("Solo cuando tocas «Guardar una copia». Queda donde la guardes."))

            Spacer(Modifier.height(Medidas.e5))
            Text("Borrar todo", style = tipos.seccion, color = c.error, modifier = Modifier.padding(bottom = Medidas.e3))
            Pista("Borra de este teléfono el padrón, la asistencia, el B-1 y las entregas. No se puede deshacer.", Modifier.padding(bottom = Medidas.e3))
            Boton("Borrar todo", { confirmandoBorrar = true }, tipo = TipoBoton.PELIGRO)
        }
    }

    // ---- diálogos ----
    val sobre = pidiendoContrasena
    if (sobre != null) {
        var clave by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pidiendoContrasena = null },
            containerColor = c.superficie,
            title = { Text("Esta copia tiene contraseña", style = tipos.seccion, color = c.titulo) },
            text = { Column { Campo("Escríbela", clave, { clave = it }); if (msg.isNotEmpty()) Text(msg, style = tipos.chico, color = c.error, modifier = Modifier.padding(top = 4.dp)) } },
            confirmButton = {
                Boton("Abrir copia", {
                    alcance.launch {
                        val r = withContext(Dispatchers.Default) { runCatching { leerRespaldo(descifrarRespaldo(sobre, clave)) }.getOrNull() }
                        if (r == null) msg = "Esa contraseña no abre la copia. Revisa las mayúsculas."
                        else { pidiendoContrasena = null; recibirLectura(r) }
                    }
                }, tipo = TipoBoton.PRINCIPAL)
            },
            dismissButton = { Boton("Cancelar", { pidiendoContrasena = null }) }
        )
    }
    val lista = confirmando
    if (lista != null) {
        AlertDialog(
            onDismissRequest = { confirmando = null },
            containerColor = c.superficie,
            title = { Text("Recuperar esta copia", style = tipos.seccion, color = c.titulo) },
            text = { Text("La copia trae: ${lista.revision.resumen}.\n\nReemplazará eso mismo en este equipo. Lo demás se queda como está.", style = tipos.texto, color = c.tinta) },
            confirmButton = {
                Boton("Continuar", {
                    confirmando = null
                    val ok = estado.restaurar(lista)
                    avisos.mostrar(if (ok) "Copia recuperada." else "No se pudo recuperar toda la copia: puede que el espacio esté lleno. Revisa qué quedó y vuelve a intentar.")
                }, tipo = TipoBoton.PRINCIPAL)
            },
            dismissButton = { Boton("Cancelar", { confirmando = null }) }
        )
    }
    val err = errorCopia
    if (err != null) {
        AlertDialog(
            onDismissRequest = { errorCopia = null }, containerColor = c.superficie,
            title = { Text("No se pudo recuperar", style = tipos.seccion, color = c.titulo) },
            text = { Text(err, style = tipos.texto, color = c.tinta) },
            confirmButton = { Boton("Entendido", { errorCopia = null }, tipo = TipoBoton.PRINCIPAL) }
        )
    }
    if (confirmandoBorrar) {
        AlertDialog(
            onDismissRequest = { confirmandoBorrar = false }, containerColor = c.superficie,
            title = { Text("¿Borrar todo?", style = tipos.seccion, color = c.titulo) },
            text = { Text("Se borrará de este equipo: el padrón (${cuantos(padron.afiliados.size, "persona")}), la asistencia, el formato B-1 y las entregas.\n\nNo se puede deshacer. ¿Guardaste una copia?", style = tipos.texto, color = c.tinta) },
            confirmButton = { Boton("Borrar todo", { confirmandoBorrar = false; estado.borrarTodo(); avisos.mostrar("Se borró todo.") }, tipo = TipoBoton.PELIGRO) },
            dismissButton = { Boton("Cancelar", { confirmandoBorrar = false }) }
        )
    }
}

@Composable
private fun Lugar(icono: String, titulo: String, textos: List<String>) {
    val c = colores
    Row(Modifier.fillMaxWidth().padding(bottom = Medidas.e3).clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e4, Medidas.e3), verticalAlignment = Alignment.Top) {
        Icono(icono, 24.dp, c.acentoTexto, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(Medidas.e3))
        Column {
            Text(titulo, style = tipos.textoNegrita, color = c.tinta)
            for (t in textos) Text(t, style = tipos.texto, color = c.tinta, modifier = Modifier.padding(top = Medidas.e1))
        }
    }
}
