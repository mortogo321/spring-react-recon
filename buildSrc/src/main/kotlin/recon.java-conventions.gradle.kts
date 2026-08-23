// Baseline applied to every JVM module: toolchain, compiler hygiene, test wiring, coverage.
plugins {
    `java-library`
    jacoco
}

group = "io.github.mortogo321.recon"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(26) }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 26
    // -parameters keeps constructor parameter names for Spring / Jackson binding on records.
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-serial,-processing,-this-escape"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Virtual-thread friendly: the suite is IO bound against embedded databases.
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.55".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn(tasks.named("jacocoTestCoverageVerification")) }
