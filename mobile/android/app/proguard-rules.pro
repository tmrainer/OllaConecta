# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Trazas de error legibles (número de línea) en el APK optimizado.
-keepattributes SourceFile,LineNumberTable

# Los complementos propios (LectorTextoPlugin, CompartirArchivoPlugin) los
# conserva la regla de Capacitor (toda clase que extiende Plugin); esto es
# por si esa regla cambia en una versión futura.
-keep public class io.github.tmrainer.asistencia.** extends com.getcapacitor.Plugin { *; }

# @capacitor-firebase/authentication trae código para entrar con Facebook,
# Apple, etc., sin incluir esas librerías; la app solo usa Google. R8 se
# frena al no encontrarlas: se le dice que no importan.
-dontwarn com.facebook.**
