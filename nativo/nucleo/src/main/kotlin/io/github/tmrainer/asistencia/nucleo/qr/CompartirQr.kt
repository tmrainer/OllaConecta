// CompartirQr.kt — porte de web/js/compartir-qr.js: pasar el padrón, la
// asistencia de uno o varios días o las entregas de un teléfono a otro con
// códigos QR, sin internet ni servidor. Funciones puras: sin Android, sin red.
// Comprimir usa java.util.zip, del propio teléfono: nada sale de él.
//
// Quien recibe NUNCA guarda a ciegas: primero se compara lo
// recibido con lo que ya hay (compararPadron, compararAsistencia,
// compararEntregas) y la pantalla muestra qué se agrega, qué es igual y qué
// no coincide. Lo que no coincide queda como está salvo que la persona elija
// lo recibido; las aplicar…() solo hacen lo elegido y devuelven objetos
// nuevos.
//
// Formato de cada QR (modo alfanumérico, el más compacto con letras):
//   «P3» · tipo (P padrón, A asistencia, E entregas) · id del envío (4)
//   · parte (2 cifras) · total (2 cifras) · trozo · «:» de cierre
// El contenido es JSON comprimido (deflate sin envoltura zlib: el
// 'deflate-raw' de CompressionStream en la web, Deflater/Inflater con
// nowrap = true aquí) y escrito en base45 (RFC 9285), que usa justo las letras
// que el QR guarda en modo alfanumérico. El «:» del final protege los espacios
// del trozo si un lector recorta el texto. El id une las partes de un mismo
// envío: dos envíos no se mezclan. También se leen los QR de antes (solo
// cifras, ListaQr.kt).
//
// Los bytes comprimidos pueden no ser idénticos a los de la web (otra
// versión de zlib), pero los dos lados leen lo que arma el otro: lo prueba
// QrCruzadoTest en ambos sentidos.
package io.github.tmrainer.asistencia.nucleo.qr

import io.github.tmrainer.asistencia.nucleo.calculos.ConteoSinInscribir
import io.github.tmrainer.asistencia.nucleo.calculos.TRAMOS_ETARIOS
import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.calculos.asistenciasDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.sinInscribirDelDia
import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.modelo.Atencion
import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.Entrega
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.PreciosMenu
import io.github.tmrainer.asistencia.nucleo.modelo.SinInscribir
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaAtencion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaEntrega
import io.github.tmrainer.asistencia.nucleo.modelo.uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Collections
import java.util.IdentityHashMap
import java.util.zip.Deflater
import java.util.zip.Inflater

private const val ALFABETO = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

// Medido en un moto g23 (gama de entrada) leyendo otra pantalla: hasta la
// versión 24 (~1 700 car.) lee; la 27 ya no. 1 400 (versión ~22) deja margen
// para un celular en la mano: la asistencia de un día entra siempre en uno.
private const val POR_PARTE = 1400

// Si no entra en uno, va animado: cuadros más livianos, que una cámara modesta
// agarra al vuelo mientras cambian.
private const val POR_CUADRO = 900
private const val MAX_PARTES = 99

// Lo más que se acepta descomprimido. La web no pone límite; aquí un QR
// armado a propósito (deflate comprime hasta ~1 000 a 1) no debe llenar la
// memoria de un teléfono modesto. Un padrón de 300 personas son ~30 KB.
private const val MAX_JSON = 4 * 1024 * 1024

/** Qué lleva un envío. `letra` es la que va en el QR. */
enum class TipoEnvio(val clave: String, val letra: Char) {
    PADRON("padron", 'P'), ASISTENCIA("asistencia", 'A'), ENTREGAS("entregas", 'E');

    companion object {
        fun deLetra(letra: Char): TipoEnvio? = entries.firstOrNull { it.letra == letra }
    }
}

// ------------------------------------------------------------- paquetes
//
// Lo que viaja, ya revisado. En el QR va como JSON con claves cortas y filas
// en arreglos (mismo formato que la web, versión 1):
//   padrón      {"t":"P","v":1,"p":[[13 campos], …]}
//   asistencia  {"t":"A","v":1,"d":[[fecha, normal|null, ayuda|null, [[doc] | [doc,1], …], [fueraN, fueraA]?], …]}
//   entregas    {"t":"E","v":1,"c":[periodicidad, fechaBase, [días de aviso]],"e":[[fecha, estado, nota], …]}

sealed interface Paquete {
    val tipo: TipoEnvio
}

/** Una persona del padrón, tal como viaja (sin notas ni id). `activo`: 1 o 0. */
data class FilaPersonaQr(
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val nombres: String,
    val numeroDocumento: String,
    val tipoDocumento: String,
    val fechaNacimiento: String,
    val grupoEtarioManual: String,
    val sexo: String,
    val tipoAfiliado: String,
    val activo: Int,
    val altaEn: String,
    val bajaEn: String,
    val motivoBaja: String
) {
    internal fun campos(): List<Any> = listOf(
        apellidoPaterno, apellidoMaterno, nombres, numeroDocumento, tipoDocumento, fechaNacimiento,
        grupoEtarioManual, sexo, tipoAfiliado, activo, altaEn, bajaEn, motivoBaja
    )
}

data class PaquetePadron(val personas: List<FilaPersonaQr>) : Paquete {
    override val tipo get() = TipoEnvio.PADRON
}

/** Una marca de asistencia: el documento y si fue menú de ayuda social. */
data class MarcaQr(val documento: String, val ayudaSocial: Boolean = false)

