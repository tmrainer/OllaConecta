// Lectura de un padrón pegado desde Excel o de un CSV (un .xlsx entra como el
// texto con tabuladores que devuelve leerXlsx()). Funciones PURAS: sin
// Android, sin estado, sin red.
//
// Interpreta lo que la usuaria pega, pero NO completa ni inventa lo que falte:
// una fecha que no existe («31/02/1990») o una edad sin cifras («s/d») quedan
// vacías con su aviso, y es ella quien confirma la importación.
package io.github.tmrainer.asistencia.nucleo.importar

import io.github.tmrainer.asistencia.nucleo.calculos.grupoPorEdad
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import java.time.LocalDate

// Los espacios como los entiende JavaScript (\s y trim()): incluyen el espacio
// duro y la marca BOM (U+FEFF), que Java no cuenta como espacio. Un CSV con
// BOM trae esa marca pegada al primer título.
private const val CLASE_ESPACIO = "\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
private val ESPACIO = Regex("[$CLASE_ESPACIO]")
private val ESPACIOS = Regex("[$CLASE_ESPACIO]+")

private fun esEspacio(c: Char) = ESPACIO.matches(c.toString())

/** trim() de JavaScript. */
private fun recortar(t: String) = t.trim(::esEspacio)

/** Detecta el separador mirando la primera línea fuera de comillas. */
fun detectarSeparador(texto: String?): Char {
    val linea = (texto ?: "").split(Regex("\r?\n"))[0]
    var dentro = false
    val cuenta = linkedMapOf('\t' to 0, ';' to 0, ',' to 0)
    for (ch in linea) {
        if (ch == '"') dentro = !dentro
        else if (!dentro && ch in cuenta) cuenta[ch] = cuenta.getValue(ch) + 1
    }
    // maxBy se queda con el primero entre empates: tabulador, luego ; y luego ,
    val mejor = cuenta.entries.maxBy { it.value }
    return if (mejor.value > 0) mejor.key else '\t'
}

/**
 * Una fila del archivo. `linea` es su número en el archivo (contando las
 * vacías que se saltaron), para que «fila 7» sea la fila 7 que se ve en Excel.
 */
data class FilaTabla(val celdas: List<String>, val linea: Int) {
    operator fun get(i: Int): String = celdas[i]
}

/**
 * CSV/TSV a matriz de celdas. Admite comillas, separadores y saltos dentro.
 * Las filas vacías se saltan, pero cada fila recuerda en `linea` su número en
 * el archivo: así «fila 7» en la revisión es la fila 7 que se ve en Excel.
 */
fun parsearTabla(texto: String?, separador: Char? = null): List<FilaTabla> {
    val sep = separador ?: detectarSeparador(texto)
    val filas = ArrayList<List<String>>()
    var fila = ArrayList<String>()
    val celda = StringBuilder()
    var dentro = false
    val s = (texto ?: "").replace("\r\n", "\n").replace('\r', '\n')

    var i = 0
    while (i < s.length) {
        val ch = s[i]
        if (dentro) {
            if (ch == '"') {
                if (i + 1 < s.length && s[i + 1] == '"') { celda.append('"'); i += 1 } else dentro = false
            } else celda.append(ch)
        } else when (ch) {
            '"' -> dentro = true
            sep -> { fila.add(celda.toString()); celda.clear() }
            '\n' -> { fila.add(celda.toString()); filas.add(fila); fila = ArrayList(); celda.clear() }
            else -> celda.append(ch)
        }
        i += 1
    }
    fila.add(celda.toString())
    filas.add(fila)

    return filas
        .mapIndexed { n, f -> FilaTabla(f.map { desarmarCelda(recortar(it)) }, n + 1) }
        .filter { f -> f.celdas.any { it.isNotEmpty() } }
}

private val FORMULA = Regex("=\"(.*)\"")

