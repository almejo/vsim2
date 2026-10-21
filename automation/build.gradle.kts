// IA.1: the domain API the AI works with (documents, tasks, edits, reads, simulation receipts) and the host the
// editor implements. Depends on core only: never on Swing, never on the MCP SDK.
plugins {
    `java-library`
}

dependencies {
    api(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)
}
