// K8.4 build guards. Each guard fails the build on its own violation; `verifyGuards` runs them all
// and is wired into `check`, so `./gradlew build` enforces them.
//
// Guards 5 (egress) and 6 (zero traffic) of K8.4 are unit tests in :market (MarketHttpEgressTest,
// ZeroTrafficTest); `./gradlew build` runs them as part of :market:check. Everything else is here.
//
// No Persian text and no emoji live in this file: both come from tools/guards/guard_config.json (law 3, K7).

import groovy.json.JsonSlurper

// ---------------------------------------------------------------- expected architecture (K4, K8.1)

// path -> the project dependencies it may declare
val expectedProjectDeps: Map<String, Set<String>> = mapOf(
    ":engine" to emptySet(),
    ":data" to setOf(":engine"),
    ":market" to emptySet(),
    ":app" to setOf(":engine", ":data", ":market"),
)

// Permissions of K8.1, by the module whose own manifest must declare them.
val expectedPermissionOwners: Map<String, Set<String>> = mapOf(
    ":app" to setOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.USE_BIOMETRIC",
        "android.permission.VIBRATE",
    ),
    ":market" to setOf(
        "android.permission.INTERNET",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.WAKE_LOCK",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.USE_FULL_SCREEN_INTENT",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "com.android.alarm.permission.SET_ALARM",
        "android.permission.SYSTEM_ALERT_WINDOW",
    ),
)

// Entries that libraries add to the merged manifest. Each one is recorded with its owner in
// DECISIONS.md (K8.1). Anything outside allowlist + this set fails guard 1.
val libraryPermissions: Map<String, String> = mapOf(
    "android.permission.USE_FINGERPRINT" to "androidx.biometric (API 26-28 fingerprint path)",
)

val forbiddenPermissions: Set<String> = setOf(
    "android.permission.REQUEST_INSTALL_PACKAGES",
)

// Artifact coordinates that must not appear on a module's runtime classpath (K8.4 guard 4).
//
// The tokens are GROUP coordinates, not bare words, so the guard names the thing it actually forbids: an
// HTTP stack reachable from the journal.
val forbiddenOnClasspath: Map<String, List<String>> = mapOf(
    ":engine" to listOf("com.squareup.okhttp3", "com.squareup.okio", "retrofit", "firebase", "play-services", "crashlytics", "sqlcipher"),
    ":data" to listOf("com.squareup.okhttp3", "retrofit", "firebase", "play-services", "crashlytics", "sqlcipher"),
    ":market" to listOf("retrofit", "firebase", "play-services", "crashlytics", "sqlcipher"),
    ":app" to listOf("retrofit", "firebase", "play-services", "crashlytics", "sqlcipher"),
)

// okio on :data is the one documented exception (D-M39). K8.4 lists the bare word "okio" because okio
// normally arrives with OkHttp -- but Preferences DataStore, which 2.1 mandates for settings, pulls
// com.squareup.okio through androidx.datastore:datastore-core-okio. okio is a buffer and filesystem
// library: it cannot open a socket, and the guard's purpose (no HTTP client reachable from the journal)
// is untouched.
//
// So instead of trusting that, :data asserts the exact shape of the exception: okio may be present only
// while okhttp3 is absent AND androidx.datastore is the thing that brought it. An okio that arrives any
// other way still fails the build.
//
// :market and :app are not checked for okio at all: OkHttp is allowed in :market (K2) and okio is its
// I/O layer, so okio there is expected. K8.4's :market list never mentioned it. What :app must not have
// is a *direct* okhttp dependency, which is checked separately below.
val okioCheckedModules: Set<String> = setOf(":engine", ":data")
val okioExceptionModules: Set<String> = setOf(":data")


// ---------------------------------------------------------------- helpers

val guardConfigFile: File = rootProject.file("tools/guards/guard_config.json")

@Suppress("UNCHECKED_CAST")
fun guardConfig(): Map<String, Any> = JsonSlurper().parse(guardConfigFile, "UTF-8") as Map<String, Any>

@Suppress("UNCHECKED_CAST")
fun emojiRanges(cfg: Map<String, Any>): List<IntRange> =
    (cfg["emojiRanges"] as List<List<String>>).map { (lo, hi) -> lo.toInt(16)..hi.toInt(16) }

fun firstEmoji(text: String, ranges: List<IntRange>): String? {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (ranges.any { cp in it }) return "U+" + Integer.toHexString(cp).uppercase()
        i += Character.charCount(cp)
    }
    return null
}

val persianRange: IntRange = 0x0600..0x06FF

