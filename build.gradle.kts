import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "1.4.21"
    id("com.google.protobuf") version "0.9.2"
}

group = "nsu"
version = "0.0.1"

application {
    mainClass.set("nsu.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // kotlin
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    // jwt
    implementation("com.auth0:java-jwt:4.0.0")
    // logback
    implementation("org.slf4j:slf4j-simple:${properties["slf4j_version"]}")
    implementation("io.github.oshai:kotlin-logging-jvm:${properties["kotlin_logging_version"]}")
    // protobuf + grpc
    implementation("io.grpc:grpc-stub:${properties["grpc_java_version"]}")
    implementation("io.grpc:grpc-kotlin-stub:${properties["grpc_kotlin_version"]}")
    implementation("io.grpc:grpc-protobuf:${properties["grpc_java_version"]}")
    implementation("com.google.protobuf:protobuf-java:${properties["protobuf_version"]}")
    implementation("com.google.protobuf:protobuf-java-util:${properties["protobuf_version"]}")
    implementation("com.google.protobuf:protobuf-kotlin:${properties["protobuf_version"]}")
    implementation("io.grpc:grpc-netty:${properties["grpc_java_version"]}")
    // exposed
    implementation("org.jetbrains.exposed:exposed-core:${properties["exposed_version"]}")
    implementation("org.jetbrains.exposed:exposed-crypt:${properties["exposed_version"]}")
    implementation("org.jetbrains.exposed:exposed-dao:${properties["exposed_version"]}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${properties["exposed_version"]}")

    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:${properties["exposed_version"]}")

    implementation("org.jetbrains.exposed:exposed-json:${properties["exposed_version"]}")
    implementation("org.jetbrains.exposed:exposed-money:${properties["exposed_version"]}")
    // postgres
    implementation("org.postgresql:postgresql:${properties["postgres_version"]}")
    // testing grpc
    testImplementation("io.grpc:grpc-testing:${properties["grpc_java_version"]}")
    // testing kotlin
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${properties["kotlin_version"]}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${properties["kotest_version"]}")
    testImplementation ("io.mockk:mockk:${properties["mockk_version"]}")
    testImplementation("io.kotest:kotest-runner-junit5:${properties["kotest_version"]}")
    testImplementation("io.kotest:kotest-assertions-core:${properties["kotest_version"]}")
    testImplementation("org.testcontainers:junit-jupiter:${properties["testcontainers_version"]}")
    testImplementation("org.testcontainers:postgresql:${properties["testcontainers_version"]}")
}

sourceSets {
    main {
        proto {
            srcDirs("generated/source/proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${properties["protoc_version"]}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${properties["grpc_java_version"]}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${properties["grpc_kotlin_version"]}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
            it.builtins {
                id("kotlin")
            }
        }
    }
    generatedFilesBaseDir = "$projectDir/src/generated"
}

tasks {
    processResources {
        dependsOn("generateProto")
    }
    withType<KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }
    withType<Test> {
        useJUnitPlatform()
    }
}


