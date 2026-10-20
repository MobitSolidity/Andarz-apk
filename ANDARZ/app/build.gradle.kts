import com.android.build.api.artifact.SingleArtifact
import groovy.json.JsonSlurper
import java.util.Properties

// :app — Compose UI for the journal AND the Market screens, Hilt, navigation, the adapters that connect
// :market to the app (K4). It is the only module that may depend on :market.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------- release signing (K8.6, D-M10)
// March's committed keystore is treated as compromised and is never used. Andarz reads its own key from
// the environment or from a git-ignored keystore.properties; without it the release build is signed with
// the debug key and says so loudly.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotEmpty() } ?: keystoreProps.getProperty(prop)

val releaseStore: String? = signingValue("ANDARZ_KEYSTORE", "storeFile")

android {
    namespace = "ir.andarz.app"
    compileSdk = 35

    defaultConfig {
        // Placeholder (D-11, D-M02): decide the final id before any store release.
        applicationId = "ir.andarz.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("fa")
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = rootProject.file(releaseStore)
                storePassword = signingValue("ANDARZ_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ANDARZ_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ANDARZ_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // F9/F10 run as Robolectric Compose tests on the JVM (D-71).
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.logging.enabled", "false") }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":data"))
    implementation(project(":market"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // Core icons only: material-icons-extended is deliberately not added (K7).
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // NO okhttp here: guard 4 of K8.4 fails the build on a direct okhttp dependency.

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)
}

// ---------------------------------------------------------------- content assets (2.2, K7)
// The spec files are read-only inputs: they are copied, never edited. content_fa.json is the base wording;
// content_market_fa.json adds the mkt.* keys and the overrides block (D-M12).
val prepareContentAssets = tasks.register<Copy>("prepareContentAssets") {
    group = "build setup"
    description = "Copies the spec content JSON into the app assets (2.2)."
    from(rootProject.file("spec/andarz_content_fa.json")) { rename { "content_fa.json" } }
    from(rootProject.file("spec/andarz_design_tokens.json")) { rename { "design_tokens.json" } }
    into(layout.projectDirectory.dir("src/main/assets"))
}

// The manifest label must be a string resource, because Kotlin sources may not hold Persian (law 3,
// Appendix C trap 8). It is generated from the content JSON so there is exactly one source of the name.
val generatedResDir = layout.buildDirectory.dir("generated/res/andarz")

val generateAppLabel = tasks.register("generateAppLabel") {
    group = "build setup"
    description = "Writes the app label string resource from meta.app.name of the content JSON (trap 8)."
    val contentFile = rootProject.file("spec/andarz_content_fa.json")
    val outDir = generatedResDir
    inputs.file(contentFile)
    outputs.dir(outDir)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(contentFile, "UTF-8") as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val meta = root["meta"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val app = meta["app"] as Map<String, Any>
        val name = app["name"]?.toString() ?: error("meta.app.name is missing from the content JSON")
        val values = outDir.get().dir("values").asFile
        values.mkdirs()
        // The file is generated, so no Persian literal lives in a tracked source file.
        values.resolve("strings_generated.xml").writeText(
            buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                append("<!-- Generated from spec/andarz_content_fa.json by :app:generateAppLabel. Do not edit. -->\n")
                append("<resources>\n")
                append("    <string name=\"app_name\">").append(name).append("</string>\n")
                append("</resources>\n")
            },
            Charsets.UTF_8,
        )
    }
}

android.sourceSets.getByName("main").res.srcDir(generatedResDir)

tasks.named("preBuild") {
    dependsOn(prepareContentAssets, generateAppLabel)
}

// ---------------------------------------------------------------- K8.4 guard 1: merged manifest
// The merged release manifest must contain exactly the permissions of K8.1 plus the recorded library
// entries, and must never contain REQUEST_INSTALL_PACKAGES.
val allowedMergedPermissions: Set<String> = setOf(
    // :app (2.3)
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.USE_BIOMETRIC",
    "android.permission.VIBRATE",
    // :market (K8.1)
    "android.permission.INTERNET",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.WAKE_LOCK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.USE_FULL_SCREEN_INTENT",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "com.android.alarm.permission.SET_ALARM",
    "android.permission.SYSTEM_ALERT_WINDOW",
    // Library entries, each recorded with its owner in DECISIONS.md (K8.1, D-M40).
    // Verified against app/build/outputs/logs/manifest-merger-release-report.txt:
    "android.permission.USE_FINGERPRINT", // androidx.biometric:biometric:1.1.0 (API 26-28 path)
    "android.permission.ACCESS_NETWORK_STATE", // androidx.work:work-runtime:2.10.0 (network constraints)
)

/**
 * Signature-level permissions a library defines for its own use. androidx.core declares
 * `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` so a dynamically registered receiver stays
 * private to the app; it grants nothing to anyone else (K8.1, D-M40).
 */
val allowedMergedPermissionSuffixes: Set<String> = setOf(
    ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

val forbiddenMergedPermissions: Set<String> = setOf(
    "android.permission.REQUEST_INSTALL_PACKAGES",
)

abstract class VerifyMergedManifest : DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val manifest: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.Input
    abstract val allowed: org.gradle.api.provider.SetProperty<String>

    @get:org.gradle.api.tasks.Input
    abstract val allowedSuffixes: org.gradle.api.provider.SetProperty<String>

    @get:org.gradle.api.tasks.Input
    abstract val forbidden: org.gradle.api.provider.SetProperty<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val text = manifest.get().asFile.readText(Charsets.UTF_8)
        val found = Regex("""uses-permission[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(text).map { it.groupValues[1] }.toSortedSet()
        val problems = ArrayList<String>()
        for (p in found) {
            if (p in forbidden.get()) problems.add("the merged manifest declares the forbidden permission $p")
            val ok = p in allowed.get() || allowedSuffixes.get().any { p.endsWith(it) }
            if (!ok) problems.add("the merged manifest declares $p, which the K8.1 allowlist does not hold")
        }
        if (text.contains("android:allowBackup=\"true\"")) {
            problems.add("allowBackup must stay false (law 1)")
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "K8.4 guard 1 (merged manifest) failed:" + System.lineSeparator() +
                    problems.joinToString(System.lineSeparator()) { "  - $it" } +
                    System.lineSeparator() + "  merged set: " + found.joinToString(),
            )
        }
        logger.lifecycle("K8.4 guard 1: merged manifest permissions = " + found.joinToString())
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<VerifyMergedManifest>(
            "verifyMergedManifest" + variant.name.replaceFirstChar { it.uppercase() },
        ) {
            group = "verification"
            description = "K8.4 guard 1: the merged manifest equals the K8.1 permission allowlist."
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowed.set(allowedMergedPermissions)
            allowedSuffixes.set(allowedMergedPermissionSuffixes)
            forbidden.set(forbiddenMergedPermissions)
        }
        // The release manifest is the one K8.1 pins; the debug one is checked too, as it is the same merge.
        if (variant.name == "release") {
            tasks.named("verifyMergedManifest") { dependsOn(task) }
        }
    }
}

tasks.register("verifyMergedManifest") {
    group = "verification"
    description = "K8.4 guard 1 on the release variant."
}

// D-71: the Compose UI tests need the debug-only ui-test-manifest activity; the release unit-test task skips them.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach {
    (this as Test).filter.excludeTestsMatching("ir.andarz.app.ui.AccessibilityLayoutTest")
}
