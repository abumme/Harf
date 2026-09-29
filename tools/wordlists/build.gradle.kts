plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("uz.abumme.harfgame.tools.wordlists.MainKt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The app's own Normalizer / Tokenizer / LaunchLanguages, so generated words tokenize exactly as in play.
    implementation(project(":sharedUI"))
    implementation(libs.kotlinx.serialization.json)
    // Hunspell dictionaries (spell check against the Kazakh one) and word counts from FineWeb-2's Parquet files.
    implementation(libs.lucene.analysis.common)
    implementation(libs.duckdb.jdbc)

    testImplementation(kotlin("test"))
}

tasks.named<JavaExec>("run") {
    // Arguments and output paths are relative to the repository root.
    workingDir = rootDir
}
