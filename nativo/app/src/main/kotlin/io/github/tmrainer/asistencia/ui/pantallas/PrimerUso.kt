// Lo que la app pide antes de usarse, y vuelve a pedir mientras falte algo. Es de este
// teléfono (Preferencias). Los datos los escribe la persona; la cámara lee el DNI solo
// para CONFIRMARLOS. Lo de aquí solo se PROPONE en el B-1, nunca se llena solo.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.ROLES
import io.github.tmrainer.asistencia.nucleo.dni.CampoCruzado
import io.github.tmrainer.asistencia.nucleo.dni.EstadoCampo
import io.github.tmrainer.asistencia.nucleo.dni.confirmarConDni
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.camara.CamaraDni
import io.github.tmrainer.asistencia.ui.camara.LecturaDni
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.FranjaEstado
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.IconosBarras
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.Separador
import io.github.tmrainer.asistencia.ui.componentes.Superficie
import io.github.tmrainer.asistencia.ui.componentes.TipoBoton
import io.github.tmrainer.asistencia.ui.componentes.TonoPastilla
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

/** El nombre leído del DNI: el del lado de la foto (con tildes) si se leyó; si no, el de la MRZ. */
private fun nombreDelDni(l: LecturaDni): String {
    val m = l.mrz.campos
    fun parte(cruce: CampoCruzado?, deMrz: String) =
        cruce?.valor?.takeIf { it.isNotBlank() && cruce.estado != EstadoCampo.DUDOSO } ?: deMrz
    val a = l.anverso
    return listOf(
        parte(a?.nombres, m.nombres),
        parte(a?.apellidoPaterno, m.apellidos.joinToString(" ")),
        parte(a?.apellidoMaterno, "")
    ).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Bienvenida → ¿Quién eres? → Confirma con tu DNI → ¿De qué olla eres?
 * [alTraerDatos]: el teléfono es nuevo y la olla ya usa la app en otro (va a
 * «Tus datos»). [alTerminar]: ya está todo; el perfil quedó guardado.
 */
@Composable
fun PrimerUso(ctx: Contexto, ollaPropuesta: String, alTraerDatos: () -> Unit, alTerminar: () -> Unit) {
    val prefs = ctx.preferencias
    val inicial = prefs.perfil.value
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var rol by rememberSaveable { mutableStateOf(inicial.rol) }
    var nombre by rememberSaveable { mutableStateOf(inicial.nombre) }
    var dni by rememberSaveable { mutableStateOf(inicial.dni) }
    var olla by rememberSaveable { mutableStateOf(inicial.olla.ifBlank { ollaPropuesta }) }
    // Lo leído del DNI (null: todavía no se leyó). Si se corrige a mano, se vuelve a comparar.
    var nombreLeido by rememberSaveable { mutableStateOf<String?>(null) }
    var dniLeido by rememberSaveable { mutableStateOf<String?>(null) }
    // Los errores se muestran después de tocar «Continuar» o «Terminar», no mientras se escribe.
    var intento by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = paso > 0) { intento = false; paso -= 1 }
    val c = colores
    // Sin cabecera: detrás de las barras del teléfono está el fondo de la app.
    IconosBarras(c.esOscuro, c.esOscuro)

    val faltaRol = ROLES.none { it.first == rol }
    val faltaNombre = nombre.isBlank()
    val faltaDni = dni.length != 8
    val faltaOlla = olla.isBlank()
    val confirmacion = dniLeido?.let { confirmarConDni(nombre, dni, nombreLeido.orEmpty(), it) }

    fun guardarQuienEres() = prefs.guardarPerfil(prefs.perfil.value.copy(rol = rol, nombre = nombre.trim(), dni = dni))
    fun leerDni() = ctx.abrirCapa {
        CamaraDni(alCerrar = ctx::cerrarCapa, conRevision = false, alLeer = { l ->
            nombreLeido = nombreDelDni(l)
            dniLeido = l.mrz.campos.numeroDocumento
        })
    }

    Column(
        // Los toques no pasan a la pantalla de abajo.
        Modifier.fillMaxSize().background(c.fondo).pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding().imePadding()
    ) {
        // Cada paso empieza arriba de todo.
        key(paso) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Medidas.e4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(Modifier.widthIn(max = Medidas.anchoColumna).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                    when (paso) {
                        0 -> {
                            Spacer(Modifier.height(Medidas.e5))
                            Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(c.superficie2).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                                Icono("casa", 64.dp, c.acentoTexto)
                            }
                            Text("Te damos la bienvenida", style = tituloGrande, color = c.titulo, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().semantics { heading() })
                            Text("Antes de empezar, dinos quién eres y de qué olla eres. Así la app propone tus datos en el B-1.",
                                style = tipos.texto, color = c.tintaSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            // Lo que viene, antes de empezar: tres pasos cortos, sin sorpresas.
                            Spacer(Modifier.height(Medidas.e2))
                            Superficie {
                                PasoQueViene(1, "personas", "¿Quién eres?", "Tu rol, tu nombre y tu DNI.")
                                Separador()
                                PasoQueViene(2, "camara", "Confirma con tu DNI", "La cámara revisa lo que escribiste.")
                                Separador()
                                PasoQueViene(3, "casa", "¿De qué olla eres?", "Su nombre, como en tu resolución.")
                            }
                            Row(Modifier.fillMaxWidth().padding(top = Medidas.e1), verticalAlignment = Alignment.CenterVertically) {
                                Icono("escudo", 22.dp, c.acentoTexto)
                                Text("Tus datos quedan solo en este teléfono.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(start = Medidas.e2))
                            }
                        }
                        1 -> {
                            Encabezado(1, "¿Quién eres?", "Con esto, la app te propone tu nombre y tu DNI en el B-1.")
                            Leyenda("Tu rol en la olla", obligatorio = true)
                            Opciones(ROLES, { it == rol }, { rol = it })
                            if (intento && faltaRol) Text("Elige tu rol.", style = tipos.pista, color = c.error)
                            Spacer(Modifier.height(Medidas.e2))
                            Campo("Tu nombre y apellidos", nombre, { nombre = it }, obligatorio = true, capitalizar = true,
                                pista = "Como está en tu DNI.",
                                error = if (intento && faltaNombre) "Escribe tu nombre y apellidos." else null)
                            Campo("Tu DNI", dni, { dni = it }, obligatorio = true, soloDigitos = true, maxLargo = 8,
                                error = if (intento && faltaDni) "El DNI tiene 8 números." else null)
                        }
                        2 -> {
                            Encabezado(2, "Confirma con tu DNI", "Muestra tu DNI a la cámara. La app revisa que tu nombre y tu número estén bien escritos.")
                            // Lo que escribió, para comparar con el DNI en la mano.
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e4)) {
                                Text("Lo que escribiste", style = tipos.chicoFuerte, color = c.tintaSuave)
                                Text(nombre.trim(), style = tipos.seccion, color = c.titulo, modifier = Modifier.padding(top = Medidas.e1))
                                Text("DNI $dni", style = tipos.seccion, color = c.tinta)
                            }
                            when {
                                confirmacion == null -> {}
                                confirmacion.coincide -> FranjaEstado("Coincide con tu DNI.", TonoPastilla.OK, icono = "ok")
                                else -> {
                                    val partes = buildList {
                                        if (confirmacion.numeroDistinto) add("El número de tu DNI es $dniLeido.")
                                        if (confirmacion.faltanEnDni.isNotEmpty()) add("Tu DNI dice: $nombreLeido.")
                                    }
                                    FranjaEstado("No coincide. " + partes.joinToString(" "), TonoPastilla.ADVERTENCIA, icono = "aviso")
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                                        Boton("Corregir a mano", { intento = false; paso = 1 }, Modifier.weight(1f))
                                        Boton("Leer otra vez", ::leerDni, Modifier.weight(1f), icono = "camara")
                                    }
                                }
                            }
                            // Sin cámara o con el DNI gastado, nadie se queda trabada aquí.
                            // Un botón entero, no un enlace chico: es fácil de ver y de tocar.
                            if (confirmacion?.coincide != true) Boton("No puedo leer mi DNI ahora: seguir sin confirmar", { paso = 3 }, Modifier.fillMaxWidth())
                        }
                        else -> {
                            Encabezado(3, "¿De qué olla eres?", "Con esto, la app te propone el nombre de tu olla en el B-1.")
                            Campo("Nombre de la olla", olla, { olla = it }, obligatorio = true, capitalizar = true,
                                pista = "Como sale en tu resolución o en tu B-1.",
                                error = if (intento && faltaOlla) "Escribe el nombre de tu olla." else null)
                        }
                    }
                }
            }
        }

        // Los botones, abajo y al alcance del pulgar, como en el B-1.
        if (paso > 0) Separador()
        Column(Modifier.fillMaxWidth().background(if (paso > 0) c.superficie else c.fondo).padding(Medidas.e4, Medidas.e3), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = Medidas.anchoColumna).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e3)) {
                when (paso) {
                    0 -> {
                        Boton("Empezar", { paso = 1 }, Modifier.fillMaxWidth(), tipo = TipoBoton.PRINCIPAL)
                        Boton("Ya uso la app en otro teléfono", alTraerDatos, Modifier.fillMaxWidth(), icono = "nube")
                    }
                    1 -> BotonesPaso({ intento = false; paso = 0 }, "Continuar") {
                        intento = true
                        if (!faltaRol && !faltaNombre && !faltaDni) { guardarQuienEres(); intento = false; paso = 2 }
                    }
                    2 -> when {
                        confirmacion == null -> BotonesPaso({ paso = 1 }, "Leer mi DNI", icono = "camara", alSiguiente = ::leerDni)
                        confirmacion.coincide -> BotonesPaso({ paso = 1 }, "Continuar") { paso = 3 }
                        // Lo más probable: que el DNI tenga razón.
                        else -> BotonesPaso({ paso = 1 }, "Usar los datos del DNI") {
                            nombre = nombreLeido.orEmpty(); dni = dniLeido.orEmpty()
                            guardarQuienEres()
                        }
                    }
                    else -> BotonesPaso({ intento = false; paso = 2 }, "Terminar") {
                        intento = true
                        if (!faltaOlla) {
                            prefs.guardarPerfil(prefs.perfil.value.copy(olla = olla.trim()))
                            alTerminar()
                        }
                    }
                }
            }
        }
    }
}

