// Lectura del DNI: MRZ TD1, anverso, PDF417 y lector HID. Funciones puras:
// sin android.*, sin estado, sin red.
//
// parsearCadenaDni es un stub deliberado: la codificación del PDF417 del DNI
// no está verificada y un parser contra un formato supuesto inventaría datos.
// leerPdf417DniAzul es de un tercero, SIN VERIFICAR.
//
// Privacidad: lo leído (líneas de la MRZ, filas del anverso, la cadena
// del PDF417) vive solo en memoria mientras dura la lectura. No se guarda, no
// va a un log, no se muestra crudo. Por eso LecturaMrz no imprime sus líneas.
package io.github.tmrainer.asistencia.nucleo.dni

import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tan

val CAMPOS: List<String> = listOf(
    "tipoDocumento", "numeroDocumento", "apellidoPaterno",
    "apellidoMaterno", "nombres", "fechaNacimiento", "sexo"
)

enum class Confianza(val clave: String) { ALTA("alta"), BAJA("baja"), DESCONOCIDA("desconocida") }

/** Lo que devuelve parsearCadenaDni (contrato). */
data class DatosDni(
    val tipoDocumento: String?,              // "dni" | "ce" | null
    val numeroDocumento: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val nombres: String,
    val fechaNacimiento: String,             // ISO, o ""
    val sexo: String,                        // "F" | "M" | ""
    val camposNoLeidos: List<String>,
    val confianza: Confianza
)

private fun resultadoVacio(camposNoLeidos: List<String>, confianza: Confianza) = DatosDni(
    tipoDocumento = null,
    numeroDocumento = "",
    apellidoPaterno = "",
    apellidoMaterno = "",
    nombres = "",
    fechaNacimiento = "",
    sexo = "",
    camposNoLeidos = camposNoLeidos.toList(),
    confianza = confianza
)

/**
 * Parsea la cadena que entrega un lector de códigos de barras.
 * NUNCA lanza: ante cualquier entrada devuelve el objeto completo.
 * Campo que no se puede leer con certeza queda vacío y listado en camposNoLeidos.
 */
@Suppress("UNUSED_PARAMETER")
fun parsearCadenaDni(cadena: String?): DatosDni {
    // NO IMPLEMENTADO a propósito: ver la nota de cabecera.
    // La entrada se ignora por completo; ni siquiera se inspecciona, para que
    // nadie confunda este stub con un parser a medio hacer.
    return resultadoVacio(CAMPOS, Confianza.DESCONOCIDA)
}

// En verificación. Dos fuentes:
// - La MRZ del reverso, en todos los DNI: TD1 de ICAO 9303 (parte 5), pública,
//   con dígitos de control.
// - El PDF417 del DNI azul (1997): distribución de campos de un tercero, SIN VERIFICAR.

/** Valor de un carácter de la MRZ para el dígito de control (ICAO 9303-3 §4.9). */
private fun valorIcao(c: Char): Int = when (c) {
    '<' -> 0
    in '0'..'9' -> c - '0'
    in 'A'..'Z' -> c - 'A' + 10
    else -> -1
}

/**
 * Dígito de control ICAO: pesos 7, 3, 1 que se repiten, suma módulo 10.
 * @param texto solo A-Z, 0-9 y «<»
 * @return 0 a 9, o -1 si hay un carácter que no vale en una MRZ
 */
fun digitoControlIcao(texto: String?): Int {
    val pesos = intArrayOf(7, 3, 1)
    var suma = 0
    val s = texto ?: ""
    for (i in s.indices) {
        val v = valorIcao(s[i])
        if (v < 0) return -1
        suma += v * pesos[i % 3]
    }
    return suma % 10
}

private fun controlOk(campo: String, digito: Char) =
    digito in '0'..'9' && digitoControlIcao(campo) == digito - '0'

private val SEIS_CIFRAS = Regex("^\\d{6}$")

