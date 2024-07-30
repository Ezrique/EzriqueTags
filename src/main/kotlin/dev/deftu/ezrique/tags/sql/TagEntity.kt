package dev.deftu.ezrique.tags.sql

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

class TagEntity(id: EntityID<Long>) : LongEntity(id) {

    companion object : LongEntityClass<TagEntity>(TagTable)

    var guildId by TagTable.guildId
    var name by TagTable.name
    var description by TagTable.description
    var copyable by TagTable.copyable
    var content by TagTable.content

}

object TagTable : LongIdTable("tags") {

    const val COPYABLE_DEFAULT = true

    val guildId = long("guild_id").index()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val copyable = bool("copyable").default(COPYABLE_DEFAULT)
    val content = text("content")

}
