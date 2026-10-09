plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.flatlaf)
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
