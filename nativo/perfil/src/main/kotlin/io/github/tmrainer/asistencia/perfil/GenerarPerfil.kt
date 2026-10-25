// GenerarPerfil.kt — el recorrido que define el perfil de arranque: abrir la
// app, llenar el primer uso (la app aparte arranca sin datos) y pasar por las
// cinco secciones del menú de abajo. Lo que se use aquí, Android lo deja
// compilado por adelantado y la app abre más rápido.
//
//   ./gradlew :app:generateBaselineProfile
//
// deja el perfil en app/src/release/generated/baselineProfiles/.
package io.github.tmrainer.asistencia.perfil

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerarPerfil {

    @get:Rule
    val regla = BaselineProfileRule()

    /** Escribe en el primer campo vacío de la pantalla. */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.escribir(texto: String) {
        device.waitForIdle()
        device.findObjects(By.clazz("android.widget.EditText")).firstOrNull { it.text.isNullOrEmpty() }?.text = texto
    }

    @Test
    fun generar() = regla.collect(
        packageName = InstrumentationRegistry.getArguments().getString("idApp") ?: error("Falta el argumento idApp"),
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        // La app aparte no tiene perfil: se llena el primer uso y se cierra la guía de bienvenida.
        if (device.wait(Until.findObject(By.text("Empezar")), 3_000) != null) {
            device.findObject(By.text("Empezar")).click()
            device.wait(Until.findObject(By.text("Presidenta")), 3_000)?.click()
            escribir("Prueba Perfil")   // nombre
            escribir("12345678")        // DNI
            device.findObject(By.text("Siguiente"))?.click()
            // Sin un DNI delante de la cámara: se sigue sin confirmar.
            device.wait(Until.findObject(By.textStartsWith("No puedo leer mi DNI")), 3_000)?.click()
            device.wait(Until.findObject(By.text("Nombre de la olla")), 3_000)
            escribir("Olla de prueba")
            device.findObject(By.text("Listo"))?.click()
            device.wait(Until.findObject(By.text("Cerrar")), 3_000)?.click()
        }
        for (seccion in listOf("Asistencia", "Padrón", "B-1", "Entregas", "Inicio")) {
            device.wait(Until.findObject(By.text(seccion)), 3_000)?.click()
            device.waitForIdle()
        }
    }
}
