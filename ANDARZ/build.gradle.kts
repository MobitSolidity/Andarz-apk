// Andarz — root build.
// The guards of K8.4 live in gradle/guards.gradle.kts and are wired into `check`/`build` there.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

apply(from = rootProject.file("gradle/guards.gradle.kts"))

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
