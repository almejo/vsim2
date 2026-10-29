subprojects {
    apply(plugin = "java")

    group = "cl.almejo.vsim"
    version = "0.5"

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Two test JVMs per module (with org.gradle.parallel): the fastest setting measured on 2026-10-28; more forks
        // were slower and made timing-sensitive tests flaky.
        maxParallelForks = 2
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }
}