/** Quienes comieron sin estar en el padrón, por menú. */
data class CuentaFuera(val normal: Int, val ayudaSocial: Int)

/** Un día. `fuera` solo si hubo alguien de fuera: así se lee igual en una versión anterior. */
data class DiaQr(
    val fecha: String,
    val precioNormal: Long?,
    val precioAyuda: Long?,
    val marcas: List<MarcaQr>,
    val fuera: CuentaFuera? = null
)

data class PaqueteAsistencia(val dias: List<DiaQr>) : Paquete {
    override val tipo get() = TipoEnvio.ASISTENCIA
}

data class ConfigEntregasQr(val periodicidad: String, val fechaLimiteBase: String, val diasDeAviso: List<Int>)

data class EntregaQr(val fechaLimite: String, val estado: String, val nota: String)

data class PaqueteEntregas(val config: ConfigEntregasQr, val entregas: List<EntregaQr>) : Paquete {
    override val tipo get() = TipoEnvio.ENTREGAS
}

// ------------------------------------------------------------------ base45

fun aBase45(bytes: ByteArray): String {
    val s = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        if (i + 1 < bytes.size) {
            val n = (bytes[i].toInt() and 255) * 256 + (bytes[i + 1].toInt() and 255)
            s.append(ALFABETO[n % 45]).append(ALFABETO[n / 45 % 45]).append(ALFABETO[n / 2025])
        } else {
            val b = bytes[i].toInt() and 255
            s.append(ALFABETO[b % 45]).append(ALFABETO[b / 45])
        }
        i += 2
    }
    return s.toString()
}

/** null si el texto no es base45 válido. */
fun deBase45(texto: String): ByteArray? {
    if (texto.length % 3 == 1) return null
    val salida = ByteArrayOutputStream()
    var i = 0
    while (i < texto.length) {
        val v = texto.substring(i, minOf(i + 3, texto.length)).map { ALFABETO.indexOf(it) }
        if (v.any { it < 0 }) return null
        if (v.size == 3) {
            val n = v[0] + v[1] * 45 + v[2] * 2025
            if (n > 65535) return null
            salida.write(n shr 8)
            salida.write(n and 255)
        } else {
            val n = v[0] + v[1] * 45
            if (n > 255) return null
            salida.write(n)
        }
        i += 3
    }
    return salida.toByteArray()
}

private fun comprimir(bytes: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    try {
        deflater.setInput(bytes)
        deflater.finish()
        val salida = ByteArrayOutputStream()
        val trozo = ByteArray(4096)
        while (!deflater.finished()) salida.write(trozo, 0, deflater.deflate(trozo))
        return salida.toByteArray()
    } finally {
        deflater.end()
    }
}

/** null si no es deflate válido, está cortado, sobra algo al final o pasa de MAX_JSON. */
private fun descomprimir(bytes: ByteArray): ByteArray? {
    val inflater = Inflater(true)
    try {
        // Con nowrap, zlib pide un byte de más al final (ver Inflater).
        inflater.setInput(bytes + 0)
        val salida = ByteArrayOutputStream()
        val trozo = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(trozo)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
            salida.write(trozo, 0, n)
            if (salida.size() > MAX_JSON) return null
        }
        return if (inflater.remaining <= 1) salida.toByteArray() else null
    } catch (e: java.util.zip.DataFormatException) {
        return null
    } finally {
        inflater.end()
    }
}

// ------------------------------------------------------- partes del envío

private val AZAR = SecureRandom()

private fun idCorto(): String {
    val v = ByteArray(4).also { AZAR.nextBytes(it) }
    return v.joinToString("") { ALFABETO[(it.toInt() and 255) % 36].toString() }
}

private fun dos(n: Int) = n.toString().padStart(2, '0')

/**
 * El texto de cada QR de un envío, para mostrarlos con matrizQr(). Si entra
 * en un QR legible (POR_PARTE), uno solo. Si no, cuadros PAREJOS de hasta
 * POR_CUADRO, que se muestran animados.
 * @param porParte máximo de caracteres por QR (pruebas)
 * @param id el id del envío; por defecto, 4 letras al azar (pruebas)
 * @return vacío si no cabe en 99 códigos
 */
fun codificarParaQr(paquete: Paquete, porParte: Int = POR_PARTE, id: String = idCorto()): List<String> {
    val json = textoJson(paquete).toByteArray(Charsets.UTF_8)
    var texto = "J" + aBase45(json)
    val z = comprimir(json)
    if (z.size < json.size) texto = "Z" + aBase45(z)
    val porCuadro = minOf(porParte, POR_CUADRO)
    val total = if (texto.length <= porParte) 1 else (texto.length + porCuadro - 1) / porCuadro
    if (total > MAX_PARTES) return emptyList()
    val largo = (texto.length + total - 1) / total
    return List(total) { i ->
        "P3" + paquete.tipo.letra + id + dos(i + 1) + dos(total) +
            texto.substring(minOf(i * largo, texto.length), minOf((i + 1) * largo, texto.length)) + ":"
    }
}

/**
 * Una parte leída. `trozo` en las de ahora; `viejo` en las del QR de antes
 * (solo cifras), que siempre son de asistencia.
 */
data class ParteQr(
    val tipo: TipoEnvio,
    val id: String,
    val parte: Int,
    val total: Int,
    val trozo: String? = null,
    val viejo: ParteLista? = null
)

private val PARTE_P3 = Regex("P3([PAE])([0-9A-Z]{4})(\\d{2})(\\d{2})([0-9A-Z \$%*+\\-./:]*):")

