package io.github.ascenderx.mobilescript.models.scripting

enum class ScriptMessageStatus(val value: Int) {
    ERROR(-1),
    RESULT(0),
    PRINT(1),
    CLEAR(2)
}