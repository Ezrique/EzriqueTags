package dev.deftu.ezrique.tags

import dev.deftu.ezrique.*
import dev.deftu.ezrique.tags.sql.TagEntity
import dev.deftu.ezrique.tags.utils.transformTagNameForCommandName
import dev.deftu.ezrique.tags.utils.getTagNameFromCommandName
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.entity.Guild
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand

object CommandHandler {

    fun setupGlobalCommands(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("tag", "Manage tags for your server.") {
            defaultMemberPermissions = null
            dmPermission = false

            subCommand("create", "Create a tag.")

            subCommand("edit", "Edit a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("delete", "Delete a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("info", "Get information about a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("list", "List all tags.")

            subCommand("trigger", "Trigger a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("transfer", "Transfers a tag to another guild (requires admin in both guilds)") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                string("guild", "The guild ID to transfer the tag to.") {
                    required = true
                }
            }
        }
    }

    suspend fun setupTagCommands(kord: Kord) {
        val tags = TagEntity.listAll()

        for ((guildId, guildTags) in tags.groupBy { entity ->
            entity.guildId
        }) {
            for (guildTag in guildTags) {
                kord.createGuildChatInputCommand(Snowflake(guildId), guildTag.name.transformTagNameForCommandName(), "Triggers the ${guildTag.name} tag.")
            }
        }
    }

    suspend fun handleAll(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?,
        commandName: String,
        subCommandName: String?
    ) {
        when (commandName) {
            "tag" -> handleBaseTag(event, guild, subCommandName)
            else -> handleTagTrigger(event, guild, commandName)
        }
    }

    suspend fun handleAutoComplete(
        event: AutoCompleteInteractionCreateEvent,
        guild: Guild?,
        commandName: String,
        subCommandName: String?
    ) {
        when (commandName) {
            "tag" -> handleBaseAutoComplete(event, guild, subCommandName)
        }
    }

    private suspend fun handleBaseTag(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?,
        subCommandName: String?
    ) {
        if (guild == null) return

        val member = event.interaction.user.asMember(guild.id)
        if (subCommandName != "trigger" && !member.checkPermissionDeferred(Permission.ManageGuild) { event.interaction.deferPublicResponse() }) return

        when (subCommandName) {
            "create" -> handleBaseCreate(event)
            "edit" -> handleBaseEdit(event)
            "delete" -> handleBaseDelete(event, guild)
            "info" -> handleBaseInfo(event, guild)
            "list" -> handleBaseList(event, guild)
            "trigger" -> handleBaseTrigger(event, guild)
            "transfer" -> handleBaseTransfer(event, guild)
        }
    }

    private suspend fun handleBaseCreate(event: ChatInputCommandInteractionCreateEvent) {
        event.interaction.modal("Tag Creation", "tag-creator") {
            actionRow {
                textInput(TextInputStyle.Short, "name", "Name") {
                    required = true
                }
            }

            actionRow {
                textInput(TextInputStyle.Paragraph, "content", "Content") {
                    required = true
                }
            }
        }
    }

    private suspend fun handleBaseEdit(event: ChatInputCommandInteractionCreateEvent) {
        val name = event.interaction.command.options["name"]?.value?.toString() ?: return
        event.interaction.modal("Tag Editing", "tag-editor_$name") {
            actionRow {
                textInput(TextInputStyle.Paragraph, "content", "Content") {
                    required = true
                }
            }
        }
    }

    private suspend fun handleBaseDelete(event: ChatInputCommandInteractionCreateEvent, guild: Guild) {
        val name = event.interaction.command.options["name"]?.value?.toString()?.getTagNameFromCommandName() ?: return

        val response = event.interaction.deferEphemeralResponse()

        if (!TagEntity.exists(guild.id.rawValue, name)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "A tag with the name `$name` doesn't exist."
                }
            }

            return
        }

