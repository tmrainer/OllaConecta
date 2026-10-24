// Grupo «Escaneo DNI» de web/js/casos-prueba.js (16 casos). Todos los datos
// son inventados: la MRZ de ejemplo de la propia norma ICAO 9303-5 (país
// ficticio «UTO») y nombres y números de prueba.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.dni.CAMPOS
import io.github.tmrainer.asistencia.nucleo.dni.CamposMrz
import io.github.tmrainer.asistencia.nucleo.dni.Confianza
import io.github.tmrainer.asistencia.nucleo.dni.DatosDni
import io.github.tmrainer.asistencia.nucleo.dni.EstadoCampo
import io.github.tmrainer.asistencia.nucleo.dni.FragmentoOcr
import io.github.tmrainer.asistencia.nucleo.dni.buscarMrzTd1
import io.github.tmrainer.asistencia.nucleo.dni.combinarMrzYAnverso
import io.github.tmrainer.asistencia.nucleo.dni.digitoControlIcao
import io.github.tmrainer.asistencia.nucleo.dni.leerMrzTd1
import io.github.tmrainer.asistencia.nucleo.dni.leerPdf417DniAzul
import io.github.tmrainer.asistencia.nucleo.dni.normalizarLetras
import io.github.tmrainer.asistencia.nucleo.dni.parsearCadenaDni
import io.github.tmrainer.asistencia.nucleo.dni.plantillaAnonima
import io.github.tmrainer.asistencia.nucleo.dni.unirFilasOcr
import kotlin.test.Test
import kotlin.test.assertNotNull

// Ejemplo de MRZ TD1 de la norma ICAO 9303-5 (persona y país inventados).
private val MRZ_ICAO = listOf(
    "I<UTOD231458907<<<<<<<<<<<<<<<",
    "7408122F1204159UTO<<<<<<<<<<<6",
    "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"
)

// Filas de un anverso inventado, como las devuelve el reconocimiento de texto.
private val ANVERSO_PRUEBA = listOf("REPÚBLICA DEL PERÚ", "DOCUMENTO NACIONAL DE IDENTIDAD", "Primer Apellido", "MUÑOZ",
    "Segundo Apellido", "QUISPE", "Prenombres", "JOSÉ ALBERTO", "Sexo M")

/** El campo de CAMPOS por su nombre, como `r[c]` en la web. */
private fun campo(r: DatosDni, nombre: String): String? = when (nombre) {
    "tipoDocumento" -> r.tipoDocumento
    "numeroDocumento" -> r.numeroDocumento
    "apellidoPaterno" -> r.apellidoPaterno
    "apellidoMaterno" -> r.apellidoMaterno
    "nombres" -> r.nombres
    "fechaNacimiento" -> r.fechaNacimiento
    "sexo" -> r.sexo
    else -> error("falta el campo $nombre")
}

class EscaneoDniTest {

    @Test
    fun `parsearCadenaDni nunca lanza y devuelve el objeto completo`() {
        for (entrada in listOf("", null, "§%&/()=?", "0", "x".repeat(500))) {
            val r = parsearCadenaDni(entrada)
            for (c in CAMPOS) campo(r, c)
            igual(r.camposNoLeidos.toSet(), CAMPOS.toSet(), "camposNoLeidos lista los campos")
        }
    }

    @Test
    fun `Mientras sea un stub, no afirma nada - confianza desconocida y todo vacío`() {
        val r = parsearCadenaDni("CUALQUIER COSA 12345678")
        igual(r.confianza, Confianza.DESCONOCIDA)
        igual(r.numeroDocumento, "")
        igual(r.apellidoPaterno, "")
        igual(r.nombres, "")
        igual(r.fechaNacimiento, "")
        igual(r.camposNoLeidos.size, CAMPOS.size, "todos los campos quedan sin leer")
    }

