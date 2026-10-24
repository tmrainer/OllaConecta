// Grupo «Exportar» de web/js/casos-prueba.js (Excel del padrón, la asistencia
// y el B-1), más la ida y vuelta armarXlsx -> leerXlsx.
//
// El primer caso de la web termina pasando lo leído por prepararImportacion()
// (importar.js), que todavía no está portado: aquí se comprueba la fila tal
// como la recibiría la importación.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.documentos.CABECERA_PADRON
import io.github.tmrainer.asistencia.nucleo.documentos.Celda
import io.github.tmrainer.asistencia.nucleo.documentos.ErrorXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.HojaXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.armarXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.excelDeAsistencia
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelPadron
import io.github.tmrainer.asistencia.nucleo.documentos.leerXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.pareceXlsx
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.PreciosMenu
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import io.github.tmrainer.asistencia.nucleo.modelo.padronVacio
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExportarTest {

    @Test
    fun `El padrón exportado a Excel vuelve igual al importar, con los ceros del DNI`() {
        val p = padronVacio().copy(afiliados = listOf(nuevoAfiliado().copy(
            id = "x1", apellidoPaterno = "Álvarez", apellidoMaterno = "Rojas", nombres = "Rosa", numeroDocumento = "01234567",
            grupoEtarioManual = "adulto_mayor", tipoAfiliado = "caso_social", altaEn = "2026-01-01"
        )))
        val bytes = excelDelPadron(p, fechaRef = "2026-10-17", inicio = "2026-10-01", fin = "2026-10-17")
        ok(pareceXlsx(bytes), "debe ser un .xlsx")
        val filas = leerXlsx(bytes).split("\n")
        ok(filas[3].startsWith(CABECERA_PADRON.joinToString("\t")), "la cuarta fila son los títulos")
        igual(filas.size, 5, "solo la persona: el título de arriba y el resumen no entran")
        igual(filas[4], "1\tÁlvarez\tRojas\tRosa\tDNI\t01234567\tAdulto/a mayor\t\tAyuda social\t0", "el DNI con su 0")
    }

    @Test
    fun `En el Excel, un texto que empieza como fórmula queda como texto`() {
        val xml = armarXlsx(listOf(HojaXlsx("A", listOf(listOf("=HIPERVINCULO(\"x\")", "+51 999", 12.5))))).decodeToString()
        ok(!xml.contains("<f>"), "no debe haber ninguna fórmula")
        ok(xml.contains("t=\"inlineStr\"") && xml.contains("=HIPERVINCULO(&quot;x&quot;)"), "va como texto, escapado")
        ok(xml.contains("<v>12.5</v>"), "los números van como números")
    }

    @Test
    fun `Asistencia y B-1 en Excel - cifras como números y totales iguales a los del PDF`() {
        val p = padronVacio().copy(
            afiliados = listOf(nuevoAfiliado().copy(id = "a", apellidoPaterno = "A", nombres = "Uno", numeroDocumento = "11111111",
                tipoAfiliado = "habitual", altaEn = "2026-01-01")),
            atenciones = listOf(nuevaAtencion(PreciosMenu(150, 0)).copy(fecha = "2026-10-05",
                asistencias = listOf(Asistencia(afiliadoId = "a", tipoMenu = "normal"))))
        )
        val filas = leerXlsx(excelDeAsistencia(p, inicio = "2026-10-01", fin = "2026-10-31")).split("\n")
        igual(filas[4], listOf("05-10-26", "Lunes", "1", "0", "0", "1", "1.5").joinToString("\t"),
            "debajo de título, fechas, renglón vacío y títulos")
        ok(filas[5].startsWith("Total\t1 día\t"), filas[5])

        val r = nuevaRendicion().copy(montoSubsidioCent = 20000,
            egresos = listOf(nuevoEgreso().copy(fechaCompra = "2026-10-02", descripcion = "Arroz", montoTotalCent = 20985)))
        val b1 = leerXlsx(excelDelB1(r))
        ok(Regex("Gastos con el subsidio económico(\t)+200(\n|$)").containsMatchIn(b1), "gastos con el subsidio: 200")
        ok(Regex("Aporte del centro de atención(\t)+9.85(\n|$)").containsMatchIn(b1), "aporte: 9.85")
        ok(Regex("Total de rendición de gastos(\t)+209.85(\n|$)").containsMatchIn(b1), "total: 209.85")
    }

    @Test
    fun `armarXlsx y leerXlsx - ida y vuelta, con comillas, saltos, vacíos y caracteres de control`() {
        val bytes = armarXlsx(listOf(
            HojaXlsx("Ventas: [enero]/2026 y un nombre demasiado largo", listOf(
                listOf("Nombre", "DNI", "Monto"),
                null,
                listOf("Ñahui \"La Rosa\"", "00012345", Celda(12.5, soles = true)),
                listOf(null, "a\tb\nc", Celda("x", negrita = true), 3),
                emptyList(),
                listOf("con\u0001control", "<&>", Celda(""), -0.5)
            ), anchos = listOf(20, 10)),
            HojaXlsx(null, listOf(listOf("la segunda hoja no se lee")))
        ))
        igual(leerXlsx(bytes), "Nombre\tDNI\tMonto\n\n\"Ñahui \"\"La Rosa\"\"\"\t00012345\t12.5\n\ta b c\tx\t3\n\nconcontrol\t<&>\t\t-0.5")
        val texto = bytes.decodeToString()
        ok(texto.contains("<sheet name=\"Ventas   enero  2026 y un nombr\""), "nombre de hoja sin []:*?/\\ y de 31 letras")
        ok(texto.contains("<sheet name=\"Hoja 2\""), "sin nombre: Hoja 2")
    }

    @Test
    fun `leerXlsx rechaza lo que no es un xlsx`() {
        ok(!pareceXlsx("hola".encodeToByteArray()))
        val e = assertFailsWith<ErrorXlsx> { leerXlsx("Nombre,DNI\nAna,1".encodeToByteArray()) }
        igual(e.message, "no es un archivo .xlsx")
    }
}