/** Lee un QR. NUNCA lanza: si no es de esta app, null. */
fun leerParteQr(texto: String?): ParteQr? {
    if (texto == null) return null
    val viejo = decodificarParteQr(texto)
    if (viejo != null) {
        return ParteQr(TipoEnvio.ASISTENCIA, "81${viejo.fecha}${viejo.total}", viejo.parte, viejo.total, viejo = viejo)
    }
    val m = PARTE_P3.matchEntire(texto) ?: return null
    val (letra, id, p, t, trozo) = m.destructured
    val parte = p.toInt()
    val total = t.toInt()
    if (parte == 0 || total == 0 || parte > total) return null
    return ParteQr(TipoEnvio.deLetra(letra[0])!!, letra + id, parte, total, trozo = trozo)
}

/**
 * Lo que el lector lleva juntado (el bucle de lectura de camara-qr.js): una
 * parte de otro envío empieza de nuevo con ese; una repetida no cambia nada.
 * Cuando `completas`, se llama a juntarPartesQr(partes.values); si da null
 * («no se leyó bien»), se vuelve a empezar con PartesRecibidas().
 */
data class PartesRecibidas(val id: String? = null, val total: Int = 0, val partes: Map<Int, ParteQr> = emptyMap()) {
    val completas: Boolean get() = total > 0 && partes.size == total

    /** Suma un texto leído por la cámara. Si no es de esta app, queda igual. */
    fun sumar(texto: String?): PartesRecibidas {
        val p = leerParteQr(texto) ?: return this
        val base = if (p.id != id || p.total != total) PartesRecibidas(p.id, p.total) else this
        return if (p.parte in base.partes) base else base.copy(partes = base.partes + (p.parte to p))
    }
}

/**
 * Junta todas las partes de un envío y devuelve el paquete, ya revisado.
 * @param partes resultados de leerParteQr, todas del mismo id, en cualquier orden
 * @return null si falta algo o no tiene la forma esperada
 */
fun juntarPartesQr(partes: Collection<ParteQr>): Paquete? {
    try {
        val orden = partes.sortedBy { it.parte }
        if (orden.isEmpty() || orden.size != orden[0].total || orden.withIndex().any { (i, p) -> p.parte != i + 1 }) return null
        if (orden[0].viejo != null) {
            val marcas = orden.flatMap { it.viejo?.marcas ?: return null }
                .map { MarcaQr(it.dni, it.menu == "ayuda_social") }
            return PaqueteAsistencia(listOf(DiaQr(orden[0].viejo!!.fecha, null, null, marcas)))
        }
        val texto = orden.joinToString("") { it.trozo ?: "" }
        if (texto.isEmpty()) return null
        val bytes = deBase45(texto.substring(1)) ?: return null
        val json = when (texto[0]) {
            'Z' -> descomprimir(bytes) ?: return null
            'J' -> bytes
            else -> return null
        }
        return revisarPaquete(orden[0].tipo, Json.parseToJsonElement(String(json, Charsets.UTF_8)))
    } catch (e: Exception) {
        return null
    }
}

// ----------------------------------------------- el JSON que va en el QR

/** El JSON del paquete, igual al JSON.stringify() de la web. */
internal fun textoJson(paquete: Paquete): String = buildJsonObject {
    put("t", paquete.tipo.letra.toString())
    put("v", 1)
    when (paquete) {
        is PaquetePadron -> putJsonArray("p") {
            for (f in paquete.personas) addJsonArray {
                for (x in f.campos()) if (x is Int) add(x) else add(x as String)
            }
        }
        is PaqueteAsistencia -> putJsonArray("d") {
            for (dia in paquete.dias) addJsonArray {
                add(dia.fecha)
                add(dia.precioNormal)
                add(dia.precioAyuda)
                addJsonArray {
                    for (m in dia.marcas) addJsonArray {
                        add(m.documento)
                        if (m.ayudaSocial) add(1)
                    }
                }
                dia.fuera?.let { addJsonArray { add(it.normal); add(it.ayudaSocial) } }
            }
        }
        is PaqueteEntregas -> {
            putJsonArray("c") {
                add(paquete.config.periodicidad)
                add(paquete.config.fechaLimiteBase)
                addJsonArray { for (n in paquete.config.diasDeAviso) add(n) }
            }
            putJsonArray("e") {
                for (e in paquete.entregas) addJsonArray { add(e.fechaLimite); add(e.estado); add(e.nota) }
            }
        }
    }
}.toString()

// ------------------------------------------------- qué lleva cada envío

private val FECHA = Regex("\\d{4}-\\d{2}-\\d{2}")
private fun esFecha(s: String?) = s != null && FECHA.matches(s)
private fun docDe(a: Afiliado?) = (a?.numeroDocumento ?: "").trim().uppercase()

/** Un texto de hasta 200 caracteres (esTexto); si no, null. */
private fun JsonElement?.texto(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString && it.content.length <= 200 }?.content

/** Un número entero, como Number.isInteger() de lo que dio JSON.parse; si no, null. */
private fun JsonElement?.entero(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString) return null
    return p.content.toLongOrNull() ?: p.content.toDoubleOrNull()?.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toLong()
}

private fun JsonElement?.arreglo(): JsonArray? = this as? JsonArray