/**
 * Las versiones anteriores exportaban el padrón como CSV: los documentos iban
 * como ="01234567" (para que Excel no se coma el 0) y lo que parecía fórmula,
 * con ' adelante. Al importar uno de esos archivos se deshace: ="0123" → 0123,
 * '=algo → =algo. Hoy se exporta en .xlsx, que no necesita nada de esto.
 */
fun desarmarCelda(texto: String?): String {
    val t = texto ?: ""
    FORMULA.matchEntire(t)?.let { return it.groupValues[1].replace("\"\"", "\"") }
    if (t.length >= 2 && t[0] == '\'' && t[1] in "=+-@") return t.substring(1)
    return t
}

// El orden importa: gana el primer campo que coincide.
private val SINONIMOS: Map<String, List<String>> = linkedMapOf(
    "apellidoPaterno" to listOf("apellido paterno", "ap paterno", "ap. paterno", "apaterno",
        "paterno", "primer apellido"),
    "apellidoMaterno" to listOf("apellido materno", "ap materno", "ap. materno", "amaterno",
        "materno", "segundo apellido"),
    "nombres" to listOf("nombres", "nombre", "nombre(s)"),
    "apellidos" to listOf("apellidos", "apellido"),
    "numeroDocumento" to listOf("numero de documento", "numero documento", "n documento",
        "nro documento", "nro de documento", "documento", "dni", "dni/ce", "doc", "numero"),
    "tipoDocumento" to listOf("tipo de documento", "tipo documento", "tipo doc"),
    "fechaNacimiento" to listOf("fecha de nacimiento", "fecha nacimiento", "nacimiento",
        "fec nac", "fecha nac", "f nacimiento"),
    "grupoEtario" to listOf("grupo de edad", "grupo etario", "grupo", "etapa de vida", "etapa"),
    "edad" to listOf("edad", "anos", "años"),
    "tipoAfiliado" to listOf("tipo", "tipo de afiliada", "tipo de afiliado", "condicion",
        "categoria", "ayuda social"),
    "sexo" to listOf("sexo", "genero")
)

private fun norm(t: String?): String =
    recortar(normalizarNombre(t).replace(".", "").replace(ESPACIOS, " "))

/** Qué columna del archivo va a cada campo del padrón, y los títulos que no se entendieron. */
data class Columnas(val mapa: Map<String, Int>, val sinReconocer: List<String>)

/** Asocia cada columna del archivo a un campo del padrón. Lo que no reconoce, lo ignora. */
fun detectarColumnas(cabecera: List<String>?): Columnas {
    val mapa = LinkedHashMap<String, Int>()
    val sinReconocer = ArrayList<String>()
    cabecera.orEmpty().forEachIndexed { i, titulo ->
        val t = norm(titulo)
        if (t.isEmpty()) return@forEachIndexed
        val encontrado = SINONIMOS.entries.firstOrNull { t in it.value }?.key
            ?: SINONIMOS.entries.firstOrNull { (_, nombres) -> nombres.any { t.startsWith(it) } }?.key
        if (encontrado == null) sinReconocer.add(titulo)
        else if (encontrado !in mapa) mapa[encontrado] = i
    }
    return Columnas(mapa, sinReconocer)
}

fun pareceCabecera(fila: List<String>): Boolean = detectarColumnas(fila).mapa.size >= 2

private val FECHA_DMA = Regex("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})")

/**
 * 'DD/MM/AAAA', 'DD-MM-AA', 'DD.MM.AAAA' o ISO, a ISO. Lo que no se entiende
 * o no existe devuelve ''.
 * @param hoy fija el pivote de los años de dos cifras
 */
