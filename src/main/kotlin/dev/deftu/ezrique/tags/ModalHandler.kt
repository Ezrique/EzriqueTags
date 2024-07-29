package dev.deftu.ezrique.tags

import dev.deftu.ezrique.EmbedState
import dev.deftu.ezrique.rawValue
import dev.deftu.ezrique.stateEmbed
import dev.deftu.ezrique.tags.sql.TagEntity
import dev.deftu.ezrique.tags.utils.getTagNameFromCommandName
import dev.deftu.ezrique.tags.utils.transformTagNameForCommandName
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent

object ModalHandler {

    private val tagEditorRegex = Regex("^tag-editor_(?<name>.+)$")

    suspend fun handle(event: ModalSubmitInteractionCreateEvent, guild: Guild?) {
        when (event.interaction.modalId) {
            "tag-creator" -> handleTagCreator(event, guild ?: return)
            else -> {
                tagEditorRegex.matchEntire(event.interaction.modalId)?.let { match ->
                    val name = match.groups["name"]?.value?.getTagNameFromCommandName() ?: return
                    handleTagEditor(event, guild ?: return, name)
                }
            }
        }
    }

    private suspend fun handleTagCreator(event: ModalSubmitInteractionCreateEvent, guild: Guild) {
        val name = event.interaction.textInputs["name"]?.value ?: return
        val content = event.interaction.textInputs["content"]?.value ?: return

        val response = event.interaction.deferEphemeralResponse()

        // Check if the tag already exists
        if (TagEntity.exists(guild.id.rawValue, name)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    title = "Tag already exists"
                    description = "A tag with the name `$name` already exists."

                    field("Content", false) {
                        buildString {
                            appendLine("Still want to create a tag?")
                            appendLine("Here's the content you provided:")
                            appendLine("```")
                            append(content)
                            append("```")
                        }
                    }
                }
            }

            return
        }

        // Create the tag
        TagEntity.create(guild.id.rawValue, name, content)

        // Register a slash command for the tag
        event.kord.createGuildChatInputCommand(guild.id, name.transformTagNameForCommandName(), "Trigger the '$name' tag")

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                title = "Tag created"
                description = "The tag `$name` has been created."
            }
        }
    }

    private suspend fun handleTagEditor(event: ModalSubmitInteractionCreateEvent, guild: Guild, name: String) {
        val content = event.interaction.textInputs["content"]?.value ?: return

        val response = event.interaction.deferEphemeralResponse()

        // Check if the tag exists
        if (!TagEntity.exists(guild.id.rawValue, name)) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    title = "Tag not found"
                    description = "A tag with the name `$name` does not exist."

                    field("Content", false) {
                        buildString {
                            appendLine("Still want to create/edit a tag?")
                            appendLine("Here's the content you provided:")
                            appendLine("```")
                            append(content)
                            append("```")
                        }
                    }
                }
            }

            return
        }

        // Update the tag
        TagEntity.edit(guild.id.rawValue, name, content)

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                title = "Tag updated"
                description = "The tag `$name` has been updated."
            }
        }
    }

}