/** Lo que vino por QR, con la forma esperada; si no, null. Nunca se guarda algo raro. */
private fun revisarPaquete(tipo: TipoEnvio, p: JsonElement): Paquete? {
    if (p !is JsonObject || p["v"].entero() != 1L || p["t"].texto() != tipo.letra.toString()) return null
    return when (tipo) {
        TipoEnvio.PADRON -> {
            val filas = p["p"].arreglo() ?: return null
            PaquetePadron(filas.map { f ->
                val c = f.arreglo()?.takeIf { it.size == 13 } ?: return null
                val activo = c[9].entero()?.takeIf { it == 0L || it == 1L } ?: return null
                val t = (0 until 13).map { i -> if (i == 9) "" else c[i].texto() ?: return null }
                FilaPersonaQr(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7], t[8], activo.toInt(), t[10], t[11], t[12])
            })
        }
        TipoEnvio.ASISTENCIA -> {
            val dias = p["d"].arreglo() ?: return null
            PaqueteAsistencia(dias.map { d ->
                val c = d.arreglo()?.takeIf { it.size == 4 || it.size == 5 } ?: return null
                val fecha = c[0].texto()?.takeIf { esFecha(it) } ?: return null
                val normal = if (c[1] is JsonNull) null else c[1].entero()?.takeIf { it >= 0 } ?: return null
                val ayuda = if (c[2] is JsonNull) null else c[2].entero()?.takeIf { it >= 0 } ?: return null
                val marcas = c[3].arreglo()?.map { m ->
                    val x = m.arreglo() ?: return null
                    val doc = x.getOrNull(0).texto()?.takeIf { it.isNotEmpty() } ?: return null
                    when {
                        x.size == 1 -> MarcaQr(doc)
                        x.size == 2 && x[1].entero() == 1L -> MarcaQr(doc, true)
                        else -> return null
                    }
                } ?: return null
                val fuera = if (c.size == 5) {
                    val cuenta = c[4].arreglo()?.takeIf { it.size == 2 } ?: return null
                    val n = cuenta.map { e -> e.entero()?.takeIf { it in 0..9999 } ?: return null }
                    CuentaFuera(n[0].toInt(), n[1].toInt())
                } else null
                DiaQr(fecha, normal, ayuda, marcas, fuera)
            })
        }
        TipoEnvio.ENTREGAS -> {
            val c = p["c"].arreglo()?.takeIf { it.size == 3 } ?: return null
            val periodicidad = c[0].texto() ?: return null
            val base = c[1].texto()?.takeIf { it == "" || esFecha(it) } ?: return null
            val avisos = c[2].arreglo()?.map { e -> e.entero()?.takeIf { it in 0..399 }?.toInt() ?: return null } ?: return null
            val entregas = p["e"].arreglo()?.map { e ->
                val x = e.arreglo()?.takeIf { it.size == 3 } ?: return null
                EntregaQr(
                    x[0].texto()?.takeIf { esFecha(it) } ?: return null,
                    x[1].texto() ?: return null,
                    x[2].texto() ?: return null
                )
            } ?: return null
            PaqueteEntregas(ConfigEntregasQr(periodicidad, base, avisos), entregas)
        }
    }
}

/** Todas las personas del padrón, con lo necesario para inscribirlas en otro teléfono (sin notas). */
fun paqueteDelPadron(padron: Padron): PaquetePadron = PaquetePadron(padron.afiliados.map { a ->
    fun t(s: String) = s.take(200)
    FilaPersonaQr(
        t(a.apellidoPaterno), t(a.apellidoMaterno), t(a.nombres), t(docDe(a)), t(a.tipoDocumento.ifEmpty { "dni" }),
        t(a.fechaNacimiento), t(a.grupoEtarioManual ?: ""), t(a.sexo), t(a.tipoAfiliado),
        if (a.activo) 1 else 0, t(a.altaEn), t(a.bajaEn), t(a.motivoBaja)
    )
})

// Lo que no es un valor de la app se descarta, no se guarda tal cual: un tipo
// desconocido queda vacío y la persona aparece en «Les faltan datos».
private fun unoDe(v: String, permitidos: List<String>, si: String): String = if (v in permitidos) v else si

private fun fechaO(v: String) = if (esFecha(v)) v else ""

private val PERMITIDOS = mapOf(
    "tipoDocumento" to listOf("dni", "ce"),
    "sexo" to listOf("F", "M"),
    "tipoAfiliado" to listOf("habitual", "caso_social"),
    "grupoEtarioManual" to TRAMOS_ETARIOS.map { it.clave }
)

/** La persona que viene en una fila. Sin id: aplicarPadron() le da uno al agregarla. */
private fun personaDeFila(f: FilaPersonaQr) = Afiliado(
    apellidoPaterno = f.apellidoPaterno, apellidoMaterno = f.apellidoMaterno, nombres = f.nombres,
    numeroDocumento = f.numeroDocumento,
    tipoDocumento = unoDe(f.tipoDocumento, PERMITIDOS.getValue("tipoDocumento"), "dni"),
    fechaNacimiento = fechaO(f.fechaNacimiento),
    grupoEtarioManual = f.grupoEtarioManual.takeIf { it in PERMITIDOS.getValue("grupoEtarioManual") },
    sexo = unoDe(f.sexo, PERMITIDOS.getValue("sexo"), ""),
    tipoAfiliado = unoDe(f.tipoAfiliado, PERMITIDOS.getValue("tipoAfiliado"), ""),
    activo = f.activo == 1, altaEn = fechaO(f.altaEn), bajaEn = fechaO(f.bajaEn), motivoBaja = f.motivoBaja
)

/** paqueteDeAsistencia(): el paquete y cuántas marcas van y cuántas no (sin documento). */
data class AsistenciaParaEnviar(val paquete: PaqueteAsistencia, val personas: Int, val sinDocumento: Int)

