import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version("2.0.0")
    val dgtVersion = "2.13.0"
    id("dev.deftu.gradle.tools") version(dgtVersion)
    id("dev.deftu.gradle.tools.bloom") version(dgtVersion)
    id("dev.deftu.gradle.tools.shadow") version(dgtVersion)
    id("dev.deftu.gradle.tools.resources") version(dgtVersion)
}

repositories {
    maven("https://maven.deftu.dev/internal-exposed")
}

dependencies {
    shade(implementation("dev.deftu:ezrique-core:${libs.versions.ezrique.core.get()}")!!)

    // SQL
    api("org.postgresql:postgresql:${libs.versions.postgres.get()}")
    api("org.jetbrains.exposed:exposed-core:${libs.versions.exposed.get()}")
    api("org.jetbrains.exposed:exposed-dao:${libs.versions.exposed.get()}")
    api("org.jetbrains.exposed:exposed-jdbc:${libs.versions.exposed.get()}")
}

tasks {
    jar {
        enabled = false
        manifest.attributes(
            "Main-Class" to "dev.deftu.ezrique.tags.EzriqueTags"
        )
    }

    withType<ShadowJar> {
        archiveFileName.set("${projectData.name}.jar")
        archiveClassifier.set("")
    }
}
