// Leer y escribir el MISMO archivo .json que exporta la web («Guardar una
// copia»), para que una usuaria migre con su copia. Aquí entra y sale texto:
// dónde vive el archivo en el teléfono es asunto de la capa de arriba.
package io.github.tmrainer.asistencia.nucleo.respaldo

import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.Config
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.Respaldo
import io.github.tmrainer.asistencia.nucleo.modelo.ahoraIso
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.migrarPadron
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class RevisionRespaldo(val error: String?, val partes: List<String>, val resumen: String)

private fun JsonElement?.ausente() = this == null || this is JsonNull

/**
 * Revisa un respaldo antes de restaurarlo: qué trae y si tiene la forma que
 * espera la aplicación. No escribe nada. Solo cuentan las partes que el archivo
 * trae de verdad. Mismos textos que la web.
 */
fun revisarRespaldo(datos: JsonElement?): RevisionRespaldo {
    if (datos !is JsonObject) {
        return RevisionRespaldo("El archivo no es un respaldo de esta aplicación.", emptyList(), "")
    }
    val partes = ArrayList<String>()
    val textos = ArrayList<String>()
    val errores = ArrayList<String>()

    val padron = datos["padron"]
    if (!padron.ausente()) {
        val afiliados = (padron as? JsonObject)?.get("afiliados")
        if (afiliados !is JsonArray) {
            errores.add("el padrón no tiene la lista de personas")
        } else {
            partes.add("padron")
            val dias = ((padron as JsonObject)["atenciones"] as? JsonArray)?.size ?: 0
            textos.add("${cuantos(afiliados.size, "persona")} en el padrón y ${cuantos(dias, "día")} de asistencia")
        }
    }
    val rendiciones = datos["rendiciones"]
    if (!rendiciones.ausente()) {
        if (rendiciones !is JsonArray) errores.add("las rendiciones no son una lista")
        else if (rendiciones.isNotEmpty()) {
            partes.add("rendiciones")
            textos.add(cuantos(rendiciones.size, "Formato B-1", "Formatos B-1"))
        }
    }
    val calendario = datos["calendario"]
    if (!calendario.ausente()) {
        if (calendario !is JsonObject) errores.add("el calendario está dañado")
        else { partes.add("calendario"); textos.add("el calendario de entregas") }
    }
    val config = datos["config"]
    if (!config.ausente()) {
        if (config !is JsonObject) errores.add("la configuración está dañada")
        else { partes.add("config"); textos.add("los precios del menú y datos del centro") }
    }
    if (errores.isNotEmpty()) {
        return RevisionRespaldo("El respaldo está dañado: ${errores.joinToString("; ")}. No se cambió nada.", emptyList(), "")
    }
    if (partes.isEmpty()) {
        return RevisionRespaldo("El archivo no trae datos para restaurar. No se cambió nada.", emptyList(), "")
    }
    return RevisionRespaldo(null, partes, textos.joinToString(", "))
}

sealed interface LecturaRespaldo {
    /** Un respaldo legible: solo trae las partes que el archivo traía. */
    data class Lista(val respaldo: Respaldo, val revision: RevisionRespaldo) : LecturaRespaldo
    /** Un respaldo con contraseña: hay que pedirla y llamar a descifrarRespaldo(). */
    data class Protegido(val sobre: JsonObject) : LecturaRespaldo
    data class Fallo(val mensaje: String) : LecturaRespaldo
}

/** Lee el texto de un .json de respaldo. Nunca lanza: devuelve Fallo. */
fun leerRespaldo(texto: String): LecturaRespaldo {
    val datos = try {
        JSON.parseToJsonElement(texto)
    } catch (e: SerializationException) {
        return LecturaRespaldo.Fallo("El archivo no es un respaldo de esta aplicación.")
    }
    return leerRespaldo(datos)
}

/** Lee un respaldo ya parseado (o ya descifrado). */
fun leerRespaldo(datos: JsonElement): LecturaRespaldo {
    if (esRespaldoCifrado(datos)) return LecturaRespaldo.Protegido(datos as JsonObject)
    val revision = revisarRespaldo(datos)
    if (revision.error != null) return LecturaRespaldo.Fallo(revision.error)
    val obj = datos as JsonObject
    return try {
        LecturaRespaldo.Lista(
            Respaldo(
                exportadoEn = (obj["exportadoEn"] as? JsonPrimitive)?.contentOrNull ?: "",
                aplicacion = (obj["aplicacion"] as? JsonPrimitive)?.contentOrNull ?: "rendicion-b1",
                rendiciones = if ("rendiciones" in revision.partes) JSON.decodeFromJsonElement(obj["rendiciones"]!!) else emptyList(),
                padron = if ("padron" in revision.partes) migrarPadron(obj["padron"]) else null,
                calendario = if ("calendario" in revision.partes) JSON.decodeFromJsonElement<Calendario>(obj["calendario"]!!) else null,
                config = if ("config" in revision.partes) JSON.decodeFromJsonElement<Config>(obj["config"]!!) else null
            ),
            revision
        )
    } catch (e: SerializationException) {
        LecturaRespaldo.Fallo("El respaldo está dañado: ${e.message ?: "no se pudo leer"}. No se cambió nada.")
    } catch (e: IllegalArgumentException) {
        LecturaRespaldo.Fallo("El respaldo está dañado: ${e.message ?: "no se pudo leer"}. No se cambió nada.")
    }
}

