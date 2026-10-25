// perfil: genera el perfil de arranque de :app (baseline profile). Recorre la
// app en un teléfono conectado y anota qué código se usa al abrirla, para que
// Android lo compile por adelantado. Cómo generarlo: GenerarPerfil.kt.
// También revisa la accesibilidad de la app instalada: Revisor.kt.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "io.github.tmrainer.asistencia.perfil"
    compileSdk = 36

    defaultConfig {
        // El perfil se puede generar desde Android 9; la app sigue en minSdk 24.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// En el teléfono que esté conectado, no en un emulador administrado por Gradle.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    // Los chequeos del Accessibility Scanner de Google, para Revisor.kt.
    implementation("com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:4.1.1")
    implementation("com.google.guava:guava:33.3.1-android")   // la usa su API y no la expone
}

// La prueba abre la app por su applicationId (el de la app aparte, …generarperfil).
androidComponents {
    onVariants { v ->
        val cargador = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put("idApp", v.testedApks.map { cargador.load(it)?.applicationId })
    }
}
