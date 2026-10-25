// Los datos en el teléfono: una clave = un archivo JSON en una carpeta (en
// Android, context.filesDir). Se separan a propósito: un padrón corrupto no
// debe impedir llenar una rendición. Lo ilegible se conserva aparte
// (<archivo>.respaldo), nunca se borra en silencio. Toda escritura es atómica
// (temporal + rename): un corte de luz a medio escribir deja el anterior intacto.
package io.github.tmrainer.asistencia.nucleo.almacen

import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Las claves guardadas, con su archivo. Mismos nombres que CLAVES en modelo.js. */
enum class Clave(val archivo: String) {
    RENDICIONES("rendiciones.json"),
    PADRON("padron.json"),
    CALENDARIO("calendario.json"),
    CONFIG("config.json"),
    RESPALDO("respaldo.json")      // { ultimoEn: 'AAAA-MM-DD' }: cuándo se guardó la última copia
}

class Almacen(private val carpeta: File) {

    private val avisos = ArrayList<String>()

    // Lo último leído o escrito de cada clave: si no cambió, no se escribe.
    private val enDisco = HashMap<Clave, String?>()

    /** Avisos de lectura o escritura acumulados. */
    fun avisosAlmacenamiento(): List<String> = avisos.toList()

    private fun archivo(clave: Clave) = File(carpeta, clave.archivo)

    /**
     * El JSON guardado bajo la clave, o null si no hay nada. Si el archivo no
     * se puede leer o no es JSON, se aparta como <archivo>.respaldo y se avisa.
     */
    fun leer(clave: Clave): JsonElement? {
        val f = archivo(clave)
        if (!f.exists()) { enDisco[clave] = null; return null }
        val crudo = try {
            f.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            avisos.add("No se pudo leer el almacenamiento del teléfono (${clave.archivo}). " +
                "Si vuelve a pasar, guarda una copia y reinstala la aplicación.")
            return null
        }
        return try {
            val valor = JSON.parseToJsonElement(crudo)
            if (valor is JsonNull) throw SerializationException("forma inesperada")
            enDisco[clave] = crudo
            valor
        } catch (e: SerializationException) {
            apartar(f)
            avisos.add("Los datos guardados en \"${clave.archivo}\" no se pudieron leer y se " +
                "conservaron aparte en \"${clave.archivo}.respaldo\". Se empezó con los datos vacíos.")
            enDisco[clave] = null
            null
        }
    }

    /**
     * Aparta un archivo que es JSON válido pero no tiene la forma esperada
     * (lo detecta quien lo decodifica). Igual que un archivo ilegible: se
     * conserva como <archivo>.respaldo y se avisa.
     */
    fun apartarIlegible(clave: Clave) {
        val f = archivo(clave)
        if (f.exists()) apartar(f)
        enDisco[clave] = null
        avisos.add("Los datos guardados en \"${clave.archivo}\" no tienen la forma esperada y se " +
            "conservaron aparte en \"${clave.archivo}.respaldo\". Se empezó con los datos vacíos.")
    }

    private fun apartar(f: File) {
        val aparte = File(carpeta, f.name + ".respaldo")
        if (aparte.exists()) aparte.delete()
        if (!f.renameTo(aparte)) f.copyTo(aparte, overwrite = true)
    }

    /**
     * Escribe el JSON bajo la clave, de forma atómica. Si el texto no cambió,
     * no escribe. Devuelve false si falló (y lo anota en los avisos).
     */
    fun guardar(clave: Clave, valor: JsonElement): Boolean = guardar(clave, JSON.encodeToString(JsonElement.serializer(), valor))

    fun guardar(clave: Clave, texto: String): Boolean {
        if (enDisco[clave] == texto && archivo(clave).exists()) return true
        return try {
            carpeta.mkdirs()
            val destino = archivo(clave)
            val temporal = File(carpeta, clave.archivo + ".tmp")
            FileOutputStream(temporal).use { salida ->
                salida.write(texto.toByteArray(Charsets.UTF_8))
                salida.fd.sync()
            }
            if (!temporal.renameTo(destino)) throw IOException("no se pudo reemplazar ${destino.name}")
            enDisco[clave] = texto
            true
        } catch (e: IOException) {
            avisos.add("No se pudo guardar en \"${clave.archivo}\". Puede que el almacenamiento " +
                "esté lleno. Guarda una copia antes de cerrar.")
            false
        }
    }

    /** Borra todas las claves y lo apartado. true si no quedó nada. */
    fun borrarTodo(): Boolean {
        var ok = true
        for (clave in Clave.values()) {
            for (nombre in listOf(clave.archivo, clave.archivo + ".respaldo", clave.archivo + ".tmp")) {
                val f = File(carpeta, nombre)
                if (f.exists() && !f.delete()) ok = false
            }
            enDisco.remove(clave)
        }
        return ok
    }

    /** Cuánto ocupan los datos de la app, en bytes. */
    fun usoAlmacenamiento(): Long = Clave.values().sumOf { archivo(it).let { f -> if (f.exists()) f.length() else 0L } }
}
