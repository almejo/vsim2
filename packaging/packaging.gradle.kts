// Distribución (spec §3): VSim with its own Java runtime. Applied from ui/build.gradle.kts, so `project` is :ui.
//
//   ./gradlew :ui:runtimeImage           jlink → ui/build/packaging/runtime
//   ./gradlew :ui:jpackageImage          jpackage app-image → ui/build/packaging/image/VSim2 (VSim2.app on macOS)
//   ./gradlew :ui:nativePackages         this system's installers → ui/build/distribution/
//   ./gradlew :ui:packagedRuntimeCheck   PackagedRuntimeCheck with the image's own runtime
//
// Properties: -PvsimPackageArchitecture=x64|arm64 (default: this machine's), -PvsimPackageFormat=<format> for
// jpackageImage (default: zip on Windows, dmg on macOS, appimage on Linux), -PvsimInstalledApplication=<folder> to
// run packagedRuntimeCheck on an installed package instead of the image, -PvsimPackagingJdk=<JDK folder> to use a
// given JDK's jlink and jpackage instead of the Java 21 toolchain. No Gradle plugin: jlink and jpackage run as Exec
// tasks (workflow.md §4).

import java.nio.file.Files
import java.nio.file.StandardCopyOption

val applicationName = "VSim2"
val applicationVersion = project.version.toString()
val applicationVendor = "Alejandro Vera"
val applicationDescription = "Simulador de circuitos digitales para enseñar"
val applicationHomepage = "https://github.com/almejo/vsim2"
val mainClassName = "cl.almejo.vsim.ui.app.Main"
val packagedRuntimeCheckClass = "cl.almejo.vsim.ui.app.PackagedRuntimeCheck"
val windowsUpgradeUuid = "6b0f3c1e-2d8a-4f57-9a43-0c5e8f1d7a20"
val packagingDirectory = rootProject.layout.projectDirectory.dir("packaging")
val jarFileName = "vsim2-$applicationVersion.jar"

/**
 * The runtime's modules. Derived on 2026-11-03 with
 * `jdeps --multi-release 21 --ignore-missing-deps --print-module-deps ui/build/libs/vsim2-0.5.jar`
 * (java.base, java.desktop, java.management, java.naming, java.net.http, java.prefs, java.security.jgss, java.sql),
 * plus what is only reached by reflection, services or the system: java.logging (named although java.sql brings it),
 * jdk.localedata (Spanish month and number names; PackagedRuntimeCheck checks it), jdk.charsets (file names and
 * consoles in the less common code pages), jdk.crypto.ec (TLS with elliptic curves, the usual kind),
 * jdk.accessibility (the Windows Java Access Bridge for screen readers) and jdk.unsupported (libraries that probe
 * sun.misc.Unsafe by reflection). When a dependency changes, run jdeps again and ./gradlew :ui:packagedRuntimeCheck.
 */
val runtimeModules = listOf(
    "java.base",
    "java.desktop",
    "java.logging",
    "java.management",
    "java.naming",
    "java.net.http",
    "java.prefs",
    "java.security.jgss",
    "java.sql",
    "jdk.accessibility",
    "jdk.charsets",
    "jdk.crypto.ec",
    "jdk.localedata",
    "jdk.unsupported",
)
/** The locales whose data jdk.localedata keeps: VSim speaks Spanish and English. */
val runtimeLocales = "en,es"

/** The formats a launcher can name in -Dvsim.packageFormat (AboutInformation#packageFormat; absent = jar). */
val packageFormats = setOf("msi", "zip", "deb", "rpm", "appimage", "flatpak", "snap", "dmg")

/**
 * The hidden bundle version of a macOS package: macOS refuses a version that starts with 0, so before 1.0 the bundle
 * says 1.0.<minor> (VSim 0.6 → 1.0.6; macOS replaces the app on copy, so it never clashes with the real 1.0), and the
 * version itself from 1.0 on.
 */
fun macBundleVersion(version: String): String {
    val parts = version.split('.')
    require(parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) }) {
        "the version must be numbers separated by dots: $version"
    }
    if (parts.first() != "0") {
        return version
    }
    require(parts.size == 2) { "a 0.x version must have two numbers to fit macOS's three: $version" }
    return "1.0.${parts[1]}"
}
extra["macBundleVersion"] = macBundleVersion(applicationVersion)

