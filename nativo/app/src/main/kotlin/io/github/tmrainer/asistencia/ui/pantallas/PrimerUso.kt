// PrimerUso.kt — lo que la app pide antes de usarse, y vuelve a pedir al
// abrir mientras falte algo: quién eres (rol, nombre y DNI), la confirmación
// con tu DNI y de qué olla eres. Es de este teléfono (Preferencias). Los datos
// los escribe la persona; la cámara lee el DNI solo para CONFIRMARLOS y, si
// algo difiere, ofrece usar lo del DNI. Lo de aquí solo se PROPONE en el B-1,
// nunca se llena solo.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import io.github.tmrainer.asistencia.ui.componentes.Enlace
import io.github.tmrainer.asistencia.ui.componentes.FranjaEstado
import io.github.tmrainer.asistencia.ui.componentes.Icono
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.Separador
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
    // Los errores se muestran después de tocar «Siguiente» o «Listo», no mientras se escribe.
    var intento by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = paso > 0) { intento = false; paso -= 1 }
    val c = colores

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
                            Spacer(Modifier.height(Medidas.e6))
                            Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(c.superficie2).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                                Icono("casa", 64.dp, c.acentoTexto)
                            }
                            Text("Te damos la bienvenida", style = tipos.titulo, color = c.titulo, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().semantics { heading() })
                            Text("Antes de empezar, dinos quién eres y de qué olla eres. Así la app propone tus datos en el B-1.",
                                style = tipos.texto, color = c.tintaSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        1 -> {
                            Encabezado(1, "¿Quién eres?")
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
                            Encabezado(2, "Confirma con tu DNI")
                            Text("Muestra tu DNI a la cámara. La app revisa que tu nombre y tu número estén bien escritos.",
                                style = tipos.texto, color = c.tintaSuave)
                            // Lo que escribió, para comparar con el DNI en la mano.
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Medidas.rM)).background(c.superficie2).padding(Medidas.e4)) {
                                Text("Lo que escribiste", style = tipos.chicoFuerte, color = c.tintaSuave)
                                Text(nombre.trim(), style = tipos.textoFuerte, color = c.titulo, modifier = Modifier.padding(top = Medidas.e1))
                                Text("DNI $dni", style = tipos.texto, color = c.tinta)
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
                            if (confirmacion?.coincide != true) Enlace("No puedo leer mi DNI ahora: seguir sin confirmar", { paso = 3 })
                        }
                        else -> {
                            Encabezado(3, "¿De qué olla eres?")
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
                    1 -> BotonesPaso({ intento = false; paso = 0 }, "Siguiente") {
                        intento = true
                        if (!faltaRol && !faltaNombre && !faltaDni) { guardarQuienEres(); intento = false; paso = 2 }
                    }
                    2 -> when {
                        confirmacion == null -> BotonesPaso({ paso = 1 }, "Leer mi DNI", icono = "camara", alSiguiente = ::leerDni)
                        confirmacion.coincide -> BotonesPaso({ paso = 1 }, "Siguiente") { paso = 3 }
                        // Lo más probable: que el DNI tenga razón.
                        else -> BotonesPaso({ paso = 1 }, "Usar los datos del DNI") {
                            nombre = nombreLeido.orEmpty(); dni = dniLeido.orEmpty()
                            guardarQuienEres()
                        }
                    }
                    else -> BotonesPaso({ intento = false; paso = 2 }, "Listo") {
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

/** «Paso 2 de 3» y el título del paso. */
@Composable
private fun ColumnScope.Encabezado(n: Int, titulo: String) {
    Text("Paso $n de 3", style = tipos.chicoFuerte, color = colores.acentoTexto)
    Text(titulo, style = tipos.titulo, color = colores.titulo, modifier = Modifier.semantics { heading() })
}

/** «Atrás» a la izquierda y el paso siguiente ocupando el resto, como en el B-1. */
@Composable
private fun BotonesPaso(alAtras: () -> Unit, siguiente: String, icono: String? = null, alSiguiente: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e3)) {
        Boton("Atrás", alAtras)
        Boton(siguiente, alSiguiente, Modifier.weight(1f), tipo = TipoBoton.PRINCIPAL, icono = icono)
    }
}
