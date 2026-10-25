// Inscribir, listar y corregir personas. La olla inscribe a quien llega y queda
// pendiente hasta que la Municipalidad la registre; la hoja municipal (Importar.kt)
// confirma a las pendientes y actualiza el resto.
package io.github.tmrainer.asistencia.ui.pantallas

import io.github.tmrainer.asistencia.nucleo.documentos.excelDePendientes
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDePendientes
import io.github.tmrainer.asistencia.nucleo.padron.inscribirEnLaOlla
import io.github.tmrainer.asistencia.nucleo.dni.EstadoCampo
import io.github.tmrainer.asistencia.nucleo.dni.CampoCruzado
import io.github.tmrainer.asistencia.ui.ListaParaGuia
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.camara.LecturaDni
import io.github.tmrainer.asistencia.ui.camara.CamaraDni
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.padron.retirarPorLaHoja
import io.github.tmrainer.asistencia.nucleo.padron.ultimaHoja
import io.github.tmrainer.asistencia.nucleo.padron.agregarDeLaHoja
import androidx.compose.foundation.layout.Arrangement
import io.github.tmrainer.asistencia.nucleo.envio.correoDelPadron
import io.github.tmrainer.asistencia.nucleo.documentos.selloAhora
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelPadron
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelPadron
import io.github.tmrainer.asistencia.ui.componentes.horaAhora
import io.github.tmrainer.asistencia.ui.componentes.Documento
import io.github.tmrainer.asistencia.ui.componentes.BloqueEnvio
import io.github.tmrainer.asistencia.ui.componentes.BotonesExportar
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
import io.github.tmrainer.asistencia.ui.GUIA_INSCRIBIR
import io.github.tmrainer.asistencia.ui.GUIA_PADRON
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