/** Checks [macBundleVersion]; `test` depends on it, so the usual test run covers the rule. */
val packagingVersionRules = tasks.register("packagingVersionRules") {
    group = "verification"
    description = "Checks the macOS bundle version rule."
    doLast {
        val expected = mapOf("0.6" to "1.0.6", "0.12" to "1.0.12", "1.0" to "1.0", "1.2" to "1.2", "2.1.3" to "2.1.3")
        expected.forEach { (version, bundleVersion) ->
            check(macBundleVersion(version) == bundleVersion) {
                "macBundleVersion($version) is ${macBundleVersion(version)}, expected $bundleVersion"
            }
        }
        listOf("0.6.1", "0.x", "").forEach { version ->
            check(runCatching { macBundleVersion(version) }.isFailure) { "macBundleVersion($version) must fail" }
        }
    }
}
tasks.named("test") { dependsOn(packagingVersionRules) }

enum class PackagingSystem { LINUX, WINDOWS, MAC }

val operatingSystemName = System.getProperty("os.name").lowercase()
val packagingSystem = when {
    operatingSystemName.contains("windows") -> PackagingSystem.WINDOWS
    operatingSystemName.contains("mac") -> PackagingSystem.MAC
    else -> PackagingSystem.LINUX
}

/** x64 or arm64: -PvsimPackageArchitecture, else the architecture of the JVM running Gradle. */
val packageArchitecture: String = providers.gradleProperty("vsimPackageArchitecture").orNull
    ?: when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        else -> "x64"
    }
require(packageArchitecture in setOf("x64", "arm64")) {
    "vsimPackageArchitecture must be x64 or arm64, not $packageArchitecture"
}
val debianArchitecture = if (packageArchitecture == "arm64") "arm64" else "amd64"
val rpmArchitecture = if (packageArchitecture == "arm64") "aarch64" else "x86_64"

/** The JDK whose jlink and jpackage build the packages: -PvsimPackagingJdk, else the Java 21 toolchain. */
val packagingJdk: Provider<File> = providers.gradleProperty("vsimPackagingJdk").map { path -> file(path) }
    .orElse(extensions.getByType<JavaToolchainService>()
        .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
        .map { launcher -> launcher.metadata.installationPath.asFile })

fun jdkTool(name: String): String {
    val suffix = if (packagingSystem == PackagingSystem.WINDOWS) ".exe" else ""
    val tool = packagingJdk.get().resolve("bin/$name$suffix")
    check(tool.isFile) { "$tool is missing: give a full JDK 21 with -PvsimPackagingJdk=<folder>" }
    return tool.absolutePath
}

val packagingBuildDirectory = layout.buildDirectory.dir("packaging")
val runtimeDirectory = packagingBuildDirectory.map { directory -> directory.dir("runtime") }
val inputDirectory = packagingBuildDirectory.map { directory -> directory.dir("input") }
val imageDirectory = packagingBuildDirectory.map { directory -> directory.dir("image") }
val distributionDirectory = layout.buildDirectory.dir("distribution")
val executableJar = tasks.named<Jar>("executableJar")

/** jpackage's --input holds the executable jar alone (it copies the whole folder into the package). */
val packagingInput = tasks.register<Sync>("packagingInput") {
    description = "Copies the executable jar into jpackage's input folder."
    from(executableJar)
    into(inputDirectory)
}

val runtimeImage = tasks.register<Exec>("runtimeImage") {
    group = "distribution"
    description = "Builds VSim's own Java runtime with jlink."
    inputs.property("modules", runtimeModules)
    inputs.property("locales", runtimeLocales)
    inputs.property("jdk", packagingJdk.map { jdk -> jdk.absolutePath })
    outputs.dir(runtimeDirectory)
    doFirst {
        delete(runtimeDirectory)
        executable = jdkTool("jlink")
        val jmods = packagingJdk.get().resolve("jmods")
        check(jmods.isDirectory) { "$jmods is missing: jlink needs a JDK with its jmods" }
        args(
            "--module-path", jmods.absolutePath,
            "--add-modules", runtimeModules.joinToString(","),
            "--include-locales", runtimeLocales,
            "--strip-debug", "--no-header-files", "--no-man-pages",
            "--output", runtimeDirectory.get().asFile.absolutePath,
        )
    }
}

