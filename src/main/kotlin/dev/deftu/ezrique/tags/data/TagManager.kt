package dev.deftu.ezrique.tags.data

import dev.deftu.ezrique.rawValue
import dev.deftu.ezrique.tags.sql.TagEntity
import dev.deftu.ezrique.tags.sql.TagTable
import dev.deftu.ezrique.tags.utils.ilike
import dev.kord.common.entity.Snowflake
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object TagManager {

    private val TAG_NAME_REGEX = Regex("^[a-z0-9-]{3,32}$")
    private val RESERVED_NAMES = arrayOf("tag")

    /**
     * Validate a tag name.
     *
     * @param name The tag name.
     * @return True if the tag name is valid, false otherwise.
     *
     * @since 0.1.0
     * @author Deftu
     */
    fun validateName(name: String): Boolean {
        return name.matches(TAG_NAME_REGEX) && !RESERVED_NAMES.contains(name)
    }

    /**
     * Replaces spaces with dashes, forces lowercase and trims whitespace.
     *
     * @param name The tag name.
     * @return The ensured tag name.
     *
     * @since 0.1.0
     * @author Deftu
     */
    fun ensureName(name: String): String {
        return name.trim().replace(" ", "-").lowercase()
    }

    /**
     * Get the default description for a tag.
     *
     * @param tagName The tag name.
     * @return The default description for the tag.
     *
     * @since 0.1.0
     * @author Deftu
     */
    fun getDefaultDescriptionFor(tagName: String): String {
        return "Triggers the '$tagName' tag."
    }

    /**
     * Get the description for a tag.
     *
     * @param tag The tag entity.
     * @param tagName The tag name.
     * @return The description for the tag.
     *
     * @since 0.1.0
     * @author Deftu
     */
    fun getDescriptionFor(tag: TagEntity?, tagName: String): String {
        val description = tag?.description
        return if (description.isNullOrBlank()) {
            getDefaultDescriptionFor(tagName)
        } else {
            description
        }
    }

    /**
     * Check if a tag exists.
     *
     * @param id The guild ID.
     * @param name The tag name, not case-sensitive.
     * @return True if the tag exists, false otherwise.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun existsFor(id: Snowflake, name: String): Boolean {
        return newSuspendedTransaction {
            TagEntity.find {
                (TagTable.guildId eq id.rawValue) and (TagTable.name ilike name)
            }.count() > 0
        }
    }

    /**
     * Get a tag.
     *
     * @param id The guild ID.
     * @param name The tag name, not case-sensitive.
     * @return The tag entity, or null if it doesn't exist.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun getFor(id: Snowflake, name: String): TagEntity? {
        return newSuspendedTransaction {
            TagEntity.find {
                (TagTable.guildId eq id.rawValue) and (TagTable.name ilike name)
            }.firstOrNull()
        }
    }

    /**
     * Creates a tag.
     *
     * @param id The guild ID.
     * @param name The tag name.
     * @param description The tag description.
     * @param copyable If the tag is syncable.
     * @param content The tag content.
     *
     * @throws IllegalArgumentException If the tag name is invalid.
     * @see validateName
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun createFor(
        id: Snowflake,
        name: String,
        description: String?,
        copyable: Boolean?,
        content: String
    ): TagEntity {
        if (!validateName(name)) {
            throw IllegalArgumentException("Invalid tag name")
        }

        return newSuspendedTransaction {
            TagEntity.new {
                this.guildId = id.rawValue
                this.name = name
                this.description = description ?: getDefaultDescriptionFor(name)
                this.copyable = copyable ?: TagTable.COPYABLE_DEFAULT
                this.content = content
            }
        }
    }

    /**
     * Edit a tag.
     *
     * This function only allows the description, syncability, and content to be edited.
     *
     * @param id The guild ID.
     * @param name The tag name.
     * @param description The tag description.
     * @param copyable If the tag is syncable.
     * @param content The tag content.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun editFor(
        id: Snowflake,
        name: String,
        description: String?,
        copyable: Boolean?,
        content: String
    ) {
        newSuspendedTransaction {
            TagEntity.find {
                (TagTable.guildId eq id.rawValue) and (TagTable.name ilike name)
            }.firstOrNull()?.apply {
                this.description = description
                this.copyable = copyable ?: TagTable.COPYABLE_DEFAULT
                this.content = content
            }
        }
    }

    /**
     * Delete a tag.
     *
     * @param id The guild ID.
     * @param name The tag name, not case-sensitive.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun deleteFor(id: Snowflake, name: String) {
        newSuspendedTransaction {
            TagEntity.find {
                (TagTable.guildId eq id.rawValue) and (TagTable.name ilike name)
            }.firstOrNull()?.delete()
        }
    }

    /**
     * List tags for a guild.
     *
     * @param id The guild ID.
     * @return The list of tags for the guild.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun listFor(id: Snowflake): List<TagEntity> {
        return newSuspendedTransaction {
            TagEntity.find { TagTable.guildId eq id.rawValue }.toList()
        }
    }

    /**
     * Copy a tag to another guild.
     *
     * @param id The guild ID.
     * @param name The tag name, not case-sensitive.
     * @param target The target guild ID.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun copyTo(id: Snowflake, name: String, target: Snowflake): TagEntity? {
        return newSuspendedTransaction {
            TagEntity.find {
                (TagTable.guildId eq id.rawValue) and (TagTable.name ilike name)
            }.firstOrNull()?.let { tag ->
                TagEntity.new {
                    this.guildId = target.rawValue
                    this.name = tag.name
                    this.description = tag.description
                    this.copyable = tag.copyable
                    this.content = tag.content
                }
            }
        }
    }

    /**
     * Move a tag to another guild.
     *
     * @param id The guild ID.
     * @param name The tag name, not case-sensitive.
     * @param target The target guild ID.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun moveTo(id: Snowflake, name: String, target: Snowflake): TagEntity? {
        return newSuspendedTransaction {
            TagEntity.find {
                (TagTable.guildId eq id.rawValue) and (TagTable.name ilike name)
            }.firstOrNull()?.apply {
                this.guildId = target.rawValue
            }
        }
    }

    /**
     * List all tags.
     *
     * @return The list of all tags.
     *
     * @since 0.1.0
     * @author Deftu
     */
    suspend fun listAll(): List<TagEntity> {
        return newSuspendedTransaction {
            TagEntity.all().toList()
        }
    }

}
