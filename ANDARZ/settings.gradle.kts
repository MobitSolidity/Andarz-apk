// Andarz — Gradle settings. The Gradle root is also the git root (K1).
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Canonical sources first: when they are reachable they win.
        google()
        mavenCentral()
        // Fallback mirrors. dl.google.com answers 404 for every artifact from the owner's network, so
        // without these the androidx coordinates cannot be fetched at all (D-M38). Same coordinates,
        // same version catalog; only the transport differs.
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "andarz"

// :engine  pure Kotlin/JVM, depends on nothing
// :data    Android library, depends on :engine only
// :market  Android library, depends on no project module (K4)
// :app     Compose UI, depends on :engine, :data, :market
include(":engine")
include(":data")
include(":market")
include(":app")