/** AAMMDD de la MRZ a ISO. El siglo sale de la fecha de referencia. */
private fun fechaMrz(aammdd: String, pasado: Boolean, fechaRef: String): String {
    if (!SEIS_CIFRAS.matches(aammdd)) return ""
    val aa = aammdd.substring(0, 2).toInt()
    val mm = aammdd.substring(2, 4).toInt()
    val dd = aammdd.substring(4, 6).toInt()
    val anioRef = fechaRef.take(4).toIntOrNull() ?: return ""
    val siglo = Math.floorDiv(anioRef, 100) * 100
    var anio = siglo + aa
    // Un nacimiento no puede ser futuro; un vencimiento rara vez pasa de 50 años.
    if (pasado && anio > anioRef) anio -= 100
    if (!pasado && anio < anioRef - 50) anio += 100
    // Una fecha que no existe (31 de febrero, mes 13) queda vacía.
    val f = runCatching { LocalDate.of(anio, mm, dd) }.getOrNull() ?: return ""
    return "${f.year}-${f.monthValue.toString().padStart(2, '0')}-${f.dayOfMonth.toString().padStart(2, '0')}"
}

/** Dígitos de control de una MRZ TD1. Un dato con control que no cuadra queda vacío. */
data class ControlesMrz(
    val documento: Boolean = false,
    val nacimiento: Boolean = false,
    val vencimiento: Boolean = false,
    val general: Boolean = false
) {
    val todos: Boolean get() = documento && nacimiento && vencimiento && general
}

/**
 * Datos de la MRZ. `apellidos`: en el DNI peruano, solo el primero.
 * `nombres` sin tildes ni ñ (la norma solo admite A-Z): ver combinarMrzYAnverso.
 */
data class CamposMrz(
    val pais: String = "",
    val numeroDocumento: String = "",
    val fechaNacimiento: String = "",        // ISO, o ""
    val sexo: String = "",                   // "F" | "M" | ""
    val vencimiento: String = "",            // ISO, o ""
    val apellidos: List<String> = emptyList(),
    val nombres: String = ""
)

/**
 * Lectura de una MRZ TD1. `lineas`: las tres líneas ya corregidas, solo las
 * llena buscarMrzTd1; sirven para comparar dos lecturas seguidas (y para la
 * plantilla anónima). Solo en memoria: no salen en toString().
 */
data class LecturaMrz(
    val estructura: Boolean,
    val controles: ControlesMrz,
    val campos: CamposMrz,
    val lineas: List<String> = emptyList()
) {
    override fun toString() = "LecturaMrz(estructura=$estructura, controles=$controles, lineas=${lineas.size})"
}

private val LECTURA_VACIA = LecturaMrz(false, ControlesMrz(), CamposMrz())
private val LINEA_TD1 = Regex("^[A-Z0-9<]{30}$")
private val RELLENO_FINAL = Regex("<+$")

private fun partes(s: String) = s.split('<').filter { it.isNotEmpty() }

/**
 * Lee las tres líneas de una MRZ TD1 (3 × 30 caracteres) según ICAO 9303-5.
 * NUNCA lanza. Los campos con control (documento, fechas) solo valen si su
 * dígito de control coincide; los nombres no tienen dígito de control.
 *
 * @param lineas las tres líneas, ya limpias
 * @param fechaRef ISO; decide el siglo de las fechas
 */
