// Importar.kt — cargar la hoja de la Municipalidad: la olla no inscribe, el padrón es el de esa hoja. La primera
// carga arma el padrón; las siguientes son actualizaciones. Antes de guardar
// se ve quién entra, quién vuelve, quién sigue y quién sale (de baja, no se
// borra), y se elige. Lee Excel o CSV, o filas pegadas (importar.js).
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
import androidx.compose.runtime.mutableStateMapOf
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
import io.github.tmrainer.asistencia.nucleo.importar.FilaImportada
import io.github.tmrainer.asistencia.nucleo.importar.Importacion
import io.github.tmrainer.asistencia.nucleo.importar.prepararImportacion
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.padron.ComparacionHoja
import io.github.tmrainer.asistencia.nucleo.padron.EleccionHoja
import io.github.tmrainer.asistencia.nucleo.padron.aplicarHoja
import io.github.tmrainer.asistencia.nucleo.padron.compararConHoja
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.Casilla
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.Chip
import io.github.tmrainer.asistencia.ui.componentes.Chips
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo leído de la hoja y su comparación con el padrón de ahora. */
private class Revision(val lectura: Importacion, val comparacion: ComparacionHoja?, val padron: Padron)

@Composable
fun PantallaImportar(ctx: Contexto) {
    val estado = ctx.estado
    val context = LocalContext.current
    val c = colores
    val alcance = rememberCoroutineScope()
    val hoy = hoyIso()
    var fechaHoja by remember { mutableStateOf(hoy) }
    var texto by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf<Revision?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    // Lo que se aplica: marcado = se aplica. Clave: «e<i>» entra, «v<id>» vuelve, «s<id>» sale, «n<id>» nombre de la hoja.
    val elegido = remember { mutableStateMapOf<String, Boolean>() }

    fun revisar() {
        aviso = ""
        elegido.clear()
        if (texto.isBlank()) { revision = null; return }
        // Contra un padrón vacío: la importación solo lee la hoja; la comparación es por DNI.
        val lectura = prepararImportacion(texto, Padron(), hoy)
        val padron = estado.padron.value
        val comparacion = if (lectura.error == null) compararConHoja(padron, lectura) else null
        comparacion?.let { k ->
            k.entran.forEachIndexed { i, _ -> elegido["e$i"] = true }
            k.vuelven.forEach { elegido["v${it.id}"] = true }
            k.salen.forEach { elegido["s${it.id}"] = true }
            k.nombreDistinto.forEach { elegido["n${it.mia.id}"] = false }
        }
        revision = Revision(lectura, comparacion, padron)
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
        Cabecera(ctx, "Hoja de la Municipalidad", "Actualizar el padrón")
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, Medidas.e5)
        ) {
            Pista("Carga la hoja que te entregó la Municipalidad (en Excel o CSV, o copia sus filas). Antes de guardar verás quién entra, quién sale y a quiénes de las que inscribiste aquí ya registraron.", Modifier.padding(bottom = Medidas.e3))
            CampoFecha("Fecha de la hoja", fechaHoja, { if (it.isNotEmpty()) fechaHoja = it }, pista = "La que dice la hoja, o el día que te la dieron.")
            Spacer(Modifier.height(Medidas.e4))
            Acciones {
                Boton("Elegir el archivo de Excel", {
                    elegir.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
                        "text/csv", "text/comma-separated-values", "text/tab-separated-values", "text/plain", "application/octet-stream"
                    ))
                }, tipo = TipoBoton.PRINCIPAL, icono = "archivo")
            }
            Spacer(Modifier.height(Medidas.e4))
            Campo(
                "O pega aquí las filas (con la fila de títulos)", texto, { texto = it; if (revision != null) revision = null },
                placeholder = "Apellido paterno, Apellido materno, Nombres, DNI", unaLinea = false
            )
            Spacer(Modifier.height(Medidas.e2))
            Boton("Revisar", ::revisar, diminuto = true, habilitado = texto.isNotBlank())
            Pista("¿Te dieron la hoja en papel? En «Inscribir», elige «Ya está en la hoja de la Municipalidad».", Modifier.padding(top = Medidas.e3))

            if (aviso.isNotEmpty()) AvisoSistema(aviso, Modifier.padding(top = Medidas.e3))
            val r = revision
            if (r != null) {
                Spacer(Modifier.height(Medidas.e4))
                val error = r.lectura.error
                val k = r.comparacion
                if (error != null || k == null) AvisoSistema(error ?: "No se pudo leer la hoja.")
                else VistaComparacion(r.lectura, k, elegido, fechaHoja)
            }
        }
        // Abajo y siempre a la vista: guardar la actualización.
        val k = revision?.comparacion
        if (k != null && k.hayCambios) {
            Column(Modifier.fillMaxWidth().background(c.superficie)) {
                Separador()
                Column(
                    Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(Medidas.e4, Medidas.e3)
                ) {
                    Boton("Guardar la actualización", { confirmar = true }, Modifier.fillMaxWidth(), tipo = TipoBoton.PRINCIPAL, habilitado = esIso(fechaHoja))
                }
            }
        }
    }

    val r = revision
    val k = r?.comparacion
    if (confirmar && r != null && k != null) {
        val entran = k.entran.filterIndexed { i, _ -> elegido["e$i"] == true }
        val vuelven = k.vuelven.filter { elegido["v${it.id}"] == true }
        val salen = k.salen.filter { elegido["s${it.id}"] == true }
        val nombres = k.nombreDistinto.filter { elegido["n${it.mia.id}"] == true }
        val partes = listOfNotNull(
            if (entran.isNotEmpty()) "entran ${cuantos(entran.size, "persona")}" else null,
            if (vuelven.isNotEmpty()) "${if (vuelven.size == 1) "vuelve" else "vuelven"} ${cuantos(vuelven.size, "persona")}" else null,
            if (salen.isNotEmpty()) "${if (salen.size == 1) "sale" else "salen"} ${cuantos(salen.size, "persona")} (de baja desde el ${aDdMmAa(fechaHoja)}, no se borran)" else null,
            if (nombres.isNotEmpty()) "${cuantos(nombres.size, "nombre")} como en la hoja" else null
        )
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = c.superficie,
            title = { Text("¿Guardar la hoja del ${aDdMmAa(fechaHoja)}?", style = tipos.seccion, color = c.titulo) },
            text = { Text(if (partes.isEmpty()) "No elegiste nada para cambiar: solo se anota que cargaste esta hoja." else "Con esta hoja: ${partes.joinToString("; ")}.", style = tipos.texto, color = c.tinta) },
            confirmButton = {
                Boton("Guardar", {
                    confirmar = false
                    val antes = estado.padron.value
                    // Sobre el mismo padrón que se comparó: la comparación guarda sus personas.
                    val nuevo = aplicarHoja(r.padron, fechaHoja, EleccionHoja(entran, vuelven, salen, nombres, siguen = k.siguen), k.enLaHoja)
                    estado.cambiarPadron { nuevo }
                    estado.guardarYa()
                    ctx.avisos.mostrar("Padrón actualizado con la hoja del ${aDdMmAa(fechaHoja)}.") {
                        estado.cambiarPadron { antes }
                        estado.guardarYa()
                    }
                    ctx.volver()
                }, tipo = TipoBoton.PRINCIPAL)
            },
            dismissButton = { Boton("Cancelar", { confirmar = false }) }
        )
    }
}

