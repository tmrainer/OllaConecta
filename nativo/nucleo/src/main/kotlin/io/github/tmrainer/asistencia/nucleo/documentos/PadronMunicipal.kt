// El padrón como lo piden los formatos que maneja la olla (05-11-2026):
// el formato municipal de actualización de beneficiarios (una fila por
// persona, con socio / usuario / caso social y la dirección) y la ficha del
// libro de socias (la socia y, debajo, sus hijos y otros familiares).
// Solo ordenan y copian lo guardado; lo que falta queda vacío.
package io.github.tmrainer.asistencia.nucleo.documentos

import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.edadEnFecha
import io.github.tmrainer.asistencia.nucleo.calculos.familiaresDe
import io.github.tmrainer.asistencia.nucleo.calculos.tipoBeneficiarioMunicipal
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.DatosOlla
import io.github.tmrainer.asistencia.nucleo.modelo.MESES
import io.github.tmrainer.asistencia.nucleo.modelo.ORDEN_NOMBRES
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import kotlin.math.min

/** Una fila del formato municipal. `tipo`: "socio" | "usuario" | "caso_social" | "". */
data class FilaMunicipal(
    val n: Int,
    val afiliadoId: String,
    val dni: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val nombres: String,
    val nacimiento: String,
    val edad: Int?,
    val sexo: String,
    val direccion: String,
    val tipo: String
)

/** La dirección de una persona: la suya o, si es familiar y no tiene, la de su socia. */
fun direccionDe(a: Afiliado, porId: Map<String, Afiliado>): String =
    a.direccion.trim().ifEmpty { if (a.condicion == "familiar") porId[a.titularId]?.direccion?.trim().orEmpty() else "" }

/**
 * Las personas activas a `fechaRefIso`, como en la hoja municipal: cada socia
 * seguida de sus familiares; al final, quienes no tienen socia anotada
 * (datos de antes o importados). Por apellido, numeradas desde 1.
 */
fun filasPadronMunicipal(padron: Padron, fechaRefIso: String? = null): List<FilaMunicipal> {
    val ref = if (esIso(fechaRefIso)) fechaRefIso!! else hoyIso()
    val activos = afiliadosActivos(padron, ref)
    val porId = padron.afiliados.associateBy { it.id }
    fun orden(a: Afiliado) = "${a.apellidoPaterno} ${a.apellidoMaterno} ${a.nombres}".trim()
    val porNombre = Comparator<Afiliado> { a, b -> ORDEN_NOMBRES.compare(orden(a), orden(b)) }
    val puestos = LinkedHashSet<String>()
    val lista = ArrayList<Afiliado>()
    for (s in activos.filter { it.condicion == "socia" }.sortedWith(porNombre)) {
        lista.add(s); puestos.add(s.id)
        for (f in activos.filter { it.condicion == "familiar" && it.titularId == s.id }.sortedWith(porNombre)) { lista.add(f); puestos.add(f.id) }
    }
    for (a in activos.sortedWith(porNombre)) if (a.id !in puestos) lista.add(a)
    return lista.mapIndexed { i, a ->
        FilaMunicipal(
            n = i + 1, afiliadoId = a.id, dni = a.numeroDocumento,
            apellidoPaterno = a.apellidoPaterno, apellidoMaterno = a.apellidoMaterno, nombres = a.nombres,
            nacimiento = aDdMmAa(a.fechaNacimiento),
            edad = if (esIso(a.fechaNacimiento)) edadEnFecha(a.fechaNacimiento, ref) else null,
            sexo = a.sexo, direccion = direccionDe(a, porId), tipo = tipoBeneficiarioMunicipal(a)
        )
    }
}

private fun marca(tipo: String, cual: String) = if (tipo == cual) "X" else ""
private fun negrita(v: Any?) = Celda(v, negrita = true)
private fun anio(iso: String) = if (esIso(iso)) iso.substring(0, 4) else ""

