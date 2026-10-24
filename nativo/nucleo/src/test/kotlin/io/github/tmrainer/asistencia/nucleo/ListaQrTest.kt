// Grupo «Lista por QR» de web/js/casos-prueba.js.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.modelo.Afiliado
import io.github.tmrainer.asistencia.nucleo.modelo.Asistencia
import io.github.tmrainer.asistencia.nucleo.qr.MarcaLista
import io.github.tmrainer.asistencia.nucleo.qr.codificarListaQr
import io.github.tmrainer.asistencia.nucleo.qr.decodificarParteQr
import io.github.tmrainer.asistencia.nucleo.qr.sumarMarcasRecibidas
import kotlin.test.Test

class ListaQrTest {

    @Test
    fun `Ida y vuelta - lo que sale en el QR vuelve igual, solo con cifras`() {
        val marcas = listOf(MarcaLista("00000001", "normal"), MarcaLista("00000002", "ayuda_social"))
        val partes = codificarListaQr("2026-10-12", marcas)
        igual(partes.size, 1)
        ok(partes[0].all { it in '0'..'9' }, "solo cifras (el QR más chico)")
        val r = decodificarParteQr(partes[0])!!
        igual(r.fecha, "2026-10-12")
        igual(r.parte, 1)
        igual(r.total, 1)
        igual(r.marcas, marcas)
    }

    @Test
    fun `Muchas personas - se parte en varios códigos, sin DNI de 8 cifras no va`() {
        val marcas = List(100) { MarcaLista((10000000 + it).toString(), "normal") } + MarcaLista("123", "normal")
        val partes = codificarListaQr("2026-10-12", marcas)
        igual(partes.size, 2)
        igual(partes.sumOf { decodificarParteQr(it)!!.marcas.size }, 100, "el DNI corto no entra")
        igual(codificarListaQr("2026-10-12", emptyList()).size, 0)
        igual(codificarListaQr("ayer", marcas).size, 0)
    }

    @Test
    fun `Un QR que no es de esta app, o mal leído, no se usa`() {
        for (t in listOf("", null, "https://ejemplo.pe", "81", "8120261012010100100000001", "812026101201010010000000019")) {
            igual(decodificarParteQr(t), null, t.toString())
        }
    }

    @Test
    fun `Sumar lo recibido - solo quien falta, dice cuántos ya estaban y cuántos no están en el padrón`() {
        val personas = listOf(Afiliado(id = "a", numeroDocumento = "00000001"), Afiliado(id = "b", numeroDocumento = "00000002"))
        val r = sumarMarcasRecibidas(listOf(
            MarcaLista("00000001", "ayuda_social"), MarcaLista("00000002", "normal"),
            MarcaLista("00000009", "normal"), MarcaLista("00000001", "normal")
        ), personas, setOf("b"))
        igual(r.agregar, listOf(Asistencia("a", "ayuda_social")))
        igual(r.yaEstaban, 1)
        igual(r.desconocidos, 1)
    }
}
