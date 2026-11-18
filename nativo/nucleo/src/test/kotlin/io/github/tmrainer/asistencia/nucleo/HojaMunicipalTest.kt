// Cargar la hoja de la Municipalidad: la primera arma
// el padrón; las siguientes dicen quién entra, quién vuelve, quién sigue y
// quién sale, y solo se aplica lo elegido. Datos inventados.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.afiliadosActivos
import io.github.tmrainer.asistencia.nucleo.importar.prepararImportacion
import io.github.tmrainer.asistencia.nucleo.modelo.Padron
import io.github.tmrainer.asistencia.nucleo.modelo.nombreCompleto
import io.github.tmrainer.asistencia.nucleo.modelo.nuevoAfiliado
import io.github.tmrainer.asistencia.nucleo.padron.EleccionHoja
import io.github.tmrainer.asistencia.nucleo.padron.agregarDeLaHoja
import io.github.tmrainer.asistencia.nucleo.padron.aplicarHoja
import io.github.tmrainer.asistencia.nucleo.padron.compararConHoja
import io.github.tmrainer.asistencia.nucleo.padron.inscribirEnLaOlla
import io.github.tmrainer.asistencia.nucleo.padron.pendientesDeRegistro
import io.github.tmrainer.asistencia.nucleo.padron.motivoNoFigura
import io.github.tmrainer.asistencia.nucleo.padron.retirarPorLaHoja
import io.github.tmrainer.asistencia.nucleo.padron.sociasSegunLaHoja
import io.github.tmrainer.asistencia.nucleo.padron.ultimaHoja
import io.github.tmrainer.asistencia.nucleo.padron.unirASusSocias
import org.junit.jupiter.api.Test

class HojaMunicipalTest {

    private val cabecera = "Apellido paterno;Apellido materno;Nombres;DNI;Grupo de edad;Tipo"

    private fun hoja(vararg filas: String) = prepararImportacion((listOf(cabecera) + filas).joinToString("\n"), Padron(), "2026-10-20")

    /** Carga una hoja aceptando todo lo que propone. */
    private fun cargar(padron: Padron, fecha: String, vararg filas: String): Padron {
        val c = compararConHoja(padron, hoja(*filas))
        return aplicarHoja(padron, fecha, EleccionHoja(c.entran, c.vuelven, c.salen, siguen = c.siguen), c.enLaHoja, "2026-10-20T10:00:00.000Z")
    }

    @Test
    fun `La primera hoja arma el padrón`() {
        val p = cargar(Padron(), "2026-10-01",
            "Ficticia;Uno;Ana;00000011;adulto;habitual",
            "Ficticia;Dos;Bea;00000012;adulto;habitual")
        igual(p.afiliados.size, 2)
        ok(p.afiliados.all { it.altaEn == "2026-10-01" && it.origenDato == "importado" }, "alta con la fecha de la hoja")
        val h = ultimaHoja(p)!!
        igual(h.fecha, "2026-10-01"); igual(h.personas, 2); igual(h.agregados.size, 2); igual(h.retirados.size, 0)
    }

    @Test
    fun `Una hoja nueva dice quién entra, quién sigue y quién sale`() {
        val p1 = cargar(Padron(), "2026-10-01",
            "Ficticia;Uno;Ana;00000011;adulto;habitual",
            "Ficticia;Dos;Bea;00000012;adulto;habitual")
        val c = compararConHoja(p1, hoja(
            "Ficticia;Uno;Ana;00000011;adulto;habitual",
            "Ficticia;Tres;Ceci;00000013;adulto;habitual"))
        igual(c.entran.map { it.afiliado.numeroDocumento }, listOf("00000013"))
        igual(c.siguen.map { it.numeroDocumento }, listOf("00000011"))
        igual(c.salen.map { it.numeroDocumento }, listOf("00000012"))
        ok(c.hayCambios)

        val p2 = aplicarHoja(p1, "2026-11-01", EleccionHoja(c.entran, c.vuelven, c.salen), c.enLaHoja)
        val bea = p2.afiliados.first { it.numeroDocumento == "00000012" }
        ok(!bea.activo); igual(bea.bajaEn, "2026-11-01"); igual(bea.motivoBaja, motivoNoFigura("2026-11-01"))
        igual(p2.afiliados.size, 3, "las bajas no borran")
        // Bea sigue contando en octubre, no desde noviembre.
        ok(afiliadosActivos(p2, "2026-10-15").any { it.id == bea.id })
        ok(afiliadosActivos(p2, "2026-11-02").none { it.id == bea.id })
        val h = ultimaHoja(p2)!!
        igual(h.agregados.size, 1); igual(h.retirados, listOf(bea.id))
    }