/** Source trees that law 3 and the token scan cover: every module, main and test. */
fun sourceFiles(vararg extensions: String): List<File> {
    val out = ArrayList<File>()
    for (p in listOf("engine", "data", "market", "app")) {
        val dir = rootProject.file(p + "/src")
        if (!dir.isDirectory) continue
        dir.walkTopDown()
            .filter { it.isFile && extensions.any { e -> it.name.endsWith(e) } }
            .forEach { out.add(it) }
    }
    return out
}

fun rel(f: File): String = f.absolutePath.removePrefix(rootProject.projectDir.absolutePath).trimStart('\\', '/')

fun fail(guard: String, problems: List<String>) {
    if (problems.isEmpty()) return
    val body = problems.joinToString(System.lineSeparator()) { "  - " + it }
    throw GradleException("K8.4 $guard failed:" + System.lineSeparator() + body)
}

/** Declared <uses-permission> names of a module's own manifests (release + main). */
fun declaredPermissions(modulePath: String): Set<String> {
    val dir = rootProject.file(modulePath.trimStart(':') + "/src")
    if (!dir.isDirectory) return emptySet()
    val names = LinkedHashSet<String>()
    dir.walkTopDown().filter { it.isFile && it.name == "AndroidManifest.xml" }.forEach { m ->
        Regex("""uses-permission[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(m.readText(Charsets.UTF_8))
            .forEach { names.add(it.groupValues[1]) }
    }
    return names
}

// Captured once every project is evaluated, so the tasks never configure another project.
val declaredProjectDeps: MutableMap<String, MutableSet<String>> = linkedMapOf()

gradle.projectsEvaluated {
    rootProject.subprojects.forEach { p ->
        val deps = linkedSetOf<String>()
        p.configurations.forEach { c ->
            c.dependencies.withType(ProjectDependency::class.java).forEach { d ->
                @Suppress("DEPRECATION")
                val target = d.dependencyProject.path
                // AGP points a module's own test configurations back at the module under test. That is
                // not an architectural edge, so it never counts here.
                if (target != p.path) deps.add(target)
            }
        }
        declaredProjectDeps[p.path] = deps
    }
}

// ---------------------------------------------------------------- guard 2 + part of 1: manifest sources

val guardManifestSources = tasks.register("guardManifestSources") {
    group = "verification"
    description = "K8.4 guard 2: INTERNET is declared in :market's manifest only; module permissions match K8.1."
    outputs.upToDateWhen { false }
    doLast {
        val problems = ArrayList<String>()
        val modules = listOf(":app", ":data", ":market")
        for (m in modules) {
            val declared = declaredPermissions(m)
            val allowed = expectedPermissionOwners[m] ?: emptySet()
            for (p in declared) {
                if (p in forbiddenPermissions) problems.add("$m declares the forbidden permission $p")
                if (p !in allowed) problems.add("$m declares $p, which K8.1 does not assign to it")
            }
            for (p in allowed) {
                if (p !in declared) problems.add("$m is missing the K8.1 permission $p")
            }
        }
        // The point of the guard: only :market may declare INTERNET.
        val internet = "android.permission.INTERNET"
        for (m in modules) {
            if (m != ":market" && internet in declaredPermissions(m)) {
                problems.add("$m declares $internet; only :market may (K2, K8.4 guard 2)")
            }
        }
        if (internet !in declaredPermissions(":market")) {
            problems.add(":market must declare $internet (K8.1)")
        }
        fail("guard 2 (INTERNET only in :market)", problems)
    }
}

// ---------------------------------------------------------------- guard 3: dependency graph

val guardDependencyGraph = tasks.register("guardDependencyGraph") {
    group = "verification"
    description = "K8.4 guard 3: the project dependency graph equals K4."
    outputs.upToDateWhen { false }
    doLast {
        val problems = ArrayList<String>()
        for ((path, expected) in expectedProjectDeps) {
            val actual = declaredProjectDeps[path]
            if (actual == null) {
                problems.add("module $path is missing from the build")
                continue
            }
            (actual - expected).forEach { problems.add("$path must not depend on $it (K4)") }
            (expected - actual).forEach { problems.add("$path is expected to depend on $it (K4)") }
        }
        declaredProjectDeps.keys.filter { it !in expectedProjectDeps.keys }
            .forEach { problems.add("unexpected module $it: add it to K4 and to this guard first") }
        // Spelled out, because these two are the reason the guard exists.
        declaredProjectDeps.forEach { (path, deps) ->
            if (path != ":app" && ":app" in deps) problems.add("$path depends on :app; nothing may (K4)")
            if (path != ":app" && ":market" in deps) problems.add("$path depends on :market; only :app may (K4)")
        }
        fail("guard 3 (dependency graph)", problems)
    }
}

// ---------------------------------------------------------------- guard 4: runtime classpaths

val guardClasspaths = tasks.register("guardClasspaths") {
    group = "verification"
    description = "K8.4 guard 4: forbidden libraries are absent from each module's runtime classpath."
    outputs.upToDateWhen { false }
    notCompatibleWithConfigurationCache("resolves runtime classpaths of every module")
    doLast {
        val problems = ArrayList<String>()
        val candidates = listOf("releaseRuntimeClasspath", "runtimeClasspath", "debugRuntimeClasspath")
        for ((path, forbidden) in forbiddenOnClasspath) {
            val project = rootProject.project(path)
            val config = candidates.firstNotNullOfOrNull { name ->
                project.configurations.findByName(name)?.takeIf { it.isCanBeResolved }
            }
            if (config == null) {
                problems.add("$path has no resolvable runtime classpath to check")
                continue
            }
            val names = try {
                config.incoming.artifactView { lenient(true) }.artifacts.map { it.id.displayName.lowercase() }
            } catch (e: Exception) {
                problems.add("$path: could not resolve ${config.name}: ${e.message}")
                emptyList()
            }
            for (bad in forbidden) {
                val hit = names.firstOrNull { it.contains(bad) }
                if (hit != null) problems.add("$path must not carry '$bad' on its runtime classpath (found $hit)")
            }
            // The okio exception of D-M39, asserted rather than assumed.
            val okio = names.firstOrNull { it.contains("com.squareup.okio") }
            if (okio != null && path in okioCheckedModules) {
                if (path !in okioExceptionModules) {
                    problems.add("$path must not carry 'com.squareup.okio' on its runtime classpath (found $okio)")
                } else {
                    if (names.any { it.contains("com.squareup.okhttp3") }) {
                        problems.add("$path carries okio together with okhttp3; the D-M39 exception covers DataStore only")
                    }
                    if (names.none { it.contains("androidx.datastore") }) {
                        problems.add("$path carries okio ($okio) without androidx.datastore; the D-M39 exception does not apply")
                    }
                }
            }
        }
        // :app declares no direct okhttp dependency (K8.4 guard 4).
        val appDirect = rootProject.project(":app").configurations
            .flatMap { c -> c.dependencies.map { d -> (d.group ?: "") + ":" + d.name } }
            .filter { it.contains("okhttp", ignoreCase = true) }
        appDirect.forEach { problems.add(":app declares a direct okhttp dependency ($it); OkHttp belongs to :market only") }
        fail("guard 4 (classpaths)", problems)
    }
}

// ---------------------------------------------------------------- guard 7: law 3, no Persian in code

val guardNoPersianInCode = tasks.register("guardNoPersianInCode") {
    group = "verification"
    description = "K8.4 guard 7 (law 3): no character in U+0600-U+06FF in any .kt file of any module, tests included."
    outputs.upToDateWhen { false }
    doLast {
        val problems = ArrayList<String>()
        for (f in sourceFiles(".kt")) {
            f.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                val cp = line.codePoints().filter { it in persianRange }.findFirst()
                if (cp.isPresent) {
                    problems.add(rel(f) + ":" + (i + 1) + " holds U+" + Integer.toHexString(cp.asInt).uppercase() +
                        "; Persian text belongs in the content JSON (law 3)")
                }
            }
        }
        fail("guard 7 (no Persian in code)", problems)
    }
}

// ---------------------------------------------------------------- guard 8: forbidden tokens

val guardForbiddenTokens = tasks.register("guardForbiddenTokens") {
    group = "verification"
    description = "K8.4 guard 8: no android.icu, no PackageInstaller, no APK mime type, no install permission."
    outputs.upToDateWhen { false }
    doLast {
        @Suppress("UNCHECKED_CAST")
        val tokens = guardConfig()["forbiddenTokens"] as List<String>
        val problems = ArrayList<String>()
        for (f in sourceFiles(".kt", ".kts", ".xml", ".pro")) {
            val text = f.readText(Charsets.UTF_8)
            for (t in tokens) {
                if (text.contains(t)) problems.add(rel(f) + " contains the forbidden token '" + t + "'")
            }
        }
        fail("guard 8 (forbidden tokens)", problems)
    }
}

// ---------------------------------------------------------------- guard 9: shipped strings

val guardShippedStrings = tasks.register("guardShippedStrings") {
    group = "verification"
    description = "K8.4 guard 9: shipped strings carry no emoji and no claim that Andarz has no internet."
    outputs.upToDateWhen { false }
    dependsOn(":app:prepareContentAssets")
    doLast {
        val cfg = guardConfig()
        val ranges = emojiRanges(cfg)
        @Suppress("UNCHECKED_CAST") val claims = cfg["noInternetClaims"] as List<String>
        @Suppress("UNCHECKED_CAST") val productNames = cfg["productNames"] as List<String>
        @Suppress("UNCHECKED_CAST") val creditKeys = (cfg["marchCreditKeys"] as List<String>).toSet()
        val problems = ArrayList<String>()

        val assets = rootProject.file("app/src/main/assets")
        val base = assets.resolve("content_fa.json")
        val market = assets.resolve("content_market_fa.json")
        if (!base.isFile) throw GradleException("guard 9: " + rel(base) + " is missing; run :app:prepareContentAssets")

        // Effective strings = base content with the market overrides applied (K7).
        val flat = LinkedHashMap<String, String>()
        fun walk(node: Any?, path: String) {
            when (node) {
                is String -> flat[path] = node
                is Map<*, *> -> node.forEach { (k, v) -> walk(v, if (path.isEmpty()) k.toString() else "$path.$k") }
                is List<*> -> node.forEachIndexed { i, v -> walk(v, "$path[$i]") }
            }
        }
        walk(JsonSlurper().parse(base, "UTF-8"), "")
        if (market.isFile) {
            @Suppress("UNCHECKED_CAST")
            val m = JsonSlurper().parse(market, "UTF-8") as Map<String, Any>
            walk(m["ui"], "mkt")
            @Suppress("UNCHECKED_CAST")
            val overrides = (m["overrides"] as? Map<String, String>) ?: emptyMap()
            overrides.forEach { (k, v) -> flat["ui.$k"] = v }
        }
        // The English wording and the glossary ship too (D-90, D-92); they follow the same rules.
        val baseEn = assets.resolve("content_en.json")
        if (baseEn.isFile) walk(JsonSlurper().parse(baseEn, "UTF-8"), "en")
        val marketEn = assets.resolve("content_market_en.json")
        if (marketEn.isFile) {
            @Suppress("UNCHECKED_CAST")
            val m = JsonSlurper().parse(marketEn, "UTF-8") as Map<String, Any>
            walk(m["ui"], "en.mkt")
            walk(m["overrides"], "en.overrides")
            walk(m["numberWords"], "en.numberWords")
        }
        for (name in listOf("content_app_fa.json", "content_app_en.json")) {
            val f = assets.resolve(name)
            if (f.isFile) walk(JsonSlurper().parse(f, "UTF-8"), name.removeSuffix(".json"))
        }
        val glossary = assets.resolve("glossary.json")
        if (glossary.isFile) walk(JsonSlurper().parse(glossary, "UTF-8"), "glossary")
        // The market calendar asset ships too (K6.7 drops the flag emoji).
        val calendar = rootProject.file("market/src/main/assets/market_calendar.json")
        if (calendar.isFile) walk(JsonSlurper().parse(calendar, "UTF-8"), "calendar")

        for ((key, value) in flat) {
            firstEmoji(value, ranges)?.let { problems.add("$key carries the emoji $it (K6.7: no emoji in shipped strings)") }
            for (c in claims) {
                if (value.contains(c)) {
                    problems.add("$key claims the app has no internet; Market uses the network (K2). " +
                        "Replace it through the overrides block of content_market_fa.json")
                }
            }
            if (key.substringAfter("ui.", "") !in creditKeys && key.substringAfter("mkt.", "") !in creditKeys) {
                for (n in productNames) {
                    if (value.contains(n)) problems.add("$key names '$n'; only ${creditKeys.joinToString()} may (A10)")
                }
            }
        }
        fail("guard 9 (shipped strings)", problems)
    }
}

// ---------------------------------------------------------------- aggregate

val verifyGuards = tasks.register("verifyGuards") {
    group = "verification"
    description = "Runs every K8.4 build guard."
    dependsOn(
        guardManifestSources,
        guardDependencyGraph,
        guardClasspaths,
        guardNoPersianInCode,
        guardForbiddenTokens,
        guardShippedStrings,
        ":app:verifyMergedManifest",
    )
}

gradle.projectsEvaluated {
    rootProject.subprojects.forEach { p ->
        p.tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyGuards) }
    }
}
