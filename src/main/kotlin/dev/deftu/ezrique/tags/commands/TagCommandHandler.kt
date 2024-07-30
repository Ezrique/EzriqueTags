package dev.deftu.ezrique.tags.commands

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import dev.deftu.ezrique.*
import dev.deftu.ezrique.tags.TagsErrorCode
import dev.deftu.ezrique.tags.data.TagManager
import dev.deftu.ezrique.tags.sql.TagEntity
import dev.deftu.ezrique.tags.sql.TagTable
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.InteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.entity.Guild
import dev.kord.core.event.interaction.GuildAutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.boolean
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import kotlinx.coroutines.flow.firstOrNull

object TagCommandHandler {

    private const val COMMAND_NAME = "tag"
    private val TAG_NAME_AUTO_COMPLETE_COMMANDS = setOf("edit", "export", "delete", "copy", "move", "info", "trigger")

    fun setupGlobalCommands(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input(COMMAND_NAME, "Manage tags for your server.") {
            defaultMemberPermissions = Permissions(Permission.ManageGuild)
            dmPermission = false

            subCommand("list", "List all tags.")

            subCommand("create", "Create a tag.") {
                boolean("copyable", "Whether the tag should be copyable.") {
                    required = true
                }
            }

            subCommand("edit", "Edit a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                boolean("copyable", "Whether the tag should be copyable.") {
                    required = true
                }
            }

            subCommand("export", "Exports a tag as JSON.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("exportall", "Exports all tags as JSON.")

            subCommand("import", "Imports a tag from JSON.") {
                string("json", "The JSON to import.") {
                    required = true
                }

                boolean("overwrite", "Whether to overwrite the tag if it already exists.") {
                    required = false
                }
            }

            subCommand("importbulk", "Imports all tags from the provided JSON.") {
                string("json", "The JSON to import.") {
                    required = true
                }

                boolean("overwrite", "Whether to overwrite the tags if they already exist.") {
                    required = false
                }
            }

            subCommand("delete", "Delete a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("clear", "Clear all tags.")

            subCommand("copy", "Copy a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                string("guild", "The guild ID to copy the tag to.") {
                    required = true
                }
            }

            subCommand("copyall", "Copy all tags.") {
                string("guild", "The guild ID to copy the tags to.") {
                    required = true
                }
            }

            subCommand("move", "Move a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                string("guild", "The guild ID to move the tag to.") {
                    required = true
                }
            }

            subCommand("moveall", "Move all tags.") {
                string("guild", "The guild ID to move the tags to.") {
                    required = true
                }
            }

            subCommand("info", "Get information about a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("trigger", "Trigger a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }
        }
    }

    suspend fun handleCommand(event: GuildChatInputCommandInteractionCreateEvent, commandName: String, subCommandName: String?) {
        if (commandName != COMMAND_NAME) {
            return
        }

        val guild = event.interaction.getGuild()
        val member = event.interaction.user.asMember(guild.id)
        if (subCommandName != "trigger" && !member.checkPermissionDeferred(Permission.ManageGuild) {
            event.interaction.deferPublicResponse()
        }) {
            return
        }

        when (subCommandName) {
            "list" -> {
                val response = event.interaction.deferPublicResponse()

                try {
                    val tags = TagManager.listFor(guild.id)
                    response.respond {
                        stateEmbed(if (tags.isNotEmpty()) EmbedState.SUCCESS else EmbedState.ERROR) {
                            title = "Tags"
                            description = if (tags.isNotEmpty()) {
                                tags.joinToString("\n") { tag ->
                                    "`${tag.name}` - ${TagManager.getDescriptionFor(tag, tag.name)}"
                                }
                            } else {
                                "No tags found."
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_LIST, response)
                }
            }

            "create" -> {
                try {
                    val copyable = event.interaction.command.options["copyable"]?.value?.toString()?.toBoolean() ?: TagTable.COPYABLE_DEFAULT
                    event.interaction.modal("Tag Creator", "tag-creator_$copyable") {
                        actionRow {
                            textInput(TextInputStyle.Short, "name", "Name") {
                                required = true
                            }
                        }

                        actionRow {
                            textInput(TextInputStyle.Short, "description", "Description") {
                                required = false
                            }
                        }

                        actionRow {
                            textInput(TextInputStyle.Paragraph, "content", "Content") {
                                required = true
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_CREATE, event.interaction.deferEphemeralResponse())
                }
            }

            "edit" -> {
                try {
                    val name = event.interaction.command.options["name"]?.value?.toString() ?: return
                    val copyable = event.interaction.command.options["copyable"]?.value?.toString()?.toBoolean() ?: TagTable.COPYABLE_DEFAULT
                    val tag = TagManager.getFor(guild.id, name) ?: return
                    event.interaction.modal("Tag Editor", "tag-editor_${name}_${copyable}") {
                        actionRow {
                            textInput(TextInputStyle.Short, "description", "Description") {
                                required = false
                                value = tag.description
                            }
                        }

                        actionRow {
                            textInput(TextInputStyle.Paragraph, "content", "Content") {
                                required = false
                                value = tag.content
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_EDIT, event.interaction.deferEphemeralResponse())
                }
            }

            "export" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val name = event.interaction.command.options["name"]?.value?.toString()
                    if (name == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name cannot be empty."
                            }
                        }

                        return
                    }

                    val tag = TagManager.getFor(guild.id, name)
                    if (tag == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    val json = TagManager.convertToJson(tag)

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag Export"
                            description = "Here is the JSON for the tag `$name`."

                            field("JSON") {
                                buildString {
                                    appendLine("```json")
                                    appendLine(json.toString())
                                    appendLine("```")
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_EXPORT, response)
                }
            }

            "exportall" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val tags = TagManager.listFor(guild.id)
                    if (tags.isEmpty()) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "No tags found"
                                description = "There are no tags to export."
                            }
                        }

                        return
                    }

                    val array = JsonArray()
                    for (tag in tags) {
                        array.add(TagManager.convertToJson(tag))
                    }

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tags Export"
                            description = "Here is the JSON for all tags."

                            field("JSON") {
                                buildString {
                                    appendLine("```json")
                                    appendLine(array.toString())
                                    appendLine("```")
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_EXPORTALL, response)
                }
            }

            "import" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val jsonRaw = event.interaction.command.options["json"]?.value?.toString()
                    if (jsonRaw == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid JSON"
                                description = "The JSON cannot be empty."
                            }
                        }

                        return
                    }

                    val overwrite = event.interaction.command.options["overwrite"]?.value?.toString()?.toBoolean() ?: false

                    if (!checkTagLimit(guild, response)) {
                        return
                    }

                    val json = JsonParser.parseString(jsonRaw)?.asJsonObject
                    if (json == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid JSON"
                                description = "The JSON is invalid."
                            }
                        }

                        return
                    }

                    val tagName = TagManager.getTagNameFromJson(json)
                    if (!overwrite && TagManager.existsFor(guild.id, tagName)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag already exists"
                                description = "A tag with the name `$tagName` already exists."
                            }
                        }

                        return
                    }

                    if (overwrite) {
                        TagManager.deleteFor(guild.id, tagName)
                    }

                    val tag = TagManager.createFromJson(guild.id, json)
                    if (tag == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid JSON"
                                description = "The JSON is invalid."
                            }
                        }

                        return
                    }

                    event.kord.createGuildChatInputCommand(
                        guild.id,
                        tag.name,
                        TagManager.getDescriptionFor(tag, tag.name)
                    )

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag imported"
                            description = "The tag `${tag.name}` has been imported."
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_IMPORT, response)
                }
            }

            "importbulk" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val jsonRaw = event.interaction.command.options["json"]?.value?.toString()
                    if (jsonRaw == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid JSON"
                                description = "The JSON cannot be empty."
                            }
                        }

                        return
                    }

                    val overwrite = event.interaction.command.options["overwrite"]?.value?.toString()?.toBoolean() ?: false

                    if (!checkTagLimit(guild, response)) {
                        return
                    }

                    val json = JsonParser.parseString(jsonRaw)?.asJsonArray
                    if (json == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid JSON"
                                description = "The JSON is invalid."
                            }
                        }

                        return
                    }

                    val tags = mutableListOf<TagEntity>()
                    val existingTags = mutableSetOf<String>()
                    val overLimitTags = mutableListOf<String>()
                    val invalidTags = mutableSetOf<String>()
                    for (element in json) {
                        val tagJson = element.asJsonObject
                        val tagName = TagManager.getTagNameFromJson(tagJson)
                        if (!overwrite && TagManager.existsFor(guild.id, tagName)) {
                            existingTags.add(tagName)
                            continue
                        }

                        if (TagManager.isOverLimit(guild.id)) {
                            overLimitTags.add(tagName)
                            continue
                        }

                        if (overwrite) {
                            TagManager.deleteFor(guild.id, tagName)
                        }

                        val tag = TagManager.createFromJson(guild.id, tagJson)
                        if (tag == null) {
                            invalidTags.add(tagName)
                            continue
                        }

                        tags.add(tag)
                    }

                    for (tag in tags) {
                        event.kord.createGuildChatInputCommand(
                            guild.id,
                            tag.name,
                            TagManager.getDescriptionFor(tag, tag.name)
                        )
                    }

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tags imported"
                            description = "All tags have been imported."

                            if (existingTags.isNotEmpty()) {
                                field("Existing tags") {
                                    existingTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (overLimitTags.isNotEmpty()) {
                                field("Over limit tags") {
                                    overLimitTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (invalidTags.isNotEmpty()) {
                                field("Invalid tags") {
                                    invalidTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_IMPORTBULK, response)
                }
            }

            "delete" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val name = event.interaction.command.options["name"]?.value?.toString()
                    if (name == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name cannot be empty."
                            }
                        }

                        return
                    }

                    if (!TagManager.existsFor(guild.id, name)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    TagManager.deleteFor(guild.id, name)

                    event.kord.getGuildApplicationCommands(guild.id).firstOrNull { command ->
                        command.name == name
                    }?.delete()

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag deleted"
                            description = "The tag `$name` has been deleted."
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_DELETE, response)
                }
            }

            "clear" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    if (!member.checkPermissionDeferred(Permission.Administrator, response)) {
                        return
                    }

                    TagManager.clearFor(guild.id)

                    event.kord.getGuildApplicationCommands(guild.id).collect { command ->
                        command.delete()
                    }

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tags cleared"
                            description = "All tags have been cleared."
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_CLEAR, response)
                }
            }

            "copy" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val targetGuildId = event.interaction.command.options["guild"]?.value?.toString()?.toLongOrNull()
                    if (targetGuildId == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid guild ID"
                                description = "The guild ID must be a valid snowflake."
                            }
                        }

                        return
                    }

                    val name = event.interaction.command.options["name"]?.value?.toString()
                    if (name == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name cannot be empty."
                            }
                        }

                        return
                    }

                    if (!member.checkPermissionDeferred(Permission.Administrator, response)) {
                        return
                    }

                    if (!TagManager.existsFor(guild.id, name)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    val targetGuild = event.kord.getGuildOrNull(Snowflake(targetGuildId))
                    if (targetGuild == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Guild not found"
                                description = "A guild with the ID `$targetGuildId` cannot be found."
                            }
                        }

                        return
                    }

                    if (!checkTagLimit(targetGuild, response)) {
                        return
                    }

                    if (!event.interaction.user.asMember(targetGuild.id)
                            .checkPermissionDeferred(Permission.Administrator, response)
                    ) {
                        return
                    }

                    if (TagManager.existsFor(targetGuild.id, name)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag already exists"
                                description =
                                    "A tag with the name `$name` already exists in \"${targetGuild.name}\" ($targetGuildId)."
                            }
                        }

                        return
                    }

                    if (!TagManager.isCopyable(guild.id, name)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not copyable"
                                description = "The tag `$name` is not copyable."
                            }
                        }

                        return
                    }

                    val tag = TagManager.copyTo(guild.id, name, targetGuild.id)
                    if (tag == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    event.kord.createGuildChatInputCommand(
                        targetGuild.id,
                        name,
                        TagManager.getDescriptionFor(tag, name)
                    )

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag copied"
                            description = "The tag `$name` has been copied to \"${targetGuild.name}\" ($targetGuildId)"
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_COPY, response)
                }
            }

            "copyall" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val targetGuildId = event.interaction.command.options["guild"]?.value?.toString()?.toLongOrNull()
                    if (targetGuildId == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid guild ID"
                                description = "The guild ID must be a valid Snowflake."
                            }
                        }

                        return
                    }

                    if (!member.checkPermissionDeferred(Permission.Administrator, response)) {
                        return
                    }

                    val targetGuild = event.kord.getGuildOrNull(Snowflake(targetGuildId))
                    if (targetGuild == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Guild not found"
                                description = "A guild with the ID `$targetGuildId` cannot be found."
                            }
                        }

                        return
                    }

                    if (!checkTagLimit(targetGuild, response)) {
                        return
                    }

                    if (!event.interaction.user.asMember(targetGuild.id)
                            .checkPermissionDeferred(Permission.Administrator, response)
                    ) {
                        return
                    }

                    val tags = TagManager.listFor(guild.id)
                    val existingTags = mutableSetOf<String>()
                    val overLimitTags = mutableListOf<String>()
                    val uncopyableTags = mutableSetOf<String>()
                    val failedTags = mutableSetOf<String>()
                    for (tag in tags) {
                        if (TagManager.existsFor(targetGuild.id, tag.name)) {
                            existingTags.add(tag.name)
                            continue
                        }

                        if (TagManager.isOverLimit(targetGuild.id)) {
                            overLimitTags.add(tag.name)
                            continue
                        }

                        if (!TagManager.isCopyable(guild.id, tag.name)) {
                            uncopyableTags.add(tag.name)
                            continue
                        }

                        val newTag = TagManager.copyTo(guild.id, tag.name, targetGuild.id)
                        if (newTag == null) {
                            failedTags.add(tag.name)
                            continue
                        }

                        event.kord.createGuildChatInputCommand(
                            targetGuild.id,
                            tag.name,
                            TagManager.getDescriptionFor(newTag, tag.name)
                        )
                    }

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tags copied"
                            description = "All tags have been copied to \"${targetGuild.name}\" ($targetGuildId)"

                            if (existingTags.isNotEmpty()) {
                                field("Existing tags") {
                                    existingTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (overLimitTags.isNotEmpty()) {
                                field("Over limit tags") {
                                    overLimitTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (uncopyableTags.isNotEmpty()) {
                                field("Uncopyable tags") {
                                    uncopyableTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (failedTags.isNotEmpty()) {
                                field("Failed tags") {
                                    failedTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_COPYALL, response)
                }
            }

            "move" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val targetGuildId = event.interaction.command.options["guild"]?.value?.toString()?.toLongOrNull()
                    if (targetGuildId == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid guild ID"
                                description = "The guild ID must be a valid Snowflake."
                            }
                        }

                        return
                    }

                    val name = event.interaction.command.options["name"]?.value?.toString()
                    if (name == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name cannot be empty."
                            }
                        }

                        return
                    }

                    if (!member.checkPermissionDeferred(Permission.Administrator, response)) {
                        return
                    }

                    if (!TagManager.existsFor(guild.id, name)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    val targetGuild = event.kord.getGuildOrNull(Snowflake(targetGuildId))
                    if (targetGuild == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Guild not found"
                                description = "A guild with the ID `$targetGuildId` cannot be found."
                            }
                        }

                        return
                    }

                    if (!checkTagLimit(targetGuild, response)) {
                        return
                    }

                    if (!event.interaction.user.asMember(targetGuild.id)
                            .checkPermissionDeferred(Permission.Administrator, response)
                    ) {
                        return
                    }

                    if (TagManager.existsFor(targetGuild.id, name)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag already exists"
                                description =
                                    "A tag with the name `$name` already exists in \"${targetGuild.name}\" ($targetGuildId)."
                            }
                        }

                        return
                    }

                    if (!TagManager.isCopyable(guild.id, name)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not copyable"
                                description = "The tag `$name` is not copyable."
                            }
                        }

                        return
                    }

                    val tag = TagManager.moveTo(guild.id, name, targetGuild.id)
                    if (tag == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    event.kord.getGuildApplicationCommands(guild.id).firstOrNull { command ->
                        command.name == name
                    }?.delete()

                    event.kord.createGuildChatInputCommand(
                        targetGuild.id,
                        name,
                        TagManager.getDescriptionFor(tag, name)
                    )

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag moved"
                            description = "The tag `$name` has been moved to \"${targetGuild.name}\" ($targetGuildId)"
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_MOVE, response)
                }
            }

            "moveall" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val targetGuildId = event.interaction.command.options["guild"]?.value?.toString()?.toLongOrNull()
                    if (targetGuildId == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid guild ID"
                                description = "The guild ID must be a valid Snowflake."
                            }
                        }

                        return
                    }

                    if (!member.checkPermissionDeferred(Permission.Administrator, response)) {
                        return
                    }



                    val targetGuild = event.kord.getGuildOrNull(Snowflake(targetGuildId))
                    if (targetGuild == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Guild not found"
                                description = "A guild with the ID `$targetGuildId` cannot be found."
                            }
                        }

                        return
                    }

                    if (!checkTagLimit(targetGuild, response)) {
                        return
                    }

                    if (!event.interaction.user.asMember(targetGuild.id)
                            .checkPermissionDeferred(Permission.Administrator, response)
                    ) {
                        return
                    }

                    val tags = TagManager.listFor(guild.id)
                    val existingTags = mutableSetOf<String>()
                    val overLimitTags = mutableListOf<String>()
                    val uncopyableTags = mutableSetOf<String>()
                    val failedTags = mutableSetOf<String>()
                    for (tag in tags) {
                        if (TagManager.existsFor(targetGuild.id, tag.name)) {
                            existingTags.add(tag.name)
                            continue
                        }

                        if (TagManager.isOverLimit(targetGuild.id)) {
                            overLimitTags.add(tag.name)
                            continue
                        }

                        if (!TagManager.isCopyable(guild.id, tag.name)) {
                            uncopyableTags.add(tag.name)
                            continue
                        }

                        val newTag = TagManager.moveTo(guild.id, tag.name, targetGuild.id)
                        if (newTag == null) {
                            failedTags.add(tag.name)
                            continue
                        }

                        event.kord.getGuildApplicationCommands(guild.id).firstOrNull { command ->
                            command.name == tag.name
                        }?.delete()

                        event.kord.createGuildChatInputCommand(
                            targetGuild.id,
                            tag.name,
                            TagManager.getDescriptionFor(newTag, tag.name)
                        )
                    }

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tags moved"
                            description = "All tags have been moved to \"${targetGuild.name}\" ($targetGuildId)"

                            if (existingTags.isNotEmpty()) {
                                field("Existing tags") {
                                    existingTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (overLimitTags.isNotEmpty()) {
                                field("Over limit tags") {
                                    overLimitTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (uncopyableTags.isNotEmpty()) {
                                field("Uncopyable tags") {
                                    uncopyableTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }

                            if (failedTags.isNotEmpty()) {
                                field("Failed tags") {
                                    failedTags.joinToString("\n") { tagName -> "`$tagName`" }
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_MOVEALL, response)
                }
            }

            "info" -> {
                val response = event.interaction.deferPublicResponse()

                try {
                    val name = event.interaction.command.options["name"]?.value?.toString()
                    if (name == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name cannot be empty."
                            }
                        }

                        return
                    }

                    val tag = TagManager.getFor(guild.id, name)
                    if (tag == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag Information"

                            field {
                                this.name = "Name"
                                this.value = tag.name
                            }

                            field {
                                this.name = "Description"
                                this.value = tag.description ?: "No description."
                            }

                            field {
                                this.name = "Copyable"
                                this.value = if (tag.copyable) "Yes" else "No"
                            }

                            field {
                                this.name = "Content"
                                this.value = tag.content
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_INFO, response)
                }
            }

            "trigger" -> {
                val response = event.interaction.deferPublicResponse()

                try {
                    val name = event.interaction.command.options["name"]?.value?.toString()
                    if (name == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name cannot be empty."
                            }
                        }

                        return
                    }

                    val tag = TagManager.getFor(guild.id, name)
                    if (tag == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$name` does not exist."
                            }
                        }

                        return
                    }

                    response.respond {
                        content = tag.content
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_MANUAL_TRIGGER, response)
                }
            }
        }
    }

    suspend fun handleAutoComplete(event: GuildAutoCompleteInteractionCreateEvent, commandName: String, subCommandName: String?) {
        try {
            if (commandName != COMMAND_NAME || subCommandName !in TAG_NAME_AUTO_COMPLETE_COMMANDS) {
                return
            }

            val guild = event.interaction.getGuild()
            val name = event.interaction.command.options["name"]?.value?.toString() ?: return
            val tags = TagManager.listFor(guild.id)
            val filteredTags = tags.filter { tag -> tag.name.startsWith(name) }
            event.interaction.suggestString {
                for (tag in filteredTags) {
                    choice(tag.name, tag.name)
                }
            }
        } catch (t: Throwable) {
            handleError(t, TagsErrorCode.TAG_AUTOCOMPLETE)
        }
    }

    suspend fun handleModal(event: GuildModalSubmitInteractionCreateEvent) {
        // The modal type is everything before the first _
        val modalType = event.interaction.modalId.substringBefore('_')

        when (modalType) {
            "tag-creator" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val rawTagName = event.interaction.textInputs["name"]?.value
                    if (rawTagName == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name must be alphanumeric and have a length between ${TagManager.TAG_NAME_MIN_LENGTH} and ${TagManager.TAG_NAME_MAX_LENGTH} characters."
                            }
                        }

                        return
                    }

                    val tagName = TagManager.ensureName(rawTagName)
                    val tagDescription = event.interaction.textInputs["description"]?.value
                    val tagContent = event.interaction.textInputs["content"]?.value
                    if (tagContent == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag content"
                                description = "The tag content cannot be empty."
                            }
                        }

                        return
                    }

                    val tagCopyable = event.interaction.modalId.substringAfter('_').toBooleanStrictOrNull()

                    val guild = event.interaction.getGuild()
                    if (!TagManager.validateName(tagName)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag name"
                                description = "The tag name must be alphanumeric and have a length between ${TagManager.TAG_NAME_MIN_LENGTH} and ${TagManager.TAG_NAME_MAX_LENGTH} characters."

                                field("Still want to create your tag?") {
                                    buildString {
                                        appendLine("```")
                                        appendLine(tagContent)
                                        appendLine("```")
                                    }
                                }
                            }
                        }

                        return
                    }

                    if (TagManager.existsFor(guild.id, tagName)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag already exists"
                                description = "A tag with the name `$tagName` already exists."

                                field("Still want to create your tag?") {
                                    buildString {
                                        appendLine("```")
                                        appendLine(tagContent)
                                        appendLine("```")
                                    }
                                }
                            }
                        }

                        return
                    }

                    val tag = TagManager.createFor(guild.id, tagName, tagDescription, tagCopyable, tagContent)
                    event.kord.createGuildChatInputCommand(
                        guild.id,
                        tagName,
                        TagManager.getDescriptionFor(tag, tagName)
                    )

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag created"
                            description = "The tag `$tagName` has been created."
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_CREATE_SUBMIT, response)
                }
            }

            "tag-editor" -> {
                val response = event.interaction.deferEphemeralResponse()

                try {
                    val (tagName, tagCopyableString) = event.interaction.modalId.substringAfter('_').split('_')
                    val tagDescription = event.interaction.textInputs["description"]?.value
                    val tagContent = event.interaction.textInputs["content"]?.value
                    if (tagContent == null) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Invalid tag content"
                                description = "The tag content cannot be empty."
                            }
                        }

                        return
                    }

                    val tagCopyable = tagCopyableString.toBooleanStrictOrNull()

                    val guild = event.interaction.getGuild()
                    if (!TagManager.existsFor(guild.id, tagName)) {
                        response.respond {
                            stateEmbed(EmbedState.ERROR) {
                                title = "Tag not found"
                                description = "The tag `$tagName` does not exist."

                                field("Still want to edit your tag?") {
                                    buildString {
                                        appendLine("```")
                                        appendLine(tagContent)
                                        appendLine("```")
                                    }
                                }
                            }
                        }

                        return
                    }

                    TagManager.editFor(guild.id, tagName, tagDescription, tagCopyable, tagContent)

                    response.respond {
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tag edited"
                            description = "The tag `$tagName` has been edited."
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_EDIT_SUBMIT, response)
                }
            }
        }
    }

    private suspend fun checkTagLimit(guild: Guild, response: DeferredMessageInteractionResponseBehavior): Boolean {
        if (TagManager.isOverLimit(guild.id)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    title = "Tag limit reached"
                    description = "The tag limit of ${TagManager.MAX_TAGS_PER_GUILD} has been reached in ${guild.name} (${guild.id})."
                }
            }

            return false
        }

        return true
    }

}
