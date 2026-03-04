import com.github.spotbugs.snom.SpotBugsTask
import com.google.protobuf.gradle.id

plugins {
    id("java")
    id("checkstyle")
    id("pmd")
    id("com.github.spotbugs") version "6.4.8"
    id("com.google.protobuf") version "0.9.4"
}

group = "hse.ru"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:4.27.2")
    implementation("io.grpc:grpc-protobuf:1.79.0")
    implementation("io.grpc:grpc-stub:1.79.0")
    implementation("io.grpc:grpc-netty-shaded:1.79.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.27.2"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.79.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc")
            }
        }
    }
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = file("config/checkstyle/checkstyle.xml")
}

pmd {
    toolVersion = "7.16.0"
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
}

spotbugs {
    toolVersion = "4.9.3"
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/generated/**")
}

tasks.withType<Pmd>().configureEach {
    exclude("**/generated/**")
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
}