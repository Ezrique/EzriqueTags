import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version("2.0.0")
    val dgt = "2.5.0"
    id("dev.deftu.gradle.tools") version(dgt)
    id("dev.deftu.gradle.tools.bloom") version(dgt)
    id("dev.deftu.gradle.tools.shadow") version(dgt)
}

repositories {
    maven("https://maven.deftu.dev/internal-exposed")
}

dependencies {
    shade(implementation("dev.deftu:ezrique-core:${libs.versions.ezrique.core.get()}")!!)

    shade(implementation("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")!!)
    shade(implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")!!)
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
