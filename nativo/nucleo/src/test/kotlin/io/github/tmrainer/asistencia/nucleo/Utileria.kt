// Utileria.kt — ayudas compartidas por las pruebas. Porte de la utilería de
// web/js/casos-prueba.js: ok(), igual() y la rendición del ejemplar.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.Periodo
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevaRendicion
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoEgreso
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun ok(condicion: Boolean, mensaje: String = "La condición no se cumplió") = assertTrue(condicion, mensaje)

fun <T> igual(obtenido: T, esperado: T, mensaje: String = "Valor distinto") =
    assertEquals(esperado, obtenido, mensaje)

/**
 * Rendición con la MISMA ARITMÉTICA que el ejemplar llenado a mano (200.00 /
 * 9.85 / 209.85), con todos los datos identificativos sustituidos por valores
 * ficticios.
 */
fun rendicionDelEjemplar(): Rendicion = nuevaRendicion().copy(
    tipoCentro = "olla_comun",
    nombreCentro = "Centro de prueba",
    codigoPca = "0000000000",
    periodo = Periodo(tipo = "mensual", inicio = "2026-09-01", fin = "2026-09-30", etiqueta = "Setiembre"),
    fechaRendicion = "2026-09-14",
    nombrePresidenta = "Nombre Ficticio",
    dniPresidenta = "00000001",
    celular = "900000000",
    montoSubsidioCent = 20000,          // S/ 200.00
    fechaAsignacion = "2026-09-07",
    egresos = listOf(
        nuevoEgreso().copy(
            fechaCompra = "2026-09-10", descripcion = "Saco de papa",
            cantidad = 1.0, unidadMedida = "saco", rucProveedor = "10000000001",
            boletaSerie = "E001", boletaCorrelativo = "143",
            precioUnitarioCent = 6800, montoTotalCent = 6800
        ),
        nuevoEgreso().copy(
            fechaCompra = "2026-09-13", descripcion = "Carne",
            cantidadVarios = true, unidadMedida = "varios", rucProveedor = "10000000002",
            boletaSerie = "0001", boletaCorrelativo = "000080",
            precioUnitarioVarios = true, montoTotalCent = 14185
        )
    )
)

/** Una fila de egreso solo con monto, como `Object.assign(nuevoEgreso(), { montoTotalCent })`. */
fun egresoCon(montoTotalCent: Long): Egreso = nuevoEgreso().copy(montoTotalCent = montoTotalCent)