fun leerMrzTd1(lineas: List<String?>?, fechaRef: String = hoyIso()): LecturaMrz {
    if (lineas == null || lineas.size != 3) return LECTURA_VACIA
    val (l1, l2, l3) = lineas.map { it ?: "" }
    if (!listOf(l1, l2, l3).all { LINEA_TD1.matches(it) } || l1[0] != 'I') return LECTURA_VACIA

    val numero = l1.substring(5, 14)
    val nacimiento = l2.substring(0, 6)
    val vence = l2.substring(8, 14)
    val controles = ControlesMrz(
        documento = controlOk(numero, l1[14]),
        nacimiento = controlOk(nacimiento, l2[6]),
        vencimiento = controlOk(vence, l2[14]),
        general = controlOk(l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29), l2[29])
    )
    // Entre apellido y nombres va «<<». El reconocimiento a veces se come un
    // «<» (medido en un DNI azul): sin ningún «<<» antes del relleno, el primer
    // bloque es el apellido y el resto son los nombres (el DNI peruano trae un
    // solo apellido en la MRZ). El anverso lo confirma o no.
    val nombre = l3.replace(RELLENO_FINAL, "")
    val trozos = if (nombre.contains("<<")) nombre.split("<<")
    else listOf(partes(nombre).firstOrNull() ?: "", partes(nombre).drop(1).joinToString("<"))
    val primario = trozos.first()
    val resto = trozos.drop(1)
    val sexo = if (l2[7] == 'M' || l2[7] == 'F') l2[7].toString() else ""
    return LecturaMrz(
        estructura = true,
        controles = controles,
        campos = CamposMrz(
            pais = l1.substring(2, 5).replace("<", ""),
            numeroDocumento = if (controles.documento) numero.replace("<", "") else "",
            fechaNacimiento = if (controles.nacimiento) fechaMrz(nacimiento, pasado = true, fechaRef = fechaRef) else "",
            sexo = sexo,
            vencimiento = if (controles.vencimiento) fechaMrz(vence, pasado = false, fechaRef = fechaRef) else "",
            apellidos = partes(primario),
            nombres = partes(resto.joinToString("<")).joinToString(" ")
        )
    )
}

// Lo que el reconocimiento de texto suele confundir en una MRZ. Solo se
// corrige donde la norma exige una cifra, y el dígito de control decide.
private val A_CIFRA = mapOf('O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', 'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6')
private val CIFRAS_TD1 = listOf(
    setOf(14),                                       // línea 1: el número puede tener letras
    setOf(0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 29),
    emptySet()
)
// Y al revés: donde la norma solo admite letras, una cifra es una letra mal
// leída (un apellido salía «QUISP0»). La línea 3 (apellidos y nombres) es
// toda letras; en las otras, el tipo, el país, el sexo y la nacionalidad.
private val A_LETRA = mapOf('0' to 'O', '1' to 'I', '2' to 'Z', '5' to 'S', '6' to 'G', '8' to 'B')
private val LETRAS_TD1: List<Set<Int>?> = listOf(setOf(0, 1, 2, 3, 4), setOf(7, 15, 16, 17), null)   // null: toda la línea
private fun soloLetras(n: Int, j: Int) = LETRAS_TD1[n]?.contains(j) ?: true

private val NO_MRZ = Regex("[^A-Z0-9<]")

private fun limpiarLineaMrz(texto: String): String =
    texto.uppercase().replace("«", "<<").replace(NO_MRZ, "")   // los espacios también se van

/**
 * Un pedazo de texto del reconocimiento (una `Text.Line` de ML Kit), con su
 * caja en píxeles de la imagen. `angulo`: inclinación en grados según
 * `cornerPoints`, negativa si sube hacia la derecha.
 */
data class FragmentoOcr(
    val texto: String,
    val arriba: Int,
    val abajo: Int,
    val izquierda: Int,
    val derecha: Int? = null,
    val angulo: Double? = null
) {
    // El texto puede ser la MRZ de alguien: no sale en un log.
    override fun toString() = "FragmentoOcr(${texto.length} caracteres, arriba=$arriba, izquierda=$izquierda)"
}

/**
 * Une en filas los pedazos que devolvió el reconocimiento de texto: los que
 * están a la misma altura, de izquierda a derecha. El reconocimiento suele
 * partir una línea de la MRZ donde hay muchos «<». Si el DNI está inclinado,
 * la altura se corrige con el ángulo de las líneas largas.
 * @return las filas, de arriba abajo
 */
