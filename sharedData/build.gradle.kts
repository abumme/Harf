import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    android {
        namespace = "uz.abumme.harfgame.data"
        compileSdk = 37
        minSdk = 24
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    js { browser() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // PuzzleDaysTest's named zones (Asia/Tashkent, Europe/Moscow, ...) resolve in the browser through js-joda, which
        // ships without the IANA database; the browser tests load it as sharedUI's web platform modules do. Test-only:
        // the staff panel reads dates from the browser's Intl and must not bundle it.
        jsTest.dependencies {
            implementation(npm("@js-joda/timezone", libs.versions.js.joda.timezone.get()))
        }

        wasmJsTest.dependencies {
            implementation(npm("@js-joda/timezone", libs.versions.js.joda.timezone.get()))
        }
    }
}
