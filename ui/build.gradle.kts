plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.miglayout.swing)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
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
}

// --- M4-D executable jar ---
// ./gradlew :ui:executableJar → ui/build/libs/vsim2-<version>.jar, runnable with java -jar.
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
    val runtimeClasspath = configurations.runtimeClasspath
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.get().filter { file -> file.name.endsWith(".jar") }.map { file -> zipTree(file) } }) {
        exclude(
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