fun unirFilasOcr(fragmentos: List<FragmentoOcr>?): List<String> {
    if (fragmentos == null) return emptyList()
    val angulos = fragmentos.filter { it.texto.length >= 10 && it.angulo != null && it.angulo.isFinite() }
        .map { it.angulo!! }.sorted()
    val pendiente = if (angulos.isNotEmpty()) tan(angulos[angulos.size / 2] * PI / 180) else 0.0

    class Puesto(val f: FragmentoOcr, val y: Double, val alto: Double)
    class Fila(val y: Double, val alto: Double, val partes: MutableList<FragmentoOcr>)

    // Altura «enderezada» del centro de cada pedazo.
    val puestos = fragmentos.map { f ->
        val cx = (f.izquierda + (f.derecha ?: f.izquierda)) / 2.0
        Puesto(f, (f.arriba + f.abajo) / 2.0 - pendiente * cx, max(1.0, (f.abajo - f.arriba).toDouble()))
    }.sortedBy { it.y }
    val filas = ArrayList<Fila>()
    for (p in puestos) {
        val fila = filas.firstOrNull { abs(it.y - p.y) < max(it.alto, p.alto) * 0.6 }
        if (fila != null) fila.partes.add(p.f)
        else filas.add(Fila(p.y, p.alto, mutableListOf(p.f)))
    }
    return filas.sortedBy { it.y }
        .map { x -> x.partes.sortedBy { it.izquierda }.joinToString("") { it.texto } }
}

// El reconocimiento se come «<» seguidos. Solo se reponen donde la norma pone
// relleno: al final de las líneas 1 y 3, y en la línea 2 entre la
// nacionalidad y el último dígito de control. Si se repuso mal, algún dígito
// de control deja de cuadrar y la lectura se descarta.
private fun ajustarLineaTd1(l: String, n: Int): String? {
    if (n == 1) {
        if (l.length == 30) return l
        if (l.length < 19) return null
        return l.substring(0, 18) + "<".repeat(11) + l.last()
    }
    if (l.length < (if (n == 0) 15 else 3)) return null
    return if (l.length >= 30) l.substring(0, 30) else l + "<".repeat(30 - l.length)
}

private fun conCifras(l: String, n: Int) =
    l.mapIndexed { j, c -> if (j in CIFRAS_TD1[n]) A_CIFRA[c] ?: c else c }.joinToString("")

/** El número de documento (posiciones 5 a 13 de la línea 1) leído como cifras. */
private fun numeroEnCifras(l: String) =
    l.mapIndexed { j, c -> if (j in 5..13) A_CIFRA[c] ?: c else c }.joinToString("")

/**
 * Busca una MRZ TD1 en el texto que devolvió el reconocimiento de texto, y
 * solo la da por buena si pasan TODOS sus dígitos de control. NUNCA lanza.
 * @param lineasOcr filas, de arriba abajo (ver unirFilasOcr)
 * @return la lectura, con `lineas` llenas; null si no hay una MRZ segura
 */
