// Grupo «Dinero» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.aCentimos
import io.github.tmrainer.asistencia.nucleo.modelo.aNumero
import io.github.tmrainer.asistencia.nucleo.modelo.formatearSoles
import kotlin.test.Test

class DineroTest {

    @Test
    fun `aCentimos convierte sin error de coma flotante`() {
        igual(aCentimos("209.85"), 20985L)
        igual(aCentimos("0.10"), 10L)
        igual(aCentimos("0.20"), 20L)
        igual(aCentimos("200"), 20000L)
        igual(aCentimos("1,50"), 150L, "acepta coma decimal")
        igual(aCentimos("  68.00 "), 6800L)
    }

    @Test
    fun `aCentimos devuelve null ante lo vacío o inválido, no adivina`() {
        igual(aCentimos(""), null)
        igual(aCentimos("   "), null)
        igual(aCentimos("abc"), null)
        igual(aCentimos("1.234"), null, "más de dos decimales no es un monto en soles")
        igual(aCentimos(null), null)
        igual(aCentimos("-5.00"), null)
    }

    @Test
    fun `0,1 + 0,2 en céntimos da exactamente 0,30`() {
        val a = aCentimos("0.10")!!
        val b = aCentimos("0.20")!!
        igual(a + b, 30L)
        igual(formatearSoles(a + b), "0.30")
        ok(0.1 + 0.2 != 0.3, "la coma flotante sí falla: por eso trabajamos en céntimos")
    }

    @Test
    fun `formatearSoles deja vacío lo que no existe`() {
        igual(formatearSoles(null), "")
        igual(formatearSoles(20985), "209.85")
        igual(formatearSoles(5), "0.05")
        igual(formatearSoles(0), "0.00")
    }

    @Test
    fun `aNumero acepta cantidades y rechaza texto`() {
        igual(aNumero("3.5"), 3.5)
        igual(aNumero("2"), 2.0)
        igual(aNumero("varios"), null)
        igual(aNumero(""), null)
    }
}