/** Lo que cambiaría la hoja, por grupos, con una casilla por persona. */
@Composable
private fun VistaComparacion(lectura: Importacion, k: ComparacionHoja, elegido: MutableMap<String, Boolean>, fechaHoja: String) {
    val c = colores
    val hoy = hoyIso()
    Chips {
        Chip("En la hoja", "${k.enLaHoja}")
        Chip("Entran", "${k.entran.size}")
        if (k.vuelven.isNotEmpty()) Chip("Vuelven", "${k.vuelven.size}")
        if (k.confirman.isNotEmpty()) Chip("Ya registradas", "${k.confirman.size}")
        Chip("Siguen", "${k.siguen.size}")
        Chip("Salen", "${k.salen.size}")
        if (k.pendientes.isNotEmpty()) Chip("Siguen pendientes", "${k.pendientes.size}")
        if (k.repetidas.isNotEmpty()) Chip("Repetidas en la hoja", "${k.repetidas.size}")
        if (lectura.resumen.conCero > 0) Chip("DNI con el 0 agregado", "${lectura.resumen.conCero}")
        if (lectura.resumen.vacia > 0) Chip("Filas vacías", "${lectura.resumen.vacia}")
    }
    if (lectura.sinReconocer.isNotEmpty()) {
        Pista("Columnas que no se reconocieron y se ignoran: ${lectura.sinReconocer.joinToString(", ")}.", Modifier.padding(top = Medidas.e2))
    }
    if (k.confirman.isNotEmpty()) {
        Text("Ya las registró la Municipalidad: ${k.confirman.joinToString(", ") { nombreCompleto(it) }}.", style = tipos.textoFuerte, color = c.ok, modifier = Modifier.padding(top = Medidas.e3))
    }
    if (k.pendientes.isNotEmpty()) {
        Text("Inscritas aquí que la hoja todavía no trae (siguen pendientes, no salen): ${k.pendientes.joinToString(", ") { nombreCompleto(it) }}.", style = tipos.chico, color = c.aviso, modifier = Modifier.padding(top = Medidas.e2))
    }
    if (!k.hayCambios) {
        Text("El padrón ya está igual a esta hoja: no hay nada que cambiar.", style = tipos.textoFuerte, color = c.ok, modifier = Modifier.padding(top = Medidas.e3))
        return
    }
    fun datos(f: FilaImportada): String {
        val a = f.afiliado
        val g = a.grupoEtarioManual ?: grupoEtario(a, hoy)
        return listOf(
            if (a.numeroDocumento.isNotEmpty()) "${if (a.tipoDocumento == "ce") "CE" else "DNI"} ${a.numeroDocumento}" else "sin documento",
            if (g != null) etiquetaGrupoEtario(g) else "sin grupo de edad",
            when (a.tipoAfiliado) { "caso_social" -> "Ayuda social"; "habitual" -> "Habitual"; else -> "sin tipo" }
        ).joinToString(" · ")
    }
    if (k.entran.isNotEmpty()) {
        Grupo("Entran (${k.entran.size})", "Están en la hoja y no en el padrón. Se agregan las marcadas.") {
            k.entran.forEachIndexed { i, f ->
                Casilla(elegido["e$i"] == true, { elegido["e$i"] = it }, f.nombreCompleto.ifEmpty { "—" }, "Fila ${f.linea} · ${datos(f)}", f.avisos.joinToString("; ").ifEmpty { null })
                if (i < k.entran.size - 1) Separador()
            }
        }
    }
    if (k.vuelven.isNotEmpty()) {
        Grupo("Vuelven (${k.vuelven.size})", "Estaban de baja y la hoja las trae otra vez. Se reactivan las marcadas.") {
            k.vuelven.forEachIndexed { i, a ->
                Casilla(elegido["v${a.id}"] == true, { elegido["v${a.id}"] = it }, nombreCompleto(a), "DNI ${a.numeroDocumento}" + if (a.motivoBaja.isNotEmpty()) " · baja: ${a.motivoBaja}" else "")
                if (i < k.vuelven.size - 1) Separador()
            }
        }
    }
    if (k.salen.isNotEmpty()) {
        Grupo("Salen (${k.salen.size})", "Están en el padrón y no en esta hoja. Las marcadas quedan de baja desde el ${aDdMmAa(fechaHoja)}: no se borran y su asistencia anterior sigue contando.") {
            k.salen.forEachIndexed { i, a ->
                Casilla(elegido["s${a.id}"] == true, { elegido["s${a.id}"] = it }, nombreCompleto(a), if (a.numeroDocumento.isNotEmpty()) "DNI ${a.numeroDocumento}" else "sin documento")
                if (i < k.salen.size - 1) Separador()
            }
        }
    }
    if (k.nombreDistinto.isNotEmpty()) {
        Grupo("Mismo DNI, otro nombre (${k.nombreDistinto.size})", "Puede ser un nombre mal escrito en uno de los dos lados. Mira la hoja antes de elegir.") {
            k.nombreDistinto.forEachIndexed { i, d ->
                Column(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3)) {
                    Text("DNI ${d.mia.numeroDocumento}", style = tipos.textoNegrita, color = c.titulo)
                    Opciones(
                        listOf(false to "Dejar: ${nombreCompleto(d.mia)}", true to "Usar el de la hoja: ${d.deLaHoja.nombreCompleto}"),
                        { it == (elegido["n${d.mia.id}"] == true) }, { elegido["n${d.mia.id}"] = it }
                    )
                }
                if (i < k.nombreDistinto.size - 1) Separador()
            }
        }
    }
}

@Composable
private fun Grupo(titulo: String, pista: String, contenido: @Composable () -> Unit) {
    Spacer(Modifier.height(Medidas.e4))
    TituloSeccion(titulo)
    Pista(pista, Modifier.padding(bottom = Medidas.e2))
    Superficie { contenido() }
}
