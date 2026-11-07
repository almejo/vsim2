subprojects {
    apply(plugin = "java")

    group = "cl.almejo.vsim"
    version = "0.7"

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
        // On Linux, measure text with DejaVu Sans like GitHub's Ubuntu runners (gradle/test-fonts.conf); the JDK caches
        // the system's fonts under user.home, so the test JVMs get their own home in build/ to read the new mapping.
        val dejaVu = File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        if (System.getProperty("os.name").startsWith("Linux") && dejaVu.exists()) {
            val fonts = rootProject.file("gradle/test-fonts.conf")
            inputs.file(fonts).withPropertyName("testFonts").withPathSensitivity(PathSensitivity.NONE)
            environment("FONTCONFIG_FILE", fonts.absolutePath)
            systemProperty("user.home", layout.buildDirectory.dir("test-home").get().asFile.absolutePath)
        }
    }
}
