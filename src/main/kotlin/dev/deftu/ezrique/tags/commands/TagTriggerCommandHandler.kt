package dev.deftu.ezrique.tags.commands

import dev.deftu.ezrique.EmbedState
import dev.deftu.ezrique.stateEmbed
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
            for (guildTag in guildTags) {
                kord.createGuildChatInputCommand(Snowflake(guildId), guildTag.name, TagManager.getDescriptionFor(guildTag, guildTag.name))
            }
        }
    }

    suspend fun handleCommand(event: GuildChatInputCommandInteractionCreateEvent, commandName: String, subCommandName: String?) {
        val guild = event.interaction.getGuild()

        val response = event.interaction.deferPublicResponse()
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
    }

    fun handleAutoComplete(event: GuildAutoCompleteInteractionCreateEvent, commandName: String, subCommandName: String?) {
        // no-op
    }

    fun handleModal(event: ModalSubmitInteractionCreateEvent) {
        // no-op
    }

}
