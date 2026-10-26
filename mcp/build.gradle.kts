// IA.1: the MCP tools (schemas, error mapping) and the Streamable HTTP server on loopback. Depends on automation and
// the MCP Java SDK; never on ui.
plugins {
    `java-library`
    `java-test-fixtures` // McpResults, shared by the tool tests here and the editor's AI scenarios
}

dependencies {
    api(project(":automation"))
    implementation(libs.mcp.core)
    implementation(libs.mcp.json.jackson2)
    implementation(libs.jetty.servlet)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testFixturesImplementation(libs.mcp.core)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":automation")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.logback.classic)
}
