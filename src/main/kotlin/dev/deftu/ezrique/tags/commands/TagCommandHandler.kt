package dev.deftu.ezrique.tags.commands

import dev.deftu.ezrique.*
import dev.deftu.ezrique.tags.TagsErrorCode
import dev.deftu.ezrique.tags.data.TagManager
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggestString
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
    private val TAG_NAME_AUTO_COMPLETE_COMMANDS = setOf("edit", "delete", "copy", "move", "info", "trigger")

    fun setupGlobalCommands(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input(COMMAND_NAME, "Manage tags for your server.") {
            defaultMemberPermissions = Permissions(Permission.ManageGuild)
            dmPermission = false

            subCommand("list", "List all tags.")

            subCommand("create", "Create a tag.") {
                boolean("copyable", "Whether the tag should be copyable.") {
                    required = false
                }
            }

            subCommand("edit", "Edit a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                boolean("copyable", "Whether the tag should be copyable.") {
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
                        stateEmbed(EmbedState.SUCCESS) {
                            title = "Tags"
                            description = tags.joinToString("\n") { tag ->
                                "`${tag.name}` - ${TagManager.getDescriptionFor(tag, tag.name)}"
                            }
                        }
                    }
                } catch (t: Throwable) {
                    handleError(t, TagsErrorCode.TAG_LIST, response)
                }
            }

            "create" -> {
                try {
                    val copyable = event.interaction.command.options["copyable"]?.value?.toString()?.toBoolean() ?: false
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
                    val copyable = event.interaction.command.options["copyable"]?.value?.toString()?.toBoolean() ?: false
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

                    if (!event.interaction.user.asMember(targetGuild.id)
                            .checkPermissionDeferred(Permission.Administrator, response)
                    ) {
                        return
                    }

                    val tags = TagManager.listFor(guild.id)
                    val uncopyableTags = mutableSetOf<String>()
                    val failedTags = mutableSetOf<String>()
                    for (tag in tags) {
                        if (TagManager.existsFor(targetGuild.id, tag.name)) {
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
                            description = buildString {
                                appendLine("All tags have been copied to \"${targetGuild.name}\" ($targetGuildId)")

                                if (uncopyableTags.isNotEmpty()) {
                                    appendLine()
                                    appendLine("Uncopyable tags:")
                                    appendLine(uncopyableTags.joinToString("\n") { tagName -> "`$tagName`" })
                                }

                                if (failedTags.isNotEmpty()) {
                                    appendLine()
                                    appendLine("Failed tags:")
                                    appendLine(failedTags.joinToString("\n") { tagName -> "`$tagName`" })
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

                    if (!event.interaction.user.asMember(targetGuild.id)
                            .checkPermissionDeferred(Permission.Administrator, response)
                    ) {
                        return
                    }

                    val tags = TagManager.listFor(guild.id)
                    val uncopyableTags = mutableSetOf<String>()
                    val failedTags = mutableSetOf<String>()
                    for (tag in tags) {
                        if (TagManager.existsFor(targetGuild.id, tag.name)) {
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
                            description = buildString {
                                appendLine("All tags have been moved to \"${targetGuild.name}\" ($targetGuildId)")

                                if (uncopyableTags.isNotEmpty()) {
                                    appendLine()
                                    appendLine("Uncopyable tags:")
                                    appendLine(uncopyableTags.joinToString("\n") { tagName -> "`$tagName`" })
                                }

                                if (failedTags.isNotEmpty()) {
                                    appendLine()
                                    appendLine("Failed tags:")
                                    appendLine(failedTags.joinToString("\n") { tagName -> "`$tagName`" })
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
                                description = "The tag name must be alphanumeric and have a length between 3 and 32 characters."
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
                                description = "The tag name must be alphanumeric and have a length between 3 and 32 characters."

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

}