fun parsearFechaFlexible(texto: String?, hoy: String = hoyIso()): String {
    val t = recortar(texto ?: "")
    if (t.isEmpty()) return ""
    if (esIso(t)) return if (aFecha(t) != null) t else ""
    val m = FECHA_DMA.matchEntire(t) ?: return ""
    val (d, mes, a) = m.destructured
    // Dos cifras: lo que pasa del año en curso es del siglo pasado (es una
    // fecha de nacimiento: «40» es 1940, no 2040).
    val pivote = (aFecha(hoy) ?: LocalDate.now()).year % 100
    val anio = if (a.length == 2) (if (a.toInt() > pivote) "19$a" else "20$a") else a
    val iso = "$anio-${mes.padStart(2, '0')}-${d.padStart(2, '0')}"
    return if (aFecha(iso) != null) iso else ""
}

private val GRUPO_NINO = Regex("^(nino|nina|ninos|menor|infante)")
private val GRUPO_ADOLESCENTE = Regex("^adolescente")
private val GRUPO_ADULTO_MAYOR = Regex("^adulto(/a)? mayor|^tercera edad|^anciano")
private val GRUPO_ADULTO = Regex("^adulto|^joven")

fun parsearGrupoEtario(texto: String?): String? {
    val t = norm(texto)
    if (t.isEmpty()) return null
    if (GRUPO_NINO.containsMatchIn(t)) return "nino"
    if (GRUPO_ADOLESCENTE.containsMatchIn(t)) return "adolescente"
    // «Adulto/a mayor» es como la app escribe el grupo al exportar.
    if (GRUPO_ADULTO_MAYOR.containsMatchIn(t)) return "adulto_mayor"
    if (GRUPO_ADULTO.containsMatchIn(t)) return "adulto"
    return null
}

private val TIPO_SOCIAL = Regex("ayuda social|caso social|^social|vulnerable|^si$|^x$")
private val TIPO_HABITUAL = Regex("habitual|regular|normal|comun|^no$")

fun parsearTipoAfiliado(texto: String?): String {
    val t = norm(texto)
    if (t.isEmpty()) return ""
    if (TIPO_SOCIAL.containsMatchIn(t)) return "caso_social"
    if (TIPO_HABITUAL.containsMatchIn(t)) return "habitual"
    return ""
}

private val TIPO_CE = Regex("^ce|extranjeria|carne")

fun parsearTipoDocumento(texto: String?): String =
    if (TIPO_CE.containsMatchIn(norm(texto))) "ce" else "dni"

/** «Quispe Rojas Huamán» → paterno «Quispe», materno «Rojas Huamán». */
private fun partirApellidos(texto: String): Pair<String, String> {
    val partes = recortar(texto).split(ESPACIOS).filter { it.isNotEmpty() }
    return when (partes.size) {
        0 -> "" to ""
        1 -> partes[0] to ""
        else -> partes[0] to partes.drop(1).joinToString(" ")
    }
}

/** Lo que pasaría con una fila del archivo. `estado`: ver prepararImportacion(). */
data class FilaImportada(
    val linea: Int,
    val afiliado: Afiliado,
    val nombreCompleto: String,
    val estado: String,          // "lista" | "incompleta" | "duplicada" | "vacia"
    val avisos: List<String>,
    val ceroAgregado: Boolean
)

data class ResumenImportacion(
    val lista: Int = 0,
    val incompleta: Int = 0,
    val duplicada: Int = 0,
    val vacia: Int = 0,
    val conCero: Int = 0
)

/** Resultado de prepararImportacion(). Con `error`, no hay filas. */
data class Importacion(
    val columnas: Map<String, Int>,
    val sinReconocer: List<String>,
    val filas: List<FilaImportada>,
    val resumen: ResumenImportacion,
    val error: String?
)

private val DNI = Regex("^\\d{8}$")
private val DNI_SIN_CERO = Regex("^\\d{5,7}$")

