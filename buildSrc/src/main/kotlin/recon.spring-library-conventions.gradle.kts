// For Spring-aware *library* modules: they consume the Boot BOM but never produce a bootJar.
plugins {
    id("recon.java-conventions")
}

dependencies {
    val bootVersion = "4.1.1"
    add("implementation", platform("org.springframework.boot:spring-boot-dependencies:$bootVersion"))
    add("annotationProcessor", platform("org.springframework.boot:spring-boot-dependencies:$bootVersion"))
    add("testImplementation", platform("org.springframework.boot:spring-boot-dependencies:$bootVersion"))
}
