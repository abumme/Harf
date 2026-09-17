rootProject.name = "Harf-Game"

pluginManagement {
    repositories {
        google {
            content { 
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

// Auto-provision JDKs/JBR (needed by Compose Hot Reload's JBR runtime)
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            content { 
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
    }
}
include(":sharedData")

// Client modules pull in the Android SDK / KMP native toolchains. A backend-only build
// (server or Docker image, JDK only) skips them with `-PbackendOnly`.
// `-PadminWebOnly` builds just the staff panel and its shared contract (CI job, Docker export stage):
// no backend, no Android SDK.
val backendOnly = providers.gradleProperty("backendOnly").isPresent
val adminWebOnly = providers.gradleProperty("adminWebOnly").isPresent

if (!adminWebOnly) {
    include(":backend")
}
if (!backendOnly && !adminWebOnly) {
    include(":sharedUI")
    include(":androidApp")
    include(":desktopApp")
    include(":webApp")
    include(":tools:wordlists") // word-list builder: uses :sharedData's tokenizer
}
if (!backendOnly) {
    include(":adminWeb") // Kobweb staff panel, exported statically and served by the backend at /admin
}

