// Padron.kt — inscribir, listar y editar personas (pagina-padron.js +
// ui-personas.js). Inscribir es de vez en cuando: una pantalla propia que abre
// el botón verde de abajo (o el Inicio) y se queda abierta para inscribir a
// varias seguidas. La lista queda para consultar; cada fila se despliega para editar.
//
// Cargar un padrón desde Excel y exportarlo llegan en una fase siguiente.
package io.github.tmrainer.asistencia.ui.pantallas

import io.github.tmrainer.asistencia.nucleo.dni.EstadoCampo
import io.github.tmrainer.asistencia.nucleo.dni.CampoCruzado
import io.github.tmrainer.asistencia.ui.camara.LecturaDni
import io.github.tmrainer.asistencia.ui.camara.CamaraDni
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tmrainer.asistencia.nucleo.calculos.TIPOS_AFILIADO
import io.github.tmrainer.asistencia.nucleo.calculos.TRAMOS_ETARIOS
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.edadEnFecha
import io.github.tmrainer.asistencia.nucleo.calculos.etiquetaGrupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.grupoEtario
import io.github.tmrainer.asistencia.nucleo.calculos.resumenEtario
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.validaciones.Hallazgo
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.indicePadron
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAfiliado
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAfiliados
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.Acciones
import io.github.tmrainer.asistencia.ui.componentes.Avatar
import io.github.tmrainer.asistencia.ui.componentes.AvisoSistema
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.BotonFlotante
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.CampoFecha
import io.github.tmrainer.asistencia.ui.componentes.Chip
import io.github.tmrainer.asistencia.ui.componentes.Chips
import io.github.tmrainer.asistencia.ui.componentes.EstadoGuardado
import io.github.tmrainer.asistencia.ui.componentes.Ficha
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.LocalAvisos
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.PanelHallazgos
import io.github.tmrainer.asistencia.ui.componentes.Pastilla
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.componentes.TonoPastilla
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FILTROS = listOf("activos" to "Activas", "incompletas" to "Les faltan datos", "ayuda_social" to "Ayuda social", "bajas" to "De baja", "todas" to "Todas")

