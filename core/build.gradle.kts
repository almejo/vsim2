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
// showcase) work as a side effect: with one of them set the test task runs every time, never UP-TO-DATE.
tasks.named<Test>("test") {
    val toolVariables = listOf("VSIM_WRITE_BUNDLED", "VSIM_MIGRATE_PATHS", "VSIM_WRITE_SHOWCASE")
    if (toolVariables.any { variable -> System.getenv(variable) != null }) {
        outputs.upToDateWhen { false }
    }
}
