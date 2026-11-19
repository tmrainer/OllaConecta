// Las fotos de cada persona del padrón (decisión del 05-11-2026): la del DNI
// (Afiliado.fotoDni, solo de este teléfono) y la de la persona
// (Afiliado.fotoPersona, como la del libro: sale en los PDF del padrón y en la
// ficha). Viven en filesDir/fotos. No van en la copia de los datos ni por QR.
// «Borrar todo» y borrar a una persona las borran con ella.
package io.github.tmrainer.asistencia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object Fotos {
    private const val LADO_MAXIMO = 1280
    private const val CALIDAD = 85

    fun carpeta(context: Context): File = File(context.filesDir, "fotos").apply { mkdirs() }

    fun archivo(context: Context, nombre: String): File? =
        if (nombre.isBlank() || nombre.contains('/') || nombre.contains("..")) null else File(carpeta(context), nombre).takeIf { it.isFile }

    /** Guarda la foto (como mucho 1280 px de lado, JPEG) y devuelve el nombre del archivo. Escritura atómica. */
    fun guardar(context: Context, afiliadoId: String, foto: Bitmap): String {
        val nombre = "$afiliadoId.jpg"
        val factor = LADO_MAXIMO.toFloat() / max(foto.width, foto.height)
        val chica = if (factor < 1f) Bitmap.createScaledBitmap(foto, (foto.width * factor).toInt(), (foto.height * factor).toInt(), true) else foto
        val destino = File(carpeta(context), nombre)
        val temporal = File(carpeta(context), "$nombre.tmp")
        FileOutputStream(temporal).use { chica.compress(Bitmap.CompressFormat.JPEG, CALIDAD, it) }
        if (!temporal.renameTo(destino)) { destino.delete(); temporal.renameTo(destino) }
        if (chica !== foto) chica.recycle()
        return nombre
    }

    /** La foto, reducida para la pantalla (a lo más [lado] px de lado), o null si no está. */
    fun cargar(context: Context, nombre: String, lado: Int = 320): Bitmap? {
        val f = archivo(context, nombre) ?: return null
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, medidas)
        var muestra = 1
        while (max(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= lado) muestra *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = muestra })
    }

    /** La foto como JPEG chico (a lo más [lado] px de lado) para meterla en un PDF; null si no está. */
    fun bytesParaPdf(context: Context, nombre: String, lado: Int = 480): ByteArray? {
        val b = cargar(context, nombre, lado) ?: return null
        val recorte = tresPorCuatro(b)
        val factor = lado.toFloat() / max(recorte.width, recorte.height)
        val chica = if (factor < 1f) Bitmap.createScaledBitmap(recorte, (recorte.width * factor).toInt(), (recorte.height * factor).toInt(), true) else recorte
        val salida = java.io.ByteArrayOutputStream()
        chica.compress(Bitmap.CompressFormat.JPEG, 80, salida)
        if (chica !== recorte) chica.recycle()
        if (recorte !== b) recorte.recycle()
        b.recycle()
        return salida.toByteArray()
    }

    /**
     * El centro de la foto en 3 × 4, la caja de la foto en el PDF: así sale lo
     * mismo que en la miniatura de la pantalla (60 × 80, ContentScale.Crop).
     */
    fun tresPorCuatro(b: Bitmap): Bitmap {
        val w = minOf(b.width, b.height * 3 / 4)
        val h = minOf(b.height, b.width * 4 / 3)
        return if (w == b.width && h == b.height) b else Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w, h)
    }

    /**
     * El retrato, recortado del lado de la foto del DNI (la tarjeta derecha): en el
     * DNI peruano (azul y electrónico) la foto está a la izquierda, más o menos
     * entre el 4 % y el 31 % del ancho y el 20 % y el 82 % del alto. Es un recorte
     * aproximado: la pantalla deja verlo y tomar otro.
     */
    fun retratoDelDni(dni: Bitmap): Bitmap? = try {
        val x = (dni.width * 0.04f).toInt(); val w = (dni.width * 0.27f).toInt()
        val y = (dni.height * 0.20f).toInt(); val h = (dni.height * 0.62f).toInt()
        if (w < 20 || h < 20) null else Bitmap.createBitmap(dni, x, y, w, h)
    } catch (e: Exception) { null }

    fun borrar(context: Context, nombre: String) { archivo(context, nombre)?.delete() }

    fun borrarTodas(context: Context) { carpeta(context).listFiles()?.forEach { it.delete() } }
}
