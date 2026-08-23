rootProject.name = "spring-react-recon"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        // Oracle's JDBC driver is on Maven Central; no extra repo needed since 21c.
    }
}

// Backend is deliberately split so that dependency direction is enforceable (see ArchUnit tests):
//   domain  <-  legacy / core  <-  batch  <-  api
include(
    ":backend:recon-domain",
    ":backend:recon-legacy",
    ":backend:recon-core",
    ":backend:recon-batch",
    ":backend:recon-api",
)
