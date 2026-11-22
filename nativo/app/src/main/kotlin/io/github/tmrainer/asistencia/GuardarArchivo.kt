// «Guardar en el teléfono», como una opción más del menú «Compartir» de Android (al lado
// de WhatsApp, Gmail o Drive): recibe el archivo que arma la app, pregunta dónde
// guardarlo (por defecto, Descargas) y lo copia ahí. No se ve: solo el selector de Android.
package io.github.tmrainer.asistencia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuardarArchivo : ComponentActivity() {
    private val archivo: Uri? get() = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    private val nombre: String get() = intent.getStringExtra(EXTRA_NOMBRE).orEmpty().ifEmpty { "OllaConecta" }

    private val elegir = registerForActivityResult(ActivityResultContracts.CreateDocument(intent?.type ?: "*/*")) { destino ->
        val origen = archivo
        if (destino == null || origen == null) { finish(); return@registerForActivityResult }
        lifecycleScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(origen)?.use { e -> contentResolver.openOutputStream(destino)?.use { s -> e.copyTo(s) } } != null
                }
            } catch (e: Exception) { false }
            Toast.makeText(applicationContext, if (ok) "Guardado en el teléfono: $nombre" else "No se pudo guardar el archivo. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Al volver de otra pantalla (rotar, memoria), el selector ya está abierto.
        if (savedInstanceState == null) {
            if (archivo == null) finish() else elegir.launch(nombre)
        }
    }

    companion object { const val EXTRA_NOMBRE = "io.github.tmrainer.asistencia.NOMBRE" }
}