    @Test
    fun `Solo se aplica lo elegido`() {
        val p1 = cargar(Padron(), "2026-10-01", "Ficticia;Uno;Ana;00000011;adulto;habitual", "Ficticia;Dos;Bea;00000012;adulto;habitual")
        val c = compararConHoja(p1, hoja("Ficticia;Tres;Ceci;00000013;adulto;habitual"))
        igual(c.salen.size, 2)
        // Nadie entra y solo sale Ana.
        val p2 = aplicarHoja(p1, "2026-11-01", EleccionHoja(emptyList(), emptyList(), c.salen.filter { it.nombres == "Ana" }), c.enLaHoja)
        igual(p2.afiliados.size, 2)
        igual(p2.afiliados.count { it.activo }, 1)
    }

    @Test
    fun `Quien vuelve en la hoja se reactiva, y la misma hoja dos veces no cambia nada`() {
        val p1 = cargar(Padron(), "2026-10-01", "Ficticia;Uno;Ana;00000011;adulto;habitual")
        val p2 = cargar(p1, "2026-11-01", "Ficticia;Dos;Bea;00000012;adulto;habitual")
        ok(!p2.afiliados.first { it.nombres == "Ana" }.activo)
        val c = compararConHoja(p2, hoja("Ficticia;Uno;Ana;00000011;adulto;habitual", "Ficticia;Dos;Bea;00000012;adulto;habitual"))
        igual(c.vuelven.map { it.nombres }, listOf("Ana"))
        val p3 = aplicarHoja(p2, "2026-12-01", EleccionHoja(c.entran, c.vuelven, c.salen), c.enLaHoja)
        val ana = p3.afiliados.first { it.nombres == "Ana" }
        ok(ana.activo); igual(ana.bajaEn, ""); igual(ana.motivoBaja, "")
        ok(!compararConHoja(p3, hoja("Ficticia;Uno;Ana;00000011;adulto;habitual", "Ficticia;Dos;Bea;00000012;adulto;habitual")).hayCambios)
    }

    @Test
    fun `Mismo DNI con otro nombre se ofrece, no se cambia solo`() {
        val p1 = cargar(Padron(), "2026-10-01", "Ficticia;Uno;Ana;00000011;adulto;habitual")
        val c = compararConHoja(p1, hoja("Ficticia;Uno;Ana María;00000011;adulto;habitual"))
        igual(c.nombreDistinto.size, 1)
        igual(c.entran.size, 0); igual(c.salen.size, 0)
        val sin = aplicarHoja(p1, "2026-11-01", EleccionHoja(emptyList(), emptyList(), emptyList()), c.enLaHoja)
        igual(sin.afiliados[0].nombres, "Ana")
        val con = aplicarHoja(p1, "2026-11-01", EleccionHoja(emptyList(), emptyList(), emptyList(), c.nombreDistinto), c.enLaHoja)
        igual(con.afiliados[0].nombres, "Ana María")
    }

