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
    TAG_COPY(9),
    TAG_MOVE(10),
    TAG_INFO(11),
    TAG_MANUAL_TRIGGER(12),
    TAG_CREATE_SUBMIT(13),
    TAG_EDIT_SUBMIT(14),
    TAG_TRIGGER(15);

}