@Composable
fun PantallaPadron(ctx: Contexto) {
    val estado = ctx.estado
    val padron by estado.padron.collectAsStateWithLifecycle()
    val guardado by estado.guardado.collectAsStateWithLifecycle()
    val c = colores
    val avisos = LocalAvisos.current

    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("activos") }
    var fechaRef by remember { mutableStateOf(hoyIso()) }
    var abierta by remember { mutableStateOf<String?>(null) }
    val listaEstado = rememberLazyListState()

    val indice = remember(padron) { indicePadron(padron) }
    val erroresPor = remember(padron) {
        padron.afiliados.associate { a -> a.id to validarAfiliado(a, padron, indice).count { it.severidad == Severidad.ERROR } }
    }
    val incompletas = padron.afiliados.count { it.activo && (erroresPor[it.id] ?: 0) > 0 }

    var hallazgos by remember { mutableStateOf(emptyList<Hallazgo>()) }
    LaunchedEffect(padron) {
        delay(600)
        hallazgos = withContext(Dispatchers.Default) { validarAfiliados(padron) }
    }

    val filtradas = remember(padron, filtro, erroresPor) {
        padron.afiliados.filter { a ->
            when (filtro) {
                "activos" -> a.activo
                "bajas" -> !a.activo
                "ayuda_social" -> a.activo && a.tipoAfiliado == "caso_social"
                "incompletas" -> (erroresPor[a.id] ?: 0) > 0
                else -> true
            }
        }.sortedWith { a, b -> ORDEN_NOMBRES.compare(nombreCompleto(a), nombreCompleto(b)) }
    }
    val aguja = normalizarNombre(busqueda)
    val visibles = remember(filtradas, aguja) {
        filtradas.filter { a -> aguja.isEmpty() || normalizarNombre(nombreCompleto(a)).contains(aguja) || a.numeroDocumento.contains(busqueda.trim()) }
    }

    fun cambiarPersona(id: String, cambio: (Afiliado) -> Afiliado) {
        estado.cambiarPadron { p -> p.copy(afiliados = p.afiliados.map { if (it.id == id) cambio(it) else it }) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth()) {
            Cabecera(ctx, "Padrón", cuantos(padron.afiliados.size, "persona"))
            LazyColumn(
                Modifier.fillMaxWidth().widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally),
                state = listaEstado,
                contentPadding = PaddingValues(Medidas.e4, Medidas.e4, Medidas.e4, 110.dp)
            ) {
                val avisosAlmacen = estado.avisosAlmacenamiento()
                if (avisosAlmacen.isNotEmpty()) item { AvisoSistema(avisosAlmacen.joinToString(" "), Modifier.padding(bottom = Medidas.e4)) }
                item {
                    PanelHallazgos(hallazgos) { campo ->
                        // Puede estar oculta por el filtro o la búsqueda.
                        busqueda = ""; filtro = "todas"; abierta = campo.removePrefix("afiliado.")
                    }
                    TituloSeccion("Personas afiliadas")
                    if (padron.afiliados.isNotEmpty()) {
                        Campo("Buscar", busqueda, { busqueda = it }, placeholder = "Nombre o DNI")
                        Spacer(Modifier.height(Medidas.e3))
                        CampoFecha("Edades a la fecha", fechaRef, { if (it.isNotEmpty()) fechaRef = it })
                        Spacer(Modifier.height(10.dp))
                        Opciones(FILTROS.map { (v, t) -> v to if (v == "incompletas" && incompletas > 0) "$t ($incompletas)" else t }, { it == filtro }, { filtro = it })
                        val conteo = resumenEtario(padron, fechaRef)
                        Chips(Modifier.padding(top = Medidas.e3)) {
                            Chip("Activas", "${afiliadosActivos(padron, fechaRef).size}")
                            Chip("Niño/a", "${conteo.nino}"); Chip("Adolescente", "${conteo.adolescente}")
                            Chip("Adulto/a", "${conteo.adulto}"); Chip("Adulto/a mayor", "${conteo.adultoMayor}")
                            if (conteo.sinDato > 0) Chip("Sin grupo", "${conteo.sinDato}")
                        }
                        val total = padron.afiliados.size
                        val textoConteo = listOfNotNull(
                            if (visibles.size < total) "Se ven ${visibles.size} de $total." else null,
                            if (incompletas > 0) "${cuantos(incompletas, "persona")} con datos incompletos." else null
                        ).joinToString(" ")
                        if (textoConteo.isNotEmpty()) Text(textoConteo, style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
                        Spacer(Modifier.height(Medidas.e2))
                    }
                }
                if (visibles.isEmpty()) {
                    item {
                        Text(
                            if (padron.afiliados.isEmpty()) "Todavía no hay nadie en el padrón. Toca «Inscribir», abajo." else "Nadie coincide con la búsqueda o el filtro.",
                            style = tipos.pista, color = c.tintaSuave,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e4, Medidas.e6),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                items(visibles, key = { it.id }) { a ->
                    FilaPersona(
                        a = a, fechaRef = fechaRef, errores = erroresPor[a.id] ?: 0, abierta = abierta == a.id,
                        primera = a.id == visibles.first().id, ultima = a.id == visibles.last().id,
                        alAbrir = { abierta = if (abierta == a.id) null else a.id },
                        cambiar = { cambio -> cambiarPersona(a.id, cambio) },
                        borrar = {
                            val pos = padron.afiliados.indexOfFirst { it.id == a.id }
                            estado.cambiarPadron { p -> p.copy(afiliados = p.afiliados.filter { it.id != a.id }) }
                            abierta = null
                            avisos.mostrar("Borraste a ${nombreCompleto(a)}") {
                                estado.cambiarPadron { p -> p.copy(afiliados = p.afiliados.toMutableList().also { it.add(maxOf(0, minOf(pos, it.size)), a) }) }
                            }
                        }
                    )
                }
                item {
                    Spacer(Modifier.height(Medidas.e5))
                    TituloSeccion("Exportar y cargar desde Excel", apoyo = true)
                    Pista("Exportar el padrón y cargar uno desde Excel llegan en una fase siguiente de la app nativa; mientras tanto, la app web sigue haciéndolo.")
                    EstadoGuardado(guardado, "Se guarda solo, mientras escribes.")
                }
            }
        }
        BotonFlotante("Inscribir", { ctx.ir(Ruta.INSCRIBIR) }, Modifier.align(Alignment.BottomEnd).padding(end = Medidas.e4, bottom = Medidas.e4))
    }
}

/** Una línea por persona; abierta, el editor. */
@Composable
private fun FilaPersona(
    a: Afiliado, fechaRef: String, errores: Int, abierta: Boolean, primera: Boolean, ultima: Boolean,
    alAbrir: () -> Unit, cambiar: ((Afiliado) -> Afiliado) -> Unit, borrar: () -> Unit
) {
    val c = colores
    val forma = RoundedCornerShape(
        topStart = if (primera) Medidas.rM else 0.dp, topEnd = if (primera) Medidas.rM else 0.dp,
        bottomStart = if (ultima) Medidas.rM else 0.dp, bottomEnd = if (ultima) Medidas.rM else 0.dp
    )
    val g = grupoEtario(a, fechaRef)
    val edad = if (esIso(a.fechaNacimiento)) edadEnFecha(a.fechaNacimiento, fechaRef) else null
    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = alAbrir).defaultMinSize(minHeight = 64.dp).padding(Medidas.e4, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(a, g)
            Spacer(Modifier.width(Medidas.e3))
            Column(Modifier.weight(1f)) {
                Text(nombreCompleto(a).ifEmpty { "Persona sin nombre" }, style = tipos.textoFuerte, color = if (a.activo) c.titulo else c.tintaSuave)
                // El documento va primero: es lo que se busca al cotejar la lista contra un papel.
                val meta = buildList {
                    if (a.numeroDocumento.isNotEmpty()) add(a.numeroDocumento)
                    add((if (g != null) etiquetaGrupoEtario(g) else "Sin grupo") + (if (edad == null) "" else ", $edad años"))
                    if (a.tipoAfiliado == "caso_social") add("Ayuda social") else if (a.tipoAfiliado.isEmpty()) add("Sin tipo")
                    if (!a.activo) add("Dada de baja")
                }
                Text(meta.joinToString(" · "), style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 2.dp))
            }
            if (errores > 0) { Spacer(Modifier.width(Medidas.e2)); Pastilla("faltan datos", TonoPastilla.ADVERTENCIA) }
            Spacer(Modifier.width(Medidas.e2))
            Icono("flecha", 18.dp, if (abierta) c.acentoTexto else c.tintaSuave, Modifier.rotate(if (abierta) 90f else 0f))
        }
        if (abierta) {
            Separador()
            EditorPersona(a, cambiar, borrar)
        }
        if (!ultima) Separador()
    }
}

