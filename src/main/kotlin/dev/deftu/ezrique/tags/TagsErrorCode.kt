package dev.deftu.ezrique.tags

import dev.deftu.ezrique.ErrorCode

enum class TagsErrorCode(override val code: Int) : ErrorCode {

    UNKNOWN(1),

    UNKNOWN_COMMAND(2),

    KORD_LOGIN(3),

    TAG_AUTOCOMPLETE(4),
    TAG_LIST(5),
    TAG_CREATE(6),
    TAG_EDIT(7),
    TAG_DELETE(8),
    TAG_CLEAR(9),
    TAG_COPY(10),
    TAG_COPYALL(11),
    TAG_MOVE(12),
    TAG_MOVEALL(13),
    TAG_INFO(14),
    TAG_MANUAL_TRIGGER(15),
    TAG_CREATE_SUBMIT(16),
    TAG_EDIT_SUBMIT(17),
    TAG_TRIGGER(18),
    TAG_EXPORT(19),
    TAG_EXPORTALL(20),
    TAG_IMPORT(21),
    TAG_IMPORTBULK(22);

}
