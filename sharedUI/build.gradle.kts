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
            implementation(libs.purchases.kmp.ui) // RC hosted Paywall + Customer Center (mobile only)
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
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.navigation.compose)
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
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.datetime)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
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

// RevenueCat public SDK keys come from local.properties (gitignored) or a -P gradle property,
// never from committed source. Blank => purchases report Unavailable, no crash.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun rcKey(name: String): String =
    localProps.getProperty(name) ?: providers.gradleProperty(name).orNull ?: ""

// A release build must never ship a RevenueCat Test Store / placeholder key. Real public SDK keys
// start with a store prefix (goog_ / appl_); a blank key is allowed (purchases report Unavailable).
val isReleaseBuild = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}
fun rcReleaseKey(name: String, prodPrefix: String): String {
    val value = rcKey(name)
    if (isReleaseBuild && value.isNotBlank() && !value.startsWith(prodPrefix)) {
        error(
            "Refusing release build: '$name' is not a production RevenueCat key " +
                "(expected it to start with '$prodPrefix', e.g. a real public SDK key). " +
                "Test Store / placeholder keys must not ship in a release. Use a real key or leave it blank."
        )
    }
    return value
}

// Backend API base URL per environment. Dev defaults to localhost; a release build must supply a
// production HTTPS URL (never a loopback) via `-Pharf.apiBaseUrl=...` or local.properties.
fun apiBaseUrl(): String {
    val configured = localProps.getProperty("harf.apiBaseUrl")
        ?: providers.gradleProperty("harf.apiBaseUrl").orNull
        ?: ""
    val value = configured.ifBlank { if (isReleaseBuild) "" else "http://localhost:8080" }
    if (isReleaseBuild) {
        require(value.isNotBlank()) {
            "Release build requires 'harf.apiBaseUrl' (production HTTPS API URL); refusing to default to localhost."
        }
        require(value.startsWith("https://")) { "Release API base URL must be HTTPS: '$value'." }
        val host = value.removePrefix("https://").substringBefore('/').substringBefore(':')
        require(host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) {
            "Release API base URL must not point at localhost/loopback: '$value'."
        }
    }
    return value
}

buildConfig {
    packageName("uz.abumme.harfgame")
    buildConfigField("String", "REVENUECAT_ANDROID_KEY", "\"${rcReleaseKey("revenuecat.androidKey", "goog_")}\"")
    buildConfigField("String", "REVENUECAT_IOS_KEY", "\"${rcReleaseKey("revenuecat.iosKey", "appl_")}\"")
    buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
}
