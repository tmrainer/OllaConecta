// Lo que es de este teléfono: quién lo usa, el modo claro u oscuro, el tamaño de la
// letra, la voz y a qué correo va el envío a UNACEM (no va en las copias); y los
// datos de la olla (Config: sí van en la copia), que se piden al empezar.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import io.github.tmrainer.asistencia.Letra
import io.github.tmrainer.asistencia.OllaConectaApp
import io.github.tmrainer.asistencia.nucleo.envio.pareceCorreo
import io.github.tmrainer.asistencia.ROLES
import io.github.tmrainer.asistencia.Tema
import io.github.tmrainer.asistencia.ui.GUIA_AJUSTES
import io.github.tmrainer.asistencia.ui.objetivoGuia
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.SelectorDesplegable
import io.github.tmrainer.asistencia.ui.componentes.Boton
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.ParaLeer
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.Opciones
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.componentes.TituloSeccion
import io.github.tmrainer.asistencia.ui.tema.Medidas
import io.github.tmrainer.asistencia.ui.tema.colores
import io.github.tmrainer.asistencia.ui.tema.tipos

val VERSION_APP = io.github.tmrainer.asistencia.BuildConfig.VERSION_NAME

@Composable
fun PantallaAjustes(ctx: Contexto) {
    val prefs = ctx.preferencias
    val perfil by prefs.perfil.collectAsStateWithLifecycle()
    val tema by prefs.tema.collectAsStateWithLifecycle()
    val letra by prefs.letra.collectAsStateWithLifecycle()
    val vozBoton by prefs.vozBoton.collectAsStateWithLifecycle()
    val vozLenta by prefs.vozLenta.collectAsStateWithLifecycle()
    val envios = (LocalContext.current.applicationContext as OllaConectaApp).envios
    val correoUnacem by envios.correo.collectAsStateWithLifecycle()
    val recordar by envios.recordar.collectAsStateWithLifecycle()
    val c = colores

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Ajustes", "De este teléfono", guia = GUIA_AJUSTES)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            Column(Modifier.objetivoGuia("ajustes.quien")) {
                TituloSeccion("Quién usa este teléfono")
                Pista("Se usa para llenar el B-1. Queda solo en este teléfono.", Modifier.padding(bottom = Medidas.e3))
                Campo("Tu nombre completo", perfil.nombre, { prefs.guardarPerfil(perfil.copy(nombre = it)) })
                Spacer(Modifier.height(10.dp))
                Leyenda("Tu rol en la olla")
                Opciones(ROLES, { it == perfil.rol }, { prefs.guardarPerfil(perfil.copy(rol = it)) })
                Spacer(Modifier.height(10.dp))
                Campo("Tu DNI", perfil.dni, { prefs.guardarPerfil(perfil.copy(dni = it)) }, soloDigitos = true, maxLargo = 8)
                Spacer(Modifier.height(10.dp))
                Campo("Tu olla", perfil.olla, { prefs.guardarPerfil(perfil.copy(olla = it)) }, capitalizar = true)
                Spacer(Modifier.height(10.dp))
                Campo("Tu celular (opcional)", perfil.celular, { prefs.guardarPerfil(perfil.copy(celular = it)) }, soloDigitos = true, maxLargo = 9)
            }

            Spacer(Modifier.height(Medidas.e5))
            Column(Modifier.objetivoGuia("ajustes.olla")) {
                val config by ctx.estado.config.collectAsStateWithLifecycle()
                fun cambiarOlla(f: (io.github.tmrainer.asistencia.nucleo.modelo.DatosOlla) -> io.github.tmrainer.asistencia.nucleo.modelo.DatosOlla) =
                    ctx.estado.cambiarConfig { it.copy(olla = f(it.olla)) }
                TituloSeccion("Datos de la olla")
                Pista("Se escriben una vez y la app los propone en el B-1, en el padrón de la Municipalidad y en la asistencia. Van en la copia de tus datos.", Modifier.padding(bottom = Medidas.e3))
                CamposOlla(config.olla, ::cambiarOlla)
                Spacer(Modifier.height(Medidas.e4))
                TituloSeccion("La directiva", apoyo = true)
                Spacer(Modifier.height(Medidas.e2))
                CamposDirectiva(config.olla, ::cambiarOlla)
                Spacer(Modifier.height(Medidas.e4))
                TituloSeccion("Subsidio y precios", apoyo = true)
                Spacer(Modifier.height(Medidas.e2))
                CamposSubsidioYPrecios(config) { f -> ctx.estado.cambiarConfig(f) }
            }

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Aspecto")
            SelectorDesplegable("Modo", Tema.values().map { it to it.etiqueta }, tema, { prefs.cambiarTema(it) })
            Spacer(Modifier.height(Medidas.e3))
            Column(Modifier.objetivoGuia("ajustes.letra")) {
                SelectorDesplegable("Tamaño de la letra", Letra.values().map { it to it.etiqueta }, letra, { prefs.cambiarLetra(it) })
                ParaLeer("Así se verá: Rosa Quispe · 4 de 12 marcadas · S/ 12.00")
                Text(
                    buildAnnotatedString { append("Así se verá: "); withStyle(tipos.textoNegrita.toSpanStyle()) { append("Rosa Quispe") }; append(" · 4 de 12 marcadas · S/ 12.00") },
                    style = tipos.texto, color = c.tinta,
                    modifier = Modifier.fillMaxWidth().padding(top = Medidas.e3).clip(RoundedCornerShape(Medidas.rS)).background(c.superficie2).padding(Medidas.e4, Medidas.e3)
                )
            }

            Spacer(Modifier.height(Medidas.e5))
            Column(Modifier.objetivoGuia("ajustes.voz")) {
                TituloSeccion("Leer en voz alta")
                Pista("La app lee la pantalla en voz alta, sin internet, con el botón «Escuchar» de arriba.", Modifier.padding(bottom = Medidas.e3))
                Leyenda("Botón «Escuchar» en cada pantalla")
                Opciones(listOf(false to "No", true to "Sí, arriba a la derecha"), { it == vozBoton }, { prefs.cambiarVozBoton(it) })
                Spacer(Modifier.height(Medidas.e3))
                Leyenda("Velocidad de la voz")
                Opciones(listOf(false to "Normal", true to "Lenta"), { it == vozLenta }, { prefs.cambiarVozLenta(it) })
                Spacer(Modifier.height(Medidas.e3))
                Boton("Probar la voz", { ctx.decir("Hola. Así suena la voz de la app.") }, icono = "voz")
            }

            Spacer(Modifier.height(Medidas.e5))
            Column(Modifier.objetivoGuia("ajustes.unacem")) {
                TituloSeccion("Envío a UNACEM")
                Pista("La asistencia y el padrón van en Excel desde tu correo. Escribe a dónde, una sola vez.", Modifier.padding(bottom = Medidas.e3))
                Campo(
                    "Correo de UNACEM", correoUnacem, { envios.cambiarCorreo(it) }, correo = true,
                    placeholder = "nombre@unacem.com.pe",
                    error = if (correoUnacem.isNotBlank() && !pareceCorreo(correoUnacem)) "Revisa el correo: le falta la @ o el punto." else null
                )
                Spacer(Modifier.height(Medidas.e3))
                Leyenda("Recordarme a las ${envios.horaRecordatorio}:00 si falta enviar")
                Opciones(listOf(true to "Sí", false to "No"), { it == recordar }, { envios.cambiarRecordar(it) })
            }

            Spacer(Modifier.height(Medidas.e5))
            EnlaceMas("escudo", "¿Buscas la copia de tus datos?", "Está en Tus datos, con borrar todo") { ctx.ir(Ruta.DATOS) }

            // En todas las versiones (decisión del 05-11-2026): una sola APK con la prueba del escaneo.
            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Pruebas")
            Boton("Probar el escaneo del DNI", { ctx.abrirCapa { PruebaDni(ctx) } }, icono = "camara")

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Acerca de")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ParaLeer("Hecha junto a las ollas comunes y comedores de Villa María del Triunfo y Pachacámac.")
                Text("Hecha junto a las ollas comunes y comedores de Villa María del Triunfo y Pachacámac.", style = tipos.texto, color = c.tinta, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Medidas.e3))
                Text("Versión $VERSION_APP", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
            }
        }
    }
}