        TagEntity.delete(guild.id.rawValue, name)

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "The tag `$name` has been deleted."
            }
        }
    }

    private suspend fun handleBaseInfo(event: ChatInputCommandInteractionCreateEvent, guild: Guild) {
        val name = event.interaction.command.options["name"]?.value?.toString()?.getTagNameFromCommandName() ?: return
        val tag = TagEntity.get(guild.id.rawValue, name) ?: return

        val response = event.interaction.deferEphemeralResponse()

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                title = "Tag Information"
                field("Name", true) { tag.name }
                field("Content", true) { tag.content }
            }
        }
    }

    private suspend fun handleBaseList(event: ChatInputCommandInteractionCreateEvent, guild: Guild) {
        val response = event.interaction.deferEphemeralResponse()
        val tags = TagEntity.list(guild.id.rawValue)

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                title = "Tags"

                // Constructs a comma-separated string of each tag's name enclosed in backticks
                description = tags.map(TagEntity::name).joinToString(", ") { name -> "`$name`" }
            }
        }
    }

    private suspend fun handleBaseTrigger(event: ChatInputCommandInteractionCreateEvent, guild: Guild) {
        val name = event.interaction.command.options["name"]?.value?.toString()?.getTagNameFromCommandName() ?: return
        val tag = TagEntity.get(guild.id.rawValue, name) ?: return

        val response = event.interaction.deferPublicResponse()

        response.respond {
            content = tag.content
        }
    }

    private suspend fun handleBaseTransfer(event: ChatInputCommandInteractionCreateEvent, guild: Guild) {
        val name = event.interaction.command.options["name"]?.value?.toString()?.getTagNameFromCommandName() ?: return

        val response = event.interaction.deferEphemeralResponse()

        if (!TagEntity.exists(guild.id.rawValue, name)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "A tag with the name `$name` doesn't exist."
                }
            }

            return
        }

        val guildId = event.interaction.command.options["guild"]?.value?.toString()?.toLongOrNull() ?: return
        val targetGuild = guild.kord.getGuildOrNull(Snowflake(guildId)) ?: return
        if (targetGuild.id.rawValue == guild.id.rawValue) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "You can't transfer a tag to the same guild."
                }
            }

            return
        }

        if (TagEntity.exists(targetGuild.id.rawValue, name)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "A tag with the name `$name` already exists in the target guild."
                }
            }

            return
        }

        val targetGuildMember = event.interaction.user.asMember(targetGuild.id)
        if (!targetGuildMember.checkPermissionDeferred(Permission.ManageGuild, response)) return

        TagEntity.transfer(guild.id.rawValue, name, targetGuild.id.rawValue)

        // Create the tag's slash command in the target guild
        event.kord.createGuildChatInputCommand(targetGuild.id, name.transformTagNameForCommandName(), "Triggers the $name tag.")

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "The tag `$name` has been transferred to ${targetGuild.name}."
            }
        }
    }

    private suspend fun handleBaseAutoComplete(event: AutoCompleteInteractionCreateEvent, guild: Guild?, subCommandName: String?) {
        if (guild == null) return

        when (subCommandName) {
            "edit", "delete", "info", "trigger" -> {
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return
                val tags = TagEntity.list(guild.id.rawValue)
                val matchingTags = tags.filter { tag -> tag.name.startsWith(name) }

                event.interaction.suggestString {
                    for (matchingTag in matchingTags) {
                        choice(matchingTag.name.transformTagNameForCommandName(), matchingTag.name)
                    }
                }
            }
        }
    }

    private suspend fun handleTagTrigger(event: ChatInputCommandInteractionCreateEvent, guild: Guild?, commandName: String) {
        if (guild == null) return

        val tagName = commandName.getTagNameFromCommandName()

        val response = event.interaction.deferPublicResponse()
        if (!TagEntity.exists(guild.id.rawValue, tagName)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "A tag with the name `$commandName` doesn't exist."
                }
            }

            return
        }

        val tag = TagEntity.get(guild.id.rawValue, tagName) ?: return

        response.respond {
            content = tag.content
        }
    }

}
