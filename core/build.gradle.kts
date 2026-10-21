plugins {
    application
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

application {
    mainClass.set("cl.almejo.vsim.core.tools.SimulateCommandLine")
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
