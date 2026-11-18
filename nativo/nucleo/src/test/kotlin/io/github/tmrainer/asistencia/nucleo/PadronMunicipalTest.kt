// Lo del 05-11-2026: el libro de socias (socia y familiares), el formato
// municipal de beneficiarios, los datos de la olla reutilizados en el B-1 y
// el pie del B-1 de Pachacámac.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.conSocia
import io.github.tmrainer.asistencia.nucleo.calculos.familiaresDe
import io.github.tmrainer.asistencia.nucleo.calculos.tipoBeneficiarioMunicipal
import io.github.tmrainer.asistencia.nucleo.calculos.totales
import io.github.tmrainer.asistencia.nucleo.documentos.excelDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.excelLibroDeSocias
import io.github.tmrainer.asistencia.nucleo.documentos.excelPadronMunicipal
import io.github.tmrainer.asistencia.nucleo.documentos.pdfLibroDeSocias
import io.github.tmrainer.asistencia.nucleo.documentos.sociasDelLibro
import io.github.tmrainer.asistencia.nucleo.documentos.filasPadronMunicipal
import io.github.tmrainer.asistencia.nucleo.documentos.hojasDelB1
import io.github.tmrainer.asistencia.nucleo.documentos.leerXlsx
import io.github.tmrainer.asistencia.nucleo.documentos.medidasJpeg
import io.github.tmrainer.asistencia.nucleo.documentos.pdfDelPadron
import io.github.tmrainer.asistencia.nucleo.documentos.pdfFichaSocia
import io.github.tmrainer.asistencia.nucleo.documentos.pdfPadronMunicipal
import io.github.tmrainer.asistencia.nucleo.importar.prepararImportacion
import io.github.tmrainer.asistencia.nucleo.modelo.Config
import io.github.tmrainer.asistencia.nucleo.modelo.DatosOlla
import io.github.tmrainer.asistencia.nucleo.modelo.Dirigente
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.sugerencias.conDatosDeLaOlla
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasOlla
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.faltaFotoDni
import io.github.tmrainer.asistencia.nucleo.validaciones.validarAfiliado
import io.github.tmrainer.asistencia.nucleo.validaciones.validarRendicion
import kotlin.test.Test

private val OLLA = DatosOlla(
    nombre = "Olla común Los Jardines", codigoPca = "098", direccion = "Mz. 17 Lote 7", distrito = "Pachacámac",
    presidenta = Dirigente("María Ficticia", "00000001", "900000000"),
    tesorera = Dirigente("Blanca Ficticia", "00000002"),
    secretaria = Dirigente("Ana Ficticia", "00000003"),
    fiscal = Dirigente("Eli Ficticia", "00000004"),
    montoSubsidioCent = 54000
)

private fun padronDeHogares(): Padron {
    val socia = nuevoAfiliado("2026-10-01").copy(id = "s1", apellidoPaterno = "Quispe", apellidoMaterno = "León", nombres = "Rosa",
        numeroDocumento = "00000011", fechaNacimiento = "1980-05-10", sexo = "F", tipoAfiliado = "habitual", condicion = "socia",
        direccion = "Mz. H Lote 4", fotoDni = "s1.jpg")
    val hija = nuevoAfiliado("2026-10-01").copy(id = "f1", apellidoPaterno = "Huanca", apellidoMaterno = "Quispe", nombres = "Alis",
        numeroDocumento = "00000012", fechaNacimiento = "2018-05-26", sexo = "F", tipoAfiliado = "habitual", condicion = "familiar",
        titularId = "s1", parentesco = "hijo/a", ocupacion = "estudiante", fotoDni = "f1.jpg")
    val esposo = nuevoAfiliado("2026-10-01").copy(id = "f2", apellidoPaterno = "Huanca", apellidoMaterno = "Tipo", nombres = "Víctor",
        numeroDocumento = "00000013", fechaNacimiento = "1976-06-21", sexo = "M", tipoAfiliado = "habitual", condicion = "familiar",
        titularId = "s1", parentesco = "esposo/a", fotoDni = "f2.jpg")
    val social = nuevoAfiliado("2026-10-01").copy(id = "c1", apellidoPaterno = "Rosales", apellidoMaterno = "Pereira", nombres = "Lucía",
        numeroDocumento = "00000014", grupoEtarioManual = "adulto_mayor", tipoAfiliado = "caso_social", condicion = "socia", direccion = "Mz. D Lote 3")
    val vieja = nuevoAfiliado("2026-10-01").copy(id = "v1", apellidoPaterno = "Aguilar", nombres = "Eva", numeroDocumento = "00000015",
        grupoEtarioManual = "adulto", tipoAfiliado = "habitual", origenDato = "importado")
    return Padron(afiliados = listOf(vieja, hija, social, esposo, socia))
}

