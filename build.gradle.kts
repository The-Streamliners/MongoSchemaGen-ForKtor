plugins {
    kotlin("jvm") version "1.9.21"
    `maven-publish`
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.20-1.0.14")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "streamliners"
            artifactId = "schemaGen"
            version = "1.0"

            from(components["java"])
        }
    }
}