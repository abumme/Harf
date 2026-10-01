import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.roborazzi)
}

kotlin {
    // The generated BuildConfig is now an expect/actual object (single RevenueCat key); opt in
    // to silence the Beta warning across all targets.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    android {
        namespace = "uz.abumme.harfgame"
        compileSdk = 37
        minSdk = 24
        androidResources.enable = true
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    js { browser() }
    wasmJs { browser() }

    iosArm64()
    iosSimulatorArm64()

    // Manual dependsOn edges below disable the implicit hierarchy template, which
    // orphans iosMain from the ios targets — re-apply it explicitly.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // android + ios share the real RevenueCat integration; desktop/web get a no-op.
        val mobileMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(mobileMain)
        iosMain.get().dependsOn(mobileMain)
        mobileMain.dependencies {
            implementation(libs.purchases.kmp.core)
        }

        commonMain.dependencies {
            api(project(":sharedData"))
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.resources)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            // Ktor 3.6.0 curated multiplatform engine facade: supplies a client engine for every
            // target (incl. js/wasmJs) from commonMain, so no per-platform engine dependency is needed.
            implementation(libs.ktor.client.engine.defaults)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.navigation.compose)
            // Swipe-edge constants for the predictive back transitions (already pulled in by navigation-compose).
            implementation(libs.androidx.navigationevent.compose)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.ksafe)
            implementation(libs.ksafe.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        jvmTest.dependencies {
            implementation(libs.roborazzi.composeDesktop)
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            // Native Google sign-in (Credential Manager + Google ID token)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            // Play Games Services v2 (Android-only: sign-in, leaderboards, achievements)
            implementation(libs.play.services.games.v2)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.datetime)
        }


    }

    targets
        .withType<KotlinNativeTarget>()
        .matching { it.konanTarget.family.isAppleFamily }
        .configureEach {
            binaries {
                framework {
                    baseName = "SharedUI"
                    isStatic = true
                }
            }
        }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// RevenueCat public SDK keys come from -P gradle property, revenuecat.properties or local.properties