/** Los títulos de la hoja municipal: con ellos el archivo se puede volver a cargar en otro teléfono. */
val CABECERA_MUNICIPAL = listOf("N°", "DNI", "Apellido paterno", "Apellido materno", "Nombres", "F. nacimiento", "Edad", "Sexo",
    "Dirección (Mz. Lt. Sect. Barrio Urb. AA.HH.)", "Socio", "Usuario", "Caso social", "Firma")

/** El formato municipal de actualización de beneficiarios, en Excel: encabezado de la olla, una fila por persona y las firmas. */
fun excelPadronMunicipal(padron: Padron, olla: DatosOlla, fechaRef: String): ByteArray {
    val filas = filasPadronMunicipal(padron, fechaRef)
    val p = olla.presidenta
    return armarXlsx(listOf(HojaXlsx(
        nombre = "Beneficiarios",
        anchos = listOf(5, 11, 18, 18, 22, 13, 6, 6, 38, 7, 8, 11, 14),
        filas = listOf(
            listOf(negrita("FORMATO DE ACTUALIZACIÓN DE BENEFICIARIOS DEL PROGRAMA DE COMPLEMENTACIÓN ALIMENTARIA - PCA - ${anio(fechaRef)}")),
            emptyList(),
            // Los rótulos de arriba no deben parecer una fila de títulos (la importación
            // busca «nombres», «DNI», «dirección»…): por eso van así escritos.
            listOf(negrita("Datos del centro de atención"), null, null, null, null, null, null, null, negrita("Datos de la presidenta")),
            listOf("Centro de atención", olla.nombre, null, null, null, null, null, null, "Presidenta (apellidos y nombres)", p.nombre),
            listOf("Código del centro", olla.codigoPca, null, null, null, null, null, null, "DNI de la presidenta", p.dni),
            listOf("Dirección del centro", olla.direccion, null, null, null, null, null, null, "N° celular de la presidenta", p.celular),
            listOf("Distrito", olla.distrito, null, null, null, null, null, null, "Total de beneficiarios", filas.size),
            emptyList(),
            CABECERA_MUNICIPAL.map(::negrita)
        ) + filas.map { f ->
            listOf(f.n, f.dni, f.apellidoPaterno, f.apellidoMaterno, f.nombres, f.nacimiento, f.edad, f.sexo, f.direccion,
                marca(f.tipo, "socio"), marca(f.tipo, "usuario"), marca(f.tipo, "caso_social"), "")
        } + listOf(
            emptyList(),
            listOf(negrita("Presidenta"), olla.presidenta.nombre, null, "DNI", olla.presidenta.dni),
            listOf(negrita("Secretaria"), olla.secretaria.nombre, null, "DNI", olla.secretaria.dni),
            listOf(negrita("Tesorera"), olla.tesorera.nombre, null, "DNI", olla.tesorera.dni),
            listOf(negrita("Fiscal"), olla.fiscal.nombre, null, "DNI", olla.fiscal.dni),
            emptyList(),
            listOf("Copia de referencia generada con OllaConecta. No reemplaza al formato oficial firmado.")
        )
    )))
}

/** Un rótulo chico en gris y su dato al lado, sobre una línea punteada. */
private fun dato(l: Lienzo, etiqueta: String, valor: String, x: Double, y: Double, anchoTotal: Double) {
    l.gris(0.4)
    l.texto(etiqueta, x, y, 6.5)
    val xDato = x + anchoMm(etiqueta, 6.5) + 1.5
    l.grosor(0.2); l.punteada(true); l.linea(xDato, y + 0.6, x + anchoTotal, y + 0.6); l.punteada(false)
    l.gris(0.0)
    if (valor.isNotBlank()) celda(l, valor.trim(), xDato, y - 4.2, x + anchoTotal - xDato, 4.6, 8.5)
}

