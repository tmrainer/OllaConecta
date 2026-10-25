// Importar.kt — cargar un padrón desde Excel o desde un CSV (ui-importar.js),
// en su propia pantalla. Nada se guarda hasta que la persona ve qué filas
// entran y cuáles no, y confirma. No se borra ni se cambia
// a nadie de los que ya están.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.nucleo.calculos.etiquetaGrupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.grupoEtario
import io.github.tmrainer.asistencia.nucleo.documentos.ErrorXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.leerXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.pareceXlsx
import io.github.tmrainer.asistencia.nucleo.importar.Importacion
import io.github.tmrainer.asistencia.nucleo.importar.filasImportables
import io.github.tmrainer.asistencia.nucleo.importar.prepararImportacion
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.Chip
import io.github.tmrainer.asistencia.ui.componentes.Chips
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ESTADO_FILA = mapOf(
    "lista" to "Se agrega",
    "incompleta" to "Se agrega, le faltan datos",
    "duplicada" to "No se agrega: ya está"
)

@Composable
fun PantallaImportar(ctx: Contexto) {
    val estado = ctx.estado
    val context = LocalContext.current
    val c = colores
    val alcance = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf<Importacion?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    val hoy = hoyIso()

    fun revisar() {
        aviso = ""
        revision = if (texto.isBlank()) null else prepararImportacion(texto, estado.padron.value, hoy)
    }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val nombre = uri.lastPathSegment.orEmpty()
        alcance.launch {
            val leido: Result<String> = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException()
                    when {
                        pareceXlsx(bytes) -> leerXlsx(bytes)
                        nombre.endsWith(".xls", ignoreCase = true) -> throw ErrorXlsx("Ese es un Excel antiguo (.xls). Ábrelo en Excel y guárdalo como .xlsx.")
                        else -> bytes.toString(Charsets.UTF_8)
                    }
                }
            }
            leido.onSuccess { texto = it; revisar() }
                .onFailure { e ->
                    revision = null
                    aviso = (e as? ErrorXlsx)?.message?.let { m -> if (m.startsWith("Ese es")) m else "No se pudo leer el archivo: $m." }
                        ?: "No se pudo leer el archivo. Prueba abriéndolo y copiando las filas aquí."
                }
        }
    }

    Column(Modifier.fillMaxSize().background(c.fondo)) {
        Cabecera(ctx, "Cargar un padrón", "Desde Excel o un CSV")
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, Medidas.e5)
        ) {
            Pista("Elige el archivo, o copia las filas con sus títulos y pégalas. Nada se guarda hasta que revises.", Modifier.padding(bottom = Medidas.e3))
            Acciones {
                Boton("Elegir archivo de Excel", {
                    elegir.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
                        "text/csv", "text/comma-separated-values", "text/tab-separated-values", "text/plain", "application/octet-stream"
                    ))
                }, tipo = TipoBoton.PRINCIPAL, icono = "archivo")
            }
            Spacer(Modifier.height(Medidas.e4))
            Campo(
                "O pega aquí las filas (con la fila de títulos)", texto, { texto = it; if (revision != null) revision = null },
                placeholder = "Apellido paterno, Apellido materno, Nombres, DNI, Grupo de edad, Tipo", unaLinea = false
            )
            Spacer(Modifier.height(Medidas.e2))
            Boton("Revisar", ::revisar, diminuto = true, habilitado = texto.isNotBlank())

            if (aviso.isNotEmpty()) AvisoSistema(aviso, Modifier.padding(top = Medidas.e3))
            val r = revision
            if (r != null) {
                Spacer(Modifier.height(Medidas.e4))
                val error = r.error
                if (error != null) AvisoSistema(error)
                else {
                    val entran = filasImportables(r.filas)
                    Chips {
                        Chip("Se agregarán", "${entran.size}")
                        if (r.resumen.incompleta > 0) Chip("Con datos que faltan", "${r.resumen.incompleta}")
                        if (r.resumen.duplicada > 0) Chip("Ya están en el padrón", "${r.resumen.duplicada}")
                        if (r.resumen.conCero > 0) Chip("DNI con el 0 agregado", "${r.resumen.conCero}")
                        if (r.resumen.vacia > 0) Chip("Filas vacías", "${r.resumen.vacia}")
                    }
                    if (r.sinReconocer.isNotEmpty()) {
                        Pista("Columnas que no se reconocieron y se ignoran: ${r.sinReconocer.joinToString(", ")}.", Modifier.padding(top = Medidas.e2))
                    }
                    // Una tarjeta por persona, no una tabla: en el celular la tabla cortaba la columna del estado.
                    val visibles = r.filas.filter { it.estado != "vacia" }
                    Spacer(Modifier.height(Medidas.e3))
                    Superficie {
                        visibles.take(50).forEachIndexed { i, f ->
                            val a = f.afiliado
                            val g = a.grupoEtarioManual ?: grupoEtario(a, hoy)
                            val datos = listOf(
                                if (a.numeroDocumento.isNotEmpty()) "${if (a.tipoDocumento == "ce") "CE" else "DNI"} ${a.numeroDocumento}" else "sin documento",
                                if (g != null) etiquetaGrupoEtario(g) else "sin grupo de edad",
                                when (a.tipoAfiliado) { "caso_social" -> "Ayuda social"; "habitual" -> "Habitual"; else -> "sin tipo" }
                            )
                            Column(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3)) {
                                Text(f.nombreCompleto.ifEmpty { "—" }, style = tipos.textoNegrita, color = c.titulo)
                                Text(
                                    ESTADO_FILA[f.estado].orEmpty(), style = tipos.chicoFuerte,
                                    color = when (f.estado) { "lista" -> c.ok; "incompleta" -> c.aviso; else -> c.tintaSuave }
                                )
                                Text("Fila ${f.linea} · ${datos.joinToString(" · ")}", style = tipos.chico, color = c.tintaSuave)
                                if (f.avisos.isNotEmpty()) Text(f.avisos.joinToString("; "), style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (i < minOf(visibles.size, 50) - 1) Separador()
                        }
                    }
                    if (visibles.size > 50) Pista("… y ${cuantos(visibles.size - 50, "persona")} más.", Modifier.padding(top = Medidas.e2))
                }
            }
        }
        // Abajo y siempre a la vista: agregar lo revisado.
        val r = revision
        val entran = if (r != null && r.error == null) filasImportables(r.filas) else emptyList()
        if (entran.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(c.superficie)) {
                Separador()
                Column(
                    Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(Medidas.e4, Medidas.e3)
                ) {
                    Boton("Agregar ${cuantos(entran.size, "persona")}", { confirmar = true }, Modifier.fillMaxWidth(), tipo = TipoBoton.PRINCIPAL, icono = "personaMas")
                }
            }
        }
    }

    if (confirmar) {
        val r = revision
        val entran = if (r != null) filasImportables(r.filas) else emptyList()
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = c.superficie,
            title = { Text("¿Agregar al padrón?", style = tipos.seccion, color = c.titulo) },
            text = { Text("Se agregarán ${cuantos(entran.size, "persona")} al padrón. No se borra ni se cambia a nadie de los que ya están.", style = tipos.texto, color = c.tinta) },
            confirmButton = {
                Boton("Agregar", {
                    confirmar = false
                    // Se vuelve a revisar contra el padrón de ahora, por si cambió.
                    val ahora = filasImportables(prepararImportacion(texto, estado.padron.value, hoy).filas)
                    estado.cambiarPadron { p -> p.copy(afiliados = p.afiliados + ahora.map { it.afiliado }) }
                    estado.guardarYa()
                    ctx.avisos.mostrar(if (ahora.size == 1) "1 persona agregada." else "${ahora.size} personas agregadas.")
                    ctx.volver()
                }, tipo = TipoBoton.PRINCIPAL)
            },
            dismissButton = { Boton("Cancelar", { confirmar = false }) }
        )
    }
}
