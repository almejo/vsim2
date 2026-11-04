// Test helpers other modules reuse (DocumentFixtures, ExampleDefinitions) live in src/testFixtures.
plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)
}

// The app version (from the root build) as a resource, so a saved file can say which VSim wrote it.
val generateVersionResource = tasks.register("generateVersionResource") {
    val versionText = project.version.toString()
    val outputDirectory = layout.buildDirectory.dir("generated/resources/version")
    inputs.property("version", versionText)
    outputs.dir(outputDirectory)
    doLast {
        val file = outputDirectory.get().file("cl/almejo/vsim/core/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$versionText\n")
    }
}

sourceSets.named("main") {
    resources.srcDir(generateVersionResource)
}

// The test tools driven by an environment variable (regenerate the bundled examples, migrate files, write the gate
// showcase, write the class circuits) work as a side effect: with one of them set the test task runs every time, never
// UP-TO-DATE.
tasks.named<Test>("test") {
    val toolVariables = listOf("VSIM_WRITE_BUNDLED", "VSIM_MIGRATE_PATHS", "VSIM_WRITE_SHOWCASE",
        "VSIM_WRITE_PARTS_SHOWCASE")
    if (toolVariables.any { variable -> System.getenv(variable) != null }) {
        outputs.upToDateWhen { false }
    }
    // The local run against a real Logisim file that cannot be in the repository (LogisimSampleTest): pass its path
    // with -Dvsim.logisim.sample=…; the test prints its report, so it always runs and shows its output.
    val logisimSample = System.getProperty("vsim.logisim.sample")
    if (logisimSample != null) {
        systemProperty("vsim.logisim.sample", logisimSample)
        // Optional: Logisim's own dump of that file (every end's place), to check LogisimEnds against it.
        System.getProperty("vsim.logisim.sampleDump")?.let { systemProperty("vsim.logisim.sampleDump", it) }
        // Optional: where LogisimImporterSampleTest saves the imported circuit (never inside the repository).
        System.getProperty("vsim.logisim.sampleOutput")?.let { systemProperty("vsim.logisim.sampleOutput", it) }
        // Optional: where LogisimExporterSampleTest writes the sample exported back to Logisim (never in the repository).
        System.getProperty("vsim.logisim.exportOutput")?.let { systemProperty("vsim.logisim.exportOutput", it) }
        testLogging.showStandardStreams = true
        outputs.upToDateWhen { false }
    }
}
