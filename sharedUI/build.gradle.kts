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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.ksafe)
            implementation(libs.ksafe.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(libs.roborazzi.composeDesktop)
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

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

// RevenueCat public SDK keys come from local.properties (gitignored) or a -P gradle property,
// never from committed source. Blank => purchases report Unavailable, no crash.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun rcKey(name: String): String =
    localProps.getProperty(name) ?: providers.gradleProperty(name).orNull ?: ""

buildConfig {
    packageName("uz.abumme.harfgame")
    buildConfigField("String", "REVENUECAT_ANDROID_KEY", "\"${rcKey("revenuecat.androidKey")}\"")
    buildConfigField("String", "REVENUECAT_IOS_KEY", "\"${rcKey("revenuecat.iosKey")}\"")
}
