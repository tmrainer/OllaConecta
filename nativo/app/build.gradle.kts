// app: la app Android (Jetpack Compose). Las pantallas observan el Estado de
// :nucleo; nada de lógica de negocio vive aquí.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.tmrainer.asistencia"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.tmrainer.asistencia"
        minSdk = 24
        targetSdk = 36
        // Por encima del APK 1.7 (versionCode 11), para instalarse ENCIMA y
        // traer sus datos (MigracionDel17). Del 12 al 19 quedan para arreglos del 1.x.
        versionCode = 20
        versionName = "2.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    // La firma tiene que ser la MISMA que la del APK 1.7 instalado, o Android
    // no deja instalar encima. Con nativo/firma.properties (mismo formato que
    // mobile/firma.properties) se firma con la clave
    // final; sin él, con la de depuración de esta máquina, como el 1.7.
    val firma = Properties()
    val archivoFirma = rootProject.file("firma.properties")
    if (archivoFirma.exists()) archivoFirma.inputStream().use { firma.load(it) }
    signingConfigs {
        if (archivoFirma.exists()) {
            create("final") {
                storeFile = file(firma.getProperty("archivo"))
                storePassword = firma.getProperty("clave")
                keyAlias = firma.getProperty("alias")
                keyPassword = firma.getProperty("claveAlias")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (archivoFirma.exists()) "final" else "debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        // java.time en API 24 y 25 (nucleo usa LocalDate).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":nucleo"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // La cámara (leer el DNI y los QR), autorizada el 24-10-2026: CameraX para el
    // visor y ML Kit de los servicios de Google Play para leer. Los modelos los
    // instala el teléfono (com.google.mlkit.vision.DEPENDENCIES): no pesan en el APK.
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
