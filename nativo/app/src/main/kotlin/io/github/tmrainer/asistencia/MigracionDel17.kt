// MigracionDel17.kt — la primera vez que la app nativa abre en un teléfono que
// tenía el APK 1.7 (la web dentro de Capacitor), trae sus datos. El 1.7 los
// guardaba en el localStorage del WebView, del origen https://localhost; un
// WebView oculto abre ese mismo origen (la página la da esta clase, no hay red)
// y lee las claves b1.* (los datos) y pi3.* (lo de este teléfono). La
// traducción es de :nucleo (MigracionWeb.kt). Se hace una sola vez y el
// localStorage no se borra: si algo falla, la próxima vez se intenta de nuevo.
package io.github.tmrainer.asistencia

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.tmrainer.asistencia.nucleo.almacen.leerDatosWeb
import io.github.tmrainer.asistencia.nucleo.modelo.JSON
import io.github.tmrainer.asistencia.nucleo.respaldo.LecturaRespaldo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class MigracionDel17(private val app: OllaConectaApp) {

    // Fuera de filesDir/datos: «Borrar todo» no debe volver a traer lo del 1.7.
    private val marca = File(app.filesDir, "migracion-1.7.hecha")

    private fun anotar() { marca.createNewFile() }

    /** ¿Hay que intentar traer datos del 1.7? Nunca encima de datos que ya tiene la app nativa. */
    fun pendiente(): Boolean {
        if (marca.exists()) return false
        if (!File(app.applicationInfo.dataDir, "app_webview").exists() || app.estado.hayDatosGuardados()) {
            anotar()
            return false
        }
        return true
    }

    /**
     * Lee el localStorage del 1.7 y lo trae. [alTerminar] recibe el texto
     * para el aviso de abajo, o null si no hubo nada que decir.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun traer(alTerminar: (String?) -> Unit) {
        val web = WebView(app)
        var terminado = false
        fun terminar(mensaje: String?) {
            if (terminado) return
            terminado = true
            web.destroy()
            alTerminar(mensaje)
        }
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            // Una página vacía en https://localhost (el origen del 1.7); nada sale a la red.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                if (request.url.host == "localhost" && request.url.path.let { it == null || it == "/" })
                    WebResourceResponse("text/html", "utf-8", "<!doctype html><title>OllaConecta</title>".byteInputStream())
                else WebResourceResponse("text/plain", "utf-8", 404, "No", emptyMap(), "".byteInputStream())

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(LEER) { resultado -> terminar(aplicar(resultado)) }
            }
        }
        web.loadUrl("https://localhost/")
        // Si el WebView no responde, se sigue sin marca: se intenta al abrir otra vez.
        Handler(Looper.getMainLooper()).postDelayed({ terminar(null) }, 10_000)
    }

    /** Aplica lo leído; devuelve el texto del aviso. Anota la marca solo si todo se guardó. */
    private fun aplicar(resultado: String?): String? {
        // evaluateJavascript devuelve el valor en JSON: una cadena con el JSON de las claves.
        val crudos = try {
            val texto = (JSON.parseToJsonElement(resultado ?: "null") as? JsonPrimitive)?.takeIf { it.isString }?.content
            (texto?.let { JSON.parseToJsonElement(it) } as? JsonObject)?.mapValues { (it.value as JsonPrimitive).content }
        } catch (e: SerializationException) { null } ?: return null
        app.preferencias.traerDeLaWeb(crudos)
        val datos = leerDatosWeb(crudos)
        val lista = datos.lectura as? LecturaRespaldo.Lista
        if (lista == null) { anotar(); return null }   // el 1.7 no tenía datos de la olla
        if (!app.estado.restaurar(lista)) return "No se pudieron traer tus datos de la versión anterior. Se intentará otra vez al abrir la app."
        datos.ultimaCopia?.let { app.estado.registrarCopia(it) }
        anotar()
        return "Ya están aquí tus datos de la versión anterior: ${lista.revision.resumen}." +
            if (datos.ilegibles.isNotEmpty()) " Una parte no se pudo leer y quedó sin traer." else ""
    }

    private companion object {
        const val LEER = "(function(){var o={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
            "if(k.indexOf('b1.')===0||k.indexOf('pi3.')===0)o[k]=localStorage.getItem(k);}return JSON.stringify(o);})()"
    }
}
