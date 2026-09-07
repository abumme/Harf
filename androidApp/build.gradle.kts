import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

android {
    namespace = "uz.abumme.harfgame.androidApp"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        targetSdk = 37

        applicationId = "uz.abumme.harfgame"
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(libs.androidx.activityCompose)
}

// Convenience for debugging against a local backend. The `Local` in this task's name flips
// sharedUI's apiBaseUrl default to http://localhost:8080 (see sharedUI/build.gradle.kts); the
// adb-reverse tunnel makes that reachable from a physical device or emulator.
//   ./gradlew :androidApp:installLocalDebug
tasks.register<Exec>("adbReverse8080") {
    description = "adb reverse tcp:8080 -> host:8080 so the device can reach a local backend."
    commandLine("adb", "reverse", "tcp:8080", "tcp:8080")
    isIgnoreExitValue = true
}
tasks.register("installLocalDebug") {
    description = "Install the debug app wired to http://localhost:8080 and tunnel :8080 to the host."
    group = "install"
    dependsOn("installDebug", "adbReverse8080")
}