    @Test
    fun `Una fila repetida en la hoja no entra dos veces`() {
        val c = compararConHoja(Padron(), hoja("Ficticia;Uno;Ana;00000011;adulto;habitual", "Ficticia;Uno;Ana;00000011;adulto;habitual"))
        igual(c.entran.size, 1)
        igual(c.repetidas.size, 1)
        igual(c.enLaHoja, 1)
    }

    @Test
    fun `Copiar y retirar a mano quedan en la hoja de esa fecha`() {
        val a = nuevoAfiliado("2026-10-20").copy(apellidoPaterno = "Ficticia", nombres = "Ana", numeroDocumento = "00000011", grupoEtarioManual = "adulto", tipoAfiliado = "habitual")
        val p1 = agregarDeLaHoja(Padron(), a, "2026-10-15")
        val b = nuevoAfiliado("2026-10-20").copy(apellidoPaterno = "Ficticia", nombres = "Bea", numeroDocumento = "00000012", grupoEtarioManual = "adulto", tipoAfiliado = "habitual")
        val p2 = agregarDeLaHoja(p1, b, "2026-10-15")
        igual(p2.hojasMunicipales.size, 1, "misma fecha, misma hoja")
        igual(p2.hojasMunicipales[0].agregados.size, 2)
        igual(p2.afiliados.map { it.altaEn }, listOf("2026-10-15", "2026-10-15"))
        val p3 = retirarPorLaHoja(p2, a.id, "2026-11-01")
        igual(p3.hojasMunicipales.size, 2)
        igual(p3.hojasMunicipales[1].retirados, listOf(a.id))
        val ana = p3.afiliados.first { it.id == a.id }
        ok(!ana.activo); igual(ana.bajaEn, "2026-11-01")
        igual(nombreCompleto(ana), "Ficticia Ana")
    }

    @Test
    fun `Quien la olla inscribió sigue pendiente hasta que la hoja la trae`() {
        val p1 = cargar(Padron(), "2026-10-01", "Ficticia;Uno;Ana;00000011;adulto;habitual")
        ok(p1.afiliados.all { it.registroMunicipal == "registrada" && it.registroMunicipalEn == "2026-10-01" })
        // Llega Bea a la olla: se inscribe aquí, falta la Municipalidad.
        val bea = nuevoAfiliado("2026-10-10").copy(apellidoPaterno = "Ficticia", nombres = "Bea", numeroDocumento = "00000012", grupoEtarioManual = "adulto", tipoAfiliado = "habitual")
        val p2 = inscribirEnLaOlla(p1, bea)
        igual(pendientesDeRegistro(p2).map { it.nombres }, listOf("Bea"))
        // Una hoja que todavía no la trae: Bea no sale, sigue pendiente.
        val c = compararConHoja(p2, hoja("Ficticia;Uno;Ana;00000011;adulto;habitual"))
        igual(c.salen.size, 0); igual(c.pendientes.map { it.nombres }, listOf("Bea"))
        val p3 = cargar(p2, "2026-11-01", "Ficticia;Uno;Ana;00000011;adulto;habitual")
        ok(p3.afiliados.first { it.nombres == "Bea" }.activo)
        igual(pendientesDeRegistro(p3).size, 1)
        // La hoja siguiente ya la trae: queda registrada con esa fecha.
        val c2 = compararConHoja(p3, hoja("Ficticia;Uno;Ana;00000011;adulto;habitual", "Ficticia;Dos;Bea;00000012;adulto;habitual"))
        igual(c2.confirman.map { it.nombres }, listOf("Bea")); igual(c2.entran.size, 0)
        val p4 = cargar(p3, "2026-12-01", "Ficticia;Uno;Ana;00000011;adulto;habitual", "Ficticia;Dos;Bea;00000012;adulto;habitual")
        val b = p4.afiliados.first { it.nombres == "Bea" }
        igual(b.registroMunicipal, "registrada"); igual(b.registroMunicipalEn, "2026-12-01")
        igual(pendientesDeRegistro(p4).size, 0)
    }

