plugins {
    id("recon.spring-library-conventions")
}

// Read side against the legacy core-banking Oracle instance.
// MyBatis (not JPA) on purpose: the queries here are hand-tuned Oracle SQL against a schema we
// do not own and cannot migrate, which is exactly the case where an ORM gets in the way.
dependencies {
    implementation(project(":backend:recon-domain"))
    implementation(libs.mybatis.starter)
    implementation(libs.boot.jdbc)
    implementation(libs.boot.validation)
    runtimeOnly(libs.ojdbc)

    annotationProcessor(libs.boot.configproc)

    testImplementation(libs.boot.test)
    testImplementation(libs.mybatis.test)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
