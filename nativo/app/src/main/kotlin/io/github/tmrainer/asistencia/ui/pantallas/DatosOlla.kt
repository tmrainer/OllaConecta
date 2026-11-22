// Los datos de la olla, que se piden una vez (primer uso) y se corrigen en
// Ajustes: la olla, su directiva, el subsidio y los precios del menú. Viven en
// Config (van en la copia y se reutilizan en el B-1, el padrón municipal y la
// asistencia). Las mismas piezas en las dos pantallas.
package io.github.tmrainer.asistencia.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tmrainer.asistencia.nucleo.modelo.Config
import io.github.tmrainer.asistencia.nucleo.modelo.DatosOlla
import io.github.tmrainer.asistencia.nucleo.modelo.Dirigente
import io.github.tmrainer.asistencia.ui.componentes.FilaCampos
import io.github.tmrainer.asistencia.ui.componentes.Campo
import io.github.tmrainer.asistencia.ui.componentes.CampoSoles
import io.github.tmrainer.asistencia.ui.componentes.Leyenda
import io.github.tmrainer.asistencia.ui.componentes.Pista
import io.github.tmrainer.asistencia.ui.tema.Medidas

/** Nombre, código PCA, distrito y dirección de la olla. */
@Composable
fun CamposOlla(olla: DatosOlla, cambiar: ((DatosOlla) -> DatosOlla) -> Unit, intento: Boolean = false) {
    Campo("Nombre de la olla", olla.nombre, { v -> cambiar { it.copy(nombre = v) } }, obligatorio = true, capitalizar = true,
        pista = "Como sale en tu resolución o en tu B-1.", placeholder = "Ej.: Olla común Los Jardines",
        error = if (intento && olla.nombre.isBlank()) "Escribe el nombre de tu olla." else null)
    Spacer(Modifier.height(Medidas.e3))
    Campo("Código del Sistema Informático del PCA N.º", olla.codigoPca, { v -> cambiar { it.copy(codigoPca = v) } }, soloDigitos = true,
        pista = "El código que te dio la Municipalidad. Está en tu B-1 anterior.", placeholder = "Ej.: 098")
    Spacer(Modifier.height(Medidas.e3))
    Campo("Distrito", olla.distrito, { v -> cambiar { it.copy(distrito = v) } }, capitalizar = true,
        pista = "El de la Municipalidad a la que rindes.", placeholder = "Ej.: Pachacámac")
    Spacer(Modifier.height(Medidas.e3))
    Campo("Dirección de la olla", olla.direccion, { v -> cambiar { it.copy(direccion = v) } }, capitalizar = true,
        pista = "Mz., lote, sector o asociación. Va en el padrón de la Municipalidad.", placeholder = "Ej.: Mz. H Lote 7, As. Los Jardines")
}

@Composable
private fun CamposDirigente(cargo: String, d: Dirigente, conCelular: Boolean, cambiar: ((Dirigente) -> Dirigente) -> Unit, obligatorio: Boolean = false, intento: Boolean = false) {
    Leyenda(cargo, obligatorio = obligatorio)
    Spacer(Modifier.height(Medidas.e2))
    Campo("Nombres y apellidos", d.nombre, { v -> cambiar { it.copy(nombre = v) } }, capitalizar = true, pista = "Como en su DNI.",
        error = if (intento && obligatorio && d.nombre.isBlank()) "Escribe el nombre de la presidenta." else null)
    Spacer(Modifier.height(10.dp))
    FilaCampos {
        Campo("DNI", d.dni, { v -> cambiar { it.copy(dni = v) } }, soloDigitos = true, maxLargo = 8,
            error = if (intento && obligatorio && d.dni.length != 8) "8 números." else null)
        if (conCelular) Campo("Celular", d.celular, { v -> cambiar { it.copy(celular = v) } }, soloDigitos = true, maxLargo = 9, pista = "9 cifras; empieza con 9.")
    }
}

/** La directiva: presidenta (obligatoria: firma y encabeza el B-1), tesorera (firma el B-1), secretaria y fiscal (firman el padrón). */
@Composable
fun CamposDirectiva(olla: DatosOlla, cambiar: ((DatosOlla) -> DatosOlla) -> Unit, intento: Boolean = false) {
    CamposDirigente("Presidenta", olla.presidenta, conCelular = true, { f -> cambiar { it.copy(presidenta = f(it.presidenta)) } }, obligatorio = true, intento = intento)
    Spacer(Modifier.height(Medidas.e4))
    CamposDirigente("Tesorera", olla.tesorera, conCelular = false, { f -> cambiar { it.copy(tesorera = f(it.tesorera)) } })
    Pista("Firma el B-1 junto a la presidenta.", Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(Medidas.e4))
    CamposDirigente("Secretaria", olla.secretaria, conCelular = false, { f -> cambiar { it.copy(secretaria = f(it.secretaria)) } })
    Spacer(Modifier.height(Medidas.e4))
    CamposDirigente("Fiscal", olla.fiscal, conCelular = false, { f -> cambiar { it.copy(fiscal = f(it.fiscal)) } })
    Pista("Secretaria y fiscal firman el padrón de la Municipalidad.", Modifier.padding(top = 4.dp))
}

/** El subsidio que asigna la Municipalidad y los dos precios del menú. */
@Composable
fun CamposSubsidioYPrecios(config: Config, cambiar: ((Config) -> Config) -> Unit) {
    CampoSoles("Subsidio de cada periodo (S/)", config.olla.montoSubsidioCent, { v -> cambiar { it.copy(olla = it.olla.copy(montoSubsidioCent = v)) } },
        pista = "Lo que te asigna la Municipalidad. Se propone en el B-1; si un mes cambia, lo corriges ahí.")
    Spacer(Modifier.height(Medidas.e4))
    Leyenda("Precio del menú")
    Pista("Vale para hoy y los días nuevos. Cada día anterior guarda su precio.", Modifier.padding(bottom = Medidas.e2))
    Column(Modifier.fillMaxWidth()) {
        CampoSoles("Menú normal (S/)", config.precioMenuNormalCent, { v -> cambiar { it.copy(precioMenuNormalCent = v) } })
        Spacer(Modifier.height(10.dp))
        CampoSoles("Menú de ayuda social (S/)", config.precioMenuAyudaSocialCent, { v -> cambiar { it.copy(precioMenuAyudaSocialCent = v) } }, pista = "Si es gratuito, escribe 0.00.")
    }
}
