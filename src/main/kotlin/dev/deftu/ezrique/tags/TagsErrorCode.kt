package dev.deftu.ezrique.tags

import dev.deftu.ezrique.ErrorCode

enum class TagsErrorCode(override val code: Int) : ErrorCode {

    UNKNOWN(1),

    UNKNOWN_COMMAND(2),

    KORD_LOGIN(3)

}