class PadronMunicipalTest {

    @Test
    fun `Al cambiar o quitar la socia se vuelve a indicar el parentesco`() {
        val padron = padronDeHogares()
        val hija = padron.afiliados.single { it.id == "f1" }
        igual(conSocia(hija.copy(titularId = ""), "s1"), hija, "se puede anotar el parentesco antes de la primera socia")
        igual(conSocia(hija, "s1"), hija)
        igual(conSocia(hija, "c1"), hija.copy(titularId = "c1", parentesco = ""))
        igual(conSocia(hija, ""), hija.copy(titularId = "", parentesco = ""))
    }

    @Test
    fun `Socio, usuario o caso social, según la condición y el tipo`() {
        val p = padronDeHogares()
        val por = p.afiliados.associateBy { it.id }
        igual(tipoBeneficiarioMunicipal(por.getValue("s1")), "socio")
        igual(tipoBeneficiarioMunicipal(por.getValue("f1")), "usuario")
        igual(tipoBeneficiarioMunicipal(por.getValue("c1")), "caso_social", "el caso social manda sobre la condición")
        igual(tipoBeneficiarioMunicipal(por.getValue("v1")), "", "datos de antes: no se inventa")
        igual(familiaresDe(p, "s1").map { it.id }, listOf("f1", "f2"))
    }

    @Test
    fun `El formato municipal lista a cada socia con su familia y hereda la dirección`() {
        val filas = filasPadronMunicipal(padronDeHogares(), "2026-11-05")
        igual(filas.map { it.afiliadoId }, listOf("s1", "f1", "f2", "c1", "v1"), "socia, sus familiares, la otra socia y al final quien no tiene socia")
        igual(filas.map { it.n }, listOf(1, 2, 3, 4, 5))
        igual(filas[1].direccion, "Mz. H Lote 4", "la hija toma la dirección de su socia")
        igual(filas[1].edad, 8)
        igual(filas[0].nacimiento, "10-05-80")
        igual(filas.map { it.tipo }, listOf("socio", "usuario", "usuario", "caso_social", ""))
    }

    @Test
    fun `El Excel municipal trae la olla, la presidenta, una fila por persona con su X, y las firmas`() {
        val texto = leerXlsx(excelPadronMunicipal(padronDeHogares(), OLLA, "2026-11-05"))
        ok("FORMATO DE ACTUALIZACIÓN DE BENEFICIARIOS" in texto && "2026" in texto, "el título con el año")
        ok("Olla común Los Jardines" in texto && "098" in texto && "Mz. 17 Lote 7" in texto, "los datos del centro")
        ok("María Ficticia" in texto && "900000000" in texto, "la presidenta")
        ok(Regex("Total de beneficiarios\\t5").containsMatchIn(texto), "el total")
        ok(Regex("^1\\t00000011\\tQuispe\\tLeón\\tRosa\\t10-05-80\\t46\\tF\\tMz\\. H Lote 4\\tX$", RegexOption.MULTILINE).containsMatchIn(texto), "la socia, con X en Socio")
        ok(Regex("^2\\t00000012\\tHuanca\\tQuispe\\tAlis\\t26-05-18\\t8\\tF\\tMz\\. H Lote 4\\t\\tX$", RegexOption.MULTILINE).containsMatchIn(texto), "la hija, con X en Usuario")
        ok(Regex("^4\\t00000014\\tRosales\\tPereira\\tLucía\\t\\t\\t\\tMz\\. D Lote 3\\t\\t\\tX", RegexOption.MULTILINE).containsMatchIn(texto), "caso social")
        ok("Secretaria\tAna Ficticia" in texto && "Fiscal\tEli Ficticia" in texto, "las firmas de la directiva")
        // Se puede volver a cargar: la fila de títulos se reconoce y la X dice quién es socia.
        val imp = prepararImportacion(texto, Padron(), "2026-11-05")
        igual(imp.error ?: "", "")
        val rosa = imp.filas.first { it.afiliado.numeroDocumento == "00000011" }
        igual(rosa.afiliado.condicion, "socia"); igual(rosa.afiliado.tipoAfiliado, "habitual"); igual(rosa.afiliado.direccion, "Mz. H Lote 4")
        val alis = imp.filas.first { it.afiliado.numeroDocumento == "00000012" }
        igual(alis.afiliado.condicion, "familiar")
        val lucia = imp.filas.first { it.afiliado.numeroDocumento == "00000014" }
        igual(lucia.afiliado.tipoAfiliado, "caso_social")
    }

