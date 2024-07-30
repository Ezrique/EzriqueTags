package dev.deftu.ezrique.tags.commands

import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildAutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder

object CommandDelegator {

    fun setupGlobalCommands(builder: GlobalMultiApplicationCommandBuilder) {
        TagCommandHandler.setupGlobalCommands(builder)
    }

    suspend fun setupGuildCommands(kord: Kord) {
        TagTriggerCommandHandler.setupGuildCommands(kord)
    }

    suspend fun handleCommand(event: GuildChatInputCommandInteractionCreateEvent, commandName: String, subCommandName: String?) {
        TagCommandHandler.handleCommand(event, commandName, subCommandName)
        TagTriggerCommandHandler.handleCommand(event, commandName, subCommandName)
    }

    suspend fun handleAutoComplete(event: GuildAutoCompleteInteractionCreateEvent, commandName: String, subCommandName: String?) {
        TagCommandHandler.handleAutoComplete(event, commandName, subCommandName)
        TagTriggerCommandHandler.handleAutoComplete(event, commandName, subCommandName)
    }

    suspend fun handleModal(event: GuildModalSubmitInteractionCreateEvent) {
        TagCommandHandler.handleModal(event)
        TagTriggerCommandHandler.handleModal(event)
    }

}
