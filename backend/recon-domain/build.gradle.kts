plugins {
    id("recon.java-conventions")
}

// The domain module is deliberately framework-free: no Spring, no JPA, no MyBatis.
// ArchUnit (in :backend:recon-api) fails the build if that ever regresses.
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Pure logic: hold this module to a much higher bar than the wiring modules.
// Rules stack on top of the repo-wide baseline in recon.java-conventions; the stricter one wins.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