/** El respaldo completo: todo lo que la app guarda, en un solo objeto. */
fun armarRespaldo(
    rendiciones: List<Rendicion>,
    padron: Padron?,
    calendario: Calendario?,
    config: Config?,
    exportadoEn: String = ahoraIso()
) = Respaldo(exportadoEn = exportadoEn, aplicacion = "rendicion-b1", rendiciones = rendiciones,
    padron = padron, calendario = calendario, config = config)

/** El texto del .json, con la misma sangría de dos espacios que la web. */
fun escribirRespaldo(respaldo: Respaldo): String = JSON.encodeToString(Respaldo.serializer(), respaldo)

// Mismo sobre que cifrarRespaldo() de la web: AES-GCM de 256 bits con una
// clave derivada por PBKDF2-SHA256. Sin la contraseña no hay forma de abrirlo.
// PBKDF2 va escrito a mano sobre HmacSHA256 porque el proveedor de Android 24
// y 25 no trae «PBKDF2WithHmacSHA256»; HmacSHA256 y AES/GCM sí están.

private const val ITERACIONES_PBKDF2 = 310000   // recomendación OWASP para PBKDF2-SHA256

/** ¿Es un respaldo cifrado por cifrarRespaldo()? */
fun esRespaldoCifrado(objeto: JsonElement?): Boolean {
    val o = objeto as? JsonObject ?: return false
    val datos = o["datos"] as? JsonPrimitive
    return o["cifrado"] is JsonObject && datos != null && datos.isString
}

fun cifrarRespaldo(respaldo: Respaldo, contrasena: String): JsonObject {
    val azar = SecureRandom()
    val sal = ByteArray(16).also { azar.nextBytes(it) }
    val iv = ByteArray(12).also { azar.nextBytes(it) }
    val clave = pbkdf2Sha256(contrasena.toByteArray(Charsets.UTF_8), sal, ITERACIONES_PBKDF2, 32)
    val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
    cifrador.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, iv))
    val cifrado = cifrador.doFinal(escribirRespaldo(respaldo).toByteArray(Charsets.UTF_8))
    return buildJsonObject {
        put("aplicacion", "rendicion-b1")
        put("cifrado", buildJsonObject {
            put("algoritmo", "AES-GCM")
            put("derivacion", "PBKDF2-SHA256")
            put("iteraciones", ITERACIONES_PBKDF2)
            put("sal", aBase64(sal))
            put("iv", aBase64(iv))
        })
        put("datos", aBase64(cifrado))
    }
}

/**
 * Abre un respaldo cifrado. Con la contraseña equivocada (o el archivo
 * alterado) AES-GCM no descifra: lanza, nunca devuelve basura.
 * @throws javax.crypto.AEADBadTagException contraseña equivocada o archivo alterado
 */
fun descifrarRespaldo(sobre: JsonObject, contrasena: String): JsonElement {
    val c = sobre["cifrado"] as JsonObject
    val iteraciones = (c["iteraciones"] as JsonPrimitive).intOrNull ?: ITERACIONES_PBKDF2
    val clave = pbkdf2Sha256(contrasena.toByteArray(Charsets.UTF_8), deBase64((c["sal"] as JsonPrimitive).content), iteraciones, 32)
    val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
    cifrador.init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, deBase64((c["iv"] as JsonPrimitive).content)))
    val plano = cifrador.doFinal(deBase64((sobre["datos"] as JsonPrimitive).content))
    return JSON.parseToJsonElement(String(plano, Charsets.UTF_8))
}

internal fun pbkdf2Sha256(contrasena: ByteArray, sal: ByteArray, iteraciones: Int, largo: Int): ByteArray {
    require(contrasena.isNotEmpty()) { "La contraseña no puede estar vacía." }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(contrasena, "HmacSHA256"))
    val salida = ByteArray(largo)
    var pos = 0
    var bloque = 1
    while (pos < largo) {
        mac.update(sal)
        mac.update(byteArrayOf((bloque ushr 24).toByte(), (bloque ushr 16).toByte(), (bloque ushr 8).toByte(), bloque.toByte()))
        var u = mac.doFinal()
        val t = u.copyOf()
        for (i in 2..iteraciones) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        val n = minOf(t.size, largo - pos)
        System.arraycopy(t, 0, salida, pos, n)
        pos += n
        bloque += 1
    }
    return salida
}

// Base64 propio: java.util.Base64 recién existe en Android 26.
private const val ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal fun aBase64(bytes: ByteArray): String {
    val sb = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
        sb.append(ABC[b0 shr 2])
        sb.append(ABC[((b0 and 3) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
        sb.append(if (b1 >= 0) ABC[((b1 and 15) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)] else '=')
        sb.append(if (b2 >= 0) ABC[b2 and 63] else '=')
        i += 3
    }
    return sb.toString()
}

internal fun deBase64(texto: String): ByteArray {
    val limpio = texto.trimEnd('=')
    val salida = ArrayList<Byte>(limpio.length * 3 / 4)
    var acumulado = 0
    var bits = 0
    for (c in limpio) {
        val v = ABC.indexOf(c)
        require(v >= 0) { "Base64 inválido." }
        acumulado = (acumulado shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            salida.add(((acumulado shr bits) and 0xFF).toByte())
        }
    }
    return salida.toByteArray()
}