    @Test
    fun `La hoja solo propone titulares que quedan como socias en el padrón`() {
        val texto = """Apellido paterno;Nombres;DNI;Socio;Usuario
            |Ficticia;Ana;00000021;X;
            |Ficticia;Bea;00000022;;X""".trimMargin()
        val hoja = prepararImportacion(texto, Padron(), "2026-10-20")
        for (condicion in listOf("", "familiar")) {
            val ana = nuevoAfiliado().copy(numeroDocumento = "00000021", condicion = condicion)
            val padron = Padron(afiliados = listOf(ana))
            val comparacion = compararConHoja(padron, hoja)
            igual(comparacion.entran.single().afiliado.nombres, "Bea")
            ok(sociasSegunLaHoja(padron, hoja, comparacion.entran).isEmpty(),
                "La hoja no cambia la condición de Ana ni puede proponerla como titular")
        }
    }

    @Test
    fun `La hoja propone la socia de cada familiar por el orden de las filas`() {
        val titulos = "Apellido paterno;Nombres;DNI;Grupo de edad;Socio;Usuario;Caso social"
        fun leer(vararg filas: String) = prepararImportacion((listOf(titulos) + filas).joinToString("\n"), Padron(), "2026-10-20")
        val h = leer(
            "Ficticia;Hijo Suelto;00000020;nino;;X;",          // familiar antes de cualquier socia
            "Ficticia;Ana;00000021;adulto;X;;",
            "Ficticia;Hijo de Ana;00000022;nino;;X;",
            "Ficticia;Otro de Ana;00000023;nino;;X;",
            "Ficticia;Caso;00000024;adulto;;;X",               // corta la familia
            "Ficticia;Tras el Caso;00000025;nino;;X;",
            "Ficticia;Bea;00000026;adulto;X;;",
            "Ficticia;Hijo de Bea;00000027;nino;;X;")
        val k = compararConHoja(Padron(), h)
        val socias = sociasSegunLaHoja(Padron(), h, k.entran)
        fun id(nombres: String) = k.entran.first { it.afiliado.nombres == nombres }.afiliado.id
        igual(socias.size, 3)
        igual(socias[id("Hijo de Ana")]?.id, id("Ana")); igual(socias[id("Otro de Ana")]?.id, id("Ana"))
        igual(socias[id("Hijo de Bea")]?.id, id("Bea"))
        ok(id("Hijo Suelto") !in socias && id("Tras el Caso") !in socias, "sin socia encima no se propone nada")

        // Sin aceptar la propuesta nadie queda unido; aceptada, solo los propuestos, y sin parentesco inventado.
        ok(k.entran.all { it.afiliado.titularId.isEmpty() }, "la lectura sola no une a nadie")
        val p = aplicarHoja(Padron(), "2026-10-01", EleccionHoja(unirASusSocias(k.entran, socias), emptyList(), emptyList()), k.enLaHoja)
        igual(p.afiliados.count { it.titularId.isNotEmpty() }, 3)
        ok(p.afiliados.all { it.parentesco.isEmpty() }, "el parentesco no viene en la hoja")

        // Si Ana no entra (se desmarcó), los suyos quedan sin socia.
        val sinAna = k.entran.filter { it.afiliado.nombres != "Ana" }
        igual(sociasSegunLaHoja(Padron(), h, sinAna).size, 1)

        // Ana ya está en el padrón: los suyos que entran se unen a la de aquí.
        val conAna = aplicarHoja(Padron(), "2026-10-01", EleccionHoja(k.entran.filter { it.afiliado.nombres == "Ana" }, emptyList(), emptyList()), 1)
        val k2 = compararConHoja(conAna, h)
        val socias2 = sociasSegunLaHoja(conAna, h, k2.entran)
        igual(socias2[k2.entran.first { it.afiliado.nombres == "Hijo de Ana" }.afiliado.id]?.id, conAna.afiliados[0].id)
    }
}