/** Las cuatro firmas de la directiva (presidenta, secretaria, tesorera, fiscal), de lado a lado. */
private fun firmasDirectiva(l: Lienzo, olla: DatosOlla, x: Double, y: Double, ancho: Double) {
    val bloques = listOf("Presidenta" to olla.presidenta, "Secretaria" to olla.secretaria, "Tesorera" to olla.tesorera, "Fiscal" to olla.fiscal)
    val w = ancho / bloques.size
    bloques.forEachIndexed { i, (cargo, d) ->
        val bx = x + i * w
        l.gris(0.0); l.grosor(0.3)
        l.linea(bx + 6, y + 14, bx + w - 6, y + 14)
        l.texto(cargo, bx + w / 2 - anchoMm(cargo, 7.0, true) / 2, y + 18, 7.0, negrita = true)
        if (d.nombre.isNotBlank()) celda(l, d.nombre.trim(), bx + 2, y + 19, w - 4, 4.6, 7.0, "center")
        if (d.dni.isNotBlank()) celda(l, "DNI ${d.dni.trim()}", bx + 2, y + 23.5, w - 4, 4.6, 7.0, "center")
    }
}

/**
 * El formato municipal de actualización de beneficiarios, en PDF apaisado:
 * los datos de la olla y de la presidenta, una fila por persona con espacio
 * para firmar, y las firmas de la directiva al final.
 * @param generado «14-10-26 18:40»; por defecto, ahora
 */
fun pdfPadronMunicipal(padron: Padron, olla: DatosOlla, fechaRef: String, generado: String = selloAhora()): ByteArray {
    val filas = filasPadronMunicipal(padron, fechaRef)
    val doc = Documento(
        titulo = "Formato de actualización de beneficiarios del PCA - ${anio(fechaRef)}".trimEnd(' ', '-'),
        subtitulo = "Activas al ${aDdMmAa(fechaRef)}",
        generado = generado
    )
    val columnas = listOf(
        ColumnaTabla("N°", 8.0, "right"), ColumnaTabla("DNI", 20.0, "center"),
        ColumnaTabla("Apellido paterno", 30.0, "left"), ColumnaTabla("Apellido materno", 30.0, "left"), ColumnaTabla("Nombres", 36.0, "left"),
        ColumnaTabla("F. nacimiento", 18.0, "center"), ColumnaTabla("Edad", 10.0, "center"), ColumnaTabla("Sexo", 9.0, "center"),
        ColumnaTabla("Dirección (Mz. Lt. Sect. Barrio Urb. AA.HH.)", 58.0, "left"),
        ColumnaTabla("Socio", 12.0, "center"), ColumnaTabla("Usuario", 14.0, "center"), ColumnaTabla("Caso social", 14.0, "center"),
        ColumnaTabla("Firma", 10.0, "center")
    )
    val ancho = columnas.sumOf { it.ancho }
    val datos = filas.map { f ->
        listOf(f.n, f.dni, f.apellidoPaterno, f.apellidoMaterno, f.nombres, f.nacimiento, f.edad ?: "", f.sexo, f.direccion,
            marca(f.tipo, "socio"), marca(f.tipo, "usuario"), marca(f.tipo, "caso_social"), "")
    }
    val p = olla.presidenta
    fun encabezado(h: Nueva): Double {
        var y = h.arriba + 2
        dato(h.l, "Nombre del centro de atención:", olla.nombre, MARGEN, y, 118.0)
        dato(h.l, "Código:", olla.codigoPca, MARGEN + 122, y, 36.0)
        dato(h.l, "Presidenta (apellidos y nombres):", p.nombre, MARGEN + 162, y, ancho - 162)
        y += 7
        dato(h.l, "Dirección:", olla.direccion, MARGEN, y, 118.0)
        dato(h.l, "Distrito:", olla.distrito, MARGEN + 122, y, 36.0)
        dato(h.l, "DNI:", p.dni, MARGEN + 162, y, 40.0)
        dato(h.l, "N° celular:", p.celular, MARGEN + 206, y, ancho - 206)
        y += 7
        h.l.texto("Total de beneficiarios: ${filas.size}", MARGEN, y, 8.0, negrita = true)
        return y + 4
    }
    val porHoja = 15
    var hoja = doc.nueva(apaisada = true)
    var y = encabezado(hoja)
    if (datos.isEmpty()) hoja.l.texto("El padrón está vacío.", MARGEN, y + 8, 10.0)
    var fin = y
    for (i in datos.indices step porHoja) {
        if (i > 0) { hoja = doc.nueva(apaisada = true); y = encabezado(hoja) }
        fin = tabla(hoja.l, MARGEN, y, columnas, datos.subList(i, min(i + porHoja, datos.size)), altoFila = 7.5)
    }
    // Las firmas de la directiva, al final; si no entran, en otra hoja.
    if (fin > hoja.alto - 40) { hoja = doc.nueva(apaisada = true); fin = hoja.arriba }
    firmasDirectiva(hoja.l, olla, MARGEN, fin + 6, ancho)
    return doc.bytes("Formato de beneficiarios")
}

