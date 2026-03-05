import com.github.spotbugs.snom.SpotBugsTask
import com.google.protobuf.gradle.id
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("java")
    id("maven-publish")
    id("checkstyle")
    id("pmd")
    id("com.github.spotbugs") version "6.4.8"
    id("com.google.protobuf") version "0.9.4"
}

group = "hse.ru"
version = (findProperty("version") as String?) ?: "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:4.27.2")
    implementation("io.grpc:grpc-protobuf:1.79.0")
    implementation("io.grpc:grpc-stub:1.79.0")
    implementation("io.grpc:grpc-netty-shaded:1.79.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
}

spotbugs {
    toolVersion = "4.9.3"
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/generated/**")
    exclude("**/build/generated/**")
}

tasks.withType<Pmd>().configureEach {
    exclude("**/generated/**")
    exclude("**/build/generated/**")
    if (name != "pmdMain") {
        ruleSetFiles = files("config/pmd/pmd-test.xml")
        ruleSets = emptyList()
    }
}

tasks.named<Checkstyle>("checkstyleMain") {
    source = fileTree("src/main/java")
}

tasks.named<Checkstyle>("checkstyleTest") {
    source = fileTree("src/test/java")
}

tasks.named<Pmd>("pmdMain") {
    source = fileTree("src/main/java")
}

tasks.named<Pmd>("pmdTest") {
    source = fileTree("src/test/java")
}

tasks.withType<SpotBugsTask>().configureEach {
    excludeFilter.set(file("config/spotbugs/exclude-generated.xml"))
    reports.create("html") {
        required.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "java-dragonfly-image-puller"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            val repository = System.getenv("GITHUB_REPOSITORY") ?: "UsatovPavel/java-dragonfly-image-puller"
            url = uri("https://maven.pkg.github.com/$repository")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_PACKAGES_TOKEN")
            }
        }
    }
}