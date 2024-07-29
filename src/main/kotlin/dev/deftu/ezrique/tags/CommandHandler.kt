package dev.deftu.ezrique.tags

import dev.deftu.ezrique.EmbedState
import dev.deftu.ezrique.checkPermission
import dev.deftu.ezrique.rawValue
import dev.deftu.ezrique.stateEmbed
import dev.deftu.ezrique.tags.sql.TagEntity
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
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

            subCommand("create", "Create a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                }

                string("content", "The content of the tag.") {
                    required = true
                }
            }

            subCommand("delete", "Delete a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }
            }

            subCommand("edit", "Edit a tag.") {
                string("name", "The name of the tag.") {
                    required = true
                    autocomplete = true
                }

                string("content", "The new content of the tag.") {
                    required = true
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
                kord.createGuildChatInputCommand(Snowflake(guildId), guildTag.name.transformForCommandName(), "Triggers the ${guildTag.name} tag.")
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
        val response = event.interaction.deferPublicResponse()

        // Check if the member has the required permissions. Only check on all subcommands except for "trigger"
        if (subCommandName != "trigger" && !member.checkPermission(Permission.ManageGuild, response)) return

        when (subCommandName) {
            "create" -> handleBaseCreate(event, guild, response)
            "delete" -> handleBaseDelete(event, guild, response)
            "edit" -> handleBaseEdit(event, guild, response)
            "info" -> handleBaseInfo(event, guild, response)
            "list" -> handleBaseList(guild, response)
            "trigger" -> handleBaseTrigger(event, guild, response)
            "transfer" -> handleBaseTransfer(event, guild, response)
        }
    }

    private suspend fun handleBaseCreate(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val name = event.interaction.command.options["name"]?.value?.toString() ?: return
        if (name == "tag") {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "You can't create a tag with the name `tag`."
                }
            }

            return
        }

        if (TagEntity.exists(guild.id.rawValue, name)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "A tag with the name `$name` already exists."
                }
            }

            return
        }

        val content = event.interaction.command.options["content"]?.value?.toString() ?: return

        TagEntity.create(guild.id.rawValue, name, content)

        // Create the tag's slash command
        event.kord.createGuildChatInputCommand(guild.id, name.transformForCommandName(), "Triggers the $name tag.")

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "The tag `$name` has been created."
            }
        }
    }

    private suspend fun handleBaseDelete(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val name = event.interaction.command.options["name"]?.value?.toString() ?: return
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

    private suspend fun handleBaseEdit(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val name = event.interaction.command.options["name"]?.value?.toString() ?: return
        if (!TagEntity.exists(guild.id.rawValue, name)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "A tag with the name `$name` doesn't exist."
                }
            }

            return
        }

        val content = event.interaction.command.options["content"]?.value?.toString() ?: return

        TagEntity.edit(guild.id.rawValue, name, content)

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "The tag `$name` has been edited."
            }
        }
    }

    private suspend fun handleBaseInfo(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val name = event.interaction.command.options["name"]?.value?.toString() ?: return
        val tag = TagEntity.get(guild.id.rawValue, name) ?: return

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                title = "Tag Information"
                field("Name", true) { tag.name }
                field("Content", true) { tag.content }
            }
        }
    }

    private suspend fun handleBaseList(
        guild: Guild,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val tags = TagEntity.list(guild.id.rawValue)

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                title = "Tags"

                // Constructs a comma-separated string of each tag's name enclosed in backticks
                description = tags.map(TagEntity::name).joinToString(", ") { name -> "`$name`" }
            }
        }
    }

    private suspend fun handleBaseTrigger(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val name = event.interaction.command.options["name"]?.value?.toString() ?: return
        val tag = TagEntity.get(guild.id.rawValue, name) ?: return

        response.respond {
            content = tag.content
        }
    }

    private suspend fun handleBaseTransfer(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val name = event.interaction.command.options["name"]?.value?.toString() ?: return
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
        if (!targetGuildMember.checkPermission(Permission.ManageGuild, response)) return

        TagEntity.transfer(guild.id.rawValue, name, targetGuild.id.rawValue)

        // Create the tag's slash command in the target guild
        event.kord.createGuildChatInputCommand(targetGuild.id, name.transformForCommandName(), "Triggers the $name tag.")

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "The tag `$name` has been transferred to ${targetGuild.name}."
            }
        }
    }

    private suspend fun handleBaseAutoComplete(
        event: AutoCompleteInteractionCreateEvent,
        guild: Guild?,
        subCommandName: String?
    ) {
        if (guild == null) return

        when (subCommandName) {
            "delete", "edit", "info", "trigger" -> {
                val name = event.interaction.command.options["name"]?.value?.toString() ?: return
                val tags = TagEntity.list(guild.id.rawValue)
                val matchingTags = tags.filter { tag -> tag.name.startsWith(name) }

                event.interaction.suggestString {
                    for (matchingTag in matchingTags) {
                        choice(matchingTag.name.transformForCommandName(), matchingTag.name)
                    }
                }
            }
        }
    }

    private suspend fun handleTagTrigger(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?,
        commandName: String
    ) {
        if (guild == null) return

        val response = event.interaction.deferPublicResponse()
        if (!TagEntity.exists(guild.id.rawValue, commandName)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "A tag with the name `$commandName` doesn't exist."
                }
            }

            return
        }

        val tag = TagEntity.get(guild.id.rawValue, commandName) ?: return

        response.respond {
            content = tag.content
        }
    }

    private fun String.transformForCommandName(): String {
        return lowercase().replace(" ", "_")
    }

}
