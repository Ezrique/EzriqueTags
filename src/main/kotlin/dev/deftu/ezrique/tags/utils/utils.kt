package dev.deftu.ezrique.tags.utils

fun String.transformTagNameForCommandName(): String {
    return lowercase().replace(" ", "-")
}

fun String.getTagNameFromCommandName(): String {
    return replace("-", " ")
}
