plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"

}

group = "com.example.catering"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "com.example.catering.MainKt"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)


    implementation("org.jetbrains.exposed:exposed-core:1.5.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.5.0")
    implementation("org.postgresql:postgresql:42.7.12")
    implementation("org.jetbrains.exposed:exposed-java-time:1.5.0")



    implementation("org.eclipse.angus:jakarta.mail:2.0.5")
    implementation("com.password4j:password4j:1.8.4")
    implementation("io.ktor:ktor-server-rate-limit-jvm:3.5.2")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:3.5.2")

    implementation("org.flywaydb:flyway-core:13.11.1")
    implementation("org.flywaydb:flyway-database-postgresql:13.11.1")

    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.5.2")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