/** The arguments every jpackage run shares: the application, its runtime and the format its launcher reports. */
fun commonJpackageArguments(type: String, format: String, version: String, destination: File): List<String> {
    require(format in packageFormats) { "unknown package format $format, expected one of $packageFormats" }
    return listOf(
        "--type", type,
        "--name", applicationName,
        "--app-version", version,
        "--vendor", applicationVendor,
        "--description", applicationDescription,
        "--input", inputDirectory.get().asFile.absolutePath,
        "--main-jar", jarFileName,
        "--main-class", mainClassName,
        "--runtime-image", runtimeDirectory.get().asFile.absolutePath,
        "--icon", iconFile().absolutePath,
        "--java-options", "-Dvsim.packageFormat=$format",
        "--dest", destination.absolutePath,
    )
}

fun iconFile(): File = packagingDirectory.file(
    when (packagingSystem) {
        PackagingSystem.WINDOWS -> "icons/io.github.almejo.vsim2.ico"
        PackagingSystem.MAC -> "icons/io.github.almejo.vsim2.icns"
        PackagingSystem.LINUX -> "icons/io.github.almejo.vsim2.png"
    }).asFile

/** What an installer (not an app image) adds: the .vsim association and the system's own options. */
fun installerArguments(): List<String> {
    val common = listOf(
        "--file-associations", packagingDirectory.file("file-associations/vsim.properties").asFile.absolutePath,
        "--about-url", applicationHomepage,
    )
    val system = when (packagingSystem) {
        PackagingSystem.WINDOWS -> listOf(
            "--win-per-user-install", "--win-menu", "--win-menu-group", "VSim 2", "--win-shortcut",
            "--win-upgrade-uuid", windowsUpgradeUuid,
        )
        PackagingSystem.MAC -> macArguments()
        PackagingSystem.LINUX -> listOf(
            "--linux-package-name", "vsim2",
            "--linux-app-category", "Education",
            "--linux-menu-group", "Education;Electronics;",
            "--linux-shortcut",
            "--linux-rpm-license-type", "GPL-3.0-or-later",
            // our preinst (the menu directory jpackage's postinst needs) and desktop entry (the window class)
            "--resource-dir", packagingDirectory.dir("linux/jpackage").asFile.absolutePath,
        )
    }
    return common + system
}

fun macArguments(): List<String> = listOf(
    "--mac-package-identifier", "io.github.almejo.vsim2",
    "--mac-package-name", "VSim 2",
)

/** A jpackage run of [type] into its own folder under build/packaging/native, so its one output is easy to find. */
fun registerJpackage(taskName: String, type: String, format: String) = tasks.register<Exec>(taskName) {
    description = "Builds the $type package with jpackage."
    val destination = packagingBuildDirectory.map { directory -> directory.dir("native/$type") }
    val version = if (packagingSystem == PackagingSystem.MAC) macBundleVersion(applicationVersion)
    else applicationVersion
    dependsOn(runtimeImage, packagingInput)
    inputs.dir(runtimeDirectory)
    inputs.dir(inputDirectory)
    inputs.dir(packagingDirectory)
    outputs.dir(destination)
    doFirst {
        delete(destination)
        executable = jdkTool("jpackage")
    }
    args(commonJpackageArguments(type, format, version, destination.get().asFile) + installerArguments())
}

/** The default format of the app image: what is built from it on this system without other tools. */
val imageFormat: String = providers.gradleProperty("vsimPackageFormat").orNull ?: when (packagingSystem) {
    PackagingSystem.WINDOWS -> "zip"
    PackagingSystem.MAC -> "dmg"
    PackagingSystem.LINUX -> "appimage"
}

val jpackageImage = tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Builds VSim's application folder with its own runtime (jpackage app-image)."
    val version = if (packagingSystem == PackagingSystem.MAC) macBundleVersion(applicationVersion)
    else applicationVersion
    dependsOn(runtimeImage, packagingInput)
    inputs.dir(runtimeDirectory)
    inputs.dir(inputDirectory)
    inputs.property("format", imageFormat)
    outputs.dir(imageDirectory)
    doFirst {
        delete(imageDirectory)
        executable = jdkTool("jpackage")
    }
    val macOnly = if (packagingSystem == PackagingSystem.MAC) macArguments() else listOf()
    args(commonJpackageArguments("app-image", imageFormat, version, imageDirectory.get().asFile) + macOnly)
}

/** Where a VSim folder (the image, or an installed package) keeps its runtime's java and the jar. */
fun runtimeJava(root: File): File = when (packagingSystem) {
    PackagingSystem.WINDOWS -> root.resolve("runtime/bin/java.exe")
    PackagingSystem.MAC -> root.resolve("Contents/runtime/Contents/Home/bin/java")
    PackagingSystem.LINUX -> root.resolve("lib/runtime/bin/java")
}

