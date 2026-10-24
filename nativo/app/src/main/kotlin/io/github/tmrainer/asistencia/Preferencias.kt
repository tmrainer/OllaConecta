// Preferencias.kt — lo que es de este teléfono y no va en las copias (como
// las claves «pi3.*» de preferencias.js y perfil.js): el modo, el tamaño de
// letra, quién usa el teléfono, y si ya vio la bienvenida.
package io.github.tmrainer.asistencia

import android.content.Context
import io.github.tmrainer.asistencia.nucleo.modelo.Perfil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Tema(val clave: String, val etiqueta: String) {
    AUTO("auto", "Automático (como el teléfono)"), CLARO("claro", "Claro"), OSCURO("oscuro", "Oscuro")
}

enum class Letra(val clave: String, val etiqueta: String, val factor: Float) {
    NORMAL("normal", "Normal", 1f), GRANDE("grande", "Grande", 1.125f), MUY_GRANDE("muy-grande", "Muy grande", 1.25f)
}

val ROLES = listOf("presidenta" to "Presidenta", "tesorera" to "Tesorera", "apoyo" to "Apoyo")

class Preferencias(context: Context) {
    private val prefs = context.getSharedPreferences("pi3", Context.MODE_PRIVATE)

    private val _tema = MutableStateFlow(Tema.values().firstOrNull { it.clave == prefs.getString("tema", "auto") } ?: Tema.AUTO)
    private val _letra = MutableStateFlow(Letra.values().firstOrNull { it.clave == prefs.getString("letra", "normal") } ?: Letra.NORMAL)
    private val _perfil = MutableStateFlow(Perfil(
        nombre = prefs.getString("perfil.nombre", "") ?: "",
        rol = prefs.getString("perfil.rol", "") ?: "",
        dni = prefs.getString("perfil.dni", "") ?: "",
        celular = prefs.getString("perfil.celular", "") ?: ""
    ))
    private val _perfilOmitido = MutableStateFlow(prefs.getBoolean("perfil.omitido", false))
    private val _bienvenidaVista = MutableStateFlow(prefs.getBoolean("bienvenidaVista", false))

    val tema: StateFlow<Tema> = _tema.asStateFlow()
    val letra: StateFlow<Letra> = _letra.asStateFlow()
    val perfil: StateFlow<Perfil> = _perfil.asStateFlow()
    val perfilOmitido: StateFlow<Boolean> = _perfilOmitido.asStateFlow()
    val bienvenidaVista: StateFlow<Boolean> = _bienvenidaVista.asStateFlow()

    fun cambiarTema(t: Tema) { _tema.value = t; prefs.edit().putString("tema", t.clave).apply() }
    fun cambiarLetra(l: Letra) { _letra.value = l; prefs.edit().putString("letra", l.clave).apply() }

    fun guardarPerfil(p: Perfil) {
        _perfil.value = p
        prefs.edit()
            .putString("perfil.nombre", p.nombre).putString("perfil.rol", p.rol)
            .putString("perfil.dni", p.dni).putString("perfil.celular", p.celular)
            .apply()
    }

    /** ¿Ya dijo quién es? Con nombre y rol alcanza. */
    fun perfilCompleto(): Boolean {
        val p = _perfil.value
        return p.nombre.trim().isNotEmpty() && ROLES.any { it.first == p.rol }
    }

    /** «Ahora no» en el Inicio: no volver a preguntar. */
    fun omitirPerfil() { _perfilOmitido.value = true; prefs.edit().putBoolean("perfil.omitido", true).apply() }

    fun marcarBienvenidaVista() { _bienvenidaVista.value = true; prefs.edit().putBoolean("bienvenidaVista", true).apply() }
}
