// Ajustes.kt — lo que es de este teléfono (pagina-ajustes.js): quién lo usa,
// el modo claro u oscuro y el tamaño de la letra. No va en las copias.
// La lectura en voz alta llega en una fase siguiente.
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
import io.github.tmrainer.asistencia.Letra
import io.github.tmrainer.asistencia.R
import io.github.tmrainer.asistencia.ROLES
import io.github.tmrainer.asistencia.Tema
import io.github.tmrainer.asistencia.ui.Cabecera
import io.github.tmrainer.asistencia.ui.Contexto
import io.github.tmrainer.asistencia.ui.Ruta
import io.github.tmrainer.asistencia.ui.componentes.Campo
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
    val c = colores

    Column(Modifier.fillMaxWidth()) {
        Cabecera(ctx, "Ajustes", "De este teléfono")
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).widthIn(max = Medidas.anchoColumna)
                .align(Alignment.CenterHorizontally).padding(Medidas.e4, Medidas.e4, Medidas.e4, 40.dp)
        ) {
            TituloSeccion("Quién usa este teléfono")
            Pista("Con tu nombre y tu rol, la app te propone tus datos en el B-1. Se queda en este teléfono: no va en las copias.", Modifier.padding(bottom = Medidas.e3))
            Campo("Tu nombre completo", perfil.nombre, { prefs.guardarPerfil(perfil.copy(nombre = it)) })
            Spacer(Modifier.height(10.dp))
            Leyenda("Tu rol en la olla")
            Opciones(ROLES, { it == perfil.rol }, { prefs.guardarPerfil(perfil.copy(rol = it)) })
            Spacer(Modifier.height(10.dp))
            Campo("Tu DNI (opcional)", perfil.dni, { prefs.guardarPerfil(perfil.copy(dni = it)) }, soloDigitos = true, maxLargo = 8)
            Spacer(Modifier.height(10.dp))
            Campo("Tu celular (opcional)", perfil.celular, { prefs.guardarPerfil(perfil.copy(celular = it)) }, soloDigitos = true, maxLargo = 9)
            Text("Se guarda solo en este teléfono.", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Aspecto")
            Leyenda("Modo")
            Opciones(Tema.values().map { it to it.etiqueta }, { it == tema }, { prefs.cambiarTema(it) })
            Spacer(Modifier.height(Medidas.e3))
            Leyenda("Tamaño de la letra")
            Opciones(Letra.values().map { it to it.etiqueta }, { it == letra }, { prefs.cambiarLetra(it) })
            Text(
                buildAnnotatedString { append("Así se verá: "); withStyle(tipos.textoNegrita.toSpanStyle()) { append("Rosa Quispe") }; append(" · 4 de 12 marcadas · S/ 12.00") },
                style = tipos.texto, color = c.tinta,
                modifier = Modifier.fillMaxWidth().padding(top = Medidas.e3).clip(RoundedCornerShape(Medidas.rS)).background(c.superficie2).padding(Medidas.e4, Medidas.e3)
            )

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Leer en voz alta")
            Pista("La lectura en voz alta llega en una fase siguiente de la app nativa.")

            Spacer(Modifier.height(Medidas.e5))
            EnlaceMas("escudo", "¿Buscas la copia de tus datos?", "Está en Tus datos, con borrar todo") { ctx.ir(Ruta.DATOS) }

            Spacer(Modifier.height(Medidas.e5))
            TituloSeccion("Acerca de")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(if (c.esOscuro) R.drawable.utec_blanco else R.drawable.utec_color), contentDescription = "UTEC, Universidad de Ingeniería y Tecnología", Modifier.width(130.dp).padding(vertical = Medidas.e3))
                Text("Proyecto de estudiantes de UTEC, hecho junto a las ollas comunes y comedores de Villa María del Triunfo.", style = tipos.texto, color = c.tinta, textAlign = TextAlign.Center)
                Text("Versión $VERSION_APP", style = tipos.chico, color = c.tintaSuave, modifier = Modifier.padding(top = Medidas.e2))
            }
        }
    }
}