fun applicationJar(root: File): File = when (packagingSystem) {
    PackagingSystem.WINDOWS -> root.resolve("app/$jarFileName")
    PackagingSystem.MAC -> root.resolve("Contents/app/$jarFileName")
    PackagingSystem.LINUX -> root.resolve("lib/app/$jarFileName")
}

val installedApplication: String? = providers.gradleProperty("vsimInstalledApplication").orNull

tasks.register<Exec>("packagedRuntimeCheck") {
    group = "verification"
    description = "Runs PackagedRuntimeCheck with the image's own runtime (or -PvsimInstalledApplication's)."
    val root = if (installedApplication != null) file(installedApplication)
    else imageDirectory.get().asFile.resolve(if (packagingSystem == PackagingSystem.MAC) "$applicationName.app"
    else applicationName)
    if (installedApplication == null) {
        dependsOn(jpackageImage)
    }
    doFirst {
        val java = runtimeJava(root)
        check(java.isFile) { "$java is missing: is $root a VSim folder?" }
        executable = java.absolutePath
    }
    args("-cp", applicationJar(root).absolutePath, packagedRuntimeCheckClass)
}

/** Copies jpackage's one output of [type] to [finalName] in ui/build/distribution. */
fun copyToDistribution(type: String, extension: String, finalName: String) {
    val folder = packagingBuildDirectory.get().dir("native/$type").asFile
    val built = folder.listFiles { file -> file.name.endsWith(".$extension") }.orEmpty()
    check(built.size == 1) { "expected one .$extension in $folder, found ${built.map(File::getName)}" }
    val target = distributionDirectory.get().file(finalName).asFile
    target.parentFile.mkdirs()
    Files.copy(built.single().toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    logger.lifecycle("Built $target")
}

val nativePackages = tasks.register("nativePackages") {
    group = "distribution"
    description = "Builds this system's installers into ui/build/distribution."
}

when (packagingSystem) {
    PackagingSystem.LINUX -> {
        val deb = registerJpackage("jpackageDeb", "deb", "deb")
        val rpmbuildFound = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { directory -> File(directory, "rpmbuild").canExecute() }
        val rpm = registerJpackage("jpackageRpm", "rpm", "rpm")
        rpm.configure { onlyIf("rpmbuild is installed") { rpmbuildFound } }
        nativePackages.configure {
            dependsOn(deb, rpm)
            doLast {
                copyToDistribution("deb", "deb", "$applicationName-$applicationVersion-linux-$debianArchitecture.deb")
                if (rpmbuildFound) {
                    copyToDistribution("rpm", "rpm",
                        "$applicationName-$applicationVersion-linux-$rpmArchitecture.rpm")
                } else {
                    logger.warn("No rpm: rpmbuild is not installed (sudo apt-get install rpm)")
                }
            }
        }
    }
    PackagingSystem.WINDOWS -> {
        val msi = registerJpackage("jpackageMsi", "msi", "msi")
        val portableImageDirectory = packagingBuildDirectory.map { directory -> directory.dir("portable") }
        val portableImage = tasks.register<Exec>("jpackagePortableImage") {
            description = "Builds the application folder of the portable zip."
            dependsOn(runtimeImage, packagingInput)
            inputs.dir(runtimeDirectory)
            inputs.dir(inputDirectory)
            outputs.dir(portableImageDirectory)
            doFirst {
                delete(portableImageDirectory)
                executable = jdkTool("jpackage")
            }
            args(commonJpackageArguments("app-image", "zip", applicationVersion,
                portableImageDirectory.get().asFile))
        }
        val portableZip = tasks.register<Zip>("portableZip") {
            description = "Zips the portable application folder."
            dependsOn(portableImage)
            from(portableImageDirectory)
            destinationDirectory.set(distributionDirectory)
            archiveFileName.set("$applicationName-$applicationVersion-windows-$packageArchitecture-portable.zip")
        }
        nativePackages.configure {
            dependsOn(msi, portableZip)
            doLast {
                copyToDistribution("msi", "msi", "$applicationName-$applicationVersion-windows-$packageArchitecture.msi")
            }
        }
    }
    PackagingSystem.MAC -> {
        val dmg = registerJpackage("jpackageDmg", "dmg", "dmg")
        nativePackages.configure {
            dependsOn(dmg)
            doLast {
                copyToDistribution("dmg", "dmg", "$applicationName-$applicationVersion-macos-$packageArchitecture.dmg")
            }
        }
    }
}
