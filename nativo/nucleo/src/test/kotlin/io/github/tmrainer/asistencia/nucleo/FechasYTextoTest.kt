// Grupo «Fechas y texto» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.aDdMmAa
import io.github.tmrainer.asistencia.nucleo.modelo.aFecha
import io.github.tmrainer.asistencia.nucleo.modelo.cuantos
import io.github.tmrainer.asistencia.nucleo.modelo.esIso
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.normalizarNombre
import kotlin.test.Test

class FechasYTextoTest {

    @Test
    fun `Los plurales se escriben enteros, no persona(s)`() {
        igual(cuantos(1, "persona"), "1 persona")
        igual(cuantos(3, "persona"), "3 personas")
        igual(cuantos(0, "ración", "raciones"), "0 raciones")
    }

    @Test
    fun `Las fechas se muestran como en el papel`() {
        igual(aDdMmAa("2026-09-14"), "14-09-26")
        igual(aDdMmAa(""), "")
        igual(aDdMmAa("no es fecha"), "")
        ok(esIso("2026-09-14"))
        ok(!esIso("14-09-2026"))
    }

    @Test
    fun `Una fecha que no existe no se corrige sola`() {
        // Diferencia documentada con la web (que rueda al 2 de marzo).
        igual(aFecha("2024-02-31"), null)
        ok(aFecha("2024-02-29") != null, "bisiesto")
    }

    @Test
    fun `Normalizar nombres ignora tildes, mayúsculas y dobles espacios`() {
        igual(normalizarNombre("  MARÍA   José "), "maria jose")
        igual(normalizarNombre("Maria Jose"), "maria jose")
        igual(nombreCompleto(Afiliado(apellidoPaterno = "Pérez", apellidoMaterno = "", nombres = "Ana")), "Pérez Ana")
    }
}
