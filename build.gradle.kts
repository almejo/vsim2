subprojects {
    apply(plugin = "java")
    apply(plugin = "pmd")

    group = "cl.almejo.vsim"
    version = "0.9"

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // -Xdoclint:reference: javac reports a broken {@link}/@see (reference not found) as an ERROR, not a warning,
        // so it fails the build without -Werror (which would also turn every -Xlint warning into an error). It is the
        // only doclint group on: no missing-comment or html noise. Main sources only (compileTestJava is not touched).
        // Not caught by javac: a link to an inaccessible member (private, a record component); the periodic IntelliJ
        // analysis finds those. Dangling and self-pointing Javadoc: SourceRulesTest.
        val doclint = if (name == "compileJava") listOf("-Xdoclint:reference") else emptyList()
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial") + doclint)
    }

    // PMD (Gradle's own plugin) on src/main only. The rules live in config/pmd/vsim-rules.xml and are added one by one
    // (never a whole category), so ruleSets stays empty: no default category sneaks in.
    extensions.configure<PmdExtension> {
        toolVersion = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("pmd").get().requiredVersion
        sourceSets = listOf(the<SourceSetContainer>()["main"])
        ruleSets = emptyList()
        ruleSetFiles = rootProject.files("config/pmd/vsim-rules.xml")
        isConsoleOutput = true
        isIgnoreFailures = false
    }

    tasks.withType<Test>().configureEach {
        // The source checks (PMD per module, CPD over the four) run with the tests: `test` does not run `check`, and
        // CI and the pre-commit command are `./gradlew test ...`. Both are up to date unless a source changes.
        dependsOn(tasks.named("pmdMain"), rootProject.tasks.named("cpdMain"))
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

// CPD (copy/paste detector) from the same PMD distribution, over src/main/java of the four modules together (a
// duplicate between two modules counts). No third-party plugin: a JavaExec of the PMD 7 command line.
// Switch: `vsim.cpd.failOnViolation` in gradle.properties (false until the calidad-codigo duplication round calibrates
// the threshold and turns it on); the threshold is `vsim.cpd.minimumTokens`. Exceptions in the source: `// CPD-OFF` ..
// `// CPD-ON` with the reason.
repositories { mavenCentral() }

val cpdClasspath: Configuration by configurations.creating

dependencies {
    val pmdVersion = versionCatalogs.named("libs").findVersion("pmd").get().requiredVersion
    cpdClasspath("net.sourceforge.pmd:pmd-cli:$pmdVersion")
    cpdClasspath("net.sourceforge.pmd:pmd-java:$pmdVersion")
}

tasks.register<JavaExec>("cpdMain") {
    group = "verification"
    description = "Looks for duplicated code in src/main/java of core, automation, mcp and ui (PMD's CPD)."
    val moduleNames = listOf("core", "automation", "mcp", "ui")
    val sourceDirectories = moduleNames.map { moduleName -> layout.projectDirectory.dir("$moduleName/src/main/java") }
    val report = layout.buildDirectory.file("reports/cpd/cpd.txt")
    val failOnViolation = providers.gradleProperty("vsim.cpd.failOnViolation").map { it.toBoolean() }.orElse(false)
    val minimumTokens = providers.gradleProperty("vsim.cpd.minimumTokens").orElse("100")
    inputs.files(sourceDirectories.map { directory -> fileTree(directory) { include("**/*.java") } })
        .withPropertyName("sources").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("minimumTokens", minimumTokens)
    inputs.property("failOnViolation", failOnViolation)
    outputs.file(report)
    classpath = cpdClasspath
    mainClass.set("net.sourceforge.pmd.cli.PmdCli")
    isIgnoreExitValue = !failOnViolation.get()
    doFirst { report.get().asFile.parentFile.mkdirs() }
    argumentProviders.add(CommandLineArgumentProvider {
        buildList {
            add("cpd")
            add("--language"); add("java")
            add("--minimum-tokens"); add(minimumTokens.get())
            add("--format"); add("text")
            add("--encoding"); add("UTF-8")
            add("--report-file"); add(report.get().asFile.absolutePath)
            sourceDirectories.forEach { directory -> add("--dir"); add(directory.asFile.absolutePath) }
        }
    })
}
