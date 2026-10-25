// Grupo «Importar» de web/js/casos-prueba.js, más las diferencias con la web
// que son a propósito (ver la cabecera de Importar.kt).
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.importar.desarmarCelda
import io.github.tmrainer.asistencia.nucleo.importar.detectarColumnas
import io.github.tmrainer.asistencia.nucleo.importar.detectarSeparador
import io.github.tmrainer.asistencia.nucleo.importar.filasImportables
import io.github.tmrainer.asistencia.nucleo.importar.parsearFechaFlexible
import io.github.tmrainer.asistencia.nucleo.importar.parsearGrupoEtario
import io.github.tmrainer.asistencia.nucleo.importar.parsearTabla
import io.github.tmrainer.asistencia.nucleo.importar.parsearTipoAfiliado
import io.github.tmrainer.asistencia.nucleo.importar.prepararImportacion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.modelo.padronVacio
import kotlin.test.Test

class ImportarTest {

    // Las fechas de dos cifras se resuelven respecto al año de `hoy`; las
    // pruebas de la web corrían en 2026.
    private val HOY = "2026-10-20"

    @Test
    fun `Reconoce el separador de Excel, CSV y punto y coma`() {
        igual(detectarSeparador("a\tb\tc"), '\t')
        igual(detectarSeparador("a,b,c"), ',')
        igual(detectarSeparador("a;b;c"), ';')
        igual(detectarSeparador("\"a,b\";c"), ';', "no cuenta los separadores entre comillas")
    }

    @Test
    fun `Lee comillas, comas dentro del campo y filas vacías`() {
        val t = "Apellido,Nombres\n\"Pérez, Jr.\",Ana\n\nQuispe,Rosa\n"
        val filas = parsearTabla(t)
        igual(filas.size, 3, "la fila vacía se descarta")
        igual(filas[1][0], "Pérez, Jr.")
        igual(filas[2][1], "Rosa")
    }

    @Test
    fun `Reconoce los títulos de columna aunque cambien de forma`() {
        val mapa = detectarColumnas(
            listOf("Ap. Paterno", "APELLIDO MATERNO", "Nombres", "DNI", "Grupo de edad", "Tipo", "Correo")).mapa
        igual(mapa["apellidoPaterno"], 0)
        igual(mapa["apellidoMaterno"], 1)
        igual(mapa["nombres"], 2)
        igual(mapa["numeroDocumento"], 3)
        igual(mapa["grupoEtario"], 4)
        igual(mapa["tipoAfiliado"], 5)
        val sinReconocer = detectarColumnas(listOf("Nombres", "Correo")).sinReconocer
        ok("Correo" in sinReconocer, "lo que no entiende lo declara")
    }

    @Test
    fun `Entiende las fechas como se escriben aquí`() {
        igual(parsearFechaFlexible("14-09-2026", HOY), "2026-09-14")
        igual(parsearFechaFlexible("14/9/1960", HOY), "1960-09-14")
        igual(parsearFechaFlexible("2026-09-14", HOY), "2026-09-14")
        igual(parsearFechaFlexible("14-09-60", HOY), "1960-09-14", "dos dígitos: 60 es 1960")
        igual(parsearFechaFlexible("14-09-26", HOY), "2026-09-14", "y 26 es 2026")
        igual(parsearFechaFlexible("14-09-99", HOY), "1999-09-14", "un año de dos cifras nunca cae en el futuro")
        igual(parsearFechaFlexible("cualquier cosa", HOY), "", "no inventa una fecha")
        igual(parsearFechaFlexible("", HOY), "")
    }

    @Test
    fun `Entiende el grupo de edad y el tipo escritos de varias formas`() {
        igual(parsearGrupoEtario("Niño"), "nino")
        igual(parsearGrupoEtario("NIÑA"), "nino")
        igual(parsearGrupoEtario("adolescente"), "adolescente")
        igual(parsearGrupoEtario("Adulto Mayor"), "adulto_mayor")
        igual(parsearGrupoEtario("Adulto/a mayor"), "adulto_mayor", "como lo escribe la app al exportar")
        igual(parsearGrupoEtario("tercera edad"), "adulto_mayor")
        igual(parsearGrupoEtario("Adulto"), "adulto")
        igual(parsearGrupoEtario("vejete"), null, "lo que no entiende queda sin grupo")

        igual(parsearTipoAfiliado("Ayuda social"), "caso_social")
        igual(parsearTipoAfiliado("caso social"), "caso_social")
        igual(parsearTipoAfiliado("SI"), "caso_social")
        igual(parsearTipoAfiliado("Habitual"), "habitual")
        igual(parsearTipoAfiliado("no"), "habitual")
        igual(parsearTipoAfiliado("???"), "", "no adivina")
    }

