// IA.1: the MCP tools (schemas, error mapping) and the Streamable HTTP server on loopback. Depends on automation and
// the MCP Java SDK; never on ui.
plugins {
    `java-library`
}

dependencies {
    api(project(":automation"))
    implementation(libs.mcp.core)
    implementation(libs.mcp.json.jackson2)
    implementation(libs.jetty.servlet)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.logback.classic)
}
