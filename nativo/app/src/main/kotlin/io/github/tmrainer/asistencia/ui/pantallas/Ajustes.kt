// Lo que es de este teléfono: quién lo usa, el modo claro u oscuro, el tamaño de la
// letra, la voz y a qué correo va el envío a UNACEM. No va en las copias.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import io.github.tmrainer.asistencia.Letra
import io.github.tmrainer.asistencia.OllaConectaApp
import io.github.tmrainer.asistencia.nucleo.envio.pareceCorreo
import io.github.tmrainer.asistencia.R
import io.github.tmrainer.asistencia.ROLES
import io.github.tmrainer.asistencia.Tema
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
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

const val VERSION_APP = "2.0.0"

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
        Cabecera(ctx, "Ajustes", "De este teléfono")
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
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

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Aspecto")
            Leyenda("Modo")
            Opciones(Tema.values().map { it to it.etiqueta }, { it == tema }, { prefs.cambiarTema(it) })
            Spacer(Modifier.height(Medidas.e3))
            Leyenda("Tamaño de la letra")
            Opciones(Letra.values().map { it to it.etiqueta }, { it == letra }, { prefs.cambiarLetra(it) })
            ParaLeer("Así se verá: Rosa Quispe · 4 de 12 marcadas · S/ 12.00")
            Text(
                buildAnnotatedString { append("Así se verá: "); withStyle(tipos.textoNegrita.toSpanStyle()) { append("Rosa Quispe") }; append(" · 4 de 12 marcadas · S/ 12.00") },
                style = tipos.texto, color = c.tinta,
                modifier = Modifier.fillMaxWidth().padding(top = Medidas.e3).clip(RoundedCornerShape(Medidas.rS)).background(c.superficie2).padding(Medidas.e4, Medidas.e3)
            )

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Leer en voz alta")
            Pista("La app lee la pantalla en voz alta, sin internet, con el botón «Escuchar» de arriba.", Modifier.padding(bottom = Medidas.e3))
            Leyenda("Botón «Escuchar» en cada pantalla")
            Opciones(listOf(false to "No", true to "Sí, arriba a la derecha"), { it == vozBoton }, { prefs.cambiarVozBoton(it) })
            Spacer(Modifier.height(Medidas.e3))
            Leyenda("Velocidad de la voz")
            Opciones(listOf(false to "Normal", true to "Lenta"), { it == vozLenta }, { prefs.cambiarVozLenta(it) })
            Spacer(Modifier.height(Medidas.e3))
            Boton("Probar la voz", { ctx.decir("Hola. Así suena la voz de la app.") }, icono = "voz")

            Spacer(Modifier.height(Medidas.e5))
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

            Spacer(Modifier.height(Medidas.e5))
            EnlaceMas("escudo", "¿Buscas la copia de tus datos?", "Está en Tus datos, con borrar todo") { ctx.ir(Ruta.DATOS) }

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Acerca de")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(if (c.esOscuro) R.drawable.utec_blanco else R.drawable.utec_color), contentDescription = "UTEC, Universidad de Ingeniería y Tecnología", Modifier.width(130.dp).padding(vertical = Medidas.e3))
                ParaLeer("Proyecto de estudiantes de UTEC con ollas comunes y comedores de Villa María del Triunfo y Pachacámac.")
                Text("Proyecto de estudiantes de UTEC con ollas comunes y comedores de Villa María del Triunfo y Pachacámac.", style = tipos.texto, color = c.tinta, textAlign = TextAlign.Center)
                Text("Versión $VERSION_APP", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
            }
        }
    }
}
