plugins {
    id("recon.spring-library-conventions")
}

// The reconciliation job itself. Depends on both sides — Oracle reads and MySQL writes — but
// deliberately contains no SQL of its own: orchestration only.
dependencies {
    implementation(project(":backend:recon-domain"))
    implementation(project(":backend:recon-core"))
    implementation(project(":backend:recon-legacy"))
    implementation(libs.boot.batch)
    implementation(libs.boot.batch.jdbc)
    implementation(libs.boot.data.jpa)
    implementation(libs.boot.actuator)
    implementation(libs.boot.validation)

    annotationProcessor(libs.boot.configproc)

    testImplementation(libs.boot.test)
    testImplementation(libs.spring.batch.test)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
