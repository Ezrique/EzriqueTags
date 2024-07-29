package dev.deftu.ezrique.tags.sql

import dev.deftu.ezrique.tags.utils.ilike
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class TagEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<TagEntity>(TagTable) {

        /**
         * Check if a tag exists.
         *
         * @param guildId The guild ID.
         * @param name The tag name, not case-sensitive.
         * @return True if the tag exists, false otherwise.
         *
         * @since 0.1.0
         * @author Deftu
         */
        suspend fun exists(
            guildId: Long,
            name: String
        ): Boolean {
            return newSuspendedTransaction {
                find {
                    (TagTable.guildId eq guildId) and (TagTable.name ilike name)
                }.count() > 0
            }
        }

        suspend fun create(
            guildId: Long,
            name: String,
            content: String
        ) {
            newSuspendedTransaction {
                TagEntity.new {
                    this.guildId = guildId
                    this.name = name
                    this.content = content
                }
            }
        }

        suspend fun delete(
            guildId: Long,
            name: String
        ) {
            newSuspendedTransaction {
                find {
                    (TagTable.guildId eq guildId) and (TagTable.name ilike name)
                }.firstOrNull()?.delete()
            }
        }

        suspend fun edit(
            guildId: Long,
            name: String,
            content: String
        ) {
            newSuspendedTransaction {
                find {
                    (TagTable.guildId eq guildId) and (TagTable.name ilike name)
                }.firstOrNull()?.let {
                    it.content = content
                }
            }
        }

        suspend fun transfer(
            guildId: Long,
            name: String,
            newGuildId: Long
        ) {
            newSuspendedTransaction {
                find {
                    (TagTable.guildId eq guildId) and (TagTable.name ilike name)
                }.firstOrNull()?.let {
                    it.guildId = newGuildId
                }
            }
        }

        suspend fun get(
            guildId: Long,
            name: String
        ): TagEntity? {
            return newSuspendedTransaction {
                find {
                    (TagTable.guildId eq guildId) and (TagTable.name ilike name)
                }.firstOrNull()
            }
        }

        suspend fun list(
            guildId: Long
        ): List<TagEntity> {
            return newSuspendedTransaction {
                find {
                    TagTable.guildId eq guildId
                }.toList()
            }
        }

        suspend fun listAll(): List<TagEntity> {
            return newSuspendedTransaction {
                all().toList()
            }
        }

    }

    var guildId by TagTable.guildId
    var name by TagTable.name
    var content by TagTable.content

}

object TagTable : LongIdTable("tags") {

    val guildId = long("guild_id").index()
    val name = varchar("name", 255)
    val content = text("content")

}