    // La MRZ de ejemplo de la norma ICAO 9303-5 (TD1): una persona inventada
    // por la propia norma, del país ficticio «UTO».
    @Test
    fun `Dígito de control ICAO - los ejemplos de la norma`() {
        igual(digitoControlIcao("L898902C3"), 6)
        igual(digitoControlIcao("740812"), 2)
        igual(digitoControlIcao("120415"), 9)
        igual(digitoControlIcao("<<<"), 0)
        igual(digitoControlIcao("ñ"), -1, "un carácter que no vale en una MRZ")
    }

    @Test
    fun `MRZ TD1 - lee el ejemplo de la norma y pasan sus cuatro controles`() {
        val r = leerMrzTd1(MRZ_ICAO, "2026-10-11")
        ok(r.estructura)
        ok(r.controles.todos, r.controles.toString())
        igual(r.campos.numeroDocumento, "D23145890")
        igual(r.campos.fechaNacimiento, "1974-08-12")
        igual(r.campos.sexo, "F")
        igual(r.campos.vencimiento, "2012-04-15")
        igual(r.campos.apellidos.joinToString(" "), "ERIKSSON")
        igual(r.campos.nombres, "ANNA MARIA")
    }

    @Test
    fun `MRZ TD1 - un dato con el control equivocado queda vacío`() {
        val r = leerMrzTd1(listOf(MRZ_ICAO[0], "7408123F1204159UTO<<<<<<<<<<<6", MRZ_ICAO[2]), "2026-10-11")
        igual(r.controles.nacimiento, false)
        igual(r.campos.fechaNacimiento, "", "sin control no hay fecha")
        igual(r.controles.documento, true, "lo demás se sigue leyendo")
        for (malo in listOf(null, emptyList(), listOf("x", "y", "z"), MRZ_ICAO.take(2))) {
            igual(leerMrzTd1(malo).estructura, false)
        }
    }

    @Test
    fun `Buscar la MRZ en lo que leyó la cámara - corrige O por 0 solo donde van cifras`() {
        val ocr = listOf("REPUBLICA DEL PERU", "I<UTOD23145890 7<<<<<<<<<<<<<<<",
            "74O8122F12O4159UTO<<<<<<<<<<<6", "ERIKSSON<<ANNA<MARIA<<<<<<<<<<")
        val r = assertNotNull(buscarMrzTd1(ocr, "2026-10-11"), "debía encontrarla")
        igual(r.campos.fechaNacimiento, "1974-08-12")
        igual(buscarMrzTd1(listOf(ocr[1], "7408122F1204158UTO<<<<<<<<<<<6", ocr[3])), null,
            "con un control que no cuadra, no hay MRZ")
        igual(buscarMrzTd1(null), null)
    }

    @Test
    fun `Donde van letras, una cifra es una letra mal leída - ERIKSS0N es ERIKSSON`() {
        val ocr = listOf("I<UT0D231458907<<<<<<<<<<<<<<<", "7408122F1204159UT0<<<<<<<<<<<6", "ER1KSS0N<<ANNA<MAR1A<<<<<<<<<<")
        val r = assertNotNull(buscarMrzTd1(ocr, "2026-10-11"), "debía encontrarla")
        igual(r.campos.apellidos.joinToString(" "), "ERIKSSON", "0 → O y 1 → I en el apellido")
        igual(r.campos.nombres, "ANNA MARIA")
        igual(r.campos.pais, "UTO", "el país también es solo letras")
        igual(r.campos.numeroDocumento, "D23145890", "el número no se toca")
    }

    @Test
    fun `MRZ partida en pedazos y sin algunos « (así la leyó ML Kit en un moto g77)`() {
        // Lo que devolvió el reconocimiento de texto del APK con la MRZ de la
        // norma impresa en una imagen: pedazos sueltos y «<» de menos.
        val fragmentos = listOf(
            FragmentoOcr(texto = "I<UTOD231458907<<<", arriba = 60, abajo = 110, izquierda = 40),
            FragmentoOcr(texto = "<<", arriba = 62, abajo = 108, izquierda = 900),
            FragmentoOcr(texto = "7408122F1204159UTO<<", arriba = 170, abajo = 220, izquierda = 40),
            FragmentoOcr(texto = "<<6", arriba = 171, abajo = 219, izquierda = 1000),
            FragmentoOcr(texto = "ERIKSSON<<ANNA <MARIA<<<<<<<<<<", arriba = 280, abajo = 330, izquierda = 40)
        )
        val filas = unirFilasOcr(fragmentos)
        igual(filas.size, 3, "tres filas")
        val r = assertNotNull(buscarMrzTd1(filas, "2026-10-12"), "debía encontrarla")
        igual(r.campos.numeroDocumento, "D23145890")
        igual(r.campos.nombres, "ANNA MARIA")
        igual(unirFilasOcr(null).size, 0)
    }