@Composable
private fun EditorPersona(a: Afiliado, cambiar: ((Afiliado) -> Afiliado) -> Unit, borrar: () -> Unit) {
    val c = colores
    var pidiendoBaja by remember { mutableStateOf(false) }
    var confirmandoBorrar by remember { mutableStateOf(false) }
    val tieneFecha = esIso(a.fechaNacimiento)
    Column(Modifier.padding(Medidas.e4, Medidas.e1, Medidas.e4, Medidas.e4)) {
        Acciones(Modifier.padding(vertical = 10.dp)) {
            Boton(if (a.activo) "Dar de baja" else "Reactivar", {
                if (a.activo) pidiendoBaja = true else cambiar { it.copy(activo = true, bajaEn = "", motivoBaja = "") }
            }, diminuto = true)
            Boton("Borrar", { confirmandoBorrar = true }, diminuto = true, tipo = TipoBoton.PELIGRO)
        }
        Campo("Apellido paterno", a.apellidoPaterno, { v -> cambiar { it.copy(apellidoPaterno = v) } })
        Spacer(Modifier.height(Medidas.e3))
        Campo("Apellido materno", a.apellidoMaterno, { v -> cambiar { it.copy(apellidoMaterno = v) } })
        Spacer(Modifier.height(Medidas.e3))
        Campo("Nombres", a.nombres, { v -> cambiar { it.copy(nombres = v) } })
        Spacer(Modifier.height(Medidas.e3))
        Leyenda("Tipo de documento")
        Opciones(listOf("dni" to "DNI", "ce" to "Carné de extranjería"), { it == a.tipoDocumento }, { v -> cambiar { it.copy(tipoDocumento = v) } })
        Spacer(Modifier.height(Medidas.e3))
        Campo("Número de documento", a.numeroDocumento, { v -> cambiar { it.copy(numeroDocumento = v) } }, pista = "Obligatorio. DNI: 8 dígitos.", soloDigitos = a.tipoDocumento == "dni", maxLargo = 12)
        Spacer(Modifier.height(Medidas.e3))
        Leyenda("Tipo")
        Opciones(TIPOS_AFILIADO.map { it.clave to it.etiqueta }, { it == a.tipoAfiliado }, { v -> cambiar { it.copy(tipoAfiliado = v) } })
        Pista("Define el menú que le toca por defecto.", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(Medidas.e3))
        CampoFecha("Fecha de nacimiento", a.fechaNacimiento, { v -> cambiar { it.copy(fechaNacimiento = v) } }, pista = "Opcional.")
        Spacer(Modifier.height(Medidas.e3))
        Leyenda("Grupo de edad")
        Opciones(TRAMOS_ETARIOS.map { it.clave to "${it.etiqueta} (${it.detalle})" }, { it == a.grupoEtarioManual }, { v -> cambiar { it.copy(grupoEtarioManual = v) } }, habilitado = !tieneFecha)
        Pista(if (tieneFecha) "Se calcula desde la fecha de nacimiento." else "Obligatorio si no hay fecha de nacimiento.", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(Medidas.e3))
        Leyenda("Sexo")
        Opciones(listOf("" to "No indicado", "F" to "F", "M" to "M"), { it == a.sexo }, { v -> cambiar { it.copy(sexo = v) } })
        Pista("Opcional.", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(Medidas.e3))
        CampoFecha("Fecha de alta", a.altaEn, { v -> cambiar { it.copy(altaEn = v) } })
        Spacer(Modifier.height(Medidas.e3))
        Campo("Quién verificó los datos", a.verificadoPor, { v -> cambiar { it.copy(verificadoPor = v) } }, pista = "Obligatorio si los datos vinieron de un escaneo.")
        Spacer(Modifier.height(Medidas.e3))
        Campo("Nota", a.nota, { v -> cambiar { it.copy(nota = v) } })
    }
    if (pidiendoBaja) {
        var motivo by remember { mutableStateOf(a.motivoBaja) }
        AlertDialog(
            onDismissRequest = { pidiendoBaja = false },
            containerColor = c.superficie,
            title = { Text("Dar de baja a ${nombreCompleto(a)}", style = tipos.seccion, color = c.titulo) },
            text = { Column { Pista("Queda en el historial: antes de hoy sigue contando.", Modifier.padding(bottom = Medidas.e3)); Campo("Motivo de la baja (opcional)", motivo, { motivo = it }) } },
            confirmButton = { Boton("Dar de baja", { cambiar { it.copy(activo = false, bajaEn = hoyIso(), motivoBaja = motivo) }; pidiendoBaja = false }, tipo = TipoBoton.PRINCIPAL) },
            dismissButton = { Boton("Cancelar", { pidiendoBaja = false }) }
        )
    }
    if (confirmandoBorrar) {
        AlertDialog(
            onDismissRequest = { confirmandoBorrar = false },
            containerColor = c.superficie,
            title = { Text("¿Borrar a ${nombreCompleto(a).ifEmpty { "esta persona" }}?", style = tipos.seccion, color = c.titulo) },
            text = { Text("Se borrará definitivamente del padrón. Si solo dejó de asistir, usa «Dar de baja»: así queda el historial.", style = tipos.texto, color = c.tinta) },
            confirmButton = { Boton("Borrar de todas formas", { confirmandoBorrar = false; borrar() }, tipo = TipoBoton.PELIGRO) },
            dismissButton = { Boton("Cancelar", { confirmandoBorrar = false }) }
        )
    }
}