/** Los títulos de esta pantalla, más grandes: es lo primero que se lee de la app. */
private val tituloGrande @Composable get() = tipos.titulo.copy(fontSize = tipos.titulo.fontSize * 1.35f, lineHeight = tipos.titulo.lineHeight * 1.35f)

/** La barra de avance (como la del B-1), «Paso 2 de 3», el título y para qué sirve el paso. */
@Composable
private fun ColumnScope.Encabezado(n: Int, titulo: String, paraQue: String) {
    val c = colores
    Row(Modifier.fillMaxWidth().padding(top = Medidas.e2, bottom = Medidas.e1).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..3) {
            // Los que vienen, solo con borde (como en el B-1): rellenos de verde pálido casi no se veían.
            val barra = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(999.dp))
            Box(when { i == n -> barra.background(c.acento); i < n -> barra.background(c.acentoTexto); else -> barra.border(1.5.dp, c.contorno, RoundedCornerShape(999.dp)) })
        }
    }
    Text("Paso $n de 3", style = tipos.chicoFuerte, color = c.acentoTexto)
    Text(titulo, style = tituloGrande, color = c.titulo, modifier = Modifier.semantics { heading() })
    Text(paraQue, style = tipos.texto, color = c.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e2))
}

/** Un paso de los que vienen, en la bienvenida: su número, su dibujo y qué se pide. */
@Composable
private fun PasoQueViene(n: Int, icono: String, titulo: String, detalle: String) {
    val c = colores
    Row(Modifier.fillMaxWidth().padding(Medidas.e4, Medidas.e3).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2), contentAlignment = Alignment.Center) {
            Icono(icono, 26.dp, c.acentoTexto)
        }
        Column(Modifier.weight(1f).padding(start = Medidas.e4)) {
            Text("$n. $titulo", style = tipos.textoFuerte, color = c.titulo)
            Text(detalle, style = tipos.chico, color = c.tintaSuave)
        }
    }
}

/** «Atrás» a la izquierda y el paso siguiente ocupando el resto, como en el B-1. */
@Composable
private fun BotonesPaso(alAtras: () -> Unit, siguiente: String, icono: String? = null, alSiguiente: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
        Boton("Atrás", alAtras)
        Boton(siguiente, alSiguiente, Modifier.weight(1f), tipo = TipoBoton.PRINCIPAL, icono = icono)
    }
}
