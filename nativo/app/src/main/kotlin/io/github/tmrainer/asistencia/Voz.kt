// Voz.kt — leer en voz alta con la voz del propio teléfono (decir() y
// callar() de ui.js): TextToSpeech de Android, sin internet si el teléfono
// tiene la voz en español instalada. Se prende la primera vez que se usa; lo
// que se pide antes de que esté lista se dice apenas lo esté.
package io.github.tmrainer.asistencia

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class Voz(private val context: Context, private val lenta: () -> Boolean) {

    private var tts: TextToSpeech? = null
    private var lista = false
    private var pendiente: String? = null
    private var alNoPoderPendiente: (() -> Unit)? = null

    /** null mientras no se sabe; false si el teléfono no tiene voz que hable español. */
    var disponible: Boolean? = null
        private set

    /**
     * Dice el texto, cortando lo que se estuviera diciendo. Si este teléfono
     * no puede (ahora o al prender la voz por primera vez), llama a [alNoPoder].
     */
    fun decir(texto: String, alNoPoder: () -> Unit = {}) {
        if (texto.isBlank()) return
        if (disponible == false) { alNoPoder(); return }
        if (!lista) {
            pendiente = texto
            alNoPoderPendiente = alNoPoder
            if (tts == null) tts = TextToSpeech(context.applicationContext) { estado -> alPrender(estado) }
            return
        }
        val t = tts ?: return
        t.setSpeechRate(if (lenta()) 0.75f else 0.95f)
        if (t.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "olla") != TextToSpeech.SUCCESS) alNoPoder()
    }

    fun callar() { if (lista) tts?.stop() }

    private fun alPrender(estado: Int) {
        val t = tts
        if (estado != TextToSpeech.SUCCESS || t == null) { noPuede(); return }
        // Español del Perú si está; si no, cualquier español.
        val idioma = listOf(Locale("es", "PE"), Locale("es", "419"), Locale("es"))
            .firstOrNull { t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        if (idioma == null) { noPuede(); return }
        t.language = idioma
        disponible = true
        lista = true
        val texto = pendiente
        val alNoPoder = alNoPoderPendiente ?: {}
        pendiente = null; alNoPoderPendiente = null
        if (texto != null) decir(texto, alNoPoder)
    }

    private fun noPuede() {
        disponible = false
        alNoPoderPendiente?.invoke()
        pendiente = null; alNoPoderPendiente = null
    }
}
