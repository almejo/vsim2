import java.util.zip.ZipFile

plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":automation"))
    implementation(project(":mcp"))
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.miglayout.swing)
    implementation(libs.jfreesvg)
    implementation(libs.jfreepdf)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":automation")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.pdfbox)
    testImplementation(libs.mcp.core) // the SDK's MCP client, to talk to the server as Claude Code does
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("cl.almejo.vsim.ui.app.Main")
}

tasks.test {
    systemProperty("java.awt.headless", "true")
}

// ./gradlew :ui:run -PvsimLanguage=en
tasks.named<JavaExec>("run") {
    providers.gradleProperty("vsimLanguage").orNull?.let { systemProperty("vsim.language", it) }
    // IA.1 (temporary until the «IA» panel): ./gradlew :ui:run -Dvsim.ai=on (or -PvsimAi=on, or VSIM_AI=on)
    (providers.systemProperty("vsim.ai").orNull ?: providers.gradleProperty("vsimAi").orNull)
        ?.let { systemProperty("vsim.ai", it) }
    // IA.3: the AI's port for this session only, never remembered: -Dvsim.ai.port=47100 (or -PvsimAiPort=47100,
    // or VSIM_AI_PORT=47100, which the run inherits)
    (providers.systemProperty("vsim.ai.port").orNull ?: providers.gradleProperty("vsimAiPort").orNull)
        ?.let { systemProperty("vsim.ai.port", it) }
}

// --- M4-D executable jar ---
// ./gradlew :ui:executableJar → ui/build/libs/vsim2-<version>.jar, runnable with java -jar.

// IA.1: several jars declare providers of the same service (the MCP SDK finds its JSON mapper and schema validator
// this way), so the jar gets each META-INF/services file once, with the providers of every jar, instead of the first.
val mergeServiceFiles = tasks.register("mergeServiceFiles") {
    description = "Merges the META-INF/services files of every runtime jar."
    val runtimeClasspath = configurations.runtimeClasspath
    val outputDirectory = layout.buildDirectory.dir("generated/merged-services")
    inputs.files(runtimeClasspath)
    outputs.dir(outputDirectory)
    doLast {
        val providersByService = sortedMapOf<String, LinkedHashSet<String>>()
        runtimeClasspath.get().filter { file -> file.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { entry -> !entry.isDirectory && entry.name.startsWith("META-INF/services/") }
                    .forEach { entry ->
                        val providers = zip.getInputStream(entry).bufferedReader().readLines()
                            .map { line -> line.substringBefore('#').trim() }
                            .filter { line -> line.isNotEmpty() }
                        providersByService.getOrPut(entry.name.removePrefix("META-INF/services/")) { linkedSetOf() }
                            .addAll(providers)
                    }
            }
        }
        val servicesDirectory = outputDirectory.get().dir("META-INF/services").asFile
        servicesDirectory.deleteRecursively()
        servicesDirectory.mkdirs()
        providersByService.forEach { (service, providers) ->
            File(servicesDirectory, service).writeText(providers.joinToString("\n", postfix = "\n"))
        }
    }
}

val executableJar = tasks.register<Jar>("executableJar") {
    group = "distribution"
    description = "Builds a runnable jar with every runtime dependency merged in."
    archiveBaseName.set("vsim2")
    archiveClassifier.set("")
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to "VSim 2",
            "Implementation-Version" to project.version.toString(),
            "Multi-Release" to "true",
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(mergeServiceFiles)
    val runtimeClasspath = configurations.runtimeClasspath
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.get().filter { file -> file.name.endsWith(".jar") }.map { file -> zipTree(file) } }) {
        exclude(
            "META-INF/services/**",
            "META-INF/MANIFEST.MF",
            "META-INF/INDEX.LIST",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/*.EC",
            "module-info.class",
            "META-INF/versions/*/module-info.class",
        )
    }
}

// Checks the built jar; plain `test` skips these tests, `check` runs them.
val executableJarTest = tasks.register<Test>("executableJarTest") {
    group = "verification"
    description = "Checks the executable jar: manifest, bundles and merged dependencies."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("executable-jar") }
    systemProperty("java.awt.headless", "true")
    dependsOn(executableJar)
    val jarFile = executableJar.flatMap { jar -> jar.archiveFile }
    inputs.file(jarFile)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dvsim.executableJar=" + jarFile.get().asFile.absolutePath)
    })
}

tasks.test {
    useJUnitPlatform { excludeTags("executable-jar") }
}

tasks.check {
    dependsOn(executableJarTest)
}