private val FILTROS = listOf("activos" to "Activas", "pendientes" to "Faltan en la Municipalidad", "incompletas" to "Les faltan datos", "ayuda_social" to "Ayuda social", "bajas" to "De baja", "todas" to "Todas")

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
    val hojaActual = remember(padron.hojasMunicipales) { ultimaHoja(padron) }
    val periodo = remember { estado.periodoPorDefecto() }
    var repInicio by remember { mutableStateOf(periodo.inicio) }
    var repFin by remember { mutableStateOf(periodo.fin) }

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
                "pendientes" -> a.activo && a.registroMunicipal == "pendiente"
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
            Cabecera(ctx, "Padrón", (if (hojaActual != null) "Hoja del ${aDdMmAa(hojaActual.fecha)} · " else "") + cuantos(padron.afiliados.count { it.activo }, "persona activa", "personas activas"), guia = GUIA_PADRON)
            ListaParaGuia(listaEstado)
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
                        Campo("Buscar", busqueda, { busqueda = it }, Modifier.objetivoGuia("padron.buscar"), placeholder = "Nombre o DNI")
                        Spacer(Modifier.height(Medidas.e3))
                        CampoFecha("Edades a la fecha", fechaRef, { if (it.isNotEmpty()) fechaRef = it })
                        Spacer(Modifier.height(10.dp))
                        val pendientes = padron.afiliados.count { it.activo && it.registroMunicipal == "pendiente" }
                        // Sin pendientes, el filtro desaparece: no se queda elegido y escondido.
                        if (filtro == "pendientes" && pendientes == 0) filtro = "activos"
                        Box(Modifier.objetivoGuia("padron.filtros")) { Opciones(FILTROS.filter { it.first != "pendientes" || pendientes > 0 }.map { (v, t) ->
                            v to when { v == "incompletas" && incompletas > 0 -> "$t ($incompletas)"; v == "pendientes" -> "$t ($pendientes)"; else -> t }
                        }, { it == filtro }, { filtro = it }) }
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
                            if (padron.afiliados.isEmpty()) "Aún no hay nadie. Toca «Inscribir» o carga la hoja de la Municipalidad." else "Nadie coincide con la búsqueda o el filtro.",
                            style = tipos.pista, color = c.tintaSuave,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e4, Medidas.e6),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                items(visibles, key = { it.id }) { a ->
                    // La primera fila es la que muestra la guía («Las personas de la olla»).
                    Box(if (a.id == visibles.first().id) Modifier.objetivoGuia("padron.lista") else Modifier) {
                        FilaPersona(
                            a = a, fechaRef = fechaRef, errores = erroresPor[a.id] ?: 0, abierta = abierta == a.id,
                            primera = a.id == visibles.first().id, ultima = a.id == visibles.last().id,
                            alAbrir = { abierta = if (abierta == a.id) null else a.id },
                            cambiar = { cambio -> cambiarPersona(a.id, cambio) },
                            retirar = { fecha -> estado.cambiarPadron { retirarPorLaHoja(it, a.id, fecha) } },
                            fechaUltimaHoja = hojaActual?.fecha,
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
                }
                item {
                    Spacer(Modifier.height(Medidas.e5))
                    TituloSeccion("Exportar el padrón", apoyo = true)
                    Pista("Las personas activas, con los días que vinieron entre estas fechas.", Modifier.padding(bottom = Medidas.e3))
                    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                        CampoFecha("Días asistidos, desde", repInicio, { if (it.isNotEmpty()) repInicio = it }, Modifier.weight(1f))
                        CampoFecha("Hasta", repFin, { if (it.isNotEmpty()) repFin = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Medidas.e3))
                    val centro = estado.config.value.ultimoCentro?.nombreCentro?.trim().orEmpty()
                    fun vacio(): Boolean {
                        if (estado.padron.value.afiliados.isNotEmpty()) return false
                        avisos.mostrar("El padrón está vacío: inscribe a alguien o carga la hoja.")
                        return true
                    }
                    BotonesExportar(
                        pdf = { if (vacio()) null else Documento(pdfDelPadron(estado.padron.value, fechaRef, repInicio, repFin, centro, selloAhora()), "Padron ${hoyIso()}", "Padrón (copia de referencia)") },
                        excel = { if (vacio()) null else Documento(excelDelPadron(estado.padron.value, fechaRef, repInicio, repFin, centro), "Padron ${hoyIso()}", "Padrón") }
                    )
                    Pista("El Excel se puede cargar en otro teléfono con «Cargar la hoja».", Modifier.padding(top = Medidas.e2))

                    // Las inscritas aquí que falta llevar a la Municipalidad: la lista para el trámite.
                    val porRegistrar = padron.afiliados.count { it.activo && it.registroMunicipal == "pendiente" }
                    if (porRegistrar > 0) {
                        Spacer(Modifier.height(Medidas.e5))
                        TituloSeccion("Para llevar a la Municipalidad", apoyo = true)
                        Pista("${if (porRegistrar == 1) "1 persona inscrita aquí falta" else "$porRegistrar personas inscritas aquí faltan"} en la Municipalidad. Lleva esta lista al trámite.", Modifier.padding(bottom = Medidas.e3))
                        BotonesExportar(
                            pdf = { Documento(pdfDePendientes(estado.padron.value, centro, ctx.preferencias.perfil.value.nombre, selloAhora()), "Para registrar ${hoyIso()}", "Para registrar en la Municipalidad") },
                            excel = { Documento(excelDePendientes(estado.padron.value, centro), "Para registrar ${hoyIso()}", "Para registrar en la Municipalidad") }
                        )
                    }

                    Spacer(Modifier.height(Medidas.e5))
                    TituloSeccion("Enviar el padrón a UNACEM", apoyo = true)
                    val hoy = hoyIso()
                    BloqueEnvio(
                        ctx, "padron", hoy,
                        "El 15 y a fin de mes, envía el padrón en Excel a UNACEM.",
                        siNoHay = "No hay personas activas: no hay nada que enviar.",
                        armar = { if (estado.padron.value.afiliados.none { it.activo }) null else correoDelPadron(estado.padron.value, hoy, centro, horaAhora(), ctx.preferencias.perfil.value.nombre) }
                    )

                    Spacer(Modifier.height(Medidas.e5))
                    TituloSeccion("La hoja de la Municipalidad", apoyo = true)
                    Pista("Cada hoja nueva actualiza el padrón. Antes de guardar verás qué cambia.", Modifier.padding(bottom = Medidas.e3))
                    Boton("Cargar la hoja (Excel)", { ctx.ir(Ruta.IMPORTAR) }, Modifier.objetivoGuia("padron.hoja"), icono = "archivo")
                    Pista("¿Hoja en papel? En «Inscribir», elige «Ya está en la hoja de la Municipalidad».", Modifier.padding(top = Medidas.e2))
                    HistorialHojas(padron)
                    EstadoGuardado(guardado, "Se guarda solo, mientras escribes.")
                }
            }
        }
        BotonFlotante("Inscribir", { ctx.ir(Ruta.INSCRIBIR) }, Modifier.align(Alignment.BottomEnd).padding(end = Medidas.e4, bottom = Medidas.e4).objetivoGuia("padron.inscribir"))
    }
}

