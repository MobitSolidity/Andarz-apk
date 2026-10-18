// :engine — pure Kotlin/JVM. No Android, no Compose, no network, no project dependencies (K4).
// Every rule and formula of the constitution lives here (law 2).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main { kotlin.srcDirs("src/main/kotlin") }
    test { kotlin.srcDirs("src/test/kotlin") }
}

dependencies {
    // Nothing but the test harness: :engine depends on no project module and no runtime library (K4).
    testImplementation(libs.kotlin.test)
    // JSON reader for the vector runner only (10.1). Test scope: the engine's runtime stays dependency-free.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Engine tests must finish in under five seconds in total (3.13).
    testLogging { events("failed") }
}