private fun fechaEnLetras(iso: String): String {
    if (!esIso(iso)) return ""
    val (a, m, d) = iso.split("-")
    return "${d.toInt()} de ${MESES[m.toInt() - 1].lowercase()} de $a"
}

private fun sexoLetra(s: String) = when (s) { "F" -> "F"; "M" -> "M"; else -> "" }

/**
 * Una ficha de socia como en el libro («Registro de padrón de socios»), sobre
 * la hoja [h]: sus datos, su foto, y debajo sus hijos y los otros familiares
 * del mismo domicilio. Sin firma ni huella: eso va en el libro.
 */
private fun dibujarFicha(h: Nueva, padron: Padron, socia: Afiliado, foto: ByteArray?) {
    val l = h.l
    val ancho = h.ancho - 2 * MARGEN
    // La foto de la socia arriba a la derecha, como en el libro (3 × 4 cm); los datos de la derecha le dejan sitio.
    val conFoto = foto != null && medidasJpeg(foto) != null
    if (conFoto) {
        val (an, al) = medidasJpeg(foto!!)!!.let { it.first.toDouble() to it.second.toDouble() }
        val escala = min(30.0 / an, 40.0 / al)
        val w = an * escala; val fh = al * escala
        val fx = h.ancho - MARGEN - 30 + (30 - w) / 2
        l.gris(0.55); l.grosor(0.2); l.caja(h.ancho - MARGEN - 30, h.arriba + 1, 30.0, 40.0); l.gris(0.0)
        l.imagen(foto, fx, h.arriba + 1 + (40 - fh) / 2, w, fh)
    }
    val mitad = ancho / 2 - 4
    val derecha = if (conFoto) mitad - 34 else mitad
    var y = h.arriba + 6
    val filas = listOf(
        listOf("Nombres:" to socia.nombres, "Carnet N°:" to socia.carnet),
        listOf("Apellido paterno:" to socia.apellidoPaterno, "Fecha de ingreso:" to aDdMmAa(socia.altaEn)),
        listOf("Apellido materno:" to socia.apellidoMaterno, "DNI N°:" to socia.numeroDocumento),
        listOf("Fecha de nacimiento:" to aDdMmAa(socia.fechaNacimiento), "Sexo:" to sexoLetra(socia.sexo)),
        listOf("Distrito:" to socia.distrito, "Provincia:" to socia.provincia),
        listOf("Departamento:" to socia.departamento, "Estado civil:" to socia.estadoCivil),
        listOf("Profesión:" to socia.profesion, "Actividad:" to socia.actividad),
        listOf("Grado de instrucción:" to socia.gradoInstruccion, "Teléfono:" to socia.telefono)
    )
    filas.forEachIndexed { i, fila ->
        dato(l, fila[0].first, fila[0].second, MARGEN, y, mitad)
        // Las seis primeras filas de la derecha quedan al lado de la foto.
        dato(l, fila[1].first, fila[1].second, MARGEN + mitad + 8, y, if (i < 6) derecha else mitad)
        y += 7.5
    }
    dato(l, "Dirección:", socia.direccion, MARGEN, y, ancho)
    y += 10

    val familia = familiaresDe(padron, socia.id).filter { it.activo }
    val hijos = familia.filter { it.parentesco == "hijo/a" }
    val otros = familia.filter { it.parentesco != "hijo/a" }
    fun filaDe(f: Afiliado, cuarta: String) = listOf(nombreCompleto(f), sexoLetra(f.sexo), aDdMmAa(f.fechaNacimiento), cuarta, f.numeroDocumento)

    l.texto("Hijos/as", MARGEN, y, 8.5, negrita = true)
    y += 2
    val colsHijos = listOf(ColumnaTabla("Nombre y apellidos", 80.0, "left"), ColumnaTabla("Sexo", 14.0, "center"),
        ColumnaTabla("Fecha de nacimiento", 30.0, "center"), ColumnaTabla("Ocupación", 38.0, "left"), ColumnaTabla("DNI", 20.0, "center"))
    y = tabla(l, MARGEN, y, colsHijos, hijos.map { filaDe(it, it.ocupacion) }.ifEmpty { listOf(listOf("—", "", "", "", "")) }, altoFila = 6.5)
    y += 8
    l.texto("Otros familiares en el mismo domicilio", MARGEN, y, 8.5, negrita = true)
    y += 2
    val colsOtros = listOf(ColumnaTabla("Nombre y apellidos", 80.0, "left"), ColumnaTabla("Sexo", 14.0, "center"),
        ColumnaTabla("Fecha de nacimiento", 30.0, "center"), ColumnaTabla("Parentesco", 38.0, "left"), ColumnaTabla("DNI", 20.0, "center"))
    y = tabla(l, MARGEN, y, colsOtros, otros.map { filaDe(it, it.parentesco) }.ifEmpty { listOf(listOf("—", "", "", "", "")) }, altoFila = 6.5)
    y += 12

    dato(l, "Inscrita / aprobada:", fechaEnLetras(socia.altaEn), MARGEN, y, mitad)
    l.gris(0.0)
    l.texto("Me comprometo a cumplir el Estatuto y Reglamento de la institución.", MARGEN + mitad + 8, y, 7.0)
    y += 22
    val w = ancho / 3
    listOf("Presidenta", "Secretaria", "Firma de la titular").forEachIndexed { i, cargo ->
        val bx = MARGEN + i * w
        l.grosor(0.3); l.linea(bx + 8, y, bx + w - 8, y)
        l.texto(cargo, bx + w / 2 - anchoMm(cargo, 7.0, true) / 2, y + 4, 7.0, negrita = true)
    }
}