fun buscarMrzTd1(lineasOcr: List<String>?, fechaRef: String = hoyIso()): LecturaMrz? {
    if (lineasOcr == null) return null
    val limpias = lineasOcr.map(::limpiarLineaMrz).filter { it.length >= 3 }
    var i = 0
    while (i + 2 < limpias.size) {
        if (limpias[i].startsWith("I")) {
            val tres = limpias.subList(i, i + 3).mapIndexed { n, l ->
                // Primero las cifras (para que la línea 2 tenga su largo real), después el relleno.
                val ajustada = ajustarLineaTd1(conCifras(l, n), n) ?: return@mapIndexed null
                val cifras = if (n == 1) conCifras(ajustada, 1) else ajustada
                cifras.mapIndexed { j, c -> if (soloLetras(n, j)) A_LETRA[c] ?: c else c }.joinToString("")
            }
            if (tres.all { it != null }) {
                val r = leerMrzTd1(tres, fechaRef)
                // Las líneas van solo en memoria, para comparar dos lecturas seguidas.
                if (r.estructura && r.controles.todos) return r.copy(lineas = tres.map { it!! })
                // La norma deja letras en el número, pero el del DNI es de cifras: si
                // tal cual no cuadró, se prueba «O» por 0 (y las demás) en el número.
                val conNumero = listOf(numeroEnCifras(tres[0]!!), tres[1]!!, tres[2]!!)
                if (conNumero[0] != tres[0]) {
                    val r2 = leerMrzTd1(conNumero, fechaRef)
                    if (r2.estructura && r2.controles.todos) return r2.copy(lineas = conNumero)
                }
            }
        }
        i += 1
    }
    return null
}

// La MRZ no lleva tildes ni ñ (la norma solo admite A-Z), los nombres no
// tienen dígito de control y en el DNI peruano trae solo el primer apellido.
// El anverso sí trae los nombres como son. Se cruzan: una fila del anverso
// vale solo si, quitándole tildes y ñ, es IGUAL a lo de la MRZ. Si difiere en
// una letra que el reconocimiento suele confundir, no se elige: se ofrecen
// las dos y decide la persona.

private val MARCAS_COMBINANTES = Regex("[̀-ͯ]")
private val NO_A_Z = Regex("[^A-Z]+")
private val NO_LETRA_NI_ESPACIO = Regex("[^\\p{L} ]+")
private val ESPACIOS = Regex(" +")

/** Mayúsculas, sin tildes, ñ como N, solo letras y un espacio entre palabras. */
fun normalizarLetras(texto: String?): String =
    Normalizer.normalize(texto ?: "", Normalizer.Form.NFD)
        .replace(MARCAS_COMBINANTES, "")
        .uppercase().replace(NO_A_Z, " ").trim(' ')

/** La fila del anverso tal como está impresa: letras (con tildes y ñ) y espacios. */
private fun textoImpreso(texto: String?): String =
    Normalizer.normalize(texto ?: "", Normalizer.Form.NFC).uppercase()
        .replace(NO_LETRA_NI_ESPACIO, " ").replace(ESPACIOS, " ").trim(' ')

private val CONFUSIBLES = listOf("BS8E", "O0DQU", "I1LJT", "NMH", "CG", "VY", "KX", "PR", "FE")
private fun confusibles(a: Char, b: Char) = CONFUSIBLES.any { a in it && b in it }

/**
 * ¿Dos textos normalizados difieren solo en 1 o 2 letras que se suelen
 * confundir, o en UNA letra de más o de menos? El reconocimiento también
 * duplica o se come una letra (visto en DNI reales: «ALEJANDROO», «LUS» por LUIS).
 */
private fun casiIguales(a: String, b: String): Boolean {
    if (a == b) return false
    if (abs(a.length - b.length) == 1) {
        val (largo, corto) = if (a.length > b.length) a to b else b to a
        if (corto.length < 4) return false
        return largo.indices.any { i -> largo.removeRange(i, i + 1) == corto }
    }
    if (a.length != b.length) return false
    var distintas = 0
    for (i in a.indices) {
        if (a[i] == b[i]) continue
        if (!confusibles(a[i], b[i])) return false
        distintas += 1
    }
    return distintas <= 2
}

enum class EstadoCampo(val clave: String) {
    /** El anverso es igual a la MRZ: se usa como está impreso, con tildes y ñ. */
    CONFIRMADO("confirmado"),
    /** Difieren en una letra confundible (o una de más o de menos): hay que elegir entre `opciones`. */
    DUDOSO("dudoso"),
    /** Solo la MRZ, o el segundo apellido (que la MRZ no trae): hay que mirarlo. */
    SIN_CONFIRMAR("sin-confirmar"),
    NO_LEIDO("no-leido")
}