/**
 * Lee el texto pegado (o el de un CSV, o el que devuelve leerXlsx) y devuelve,
 * por fila, la persona que se crearía y qué le falta. NO modifica el padrón ni
 * completa ningún dato: la usuaria confirma.
 *
 * estado: "lista"      — se puede importar
 *         "incompleta" — se importa, pero quedará señalada en el padrón
 *         "duplicada"  — ya existe ese documento activo; no se importa
 *         "vacia"      — sin nombre utilizable; no se importa
 *
 * ceroAgregado: un DNI de 5 a 7 cifras es uno que empezaba con 0 y que Excel
 * guardó como número (01234567 → 1234567). Se completa con ceros adelante y se
 * dice en la fila; `resumen.conCero` los cuenta. Es la única corrección que
 * hace la importación.
 *
 * @param hoy el alta de cada persona y el pivote de los años de dos cifras
 */
fun prepararImportacion(texto: String?, padron: Padron?, hoy: String = hoyIso()): Importacion {
    val tabla = parsearTabla(texto)
    if (tabla.isEmpty()) {
        return Importacion(emptyMap(), emptyList(), emptyList(), ResumenImportacion(), "No se encontró ninguna fila.")
    }

    // Muchos padrones traen arriba un título («PADRÓN DE BENEFICIARIOS…») o el
    // nombre del comedor: los títulos de las columnas se buscan en las primeras
    // filas, no solo en la primera.
    val fila0 = tabla.take(10).indexOfFirst { pareceCabecera(it.celdas) }
    if (fila0 < 0) {
        return Importacion(
            emptyMap(), emptyList(), emptyList(), ResumenImportacion(),
            "No se encontró la fila de títulos. Debe traer títulos " +
                "como «Apellido paterno», «Nombres», «DNI», «Grupo de edad», «Tipo»."
        )
    }

    val (mapa, sinReconocer) = detectarColumnas(tabla[fila0].celdas)
    val cuerpo = tabla.drop(fila0 + 1)
    val activos = padron?.afiliados.orEmpty().filter { it.activo }
    val docsExistentes = activos.filter { it.numeroDocumento.isNotEmpty() }.map { it.numeroDocumento }.toSet()
    val nombresExistentes = activos
        .map { normalizarNombre("${it.apellidoPaterno} ${it.apellidoMaterno} ${it.nombres}") }.toSet()
    val docsEnElArchivo = HashSet<String>()

    val filas = cuerpo.map { fila ->
        fun valor(campo: String): String = mapa[campo]?.let { recortar(fila.celdas.getOrNull(it) ?: "") } ?: ""
        val avisos = ArrayList<String>()

        var apellidoPaterno = valor("apellidoPaterno")
        var apellidoMaterno = valor("apellidoMaterno")
        if (apellidoPaterno.isEmpty() && valor("apellidos").isNotEmpty()) {
            val (paterno, materno) = partirApellidos(valor("apellidos"))
            apellidoPaterno = paterno
            apellidoMaterno = materno
        }
        val nombres = valor("nombres")

        var numeroDocumento = valor("numeroDocumento").replace(ESPACIO, "")
        val tipoDocumento = if ("tipoDocumento" in mapa) parsearTipoDocumento(valor("tipoDocumento")) else "dni"
        val esDni = tipoDocumento == "dni"
        val ceroAgregado = esDni && DNI_SIN_CERO.matches(numeroDocumento)
        if (ceroAgregado) {
            numeroDocumento = numeroDocumento.padStart(8, '0')
            avisos.add("se agregó el 0 de adelante que Excel había quitado")
        }

        val fnac = parsearFechaFlexible(valor("fechaNacimiento"), hoy)
        if (valor("fechaNacimiento").isNotEmpty() && fnac.isEmpty()) avisos.add("no se entendió la fecha de nacimiento")

        var grupo = parsearGrupoEtario(valor("grupoEtario"))
        if (grupo == null && valor("edad").isNotEmpty()) {
            // Sin ninguna cifra no hay edad.
            val cifras = valor("edad").filter { it in '0'..'9' }
            grupo = if (cifras.isEmpty()) null else grupoPorEdad(cifras.toIntOrNull() ?: Int.MAX_VALUE)
            if (grupo == null) avisos.add("no se entendió la edad")
            // Sin fecha de nacimiento, el grupo sacado de la edad se guarda fijo en
            // grupoEtarioManual: no cambia cuando la persona cumple años.
            else if (fnac.isEmpty()) avisos.add("grupo de edad sacado de la edad: no cambiará solo con los años, conviene anotar la fecha de nacimiento")
        }
        val grupoEtarioManual = if (fnac.isEmpty()) grupo else null

        val tipoAfiliado = parsearTipoAfiliado(valor("tipoAfiliado"))
        val sexoTexto = norm(valor("sexo"))
        val sexo = when {
            sexoTexto.startsWith("mujer") -> "F"
            sexoTexto.startsWith("hombre") -> "M"
            sexoTexto.startsWith("f") -> "F"
            sexoTexto.startsWith("m") -> "M"
            else -> ""
        }

        val a = nuevoAfiliado(hoy).copy(
            origenDato = "importado",
            apellidoPaterno = apellidoPaterno,
            apellidoMaterno = apellidoMaterno,
            nombres = nombres,
            numeroDocumento = numeroDocumento,
            tipoDocumento = tipoDocumento,
            fechaNacimiento = fnac,
            grupoEtarioManual = grupoEtarioManual,
            tipoAfiliado = tipoAfiliado,
            sexo = sexo
        )

        val nombreCompleto = recortar("$apellidoPaterno $apellidoMaterno $nombres")
        var estado = "lista"

        if (apellidoPaterno.isEmpty() && nombres.isEmpty()) {
            estado = "vacia"
            avisos.add("la fila no trae ni apellido ni nombre")
        } else if (numeroDocumento.isNotEmpty() && numeroDocumento in docsExistentes) {
            estado = "duplicada"
            avisos.add("ese documento ya está en el padrón")
        } else if (numeroDocumento.isNotEmpty() && numeroDocumento in docsEnElArchivo) {
            estado = "duplicada"
            avisos.add("ese documento se repite dentro del propio archivo")
        } else {
            if (numeroDocumento.isNotEmpty()) docsEnElArchivo.add(numeroDocumento)
            val falta = ArrayList<String>()
            val dniMalo = esDni && numeroDocumento.isNotEmpty() && !DNI.matches(numeroDocumento)
            if (numeroDocumento.isEmpty()) falta.add("documento")
            else if (dniMalo) avisos.add("el DNI no tiene 8 dígitos")
            if (fnac.isEmpty() && grupoEtarioManual == null) falta.add("grupo de edad")
            if (tipoAfiliado.isEmpty()) falta.add("tipo")
            if (falta.isNotEmpty()) avisos.add(0, "falta " + falta.joinToString(", "))
            if (falta.isNotEmpty() || dniMalo) estado = "incompleta"
            if (normalizarNombre(nombreCompleto) in nombresExistentes) {
                avisos.add("ya hay alguien con ese mismo nombre")
            }
        }

        FilaImportada(fila.linea, a, nombreCompleto, estado, avisos, ceroAgregado && estado != "vacia")
    }

    val resumen = ResumenImportacion(
        lista = filas.count { it.estado == "lista" },
        incompleta = filas.count { it.estado == "incompleta" },
        duplicada = filas.count { it.estado == "duplicada" },
        vacia = filas.count { it.estado == "vacia" },
        conCero = filas.count { it.ceroAgregado && it.estado != "duplicada" }
    )

    return Importacion(mapa, sinReconocer, filas, resumen, null)
}

/** Las filas que realmente se darían de alta. Las duplicadas y vacías quedan fuera. */
fun filasImportables(filas: List<FilaImportada>?): List<FilaImportada> =
    filas.orEmpty().filter { it.estado == "lista" || it.estado == "incompleta" }
