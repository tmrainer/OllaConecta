// Grupo «Validación» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import io.github.tmrainer.asistencia.nucleo.validaciones.Severidad
import io.github.tmrainer.asistencia.nucleo.validaciones.contar
import io.github.tmrainer.asistencia.nucleo.validaciones.validarRendicion
import kotlin.test.Test

class ValidacionTest {

    @Test
    fun `El ejemplar completo no deja errores`() {
        val base = rendicionDelEjemplar()
        val r = base.copy(
            nombrePresidentaFirma = base.nombrePresidenta,
            dniPresidentaFirma = base.dniPresidenta,
            nombreTesorera = "Otra Ficticia",
            dniTesorera = "00000002"
        )
        val c = contar(validarRendicion(r))
        igual(c.errores, 0, "no debería haber errores: " + validarRendicion(r).filter { it.severidad == Severidad.ERROR })
    }

    @Test
    fun `DNI, RUC y celular mal formados dan error`() {
        val base = rendicionDelEjemplar()
        val r = base.copy(
            dniPresidenta = "123",
            celular = "812345678",
            egresos = listOf(base.egresos[0].copy(rucProveedor = "123"), base.egresos[1])
        )
        val hs = validarRendicion(r)
        ok(hs.any { it.campo == "dniPresidenta" && it.severidad == Severidad.ERROR }, "DNI")
        ok(hs.any { it.campo == "celular" && it.severidad == Severidad.ERROR }, "celular")
        ok(hs.any { it.severidad == Severidad.ERROR && "RUC" in it.mensaje }, "RUC")
    }

    @Test
    fun `Saldo de subsidio sin rendir es advertencia, no error`() {
        val r = rendicionDelEjemplar().copy(egresos = listOf(nuevoEgreso().copy(
            fechaCompra = "2026-09-10", descripcion = "Arroz", rucProveedor = "10000000001",
            boletaSerie = "E001", boletaCorrelativo = "1", montoTotalCent = 15000
        )))
        val saldo = validarRendicion(r).firstOrNull { "sin rendir" in it.mensaje }
        ok(saldo != null, "debe avisar del saldo")
        igual(saldo!!.severidad, Severidad.ADVERTENCIA)
    }

    @Test
    fun `Boleta repetida avisa, fecha fuera de rango da error`() {
        val base = rendicionDelEjemplar()
        val r = base.copy(egresos = listOf(
            base.egresos[0],
            base.egresos[1].copy(boletaSerie = "E001", boletaCorrelativo = "143", fechaCompra = "2026-09-01") // antes de la asignación
        ))
        val hs = validarRendicion(r)
        ok(hs.any { it.severidad == Severidad.ADVERTENCIA && "ya figura" in it.mensaje }, "duplicado")
        ok(hs.any { it.severidad == Severidad.ERROR && "anterior a la asignación" in it.mensaje }, "fecha")
    }

    @Test
    fun `Ninguna validación modifica la rendición`() {
        val r = rendicionDelEjemplar().copy(dniPresidenta = "123")
        val antes = r.copy()
        validarRendicion(r)
        igual(r, antes, "la rendición quedó intacta")
    }

    @Test
    fun `Un formato recién empezado no tiene errores, le faltan datos`() {
        val r = nuevaRendicion()
        val errores = validarRendicion(r).filter { it.severidad == Severidad.ERROR }
        ok(errores.size > 5, "hay datos por llenar")
        ok(errores.all { it.falta }, errores.filter { !it.falta }.joinToString(" | ") { it.mensaje })
        ok(validarRendicion(r.copy(dniPresidenta = "123")).any { it.campo == "dniPresidenta" && !it.falta },
            "un DNI mal escrito sí es por corregir")
    }
}
