package dev.deftu.ezrique.tags.utils

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

private val ENV_VARS = listOf(
    "DOCKER",
    "DOCKER_CONFIG",
    "DOCKER_CERT_PATH",
    "DOCKER_CONTEXT",
    "KUBERNETES_SERVICE_HOST",
    "KUBERNETES_SERVICE_PORT"
)

private val FILE_PATHS = listOf(
    "/.dockerenv",
    "/.dockerinit",
    "/proc/self/cgroup",
)

fun isInDocker(): Boolean {
    if (ENV_VARS.any { variable ->
        System.getenv(variable) != null
    }) return true

    if (FILE_PATHS.any { filePath ->
        val path = Path.of(filePath)

        // Check if the file exists
        return@any if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            // Check if "docker" is in the file
            if (Files.readAllLines(path).any { line ->
                line.contains("docker")
            }) return@any true

            true
        } else false
    }) return true

    return false
}