// (both gitignored), never from committed source. Blank => purchases report Unavailable, no crash.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val rcProps = Properties().apply {
    val f = rootProject.file("revenuecat.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun rcKey(name: String): String =
    providers.gradleProperty(name).orNull ?: rcProps.getProperty(name) ?: localProps.getProperty(name) ?: ""

// A release build must never ship a RevenueCat Test Store / placeholder key. Real public SDK keys
// start with a store prefix (goog_ / appl_); a blank key is allowed (purchases report Unavailable).
val xcodeConfig = System.getenv("CONFIGURATION")
val isXcodeRelease = xcodeConfig?.equals("Release", ignoreCase = true) == true
val releaseTasks = gradle.startParameter.taskNames.filter { it.contains("Release", ignoreCase = true) }
val isAnyReleaseTask = releaseTasks.isNotEmpty() || isXcodeRelease

fun isReleaseForTarget(target: String): Boolean {
    if (!isAnyReleaseTask) return false
    if (isXcodeRelease) {
        return target == "ios"
    }
    val hasTargetTask = releaseTasks.any { it.contains(target, ignoreCase = true) }
    val hasOtherTargetTask = releaseTasks.any {
        when (target) {
            "android" -> it.contains("ios", ignoreCase = true) || it.contains("apple", ignoreCase = true)
            "ios" -> it.contains("android", ignoreCase = true)
            else -> false
        }
    }
    return if (hasOtherTargetTask && !hasTargetTask) false else true
}

// `revenuecat.testKey` (a Test Store key) stands in for a missing store key in dev builds only —
// a release never falls back to it, so a forgotten store key ships as blank, not as a test key.
fun rcReleaseKey(name: String, prodPrefix: String, target: String): String {
    val value = rcKey(name).ifBlank { if (isReleaseForTarget(target)) "" else rcKey("revenuecat.testKey") }
    if (isReleaseForTarget(target) && value.isNotBlank() && !value.startsWith(prodPrefix)) {
        error(
            "Refusing release build for $target: '$name' is not a production RevenueCat key " +
                "(expected it to start with '$prodPrefix', e.g. a real public SDK key). " +
                "Test Store / placeholder keys must not ship in a release. Use a real key or leave it blank."
        )
    }
    return value
}

// Backend API base URL. Production is the default for every build; to debug against a local server
// override with `-Pharf.apiBaseUrl=http://localhost:8080` or run the `:androidApp:installLocalDebug`
// task (which defaults the URL to localhost). A release build additionally requires non-loopback HTTPS.
val localBackend = gradle.startParameter.taskNames.any { it.contains("Local", ignoreCase = true) }
fun apiBaseUrl(): String {
    val prodDefault = "https://api.lazydevs.uz/harf"
    val value = (providers.gradleProperty("harf.apiBaseUrl").orNull
        ?: localProps.getProperty("harf.apiBaseUrl")
        ?: "").ifBlank { if (localBackend) "http://localhost:8080" else prodDefault }
    if (isAnyReleaseTask) {
        require(value.startsWith("https://")) { "Release API base URL must be HTTPS: '$value'." }
        val host = value.removePrefix("https://").substringBefore('/').substringBefore(':')
        require(host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) {
            "Release API base URL must not point at localhost/loopback: '$value'."
        }
    }
    return value
}

// Google Web (server) OAuth client id — used as the Credential Manager serverClientId and as the
// backend audience. This is a PUBLIC id (it ships in the app), overridable via `-Pgoogle.serverClientId`
// or local.properties. Blank => Google sign-in reports NotConfigured (no crash).
fun googleServerClientId(): String =
    providers.gradleProperty("google.serverClientId").orNull
        ?: localProps.getProperty("google.serverClientId")
        ?: "508164918683-9rce0g2mrjqn9pcshk33kdf870rg1hua.apps.googleusercontent.com"

buildConfig {
    packageName("uz.abumme.harfgame")
    // Single expect-ed RevenueCat key: blank default (⇒ purchases Unavailable) for every
    // non-store target (desktop/web/jvm); android & ios provide the real store key as `actual`.
    buildConfigField("REVENUECAT_KEY", expect(""))
    buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
    buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${googleServerClientId()}\"")

    sourceSets.named("androidMain") {
        buildConfigField("REVENUECAT_KEY", rcReleaseKey("revenuecat.androidKey", "goog_", "android"))
    }
    sourceSets.named("iosMain") {
        buildConfigField("REVENUECAT_KEY", rcReleaseKey("revenuecat.iosKey", "appl_", "ios"))
    }
}

// Release step (daily-word-calendar): `./gradlew :sharedUI:refreshCalendarSnapshot` writes the published calendar of every
// launch language to composeResources/files/<lang>_calendar.json, so an offline fresh install of the release plays the
// server's words. It reads `harf.apiBaseUrl` (production by default) and fails, writing nothing, when a pack would be
// refused by the app. `-PcalendarSnapshotDir=<dir>` writes elsewhere (e.g. to try it against a local backend).
val calendarSnapshotTool: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute, org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm)
    }
}
dependencies {
    calendarSnapshotTool(project(":tools:wordlists"))
}
tasks.register<JavaExec>("refreshCalendarSnapshot") {
    group = "harf"
    description = "Refreshes the bundled daily-word calendar snapshots from the published word packs"
    val resources = layout.projectDirectory.dir("src/commonMain/composeResources/files").asFile
    val output = providers.gradleProperty("calendarSnapshotDir").map { file(it) }.getOrElse(resources)
    classpath = calendarSnapshotTool
    mainClass.set("uz.abumme.harfgame.tools.wordlists.CalendarSnapshotRefreshKt")
    args(apiBaseUrl(), output.absolutePath, resources.absolutePath)
    // The output is the network's answer: never up to date.
    outputs.upToDateWhen { false }
}

// Prints which RevenueCat key each mobile target would compile in (values masked).
tasks.register("rcKeyReport") {
    group = "harf"
    val android = rcReleaseKey("revenuecat.androidKey", "goog_", "android")
    val ios = rcReleaseKey("revenuecat.iosKey", "appl_", "ios")
    doLast {
        fun mask(v: String) = if (v.isBlank()) "<blank — purchases unavailable>" else v.take(5) + "…"
        println("android: ${mask(android)}")
        println("ios:     ${mask(ios)}")
    }
}