/** Una línea por persona; abierta, el editor. */
@Composable
private fun FilaPersona(
    a: Afiliado, fechaRef: String, errores: Int, abierta: Boolean, primera: Boolean, ultima: Boolean,
    alAbrir: () -> Unit, cambiar: ((Afiliado) -> Afiliado) -> Unit, borrar: () -> Unit, retirar: (String) -> Unit, fechaUltimaHoja: String?
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
            else if (a.activo && a.registroMunicipal == "pendiente") { Spacer(Modifier.width(Medidas.e2)); Pastilla("falta la Municipalidad", TonoPastilla.ADVERTENCIA) }
            Spacer(Modifier.width(Medidas.e2))
            Icono("flecha", 18.dp, if (abierta) c.acentoTexto else c.tintaSuave, Modifier.rotate(if (abierta) 90f else 0f))
        }
        if (abierta) {
            Separador()
            EditorPersona(a, cambiar, borrar, retirar, fechaUltimaHoja)
        }
        if (!ultima) Separador()
    }
}

@Composable
private fun EditorPersona(a: Afiliado, cambiar: ((Afiliado) -> Afiliado) -> Unit, borrar: () -> Unit, retirar: (String) -> Unit, fechaUltimaHoja: String?) {
    val c = colores
    var pidiendoBaja by remember { mutableStateOf(false) }
    var confirmandoBorrar by remember { mutableStateOf(false) }
    val tieneFecha = esIso(a.fechaNacimiento)
    Column(Modifier.padding(Medidas.e4, Medidas.e1, Medidas.e4, Medidas.e4)) {
        Acciones(Modifier.padding(vertical = 10.dp)) {
            if (a.activo && a.registroMunicipal == "pendiente") {
                Boton("Ya la registraron en la Municipalidad", { cambiar { it.copy(registroMunicipal = "registrada", registroMunicipalEn = hoyIso()) } }, diminuto = true)
            }
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
        // Lo normal: ya no figura en la hoja nueva de la Municipalidad (queda anotado en esa hoja).
        var porLaHoja by remember { mutableStateOf(true) }
        var fechaHoja by remember { mutableStateOf(fechaUltimaHoja ?: hoyIso()) }
        AlertDialog(
            onDismissRequest = { pidiendoBaja = false },
            containerColor = c.superficie,
            title = { Text("Dar de baja a ${nombreCompleto(a)}", style = tipos.seccion, color = c.titulo) },
            text = {
                Column {
                    Pista("Su asistencia anterior sigue contando.", Modifier.padding(bottom = Medidas.e3))
                    Opciones(listOf(true to "No figura en la hoja nueva", false to "Otro motivo"), { it == porLaHoja }, { porLaHoja = it })
                    Spacer(Modifier.height(Medidas.e3))
                    if (porLaHoja) CampoFecha("Fecha de la hoja nueva", fechaHoja, { if (it.isNotEmpty()) fechaHoja = it })
                    else Campo("Motivo de la baja (opcional)", motivo, { motivo = it })
                }
            },
            confirmButton = {
                Boton("Dar de baja", {
                    if (porLaHoja) retirar(fechaHoja) else cambiar { it.copy(activo = false, bajaEn = hoyIso(), motivoBaja = motivo) }
                    pidiendoBaja = false
                }, tipo = TipoBoton.PRINCIPAL)
            },
            dismissButton = { Boton("Cancelar", { pidiendoBaja = false }) }
        )
    }
    if (confirmandoBorrar) {
        AlertDialog(
            onDismissRequest = { confirmandoBorrar = false },
            containerColor = c.superficie,
            title = { Text("¿Borrar a ${nombreCompleto(a).ifEmpty { "esta persona" }}?", style = tipos.seccion, color = c.titulo) },
            text = { Text("Se borra del padrón para siempre. Si dejó de venir, usa «Dar de baja».", style = tipos.texto, color = c.tinta) },
            confirmButton = { Boton("Borrar de todas formas", { confirmandoBorrar = false; borrar() }, tipo = TipoBoton.PELIGRO) },
            dismissButton = { Boton("Cancelar", { confirmandoBorrar = false }) }
        )
    }
}

/**
 * Copiar de la hoja en papel: una pantalla propia, no una ventana encima del padrón. Los datos
 * en una lista que se desplaza; abajo, siempre a la vista y sobre el teclado,
 * qué falta (o a quién se copió) y el botón. Al inscribir a quien llegó a la
 * olla se cierra; copiando de la hoja queda abierta para copiar a varias
 * seguidas. Salir con algo escrito pregunta antes de perderlo.
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
    // Leído un DNI que ya está en el padrón: se avisa y no se llena nada.
    var yaInscrita by remember { mutableStateOf<Afiliado?>(null) }
    var saliendo by remember { mutableStateOf(false) }
    // De dónde viene: llegó a la olla (queda pendiente de la
    // Municipalidad) o ya figura en la hoja municipal (se copia con su fecha).
    var deLaHoja by remember { mutableStateOf(false) }
    var fechaHoja by remember { mutableStateOf(ultimaHoja(estado.padron.value)?.fecha ?: hoyIso()) }
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
     * Llena el formulario con lo leído del DNI.
     * No inscribe: una letra dudosa queda vacía con sus opciones, y se dice qué revisar.
     */
    fun inscritaCon(dni: String) = estado.padron.value.afiliados.firstOrNull { it.activo && it.numeroDocumento == dni }

    fun llenarConDni(l: LecturaDni) {
        val m = l.mrz.campos
        notaDni = emptyList(); dudas = emptyList(); avisoDni = ""
        if (m.pais != "PER") { avisoDni = "No parece un DNI peruano. Escribe los datos a mano."; return }
        inscritaCon(m.numeroDocumento)?.let { yaInscrita = it; return }
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
        if (deLaHoja) {
            estado.cambiarPadron { agregarDeLaHoja(it, a, fechaHoja) }
            avisar("${nombreCompleto(a)} quedó en el padrón. Puedes copiar a otra persona.", ok = true)
        } else {
            estado.cambiarPadron { inscribirEnLaOlla(it, a) }
            // Inscrita: la pantalla se cierra y el aviso queda abajo, en la de antes.
            ctx.avisos.mostrar("${nombreCompleto(a)} quedó inscrita. Llévala a la Municipalidad para registrarla.")
            ctx.volver()
            return
        }
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
        if (deLaHoja && !esIso(fechaHoja)) { avisar("Falta la fecha de la hoja."); return }
        if (malEscritos.isNotEmpty()) {
            avisar("Revisa: ${malEscritos.take(3).joinToString("; ") { it.mensaje.removeSuffix(".") }}. No se agregó a nadie.")
            return
        }
        // Duplicado por documento: se avisa ANTES de agregar, no después.
        val repetido = padron.afiliados.firstOrNull { it.activo && it.numeroDocumento == a.numeroDocumento }
        if (repetido != null) { avisar("El documento ${a.numeroDocumento} ya está en el padrón: ${nombreCompleto(repetido)}. No se agregó a nadie."); return }
        val mismoNombre = padron.afiliados.firstOrNull { it.activo && normalizarNombre(nombreCompleto(it)) == normalizarNombre(nombreCompleto(a)) }
        if (mismoNombre != null) { confirmarNombre = a; return }
        inscribirDeVerdad(a)
    }

    Column(Modifier.fillMaxSize().background(c.fondo)) {
        Cabecera(ctx, "Inscribir a una persona", "Los campos con * son obligatorios", guia = GUIA_INSCRIBIR, alVolver = ::salir)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(desplazamiento).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, Medidas.e5)
        ) {
            Leyenda("¿De dónde viene?", obligatorio = true)
            Opciones(listOf(false to "Llegó a la olla", true to "Ya está en la hoja de la Municipalidad"), { it == deLaHoja }, { deLaHoja = it })
            if (deLaHoja) {
                Pista("Cópiala tal como está en la hoja.", Modifier.padding(top = Medidas.e2, bottom = Medidas.e2))
                CampoFecha("Fecha de la hoja", fechaHoja, { if (it.isNotEmpty()) fechaHoja = it }, obligatorio = true)
            } else {
                Pista("Queda pendiente hasta que la registren en la Municipalidad.", Modifier.padding(top = Medidas.e2, bottom = Medidas.e3))
                Pista("En prueba. Revisa todo antes de inscribir.", Modifier.padding(bottom = Medidas.e2))
                Boton("Leer el DNI con la cámara", { ctx.abrirCapa { CamaraDni(alCerrar = ctx::cerrarCapa, alLeer = ::llenarConDni, yaEsta = { inscritaCon(it) != null }) } }, Modifier.objetivoGuia("inscribir.dni"), icono = "camara")
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
            }
            Spacer(Modifier.height(Medidas.e4))
            Separador()
            Spacer(Modifier.height(Medidas.e4))
            Campo("Apellido paterno", paterno, { paterno = it }, Modifier.objetivoGuia("inscribir.paterno"), obligatorio = true, capitalizar = true)
            Spacer(Modifier.height(Medidas.e3))
            Campo("Apellido materno", materno, { materno = it }, capitalizar = true)
            Spacer(Modifier.height(Medidas.e3))
            Campo("Nombres", nombres, { nombres = it }, Modifier.objetivoGuia("inscribir.nombres"), obligatorio = true, capitalizar = true)
            Spacer(Modifier.height(Medidas.e4))
            Leyenda("Documento")
            Opciones(listOf("dni" to "DNI", "ce" to "Carné de extranjería"), { it == tipoDoc }, { tipoDoc = it })
            Spacer(Modifier.height(Medidas.e3))
            // Al escribir el número, si ya está en el padrón se dice aquí mismo: no hace falta llenar el resto.
            val yaEsta = doc.trim().takeIf { it.isNotEmpty() }?.let(::inscritaCon)
            Campo("Número", doc, { doc = it }, obligatorio = true, soloDigitos = tipoDoc == "dni", maxLargo = 12,
                aviso = yaEsta?.let { "Ya está inscrita: ${nombreCompleto(it)}. No hace falta inscribirla otra vez." })
            Spacer(Modifier.height(Medidas.e4))
            CampoFecha("Fecha de nacimiento (opcional)", nacimiento, { nacimiento = it }, pista = "Con la fecha, el grupo de edad se calcula solo.")
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
                Boton("Inscribir", ::inscribir, Modifier.fillMaxWidth().objetivoGuia("inscribir.boton"), tipo = TipoBoton.PRINCIPAL, icono = "personaMas")
            }
        }
    }


    yaInscrita?.let { a ->
        AlertDialog(
            onDismissRequest = { yaInscrita = null },
            containerColor = c.superficie,
            icon = { Icono("ok", 32.dp, c.ok) },
            title = { Text("Ya está inscrita", style = tipos.seccion, color = c.titulo) },
            text = {
                Text("${nombreCompleto(a)} (DNI ${a.numeroDocumento}) ya está en el padrón. No hace falta inscribirla otra vez.",
                    style = tipos.texto, color = c.tinta)
            },
            confirmButton = { Boton("Inscribir a otra persona", { yaInscrita = null }, tipo = TipoBoton.PRINCIPAL) },
            dismissButton = { Boton("Volver al padrón", { yaInscrita = null; ctx.ir(Ruta.PADRON) }) }
        )
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
            text = { Text("Ya hay alguien con el nombre ${nombreCompleto(pendiente)} en el padrón. ¿Inscribir igual? Pueden ser dos personas.", style = tipos.texto, color = c.tinta) },
            confirmButton = { Boton("Inscribir igual", { inscribirDeVerdad(pendiente); confirmarNombre = null }, tipo = TipoBoton.PRINCIPAL) },
            dismissButton = { Boton("Cancelar", { confirmarNombre = null }) }
        )
    }
}

