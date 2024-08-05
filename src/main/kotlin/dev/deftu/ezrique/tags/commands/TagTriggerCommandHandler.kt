package dev.deftu.ezrique.tags.commands

import dev.deftu.ezrique.EmbedState
import dev.deftu.ezrique.handleError
import dev.deftu.ezrique.stateEmbed
import dev.deftu.ezrique.tags.TagsErrorCode
import dev.deftu.ezrique.tags.data.TagManager
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildAutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent

object TagTriggerCommandHandler {

    suspend fun setupGuildCommands(kord: Kord) {
        val tags = TagManager.listAll()

        for ((guildId, guildTags) in tags.groupBy { entity ->
            entity.guildId
        }) {
            try {
                val snowflake = Snowflake(guildId)

                /**
                 * Remove old tags that are no longer in the database.
                 */
                kord.getGuildApplicationCommands(snowflake).collect { command ->
                    if (command.name in guildTags.map { tag -> tag.name }) {
                        return@collect
                    }

                    command.delete()
                }

                /**
                 * Ensure all tags are available as slash commands in their associated guild.
                 */
                for (guildTag in guildTags) {
                    kord.createGuildChatInputCommand(snowflake, guildTag.name, TagManager.getDescriptionFor(guildTag, guildTag.name))
                }
            } catch (t: Throwable) {
                continue
            }
        }
    }

    suspend fun handleCommand(event: GuildChatInputCommandInteractionCreateEvent, commandName: String, subCommandName: String?) {
        val response = event.interaction.deferPublicResponse()

        try {
            val guild = event.interaction.getGuild()

            if (!TagManager.existsFor(guild.id, commandName)) {
                response.respond {
                    stateEmbed(EmbedState.ERROR) {
                        description = "A tag with the name `$commandName` doesn't exist."
                    }
                }

                return
            }

            val tag = TagManager.getFor(guild.id, commandName) ?: return
            response.respond {
                content = tag.content
            }
        } catch (t: Throwable) {
            handleError(t, TagsErrorCode.TAG_TRIGGER, response)
        }
    }

    fun handleAutoComplete(event: GuildAutoCompleteInteractionCreateEvent, commandName: String, subCommandName: String?) {
        // no-op
    }

    fun handleModal(event: ModalSubmitInteractionCreateEvent) {
        // no-op
    }

}