    @Test
    fun `Clasifica cada fila y no importa duplicados ni vacíos`() {
        val p = padronVacio().copy(afiliados = listOf(nuevoAfiliado().copy(
            id = "ya", apellidoPaterno = "Existente", nombres = "Ana",
            numeroDocumento = "00000001", activo = true
        )))
        val texto = listOf(
            "Apellido paterno\tApellido materno\tNombres\tDNI\tGrupo de edad\tTipo",
            "Quispe\tRojas\tRosa\t00000002\tAdulto\tHabitual",
            "Existente\t\tAna\t00000001\tAdulto\tHabitual",
            "Mamani\t\tLuz\t\t\t",
            "\t\t\t\t\t"
        ).joinToString("\n")
        val r = prepararImportacion(texto, p, HOY)
        igual(r.error, null)
        igual(r.filas.size, 3, "la fila totalmente vacía ni se cuenta")
        igual(r.resumen.lista, 1)
        igual(r.resumen.duplicada, 1)
        igual(r.resumen.incompleta, 1)
        igual(filasImportables(r.filas).size, 2, "la duplicada queda fuera")

        val lista = r.filas.first { it.estado == "lista" }
        igual(lista.afiliado.apellidoPaterno, "Quispe")
        igual(lista.afiliado.grupoEtarioManual, "adulto")
        igual(lista.afiliado.tipoAfiliado, "habitual")
        igual(lista.afiliado.origenDato, "importado", "queda marcado de dónde vino")

        val incompleta = r.filas.first { it.estado == "incompleta" }
        ok(incompleta.avisos.joinToString(",").contains("falta documento, grupo de edad, tipo"),
            "dice exactamente qué falta: " + incompleta.avisos.joinToString(","))
    }

    @Test
    fun `Avisa que el grupo sacado de la edad queda fijo, sin cambiar la fila`() {
        val texto = listOf(
            "Apellido paterno\tNombres\tDNI\tEdad\tFecha de nacimiento\tTipo",
            "Quispe\tRosa\t00000002\t34\t\tHabitual",
            "Rojas\tLuz\t00000003\t34\t1990-05-01\tHabitual"
        ).joinToString("\n")
        val r = prepararImportacion(texto, padronVacio(), HOY)
        val (soloEdad, conFecha) = r.filas
        igual(soloEdad.estado, "lista", "el aviso no la vuelve incompleta")
        igual(soloEdad.afiliado.grupoEtarioManual, "adulto")
        ok(soloEdad.avisos.any { "no cambiará solo" in it }, "avisa: " + soloEdad.avisos.joinToString(","))
        ok(conFecha.avisos.none { "no cambiará solo" in it },
            "con fecha de nacimiento el grupo se recalcula: no hace falta avisar")
    }

    @Test
    fun `Un DNI al que Excel le quitó el 0 vuelve a tener 8 cifras, y se dice`() {
        val p = padronVacio().copy(afiliados = listOf(nuevoAfiliado().copy(
            id = "ya", apellidoPaterno = "Rojas", nombres = "Ana", numeroDocumento = "01234567", activo = true)))
        val texto = "Apellido paterno\tNombres\tDNI\tGrupo de edad\tTipo\n" +
            "Quispe\tRosa\t4567891\tAdulto\tHabitual\n" +
            "Rojas\tAna\t1234567\tAdulto\tHabitual\n" +
            "Luna\tSol\t123456789\tAdulto\tHabitual\n" +
            "Paz\tLuz\t98765432\tAdulto\tHabitual"
        val r = prepararImportacion(texto, p, HOY)
        val (conCero, yaEsta, largo, normal) = r.filas
        igual(conCero.afiliado.numeroDocumento, "04567891")
        igual(conCero.estado, "lista")
        ok(conCero.avisos.any { "se agregó el 0" in it }, conCero.avisos.joinToString(","))
        igual(yaEsta.estado, "duplicada", "con el 0 puesto, se reconoce a quien ya está")
        igual(largo.estado, "incompleta", "un DNI de 9 cifras no se toca y no pasa como listo")
        igual(largo.afiliado.numeroDocumento, "123456789")
        igual(normal.avisos.size, 0, "un DNI de 8 cifras no se toca")
        igual(r.resumen.conCero, 1, "la duplicada no cuenta")
    }

    @Test
    fun `Encuentra los títulos aunque arriba haya un encabezado del padrón`() {
        val texto = listOf(
            "PADRÓN DE BENEFICIARIOS",
            "Comedor de prueba",
            "Apellido paterno\tNombres\tDNI\tGrupo de edad\tTipo",
            "Quispe\tRosa\t00000002\tAdulto\tHabitual"
        ).joinToString("\n")
        val r = prepararImportacion(texto, padronVacio(), HOY)
        igual(r.error, null)
        igual(r.filas.size, 1)
        igual(r.filas[0].afiliado.apellidoPaterno, "Quispe")
        igual(r.filas[0].linea, 4, "la línea es la del archivo")
        ok(prepararImportacion("hola\nmundo", padronVacio(), HOY).error != null, "sin títulos, lo dice")
    }

