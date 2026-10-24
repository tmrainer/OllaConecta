// AvisosTelefono.kt — avisos con la app cerrada (avisos-nativos.js): el
// teléfono se los programa a sí mismo con AlarmManager, sin red. Qué avisos y
// cuándo lo decide programaDeAvisos() (alertas, en :nucleo); aquí solo se
// entregan a Android y se muestran cuando llega la hora.
//
// Se reprograma todo cada vez que cambia el calendario (OllaConectaApp lo
// observa), y al encender el teléfono o actualizar la app: Android borra las
// alarmas en esos casos, y ReceptorAvisos arranca el proceso, que las vuelve a
// poner al cargar el calendario.
package io.github.tmrainer.asistencia

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.tmrainer.asistencia.nucleo.alertas.programaDeAvisos
import io.github.tmrainer.asistencia.nucleo.modelo.Calendario
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.hoyIso
import java.time.ZoneId

object AvisosTelefono {

    /** 9 de la mañana, como en el APK 1.7. */
    const val HORA_DEL_AVISO = 9

    private const val CANAL_ENTREGAS = "entregas"
    private const val ACCION_MOSTRAR = "io.github.tmrainer.asistencia.MOSTRAR_AVISO"
    const val EXTRA_ABRIR = "abrir"

    /** ¿Puede mostrar avisos? (Android 13+ pide permiso; antes, que no los hayan apagado.) */
    fun hayPermiso(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Borra los avisos programados y vuelve a programar los del calendario.
     * Devuelve cuántos quedaron (0 sin permiso: la franja de Entregas sigue al abrir).
     */
    fun reprogramar(context: Context, calendario: Calendario): Int {
        val alarmas = context.getSystemService(AlarmManager::class.java) ?: return 0
        val prefs = context.getSharedPreferences("avisos", Context.MODE_PRIVATE)
        for (id in prefs.getStringSet("ids", emptySet()).orEmpty()) {
            alarmas.cancel(intencion(context, id.toInt(), "", ""))
        }
        if (!hayPermiso(context)) {
            prefs.edit().putStringSet("ids", emptySet()).apply()
            return 0
        }
        val avisos = programaDeAvisos(calendario, hoyIso())
        for (a in avisos) {
            val dia = aFecha(a.fecha) ?: continue
            val en = dia.atTime(HORA_DEL_AVISO, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Inexacta pero aun con el teléfono en reposo: unos minutos de más no importan.
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, en, intencion(context, a.id, a.titulo, a.texto))
        }
        prefs.edit().putStringSet("ids", avisos.map { it.id.toString() }.toSet()).apply()
        return avisos.size
    }

    private fun intencion(context: Context, id: Int, titulo: String, texto: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReceptorAvisos::class.java).setAction(ACCION_MOSTRAR)
                .putExtra("id", id).putExtra("titulo", titulo).putExtra("texto", texto),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    internal fun mostrar(context: Context, id: Int, titulo: String, texto: String, abrir: String = "entregas") {
        if (!hayPermiso(context)) return
        val gestor = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && gestor.getNotificationChannel(CANAL_ENTREGAS) == null) {
            gestor.createNotificationChannel(NotificationChannel(CANAL_ENTREGAS, "Entregas del B-1", NotificationManager.IMPORTANCE_DEFAULT))
        }
        // Tocar el aviso abre la app en la pantalla que corresponde.
        val abrirApp = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ABRIR, abrir)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aviso = NotificationCompat.Builder(context, CANAL_ENTREGAS)
            .setSmallIcon(R.drawable.ic_stat_aviso)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, aviso) } catch (e: SecurityException) { /* permiso quitado justo ahora */ }
    }

    internal fun esMostrar(intent: Intent) = intent.action == ACCION_MOSTRAR
}

/** Muestra un aviso cuando llega su hora; al encender el teléfono o actualizar la app, los vuelve a programar. */
class ReceptorAvisos : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when {
            AvisosTelefono.esMostrar(intent) -> AvisosTelefono.mostrar(
                context, intent.getIntExtra("id", 0),
                intent.getStringExtra("titulo").orEmpty(), intent.getStringExtra("texto").orEmpty()
            )
            intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val app = context.applicationContext as OllaConectaApp
                AvisosTelefono.reprogramar(context, app.estado.calendario.value)
            }
        }
    }
}
