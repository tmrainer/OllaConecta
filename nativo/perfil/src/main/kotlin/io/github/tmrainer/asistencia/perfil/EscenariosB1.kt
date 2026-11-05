// EscenariosB1.kt — una persona llenando el B-1 en la app aparte (sin datos),
// tocando y escribiendo como lo haría a mano. Tres vueltas sobre el mismo B-1:
//
// 1. Bien llenado: una compra con boleta y otra de carne «Varios juntos»,
//    comprada en el mercado sin boleta. Tiene que quedar completo, sin firmas.
// 2. Errores de todos los días, paso por paso: un DNI de 7 cifras, el celular
//    mal, un RUC corto, una boleta a medias, un monto olvidado y una compra a
//    medio anotar. Al tocar «Siguiente», la app no deja seguir: dice qué está
//    mal y lleva ahí. Se corrige y se sigue, como haría una persona. Las fechas
//    imposibles (asignación después de la rendición, compra antes del
//    subsidio) ni se pueden elegir en el calendario.
// 3. Un aviso (cantidad × precio no da el total) también frena el paso hasta
//    tocar «Está bien así»; se ve en «Revisar» y, corregido, se va.
//
//   ./gradlew :perfil:connectedNonMinifiedReleaseAndroidTest \
//     -Pandroid.testInstrumentationRunnerArguments.class=io.github.tmrainer.asistencia.perfil.EscenariosB1
//   adb pull /data/local/tmp/escenarios.txt
//
// Los datos son inventados. Gradle desinstala la app aparte al terminar.
package io.github.tmrainer.asistencia.perfil

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class EscenariosB1 {

    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentacion)
    private val paquete = InstrumentationRegistry.getArguments().getString("idApp") ?: error("Falta el argumento idApp")
    private val informe = StringBuilder()
    private var fallas = 0

    @Test
    fun escenarios() {
        val abrir = instrumentacion.context.packageManager.getLaunchIntentForPackage(paquete) ?: error("No está instalada: $paquete")
        instrumentacion.context.startActivity(abrir.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.hasObject(By.pkg(paquete).depth(0)), 8_000)
        try {
            primerUso()
            toca("B-1"); espera()

            anotar("== 0. La ayuda del B-1")
            ayudaDelB1()

            anotar("== 1. Bien llenado")
            llenarBien()
            irAPaso(2); tocaFlotante("Ayuda"); espera(900)
            comprobar("con las fechas puestas, la ayuda del periodo salta «Usa lo que propone» (3 pasos)", device.findObject(By.text("1 de 3")) != null)
            captura("ayuda-periodo"); tocaFlotante("Cerrar")
            val bien = revisar("bien")
            comprobar("queda completo", bien.any { it.startsWith("El B-1 está completo") })
            comprobar("la compra sin boleta no es error", bien.none { "RUC" in it || "serie o el correlativo" in it })
            comprobar("sin paso de firmas ni nada de quién firma", device.findObject(By.descStartsWith("Paso 6:")) == null && bien.none { "firma" in it.lowercase() })

            anotar("== 2. Errores de todos los días, paso por paso")
            erroresPasoAPaso()

            anotar("== 3. Un aviso frena hasta aceptarlo, y corregido")
            irAPaso(4); abrirCompra(1)
            escribe("Monto total (S/)", "50.00")    // 10 × 4.50 son 45.00
            comprobar("el aviso frena el paso 4", "Compra 1: cantidad × precio unitario da S/ 45.00" in intentaSeguir())
            captura("aviso-frena"); toca("Está bien así")
            comprobar("«Está bien así» deja seguir", device.wait(Until.hasObject(By.textStartsWith("Paso 5 de 5")), 3_000))
            val conAviso = revisar("aviso")
            comprobar("avisa «cantidad × precio unitario da S/ 45.00»", conAviso.any { "Compra 1: cantidad × precio unitario da S/ 45.00" in it })
            comprobar("el aviso no impide el B-1 completo", conAviso.any { it.startsWith("El B-1 está completo") })
            irAPaso(4); abrirCompra(1)
            escribe("Monto total (S/)", "45.00")
            val corregido = revisar("corregido")
            comprobar("corregido, sin el aviso", corregido.none { "cantidad × precio" in it })
            comprobar("vuelve a quedar completo", corregido.any { it.startsWith("El B-1 está completo") })
        } catch (e: Throwable) {
            fallas++; anotar("FALLA inesperada: ${e.javaClass.simpleName}: ${e.message}")
            captura("falla")
            // Qué había en pantalla: texto, descripción y dónde.
            repeat(2) { vuelta ->
                if (vuelta == 1) { alPrincipio(); anotar("   -- arriba") }
                device.findObjects(By.pkg(paquete)).forEach { o ->
                    runCatching { anotar("   en pantalla: «${o.text ?: ""}» «${o.contentDescription ?: ""}» ${o.className} ${o.visibleBounds}") }
                }
            }
        }
        anotar("TOTAL: $fallas fallas")
        guardarInforme()
        assertEquals("Revisa /data/local/tmp/escenarios.txt", 0, fallas)
    }

    // ------------------------------------------------------------ los escenarios

    private fun primerUso() {
        if (device.wait(Until.findObject(By.text("Empezar")), 4_000) == null) return
        toca("Empezar"); toca("Presidenta")
        escribe("Tu nombre y apellidos", "María López Quispe")
        escribe("Tu DNI", "45678912")
        toca("Continuar")
        device.wait(Until.findObject(By.textStartsWith("No puedo leer mi DNI")), 3_000)?.click(); espera()
        escribe("Nombre de la olla", "Olla común Virgen de Fátima")
        toca("Terminar")
        device.wait(Until.findObject(By.text("Cerrar")), 3_000)?.click(); espera()
    }

    private fun llenarBien() {
        irAPaso(1)
        toca("Olla común")
        escribe("Nombre del centro de atención", "Olla común Virgen de Fátima")
        escribe("Código de PCA N.º", "0123")
        escribe("Nombre de la presidenta", "María López Quispe")
        escribe("DNI", "45678912")
        escribe("Celular N.º", "987654321")

        irAPaso(2)
        toca("Mensual")
        fecha("Fecha de rendición", "30092026")
        toca("Usar estas fechas")
        toca("Setiembre")

        irAPaso(3)
        escribe("Monto del subsidio (S/)", "186.85")
        fecha("Fecha de la asignación", "07092026")

        irAPaso(4)
        abrirCompra(1)
        fecha("Fecha de la compra", "10092026")
        escribe("Descripción de la compra", "Arroz superior")
        escribe("Cantidad", "10")
        toca("—"); toca("Kg")
        escribe("Precio unitario (S/)", "4.50")
        escribe("Monto total (S/)", "45.00")
        escribe("RUC del vendedor", "10456789123")
        escribe("Serie", "E001")
        escribe("Número", "000143")
        captura("compra-con-boleta")

        tocaFlotante("Compra")
        fecha("Fecha de la compra", "13092026")
        escribe("Descripción de la compra", "Carne")
        toca("Varios juntos")
        comprobar("con «Varios juntos» no pide cantidad ni precio",
            device.wait(Until.gone(By.text("Cantidad")), 2_000) && device.wait(Until.gone(By.text("Precio unitario (S/)")), 2_000))
        escribe("Monto total (S/)", "141.85")
        captura("compra-varios")
    }

    /** La primera vez: cómo funciona todo, y al final la del paso. La segunda: directo al paso. */
    private fun ayudaDelB1() {
        tocaFlotante("Ayuda"); espera(900)
        comprobar("la primera vez sale la general", device.findObject(By.text("Cómo se usa: B-1")) != null)
        captura("ayuda-general")
        repeat(5) { intentar("avanzar la guía") { device.findObject(By.text("Siguiente"))?.click() }; espera(800) }
        val hayVer = device.wait(Until.hasObject(By.text("Ver la ayuda de este paso")), 2_000)
        comprobar("al final ofrece «Ver la ayuda de este paso»", hayVer)
        if (hayVer) intentar("ver la ayuda del paso") { device.findObject(By.text("Ver la ayuda de este paso")).click() }
        espera(900)
        comprobar("y lleva a la del paso 1", device.wait(Until.hasObject(By.textStartsWith("Cómo se usa: B-1, paso 1")), 3_000))
        captura("ayuda-paso1"); tocaFlotante("Cerrar")
        tocaFlotante("Ayuda"); espera(900)
        comprobar("la segunda vez va directo al paso", device.findObject(By.textStartsWith("Cómo se usa: B-1, paso 1")) != null)
        tocaFlotante("Cerrar")
    }

    /** Se equivoca, toca «Siguiente», la app la frena (qué está mal y dónde), corrige y sigue. */
    private fun erroresPasoAPaso() {
        irAPaso(1)
        escribe("DNI", "4567891")              // se comió una cifra
        escribe("Celular N.º", "887654321")    // empieza en 8
        val p1 = intentaSeguir()
        comprobar("paso 1 frena por el DNI", "El DNI debe tener exactamente 8 dígitos" in p1)
        comprobar("paso 1 frena por el celular", "El celular debe tener 9 dígitos y empezar en 9" in p1)
        comprobar("el error se ve bajo el campo", buscar(By.text("El DNI debe tener exactamente 8 dígitos.")) != null)
        escribe("DNI", "45678912"); escribe("Celular N.º", "987654321")
        comprobar("corregido, el paso 1 deja seguir", intentaSeguir().isEmpty())

        irAPaso(3)
        comprobar("el calendario no deja poner la asignación después de la rendición (30-09)", fechaBloqueada("Fecha de la asignación", "02102026"))
        comprobar("y la asignación sigue en 07-09", buscar(By.desc("Fecha de la asignación: 07-09-26")) != null)
        comprobar("el paso 3 deja seguir", intentaSeguir().isEmpty())

        irAPaso(4)
        abrirCompra(1)
        comprobar("el calendario no deja una compra antes del subsidio (07-09)", fechaBloqueada("Fecha de la compra", "05092026"))
        escribe("RUC del vendedor", "1045678912")   // 10 cifras
        escribe("Número", "")                  // la serie quedó sola
        abrirCompra(2)
        escribe("Monto total (S/)", "")        // se olvidó el monto
        tocaFlotante("Compra")                 // y una compra a medio anotar
        escribe("Monto total (S/)", "12.00")
        val p4a = intentaSeguir()
        comprobar("paso 4 frena: RUC de 10 cifras", "Compra 1: el RUC debe tener exactamente 11 dígitos" in p4a)
        comprobar("paso 4 frena: boleta a medias", "Compra 1: falta la serie o el correlativo de la boleta" in p4a)
        comprobar("y abre la compra 1 para corregirla", buscar(By.text("Quitar la compra 1")) != null)
        escribe("RUC del vendedor", "10456789123"); escribe("Número", "000143")
        val p4b = intentaSeguir()
        comprobar("paso 4 frena: falta el monto de la compra 2", "Compra 2: falta el monto total" in p4b)
        comprobar("y abre la compra 2", buscar(By.text("Quitar la compra 2")) != null)
        escribe("Monto total (S/)", "141.85")
        val p4c = intentaSeguir()
        comprobar("paso 4 frena: la compra 3 a medias", "Compra 3: falta la fecha" in p4c || "Compra 3: falta la descripción" in p4c)
        abrirCompra(3)
        toca("Quitar la compra 3")              // mejor la quita: era un error
        comprobar("el aviso rojo se va solo al quitarla", device.findObject(By.textStartsWith("Para seguir")) == null)
        val deshacer = device.findObject(By.text("Deshacer"))?.visibleBounds
        val seguir = device.findObject(By.textStartsWith("Siguiente"))?.visibleBounds
        comprobar("«Deshacer» no tapa «Siguiente»", deshacer != null && seguir != null && deshacer.bottom <= seguir.top)
        captura("quitar-compra")
        comprobar("sin la compra 3, el paso 4 deja seguir", intentaSeguir().isEmpty())
    }

    /** Toca «Siguiente» y devuelve lo que la app dice para no dejar seguir (vacío si dejó). */
    private fun intentaSeguir(): String = intentar("tocar «Siguiente»") {
        val antes = device.findObject(By.textStartsWith("Paso "))?.text.orEmpty()
        (device.findObject(By.textStartsWith("Siguiente")) ?: error("No hay «Siguiente»")).click(); espera(600)
        val frena = (device.findObject(By.textStartsWith("Para seguir")) ?: device.findObject(By.textStartsWith("Revisa antes de seguir")))?.text.orEmpty()
        anotar("   «Siguiente» en «${antes.substringBefore(".")}» → ${frena.ifEmpty { "deja seguir" }}")
        frena
    }

    /** Lo que dice el paso 5 (Revisar): lo que falta (errores) y el detalle de «por revisar» (avisos). */
    private fun revisar(nombre: String): List<String> {
        irAPaso(5)
        alPrincipio()
        intentar("ver el detalle") { buscar(By.text("Ver el detalle"), bajar = false)?.click() }; espera()
        val vistos = LinkedHashSet<String>()
        repeat(10) {
            // Lo que se redibuja mientras se lee se salta: en la vuelta siguiente se vuelve a leer.
            device.findObjects(By.pkg(paquete)).mapNotNullTo(vistos) { o -> runCatching { o.text }.getOrNull()?.takeIf { it.isNotBlank() } }
            device.swipe(device.displayWidth / 2, device.displayHeight * 7 / 10, device.displayWidth / 2, device.displayHeight * 3 / 10, 25); espera(250)
        }
        captura(nombre)
        val hallazgos = vistos.filter { it.startsWith("Compra ") && ":" in it || it.startsWith("El ") || it.startsWith("La ") || it.startsWith("Falta") || it.startsWith("Queda") || it.startsWith("Las ") }
        for (h in hallazgos) anotar("   · $h")
        return vistos.toList()
    }

    // ------------------------------------------------------------ cómo toca una persona

    private fun espera(ms: Long = 350) { device.waitForIdle(); Thread.sleep(ms) }

    /** Compose redibuja seguido: si lo encontrado ya no está, se busca y se intenta de nuevo. */
    private fun <T> intentar(que: String, accion: () -> T): T {
        repeat(2) { try { return accion() } catch (e: androidx.test.uiautomator.StaleObjectException) { espera(400) } }
        return try { accion() } catch (e: Throwable) { throw IllegalStateException("$que: ${e.javaClass.simpleName} ${e.message ?: ""}".trim(), e) }
    }

    private fun alPrincipio() = repeat(8) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 10, device.displayWidth / 2, device.displayHeight * 8 / 10, 15) }.also { espera() }

    /** Lo busca en la pantalla y, si no está, baja de a poco (y si no aparece, vuelve arriba y busca de nuevo). */
    private fun buscar(sel: BySelector, bajar: Boolean = true): UiObject2? {
        device.findObject(sel)?.let { return it }
        if (!bajar) return null
        repeat(2) { vuelta ->
            if (vuelta == 1) alPrincipio()
            repeat(8) {
                device.findObject(sel)?.let { return it }
                device.swipe(device.displayWidth / 2, device.displayHeight * 7 / 10, device.displayWidth / 2, device.displayHeight * 4 / 10, 30); espera(250)
            }
        }
        return device.findObject(sel)
    }

    /** Si quedó muy abajo, lo sube a la mitad de la pantalla (debajo puede venir su campo). */
    private fun aLaVista(o: UiObject2, sel: BySelector): UiObject2 {
        if (o.visibleBounds.bottom < device.displayHeight * 0.6) return o
        device.swipe(device.displayWidth / 2, device.displayHeight * 7 / 10, device.displayWidth / 2, device.displayHeight * 4 / 10, 30); espera(250)
        return device.findObject(sel) ?: o
    }

    private fun toca(texto: String) = intentar("tocar «$texto»") {
        val o = buscar(By.text(texto)) ?: error("No encontré «$texto»")
        o.click(); espera()
    }

    /** El botón flotante: está siempre a la vista, no hace falta bajar. */
    private fun tocaFlotante(texto: String) = intentar("tocar «$texto»") { (device.findObject(By.text(texto)) ?: error("No encontré el botón «$texto»")).click(); espera(600) }

    /** ¿Está en la misma columna que el rótulo? (dos campos pueden ir lado a lado). */
    private fun mismaColumna(o: UiObject2, r: Rect) = o.visibleBounds.left <= r.left + 60 && o.visibleBounds.right > r.left

    /** El campo de texto con su rótulo dentro (la «*» de obligatorio no cuenta). */
    private fun campoDe(rotulo: String): UiObject2 {
        val sel = By.clazz("android.widget.EditText").hasDescendant(By.text(Pattern.compile(Pattern.quote(rotulo) + "( \\*)?")))
        return aLaVista(buscar(sel) ?: error("No encontré el campo «$rotulo»"), sel)
    }

    private fun escribe(rotulo: String, valor: String) = intentar("escribir en «$rotulo»") { campoDe(rotulo).text = valor; espera(250) }

    /** Una fecha: abre el calendario, pasa a escribirla y la escribe (DDMMAAAA). */
    private fun fecha(rotulo: String, ddmmaaaa: String) = intentar("poner la fecha «$rotulo»") {
        escribirEnCalendario(rotulo, ddmmaaaa)
        toca("Usar esta fecha")
    }

    /** Escribe una fecha que no se debería poder elegir: «Usar esta fecha» queda apagado. Cierra con «Cancelar». */
    private fun fechaBloqueada(rotulo: String, ddmmaaaa: String): Boolean = intentar("probar la fecha «$rotulo»") {
        escribirEnCalendario(rotulo, ddmmaaaa)
        // El texto queda «enabled»; el que se apaga es el botón que lo contiene.
        val bloqueada = device.findObject(By.enabled(false).hasDescendant(By.text("Usar esta fecha"))) != null
        toca("Cancelar")
        bloqueada
    }

    private fun escribirEnCalendario(rotulo: String, ddmmaaaa: String) {
        val sel = By.text(rotulo)
        aLaVista(buscar(sel) ?: error("No encontré la fecha «$rotulo»"), sel)
        val r = (device.findObject(sel) ?: error("Se perdió «$rotulo»")).visibleBounds
        val caja = device.findObjects(By.clickable(true)).filter { it.visibleBounds.top >= r.bottom - 8 && mismaColumna(it, r) }.minByOrNull { it.visibleBounds.top }
            ?: error("No hay calendario debajo de «$rotulo»")
        caja.click(); espera()
        (device.wait(Until.findObject(By.desc("Cambiar al modo de entrada de texto")), 3_000) ?: error("No se abrió el calendario de «$rotulo»")).click(); espera()
        (device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3_000) ?: error("El calendario no dejó escribir")).text = ddmmaaaa
        espera(250)
    }

    private fun irAPaso(n: Int) = intentar("ir al paso $n") {
        alPrincipio()
        val tramo = device.wait(Until.findObject(By.descStartsWith("Paso $n:")), 3_000) ?: error("No encontré el paso $n")
        tramo.click(); espera(500)
    }

    /** Abre la ficha de la compra [n] (si ya está abierta, tocarla la cerraría). Las fichas empiezan arriba del paso. */
    private fun abrirCompra(n: Int) = intentar("abrir la compra $n") {
        alPrincipio()
        val cabecera = buscar(By.text("Compra $n")) ?: error("No encontré «Compra $n»")
        if (estadoDe(cabecera.visibleBounds) != "Abierta") { cabecera.click(); espera() }
    }

    /** Lo que TalkBack dice del estado («Abierta» / «Cerrada») del elemento que contiene ese lugar. */
    private fun estadoDe(lugar: Rect): String? {
        fun buscarEn(nodo: AccessibilityNodeInfo?): String? {
            if (nodo == null) return null
            val r = Rect().also { nodo.getBoundsInScreen(it) }
            if (!r.contains(lugar.centerX(), lugar.centerY())) return null
            for (i in 0 until nodo.childCount) buscarEn(nodo.getChild(i))?.let { return it }
            return nodo.stateDescription?.toString()
        }
        return buscarEn(instrumentacion.uiAutomation.rootInActiveWindow)
    }

    // ------------------------------------------------------------ informe

    private fun comprobar(que: String, bien: Boolean) { if (!bien) fallas++; anotar("${if (bien) "OK   " else "FALLA"} $que") }

    private fun anotar(linea: String) { Log.i("Escenarios", linea); informe.append(linea).append('\n') }

    private fun captura(nombre: String) {
        instrumentacion.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/escenario-$nombre.png").close()
        Thread.sleep(600)
    }

    private fun guardarInforme() {
        val (salida, entrada) = instrumentacion.uiAutomation.executeShellCommandRw("tee /data/local/tmp/escenarios.txt")
        ParcelFileDescriptor.AutoCloseOutputStream(entrada).use { it.write(informe.toString().toByteArray()) }
        ParcelFileDescriptor.AutoCloseInputStream(salida).use { it.readBytes() }
    }

}
