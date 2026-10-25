// Revisor.kt — pasa la app instalada por los chequeos del Accessibility Scanner
// de Google (Accessibility Test Framework): tamaño de lo que se toca, contraste
// del texto y de los íconos, nombres para TalkBack, texto cortado, elementos
// duplicados. Recorre cada sección (y la ayuda del Inicio), baja por las
// pantallas largas y anota lo que encuentra en /data/local/tmp/revisor.txt
// (y en logcat, etiqueta «Revisor»). Solo mira y navega: no cambia datos.
//
//   ./gradlew :perfil:connectedNonMinifiedReleaseAndroidTest \
//     -Pandroid.testInstrumentationRunnerArguments.class=io.github.tmrainer.asistencia.perfil.Revisor
//   adb pull /data/local/tmp/revisor.txt
//
// Revisa la app de verdad (io.github.tmrainer.asistencia); otra, con el
// argumento «paquete».
package io.github.tmrainer.asistencia.perfil

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.graphics.Rect
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class Revisor {

    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentacion)
    private val paquete = InstrumentationRegistry.getArguments().getString("paquete") ?: "io.github.tmrainer.asistencia"
    private val chequeos: Set<AccessibilityHierarchyCheck> =
        AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)

    /** Lo ya anotado: lo mismo visto al bajar por la pantalla se cuenta una vez. */
    private val vistos = HashSet<String>()
    private var errores = 0
    private var avisos = 0
    private val informe = StringBuilder()

    private fun anotar(linea: String) { Log.i(TAG, linea); informe.append(linea).append('\n') }

    @Test
    fun revisar() {
        val abrir = instrumentacion.context.packageManager.getLaunchIntentForPackage(paquete) ?: error("No está instalada: $paquete")
        instrumentacion.context.startActivity(abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.hasObject(By.pkg(paquete).depth(0)), 5_000)
        device.waitForIdle()

        for (seccion in listOf("Inicio", "Asistencia", "Padrón", "B-1", "Entregas")) {
            device.findObject(By.text(seccion))?.click()
            device.waitForIdle(); Thread.sleep(1_000)   // que termine el paso de una pantalla a otra
            revisarPantalla(seccion)
            if (seccion == "Inicio") {
                device.findObject(By.text("Ayuda"))?.click(); Thread.sleep(900)
                revisarPantalla("Inicio · Ayuda", bajar = false)
                device.findObject(By.text("Cerrar"))?.click(); Thread.sleep(400)
                device.findObject(By.text("Ajustes"))?.click(); device.waitForIdle(); Thread.sleep(1_000)
                revisarPantalla("Ajustes")
                device.pressBack(); device.waitForIdle(); Thread.sleep(1_000)
            }
            if (seccion == "Padrón") {
                device.findObject(By.text("Inscribir"))?.click(); device.waitForIdle(); Thread.sleep(1_000)
                revisarPantalla("Inscribir")
                device.pressBack(); device.waitForIdle(); Thread.sleep(1_000)
            }
        }
        anotar("TOTAL: $errores errores, $avisos advertencias")
        // El registro del teléfono se llena rápido: el informe completo va a un archivo.
        val (salida, entrada) = instrumentacion.uiAutomation.executeShellCommandRw("tee /data/local/tmp/revisor.txt")
        ParcelFileDescriptor.AutoCloseOutputStream(entrada).use { it.write(informe.toString().toByteArray()) }
        ParcelFileDescriptor.AutoCloseInputStream(salida).use { it.readBytes() }
    }

    /** Revisa lo que se ve y, si [bajar], baja de a poco hasta que la pantalla ya no cambia. */
    private fun revisarPantalla(nombre: String, bajar: Boolean = true) {
        anotar("== $nombre")
        var anterior = ""
        repeat(if (bajar) 6 else 1) {
            val raiz = instrumentacion.uiAutomation.rootInActiveWindow ?: return
            val captura = instrumentacion.uiAutomation.takeScreenshot() ?: return
            val jerarquia = AccessibilityHierarchyAndroid.newBuilder(raiz, instrumentacion.targetContext).build()
            val parametros = Parameters().apply { putScreenCapture(BitmapImage(captura)) }
            for (chequeo in chequeos) {
                for (r in chequeo.runCheckOnHierarchy(jerarquia, null, parametros)) {
                    if (r.type != AccessibilityCheckResultType.ERROR && r.type != AccessibilityCheckResultType.WARNING) continue
                    val e = r.element
                    val lugar = e?.boundsInScreen?.let { Rect(it.left, it.top, it.right, it.bottom) }
                    // Lo que se ve cortado (entrando o saliendo al bajar) se revisa en otra vuelta, entero.
                    if (lugar != null && lugar.height() < MINIMO_VISIBLE && chequeo.javaClass.simpleName.contains("Contrast")) continue
                    val que = listOfNotNull(e?.text?.toString(), e?.contentDescription?.toString()).firstOrNull { it.isNotBlank() } ?: e?.className?.toString().orEmpty()
                    val clave = "${chequeo.javaClass.simpleName}|$que|${lugar?.width()}x${lugar?.height()}"
                    if (!vistos.add("$nombre|$clave")) continue
                    if (r.type == AccessibilityCheckResultType.ERROR) errores++ else avisos++
                    anotar("${r.type} ${chequeo.javaClass.simpleName} «${que.take(60)}» $lugar: ${r.getMessage(Locale("es")).toString().replace('\n', ' ')}")
                }
            }
            captura.recycle()
            // ¿Cambió algo al bajar? Si no, ya se llegó al final.
            val ahora = device.findObjects(By.pkg(paquete)).joinToString { it.text.orEmpty() }
            if (ahora == anterior) return
            anterior = ahora
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 30)
            device.waitForIdle(); Thread.sleep(300)
        }
    }

    private companion object {
        const val TAG = "Revisor"
        /** Alto mínimo (px) para revisar el contraste de algo: menos es texto cortado por el borde. */
        const val MINIMO_VISIBLE = 40
    }
}
