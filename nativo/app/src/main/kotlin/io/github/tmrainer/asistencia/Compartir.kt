// Compartir.kt — entregar un archivo que arma la app (compartir-archivo.js):
// el PDF o el Excel exportado va al menú «Compartir» del teléfono (WhatsApp,
// Drive, Gmail…), y el correo a UNACEM se abre en la app de correo con el
// destinatario, el asunto, el texto y el Excel ya puestos. Nada sale del
// teléfono hasta que la persona elige a dónde y toca «Enviar».
//
// El archivo se escribe en la caché (cache/compartir, lo único que ve el
// FileProvider) y se borra el de la vez anterior. Android avisa qué app se
// eligió (ReceptorEleccion): con eso se anota el B-1 exportado o el envío.
package io.github.tmrainer.asistencia

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.tmrainer.asistencia.nucleo.envio.Correo
import io.github.tmrainer.asistencia.nucleo.envio.MIME_XLSX
import java.io.File

const val MIME_PDF = "application/pdf"

/** Qué anotar cuando la persona elige a dónde mandar el archivo. */
sealed class AlElegir {
    /** El B-1 exportado: va a sus `generaciones`, como en la web. */
    data class B1(val rendicionId: String, val formato: String) : AlElegir()
    /** El correo a UNACEM de ese día: «asistencia» o «padron». */
    data class Envio(val tipo: String, val fecha: String) : AlElegir()
}

private const val ACCION_ELEGIDO = "io.github.tmrainer.asistencia.ELEGIDO"

private fun archivoEnCache(context: Context, nombre: String, bytes: ByteArray): Uri {
    val carpeta = File(context.cacheDir, "compartir")
    carpeta.mkdirs()
    // Lo de la vez anterior ya no hace falta: la caché no guarda copias de los datos.
    carpeta.listFiles()?.forEach { it.delete() }
    val archivo = File(carpeta, nombre)
    archivo.writeBytes(bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
}

private fun avisoDeEleccion(context: Context, alElegir: AlElegir?): PendingIntent? {
    if (alElegir == null) return null
    val i = Intent(context, ReceptorEleccion::class.java).setAction(ACCION_ELEGIDO)
    when (alElegir) {
        is AlElegir.B1 -> i.putExtra("que", "b1").putExtra("rendicion", alElegir.rendicionId).putExtra("formato", alElegir.formato)
        is AlElegir.Envio -> i.putExtra("que", "envio").putExtra("tipo", alElegir.tipo).putExtra("fecha", alElegir.fecha)
    }
    // MUTABLE: Android le agrega qué app se eligió.
    val banderas = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    return PendingIntent.getBroadcast(context, 7, i, banderas)
}

/** El menú «Compartir» del teléfono con el archivo. */
fun compartirArchivo(context: Context, nombre: String, mime: String, bytes: ByteArray, titulo: String, alElegir: AlElegir? = null) {
    val uri = archivoEnCache(context, nombre, bytes)
    val enviar = Intent(Intent.ACTION_SEND).setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, titulo)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    enviar.clipData = ClipData.newRawUri(nombre, uri)
    val menu = Intent.createChooser(enviar, titulo, avisoDeEleccion(context, alElegir)?.intentSender)
    context.startActivity(menu.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * El correo listo en la app de correo del teléfono, solo apps de correo (las
 * que abren un «mailto:»). Con una sola, se abre directo; con varias, el menú
 * las muestra a ellas nada más. Devuelve false si el teléfono no tiene ninguna.
 */
fun abrirCorreo(context: Context, correo: Correo, para: String, alElegir: AlElegir? = null): Boolean {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val redactar = pm.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
        .map { it.activityInfo }.distinctBy { it.packageName }
    if (redactar.isEmpty()) return false
    val apps = redactar.map { it.packageName }
    val uri = archivoEnCache(context, correo.archivo, correo.bytes)
    // A la pantalla de redactar de cada app (la que abre un «mailto:»), si acepta
    // adjuntos; si no, a la app, y ella elige (Gmail también ofrece «Chat»).
    fun envioPara(paquete: String) = Intent(Intent.ACTION_SEND).setType(MIME_XLSX)
        .also { i ->
            i.setClassName(paquete, redactar.first { it.packageName == paquete }.name)
            if (i.resolveActivity(pm) == null) i.setComponent(null).setPackage(paquete)
        }
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(para.trim()))
        .putExtra(Intent.EXTRA_SUBJECT, correo.asunto)
        .putExtra(Intent.EXTRA_TEXT, correo.cuerpo)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .also { it.clipData = ClipData.newRawUri(correo.archivo, uri) }
    if (apps.size == 1) {
        context.startActivity(envioPara(apps[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Una sola app: ya se sabe cuál es.
        if (alElegir is AlElegir.Envio) {
            val nombre = try { pm.getApplicationLabel(pm.getApplicationInfo(apps[0], 0)).toString() } catch (e: Exception) { null }
            (context.applicationContext as OllaConectaApp).envios.anotar(alElegir.tipo, alElegir.fecha, nombre)
        }
        return true
    }
    val menu = Intent.createChooser(envioPara(apps[0]), "Enviar con", avisoDeEleccion(context, alElegir)?.intentSender)
        .putExtra(Intent.EXTRA_INITIAL_INTENTS, apps.drop(1).map { envioPara(it) }.toTypedArray())
    context.startActivity(menu.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

/** Android avisa qué app se eligió en el menú: se anota (no se sabe si después se envió). */
class ReceptorEleccion : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OllaConectaApp
        @Suppress("DEPRECATION")
        val elegida: ComponentName? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        val nombreApp = elegida?.let {
            try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it.packageName, 0)).toString() } catch (e: Exception) { null }
        }
        when (intent.getStringExtra("que")) {
            "b1" -> app.anotarB1Exportado(intent.getStringExtra("rendicion").orEmpty(), intent.getStringExtra("formato").orEmpty())
            "envio" -> app.envios.anotar(intent.getStringExtra("tipo").orEmpty(), intent.getStringExtra("fecha").orEmpty(), nombreApp)
        }
    }
}