    // DNI electrónico peruano: datos inventados con la forma EXACTA de una
    // muestra real verificada (plantilla anónima): solo «<»
    // tras el DNI en la línea 1 y un solo apellido en la línea 3.
    @Test
    fun `DNI electrónico (forma verificada con una muestra real) - todo se lee, un solo apellido`() {
        val r = leerMrzTd1(listOf(
            "I<PER12345678<8<<<<<<<<<<<<<<<",
            "9001158M3001156PER<<<<<<<<<<<6",
            "VILLANUEVA<<FERNANDO<ALBERTO<<"
        ), "2026-10-12")
        ok(r.controles.todos, r.controles.toString())
        igual(r.campos.pais, "PER")
        igual(r.campos.numeroDocumento, "12345678", "el «<» de relleno no es parte del DNI")
        igual(r.campos.fechaNacimiento, "1990-01-15")
        igual(r.campos.sexo, "M")
        igual(r.campos.vencimiento, "2030-01-15")
        igual(r.campos.apellidos.joinToString(" "), "VILLANUEVA", "solo el primer apellido")
        igual(r.campos.nombres, "FERNANDO ALBERTO")
    }

    // DNI azul (forma medida en un Samsung A56, plantilla anónima
    // «AAAAAAA<AAAAAAAAAAAAA<<<<<<<<<»): el reconocimiento se comió un «<» del
    // «<<» entre apellido y nombres. Nombres inventados.
    @Test
    fun `DNI azul - sin «« en la línea 3, el primer bloque es el apellido y el resto los nombres`() {
        val r = leerMrzTd1(listOf(
            "I<PER12345678<8<<<<<<<<<<<<<<<",
            "9001158M3001156PER<<<<<<<<<<<6",
            "VILLANUEVA<FERNANDO<ALBERTO<<<"
        ), "2026-10-12")
        igual(r.campos.apellidos.joinToString(" "), "VILLANUEVA")
        igual(r.campos.nombres, "FERNANDO ALBERTO")
    }

    @Test
    fun `DNI azul - los rótulos del anverso confirman aunque la MRZ haya perdido un «`() {
        // El «<» perdido entre dos nombres junta las letras: la MRZ no sabe dónde cortar.
        val mrz = CamposMrz(apellidos = listOf("VILLANUEVA"), nombres = "FERNANDOALBERTO")
        val enFilas = assertNotNull(combinarMrzYAnverso(mrz, listOf("Primer Apellido", "VILLANUEVA", "Segundo Apellido", "QUIÑONES",
            "Pre Nombres", "FERNANDO ALBERTO")))
        igual(enFilas.apellidoPaterno.estado, EstadoCampo.CONFIRMADO)
        igual(enFilas.nombres.valor, "FERNANDO ALBERTO")
        igual(enFilas.nombres.estado, EstadoCampo.CONFIRMADO)
        igual(enFilas.apellidoMaterno.valor, "QUIÑONES")
        // Rótulo y valor en la misma fila.
        val juntos = assertNotNull(combinarMrzYAnverso(mrz, listOf("Primer Apellido VILLANUEVA", "Pre Nombres FERNANDO ALBERTO")))
        igual(juntos.nombres.valor, "FERNANDO ALBERTO")
        // La columna de al lado («Fecha de Emisión») se mete entre el rótulo y el
        // segundo apellido, o queda en su misma fila: no es el apellido.
        val conFecha = assertNotNull(combinarMrzYAnverso(mrz, listOf("Primer Apellido", "VILLANUEVA", "Segundo Apellido",
            "Fecha de Emisión", "QUIÑONES", "Pre Nombres", "FERNANDO ALBERTO")))
        igual(conFecha.apellidoMaterno.valor, "QUIÑONES")
        val pegada = assertNotNull(combinarMrzYAnverso(mrz, listOf("Primer Apellido", "VILLANUEVA", "Segundo Apellido",
            "QUIÑONES Fecha de Emisión 12/03/2015", "Pre Nombres", "FERNANDO ALBERTO")))
        igual(pegada.apellidoMaterno.valor, "QUIÑONES")
        // Un «<» leído como «K»: VILLANUEVAK<FERNANDO… también se confirma.
        val conK = assertNotNull(combinarMrzYAnverso(CamposMrz(apellidos = listOf("VILLANUEVAK"), nombres = "FERNANDOKALBERTO"),
            listOf("Primer Apellido", "VILLANUEVA", "Pre Nombres", "FERNANDO ALBERTO")))
        igual(conK.apellidoPaterno.valor, "VILLANUEVA")
        igual(conK.nombres.estado, EstadoCampo.CONFIRMADO)
        // Otro DNI frente a la cámara: no se confirma nada.
        igual(combinarMrzYAnverso(mrz, listOf("Primer Apellido", "QUISPE", "Pre Nombres", "ROSA")), null)
    }

