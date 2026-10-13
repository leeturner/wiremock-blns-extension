plugins {
    `java-library`
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    alias(libs.plugins.maven.publish)
}

repositories { mavenCentral() }

dependencies {
    compileOnly(libs.wiremock.core) // brings handlebars at compile time

    testImplementation(libs.wiremock)
    testImplementation(libs.wiremock.junit5)
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}

// Keep the shadow JAR out of the main artifact; it is published as its own artifactId below.
shadow { addShadowVariantIntoJavaComponent = false }

tasks.shadowJar {
    archiveClassifier = "standalone"
    relocate("com.github.jknack", "wiremock.com.github.jknack")
}

// Runs the standalone JAR inside wiremock-standalone, which relocates Handlebars.
val wiremockStandalone = configurations.create("wiremockStandalone") { isTransitive = false }

dependencies { wiremockStandalone(libs.wiremock.standalone) }

testing {
    suites {
        register<JvmTestSuite>("smokeTest") {
            dependencies {
                implementation(platform(libs.junit.bom))
                implementation("org.junit.jupiter:junit-jupiter")
                implementation(libs.assertj)
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
            targets.all {
                testTask.configure {
                    useJUnitPlatform()
                    inputs.files(tasks.shadowJar, wiremockStandalone)
                    val extensionJar = tasks.shadowJar.flatMap { it.archiveFile }
                    val standaloneJar = wiremockStandalone.elements.map { it.single().asFile }
                    doFirst {
                        systemProperty("extensionJar", extensionJar.get().asFile.path)
                        systemProperty("standaloneJar", standaloneJar.get().path)
                    }
                }
            }
        }
    }
}

tasks.check { dependsOn(testing.suites.named("smokeTest")) }

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get())
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    // group from gradle.properties; artifactId from rootProject.name. coordinates() is
    // not called, because it may also overwrite the standalone publication's artifactId.
    pom {
        name = "WireMock BLNS Extension"
        description = "WireMock extension that adds a Handlebars helper for strings from the Big List of Naughty Strings"
        url = "https://github.com/leeturner/wiremock-blns-extension"
        inceptionYear = "2026"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "leeturner"
                name = "Lee Turner"
                url = "https://leeturner.me"
            }
        }
        scm {
            url = "https://github.com/leeturner/wiremock-blns-extension"
            connection = "scm:git:git://github.com/leeturner/wiremock-blns-extension.git"
            developerConnection = "scm:git:ssh://git@github.com/leeturner/wiremock-blns-extension.git"
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("standalone") {
            artifactId = "wiremock-blns-extension-standalone"
            artifact(tasks.shadowJar) { classifier = null }
        }
    }
}

// Central requires sources and javadoc for every artifactId. The maven-publish plugin
// registers these tasks after evaluation, so attach them to the standalone publication then.
afterEvaluate {
    publishing.publications.named<MavenPublication>("standalone") {
        artifact(tasks.named("sourcesJar"))
        artifact(tasks.named("plainJavadocJar"))
    }
}
