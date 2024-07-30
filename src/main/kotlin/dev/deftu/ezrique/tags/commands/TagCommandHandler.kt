package dev.deftu.ezrique.tags.commands

import dev.deftu.ezrique.*
import dev.deftu.ezrique.tags.data.TagManager
import dev.deftu.ezrique.tags.sql.TagTable
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.event.interaction.GuildAutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.boolean
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand

object TagCommandHandler {

    private const val COMMAND_NAME = "tag"
    private val TAG_NAME_AUTO_COMPLETE_COMMANDS = setOf("edit", "delete", "copy", "move", "info", "trigger")

    fun setupGlobalCommands(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input(COMMAND_NAME, "Manage tags for your server.") {
            defaultMemberPermissions = Permissions(Permission.ManageGuild)
            dmPermission = false

            subCommand("list", "List all tags.")

            subCommand("create", "Create a tag.") {
                boolean("syncable", "Whether the tag should be syncable.") {
                    required = false
                }
            }

            subCommand("edit", "Edit a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                boolean("syncable", "Whether the tag should be syncable.") {
                    required = false
                }
            }

            subCommand("delete", "Delete a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("copy", "Copy a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                string("guild", "The guild ID to copy the tag to.") {
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
                val tags = TagManager.listFor(guild.id)
                val response = event.interaction.deferPublicResponse()
                response.respond {
                    stateEmbed(EmbedState.SUCCESS) {
                        title = "Tags"
                        description = tags.joinToString("\n") { tag ->
                            "`${tag.name}` - ${TagManager.getDescriptionFor(tag, tag.name)}"
                        }
                    }
                }

            }

            "create" -> {
                val syncable = event.interaction.command.options["syncable"]?.value?.toString()?.toBoolean() ?: false

                event.interaction.modal("Tag Creator", "tag-creator_$syncable") {
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

            }

            "edit" -> {
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return
                val syncable = event.interaction.command.options["syncable"]?.value?.toString()?.toBoolean() ?: false
                val tag = TagManager.getFor(guild.id, name) ?: return
                event.interaction.modal("Tag Editor", "tag-editor_${name}_${syncable}") {
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

            }

            "delete" -> {
                val response = event.interaction.deferEphemeralResponse()
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return
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
                response.respond {
                    stateEmbed(EmbedState.SUCCESS) {
                        title = "Tag deleted"
                        description = "The tag `$name` has been deleted."
                    }
                }

            }

            "copy" -> {
                val targetGuildId = event.interaction.command.options["guild"]?.value?.toString()?.toLongOrNull() ?: return
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return

                val response = event.interaction.deferEphemeralResponse()
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

                if (!event.interaction.user.asMember(targetGuild.id).checkPermissionDeferred(Permission.Administrator, response)) {
                    return
                }

                if (TagManager.existsFor(targetGuild.id, name)) {
                    response.respond {
                        stateEmbed(EmbedState.ERROR) {
                            title = "Tag already exists"
                            description = "A tag with the name `$name` already exists in \"${targetGuild.name}\" ($targetGuildId)."
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
            }

            "move" -> {
                val targetGuildId = event.interaction.command.options["guild"]?.value?.toString()?.toLongOrNull() ?: return
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return

                val response = event.interaction.deferEphemeralResponse()
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

                if (!event.interaction.user.asMember(targetGuild.id).checkPermissionDeferred(Permission.Administrator, response)) {
                    return
                }

                if (TagManager.existsFor(targetGuild.id, name)) {
                    response.respond {
                        stateEmbed(EmbedState.ERROR) {
                            title = "Tag already exists"
                            description = "A tag with the name `$name` already exists in \"${targetGuild.name}\" ($targetGuildId)."
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
            }

            "info" -> {
                val response = event.interaction.deferPublicResponse()
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return
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
                            this.name = "Syncable"
                            this.value = if (tag.copyable) "Yes" else "No"
                        }

                        field {
                            this.name = "Content"
                            this.value = tag.content
                        }
                    }
                }

            }

            "trigger" -> {
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return
                val response = event.interaction.deferPublicResponse()
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
            }
        }
    }

    suspend fun handleAutoComplete(event: GuildAutoCompleteInteractionCreateEvent, commandName: String, subCommandName: String?) {
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
    }

    suspend fun handleModal(event: GuildModalSubmitInteractionCreateEvent) {
        // The modal type is everything before the first _
        val modalType = event.interaction.modalId.substringBefore('_')

        when (modalType) {
            "tag-creator" -> {
                val tagName = TagManager.ensureName(event.interaction.textInputs["name"]?.value ?: return)
                val tagDescription = event.interaction.textInputs["description"]?.value
                val tagContent = event.interaction.textInputs["content"]?.value ?: return

                val tagSyncable = event.interaction.modalId.substringAfter('_').toBooleanStrictOrNull()

                val guild = event.interaction.getGuild()
                val response = event.interaction.deferEphemeralResponse()
                if (!TagManager.validateName(tagName)) {
                    response.respond {
                        stateEmbed(EmbedState.ERROR) {
                            title = "Invalid tag name"
                            description = "The tag name must be alphanumeric and have a length between 3 and 32 characters."
                        }
                    }

                    return
                }

                if (TagManager.existsFor(guild.id, tagName)) {
                    response.respond {
                        stateEmbed(EmbedState.ERROR) {
                            title = "Tag already exists"
                            description = "A tag with the name `$tagName` already exists."
                        }
                    }

                    return
                }

                val tag = TagManager.createFor(guild.id, tagName, tagDescription, tagSyncable, tagContent)
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
            }

            "tag-editor" -> {
                val (tagName, tagSyncableString) = event.interaction.modalId.substringAfter('_').split('_')
                val tagDescription = event.interaction.textInputs["description"]?.value
                val tagContent = event.interaction.textInputs["content"]?.value ?: return

                val tagSyncable = tagSyncableString.toBooleanStrictOrNull()

                val guild = event.interaction.getGuild()
                val response = event.interaction.deferEphemeralResponse()
                if (!TagManager.existsFor(guild.id, tagName)) {
                    response.respond {
                        stateEmbed(EmbedState.ERROR) {
                            title = "Tag not found"
                            description = "The tag `$tagName` does not exist."
                        }
                    }

                    return
                }

                TagManager.editFor(guild.id, tagName, tagDescription, tagSyncable, tagContent)

                response.respond {
                    stateEmbed(EmbedState.SUCCESS) {
                        title = "Tag edited"
                        description = "The tag `$tagName` has been edited."
                    }
                }
            }
        }
    }

}
