plugins {
    id("recon.java-conventions")
    alias(libs.plugins.spring.boot)
}

// The only deployable artefact. Everything else is a library, which is what keeps the
// dependency direction checkable (see ArchitectureTest).
dependencies {
    implementation(platform(libs.spring.boot.bom))
    annotationProcessor(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))

    implementation(project(":backend:recon-domain"))
    implementation(project(":backend:recon-core"))
    implementation(project(":backend:recon-legacy"))
    implementation(project(":backend:recon-batch"))

    implementation(libs.boot.web)
    implementation(libs.boot.data.jpa)
    implementation(libs.boot.validation)
    implementation(libs.boot.security)
    implementation(libs.boot.oauth2.rs)
    implementation(libs.boot.cache)
    implementation(libs.boot.aspectj)
    implementation(libs.boot.flyway)
    implementation(libs.bundles.observability)
    implementation(libs.caffeine)
    implementation(libs.springdoc)

    runtimeOnly(libs.mysql)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.ojdbc)
    runtimeOnly(libs.h2)

    annotationProcessor(libs.boot.configproc)

    testImplementation(libs.boot.test)
    testImplementation(libs.boot.webmvc.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.batch.test)
    testImplementation(libs.archunit)
    testImplementation(libs.boot.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mysql)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "recon-api.jar"
}

// Integration tests that need Docker are tagged and excluded from the default `test` task so a
// laptop without a running daemon still gets a green build.
tasks.test {
    useJUnitPlatform { excludeTags("docker") }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs Testcontainers-backed tests against real MySQL (requires Docker)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("docker") }
    shouldRunAfter(tasks.test)
}
