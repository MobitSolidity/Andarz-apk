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
    // The golden vectors are read straight from spec/ (single source of truth, never copied or edited).
    test {
        kotlin.srcDirs("src/test/kotlin")
        resources.srcDir("$rootDir/spec")
    }
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

// Engine line coverage must stay at or above 90% (10.7, M2 exit). `check` runs the verification.
apply(plugin = "jacoco")
configure<JacocoPluginExtension> { toolVersion = "0.8.12" }

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(false) }
}

val engineCoverage = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn(engineCoverage, "jacocoTestReport") }