    @Test
    fun `La edad en números también sirve para el grupo`() {
        val texto = "Nombres\tApellido paterno\tEdad\tDNI\tTipo\n" +
            "Ana\tPerez\t8\t00000003\tHabitual\n" +
            "Rosa\tQuispe\t67\t00000004\tAyuda social"
        val r = prepararImportacion(texto, padronVacio(), HOY)
        igual(r.filas[0].afiliado.grupoEtarioManual, "nino")
        igual(r.filas[1].afiliado.grupoEtarioManual, "adulto_mayor")
        igual(r.filas[1].afiliado.tipoAfiliado, "caso_social")
        igual(r.resumen.lista, 2)
    }

    @Test
    fun `Sin encabezado reconocible, no importa nada y lo explica`() {
        val r = prepararImportacion("Ana\tPerez\t12345678\nRosa\tQuispe\t87654321", padronVacio(), HOY)
        igual(r.filas.size, 0)
        ok(r.error?.contains("fila de títulos") == true, "explica qué falta: " + r.error)
        igual(prepararImportacion("", padronVacio(), HOY).filas.size, 0)
    }

    @Test
    fun `Lo exportado se puede volver a importar`() {
        val cab = "N°,Apellido paterno,Apellido materno,Nombres,Tipo de documento," +
            "Número de documento,Grupo de edad,Edad,Tipo,Días asistidos"
        val fila = "1,Álvarez,Rojas,Rosa,DNI,00000009,Niño/a,,Ayuda social,3"
        val r = prepararImportacion(cab + "\n" + fila, padronVacio(), HOY)
        igual(r.error, null)
        igual(r.resumen.lista, 1, r.filas.firstOrNull()?.avisos.toString())
        val a = r.filas[0].afiliado
        igual(a.apellidoPaterno, "Álvarez")
        igual(a.numeroDocumento, "00000009")
        igual(a.grupoEtarioManual, "nino")
        igual(a.tipoAfiliado, "caso_social")
    }

    @Test
    fun `Un CSV exportado por una versión anterior se sigue leyendo`() {
        igual(desarmarCelda("'=SUMA(1)"), "=SUMA(1)")
        igual(desarmarCelda("=\"00012\""), "00012")
        igual(desarmarCelda("O'Brien"), "O'Brien")
    }

    // Diferencias con la web, a propósito.

    @Test
    fun `Una fecha que no existe queda vacía, con su aviso`() {
        igual(parsearFechaFlexible("31/02/1990", HOY), "", "la web devolvía 1990-02-31")
        igual(parsearFechaFlexible("2024-02-31", HOY), "")
        igual(parsearFechaFlexible("14/13/1990", HOY), "", "no hay mes 13")
        igual(parsearFechaFlexible("1/2/199", HOY), "", "un año de tres cifras no es una fecha")
        igual(parsearFechaFlexible("29/02/2024", HOY), "2024-02-29", "un 29 de febrero bisiesto sí existe")
        val r = prepararImportacion(
            "Apellido paterno\tNombres\tDNI\tFecha de nacimiento\tTipo\nQuispe\tRosa\t00000002\t31/02/1990\tHabitual",
            padronVacio(), HOY)
        val f = r.filas[0]
        igual(f.afiliado.fechaNacimiento, "")
        igual(f.estado, "incompleta", "sin fecha ni grupo, le falta el grupo de edad")
        ok("no se entendió la fecha de nacimiento" in f.avisos, f.avisos.toString())
    }

    @Test
    fun `Una edad sin cifras no se lee como 0 años`() {
        val r = prepararImportacion(
            "Apellido paterno\tNombres\tDNI\tEdad\tTipo\nQuispe\tRosa\t00000002\ts/d\tHabitual",
            padronVacio(), HOY)
        val f = r.filas[0]
        igual(f.afiliado.grupoEtarioManual, null, "la web le ponía «niño»")
        igual(f.estado, "incompleta")
        ok("no se entendió la edad" in f.avisos, f.avisos.toString())
    }

    @Test
    fun `Mujer es F y Hombre es M`() {
        val r = prepararImportacion(
            "Apellido paterno\tNombres\tSexo\nQuispe\tRosa\tMujer\nRojas\tLuis\tHombre\nPaz\tLuz\tF\nSol\tLeo\tMasculino",
            padronVacio(), HOY)
        igual(r.filas.map { it.afiliado.sexo }, listOf("F", "M", "F", "M"))
    }

    @Test
    fun `El alta y el pivote de los años de dos cifras salen de hoy`() {
        igual(parsearFechaFlexible("14-09-30", "2026-10-20"), "1930-09-14")
        igual(parsearFechaFlexible("14-09-30", "2031-01-01"), "2030-09-14")
        val r = prepararImportacion("Apellido paterno\tNombres\nQuispe\tRosa", padronVacio(), "2026-10-20")
        igual(r.filas[0].afiliado.altaEn, "2026-10-20")
    }
}
