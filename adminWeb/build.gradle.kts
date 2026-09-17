import com.varabyte.kobweb.gradle.application.BuildTarget
import com.varabyte.kobweb.gradle.application.kobwebBuildTarget
import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication
import kotlinx.html.link

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kobweb.application)
}

// Kobweb looks for @Page composables under "<group>.pages".
group = "uz.abumme.harfgame.admin"
version = "1.0-SNAPSHOT"

kobweb {
    app {
        index {
            description.set("Harf staff panel")
            lang.set("ru")
            faviconPath.set("/favicon.svg")
            head.add {
                // Onest (SIL OFL 1.1), bundled: the panel loads nothing from third parties.
                link(rel = "stylesheet", href = basePath.prependTo("/fonts/onest/faces.css"))
            }
        }
        // Where the admin API lives. A RELEASE export is served by the backend itself, behind Caddy's /harf prefix,
        // on the same origin; a DEBUG build runs on the Kobweb dev server and calls a local backend on another port
        // (development CORS), so it must send credentials explicitly.
        // `-PadminApiBase=http://localhost:8085` points a DEBUG build at a backend on another port.
        val release = project.kobwebBuildTarget == BuildTarget.RELEASE
        val debugApiBase = providers.gradleProperty("adminApiBase").getOrElse("http://localhost:8080")
        globals.putAll(
            mapOf(
                "apiBase" to if (release) "/harf" else debugApiBase,
                "apiCredentials" to if (release) "same-origin" else "include",
            ),
        )
        export {
            includeSourceMap.set(false)
        }
    }
}

tasks.named("kobwebExport") {
    // Gradle's kobwebExport starts a Kobweb server to snapshot pages and leaves it running (the kobweb CLI stops it
    // separately), which would hold a port and a JVM after CI or Docker builds. Stop it once the export is done.
    finalizedBy("kobwebStop")

    // The export must call the API on its own origin. Once, after a failed incremental build in continuous mode, a
    // stale DEBUG klib leaked `http://localhost:…` into an export; refuse to publish such a site.
    val exportedScript = layout.projectDirectory.file(".kobweb/site/adminWeb.js")
    doLast {
        val script = exportedScript.asFile
        check(script.isFile) { "The export did not produce ${script.path}" }
        check("http://localhost" !in script.readText()) {
            "The exported adminWeb.js points at a local development API. Re-run the export with --rerun-tasks."
        }
    }
}

kotlin {
    configAsKobwebApplication("adminWeb")
    // jsTest covers the panel's pure logic in headless Chrome through Karma; GitHub's Ubuntu runners ship Chrome.
    js {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(libs.compose.runtime.kobweb)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.silk.icons.lucide)
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":sharedData"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
