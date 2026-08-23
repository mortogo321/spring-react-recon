plugins {
    id("recon.spring-library-conventions")
}

// Application-owned side of the system: our MySQL schema, accessed through JPA because we do own
// this schema, we migrate it with Flyway, and the access patterns are ordinary aggregate CRUD.
dependencies {
    api(project(":backend:recon-domain"))
    implementation(libs.boot.data.jpa)
    implementation(libs.boot.validation)
    implementation(libs.boot.cache)
    implementation(libs.boot.json)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql)

    annotationProcessor(libs.boot.configproc)

    testImplementation(libs.boot.test)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