/**
 * La ficha de una socia como en el libro («Registro de padrón de socios»).
 * @param generado «14-10-26 18:40»; por defecto, ahora
 * @param foto la foto de la socia (JPEG), si la hay
 */
fun pdfFichaSocia(padron: Padron, socia: Afiliado, olla: DatosOlla, generado: String = selloAhora(), foto: ByteArray? = null): ByteArray {
    val doc = Documento(titulo = "Registro de padrón de socias", subtitulo = olla.nombre.trim(), generado = generado)
    dibujarFicha(doc.nueva(), padron, socia, foto)
    return doc.bytes("Ficha de socia")
}

/** Las socias activas, por apellido, numeradas: el orden del libro. */
fun sociasDelLibro(padron: Padron, fechaRefIso: String? = null): List<Afiliado> {
    val ref = if (esIso(fechaRefIso)) fechaRefIso!! else hoyIso()
    fun orden(a: Afiliado) = "${a.apellidoPaterno} ${a.apellidoMaterno} ${a.nombres}".trim()
    return afiliadosActivos(padron, ref).filter { it.condicion == "socia" }.sortedWith { a, b -> ORDEN_NOMBRES.compare(orden(a), orden(b)) }
}

/**
 * El padrón de socias entero, vertical como el libro: una ficha por socia (con
 * su foto y su familia), en el orden del libro. [fotos]: la foto de cada socia
 * por su id, si la hay.
 * @param generado «14-10-26 18:40»; por defecto, ahora
 */
