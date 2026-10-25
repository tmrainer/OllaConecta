// Las entradas son JSON crudo, como los padrones viejos que llegan en un
// respaldo.
package io.github.tmrainer.asistencia.nucleo

import io.github.tmrainer.asistencia.nucleo.calculos.Origen
import io.github.tmrainer.asistencia.nucleo.calculos.desgloseDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.racionesDelDia
import io.github.tmrainer.asistencia.nucleo.calculos.recaudacionDelDiaCent
import io.github.tmrainer.asistencia.nucleo.modelo.VERSION_PADRON
import io.github.tmrainer.asistencia.nucleo.modelo.migrarPadron
import kotlinx.serialization.json.Json
import kotlin.test.Test

private fun json(texto: String) = Json.parseToJsonElement(texto)

class MigracionTest {

    @Test
    fun `Un día de la v2 conserva sus raciones escritas a mano, marcadas como legado`() {
        val v3 = migrarPadron(json("""{
            "version": 2,
            "afiliados": [{ "id": "a", "nombres": "Ana" }],
            "atenciones": [{
                "id": "at1", "fecha": "2026-09-10",
                "racionesNormales": 38, "racionesAyudaSocial": 12,
                "precioMenuNormalCent": 300, "precioMenuAyudaSocialCent": 100, "asistencias": []
            }]
        }"""))
        igual(v3.version, VERSION_PADRON)
        val at = v3.atenciones[0]
        val d = desgloseDelDia(at)
        igual(d.origen, Origen.LEGADO)
        igual(d.normal, 38)
        igual(d.ayudaSocial, 12)
        igual(d.total, 50, "siguen contando")
        igual(recaudacionDelDiaCent(at), 38L * 300 + 12L * 100, "y siguen valorándose")
    }

    @Test
    fun `La v1 pasa por v2 hasta v3 sin inventar precios`() {
        val v3 = migrarPadron(json("""{
            "version": 1,
            "afiliados": [{ "id": "a", "nombres": "Ana", "tipoDocumento": "sin_documento" }],
            "atenciones": [{ "id": "at1", "fecha": "2026-09-10", "racionesServidas": 50, "racionesCasoSocial": 12 }]
        }"""))
        val at = v3.atenciones[0]
        igual(at.legado?.racionesNormales, 38, "50 totales menos 12 de ayuda social")
        igual(at.legado?.racionesAyudaSocial, 12)
        igual(at.precioMenuNormalCent, null, "el precio nunca existió: no se inventa")
        igual(v3.afiliados[0].tipoAfiliado, "", "el tipo queda por elegir, no se supone")
        igual(v3.afiliados[0].tipoDocumento, "dni", "«sin documento» ya no existe")
    }

    @Test
    fun `Un día con asistencia marcada no hereda nada`() {
        val at = migrarPadron(json("""{
            "version": 2,
            "atenciones": [{
                "id": "at1", "fecha": "2026-09-10", "racionesNormales": 99,
                "asistencias": [{ "afiliadoId": "a", "tipoMenu": "normal" }]
            }]
        }""")).atenciones[0]
        igual(at.legado, null)
        igual(racionesDelDia(at), 1, "manda la asistencia")
    }

    @Test
    fun `Migrar algo ya migrado o basura no rompe nada`() {
        val ya = migrarPadron(migrarPadron(json("""{ "version": 1, "afiliados": [], "atenciones": [] }""")))
        igual(ya.version, VERSION_PADRON)
        igual(migrarPadron(null).afiliados.size, 0)
        igual(migrarPadron(json("\"basura\"")).atenciones.size, 0)
        igual(migrarPadron(json("{}")).version, VERSION_PADRON)
    }

    @Test
    fun `Quienes comieron sin estar en el padrón sobreviven a la migración`() {
        val at = migrarPadron(json("""{
            "version": 3,
            "atenciones": [{ "id": "at1", "fecha": "2026-09-10", "asistencias": [], "sinInscribir": { "normal": 2, "ayudaSocial": 1 } }]
        }""")).atenciones[0]
        igual(desgloseDelDia(at).total, 3)
    }
}