/** Las hojas cargadas, de la más nueva a la más vieja: quién entró y quién salió con cada una. */
@Composable
private fun HistorialHojas(padron: io.github.tmrainer.asistencia.nucleo.modelo.Padron) {
    if (padron.hojasMunicipales.isEmpty()) return
    val c = colores
    val porId = remember(padron.afiliados) { padron.afiliados.associateBy { it.id } }
    Spacer(Modifier.height(Medidas.e4))
    Leyenda("Actualizaciones")
    Superficie {
        val hojas = padron.hojasMunicipales.sortedByDescending { it.fecha }
        hojas.forEachIndexed { i, h ->
            Column(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3)) {
                Text("Hoja del ${aDdMmAa(h.fecha)}" + if (h.personas > 0) " · ${cuantos(h.personas, "persona")}" else "", style = tipos.textoNegrita, color = c.titulo)
                fun nombres(ids: List<String>) = ids.mapNotNull { porId[it]?.let(::nombreCompleto) }.let { n -> n.take(6).joinToString(", ") + if (n.size > 6) " y ${n.size - 6} más" else "" }
                Text(
                    listOfNotNull(
                        if (h.agregados.isNotEmpty()) "Entraron ${h.agregados.size}: ${nombres(h.agregados)}." else null,
                        if (h.retirados.isNotEmpty()) "Salieron ${h.retirados.size}: ${nombres(h.retirados)}." else null
                    ).ifEmpty { listOf("Sin cambios.") }.joinToString(" "),
                    style = tipos.chico, color = c.tintaSuave
                )
            }
            if (i < hojas.size - 1) Separador()
        }
    }
}