fun pdfLibroDeSocias(padron: Padron, olla: DatosOlla, fechaRef: String, generado: String = selloAhora(), fotos: ((String) -> ByteArray?)? = null): ByteArray {
    val socias = sociasDelLibro(padron, fechaRef)
    val doc = Documento(titulo = "Padrón de socias (libro)", subtitulo = "${olla.nombre.trim()}${if (olla.nombre.isBlank()) "" else " · "}${socias.size} socias al ${aDdMmAa(fechaRef)}", generado = generado)
    if (socias.isEmpty()) {
        val h = doc.nueva()
        h.l.texto("Todavía no hay socias anotadas: en el padrón, marca a cada titular como «Socia».", MARGEN, h.arriba + 8, 10.0)
    }
    for (s in socias) {
        val h = doc.nueva()
        h.l.texto("Socia N° ${socias.indexOf(s) + 1}", h.ancho - MARGEN - anchoMm("Socia N° ${socias.indexOf(s) + 1}", 8.0, true), h.arriba - 4, 8.0, negrita = true)
        dibujarFicha(h, padron, s, fotos?.invoke(s.id))
    }
    return doc.bytes("Padrón de socias")
}

/**
 * El padrón de socias en Excel, vertical como el libro: un bloque por socia
 * (sus datos en dos columnas) y debajo su familia. Sin fotos: el Excel no las lleva.
 */
fun excelLibroDeSocias(padron: Padron, olla: DatosOlla, fechaRef: String): ByteArray {
    val socias = sociasDelLibro(padron, fechaRef)
    val filas = mutableListOf<List<Any?>?>(
        listOf(negrita("PADRÓN DE SOCIAS (libro)")),
        listOf(olla.nombre, null, "Socias al ${aDdMmAa(fechaRef)}", socias.size),
        emptyList()
    )
    socias.forEachIndexed { i, s ->
        filas.add(listOf(negrita("Socia N° ${i + 1}"), negrita(nombreCompleto(s))))
        filas.add(listOf("Nombres", s.nombres, null, "Apellido paterno", s.apellidoPaterno, null, "Apellido materno", s.apellidoMaterno))
        filas.add(listOf("DNI", s.numeroDocumento, null, "Fecha de nacimiento", aDdMmAa(s.fechaNacimiento), null, "Sexo", sexoLetra(s.sexo)))
        filas.add(listOf("Dirección", s.direccion, null, "Teléfono", s.telefono, null, "Fecha de ingreso", aDdMmAa(s.altaEn)))
        filas.add(listOf("Distrito", s.distrito, null, "Provincia", s.provincia, null, "Departamento", s.departamento))
        filas.add(listOf("Profesión", s.profesion, null, "Actividad", s.actividad, null, "Grado de instrucción", s.gradoInstruccion))
        filas.add(listOf("Estado civil", s.estadoCivil, null, "Carnet N°", s.carnet))
        val familia = familiaresDe(padron, s.id).filter { it.activo }
        filas.add(listOf(negrita("Familia"), negrita("Nombre y apellidos"), negrita("Parentesco"), negrita("Sexo"), negrita("Fecha de nacimiento"), negrita("Ocupación"), negrita("DNI")))
        if (familia.isEmpty()) filas.add(listOf(null, "—"))
        for (f in familia) filas.add(listOf(null, nombreCompleto(f), f.parentesco, sexoLetra(f.sexo), aDdMmAa(f.fechaNacimiento), f.ocupacion, f.numeroDocumento))
        filas.add(emptyList())
    }
    if (socias.isEmpty()) filas.add(listOf("Todavía no hay socias anotadas: en el padrón, marca a cada titular como «Socia»."))
    filas.add(listOf("Copia de referencia generada con OllaConecta. No reemplaza al libro firmado."))
    return armarXlsx(listOf(HojaXlsx(nombre = "Socias", filas = filas, anchos = listOf(16, 26, 14, 18, 22, 14, 20, 20))))
}
