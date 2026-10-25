package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.Config
import io.github.tmrainer.asistencia.nucleo.modelo.Egreso
import io.github.tmrainer.asistencia.nucleo.modelo.Perfil
import io.github.tmrainer.asistencia.nucleo.modelo.Rendicion
import io.github.tmrainer.asistencia.nucleo.modelo.UltimoCentro
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasEncabezado
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerenciasPerfil
import io.github.tmrainer.asistencia.nucleo.sugerencias.sugerirRucProveedor
import kotlin.test.Test

class SugerenciasTest {

    @Test
    fun `Perfil, la presidenta propone sus datos y sus firmas, la tesorera los suyos, apoyo nada`() {
        val base = Perfil(nombre = "Rosa Prueba Ficticia", dni = "00000001", celular = "900000001")
        val pres = sugerenciasPerfil(base.copy(rol = "presidenta"))
        igual(pres["nombrePresidenta"]?.valor, "Rosa Prueba Ficticia")
        igual(pres["dniPresidentaFirma"]?.valor, "00000001")
        igual(pres["celular"]?.procedencia, "de tu perfil")
        ok("nombreTesorera" !in pres)
        val tes = sugerenciasPerfil(base.copy(rol = "tesorera"))
        igual(tes.keys.sorted().joinToString(","), "dniTesorera,nombreTesorera")
        igual(sugerenciasPerfil(base.copy(rol = "apoyo")).size, 0)
        igual(sugerenciasPerfil(Perfil(nombre = "", rol = "presidenta", dni = "", celular = "")).size, 0, "sin datos no propone vacíos")
        igual(sugerenciasPerfil(null).size, 0)
    }

    @Test
    fun `Toda sugerencia viaja con su procedencia`() {
        val config = Config(ultimoCentro = UltimoCentro(guardadoEn = "2026-08-14", nombreCentro = "Centro X", codigoPca = "123"))
        val s = sugerenciasEncabezado(config)
        ok(s["nombreCentro"]?.procedencia?.isNotEmpty() == true, "sin procedencia no se muestra")
        ok("14-08-26" in s["nombreCentro"]!!.procedencia, "la procedencia dice de cuándo viene")
        igual(s["nombreCentro"]?.valor, "Centro X")
    }

    @Test
    fun `Sin datos previos no hay ninguna sugerencia (no se inventa nada)`() {
        igual(sugerenciasEncabezado(null).size, 0)
        igual(sugerenciasEncabezado(Config()).size, 0)
        igual(sugerirRucProveedor(emptyList(), "Mercado"), null)
        igual(sugerirRucProveedor(null, ""), null)
    }

    @Test
    fun `El RUC sugerido sale de una compra propia anterior`() {
        val rendiciones = listOf(Rendicion(egresos = listOf(
            Egreso(proveedorNombre = "Mercado Central", rucProveedor = "10000000001", fechaCompra = "2026-08-01"),
            Egreso(proveedorNombre = "mercado  central", rucProveedor = "10000000009", fechaCompra = "2026-09-01")
        )))
        val s = sugerirRucProveedor(rendiciones, "MERCADO CENTRAL")!!
        igual(s.valor, "10000000009", "la más reciente")
        ok("01-09-26" in s.procedencia)
    }
}
