// Solo en la versión de prueba (BuildConfig.DEBUG): probar el escaneo del DNI y
// compartir cómo salió. Quien prueba lee un DNI, marca dato por dato si salió bien
// (y, si no, cuál era el correcto) y lo comparte: un resumen para leer, las fotos
// de cada lado y el detalle comprimido en una línea «OLLADNI1:…», que se lee con
// `node herramientas/leer-prueba-dni.mjs`.
//
// Lleva el DNI completo (con la foto de la cara): se comparte solo con el equipo.
package io.github.tmrainer.asistencia.ui.pantallas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.compartirPrueba
import io.github.tmrainer.asistencia.nucleo.dni.CampoCruzado
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.camara.CamaraDni
import io.github.tmrainer.asistencia.ui.camara.CapturaDni
import io.github.tmrainer.asistencia.ui.camara.LecturaDni
import io.github.tmrainer.asistencia.ui.camara.RegistroDni
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.Ficha
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

/** Un dato leído, cómo lo leyó la app y lo que dice quien prueba. */
private class DatoDni(val clave: String, val etiqueta: String, val leido: String, val estado: String, val opciones: List<String> = emptyList())

private fun datosDe(l: LecturaDni): List<DatoDni> {
    val m = l.mrz.campos
    fun cruzado(clave: String, etiqueta: String, c: CampoCruzado?, deMrz: String) =
        if (c == null) DatoDni(clave, etiqueta, deMrz, if (deMrz.isEmpty()) "no-leido" else "solo-mrz")
        else DatoDni(clave, etiqueta, c.valor, c.estado.clave, c.opciones)
    return listOf(
        DatoDni("numero", "Número de DNI", m.numeroDocumento, "mrz"),
        cruzado("paterno", "Apellido paterno", l.anverso?.apellidoPaterno, m.apellidos.joinToString(" ")),
        cruzado("materno", "Apellido materno", l.anverso?.apellidoMaterno, ""),
        cruzado("nombres", "Nombres", l.anverso?.nombres, m.nombres),
        DatoDni("nacimiento", "Fecha de nacimiento", aDdMmAa(m.fechaNacimiento), "mrz"),
        DatoDni("sexo", "Sexo", m.sexo, "mrz"),
        DatoDni("vencimiento", "Vence", aDdMmAa(m.vencimiento), "mrz")
    )
}

@Composable
fun PruebaDni(ctx: Contexto) {
    val c = colores
    val context = LocalContext.current
    var leyendo by remember { mutableStateOf(false) }
    var registro by remember { mutableStateOf(RegistroDni()) }
    var lectura by remember { mutableStateOf<LecturaDni?>(null) }
    val bien = remember { mutableStateMapOf<String, Boolean>() }
    val correcto = remember { mutableStateMapOf<String, String>() }
    var quien by remember { mutableStateOf(ctx.preferencias.perfil.value.nombre) }
    var nota by remember { mutableStateOf("") }

    fun leerOtro() {
        registro = RegistroDni(); lectura = null; bien.clear(); correcto.clear(); nota = ""
        leyendo = true
    }

    if (leyendo) {
        BackHandler { leyendo = false }
        CamaraDni(alCerrar = { leyendo = false }, alLeer = { lectura = it }, conRevision = false, registro = registro)
        return
    }

    Column(
        Modifier.fillMaxSize().background(c.fondo).systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(Medidas.e4).widthIn(max = Medidas.anchoColumna),
        verticalArrangement = Arrangement.spacedBy(Medidas.e3)
    ) {
        Text("Probar el escaneo del DNI", style = tipos.titulo, color = c.titulo, modifier = Modifier.semantics { heading() })
        Pista("Solo en la versión de prueba. Lee un DNI, marca si cada dato salió bien y comparte el resultado. Lleva las fotos del DNI, con la cara: compártelo solo con el equipo.")
        val l = lectura
        if (l == null) {
            Boton("Leer un DNI", ::leerOtro, Modifier.fillMaxWidth(), tipo = TipoBoton.PRINCIPAL, icono = "camara")
        } else {
            val datos = remember(l) { datosDe(l) }
            val ctl = l.mrz.controles
            Superficie {
                Text("Las tres líneas: ${if (ctl.todos) "los 4 controles bien" else "fallan " + listOfNotNull("documento".takeIf { !ctl.documento }, "nacimiento".takeIf { !ctl.nacimiento }, "vencimiento".takeIf { !ctl.vencimiento }, "general".takeIf { !ctl.general }).joinToString(", ")}", style = tipos.texto, color = if (ctl.todos) c.tinta else c.error)
                Text(resumenTiempos(registro, l), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 4.dp))
            }
            TituloSeccion("¿Salió bien cada dato?")
            for (d in datos) Superficie {
                Text(d.etiqueta, style = tipos.chicoFuerte, color = c.tintaSuave)
                Text(d.leido.ifEmpty { "(no se leyó)" }, style = tipos.textoNegrita, color = if (d.leido.isEmpty()) c.tintaSuave else c.tinta)
                Text(d.estado + if (d.opciones.isNotEmpty()) ": ${d.opciones.joinToString(" / ")}" else "", style = tipos.chico, color = c.tintaSuave)
                Row(Modifier.padding(top = Medidas.e2), horizontalArrangement = Arrangement.spacedBy(Medidas.e2)) {
                    Ficha("Bien", bien[d.clave] == true, { bien[d.clave] = true })
                    Ficha("Mal", bien[d.clave] == false, { bien[d.clave] = false })
                }
                if (bien[d.clave] == false) Campo("Lo correcto", correcto[d.clave] ?: "", { correcto[d.clave] = it }, Modifier.padding(top = Medidas.e2), capitalizar = true)
            }
            Campo("Quién prueba", quien, { quien = it }, capitalizar = true)
            Campo("Comentario (opcional)", nota, { nota = it }, placeholder = "Ej.: con poca luz, DNI azul, al segundo intento", unaLinea = false)
            val faltan = datos.count { bien[it.clave] == null }
            if (faltan > 0) Pista("Para compartir, marca los ${if (faltan == 1) "un dato que falta" else "$faltan datos que faltan"}.")
            Boton("Compartir la prueba", {
                val texto = textoPrueba(context, registro, l, datos, bien, correcto, quien, nota)
                val imagenes = listOfNotNull(registro.reverso?.let { "dni-reverso.jpg" to jpeg(it) }, registro.anverso?.let { "dni-anverso.jpg" to jpeg(it) })
                compartirPrueba(context, texto, imagenes, "Compartir la prueba del DNI")
            }, Modifier.fillMaxWidth(), tipo = TipoBoton.PRINCIPAL, habilitado = faltan == 0)
            Boton("Copiar solo el texto", {
                val texto = textoPrueba(context, registro, l, datos, bien, correcto, quien, nota)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Prueba del DNI", texto))
            }, Modifier.fillMaxWidth(), habilitado = faltan == 0)
            Boton("Leer otro DNI", ::leerOtro, Modifier.fillMaxWidth(), icono = "camara")
        }
        Spacer(Modifier.height(Medidas.e2))
        Boton("Cerrar", ctx::cerrarCapa, Modifier.fillMaxWidth().align(Alignment.CenterHorizontally))
    }
}