/** Un nombre cruzado. `opciones` solo en DUDOSO: [como está impreso, como vino en la MRZ]. */
data class CampoCruzado(val valor: String, val estado: EstadoCampo, val opciones: List<String> = emptyList())

data class LecturaAnverso(
    val apellidoPaterno: CampoCruzado,
    val apellidoMaterno: CampoCruzado,
    val nombres: CampoCruzado
)

private class FilaAnverso(val impreso: String, val normal: String)

private fun cruzar(valorMrz: String, filas: List<FilaAnverso>): CampoCruzado {
    if (valorMrz.isEmpty()) return CampoCruzado("", EstadoCampo.NO_LEIDO)
    val igual = filas.firstOrNull { it.normal == valorMrz }
    if (igual != null) return CampoCruzado(igual.impreso, EstadoCampo.CONFIRMADO)
    val parecidas = filas.filter { casiIguales(it.normal, valorMrz) }
    if (parecidas.size == 1) {
        return CampoCruzado("", EstadoCampo.DUDOSO, listOf(parecidas[0].impreso, valorMrz))
    }
    return CampoCruzado(valorMrz, EstadoCampo.SIN_CONFIRMAR)
}

// Rótulos del anverso que no son nombres. Se quitan palabra por palabra:
// «FECHA DE EMISIÓN», «ESTADO CIVIL», «SEXO», «UBIGEO»…
private val OTRAS_PALABRAS = setOf("FECHA", "EMISION", "CADUCIDAD", "NACIMIENTO", "VENCIMIENTO", "INSCRIPCION",
    "SEXO", "UBIGEO", "ESTADO", "CIVIL", "DNI", "REPUBLICA", "PERU", "DOCUMENTO", "NACIONAL", "IDENTIDAD")

private fun sinOtrosRotulos(impreso: String): FilaAnverso {
    val palabras = textoImpreso(impreso).split(' ').filter { it.isNotEmpty() }
    val quedan = palabras.filterIndexed { j, w ->
        val n = normalizarLetras(w)
        if (n in OTRAS_PALABRAS) return@filterIndexed false
        // «DE»/«DEL» entre dos palabras de un rótulo («FECHA DE EMISIÓN»).
        !((n == "DE" || n == "DEL") && normalizarLetras(palabras.getOrElse(j - 1) { "" }) in OTRAS_PALABRAS)
    }
    val impresoLimpio = quedan.joinToString(" ")
    return FilaAnverso(impresoLimpio, normalizarLetras(impresoLimpio))
}

// «APELL?IDO»: el reconocimiento a veces se come una L del rótulo («Primer Apelido»).
private val ROTULO = Regex("PRIMER ?APELL?IDO|SEGUNDO ?APELL?IDO|PRE ?NOMBRES|NOMBRES")
private val RE_PRIMER_APELLIDO = Regex("PRIMER ?APELL?IDO")
private val RE_SEGUNDO_APELLIDO = Regex("SEGUNDO ?APELL?IDO")
private val RE_PRENOMBRES = Regex("PRE ?NOMBRES")
private val HASTA_EL_ROTULO = Regex("^.*?(NOMBRES|APELL?IDO)\\s*")

/**
 * Cruza los nombres de la MRZ con las filas leídas del anverso. NUNCA lanza.
 * Ver EstadoCampo para el significado de cada estado.
 *
 * @param camposMrz de leerMrzTd1 / buscarMrzTd1 (solo usa apellidos y nombres)
 * @param filasAnverso de unirFilasOcr
 * @return null si el anverso no parece ser del mismo DNI (todavía no se leyó bien)
 */
