// GenerarPerfil.kt — el recorrido que define el perfil de arranque: abrir la
// app, saltar la bienvenida (la app aparte arranca sin datos) y pasar por las
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

    @Test
    fun generar() = regla.collect(
        packageName = InstrumentationRegistry.getArguments().getString("idApp") ?: error("Falta el argumento idApp"),
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.findObject(By.text("Saltar")), 3_000)?.click()
        for (seccion in listOf("Asistencia", "Padrón", "B-1", "Entregas", "Inicio")) {
            device.wait(Until.findObject(By.text(seccion)), 3_000)?.click()
            device.waitForIdle()
        }
    }
}