private fun resumenTiempos(r: RegistroDni, l: LecturaDni): String =
    listOfNotNull(
        r.reverso?.let { "tres líneas en ${segundos(it.ms)}" },
        r.anverso?.let { "lado de la foto en ${segundos(it.ms)}" } ?: if (l.anverso == null) "sin el lado de la foto" else null,
        "${r.fotogramas} fotogramas leídos"
    ).joinToString(" · ").replaceFirstChar { it.uppercase() } + "."

private fun segundos(ms: Long) = "%.1f s".format(ms / 1000.0)

private fun jpeg(c: CapturaDni): ByteArray = ByteArrayOutputStream().also { c.imagen.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()

/** Un resumen para leer en el chat y, al final, todo el detalle comprimido en una línea. */
private fun textoPrueba(
    context: Context, r: RegistroDni, l: LecturaDni, datos: List<DatoDni>,
    bien: Map<String, Boolean>, correcto: Map<String, String>, quien: String, nota: String
): String {
    val ahora = LocalDateTime.now()
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: VERSION_APP
    val detalle = buildJsonObject {
        put("formato", 1)
        put("app", "${context.packageName} $version")
        put("telefono", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("android", Build.VERSION.RELEASE)
        put("fecha", ahora.toString())
        put("quienPrueba", quien.trim())
        put("comentario", nota.trim())
        put("fotogramas", r.fotogramas)
        putJsonObject("mrz") {
            putJsonArray("lineas") { l.mrz.lineas.forEach { add(it) } }
            put("estructura", l.mrz.estructura)
            putJsonObject("controles") {
                put("documento", l.mrz.controles.documento); put("nacimiento", l.mrz.controles.nacimiento)
                put("vencimiento", l.mrz.controles.vencimiento); put("general", l.mrz.controles.general)
            }
        }
        fun lado(nombre: String, cap: CapturaDni?) = cap?.let { putJsonObject(nombre) { put("ms", it.ms); putJsonArray("filas") { it.filas.forEach { f -> add(f) } } } }
        lado("reverso", r.reverso)
        lado("anverso", r.anverso)
        putJsonArray("datos") {
            for (d in datos) add(buildJsonObject {
                put("campo", d.clave); put("leido", d.leido); put("estado", d.estado)
                if (d.opciones.isNotEmpty()) putJsonArray("opciones") { d.opciones.forEach { add(it) } }
                put("bien", bien[d.clave])
                if (bien[d.clave] == false) put("correcto", correcto[d.clave].orEmpty().trim())
            })
        }
    }
    val malos = datos.filter { bien[it.clave] == false }
    return buildString {
        appendLine("Prueba del escaneo del DNI · ${datos.size - malos.size} de ${datos.size} datos bien")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${ahora.format(DateTimeFormatter.ofPattern("dd-MM-yy HH:mm"))}")
        if (quien.isNotBlank()) appendLine("Prueba: ${quien.trim()}")
        for (d in malos) appendLine("✗ ${d.etiqueta}: leyó «${d.leido}», es «${correcto[d.clave].orEmpty().trim()}»")
        appendLine(if (l.mrz.controles.todos) "Tres líneas: controles bien" else "Tres líneas: algún control falla")
        appendLine(resumenTiempos(r, l))
        if (nota.isNotBlank()) appendLine("Comentario: ${nota.trim()}")
        appendLine()
        append("OLLADNI1:").append(comprimir(detalle))
    }
}

private fun comprimir(json: JsonObject): String {
    val bytes = ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(json.toString().toByteArray()) } }.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
}