fun combinarMrzYAnverso(camposMrz: CamposMrz?, filasAnverso: List<String>?): LecturaAnverso? {
    if (camposMrz == null || filasAnverso == null) return null
    val filas = filasAnverso.map { FilaAnverso(textoImpreso(it), normalizarLetras(it)) }
        .filter { it.normal.isNotEmpty() }
    var paterno = cruzar(normalizarLetras(camposMrz.apellidos.joinToString(" ")), filas)
    var nombres = cruzar(normalizarLetras(camposMrz.nombres), filas)

    // Por rótulo: el valor va en la misma fila, después del rótulo, o en la
    // siguiente que no sea otro rótulo. En el DNI azul, la columna de al lado
    // («Fecha de Emisión»…) cae casi a la misma altura y se mete entre el
    // rótulo y su valor: esos rótulos se quitan.
    fun porRotulo(re: Regex): FilaAnverso? {
        val i = filas.indexOfFirst { re.containsMatchIn(it.normal) }
        if (i < 0) return null
        val enLaFila = sinOtrosRotulos(filas[i].impreso.replaceFirst(HASTA_EL_ROTULO, ""))
        if (enLaFila.normal.isNotEmpty() && !ROTULO.containsMatchIn(enLaFila.normal)) return enLaFila
        for (f in filas.drop(i + 1)) {
            if (ROTULO.containsMatchIn(f.normal)) return null
            val limpia = sinOtrosRotulos(f.impreso)
            if (limpia.normal.isNotEmpty()) return limpia
        }
        return null
    }
    // Las letras de la MRZ sin ningún separador: un «<» perdido o de más no
    // cambia nada. Lo del rótulo vale si coincide ahí, letra por letra.
    // El reconocimiento también lee un «<» como «K» (visto en un DNI azul:
    // «AAAAAA<<» salió «AAAAAAA<»). Si no coincide tal cual, se compara sin las K.
    val letras = normalizarLetras((camposMrz.apellidos + camposMrz.nombres).joinToString(" ")).replace(" ", "")
    fun sinEspacios(f: FilaAnverso) = f.normal.replace(" ", "")
    fun sinK(t: String) = t.replace("K", "")
    fun empieza(v: String) = letras.startsWith(v) || sinK(letras).startsWith(sinK(v))
    fun termina(v: String) = letras.length > v.length && (letras.endsWith(v) || sinK(letras).endsWith(sinK(v)))
    val primero = porRotulo(RE_PRIMER_APELLIDO)
    if (paterno.estado != EstadoCampo.CONFIRMADO && primero != null &&
        sinK(sinEspacios(primero)).isNotEmpty() && empieza(sinEspacios(primero))) {
        paterno = CampoCruzado(primero.impreso, EstadoCampo.CONFIRMADO)
    }
    val pre = porRotulo(RE_PRENOMBRES)
    if (nombres.estado != EstadoCampo.CONFIRMADO && pre != null &&
        sinK(sinEspacios(pre)).isNotEmpty() && termina(sinEspacios(pre))) {
        nombres = CampoCruzado(pre.impreso, EstadoCampo.CONFIRMADO)
    }
    // Sin ninguna fila que coincida, no es este DNI o no se leyó todavía.
    val algoCoincide = listOf(paterno, nombres).any { it.estado == EstadoCampo.CONFIRMADO || it.estado == EstadoCampo.DUDOSO }
    if (!algoCoincide) return null

    // El segundo apellido solo está en el anverso.
    val segundo = porRotulo(RE_SEGUNDO_APELLIDO)
    val materno = if (segundo != null) CampoCruzado(segundo.impreso, EstadoCampo.SIN_CONFIRMAR)
    else CampoCruzado("", EstadoCampo.NO_LEIDO)
    return LecturaAnverso(apellidoPaterno = paterno, apellidoMaterno = materno, nombres = nombres)
}

data class CamposPdf417(
    val numeroDocumento: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val nombres: String,
    val sexo: String,
    val vencimiento: String
)

data class LecturaPdf417(val largo: Int, val campos: CamposPdf417)

