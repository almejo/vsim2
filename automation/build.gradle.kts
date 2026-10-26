// IA.1: the domain API the AI works with (documents, tasks, edits, reads, simulation receipts) and the host the
// editor implements. Depends on core only: never on Swing, never on the MCP SDK.
plugins {
    `java-library`
    `java-test-fixtures` // FakeAutomationHost, the base of the fake hosts of this module's, mcp's and ui's tests
}

dependencies {
    api(project(":core"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)
}
