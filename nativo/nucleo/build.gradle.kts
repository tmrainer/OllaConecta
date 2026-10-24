// nucleo: lógica pura. Sin android.*, sin red, sin DOM. Se prueba en la JVM.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Respaldo real (nunca versionado): -Dnativo.respaldoReal=/ruta/respaldo.json
    System.getProperty("nativo.respaldoReal")?.let { systemProperty("nativo.respaldoReal", it) }
    testLogging { events("failed"); showStandardStreams = false }
}
