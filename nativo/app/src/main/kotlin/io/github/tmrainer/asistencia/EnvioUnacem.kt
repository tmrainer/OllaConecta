// EnvioUnacem.kt — el envío a UNACEM sin nube (envio.js y la parte de
// «revisar()» de nube/apps-script/Codigo.js que corría de noche): la app
// arma el correo (nucleo/envio) y lo abre en la app de correo del teléfono;
// la dirigente lo manda desde su propio correo. Sin servidor ni cuenta.
//
// Lo que queda aquí: a qué correo va (lo pone la dirigente en Ajustes),
// cuándo se preparó cada envío, y el recordatorio de las 9 de la noche si
// hay marcas de hoy sin enviar (y el 15 y el último día del mes, el padrón).
// La app no sabe si el correo de verdad salió: dice «lo preparaste a las…
// en Gmail» y nada más.
package io.github.tmrainer.asistencia

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.envio.esDiaDePadron
import io.github.tmrainer.asistencia.nucleo.envio.pareceCorreo
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Envios(private val context: Context) {

    /** Desde esta hora (del teléfono) se recuerda lo que falte, como el envío automático de la nube. */
    val horaRecordatorio = 21

    private val prefs = context.getSharedPreferences("envios", Context.MODE_PRIVATE)
    private val _correo = MutableStateFlow(prefs.getString("correo", "") ?: "")
    private val _recordar = MutableStateFlow(prefs.getBoolean("recordar", true))
    private val _cambios = MutableStateFlow(0)

    /** A dónde se manda: el correo de UNACEM, que pone la dirigente. */
    val correo: StateFlow<String> = _correo.asStateFlow()
    val recordar: StateFlow<Boolean> = _recordar.asStateFlow()
    /** Cambia cada vez que se anota un envío, para repintar «lo preparaste a las…». */
    val cambios: StateFlow<Int> = _cambios.asStateFlow()

    fun cambiarCorreo(texto: String) { _correo.value = texto; prefs.edit().putString("correo", texto.trim()).apply() }

    fun cambiarRecordar(si: Boolean) {
        _recordar.value = si
        prefs.edit().putBoolean("recordar", si).apply()
        programarRecordatorio()
    }

    /** ¿Hay un correo bien escrito a dónde mandar? */
    fun hayCorreo(): Boolean = pareceCorreo(_correo.value)

    /** Anota que el envío de ese día se preparó ahora, en tal app (si Android lo dijo). */
    fun anotar(tipo: String, fecha: String, app: String?) {
        val hora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        prefs.edit().putString("$tipo.$fecha", if (app.isNullOrBlank()) hora else "$hora|$app").apply()
        _cambios.value += 1
        // Ya preparado: el recordatorio de esta noche no hace falta.
        if (fecha == hoyIso()) context.getSystemService(android.app.NotificationManager::class.java)?.cancel(if (tipo == "padron") ID_PADRON else ID_ASISTENCIA)
    }

    /** «Lo preparaste hoy a las 20:15 en Gmail.», o null si no se preparó. */
    fun ultimo(tipo: String, fecha: String): String? {
        val v = prefs.getString("$tipo.$fecha", null) ?: return null
        val (hora, app) = v.split("|", limit = 2).let { it[0] to it.getOrNull(1) }
        val dia = if (fecha == hoyIso()) "hoy" else "ese día"
        return "Lo preparaste $dia a las $hora${if (app != null) " en $app" else ""}. Revisa en tu correo que haya salido."
    }

    /** Deja programado el recordatorio de esta noche (o de mañana, si ya pasó la hora). */
    fun programarRecordatorio() {
        val alarmas = context.getSystemService(AlarmManager::class.java) ?: return
        val pendiente = PendingIntent.getBroadcast(
            context, ID_ASISTENCIA, Intent(context, ReceptorRecordatorio::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmas.cancel(pendiente)
        if (!_recordar.value) return
        val ahora = LocalDateTime.now()
        var cuando = ahora.toLocalDate().atTime(horaRecordatorio, 0)
        if (!cuando.isAfter(ahora)) cuando = cuando.plusDays(1)
        alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pendiente)
    }

    /** Llegó la hora: avisa lo que falte de hoy y deja programado el de mañana. */
    internal fun recordar(app: OllaConectaApp) {
        programarRecordatorio()
        if (!_recordar.value || !hayCorreo()) return
        val hoy = hoyIso()
        val padron = app.estado.padron.value
        val delDia = padron.atenciones.firstOrNull { it.fecha == hoy }
        val raciones = if (delDia != null) desgloseDelDia(delDia).total else 0
        if (raciones > 0 && ultimo("asistencia", hoy) == null) {
            AvisosTelefono.mostrar(app, ID_ASISTENCIA, "Falta enviar la asistencia de hoy",
                "Hoy hubo ${cuantos(raciones, "ración", "raciones")}. Toca para enviarla a UNACEM.", abrir = "asistencia")
        }
        if (esDiaDePadron(hoy) && padron.afiliados.any { it.activo } && ultimo("padron", hoy) == null) {
            AvisosTelefono.mostrar(app, ID_PADRON, "Hoy toca mandar el padrón",
                "Toca para enviarlo a UNACEM desde tu correo.", abrir = "padron")
        }
    }

    companion object {
        const val ID_ASISTENCIA = 900_001
        const val ID_PADRON = 900_002
    }
}

class ReceptorRecordatorio : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OllaConectaApp
        app.envios.recordar(app)
    }
}