    @Test
    fun `Los PDF municipales y la ficha de socia son PDF válidos`() {
        val p = padronDeHogares()
        val municipal = pdfPadronMunicipal(p, OLLA, "2026-11-05", generado = "05-11-26 10:00")
        ok(String(municipal, Charsets.ISO_8859_1).startsWith("%PDF-"), "el formato municipal es un PDF")
        ok(String(municipal, Charsets.ISO_8859_1).contains("/Count 1"), "cinco personas entran en una hoja apaisada")
        val ficha = pdfFichaSocia(p, p.afiliados.first { it.id == "s1" }, OLLA, generado = "05-11-26 10:00")
        val t = String(ficha, Charsets.ISO_8859_1)
        ok(t.startsWith("%PDF-"), "la ficha es un PDF")
        ok("Huanca Quispe" in t || "Alis" in t, "la hija aparece en la ficha")
        val vacio = pdfPadronMunicipal(Padron(), DatosOlla(), "2026-11-05", generado = "05-11-26 10:00")
        ok(String(vacio, Charsets.ISO_8859_1).startsWith("%PDF-"), "sin nadie también sale un PDF")
    }

    /** Un JPEG de verdad (ImageIO), 30 × 40, para probar las fotos en el PDF. */
    private fun jpegDePrueba(): ByteArray {
        val img = java.awt.image.BufferedImage(30, 40, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = java.awt.Color(120, 160, 90); g.fillRect(0, 0, 30, 40); g.dispose()
        val salida = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpeg", salida)
        return salida.toByteArray()
    }

    @Test
    fun `Las fotos de las personas entran en el PDF del padrón y en la ficha, y sin fotos el PDF es el de siempre`() {
        val jpeg = jpegDePrueba()
        igual(medidasJpeg(jpeg), Triple(30, 40, 3))
        igual(medidasJpeg("no es jpeg".toByteArray()), null)
        val p = padronDeHogares()
        val sinFotos = pdfDelPadron(p, "2026-11-05", "2026-10-01", "2026-11-05", generado = "05-11-26 10:00")
        val conFotos = pdfDelPadron(p, "2026-11-05", "2026-10-01", "2026-11-05", generado = "05-11-26 10:00", fotos = { id -> if (id == "s1") jpeg else null })
        val t = String(conFotos, Charsets.ISO_8859_1)
        ok("/XObject" in t && "/DCTDecode" in t && "/Width 30 /Height 40" in t, "la foto va como imagen JPEG del PDF")
        ok("/Im1 Do" in t, "y se dibuja")
        ok("/XObject" !in String(sinFotos, Charsets.ISO_8859_1), "sin fotos, el PDF no cambia de forma")
        val ficha = String(pdfFichaSocia(p, p.afiliados.first { it.id == "s1" }, OLLA, generado = "05-11-26 10:00", foto = jpeg), Charsets.ISO_8859_1)
        ok("/DCTDecode" in ficha && "/Im1 Do" in ficha, "la ficha lleva la foto")
    }

    @Test
    fun `El libro de socias sale entero, vertical - una ficha por socia en PDF y un bloque por socia en Excel`() {
        val p = padronDeHogares()
        igual(sociasDelLibro(p, "2026-11-05").map { it.id }, listOf("s1", "c1"), "las socias activas, por apellido: Quispe antes que Rosales")
        val jpeg = jpegDePrueba()
        val pdf = String(pdfLibroDeSocias(p, OLLA, "2026-11-05", generado = "05-11-26 10:00", fotos = { if (it == "s1") jpeg else null }), Charsets.ISO_8859_1)
        ok(pdf.startsWith("%PDF-") && "/Count 2" in pdf, "una hoja por socia")
        ok("/DCTDecode" in pdf, "con la foto de la que la tiene")
        val vacio = String(pdfLibroDeSocias(Padron(), DatosOlla(), "2026-11-05", generado = "05-11-26 10:00"), Charsets.ISO_8859_1)
        ok(vacio.startsWith("%PDF-") && "/Count 1" in vacio, "sin socias, una hoja que lo dice")
        val excel = leerXlsx(excelLibroDeSocias(p, OLLA, "2026-11-05"))
        ok("Socia N° 1\tQuispe León Rosa" in excel || Regex("Socia N° 1\tQuispe León Rosa").containsMatchIn(excel), "el bloque de la primera socia: " + excel.lines().take(6).joinToString(" | "))
        ok(Regex("\tHuanca Quispe Alis\thijo/a\tF\t26-05-18\testudiante\t00000012").containsMatchIn(excel), "su hija debajo")
        ok("Socia N° 2\tRosales Pereira Lucía" in excel, "la segunda socia")
    }

    @Test
    fun `Un familiar sin socia o sin parentesco se señala, y la foto del DNI falta salvo en importados`() {
        val p = padronDeHogares()
        val suelto = nuevoAfiliado("2026-10-01").copy(id = "x1", apellidoPaterno = "Sola", nombres = "Ana", numeroDocumento = "00000021",
            grupoEtarioManual = "adulto", tipoAfiliado = "habitual", condicion = "familiar")
        val hs = validarAfiliado(suelto, p.copy(afiliados = p.afiliados + suelto), hoy = "2026-11-05")
        ok(hs.any { "de qué socia" in it.mensaje && it.severidad == Severidad.ADVERTENCIA }, "falta la socia")
        ok(hs.any { "parentesco" in it.mensaje }, "falta el parentesco")
        ok(validarAfiliado(p.afiliados.first { it.id == "f1" }, p, hoy = "2026-11-05").none { "socia" in it.mensaje || "parentesco" in it.mensaje }, "la hija está completa")
        val sinSocia = p.copy(afiliados = p.afiliados.map { if (it.id == "s1") it.copy(activo = false, bajaEn = "2026-11-01") else it })
        ok(validarAfiliado(sinSocia.afiliados.first { it.id == "f1" }, sinSocia, hoy = "2026-11-05").any { "está de baja" in it.mensaje && it.severidad == Severidad.ADVERTENCIA }, "su socia está de baja")
        ok(faltaFotoDni(suelto), "inscrita a mano sin foto")
        ok(!faltaFotoDni(p.afiliados.first { it.id == "v1" }), "importada: no se le reclama la foto")
        ok(!faltaFotoDni(p.afiliados.first { it.id == "s1" }), "con foto")
    }

    @Test
    fun `Los datos de la olla se proponen y llenan el B-1 nuevo, siempre olla común`() {
        val config = Config(olla = OLLA)
        val props = sugerenciasOlla(config)
        igual(props.getValue("nombreCentro").valor, "Olla común Los Jardines")
        igual(props.getValue("distrito").valor, "Pachacámac")
        igual(props.getValue("montoSubsidio").valor, "540.00")
        igual(props.getValue("nombreTesorera").procedencia, "de los datos de tu olla")
        val (r, puestos) = conDatosDeLaOlla(nuevaRendicion(), config)
        igual(r.tipoCentro, "olla_comun")
        igual(r.codigoPca, "098"); igual(r.nombrePresidenta, "María Ficticia"); igual(r.celular, "900000000")
        igual(r.nombrePresidentaFirma, "María Ficticia"); igual(r.dniTesorera, "00000002"); igual(r.montoSubsidioCent, 54000L)
        ok("montoSubsidio" in puestos && "nombreCentro" in puestos && "distrito" in puestos)
        igual(conDatosDeLaOlla(nuevaRendicion(), Config()).second, emptySet(), "sin datos de la olla no se pone nada")
        ok(validarRendicion(nuevaRendicion().copy(tipoCentro = "comedor")).any { it.campo == "tipoCentro" && it.severidad == Severidad.ERROR }, "comedor ya no vale")
        ok(validarRendicion(nuevaRendicion()).none { it.campo == "tipoCentro" }, "la rendición nueva ya es olla común")
    }

    @Test
    fun `El pie del B-1 es el de Pachacámac 2026 - 581,70 comprado, 41,70 de aporte, 540 rendido`() {
        val r = nuevaRendicion().copy(montoSubsidioCent = 54000, distrito = "Pachacámac", egresos = listOf(
            nuevoEgreso().copy(fechaCompra = "2026-09-05", descripcion = "Pavita", montoTotalCent = 8070),
            nuevoEgreso().copy(fechaCompra = "2026-09-11", descripcion = "Pollo", montoTotalCent = 30100),
            nuevoEgreso().copy(fechaCompra = "2026-09-28", descripcion = "Carne guiso", montoTotalCent = 20000)
        ))
        val t = totales(r)
        igual(t.total, 58170L); igual(t.aporte, 4170L); igual(t.conSubsidio, 54000L)
        val h = hojasDelB1(r)[0]
        igual(h.campos["totalCompra"], "581.70"); igual(h.campos["totalDeclaracionJurada"], ""); igual(h.campos["totalAporte"], "41.70")
        igual(h.campos["totalRendicion"], "540.00"); igual(h.campos["distrito"], "Pachacámac"); igual(h.campos["marcaOllaComun"], "X")
        val excel = leerXlsx(excelDelB1(r))
        ok(Regex("Monto total de compra(\\t)+581.7(\\n|$)").containsMatchIn(excel), "monto total de compra")
        ok(Regex("Total de rendición de gastos(\\t)+540(\\n|$)").containsMatchIn(excel), "total de rendición = subsidio")
        ok("Municipalidad Distrital de Pachacámac" in excel, "el distrito en el título")
        val saldo = validarRendicion(r.copy(egresos = r.egresos.take(1))).first { "sin rendir" in it.mensaje }
        igual(saldo.severidad, Severidad.ADVERTENCIA)
        ok("459.30" in saldo.mensaje && "se gasta completo" in saldo.mensaje, saldo.mensaje)
    }
}
