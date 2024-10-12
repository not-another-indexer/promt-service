import com.google.protobuf.gradle.id

val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "1.4.21"
    id("io.ktor.plugin") version "3.0.0-rc-2"
    id("io.ktor.plugin") version "3.0.0-rc-1"
    id("com.google.protobuf") version "0.9.2"
}

group = "nsu"
version = "0.0.1"

application {
    mainClass.set("nsu.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // ktor
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-serialization-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-swagger")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    // logback
    implementation("ch.qos.logback:logback-classic:$logback_version")
    // protobuf
    implementation("io.grpc:grpc-kotlin-stub:1.2.0")
    implementation("io.grpc:grpc-netty-shaded:1.53.0")
    implementation("io.grpc:grpc-protobuf:1.53.0")
    implementation("io.grpc:grpc-stub:1.53.0")
    implementation("com.google.protobuf:protobuf-java:3.23.4")
    // junit
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