// El «\s» de JavaScript, tal cual: el de Java no trae los espacios Unicode y
// trim() de Kotlin también quitaría los separadores \u001C-\u001F, que JS deja.
private val ESPACIOS_JS = Regex("[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")
private val OCHO_CIFRAS = Regex("^\\d{8}$")

/**
 * Campos del PDF417 del DNI azul según la distribución publicada por un
 * tercero (SIN VERIFICAR). Solo para el modo de prueba.
 * NUNCA lanza; no inventa un DNI (sin 8 cifras queda vacío).
 */
fun leerPdf417DniAzul(cadena: String?): LecturaPdf417 {
    val s = cadena ?: ""
    fun tramo(a: Int, b: Int) =
        s.substring(minOf(a, s.length), minOf(b, s.length)).replace(ESPACIOS_JS, " ").trim(' ')
    val numero = tramo(2, 10)
    val sexo = when (s.getOrNull(125)) { '1' -> "M"; '2' -> "F"; else -> "" }
    val v = tramo(448, 456)
    return LecturaPdf417(
        largo = s.length,
        campos = CamposPdf417(
            numeroDocumento = if (OCHO_CIFRAS.matches(numero)) numero else "",
            apellidoPaterno = tramo(10, 50),
            apellidoMaterno = tramo(50, 90),
            nombres = tramo(90, 125),
            sexo = sexo,
            vencimiento = if (OCHO_CIFRAS.matches(v)) "${v.substring(0, 4)}-${v.substring(4, 6)}-${v.substring(6, 8)}" else ""
        )
    )
}

/**
 * La forma de una cadena sin su contenido: cada letra pasa a «A», cada cifra
 * a «9», lo que no se puede imprimir (la huella del PDF417, por ejemplo) a
 * «·»; se conservan espacios, «<» y signos. Así se puede compartir cómo viene
 * un DNI sin compartir de quién es.
 */
fun plantillaAnonima(cadena: String?): String {
    val s = cadena ?: ""
    val r = StringBuilder()
    s.codePoints().forEach { c ->
        r.append(
            when {
                Character.isLetter(c) -> 'A'
                c in '0'.code..'9'.code -> '9'
                c == ' '.code || c == '<'.code || c in '!'.code..'/'.code || c in ':'.code..'@'.code ||
                    c in '['.code..'`'.code || c in '{'.code..'~'.code -> c.toChar()
                else -> '·'
            }
        )
    }
    return r.toString()
}

/**
 * Reconoce una ráfaga de teclas como escaneo de un lector HID (el lector se
 * comporta como teclado. El búfer vive solo en memoria.
 */
interface DetectorLectorHid {
    /**
     * @param tecla como `KeyboardEvent.key` en la web: "Enter" o un solo carácter
     * @return la cadena escaneada al llegar Enter tras una ráfaga larga; si no, null
     */
    fun procesarTecla(tecla: String?): String?
    fun reiniciar()
}

/** @param ahora reloj en milisegundos; se inyecta para probar sin esperar. */
fun detectorLectorHid(
    msEntreTeclas: Long = 35,
    minimoCaracteres: Int = 20,
    ahora: () -> Long = System::currentTimeMillis
): DetectorLectorHid = object : DetectorLectorHid {
    private var buffer = StringBuilder()
    private var ultima = 0L

    override fun procesarTecla(tecla: String?): String? {
        val t = ahora()
        if (t - ultima > msEntreTeclas) buffer = StringBuilder()
        ultima = t
        if (tecla == "Enter") {
            val cadena = buffer.toString()
            buffer = StringBuilder()
            return if (cadena.length >= minimoCaracteres) cadena else null
        }
        if (tecla != null && tecla.length == 1) buffer.append(tecla)
        return null
    }

    override fun reiniciar() {
        buffer = StringBuilder()
        ultima = 0
    }
}