/**
 * Inscribir: una pantalla propia, no una ventana encima del padrón. Los datos
 * en una lista que se desplaza; abajo, siempre a la vista y sobre el teclado,
 * qué falta (o a quién se inscribió) y el botón. Queda abierta para inscribir
 * a varias seguidas; salir con algo escrito pregunta antes de perderlo.
 */
@Composable
fun PantallaInscribir(ctx: Contexto) {
    val estado = ctx.estado
    val c = colores
    var paterno by remember { mutableStateOf("") }
    var materno by remember { mutableStateOf("") }
    var nombres by remember { mutableStateOf("") }
    var tipoDoc by remember { mutableStateOf("dni") }
    var doc by remember { mutableStateOf("") }
    var nacimiento by remember { mutableStateOf("") }
    var grupo by remember { mutableStateOf<String?>(null) }
    var tipo by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var mensajeOk by remember { mutableStateOf(false) }
    var confirmarNombre by remember { mutableStateOf<Afiliado?>(null) }
    var saliendo by remember { mutableStateOf(false) }
    // Leído del DNI y todavía sin inscribir: el sexo, quién revisó y qué revisar.
    var escaneo by remember { mutableStateOf<String?>(null) }
    var verifica by remember { mutableStateOf("") }
    var notaDni by remember { mutableStateOf<List<String>>(emptyList()) }
    var avisoDni by remember { mutableStateOf("") }
    var dudas by remember { mutableStateOf<List<Triple<String, List<String>, (String) -> Unit>>>(emptyList()) }
    val desplazamiento = rememberScrollState()
    val alcance = rememberCoroutineScope()
    val tieneFecha = esIso(nacimiento)
    val hayDatos = listOf(paterno, materno, nombres, doc, nacimiento, tipo).any { it.isNotBlank() } || grupo != null || escaneo != null

    fun salir() { if (hayDatos) saliendo = true else ctx.volver() }
    BackHandler(enabled = hayDatos) { saliendo = true }

    fun avisar(texto: String, ok: Boolean = false) { mensaje = texto; mensajeOk = ok }

    fun armar(): Afiliado = nuevoAfiliado().copy(
        apellidoPaterno = paterno.trim(), apellidoMaterno = materno.trim(), nombres = nombres.trim(),
        tipoDocumento = tipoDoc, numeroDocumento = doc.trim(), fechaNacimiento = nacimiento,
        grupoEtarioManual = if (tieneFecha) null else grupo, tipoAfiliado = tipo
    ).let { a -> escaneo?.let { sexo -> a.copy(origenDato = "escaneo_dni", sexo = sexo, verificadoPor = verifica.trim()) } ?: a }

    /**
     * Llena el formulario con lo leído del DNI (llenarConDni de ui-personas.js).
     * No inscribe: una letra dudosa queda vacía con sus opciones, y se dice qué revisar.
     */
    fun llenarConDni(l: LecturaDni) {
        val m = l.mrz.campos
        notaDni = emptyList(); dudas = emptyList(); avisoDni = ""
        if (m.pais != "PER") { avisoDni = "No parece un DNI peruano. Escribe los datos a mano."; return }
        tipoDoc = "dni"; doc = m.numeroDocumento; nacimiento = m.fechaNacimiento
        val revisar = ArrayList<String>()
        val ds = ArrayList<Triple<String, List<String>, (String) -> Unit>>()
        fun nombre(etiqueta: String, cruce: CampoCruzado?, deMrz: String, poner: (String) -> Unit) {
            when {
                cruce == null -> { poner(deMrz); if (deMrz.isNotEmpty()) revisar.add("$etiqueta: viene sin tildes ni ñ.") }
                cruce.estado == EstadoCampo.DUDOSO -> { poner(""); ds.add(Triple(etiqueta, cruce.opciones, poner)) }
                else -> {
                    poner(cruce.valor)
                    if (cruce.estado == EstadoCampo.SIN_CONFIRMAR && cruce.valor.isNotEmpty()) revisar.add("$etiqueta: compáralo con el DNI.")
                }
            }
        }
        nombre("Apellido paterno", l.anverso?.apellidoPaterno, m.apellidos.joinToString(" ")) { paterno = it }
        nombre("Apellido materno", l.anverso?.apellidoMaterno, "") { materno = it }
        nombre("Nombres", l.anverso?.nombres, m.nombres) { nombres = it }
        if (materno.isEmpty()) revisar.add("Apellido materno: escríbelo (no se leyó).")
        escaneo = m.sexo
        // Quien usa el teléfono es quien revisa: su nombre del perfil, editable.
        if (verifica.isBlank()) verifica = ctx.preferencias.perfil.value.nombre
        notaDni = revisar; dudas = ds
    }

    fun inscribirDeVerdad(a: Afiliado) {
        estado.cambiarPadron { it.copy(afiliados = it.afiliados + a) }
        avisar("${nombreCompleto(a)} quedó inscrita. Puedes inscribir a otra persona.", ok = true)
        paterno = ""; materno = ""; nombres = ""; doc = ""; nacimiento = ""; grupo = null; tipo = ""
        escaneo = null; notaDni = emptyList(); dudas = emptyList(); avisoDni = ""
        alcance.launch { desplazamiento.animateScrollTo(0) }
    }

    fun inscribir() {
        val a = armar()
        val padron = estado.padron.value
        val faltan = ArrayList<String>()
        if (a.apellidoPaterno.isEmpty()) faltan.add("apellido paterno")
        if (a.nombres.isEmpty()) faltan.add("nombres")
        if (a.numeroDocumento.isEmpty()) faltan.add("número de documento")
        if (grupoEtario(a, hoyIso()) == null) faltan.add("grupo de edad")
        if (a.tipoAfiliado.isEmpty()) faltan.add("tipo")
        if (escaneo != null && a.verificadoPor.isEmpty()) faltan.add("quién revisó los datos con el DNI")
        if (faltan.isNotEmpty()) { avisar("Falta: ${faltan.joinToString(", ")}."); return }
        // Lleno no es lo mismo que bien escrito: las mismas reglas que «Les faltan datos».
        val malEscritos = validarAfiliado(a, padron).filter { it.severidad == Severidad.ERROR }
        if (malEscritos.isNotEmpty()) {
            avisar("Revisa: ${malEscritos.take(3).joinToString("; ") { it.mensaje.removeSuffix(".") }}. No se inscribió a nadie.")
            return
        }
        // Duplicado por documento: se avisa ANTES de inscribir, no después.
        val repetido = padron.afiliados.firstOrNull { it.activo && it.numeroDocumento == a.numeroDocumento }
        if (repetido != null) { avisar("El documento ${a.numeroDocumento} ya está registrado en ${nombreCompleto(repetido)}. No se inscribió a nadie."); return }
        val mismoNombre = padron.afiliados.firstOrNull { it.activo && normalizarNombre(nombreCompleto(it)) == normalizarNombre(nombreCompleto(a)) }
        if (mismoNombre != null) { confirmarNombre = a; return }
        inscribirDeVerdad(a)
    }

    Column(Modifier.fillMaxSize().background(c.fondo)) {
        Cabecera(ctx, "Inscribir a una persona", "Los campos con * son obligatorios", alVolver = ::salir)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(desplazamiento).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, Medidas.e5)
        ) {
            // ---- leer el DNI (en prueba) ----
            Pista("En prueba: lee las tres líneas con «<» y los nombres del lado de la foto (en el DNI azul están en la misma cara). Revisa todo antes de inscribir.", Modifier.padding(bottom = Medidas.e2))
            Boton("Leer el DNI con la cámara", { ctx.abrirCapa { CamaraDni(alCerrar = ctx::cerrarCapa, alLeer = ::llenarConDni) } }, icono = "camara")
            if (avisoDni.isNotEmpty()) Text(avisoDni, style = tipos.chicoFuerte, color = c.aviso, modifier = Modifier.padding(top = Medidas.e2))
            if (escaneo != null) {
                Text("Listo. Revisa los datos con el DNI en la mano.", style = tipos.textoFuerte, color = c.ok, modifier = Modifier.padding(top = Medidas.e3))
                for ((etiqueta, opciones, poner) in dudas) {
                    // No se elige sola: la persona toca la que coincide con el DNI.
                    Text("$etiqueta: ¿cuál dice el DNI?", style = tipos.chicoFuerte, color = c.aviso, modifier = Modifier.padding(top = Medidas.e2))
                    Acciones {
                        for (o in opciones) Boton(o, { poner(o); dudas = dudas.filter { it.first != etiqueta } }, diminuto = true)
                    }
                }
                for (t in notaDni) Text("• $t", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(Medidas.e3))
                Campo("Quién revisó los datos con el DNI", verifica, { verifica = it }, obligatorio = true, capitalizar = true)
            }
            Spacer(Modifier.height(Medidas.e4))
            Separador()
            Spacer(Modifier.height(Medidas.e4))
            Campo("Apellido paterno", paterno, { paterno = it }, obligatorio = true, capitalizar = true)
            Spacer(Modifier.height(Medidas.e3))
            Campo("Apellido materno", materno, { materno = it }, capitalizar = true)
            Spacer(Modifier.height(Medidas.e3))
            Campo("Nombres", nombres, { nombres = it }, obligatorio = true, capitalizar = true)
            Spacer(Modifier.height(Medidas.e4))
            Leyenda("Documento")
            Opciones(listOf("dni" to "DNI", "ce" to "Carné de extranjería"), { it == tipoDoc }, { tipoDoc = it })
            Spacer(Modifier.height(Medidas.e3))
            Campo("Número", doc, { doc = it }, obligatorio = true, soloDigitos = tipoDoc == "dni", maxLargo = 12)
            Spacer(Modifier.height(Medidas.e4))
            CampoFecha("Fecha de nacimiento (opcional)", nacimiento, { nacimiento = it }, pista = "Si la pones, el grupo de edad se calcula solo y se actualiza con los años.")
            Spacer(Modifier.height(Medidas.e4))
            Leyenda("Grupo de edad", obligatorio = true)
            Opciones(TRAMOS_ETARIOS.map { it.clave to "${it.etiqueta} (${it.detalle})" }, { it == grupo }, { grupo = it }, habilitado = !tieneFecha)
            if (tieneFecha) Pista("Con esa fecha, hoy le corresponde: ${etiquetaGrupoEtario(grupoEtario(Afiliado(fechaNacimiento = nacimiento), hoyIso()))}.", Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(Medidas.e4))
            Leyenda("Tipo", obligatorio = true)
            Opciones(TIPOS_AFILIADO.map { it.clave to it.etiqueta }, { it == tipo }, { tipo = it })
        }
        // La barra de abajo: sobre la barra de gestos y, con el teclado abierto, sobre el teclado.
        Column(Modifier.fillMaxWidth().background(c.superficie)) {
            Separador()
            Column(
                Modifier.widthIn(max = Medidas.anchoColumna).align(Alignment.CenterHorizontally).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(Medidas.e4, Medidas.e3)
            ) {
                if (mensaje.isNotEmpty()) {
                    Text(
                        mensaje, style = tipos.chicoFuerte, color = if (mensajeOk) c.ok else c.error,
                        modifier = Modifier.padding(bottom = Medidas.e2).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Boton("Inscribir", ::inscribir, Modifier.fillMaxWidth(), tipo = TipoBoton.PRINCIPAL, icono = "personaMas")
            }
        }
    }


    if (saliendo) {
        AlertDialog(
            onDismissRequest = { saliendo = false },
            containerColor = c.superficie,
            title = { Text("¿Salir sin inscribir?", style = tipos.seccion, color = c.titulo) },
            text = { Text("Lo que escribiste de esta persona se perderá.", style = tipos.texto, color = c.tinta) },
            confirmButton = { Boton("Salir sin inscribir", { saliendo = false; ctx.volver() }, tipo = TipoBoton.PELIGRO) },
            dismissButton = { Boton("Seguir aquí", { saliendo = false }) }
        )
    }
    val pendiente = confirmarNombre
    if (pendiente != null) {
        AlertDialog(
            onDismissRequest = { confirmarNombre = null },
            containerColor = c.superficie,
            title = { Text("Ya hay alguien con ese nombre", style = tipos.seccion, color = c.titulo) },
            text = { Text("Ya hay alguien con el nombre ${nombreCompleto(pendiente)} en el padrón. ¿Inscribir igual? Pueden ser dos personas distintas.", style = tipos.texto, color = c.tinta) },
            confirmButton = { Boton("Inscribir igual", { inscribirDeVerdad(pendiente); confirmarNombre = null }, tipo = TipoBoton.PRINCIPAL) },
            dismissButton = { Boton("Cancelar", { confirmarNombre = null }) }
        )
    }
}