/**
 * La asistencia de los días pedidos. Cada persona va por su documento: los
 * dos teléfonos pueden tener el padrón cargado por separado.
 */
fun paqueteDeAsistencia(padron: Padron, fechas: List<String>): AsistenciaParaEnviar {
    val porId = padron.afiliados.associateBy { it.id }
    var personas = 0
    var sinDocumento = 0
    val d = ArrayList<DiaQr>()
    for (fecha in fechas) {
        val at = padron.atenciones.firstOrNull { it.fecha == fecha } ?: continue
        val marcas = ArrayList<MarcaQr>()
        for (x in asistenciasDelDia(at)) {
            val doc = docDe(porId[x.afiliadoId])
            if (doc.isEmpty()) {
                sinDocumento += 1
                continue
            }
            marcas.add(MarcaQr(doc, x.tipoMenu == "ayuda_social"))
        }
        personas += marcas.size
        // Quienes comieron sin estar en el padrón, por menú. Solo si hay: así un
        // día sin ellos se lee igual en un teléfono con la versión anterior.
        val fuera = sinInscribirDelDia(at)
        d.add(DiaQr(fecha, at.precioMenuNormalCent, at.precioMenuAyudaSocialCent, marcas,
            if (fuera.total > 0) CuentaFuera(fuera.normal, fuera.ayudaSocial) else null))
    }
    return AsistenciaParaEnviar(PaqueteAsistencia(d), personas, sinDocumento)
}

/** Cada cuánto se entrega, la fecha base, los avisos y lo anotado de cada entrega. */
fun paqueteDeEntregas(calendario: Calendario): PaqueteEntregas = PaqueteEntregas(
    ConfigEntregasQr(calendario.periodicidad, if (esFecha(calendario.fechaLimiteBase)) calendario.fechaLimiteBase else "",
        calendario.diasDeAviso),
    calendario.entregas.filter { esFecha(it.fechaLimite) }
        .map { EntregaQr(it.fechaLimite, it.estado.ifEmpty { "pendiente" }, it.nota.take(200)) }
)

// --------------------------------------------------------------- padrón

/** Los campos que se comparan, con su nombre para la pantalla. */
val CAMPOS_PERSONA = listOf(
    "apellidoPaterno" to "Apellido paterno", "apellidoMaterno" to "Apellido materno", "nombres" to "Nombres",
    "tipoDocumento" to "Tipo de documento", "fechaNacimiento" to "Fecha de nacimiento",
    "grupoEtarioManual" to "Grupo de edad", "sexo" to "Sexo", "tipoAfiliado" to "Tipo",
    "activo" to "Activa en la olla", "bajaEn" to "Fecha de baja", "motivoBaja" to "Motivo de baja"
)

/** El valor tal cual (p[campo] en la web): String, String? (grupoEtarioManual) o Boolean (activo). */
private fun valorCrudo(p: Afiliado, campo: String): Any? = when (campo) {
    "apellidoPaterno" -> p.apellidoPaterno
    "apellidoMaterno" -> p.apellidoMaterno
    "nombres" -> p.nombres
    "tipoDocumento" -> p.tipoDocumento
    "fechaNacimiento" -> p.fechaNacimiento
    "grupoEtarioManual" -> p.grupoEtarioManual
    "sexo" -> p.sexo
    "tipoAfiliado" -> p.tipoAfiliado
    "activo" -> p.activo
    "bajaEn" -> p.bajaEn
    "motivoBaja" -> p.motivoBaja
    else -> error("Campo desconocido: $campo")
}

/** El mismo campo, tomado de `de`. */
private fun conCampo(a: Afiliado, campo: String, de: Afiliado): Afiliado = when (campo) {
    "apellidoPaterno" -> a.copy(apellidoPaterno = de.apellidoPaterno)
    "apellidoMaterno" -> a.copy(apellidoMaterno = de.apellidoMaterno)
    "nombres" -> a.copy(nombres = de.nombres)
    "tipoDocumento" -> a.copy(tipoDocumento = de.tipoDocumento)
    "fechaNacimiento" -> a.copy(fechaNacimiento = de.fechaNacimiento)
    "grupoEtarioManual" -> a.copy(grupoEtarioManual = de.grupoEtarioManual)
    "sexo" -> a.copy(sexo = de.sexo)
    "tipoAfiliado" -> a.copy(tipoAfiliado = de.tipoAfiliado)
    "activo" -> a.copy(activo = de.activo)
    "bajaEn" -> a.copy(bajaEn = de.bajaEn)
    "motivoBaja" -> a.copy(motivoBaja = de.motivoBaja)
    else -> error("Campo desconocido: $campo")
}

private fun valorCampo(p: Afiliado, k: String): Any =
    if (k == "activo") p.activo else (valorCrudo(p, k) as String? ?: "").trim()

private fun vacio(v: Any?) = v == null || v == ""

// Lo de aquí que no es un valor de la app cuenta como vacío: se propone lo recibido.
private fun vacioAqui(p: Afiliado, k: String): Boolean =
    vacio(valorCampo(p, k)) || PERMITIDOS[k]?.let { valorCrudo(p, k) !in it } == true

private fun claveNombre(p: Afiliado) = normalizarNombre(nombreCompleto(p))

/** No está su documento aquí, pero sí alguien con el mismo nombre y otro documento (¿un DNI mal escrito?). */
data class PersonaDudosa(val recibida: Afiliado, val mia: Afiliado)

/** Un campo que no coincide. `mio` y `recibido` son String, String? (grupo de edad) o Boolean (activa). */
data class CampoDistinto(val campo: String, val etiqueta: String, val mio: Any?, val recibido: Any?)