    // Un anverso inventado, como lo devuelve el reconocimiento de texto.
    @Test
    fun `Anverso - si coincide con la MRZ, vale como está impreso (tildes y ñ)`() {
        val r = assertNotNull(combinarMrzYAnverso(CamposMrz(apellidos = listOf("MUNOZ"), nombres = "JOSE ALBERTO"), ANVERSO_PRUEBA))
        igual(r.apellidoPaterno.valor, "MUÑOZ")
        igual(r.apellidoPaterno.estado, EstadoCampo.CONFIRMADO)
        igual(r.nombres.valor, "JOSÉ ALBERTO")
        igual(r.apellidoMaterno.valor, "QUISPE", "el segundo apellido sale del anverso")
        igual(r.apellidoMaterno.estado, EstadoCampo.SIN_CONFIRMAR, "la MRZ no lo trae: hay que mirarlo")
        igual(normalizarLetras("Peña Ñahui-José"), "PENA NAHUI JOSE")
    }

    @Test
    fun `Anverso - una letra confundida (B por S) no se elige sola, se ofrecen las dos`() {
        val r = assertNotNull(combinarMrzYAnverso(CamposMrz(apellidos = listOf("MUNOZ"), nombres = "JOSE ALSERTO"), ANVERSO_PRUEBA))
        igual(r.nombres.estado, EstadoCampo.DUDOSO)
        igual(r.nombres.valor, "", "nada elegido")
        igual(r.nombres.opciones.joinToString(" | "), "JOSÉ ALBERTO | JOSE ALSERTO")
    }

    @Test
    fun `Anverso de otro DNI (o mal leído) - no se usa`() {
        igual(combinarMrzYAnverso(CamposMrz(apellidos = listOf("ROJAS"), nombres = "ANA"), ANVERSO_PRUEBA), null)
        igual(combinarMrzYAnverso(null, ANVERSO_PRUEBA), null)
        igual(combinarMrzYAnverso(CamposMrz(apellidos = listOf("MUNOZ"), nombres = "X"), null), null)
    }

    @Test
    fun `Plantilla anónima - la forma del dato, sin el dato`() {
        igual(plantillaAnonima("Huamán 12<3"), "AAAAAA 99<9")
        igual(plantillaAnonima("A\u0001ÿ"), "A·A")
        igual(plantillaAnonima("\u0007"), "·")
        igual(plantillaAnonima(null), "")
    }

    @Test
    fun `PDF417 del DNI azul (sin verificar) - nunca lanza y no inventa un DNI`() {
        for (entrada in listOf("", null, "x".repeat(600))) {
            val r = leerPdf417DniAzul(entrada)
            igual(r.campos.numeroDocumento, "", "sin 8 cifras no hay DNI")
            igual(r.campos.sexo, "")
        }
    }
}
