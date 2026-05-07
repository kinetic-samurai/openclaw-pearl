plugins {
    kotlin("jvm") version "2.1.0"
    application
    kotlin("plugin.serialization") version "2.1.0"
}

group = "dev.openclaw.pearl"
version = "0.1.0"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.0.3")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    // Wire this in once the LiteRT-LM JVM artifact is confirmed in your environment.
    // implementation("com.google.ai.edge.litertlm:litertlm-jvm:latest.release")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.openclaw.pearl.MainKt")
}
