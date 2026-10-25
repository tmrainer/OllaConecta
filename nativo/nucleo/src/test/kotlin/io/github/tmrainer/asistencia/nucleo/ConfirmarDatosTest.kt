// Lo escrito en el primer uso, confirmado con el DNI (ConfirmarDatos.kt).
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.dni.confirmarConDni
import kotlin.test.Test

class ConfirmarDatosTest {

    private val dni = "MARÍA ELENA LÓPEZ PEÑA"

    @Test
    fun `Coincide sin mirar tildes ni mayúsculas`() {
        ok(confirmarConDni("maria elena lopez peña", "12345678", dni, "12345678").coincide)
        ok(confirmarConDni("  María   López ", "12345678", dni, "12345678").coincide, "se puede escribir menos")
    }

    @Test
    fun `Una palabra que no está en el DNI`() {
        val c = confirmarConDni("María Lopes", "12345678", dni, "12345678")
        ok(!c.coincide)
        igual(c.faltanEnDni, listOf("Lopes"))
        ok(!c.numeroDistinto)
    }

    @Test
    fun `Otro número`() {
        val c = confirmarConDni("María López", "12345679", dni, "12345678")
        ok(!c.coincide)
        ok(c.numeroDistinto)
        igual(c.faltanEnDni, emptyList<String>())
    }
}