/**
 * Misma persona (mismo documento, o mismo nombre si no trae documento) con
 * algún dato distinto. nombreDistinto: mismo documento y otro nombre (¿dos
 * personas cruzadas?). soloCompleta: solo llena lo que aquí estaba vacío.
 */
data class PersonaDistinta(
    val mia: Afiliado,
    val recibida: Afiliado,
    val campos: List<CampoDistinto>,
    val nombreDistinto: Boolean,
    val soloCompleta: Boolean
)

data class ComparacionPadron(
    val nuevas: List<Afiliado>,
    val dudosas: List<PersonaDudosa>,
    val distintas: List<PersonaDistinta>,
    val iguales: Int,
    val soloAqui: Int
)

/**
 * Compara el padrón recibido con el de este teléfono, persona por persona,
 * por el número de documento. `mia` en dudosas y distintas es la MISMA
 * instancia que está en `padron.afiliados` (aplicarPadron la busca así, como
 * la web): se aplica sobre el padrón que se comparó.
 */
fun compararPadron(padron: Padron, paquete: PaquetePadron): ComparacionPadron {
    val mias = padron.afiliados
    val porDoc = HashMap<String, Afiliado>()
    val porNombre = HashMap<String, Afiliado>()
    for (a in mias) {
        if (docDe(a).isNotEmpty()) porDoc.putIfAbsent(docDe(a), a)
        porNombre.putIfAbsent(claveNombre(a), a)
    }
    val nuevas = ArrayList<Afiliado>()
    val dudosas = ArrayList<PersonaDudosa>()
    val distintas = ArrayList<PersonaDistinta>()
    var iguales = 0
    val tocadas = Collections.newSetFromMap(IdentityHashMap<Afiliado, Boolean>())
    val vistas = HashSet<String>()
    for (fila in paquete.personas) {
        val recibida = personaDeFila(fila)
        val doc = docDe(recibida)
        val clave = doc.ifEmpty { "nombre:${claveNombre(recibida)}" }
        if (!vistas.add(clave)) continue
        val mia = if (doc.isNotEmpty()) porDoc[doc] else porNombre[claveNombre(recibida)]
        if (mia == null) {
            val tocaya = porNombre[claveNombre(recibida)]
            if (doc.isNotEmpty() && tocaya != null && docDe(tocaya).isNotEmpty() && docDe(tocaya) != doc) {
                dudosas.add(PersonaDudosa(recibida, tocaya))
            } else {
                nuevas.add(recibida)
            }
            continue
        }
        tocadas.add(mia)
        val campos = CAMPOS_PERSONA
            .filter { (k) -> !(vacio(valorCampo(recibida, k)) && k != "activo") && valorCampo(mia, k) != valorCampo(recibida, k) }
            .map { (campo, etiqueta) -> CampoDistinto(campo, etiqueta, valorCrudo(mia, campo), valorCrudo(recibida, campo)) }
        if (campos.isEmpty()) {
            iguales += 1
            continue
        }
        distintas.add(PersonaDistinta(
            mia, recibida, campos,
            nombreDistinto = claveNombre(mia) != claveNombre(recibida),
            soloCompleta = campos.all { it.campo != "activo" && vacioAqui(mia, it.campo) }
        ))
    }
    return ComparacionPadron(nuevas, dudosas, distintas, iguales, mias.count { it !in tocadas })
}

/**
 * Lo elegido: agregar, personas de nuevas o dudosas; usarRecibida, entradas
 * de distintas (se toman TODOS sus campos recibidos).
 */
data class EleccionPadron(val agregar: List<Afiliado> = emptyList(), val usarRecibida: List<PersonaDistinta> = emptyList())

/** Aplica lo elegido. No cambia `padron`: devuelve uno nuevo. */
fun aplicarPadron(padron: Padron, eleccion: EleccionPadron, hoy: String = hoyIso()): Padron {
    val cambios = IdentityHashMap<Afiliado, PersonaDistinta>()
    for (d in eleccion.usarRecibida) cambios[d.mia] = d
    val afiliados = padron.afiliados.map { a ->
        val d = cambios[a] ?: return@map a
        d.campos.fold(a) { nuevo, c -> conCampo(nuevo, c.campo, d.recibida) }
    }.toMutableList()
    for (p in eleccion.agregar) {
        afiliados.add(p.copy(id = uuid(), altaEn = p.altaEn.ifEmpty { hoy }, origenDato = "qr"))
    }
    return padron.copy(afiliados = afiliados)
}

// ----------------------------------------------------------- asistencia

data class PorAgregar(val afiliado: Afiliado, val tipoMenu: String)

data class MenuDistinto(val afiliado: Afiliado, val mio: String, val recibido: String)

data class PreciosDia(val normal: Long?, val ayuda: Long?)

data class PreciosDistintos(val mio: PreciosDia, val recibido: PreciosDia)

data class FueraDistinto(val mio: ConteoSinInscribir, val recibido: ConteoSinInscribir)

/**
 * Un día recibido frente al de aquí. fuera: los que comieron sin estar en
 * el padrón, según el otro teléfono. No se suman a los de aquí: pueden ser
 * las mismas personas contadas dos veces.
 */
data class ComparacionDia(
    val fecha: String,
    val existe: Boolean,
    val agregar: List<PorAgregar>,
    val menuDistinto: List<MenuDistinto>,
    val yaEstaban: Int,
    val desconocidos: Int,
    val inactivos: Int,
    val soloAqui: Int,
    val precios: PreciosDia,
    val preciosDistintos: PreciosDistintos?,
    val fuera: ConteoSinInscribir,
    val fueraDistinto: FueraDistinto?
)

