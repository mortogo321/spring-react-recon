plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.versions)
}

// Formatting/lint is applied from the root so a single `./gradlew spotlessApply` covers the repo.
spotless {
    java {
        target("backend/**/src/**/*.java")
        // Without this the target tree is rooted at the repo and Gradle 9 flags every module's
        // build/ output as an undeclared input of spotlessJava.
        targetExclude("**/build/**", "**/.gradle/**")
        removeUnusedImports()
        importOrder("java", "javax", "jakarta", "org", "com", "")
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("sql") {
        target("backend/**/src/main/resources/db/**/*.sql", "docker/**/*.sql")
        targetExclude("**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Aggregate coverage/verification entry point used by CI.
tasks.register("verifyBackend") {
    group = "verification"
    description = "Runs check on every backend module."
    dependsOn(subprojects.filter { it.path.startsWith(":backend") }.map { "${it.path}:check" })
}