/**
 * Compara día por día. Solo se pueden sumar personas que están en este
 * padrón (por su documento) y activas ese día.
 */
fun compararAsistencia(padron: Padron, paquete: PaqueteAsistencia): List<ComparacionDia> {
    val porDoc = HashMap<String, Afiliado>()
    for (a in padron.afiliados) if (docDe(a).isNotEmpty()) porDoc.putIfAbsent(docDe(a), a)
    return paquete.dias.map { d ->
        val at = padron.atenciones.firstOrNull { it.fecha == d.fecha }
        val activos = afiliadosActivos(padron, d.fecha).map { it.id }.toSet()
        val ya = asistenciasDelDia(at).associate { it.afiliadoId to it.tipoMenu }
        val fuera = sinInscribirDelDia(Atencion(sinInscribir = d.fuera?.let { SinInscribir(it.normal, it.ayudaSocial) }))
        val agregar = ArrayList<PorAgregar>()
        val menuDistinto = ArrayList<MenuDistinto>()
        var yaEstaban = 0
        var desconocidos = 0
        var inactivos = 0
        val vistos = HashSet<String>()
        val coinciden = HashSet<String>()
        for (m in d.marcas) {
            val clave = m.documento.trim().uppercase()
            if (!vistos.add(clave)) continue
            val tipoMenu = if (m.ayudaSocial) "ayuda_social" else "normal"
            val p = porDoc[clave]
            when {
                p == null -> desconocidos += 1
                p.id in ya -> {
                    coinciden.add(p.id)
                    if (ya[p.id] == tipoMenu) yaEstaban += 1
                    else menuDistinto.add(MenuDistinto(p, ya.getValue(p.id), tipoMenu))
                }
                p.id !in activos -> inactivos += 1
                else -> agregar.add(PorAgregar(p, tipoMenu))
            }
        }
        var preciosDistintos: PreciosDistintos? = null
        if (at != null && (d.precioNormal != null || d.precioAyuda != null)) {
            val mio = PreciosDia(at.precioMenuNormalCent, at.precioMenuAyudaSocialCent)
            if ((d.precioNormal != null && d.precioNormal != mio.normal) || (d.precioAyuda != null && d.precioAyuda != mio.ayuda)) {
                preciosDistintos = PreciosDistintos(mio, PreciosDia(d.precioNormal, d.precioAyuda))
            }
        }
        val mioFuera = sinInscribirDelDia(at)
        val fueraDistinto = if (at != null && (mioFuera.normal != fuera.normal || mioFuera.ayudaSocial != fuera.ayudaSocial) &&
            fuera.total > 0) FueraDistinto(mioFuera, fuera) else null
        ComparacionDia(
            fecha = d.fecha, existe = at != null, agregar = agregar, menuDistinto = menuDistinto,
            yaEstaban = yaEstaban, desconocidos = desconocidos, inactivos = inactivos,
            soloAqui = ya.keys.count { it !in coinciden },
            precios = PreciosDia(d.precioNormal, d.precioAyuda), preciosDistintos = preciosDistintos,
            fuera = fuera, fueraDistinto = fueraDistinto
        )
    }
}

/**
 * Lo elegido. dias: fechas a sumar; menuRecibido: «fecha|afiliadoId» donde se
 * usa el menú recibido; preciosRecibidos: fechas donde se usan los precios
 * recibidos; fueraRecibido: fechas donde se usa la cuenta recibida de quienes
 * no están en el padrón.
 */
data class EleccionAsistencia(
    val dias: Set<String>,
    val menuRecibido: Set<String> = emptySet(),
    val preciosRecibidos: Set<String> = emptySet(),
    val fueraRecibido: Set<String> = emptySet()
)

/**
 * Aplica lo elegido. No cambia `padron`: devuelve uno nuevo.
 * @param preciosPorDefecto para un día que aquí no existía y llega sin precio
 */
fun aplicarAsistencia(
    padron: Padron,
    comparacion: List<ComparacionDia>,
    eleccion: EleccionAsistencia,
    preciosPorDefecto: PreciosMenu?
): Padron {
    val atenciones = padron.atenciones.toMutableList()
    for (dia in comparacion) {
        if (dia.fecha !in eleccion.dias) continue
        val i = atenciones.indexOfFirst { it.fecha == dia.fecha }
        var at = if (i >= 0) {
            atenciones[i]
        } else {
            val recibidos = dia.precios.normal != null || dia.precios.ayuda != null
            // Como la web: un día nuevo con precios recibidos queda con
            // precioTomadoDeConfig = true (nuevaAtencion con precios).
            nuevaAtencion(if (recibidos) PreciosMenu(dia.precios.normal, dia.precios.ayuda) else preciosPorDefecto)
                .copy(fecha = dia.fecha)
        }
        val cambiaMenu = dia.menuDistinto.filter { "${dia.fecha}|${it.afiliado.id}" in eleccion.menuRecibido }
        val tocaPrecios = dia.preciosDistintos != null && dia.fecha in eleccion.preciosRecibidos
        // Día nuevo aquí: va la cuenta recibida. Día que ya estaba: solo si se eligió.
        val tocaFuera = dia.fuera.total > 0 && (i < 0 || (dia.fueraDistinto != null && dia.fecha in eleccion.fueraRecibido))
        if (i >= 0 && dia.agregar.isEmpty() && cambiaMenu.isEmpty() && !tocaPrecios && !tocaFuera) continue
        val asistencias = asistenciasDelDia(at).toMutableList()
        for (m in cambiaMenu) {
            val j = asistencias.indexOfFirst { it.afiliadoId == m.afiliado.id }
            if (j >= 0) asistencias[j] = asistencias[j].copy(tipoMenu = m.recibido)
        }
        for (a in dia.agregar) asistencias.add(Asistencia(a.afiliado.id, a.tipoMenu))
        at = at.copy(asistencias = asistencias)
        if (tocaPrecios) {
            at = at.copy(
                precioMenuNormalCent = dia.precios.normal ?: at.precioMenuNormalCent,
                precioMenuAyudaSocialCent = dia.precios.ayuda ?: at.precioMenuAyudaSocialCent,
                precioTomadoDeConfig = false
            )
        }
        if (tocaFuera) at = at.copy(sinInscribir = SinInscribir(dia.fuera.normal, dia.fuera.ayudaSocial))
        at = at.copy(legado = null)
        if (i >= 0) atenciones[i] = at else atenciones.add(at)
    }
    return padron.copy(atenciones = atenciones)
}

// ------------------------------------------------------------- entregas

private val CAMPOS_ENTREGAS = listOf(
    "periodicidad" to "Cada cuánto se entrega", "fechaLimiteBase" to "Una fecha de entrega", "diasDeAviso" to "Días de aviso"
)

/** Un dato de la configuración que no coincide: String, o List<Int> en diasDeAviso. */
data class CampoEntregas(val campo: String, val etiqueta: String, val mio: Any?, val recibido: Any?)

data class EstadoNota(val estado: String, val nota: String)

data class EntregaDistinta(val fechaLimite: String, val mio: Entrega, val recibido: EstadoNota)

data class ComparacionEntregas(
    val config: List<CampoEntregas>,
    val nuevas: List<EntregaQr>,
    val distintas: List<EntregaDistinta>,
    val iguales: Int
)

private fun configDe(c: Calendario, campo: String): Any = when (campo) {
    "periodicidad" -> c.periodicidad
    "fechaLimiteBase" -> c.fechaLimiteBase
    else -> c.diasDeAviso
}

fun compararEntregas(calendario: Calendario, paquete: PaqueteEntregas): ComparacionEntregas {
    // Una periodicidad desconocida no se ofrece; un estado desconocido llega como pendiente.
    val recibido = mapOf(
        "periodicidad" to unoDe(paquete.config.periodicidad, listOf("semanal", "quincenal", "mensual"), ""),
        "fechaLimiteBase" to paquete.config.fechaLimiteBase,
        "diasDeAviso" to paquete.config.diasDeAviso
    )
    fun texto(v: Any?): String = if (v is List<*>) v.joinToString(",") else v?.toString() ?: ""
    val config = CAMPOS_ENTREGAS
        .filter { (k) -> texto(recibido[k]) != "" && texto(configDe(calendario, k)) != texto(recibido[k]) }
        .map { (campo, etiqueta) -> CampoEntregas(campo, etiqueta, configDe(calendario, campo), recibido[campo]) }
    val nuevas = ArrayList<EntregaQr>()
    val distintas = ArrayList<EntregaDistinta>()
    var iguales = 0
    val mias = calendario.entregas.associateBy { it.fechaLimite }      // como Map: gana la última
    for (r in paquete.entregas) {
        val estado = unoDe(r.estado, listOf("pendiente", "entregada", "observada", "conforme"), "pendiente")
        val e = mias[r.fechaLimite]
        when {
            e == null -> nuevas.add(EntregaQr(r.fechaLimite, estado, r.nota))
            e.estado.ifEmpty { "pendiente" } == estado && e.nota == r.nota -> iguales += 1
            else -> distintas.add(EntregaDistinta(r.fechaLimite, e, EstadoNota(estado, r.nota)))
        }
    }
    return ComparacionEntregas(config, nuevas, distintas, iguales)
}

/** Campos (config) y fechas (nuevas, distintas) donde se usa lo recibido. */
data class EleccionEntregas(
    val config: Set<String> = emptySet(),
    val nuevas: Set<String> = emptySet(),
    val distintas: Set<String> = emptySet()
)

/** Aplica lo elegido. No cambia `calendario`: devuelve uno nuevo. */
fun aplicarEntregas(calendario: Calendario, comparacion: ComparacionEntregas, eleccion: EleccionEntregas): Calendario {
    var nuevo = calendario
    for (c in comparacion.config) {
        if (c.campo !in eleccion.config) continue
        nuevo = when (c.campo) {
            "periodicidad" -> nuevo.copy(periodicidad = c.recibido as String)
            "fechaLimiteBase" -> nuevo.copy(fechaLimiteBase = c.recibido as String)
            else -> nuevo.copy(diasDeAviso = (c.recibido as List<*>).map { it as Int })
        }
    }
    val entregas = nuevo.entregas.toMutableList()
    for (n in comparacion.nuevas) {
        if (n.fechaLimite !in eleccion.nuevas) continue
        entregas.add(nuevaEntrega(n.fechaLimite).copy(estado = n.estado, nota = n.nota))
    }
    for (d in comparacion.distintas) {
        if (d.fechaLimite !in eleccion.distintas) continue
        val j = entregas.indexOfFirst { it.fechaLimite == d.fechaLimite }
        if (j >= 0) entregas[j] = entregas[j].copy(estado = d.recibido.estado, nota = d.recibido.nota)
    }
    return nuevo.copy(entregas = entregas)
}
